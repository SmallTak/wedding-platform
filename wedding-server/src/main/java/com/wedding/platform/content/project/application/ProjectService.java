package com.wedding.platform.content.project.application;

import com.wedding.platform.content.project.persistence.entity.ProjectCreator;
import com.wedding.platform.content.project.persistence.entity.ProjectCreatorId;
import com.wedding.platform.content.project.persistence.entity.WeddingProject;
import com.wedding.platform.content.project.persistence.repository.ProjectCreatorRepository;
import com.wedding.platform.content.project.persistence.repository.WeddingProjectRepository;
import com.wedding.platform.content.project.web.ProjectDtos;
import com.wedding.platform.content.collection.persistence.repository.WorkCollectionRepository;
import com.wedding.platform.content.review.application.ReviewRevisionService;
import com.wedding.platform.content.review.persistence.entity.ReviewTargetType;
import com.wedding.platform.content.shared.ContentVisibility;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.content.shared.ReviewStatus;
import com.wedding.platform.platform.audit.AuditLogService;
import com.wedding.platform.platform.web.ApiException;
import com.wedding.platform.system.account.persistence.entity.ProfessionalRole;
import com.wedding.platform.system.account.persistence.entity.SystemUser;
import com.wedding.platform.system.account.persistence.repository.SystemUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int PROJECT_CODE_ATTEMPTS = 20;

    private final WeddingProjectRepository projectRepository;
    private final ProjectCreatorRepository projectCreatorRepository;
    private final SystemUserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ReviewRevisionService reviewRevisionService;
    private final WorkCollectionRepository collectionRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ProjectService(
            WeddingProjectRepository projectRepository,
            ProjectCreatorRepository projectCreatorRepository,
            SystemUserRepository userRepository,
            AuditLogService auditLogService,
            ReviewRevisionService reviewRevisionService,
            WorkCollectionRepository collectionRepository
    ) {
        this.projectRepository = projectRepository;
        this.projectCreatorRepository = projectCreatorRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.reviewRevisionService = reviewRevisionService;
        this.collectionRepository = collectionRepository;
    }

    @Transactional
    public ProjectDtos.ProjectResponse createProject(
            Long operatorId,
            ProjectDtos.CreateProjectRequest request,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        requireProjectAccount(actor);

        WeddingProject project = new WeddingProject();
        project.setProjectCode(generateProjectCode());
        applyProjectFields(project, request.title(), request.coupleDisplayName(), request.eventDate(),
                request.regionCode(), request.locationText(), request.description());
        project.setVisibility(ContentVisibility.HIDDEN);
        project.setReviewStatus(ReviewStatus.DRAFT);
        project.setPublishStatus(PublishStatus.UNPUBLISHED);
        project.setCreatedBy(operatorId);
        project.setUpdatedBy(operatorId);
        project = projectRepository.saveAndFlush(project);

        if (isCreator(actor)) {
            ProjectCreator creator = new ProjectCreator();
            creator.setId(new ProjectCreatorId(project.getId(), operatorId));
            creator.setAssignedBy(operatorId);
            projectCreatorRepository.save(creator);
        }

        auditLogService.record(operatorId, actor.getAccountType(), "PROJECT", "CREATE_PROJECT",
                "WEDDING_PROJECT", project.getId(), "Wedding project created", ipAddress);
        return toResponse(project);
    }

    @Transactional(readOnly = true)
    public ProjectDtos.ProjectPageResponse listProjects(Long operatorId, int page, int size, String keyword) {
        SystemUser actor = getActor(operatorId);
        requireProjectAccount(actor);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAGE_INVALID",
                    "Page must be at least 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }

        String normalizedKeyword = trimToNull(keyword);
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<WeddingProject> result = isAdmin(actor)
                ? projectRepository.findAllProjects(normalizedKeyword, pageRequest)
                : projectRepository.findAccessibleProjects(operatorId, normalizedKeyword, pageRequest);
        List<ProjectDtos.ProjectResponse> content = result.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new ProjectDtos.ProjectPageResponse(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public ProjectDtos.ProjectResponse getProject(Long operatorId, Long projectId) {
        SystemUser actor = getActor(operatorId);
        requireProjectAccount(actor);
        WeddingProject project = getProject(projectId);
        requireProjectAccess(actor, project);
        return toResponse(project);
    }

    @Transactional
    public ProjectDtos.ProjectResponse updateProject(
            Long operatorId,
            Long projectId,
            ProjectDtos.UpdateProjectRequest request,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        requireProjectAccount(actor);
        WeddingProject project = getProject(projectId);
        requireProjectAccess(actor, project);
        requireEditable(project);
        requireVersion(project, request.version());

        reviewRevisionService.ensureProjectBaseline(project);
        reviewRevisionService.cancelPendingSubmission(ReviewTargetType.PROJECT, projectId, operatorId);
        applyProjectFields(project, request.title(), request.coupleDisplayName(), request.eventDate(),
                request.regionCode(), request.locationText(), request.description());
        markContentChanged(project);
        project.setUpdatedBy(operatorId);
        project = projectRepository.saveAndFlush(project);

        auditLogService.record(operatorId, actor.getAccountType(), "PROJECT", "UPDATE_PROJECT",
                "WEDDING_PROJECT", projectId, "Wedding project updated", ipAddress);
        return toResponse(project);
    }

    @Transactional
    public void deleteProject(
            Long operatorId,
            Long projectId,
            Long version,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        requireProjectAccount(actor);
        WeddingProject project = getProject(projectId);
        requireProjectAccess(actor, project);
        requireEditable(project);
        requireVersion(project, version);
        if (collectionRepository.existsByProjectIdAndDeletedFalse(projectId)) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_COLLECTIONS_EXIST",
                    "Delete the project's work collections before deleting the project");
        }

        reviewRevisionService.cancelPendingSubmission(ReviewTargetType.PROJECT, projectId, operatorId);
        Instant now = Instant.now();
        project.setDeleted(true);
        project.setDeletedAt(now);
        project.setUpdatedBy(operatorId);
        project.setUpdatedAt(now);
        projectRepository.saveAndFlush(project);
        auditLogService.record(operatorId, actor.getAccountType(), "PROJECT", "DELETE_PROJECT",
                "WEDDING_PROJECT", projectId, "Wedding project logically deleted", ipAddress);
    }

    @Transactional
    public ProjectDtos.ProjectResponse assignCreators(
            Long operatorId,
            Long projectId,
            ProjectDtos.AssignProjectCreatorsRequest request,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        if (!isAdmin(actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "Only administrators can assign project creators");
        }
        WeddingProject project = getProject(projectId);
        requireVersion(project, request.version());

        LinkedHashSet<Long> desiredCreatorIds = new LinkedHashSet<>(request.creatorUserIds());
        if (desiredCreatorIds.size() != request.creatorUserIds().size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROJECT_CREATORS_DUPLICATED",
                    "Creator user ids must not contain duplicates");
        }

        SystemUser owner = userRepository.findById(project.getCreatedBy()).orElse(null);
        if (owner != null && isCreator(owner)) {
            desiredCreatorIds.add(owner.getId());
        }
        LinkedHashSet<Long> creatorIdsToValidate = new LinkedHashSet<>(desiredCreatorIds);
        if (owner != null && isCreator(owner)) {
            creatorIdsToValidate.remove(owner.getId());
        }
        validateActiveCreators(creatorIdsToValidate);

        List<ProjectCreator> existingRelations = projectCreatorRepository.findAllByProjectId(projectId);
        Map<Long, ProjectCreator> existingByCreatorId = existingRelations.stream()
                .collect(Collectors.toMap(relation -> relation.getId().getCreatorUserId(), Function.identity()));
        List<ProjectCreator> removedRelations = existingRelations.stream()
                .filter(relation -> !desiredCreatorIds.contains(relation.getId().getCreatorUserId()))
                .toList();
        projectCreatorRepository.deleteAll(removedRelations);

        Instant assignedAt = Instant.now();
        List<ProjectCreator> newRelations = desiredCreatorIds.stream()
                .filter(creatorId -> !existingByCreatorId.containsKey(creatorId))
                .map(creatorId -> {
                    ProjectCreator relation = new ProjectCreator();
                    relation.setId(new ProjectCreatorId(projectId, creatorId));
                    relation.setAssignedBy(operatorId);
                    relation.setAssignedAt(assignedAt);
                    return relation;
                })
                .toList();
        projectCreatorRepository.saveAll(newRelations);

        project.setUpdatedBy(operatorId);
        project.setUpdatedAt(Instant.now());
        project = projectRepository.saveAndFlush(project);

        auditLogService.record(operatorId, actor.getAccountType(), "PROJECT", "ASSIGN_PROJECT_CREATORS",
                "WEDDING_PROJECT", projectId, "Assigned " + desiredCreatorIds.size() + " project creators", ipAddress);
        return toResponse(project);
    }

    private void applyProjectFields(
            WeddingProject project,
            String title,
            String coupleDisplayName,
            LocalDate eventDate,
            String regionCode,
            String locationText,
            String description
    ) {
        project.setTitle(title.trim());
        project.setCoupleDisplayName(trimToNull(coupleDisplayName));
        project.setEventDate(eventDate);
        project.setRegionCode(regionCode.trim());
        project.setLocationText(locationText.trim());
        project.setDescription(trimToNull(description));
    }

    private void validateActiveCreators(Set<Long> creatorIds) {
        if (creatorIds.isEmpty()) {
            return;
        }
        Map<Long, SystemUser> creators = userRepository.findAllById(creatorIds).stream()
                .collect(Collectors.toMap(SystemUser::getId, Function.identity()));
        boolean invalid = creators.size() != creatorIds.size()
                || creators.values().stream().anyMatch(user -> !isCreator(user)
                || !"ACTIVE".equals(user.getAccountStatus())
                || Boolean.TRUE.equals(user.getDeleted()));
        if (invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROJECT_CREATOR_INVALID",
                    "One or more creator accounts are missing, disabled, or invalid");
        }
    }

    private ProjectDtos.ProjectResponse toResponse(WeddingProject project) {
        List<ProjectCreator> relations = projectCreatorRepository.findAllByProjectId(project.getId());
        Map<Long, SystemUser> users = new HashMap<>();
        userRepository.findAllById(relations.stream()
                        .map(relation -> relation.getId().getCreatorUserId())
                        .toList())
                .forEach(user -> users.put(user.getId(), user));

        List<ProjectDtos.ProjectCreatorResponse> creators = relations.stream()
                .map(relation -> toCreatorResponse(relation, users.get(relation.getId().getCreatorUserId())))
                .filter(response -> response != null)
                .toList();
        return new ProjectDtos.ProjectResponse(
                project.getId(),
                project.getProjectCode(),
                project.getTitle(),
                project.getCoupleDisplayName(),
                project.getEventDate(),
                project.getRegionCode(),
                project.getLocationText(),
                project.getDescription(),
                project.getCoverAssetId(),
                project.getVisibility(),
                project.getReviewStatus(),
                project.getPublishStatus(),
                project.getRejectionReason(),
                project.getSubmittedAt(),
                project.getReviewedAt(),
                project.getReviewedBy(),
                project.getPublishedAt(),
                project.getPublishedBy(),
                project.getOfflineReason(),
                project.getCreatedBy(),
                project.getUpdatedBy(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                project.getVersion(),
                creators
        );
    }

    private ProjectDtos.ProjectCreatorResponse toCreatorResponse(ProjectCreator relation, SystemUser user) {
        if (user == null) {
            return null;
        }
        List<String> professionalRoles = user.getProfessionalRoles().stream()
                .filter(role -> !Boolean.TRUE.equals(role.getDeleted()))
                .sorted(Comparator.comparing(ProfessionalRole::getSortOrder))
                .map(ProfessionalRole::getName)
                .toList();
        return new ProjectDtos.ProjectCreatorResponse(
                user.getId(),
                user.getDisplayName(),
                user.getAvatarPath(),
                user.getAccountStatus(),
                professionalRoles,
                relation.getAssignedAt()
        );
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

    private void requireProjectAccount(SystemUser actor) {
        if (!isAdmin(actor) && !isCreator(actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PROJECT_ACCESS_DENIED",
                    "This account cannot manage wedding projects");
        }
    }

    private void requireProjectAccess(SystemUser actor, WeddingProject project) {
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

    private void markContentChanged(WeddingProject project) {
        project.setReviewStatus(ReviewStatus.DRAFT);
        project.setRejectionReason(null);
        project.setSubmittedAt(null);
        project.setReviewedAt(null);
        project.setReviewedBy(null);
        if (PublishStatus.READY == project.getPublishStatus()) {
            project.setPublishStatus(PublishStatus.UNPUBLISHED);
        }
    }

    private void requireVersion(WeddingProject project, Long requestVersion) {
        if (!project.getVersion().equals(requestVersion)) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_VERSION_CONFLICT",
                    "The project was updated by another user; reload it before saving");
        }
    }

    private String generateProjectCode() {
        String date = DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now(ZoneOffset.UTC));
        for (int attempt = 0; attempt < PROJECT_CODE_ATTEMPTS; attempt++) {
            String code = "WP" + date + String.format("%06d", secureRandom.nextInt(1_000_000));
            if (!projectRepository.existsByProjectCode(code)) {
                return code;
            }
        }
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PROJECT_CODE_UNAVAILABLE",
                "Could not allocate a project code");
    }

    private boolean isAdmin(SystemUser user) {
        return "ADMIN".equals(user.getAccountType());
    }

    private boolean isCreator(SystemUser user) {
        return "CREATOR".equals(user.getAccountType());
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
