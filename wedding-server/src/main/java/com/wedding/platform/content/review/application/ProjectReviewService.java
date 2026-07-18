package com.wedding.platform.content.review.application;

import com.wedding.platform.content.project.application.ProjectService;
import com.wedding.platform.content.project.persistence.entity.WeddingProject;
import com.wedding.platform.content.project.persistence.repository.ProjectCreatorRepository;
import com.wedding.platform.content.project.persistence.repository.WeddingProjectRepository;
import com.wedding.platform.content.publication.application.PublicContentAccessService;
import com.wedding.platform.content.review.persistence.entity.ReviewItem;
import com.wedding.platform.content.review.persistence.entity.ReviewItemStatus;
import com.wedding.platform.content.review.persistence.entity.ReviewItemType;
import com.wedding.platform.content.review.persistence.entity.ReviewTargetType;
import com.wedding.platform.content.review.persistence.entity.ReviewTask;
import com.wedding.platform.content.review.web.ReviewDtos;
import com.wedding.platform.content.shared.ContentVisibility;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.content.shared.ReviewStatus;
import com.wedding.platform.operations.notification.application.UserNotificationService;
import com.wedding.platform.operations.notification.persistence.entity.UserNotificationRelatedType;
import com.wedding.platform.operations.notification.persistence.entity.UserNotificationType;
import com.wedding.platform.platform.audit.AuditLogService;
import com.wedding.platform.platform.web.ApiException;
import com.wedding.platform.system.account.persistence.entity.SystemUser;
import com.wedding.platform.system.account.persistence.repository.SystemUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Service
public class ProjectReviewService {

    private static final int MAX_PAGE_SIZE = 100;

    private final WeddingProjectRepository projectRepository;
    private final ProjectCreatorRepository projectCreatorRepository;
    private final SystemUserRepository userRepository;
    private final ProjectService projectService;
    private final ReviewRevisionService reviewRevisionService;
    private final AuditLogService auditLogService;
    private final PublicContentAccessService contentAccessService;
    private final UserNotificationService notificationService;

    public ProjectReviewService(
            WeddingProjectRepository projectRepository,
            ProjectCreatorRepository projectCreatorRepository,
            SystemUserRepository userRepository,
            ProjectService projectService,
            ReviewRevisionService reviewRevisionService,
            AuditLogService auditLogService,
            PublicContentAccessService contentAccessService,
            UserNotificationService notificationService
    ) {
        this.projectRepository = projectRepository;
        this.projectCreatorRepository = projectCreatorRepository;
        this.userRepository = userRepository;
        this.projectService = projectService;
        this.reviewRevisionService = reviewRevisionService;
        this.auditLogService = auditLogService;
        this.contentAccessService = contentAccessService;
        this.notificationService = notificationService;
    }

    @Transactional
    public ReviewDtos.ProjectReviewDetailResponse submit(
            Long operatorId,
            Long projectId,
            ReviewDtos.VersionRequest request,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        WeddingProject project = getProject(projectId);
        requireAccess(actor, project);
        requireEditable(project);
        requireVersion(project, request.version());
        if (ReviewStatus.PENDING == project.getReviewStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_ALREADY_PENDING",
                    "The project is already pending review");
        }

        ReviewTask task = reviewRevisionService.submitProject(project, operatorId);
        ReviewItem rejectedField = currentFields(projectId).stream()
                .filter(item -> ReviewItemStatus.REJECTED == item.getStatus())
                .findFirst()
                .orElse(null);
        project.setReviewStatus(rejectedField == null
                ? ReviewStatus.PENDING
                : ReviewStatus.PARTIALLY_REJECTED);
        project.setRejectionReason(rejectedField == null ? null : rejectedField.getRejectionReason());
        project.setSubmittedAt(task.getSubmittedAt());
        project.setReviewedAt(null);
        project.setReviewedBy(null);
        if (PublishStatus.READY == project.getPublishStatus()) {
            project.setPublishStatus(PublishStatus.UNPUBLISHED);
        }
        saveProject(project, operatorId);
        notificationService.notifyAdmins(
                operatorId,
                UserNotificationType.PROJECT_REVIEW_TASK,
                "婚礼项目待审核",
                "项目“" + project.getTitle() + "”已提交审核，请及时处理。",
                UserNotificationRelatedType.PROJECT_REVIEW,
                projectId
        );
        auditLogService.record(operatorId, actor.getAccountType(), "PROJECT_REVIEW", "SUBMIT_PROJECT",
                "WEDDING_PROJECT", projectId, "Project submitted for field review", ipAddress);
        return detail(operatorId, projectId);
    }

    @Transactional(readOnly = true)
    public ReviewDtos.ProjectReviewQueueResponse list(
            Long operatorId,
            int page,
            int size,
            String keyword,
            ReviewStatus reviewStatus,
            PublishStatus publishStatus
    ) {
        requireAdmin(getActor(operatorId));
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAGE_INVALID",
                    "Page must be at least 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        Page<WeddingProject> result = projectRepository.findWorkflowProjects(
                reviewStatus,
                publishStatus,
                trimToNull(keyword),
                ReviewStatus.PENDING,
                ReviewStatus.PARTIALLY_REJECTED,
                PublishStatus.READY,
                PageRequest.of(page, size)
        );
        return new ReviewDtos.ProjectReviewQueueResponse(
                result.getContent().stream().map(this::toQueueItem).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional
    public ReviewDtos.ProjectReviewDetailResponse detail(Long operatorId, Long projectId) {
        SystemUser actor = getActor(operatorId);
        WeddingProject project = getProject(projectId);
        if (!isAdmin(actor)) {
            requireAccess(actor, project);
        }
        reviewRevisionService.ensureProjectBaseline(project);
        return new ReviewDtos.ProjectReviewDetailResponse(
                projectService.getProject(operatorId, projectId),
                reviewRevisionService.history(ReviewTargetType.PROJECT, projectId)
        );
    }

    @Transactional
    public ReviewDtos.ProjectReviewDetailResponse reviewFields(
            Long operatorId,
            Long projectId,
            ReviewDtos.ReviewFieldsRequest request,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        requireAdmin(actor);
        WeddingProject project = getProject(projectId);
        requireVersion(project, request.version());
        ReviewStatus previousStatus = project.getReviewStatus();
        reviewRevisionService.ensureProjectBaseline(project);
        if (ReviewStatus.PENDING != project.getReviewStatus()
                && ReviewStatus.PARTIALLY_REJECTED != project.getReviewStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_NOT_PENDING",
                    "Only a submitted project can receive field review decisions");
        }
        reviewRevisionService.reviewItems(
                ReviewTargetType.PROJECT,
                projectId,
                ReviewItemType.FIELD,
                request.reviewItemIds(),
                request.decision(),
                request.reason(),
                operatorId
        );
        recalculate(project, operatorId);
        notifyProjectReviewResult(project, operatorId, previousStatus);
        auditLogService.record(operatorId, actor.getAccountType(), "PROJECT_REVIEW",
                request.decision() == ReviewDtos.ReviewDecision.APPROVE
                        ? "APPROVE_PROJECT_FIELDS"
                        : "REJECT_PROJECT_FIELDS",
                "WEDDING_PROJECT", projectId,
                request.decision() == ReviewDtos.ReviewDecision.APPROVE
                        ? "Approved " + request.reviewItemIds().size() + " project fields"
                        : request.reason().trim(),
                ipAddress);
        return detail(operatorId, projectId);
    }

    @Transactional
    public ReviewDtos.ProjectReviewDetailResponse approve(
            Long operatorId,
            Long projectId,
            ReviewDtos.VersionRequest request,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        requireAdmin(actor);
        WeddingProject project = getProject(projectId);
        requireVersion(project, request.version());
        ReviewStatus previousStatus = project.getReviewStatus();
        reviewRevisionService.ensureProjectBaseline(project);
        List<ReviewItem> pending = currentFields(projectId).stream()
                .filter(item -> ReviewItemStatus.PENDING == item.getStatus())
                .toList();
        if (!pending.isEmpty()) {
            reviewRevisionService.reviewAllPendingFields(
                    ReviewTargetType.PROJECT,
                    projectId,
                    ReviewDtos.ReviewDecision.APPROVE,
                    null,
                    operatorId
            );
        }
        recalculate(project, operatorId);
        if (ReviewStatus.APPROVED != project.getReviewStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_FIELDS_NOT_APPROVED",
                    "Every current project field must be approved");
        }
        notifyProjectReviewResult(project, operatorId, previousStatus);
        auditLogService.record(operatorId, actor.getAccountType(), "PROJECT_REVIEW", "APPROVE_PROJECT",
                "WEDDING_PROJECT", projectId, "Project fields approved", ipAddress);
        return detail(operatorId, projectId);
    }

    @Transactional
    public ReviewDtos.ProjectReviewDetailResponse reject(
            Long operatorId,
            Long projectId,
            ReviewDtos.RejectCollectionRequest request,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        requireAdmin(actor);
        WeddingProject project = getProject(projectId);
        requireVersion(project, request.version());
        ReviewStatus previousStatus = project.getReviewStatus();
        reviewRevisionService.ensureProjectBaseline(project);
        reviewRevisionService.reviewAllPendingFields(
                ReviewTargetType.PROJECT,
                projectId,
                ReviewDtos.ReviewDecision.REJECT,
                request.reason(),
                operatorId
        );
        recalculate(project, operatorId);
        notifyProjectReviewResult(project, operatorId, previousStatus);
        auditLogService.record(operatorId, actor.getAccountType(), "PROJECT_REVIEW", "REJECT_PROJECT",
                "WEDDING_PROJECT", projectId, request.reason().trim(), ipAddress);
        return detail(operatorId, projectId);
    }

    @Transactional
    public ReviewDtos.ProjectReviewDetailResponse publish(
            Long operatorId,
            Long projectId,
            ReviewDtos.PublishProjectRequest request,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        requireAdmin(actor);
        WeddingProject project = getProject(projectId);
        requireVersion(project, request.version());
        boolean publishableStatus = PublishStatus.READY == project.getPublishStatus()
                || PublishStatus.OFFLINE == project.getPublishStatus();
        if (ReviewStatus.APPROVED != project.getReviewStatus() || !publishableStatus) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_NOT_READY",
                    "The project must be fully approved before publishing");
        }
        if (!reviewRevisionService.allRequiredFieldsApproved(
                ReviewTargetType.PROJECT,
                projectId,
                ReviewRevisionService.PROJECT_FIELD_KEYS)) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_FIELDS_NOT_APPROVED",
                    "Every current project field must be approved before publishing");
        }

        Instant now = Instant.now();
        project.setVisibility(request.visibility());
        project.setAccessPasswordHash(ContentVisibility.PASSWORD == request.visibility()
                ? contentAccessService.encodePassword(request.accessPassword())
                : null);
        project.setPublishStatus(PublishStatus.PUBLISHED);
        project.setPublishedAt(now);
        project.setPublishedBy(operatorId);
        project.setOfflineReason(null);
        saveProject(project, operatorId);
        notificationService.notifyProjectCreators(
                projectId,
                operatorId,
                UserNotificationType.PROJECT_PUBLISHED,
                "婚礼项目已发布",
                "项目“" + project.getTitle() + "”已发布到官网。",
                UserNotificationRelatedType.PROJECT
        );
        auditLogService.record(operatorId, actor.getAccountType(), "PROJECT_PUBLICATION", "PUBLISH_PROJECT",
                "WEDDING_PROJECT", projectId, "Project published as " + request.visibility(), ipAddress);
        return detail(operatorId, projectId);
    }

    @Transactional
    public ReviewDtos.ProjectReviewDetailResponse offline(
            Long operatorId,
            Long projectId,
            ReviewDtos.OfflineProjectRequest request,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        requireAdmin(actor);
        WeddingProject project = getProject(projectId);
        requireVersion(project, request.version());
        if (PublishStatus.PUBLISHED != project.getPublishStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_NOT_PUBLISHED",
                    "Only a published project can be taken offline");
        }
        project.setPublishStatus(PublishStatus.OFFLINE);
        project.setOfflineReason(request.reason().trim());
        saveProject(project, operatorId);
        notificationService.notifyProjectCreators(
                projectId,
                operatorId,
                UserNotificationType.PROJECT_OFFLINE,
                "婚礼项目已下架",
                "项目“" + project.getTitle() + "”已从官网下架。原因：" + project.getOfflineReason(),
                UserNotificationRelatedType.PROJECT
        );
        auditLogService.record(operatorId, actor.getAccountType(), "PROJECT_PUBLICATION", "OFFLINE_PROJECT",
                "WEDDING_PROJECT", projectId, request.reason().trim(), ipAddress);
        return detail(operatorId, projectId);
    }

    private void recalculate(WeddingProject project, Long operatorId) {
        List<ReviewItem> fields = currentFields(project.getId());
        boolean pending = fields.stream().anyMatch(item -> ReviewItemStatus.PENDING == item.getStatus());
        boolean rejected = fields.stream().anyMatch(item -> ReviewItemStatus.REJECTED == item.getStatus());
        if (rejected) {
            project.setReviewStatus(ReviewStatus.PARTIALLY_REJECTED);
            project.setRejectionReason(fields.stream()
                    .filter(item -> ReviewItemStatus.REJECTED == item.getStatus())
                    .map(ReviewItem::getRejectionReason)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse(null));
        } else if (pending) {
            project.setReviewStatus(ReviewStatus.PENDING);
            project.setRejectionReason(null);
        } else if (reviewRevisionService.allRequiredFieldsApproved(
                ReviewTargetType.PROJECT,
                project.getId(),
                ReviewRevisionService.PROJECT_FIELD_KEYS)) {
            project.setReviewStatus(ReviewStatus.APPROVED);
            project.setPublishStatus(PublishStatus.READY);
            project.setRejectionReason(null);
        } else {
            project.setReviewStatus(ReviewStatus.DRAFT);
        }
        project.setReviewedAt(Instant.now());
        project.setReviewedBy(operatorId);
        saveProject(project, operatorId);
    }

    private ReviewDtos.ProjectReviewQueueItem toQueueItem(WeddingProject project) {
        List<ReviewItem> fields = currentFields(project.getId());
        return new ReviewDtos.ProjectReviewQueueItem(
                project.getId(),
                project.getProjectCode(),
                project.getTitle(),
                project.getLocationText(),
                project.getReviewStatus(),
                project.getPublishStatus(),
                project.getRejectionReason(),
                project.getSubmittedAt(),
                project.getUpdatedAt(),
                project.getVersion(),
                countStatus(fields, ReviewItemStatus.PENDING),
                countStatus(fields, ReviewItemStatus.REJECTED),
                countStatus(fields, ReviewItemStatus.APPROVED)
        );
    }

    private List<ReviewItem> currentFields(Long projectId) {
        return reviewRevisionService.currentItems(
                ReviewTargetType.PROJECT,
                projectId,
                ReviewItemType.FIELD
        );
    }

    private long countStatus(List<ReviewItem> items, ReviewItemStatus status) {
        return items.stream().filter(item -> status == item.getStatus()).count();
    }

    private WeddingProject getProject(Long projectId) {
        return projectRepository.findByIdAndDeletedFalse(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND",
                        "Wedding project was not found"));
    }

    private SystemUser getActor(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .filter(user -> "ACTIVE".equals(user.getAccountStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_NOT_FOUND",
                        "Account is not available"));
    }

    private void requireAdmin(SystemUser actor) {
        if (!isAdmin(actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED",
                    "Only administrators can review projects");
        }
    }

    private void requireAccess(SystemUser actor, WeddingProject project) {
        if (isAdmin(actor)
                || project.getCreatedBy().equals(actor.getId())
                || projectCreatorRepository.existsByProjectIdAndCreatorUserId(project.getId(), actor.getId())) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "PROJECT_ACCESS_DENIED",
                "You do not participate in this wedding project");
    }

    private void requireEditable(WeddingProject project) {
        if (PublishStatus.PUBLISHED == project.getPublishStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_PUBLISHED_LOCKED",
                    "Published project details are locked until the project is taken offline");
        }
    }

    private void requireVersion(WeddingProject project, Long requestVersion) {
        if (requestVersion == null || !project.getVersion().equals(requestVersion)) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_VERSION_CONFLICT",
                    "The project was updated by another user; reload it before saving");
        }
    }

    private void saveProject(WeddingProject project, Long operatorId) {
        project.setUpdatedBy(operatorId);
        project.setUpdatedAt(Instant.now());
        projectRepository.saveAndFlush(project);
    }

    private boolean isAdmin(SystemUser actor) {
        return "ADMIN".equals(actor.getAccountType());
    }

    private void notifyProjectReviewResult(
            WeddingProject project,
            Long operatorId,
            ReviewStatus previousStatus
    ) {
        if (previousStatus == project.getReviewStatus()) {
            return;
        }
        if (ReviewStatus.APPROVED == project.getReviewStatus()) {
            notificationService.notifyProjectCreators(
                    project.getId(),
                    operatorId,
                    UserNotificationType.PROJECT_REVIEW_APPROVED,
                    "婚礼项目审核已通过",
                    "项目“" + project.getTitle() + "”的审核已通过，可以继续发布或上架。",
                    UserNotificationRelatedType.PROJECT_REVIEW
            );
        } else if (ReviewStatus.PARTIALLY_REJECTED == project.getReviewStatus()) {
            notificationService.notifyProjectCreators(
                    project.getId(),
                    operatorId,
                    UserNotificationType.PROJECT_REVIEW_REJECTED,
                    "婚礼项目审核需要修改",
                    "项目“" + project.getTitle() + "”的审核未完全通过。原因："
                            + (project.getRejectionReason() == null ? "请查看审核详情" : project.getRejectionReason()),
                    UserNotificationRelatedType.PROJECT_REVIEW
            );
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
