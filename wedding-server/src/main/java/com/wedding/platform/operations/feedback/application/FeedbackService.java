package com.wedding.platform.operations.feedback.application;

import com.wedding.platform.content.project.persistence.entity.ProjectCreator;
import com.wedding.platform.content.project.persistence.entity.ProjectCustomerApplication;
import com.wedding.platform.content.project.persistence.entity.WeddingProject;
import com.wedding.platform.content.project.persistence.repository.ProjectCreatorRepository;
import com.wedding.platform.content.project.persistence.repository.ProjectCustomerApplicationRepository;
import com.wedding.platform.content.project.persistence.repository.WeddingProjectRepository;
import com.wedding.platform.content.shared.ContentVisibility;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.operations.notification.application.UserNotificationService;
import com.wedding.platform.operations.notification.persistence.entity.UserNotificationRelatedType;
import com.wedding.platform.operations.notification.persistence.entity.UserNotificationType;
import com.wedding.platform.operations.feedback.persistence.entity.CustomerFeedback;
import com.wedding.platform.operations.feedback.persistence.entity.FeedbackPublishStatus;
import com.wedding.platform.operations.feedback.persistence.entity.FeedbackReply;
import com.wedding.platform.operations.feedback.persistence.entity.FeedbackReviewStatus;
import com.wedding.platform.operations.feedback.persistence.repository.CustomerFeedbackRepository;
import com.wedding.platform.operations.feedback.persistence.repository.FeedbackReplyRepository;
import com.wedding.platform.operations.feedback.web.FeedbackDtos;
import com.wedding.platform.platform.audit.AuditLogService;
import com.wedding.platform.platform.web.ApiException;
import com.wedding.platform.system.account.persistence.entity.ProfessionalRole;
import com.wedding.platform.system.account.persistence.entity.CustomerProfile;
import com.wedding.platform.system.account.persistence.entity.SystemUser;
import com.wedding.platform.system.account.persistence.repository.CustomerProfileRepository;
import com.wedding.platform.system.account.persistence.repository.SystemUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class FeedbackService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerFeedbackRepository feedbackRepository;
    private final FeedbackReplyRepository replyRepository;
    private final WeddingProjectRepository projectRepository;
    private final ProjectCreatorRepository projectCreatorRepository;
    private final ProjectCustomerApplicationRepository projectCustomerApplicationRepository;
    private final SystemUserRepository userRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final UserNotificationService notificationService;
    private final AuditLogService auditLogService;

    public FeedbackService(
            CustomerFeedbackRepository feedbackRepository,
            FeedbackReplyRepository replyRepository,
            WeddingProjectRepository projectRepository,
            ProjectCreatorRepository projectCreatorRepository,
            ProjectCustomerApplicationRepository projectCustomerApplicationRepository,
            SystemUserRepository userRepository,
            CustomerProfileRepository customerProfileRepository,
            UserNotificationService notificationService,
            AuditLogService auditLogService
    ) {
        this.feedbackRepository = feedbackRepository;
        this.replyRepository = replyRepository;
        this.projectRepository = projectRepository;
        this.projectCreatorRepository = projectCreatorRepository;
        this.projectCustomerApplicationRepository = projectCustomerApplicationRepository;
        this.userRepository = userRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public FeedbackDtos.FeedbackPage list(
            Long userId,
            int page,
            int size,
            FeedbackReviewStatus reviewStatus,
            FeedbackPublishStatus publishStatus,
            Long projectId
    ) {
        validatePage(page, size);
        SystemUser actor = getActor(userId);
        requireFeedbackAccount(actor);
        Page<CustomerFeedback> result = isAdmin(actor)
                ? feedbackRepository.findAllFeedback(
                        reviewStatus,
                        publishStatus,
                        projectId,
                        FeedbackReviewStatus.PENDING,
                        FeedbackReviewStatus.REJECTED,
                        PageRequest.of(page, size))
                : feedbackRepository.findCreatorFeedback(
                        userId,
                        reviewStatus,
                        publishStatus,
                        projectId,
                        FeedbackReviewStatus.PENDING,
                        FeedbackReviewStatus.REJECTED,
                        PageRequest.of(page, size));
        return new FeedbackDtos.FeedbackPage(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public FeedbackDtos.FeedbackOptions options(Long userId) {
        SystemUser actor = getActor(userId);
        requireFeedbackAccount(actor);
        List<WeddingProject> projects = isAdmin(actor)
                ? projectRepository.findAllByDeletedFalseOrderByCreatedAtDesc()
                : projectRepository.findAllAccessibleProjects(userId);
        List<SystemUser> creators = isAdmin(actor)
                ? userRepository.findAllByAccountTypeAndDeletedFalseOrderByCreatedAtDesc("CREATOR").stream()
                        .filter(user -> "ACTIVE".equals(user.getAccountStatus()))
                        .toList()
                : List.of(actor);
        return new FeedbackDtos.FeedbackOptions(
                projects.stream().map(this::projectSummary).toList(),
                creators.stream().map(this::creatorSummary).toList()
        );
    }

    @Transactional(readOnly = true)
    public FeedbackDtos.FeedbackPage customerFeedback(
            Long customerId,
            int page,
            int size,
            FeedbackReviewStatus reviewStatus,
            FeedbackPublishStatus publishStatus
    ) {
        validatePage(page, size);
        requireCustomer(customerId);
        Page<CustomerFeedback> result = feedbackRepository.findCustomerFeedback(
                customerId,
                reviewStatus,
                publishStatus,
                PageRequest.of(page, size)
        );
        return new FeedbackDtos.FeedbackPage(
                result.getContent().stream()
                        .map(feedback -> toResponse(feedback, true))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public FeedbackDtos.CustomerFeedbackOptions customerOptions(Long customerId) {
        requireCustomer(customerId);
        List<ProjectCustomerApplication> applications = projectCustomerApplicationRepository
                .findAllByCustomerUserIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(customerId, "APPROVED");
        List<WeddingProject> projects = projectRepository.findAllById(
                        applications.stream().map(ProjectCustomerApplication::getProjectId).toList())
                .stream()
                .filter(project -> !Boolean.TRUE.equals(project.getDeleted()))
                .toList();

        LinkedHashMap<Long, SystemUser> creators = new LinkedHashMap<>();
        for (WeddingProject project : projects) {
            userRepository.findById(project.getCreatedBy())
                    .filter(this::isActiveCreator)
                    .ifPresent(creator -> creators.put(creator.getId(), creator));
            projectCreatorRepository.findAllByProjectId(project.getId()).stream()
                    .map(ProjectCreator::getId)
                    .map(id -> id.getCreatorUserId())
                    .forEach(creatorId -> userRepository.findById(creatorId)
                            .filter(this::isActiveCreator)
                            .ifPresent(creator -> creators.put(creator.getId(), creator)));
        }
        return new FeedbackDtos.CustomerFeedbackOptions(
                projects.stream().map(this::customerProjectSummary).toList(),
                creators.values().stream().map(this::creatorSummary).toList()
        );
    }

    @Transactional
    public FeedbackDtos.FeedbackResponse createByCustomer(
            Long customerId,
            FeedbackDtos.CustomerFeedbackRequest request,
            String ipAddress
    ) {
        SystemUser customer = requireCustomer(customerId);
        WeddingProject project = getProject(request.projectId());
        SystemUser creator = getCreator(request.creatorUserId());
        requireCustomerSubmissionAccess(customerId, project, creator);

        CustomerFeedback feedback = new CustomerFeedback();
        applyCustomer(feedback, customer, request);
        feedback.setCustomerUserId(customerId);
        feedback.setSubmittedBy(customerId);
        feedback.setReviewStatus(FeedbackReviewStatus.PENDING);
        feedback.setPublishStatus(FeedbackPublishStatus.UNPUBLISHED);
        feedback.setCreatedBy(customerId);
        feedback.setUpdatedBy(customerId);
        feedback = feedbackRepository.save(feedback);

        notifyNewFeedback(customerId, feedback, "新的客户评价待审核");
        auditLogService.record(
                customerId,
                customer.getAccountType(),
                "FEEDBACK",
                "CREATE_CUSTOMER_FEEDBACK",
                "CUSTOMER_FEEDBACK",
                feedback.getId(),
                "Customer submitted feedback for review",
                ipAddress
        );
        return toResponse(feedback, true);
    }

    @Transactional
    public FeedbackDtos.FeedbackResponse updateByCustomer(
            Long customerId,
            Long feedbackId,
            FeedbackDtos.CustomerFeedbackRequest request,
            String ipAddress
    ) {
        SystemUser customer = requireCustomer(customerId);
        CustomerFeedback feedback = getFeedback(feedbackId);
        if (!customerId.equals(feedback.getCustomerUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FEEDBACK_EDIT_ACCESS_DENIED",
                    "Customers can only update feedback they submitted");
        }
        requireVersion(feedback.getVersion(), request.version(), "FEEDBACK_VERSION_CONFLICT");
        if (FeedbackPublishStatus.PUBLISHED == feedback.getPublishStatus()
                || FeedbackPublishStatus.OFFLINE == feedback.getPublishStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "FEEDBACK_PUBLISHED_LOCKED",
                    "Published or offline feedback cannot be edited");
        }
        WeddingProject project = getProject(request.projectId());
        SystemUser creator = getCreator(request.creatorUserId());
        requireCustomerSubmissionAccess(customerId, project, creator);
        applyCustomer(feedback, customer, request);
        feedback.setReviewStatus(FeedbackReviewStatus.PENDING);
        feedback.setRejectionReason(null);
        feedback.setReviewedBy(null);
        feedback.setReviewedAt(null);
        feedback.setUpdatedBy(customerId);
        feedback = feedbackRepository.save(feedback);

        notifyNewFeedback(customerId, feedback, "客户评价已重新提交审核");
        auditLogService.record(
                customerId,
                customer.getAccountType(),
                "FEEDBACK",
                "UPDATE_CUSTOMER_FEEDBACK",
                "CUSTOMER_FEEDBACK",
                feedbackId,
                "Customer feedback updated and resubmitted",
                ipAddress
        );
        return toResponse(feedback, true);
    }

    @Transactional
    public FeedbackDtos.FeedbackResponse create(
            Long userId,
            FeedbackDtos.UpsertFeedbackRequest request,
            String ipAddress
    ) {
        SystemUser actor = getActor(userId);
        requireFeedbackAccount(actor);
        WeddingProject project = getProject(request.projectId());
        SystemUser creator = getCreator(request.creatorUserId());
        requireSubmissionAccess(actor, project, creator);

        CustomerFeedback feedback = new CustomerFeedback();
        apply(feedback, request);
        feedback.setSubmittedBy(userId);
        feedback.setReviewStatus(FeedbackReviewStatus.PENDING);
        feedback.setPublishStatus(FeedbackPublishStatus.UNPUBLISHED);
        feedback.setCreatedBy(userId);
        feedback.setUpdatedBy(userId);
        feedback = feedbackRepository.save(feedback);

        notifyNewFeedback(userId, feedback, "新的客户评价待审核");
        auditLogService.record(
                userId,
                actor.getAccountType(),
                "FEEDBACK",
                "CREATE_FEEDBACK",
                "CUSTOMER_FEEDBACK",
                feedback.getId(),
                "Customer feedback submitted for review",
                ipAddress
        );
        return toResponse(feedback);
    }

    @Transactional
    public FeedbackDtos.FeedbackResponse update(
            Long userId,
            Long feedbackId,
            FeedbackDtos.UpsertFeedbackRequest request,
            String ipAddress
    ) {
        SystemUser actor = getActor(userId);
        requireFeedbackAccount(actor);
        CustomerFeedback feedback = getFeedback(feedbackId);
        requireFeedbackEditAccess(actor, feedback);
        requireVersion(feedback.getVersion(), request.version(), "FEEDBACK_VERSION_CONFLICT");
        if (FeedbackPublishStatus.PUBLISHED == feedback.getPublishStatus()
                || FeedbackPublishStatus.OFFLINE == feedback.getPublishStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "FEEDBACK_PUBLISHED_LOCKED",
                    "Published or offline feedback cannot be edited");
        }

        WeddingProject project = getProject(request.projectId());
        SystemUser creator = getCreator(request.creatorUserId());
        requireSubmissionAccess(actor, project, creator);
        apply(feedback, request);
        feedback.setReviewStatus(FeedbackReviewStatus.PENDING);
        feedback.setRejectionReason(null);
        feedback.setReviewedBy(null);
        feedback.setReviewedAt(null);
        feedback.setUpdatedBy(userId);
        feedback = feedbackRepository.save(feedback);

        notifyNewFeedback(userId, feedback, "客户评价已重新提交审核");
        auditLogService.record(
                userId,
                actor.getAccountType(),
                "FEEDBACK",
                "UPDATE_FEEDBACK",
                "CUSTOMER_FEEDBACK",
                feedbackId,
                "Customer feedback updated and resubmitted",
                ipAddress
        );
        return toResponse(feedback);
    }

    @Transactional
    public void withdraw(
            Long userId,
            Long feedbackId,
            Long version,
            String ipAddress
    ) {
        SystemUser actor = getActor(userId);
        requireFeedbackEditAccount(actor);
        CustomerFeedback feedback = getFeedback(feedbackId);
        requireFeedbackEditAccess(actor, feedback);
        requireVersion(feedback.getVersion(), version, "FEEDBACK_VERSION_CONFLICT");
        if (FeedbackPublishStatus.UNPUBLISHED != feedback.getPublishStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "FEEDBACK_WITHDRAW_LOCKED",
                    "Published or offline feedback cannot be withdrawn");
        }

        Instant now = Instant.now();
        feedback.setDeleted(true);
        feedback.setDeletedAt(now);
        feedback.setUpdatedBy(userId);
        feedbackRepository.save(feedback);

        replyRepository.findByFeedbackIdAndDeletedFalse(feedbackId).ifPresent(reply -> {
            reply.setDeleted(true);
            reply.setDeletedAt(now);
            reply.setUpdatedBy(userId);
            replyRepository.save(reply);
        });

        auditLogService.record(
                userId,
                actor.getAccountType(),
                "FEEDBACK",
                "WITHDRAW_FEEDBACK",
                "CUSTOMER_FEEDBACK",
                feedbackId,
                "Customer feedback withdrawn before publication",
                ipAddress
        );
    }

    @Transactional
    public FeedbackDtos.FeedbackResponse approve(
            Long adminId,
            Long feedbackId,
            FeedbackDtos.VersionRequest request,
            String ipAddress
    ) {
        SystemUser admin = requireAdmin(adminId);
        CustomerFeedback feedback = getFeedback(feedbackId);
        requireVersion(feedback.getVersion(), request.version(), "FEEDBACK_VERSION_CONFLICT");
        requirePending(feedback);
        WeddingProject project = getProject(feedback.getProjectId());
        if (PublishStatus.PUBLISHED != project.getPublishStatus()
                || ContentVisibility.PUBLIC != project.getVisibility()) {
            throw new ApiException(HttpStatus.CONFLICT, "FEEDBACK_PROJECT_NOT_PUBLIC",
                    "Feedback can only be published for a public wedding project");
        }

        Instant now = Instant.now();
        feedback.setReviewStatus(FeedbackReviewStatus.APPROVED);
        feedback.setRejectionReason(null);
        feedback.setReviewedBy(adminId);
        feedback.setReviewedAt(now);
        feedback.setPublishStatus(FeedbackPublishStatus.PUBLISHED);
        feedback.setPublishedAt(now);
        feedback.setOfflineReason(null);
        feedback.setUpdatedBy(adminId);
        feedback = feedbackRepository.save(feedback);

        if (feedback.getCustomerUserId() != null) {
            notificationService.notifyFeedbackApproved(
                    feedback.getCustomerUserId(),
                    adminId,
                    feedback.getId()
            );
        }
        notifyFeedbackCreator(feedback, adminId, UserNotificationType.FEEDBACK_APPROVED,
                "客户评价已通过审核", "客户评价已通过审核并在官网公开展示。");
        auditLogService.record(
                adminId,
                admin.getAccountType(),
                "FEEDBACK",
                "APPROVE_FEEDBACK",
                "CUSTOMER_FEEDBACK",
                feedbackId,
                "Customer feedback approved and published",
                ipAddress
        );
        return toResponse(feedback);
    }

    @Transactional
    public FeedbackDtos.FeedbackResponse reject(
            Long adminId,
            Long feedbackId,
            FeedbackDtos.RejectRequest request,
            String ipAddress
    ) {
        SystemUser admin = requireAdmin(adminId);
        CustomerFeedback feedback = getFeedback(feedbackId);
        requireVersion(feedback.getVersion(), request.version(), "FEEDBACK_VERSION_CONFLICT");
        requirePending(feedback);
        feedback.setReviewStatus(FeedbackReviewStatus.REJECTED);
        feedback.setRejectionReason(request.reason().trim());
        feedback.setReviewedBy(adminId);
        feedback.setReviewedAt(Instant.now());
        feedback.setPublishStatus(FeedbackPublishStatus.UNPUBLISHED);
        feedback.setUpdatedBy(adminId);
        feedback = feedbackRepository.save(feedback);

        if (feedback.getCustomerUserId() != null) {
            notificationService.notifyFeedbackRejected(
                    feedback.getCustomerUserId(),
                    adminId,
                    feedback.getId(),
                    feedback.getRejectionReason()
            );
        }
        if (isCreatorUser(feedback.getSubmittedBy())) {
            notificationService.notifyUser(
                    feedback.getSubmittedBy(),
                    adminId,
                    UserNotificationType.FEEDBACK_REJECTED,
                    "客户评价未通过审核",
                    "您提交的客户评价未通过审核。原因：" + feedback.getRejectionReason(),
                    UserNotificationRelatedType.FEEDBACK,
                    feedback.getId()
            );
        }
        auditLogService.record(
                adminId,
                admin.getAccountType(),
                "FEEDBACK",
                "REJECT_FEEDBACK",
                "CUSTOMER_FEEDBACK",
                feedbackId,
                request.reason().trim(),
                ipAddress
        );
        return toResponse(feedback);
    }

    @Transactional
    public FeedbackDtos.FeedbackResponse offline(
            Long adminId,
            Long feedbackId,
            FeedbackDtos.OfflineRequest request,
            String ipAddress
    ) {
        SystemUser admin = requireAdmin(adminId);
        CustomerFeedback feedback = getFeedback(feedbackId);
        requireVersion(feedback.getVersion(), request.version(), "FEEDBACK_VERSION_CONFLICT");
        if (FeedbackPublishStatus.PUBLISHED != feedback.getPublishStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "FEEDBACK_NOT_PUBLISHED",
                    "Only published feedback can be taken offline");
        }
        feedback.setPublishStatus(FeedbackPublishStatus.OFFLINE);
        feedback.setOfflineReason(request.reason().trim());
        feedback.setUpdatedBy(adminId);
        feedback = feedbackRepository.save(feedback);

        if (feedback.getCustomerUserId() != null) {
            notificationService.notifyFeedbackOffline(
                    feedback.getCustomerUserId(),
                    adminId,
                    feedback.getId(),
                    feedback.getOfflineReason()
            );
        }
        notifyFeedbackCreator(feedback, adminId, UserNotificationType.FEEDBACK_OFFLINE,
                "客户评价已下架", "该客户评价已从官网下架。原因：" + feedback.getOfflineReason());
        auditLogService.record(
                adminId,
                admin.getAccountType(),
                "FEEDBACK",
                "OFFLINE_FEEDBACK",
                "CUSTOMER_FEEDBACK",
                feedbackId,
                request.reason().trim(),
                ipAddress
        );
        return toResponse(feedback);
    }

    @Transactional
    public FeedbackDtos.FeedbackResponse upsertReply(
            Long creatorId,
            Long feedbackId,
            FeedbackDtos.UpsertReplyRequest request,
            String ipAddress
    ) {
        SystemUser creator = getActor(creatorId);
        if (!isCreator(creator)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FEEDBACK_REPLY_ACCESS_DENIED",
                    "Only the reviewed creator can reply");
        }
        CustomerFeedback feedback = getFeedback(feedbackId);
        if (!feedback.getCreatorUserId().equals(creatorId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FEEDBACK_REPLY_ACCESS_DENIED",
                    "Only the reviewed creator can reply");
        }
        if (FeedbackPublishStatus.PUBLISHED != feedback.getPublishStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "FEEDBACK_REPLY_NOT_AVAILABLE",
                    "A reply can only be submitted for published feedback");
        }

        FeedbackReply reply = replyRepository.findByFeedbackIdAndDeletedFalse(feedbackId).orElse(null);
        if (reply == null) {
            if (request.version() != null) {
                throw new ApiException(HttpStatus.CONFLICT, "FEEDBACK_REPLY_VERSION_CONFLICT",
                        "Reload the feedback before saving the reply");
            }
            reply = new FeedbackReply();
            reply.setFeedbackId(feedbackId);
            reply.setCreatorUserId(creatorId);
            reply.setCreatedBy(creatorId);
        } else {
            requireVersion(reply.getVersion(), request.version(), "FEEDBACK_REPLY_VERSION_CONFLICT");
            if (FeedbackReviewStatus.APPROVED == reply.getReviewStatus()) {
                throw new ApiException(HttpStatus.CONFLICT, "FEEDBACK_REPLY_PUBLISHED_LOCKED",
                        "An approved reply cannot be edited");
            }
        }
        reply.setContent(request.content().trim());
        reply.setReviewStatus(FeedbackReviewStatus.PENDING);
        reply.setRejectionReason(null);
        reply.setReviewedBy(null);
        reply.setReviewedAt(null);
        reply.setPublishedAt(null);
        reply.setUpdatedBy(creatorId);
        replyRepository.save(reply);

        notificationService.notifyAdmins(
                creatorId,
                UserNotificationType.FEEDBACK_REPLY_SUBMITTED,
                "新的创作者评价回复待审核",
                "创作者提交了新的评价回复，请及时审核。",
                UserNotificationRelatedType.FEEDBACK_REPLY,
                reply.getId()
        );
        auditLogService.record(
                creatorId,
                creator.getAccountType(),
                "FEEDBACK",
                "SUBMIT_FEEDBACK_REPLY",
                "FEEDBACK_REPLY",
                reply.getId(),
                "Feedback reply submitted for review",
                ipAddress
        );
        return toResponse(feedback);
    }

    @Transactional
    public FeedbackDtos.FeedbackResponse approveReply(
            Long adminId,
            Long feedbackId,
            FeedbackDtos.VersionRequest request,
            String ipAddress
    ) {
        SystemUser admin = requireAdmin(adminId);
        CustomerFeedback feedback = getFeedback(feedbackId);
        if (FeedbackPublishStatus.PUBLISHED != feedback.getPublishStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "FEEDBACK_REPLY_NOT_AVAILABLE",
                    "The parent feedback is not published");
        }
        FeedbackReply reply = getReply(feedbackId);
        requireVersion(reply.getVersion(), request.version(), "FEEDBACK_REPLY_VERSION_CONFLICT");
        requirePending(reply);
        reply.setReviewStatus(FeedbackReviewStatus.APPROVED);
        reply.setRejectionReason(null);
        reply.setReviewedBy(adminId);
        reply.setReviewedAt(Instant.now());
        reply.setPublishedAt(Instant.now());
        reply.setUpdatedBy(adminId);
        replyRepository.save(reply);

        notificationService.notifyUser(
                reply.getCreatorUserId(),
                adminId,
                UserNotificationType.FEEDBACK_REPLY_APPROVED,
                "评价回复已通过审核",
                "您提交的评价回复已通过审核并在官网展示。",
                UserNotificationRelatedType.FEEDBACK_REPLY,
                reply.getId()
        );
        auditLogService.record(
                adminId,
                admin.getAccountType(),
                "FEEDBACK",
                "APPROVE_FEEDBACK_REPLY",
                "FEEDBACK_REPLY",
                reply.getId(),
                "Feedback reply approved and published",
                ipAddress
        );
        return toResponse(feedback);
    }

    @Transactional
    public FeedbackDtos.FeedbackResponse rejectReply(
            Long adminId,
            Long feedbackId,
            FeedbackDtos.RejectRequest request,
            String ipAddress
    ) {
        SystemUser admin = requireAdmin(adminId);
        CustomerFeedback feedback = getFeedback(feedbackId);
        FeedbackReply reply = getReply(feedbackId);
        requireVersion(reply.getVersion(), request.version(), "FEEDBACK_REPLY_VERSION_CONFLICT");
        requirePending(reply);
        reply.setReviewStatus(FeedbackReviewStatus.REJECTED);
        reply.setRejectionReason(request.reason().trim());
        reply.setReviewedBy(adminId);
        reply.setReviewedAt(Instant.now());
        reply.setPublishedAt(null);
        reply.setUpdatedBy(adminId);
        replyRepository.save(reply);

        notificationService.notifyUser(
                reply.getCreatorUserId(),
                adminId,
                UserNotificationType.FEEDBACK_REPLY_REJECTED,
                "评价回复未通过审核",
                "您提交的评价回复未通过审核。原因：" + reply.getRejectionReason(),
                UserNotificationRelatedType.FEEDBACK_REPLY,
                reply.getId()
        );
        auditLogService.record(
                adminId,
                admin.getAccountType(),
                "FEEDBACK",
                "REJECT_FEEDBACK_REPLY",
                "FEEDBACK_REPLY",
                reply.getId(),
                request.reason().trim(),
                ipAddress
        );
        return toResponse(feedback);
    }

    private void apply(CustomerFeedback feedback, FeedbackDtos.UpsertFeedbackRequest request) {
        feedback.setProjectId(request.projectId());
        feedback.setCreatorUserId(request.creatorUserId());
        feedback.setCustomerDisplayName(request.customerDisplayName().trim());
        feedback.setRating(request.rating());
        feedback.setContent(request.content().trim());
    }

    private void applyCustomer(
            CustomerFeedback feedback,
            SystemUser customer,
            FeedbackDtos.CustomerFeedbackRequest request
    ) {
        CustomerProfile profile = customerProfileRepository.findById(customer.getId()).orElse(null);
        String displayName = profile == null ? customer.getDisplayName() : profile.getNickname();
        feedback.setProjectId(request.projectId());
        feedback.setCreatorUserId(request.creatorUserId());
        feedback.setCustomerDisplayName(
                displayName == null || displayName.isBlank() ? "匿名客户" : displayName.trim());
        feedback.setRating(request.rating());
        feedback.setContent(request.content().trim());
    }

    private FeedbackDtos.FeedbackResponse toResponse(CustomerFeedback feedback) {
        return toResponse(feedback, false);
    }

    private void notifyNewFeedback(Long actorId, CustomerFeedback feedback, String title) {
        notificationService.notifyAdmins(
                actorId,
                UserNotificationType.CUSTOMER_FEEDBACK_NEW,
                title,
                "收到一条新的客户评价，请及时审核。",
                UserNotificationRelatedType.FEEDBACK,
                feedback.getId()
        );
        if (!actorId.equals(feedback.getCreatorUserId())) {
            notificationService.notifyUser(
                    feedback.getCreatorUserId(),
                    actorId,
                    UserNotificationType.CUSTOMER_FEEDBACK_NEW,
                    "收到新的客户评价",
                    "有客户向您提交了新的评价，审核完成后会在官网展示。",
                    UserNotificationRelatedType.FEEDBACK,
                    feedback.getId()
            );
        }
    }

    private void notifyFeedbackCreator(
            CustomerFeedback feedback,
            Long actorId,
            UserNotificationType type,
            String title,
            String content
    ) {
        if (!actorId.equals(feedback.getCreatorUserId())) {
            notificationService.notifyUser(
                    feedback.getCreatorUserId(),
                    actorId,
                    type,
                    title,
                    content,
                    UserNotificationRelatedType.FEEDBACK,
                    feedback.getId()
            );
        }
    }

    private boolean isCreatorUser(Long userId) {
        return userRepository.findById(userId)
                .map(this::isCreator)
                .orElse(false);
    }

    private FeedbackDtos.FeedbackResponse toResponse(CustomerFeedback feedback, boolean maskHiddenProject) {
        WeddingProject project = projectRepository.findById(feedback.getProjectId()).orElse(null);
        SystemUser creator = userRepository.findById(feedback.getCreatorUserId()).orElse(null);
        FeedbackReply reply = replyRepository.findByFeedbackIdAndDeletedFalse(feedback.getId()).orElse(null);
        return new FeedbackDtos.FeedbackResponse(
                feedback.getId(),
                project == null
                        ? null
                        : (maskHiddenProject ? customerProjectSummary(project) : projectSummary(project)),
                creator == null ? null : creatorSummary(creator),
                feedback.getCustomerDisplayName(),
                feedback.getRating(),
                feedback.getContent(),
                feedback.getReviewStatus(),
                feedback.getRejectionReason(),
                feedback.getReviewedBy(),
                feedback.getReviewedAt(),
                feedback.getPublishStatus(),
                feedback.getPublishedAt(),
                feedback.getOfflineReason(),
                feedback.getSubmittedBy(),
                feedback.getCreatedAt(),
                feedback.getUpdatedAt(),
                feedback.getVersion(),
                reply == null ? null : replyResponse(reply)
        );
    }

    private FeedbackDtos.ProjectSummary projectSummary(WeddingProject project) {
        LinkedHashSet<Long> creatorIds = new LinkedHashSet<>();
        creatorIds.add(project.getCreatedBy());
        projectCreatorRepository.findAllByProjectId(project.getId()).stream()
                .map(ProjectCreator::getId)
                .map(id -> id.getCreatorUserId())
                .forEach(creatorIds::add);
        return new FeedbackDtos.ProjectSummary(
                project.getId(),
                project.getProjectCode(),
                project.getTitle(),
                List.copyOf(creatorIds)
        );
    }

    private FeedbackDtos.ProjectSummary customerProjectSummary(WeddingProject project) {
        FeedbackDtos.ProjectSummary summary = projectSummary(project);
        if (!Boolean.TRUE.equals(project.getDeleted())
                && ContentVisibility.HIDDEN != project.getVisibility()) {
            return summary;
        }
        return new FeedbackDtos.ProjectSummary(
                summary.id(),
                summary.projectCode(),
                null,
                summary.creatorUserIds()
        );
    }

    private FeedbackDtos.CreatorSummary creatorSummary(SystemUser creator) {
        return new FeedbackDtos.CreatorSummary(
                creator.getId(),
                creator.getDisplayName(),
                creator.getProfessionalRoles().stream()
                        .filter(role -> !Boolean.TRUE.equals(role.getDeleted()))
                        .sorted(Comparator.comparing(ProfessionalRole::getSortOrder))
                        .map(ProfessionalRole::getName)
                        .toList()
        );
    }

    private FeedbackDtos.ReplyResponse replyResponse(FeedbackReply reply) {
        return new FeedbackDtos.ReplyResponse(
                reply.getId(),
                reply.getContent(),
                reply.getReviewStatus(),
                reply.getRejectionReason(),
                reply.getReviewedBy(),
                reply.getReviewedAt(),
                reply.getPublishedAt(),
                reply.getCreatedAt(),
                reply.getUpdatedAt(),
                reply.getVersion()
        );
    }

    private void requireSubmissionAccess(SystemUser actor, WeddingProject project, SystemUser creator) {
        boolean creatorParticipates = project.getCreatedBy().equals(creator.getId())
                || projectCreatorRepository.existsByProjectIdAndCreatorUserId(project.getId(), creator.getId());
        if (!creatorParticipates) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FEEDBACK_CREATOR_NOT_IN_PROJECT",
                    "The reviewed creator does not participate in this wedding project");
        }
        if (isCreator(actor)
                && (!actor.getId().equals(creator.getId())
                || (!project.getCreatedBy().equals(actor.getId())
                && !projectCreatorRepository.existsByProjectIdAndCreatorUserId(project.getId(), actor.getId())))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FEEDBACK_ACCESS_DENIED",
                    "Creators can only submit feedback about themselves in their own projects");
        }
    }

    private void requireCustomerSubmissionAccess(
            Long customerId,
            WeddingProject project,
            SystemUser creator
    ) {
        if (!projectCustomerApplicationRepository
                .existsByProjectIdAndCustomerUserIdAndStatusAndDeletedFalse(
                        project.getId(),
                        customerId,
                        "APPROVED")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CUSTOMER_PROJECT_LINK_REQUIRED",
                    "The customer must be linked to this wedding project before submitting feedback");
        }
        boolean creatorParticipates = project.getCreatedBy().equals(creator.getId())
                || projectCreatorRepository.existsByProjectIdAndCreatorUserId(project.getId(), creator.getId());
        if (!creatorParticipates) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FEEDBACK_CREATOR_NOT_IN_PROJECT",
                    "The reviewed creator does not participate in this wedding project");
        }
    }

    private void requireFeedbackEditAccess(SystemUser actor, CustomerFeedback feedback) {
        if (isAdmin(actor) || feedback.getSubmittedBy().equals(actor.getId())) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "FEEDBACK_EDIT_ACCESS_DENIED",
                "Only an administrator or the original submitter can change this feedback");
    }

    private void requirePending(CustomerFeedback feedback) {
        if (FeedbackReviewStatus.PENDING != feedback.getReviewStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "FEEDBACK_NOT_PENDING",
                    "Only pending feedback can be reviewed");
        }
    }

    private void requirePending(FeedbackReply reply) {
        if (FeedbackReviewStatus.PENDING != reply.getReviewStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "FEEDBACK_REPLY_NOT_PENDING",
                    "Only a pending reply can be reviewed");
        }
    }

    private CustomerFeedback getFeedback(Long feedbackId) {
        return feedbackRepository.findByIdAndDeletedFalse(feedbackId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FEEDBACK_NOT_FOUND",
                        "Customer feedback was not found"));
    }

    private FeedbackReply getReply(Long feedbackId) {
        return replyRepository.findByFeedbackIdAndDeletedFalse(feedbackId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FEEDBACK_REPLY_NOT_FOUND",
                        "Feedback reply was not found"));
    }

    private WeddingProject getProject(Long projectId) {
        return projectRepository.findByIdAndDeletedFalse(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND",
                        "Wedding project was not found"));
    }

    private SystemUser getCreator(Long creatorId) {
        SystemUser creator = getActor(creatorId);
        if (!isCreator(creator)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FEEDBACK_CREATOR_INVALID",
                    "The reviewed account must be a creator");
        }
        return creator;
    }

    private SystemUser getActor(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .filter(user -> "ACTIVE".equals(user.getAccountStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_NOT_FOUND",
                        "Account is not available"));
    }

    private SystemUser requireAdmin(Long userId) {
        SystemUser actor = getActor(userId);
        if (!isAdmin(actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FEEDBACK_REVIEW_ACCESS_DENIED",
                    "Only administrators can review customer feedback");
        }
        return actor;
    }

    private SystemUser requireCustomer(Long userId) {
        SystemUser actor = getActor(userId);
        if (!"CUSTOMER".equals(actor.getAccountType())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CUSTOMER_ACCOUNT_REQUIRED",
                    "A customer account is required");
        }
        return actor;
    }

    private void requireFeedbackAccount(SystemUser actor) {
        if (!isAdmin(actor) && !isCreator(actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FEEDBACK_ACCESS_DENIED",
                    "This account cannot manage customer feedback");
        }
    }

    private void requireFeedbackEditAccount(SystemUser actor) {
        if (!isAdmin(actor) && !isCreator(actor) && !"CUSTOMER".equals(actor.getAccountType())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FEEDBACK_ACCESS_DENIED",
                    "This account cannot change customer feedback");
        }
    }

    private void requireVersion(Long currentVersion, Long requestVersion, String code) {
        if (requestVersion == null || !currentVersion.equals(requestVersion)) {
            throw new ApiException(HttpStatus.CONFLICT, code,
                    "The record was updated by another user; reload it before saving");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAGE_INVALID",
                    "Page must be at least 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private boolean isAdmin(SystemUser user) {
        return "ADMIN".equals(user.getAccountType());
    }

    private boolean isCreator(SystemUser user) {
        return "CREATOR".equals(user.getAccountType());
    }

    private boolean isActiveCreator(SystemUser user) {
        return isCreator(user)
                && !Boolean.TRUE.equals(user.getDeleted())
                && "ACTIVE".equals(user.getAccountStatus());
    }
}
