package com.wedding.platform.content.collection.application;

import com.wedding.platform.content.collection.persistence.entity.CollectionCreator;
import com.wedding.platform.content.collection.persistence.entity.CollectionCreatorId;
import com.wedding.platform.content.collection.persistence.entity.CollectionTag;
import com.wedding.platform.content.collection.persistence.entity.CollectionTagId;
import com.wedding.platform.content.collection.persistence.entity.ContentCategory;
import com.wedding.platform.content.collection.persistence.entity.ContentTag;
import com.wedding.platform.content.collection.persistence.entity.WorkCollection;
import com.wedding.platform.content.collection.persistence.repository.CollectionCreatorRepository;
import com.wedding.platform.content.collection.persistence.repository.CollectionTagRepository;
import com.wedding.platform.content.collection.persistence.repository.ContentCategoryRepository;
import com.wedding.platform.content.collection.persistence.repository.ContentTagRepository;
import com.wedding.platform.content.collection.persistence.repository.WorkCollectionRepository;
import com.wedding.platform.content.collection.web.CollectionDtos;
import com.wedding.platform.content.media.persistence.entity.CollectionPhoto;
import com.wedding.platform.content.media.persistence.repository.CollectionPhotoRepository;
import com.wedding.platform.content.project.persistence.entity.WeddingProject;
import com.wedding.platform.content.project.persistence.repository.ProjectCreatorRepository;
import com.wedding.platform.content.project.persistence.repository.WeddingProjectRepository;
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

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CollectionService {

    private static final int MAX_PAGE_SIZE = 100;

    private final WorkCollectionRepository collectionRepository;
    private final CollectionCreatorRepository collectionCreatorRepository;
    private final CollectionTagRepository collectionTagRepository;
    private final ContentCategoryRepository categoryRepository;
    private final ContentTagRepository tagRepository;
    private final WeddingProjectRepository projectRepository;
    private final ProjectCreatorRepository projectCreatorRepository;
    private final CollectionPhotoRepository photoRepository;
    private final SystemUserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ReviewRevisionService reviewRevisionService;

    public CollectionService(
            WorkCollectionRepository collectionRepository,
            CollectionCreatorRepository collectionCreatorRepository,
            CollectionTagRepository collectionTagRepository,
            ContentCategoryRepository categoryRepository,
            ContentTagRepository tagRepository,
            WeddingProjectRepository projectRepository,
            ProjectCreatorRepository projectCreatorRepository,
            CollectionPhotoRepository photoRepository,
            SystemUserRepository userRepository,
            AuditLogService auditLogService,
            ReviewRevisionService reviewRevisionService
    ) {
        this.collectionRepository = collectionRepository;
        this.collectionCreatorRepository = collectionCreatorRepository;
        this.collectionTagRepository = collectionTagRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.projectRepository = projectRepository;
        this.projectCreatorRepository = projectCreatorRepository;
        this.photoRepository = photoRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.reviewRevisionService = reviewRevisionService;
    }

    @Transactional(readOnly = true)
    public CollectionDtos.CollectionOptionsResponse options(Long operatorId) {
        SystemUser actor = getActor(operatorId);
        requireCollectionAccount(actor);
        List<CollectionDtos.CategorySummary> categories = categoryRepository
                .findAllByStatusAndDeletedFalseOrderBySortOrderAscCreatedAtAsc("ACTIVE").stream()
                .map(category -> new CollectionDtos.CategorySummary(category.getId(), category.getName()))
                .toList();
        List<CollectionDtos.TagSummary> tags = tagRepository
                .findAllByStatusAndDeletedFalseOrderBySortOrderAscCreatedAtAsc("ACTIVE").stream()
                .map(tag -> new CollectionDtos.TagSummary(tag.getId(), tag.getName()))
                .toList();
        return new CollectionDtos.CollectionOptionsResponse(categories, tags);
    }

    @Transactional
    public CollectionDtos.CollectionResponse createCollection(
            Long operatorId,
            CollectionDtos.CreateCollectionRequest request,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        requireCollectionAccount(actor);
        WeddingProject project = request.projectId() == null
                ? null
                : getAccessibleProject(actor, request.projectId());
        ContentCategory category = getSelectableCategory(request.categoryId(), null);
        LinkedHashSet<Long> tagIds = distinctTagIds(request.tagIds());
        loadSelectableTags(tagIds, Set.of());

        WorkCollection collection = new WorkCollection();
        applyFields(collection, project, request.title(), request.description(), category);
        collection.setVisibility(ContentVisibility.HIDDEN);
        collection.setReviewStatus(ReviewStatus.DRAFT);
        collection.setPublishStatus(PublishStatus.UNPUBLISHED);
        collection.setSortOrder(0);
        collection.setFeatured(false);
        collection.setPinned(false);
        collection.setCreatedBy(operatorId);
        collection.setUpdatedBy(operatorId);
        collection = collectionRepository.saveAndFlush(collection);

        replaceTags(collection.getId(), tagIds);
        if (isCreator(actor)) {
            CollectionCreator creator = new CollectionCreator();
            creator.setId(new CollectionCreatorId(collection.getId(), operatorId));
            collectionCreatorRepository.save(creator);
        }

        auditLogService.record(operatorId, actor.getAccountType(), "COLLECTION", "CREATE_COLLECTION",
                "WORK_COLLECTION", collection.getId(), "Work collection created", ipAddress);
        return toResponse(collection);
    }

    @Transactional(readOnly = true)
    public CollectionDtos.CollectionPageResponse listCollections(
            Long operatorId,
            int page,
            int size,
            String keyword,
            Long projectId,
            Long categoryId
    ) {
        SystemUser actor = getActor(operatorId);
        requireCollectionAccount(actor);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAGE_INVALID",
                    "Page must be at least 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        PageRequest pageRequest = PageRequest.of(page, size);
        String normalizedKeyword = trimToNull(keyword);
        Page<WorkCollection> result = isAdmin(actor)
                ? collectionRepository.findAllCollections(projectId, categoryId, normalizedKeyword, pageRequest)
                : collectionRepository.findAccessibleCollections(
                        operatorId, projectId, categoryId, normalizedKeyword, pageRequest);
        return new CollectionDtos.CollectionPageResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public CollectionDtos.CollectionResponse getCollection(Long operatorId, Long collectionId) {
        SystemUser actor = getActor(operatorId);
        requireCollectionAccount(actor);
        WorkCollection collection = getCollection(collectionId);
        requireCollectionAccess(actor, collection);
        return toResponse(collection);
    }

    @Transactional
    public CollectionDtos.CollectionResponse updateCollection(
            Long operatorId,
            Long collectionId,
            CollectionDtos.UpdateCollectionRequest request,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        requireCollectionAccount(actor);
        WorkCollection collection = getCollection(collectionId);
        requireCollectionAccess(actor, collection);
        requireEditable(collection);
        requireVersion(collection, request.version());

        WeddingProject project = request.projectId() == null
                ? null
                : getAccessibleProject(actor, request.projectId());
        List<CollectionCreator> currentCreators = collectionCreatorRepository.findAllByCollectionId(collectionId);
        if (project != null) {
            requireProjectParticipants(project, currentCreators.stream()
                    .map(relation -> relation.getId().getCreatorUserId())
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }

        ContentCategory category = getSelectableCategory(request.categoryId(), collection.getCategoryId());
        LinkedHashSet<Long> existingTagIds = collectionTagRepository.findAllByCollectionId(collectionId).stream()
                .map(relation -> relation.getId().getTagId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<Long> tagIds = distinctTagIds(request.tagIds());
        loadSelectableTags(tagIds, existingTagIds);

        if (!collectionDetailsChanged(collection, project, request, category, existingTagIds, tagIds)) {
            return toResponse(collection);
        }
        List<CollectionPhoto> activePhotos = photoRepository
                .findAllByCollectionIdAndDeletedFalseOrderBySortOrderAscIdAsc(collectionId);
        reviewRevisionService.ensureCollectionBaseline(collection, activePhotos);
        reviewRevisionService.cancelPendingSubmission(ReviewTargetType.COLLECTION, collectionId, operatorId);
        applyFields(collection, project, request.title(), request.description(), category);
        replaceTags(collectionId, tagIds);
        markContentChanged(collection, operatorId);
        collection.setUpdatedBy(operatorId);
        collection.setUpdatedAt(Instant.now());
        collection = collectionRepository.saveAndFlush(collection);

        auditLogService.record(operatorId, actor.getAccountType(), "COLLECTION", "UPDATE_COLLECTION",
                "WORK_COLLECTION", collectionId, "Work collection updated", ipAddress);
        return toResponse(collection);
    }

    @Transactional
    public void deleteCollection(
            Long operatorId,
            Long collectionId,
            Long version,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        requireCollectionAccount(actor);
        WorkCollection collection = getCollection(collectionId);
        requireCollectionAccess(actor, collection);
        requireEditable(collection);
        requireVersion(collection, version);

        reviewRevisionService.cancelPendingSubmission(ReviewTargetType.COLLECTION, collectionId, operatorId);
        Instant now = Instant.now();
        collection.setDeleted(true);
        collection.setDeletedAt(now);
        collection.setUpdatedBy(operatorId);
        collection.setUpdatedAt(now);
        collectionRepository.saveAndFlush(collection);
        auditLogService.record(operatorId, actor.getAccountType(), "COLLECTION", "DELETE_COLLECTION",
                "WORK_COLLECTION", collectionId, "Work collection logically deleted", ipAddress);
    }

    @Transactional
    public CollectionDtos.CollectionResponse assignCreators(
            Long operatorId,
            Long collectionId,
            CollectionDtos.AssignCollectionCreatorsRequest request,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        if (!isAdmin(actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED",
                    "Only administrators can assign collection creators");
        }
        WorkCollection collection = getCollection(collectionId);
        requireEditable(collection);
        requireVersion(collection, request.version());

        LinkedHashSet<Long> desiredCreatorIds = new LinkedHashSet<>(request.creatorUserIds());
        if (desiredCreatorIds.size() != request.creatorUserIds().size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COLLECTION_CREATORS_DUPLICATED",
                    "Creator user ids must not contain duplicates");
        }

        SystemUser owner = userRepository.findById(collection.getCreatedBy()).orElse(null);
        if (owner != null && isCreator(owner)) {
            desiredCreatorIds.add(owner.getId());
        }
        LinkedHashSet<Long> creatorIdsToValidate = new LinkedHashSet<>(desiredCreatorIds);
        if (owner != null && isCreator(owner)) {
            creatorIdsToValidate.remove(owner.getId());
        }
        validateActiveCreators(creatorIdsToValidate);

        if (collection.getProjectId() != null) {
            WeddingProject project = projectRepository.findByIdAndDeletedFalse(collection.getProjectId())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "COLLECTION_PROJECT_INVALID",
                            "The linked wedding project is not available"));
            requireProjectParticipants(project, desiredCreatorIds);
        }

        List<CollectionCreator> existingRelations = collectionCreatorRepository.findAllByCollectionId(collectionId);
        Map<Long, CollectionCreator> existingByCreatorId = existingRelations.stream()
                .collect(Collectors.toMap(relation -> relation.getId().getCreatorUserId(), Function.identity()));
        if (existingByCreatorId.keySet().equals(desiredCreatorIds)) {
            return toResponse(collection);
        }

        List<CollectionPhoto> activePhotos = photoRepository
                .findAllByCollectionIdAndDeletedFalseOrderBySortOrderAscIdAsc(collectionId);
        reviewRevisionService.ensureCollectionBaseline(collection, activePhotos);
        reviewRevisionService.cancelPendingSubmission(ReviewTargetType.COLLECTION, collectionId, operatorId);
        collectionCreatorRepository.deleteAll(existingRelations.stream()
                .filter(relation -> !desiredCreatorIds.contains(relation.getId().getCreatorUserId()))
                .toList());

        Instant joinedAt = Instant.now();
        collectionCreatorRepository.saveAll(desiredCreatorIds.stream()
                .filter(creatorId -> !existingByCreatorId.containsKey(creatorId))
                .map(creatorId -> {
                    CollectionCreator relation = new CollectionCreator();
                    relation.setId(new CollectionCreatorId(collectionId, creatorId));
                    relation.setJoinedAt(joinedAt);
                    return relation;
                })
                .toList());

        markContentChanged(collection, operatorId);
        collection.setUpdatedBy(operatorId);
        collection.setUpdatedAt(Instant.now());
        collection = collectionRepository.saveAndFlush(collection);

        auditLogService.record(operatorId, actor.getAccountType(), "COLLECTION", "ASSIGN_COLLECTION_CREATORS",
                "WORK_COLLECTION", collectionId,
                "Assigned " + desiredCreatorIds.size() + " collection creators", ipAddress);
        return toResponse(collection);
    }

    private void applyFields(
            WorkCollection collection,
            WeddingProject project,
            String title,
            String description,
            ContentCategory category
    ) {
        collection.setProjectId(project == null ? null : project.getId());
        collection.setTitle(title.trim());
        collection.setDescription(trimToNull(description));
        collection.setCategoryId(category.getId());
    }

    private boolean collectionDetailsChanged(
            WorkCollection collection,
            WeddingProject project,
            CollectionDtos.UpdateCollectionRequest request,
            ContentCategory category,
            Set<Long> existingTagIds,
            Set<Long> requestedTagIds
    ) {
        Long projectId = project == null ? null : project.getId();
        return !Objects.equals(collection.getProjectId(), projectId)
                || !Objects.equals(collection.getTitle(), request.title().trim())
                || !Objects.equals(collection.getDescription(), trimToNull(request.description()))
                || !Objects.equals(collection.getCategoryId(), category.getId())
                || !existingTagIds.equals(requestedTagIds);
    }

    private void replaceTags(Long collectionId, LinkedHashSet<Long> desiredTagIds) {
        List<CollectionTag> existingRelations = collectionTagRepository.findAllByCollectionId(collectionId);
        Map<Long, CollectionTag> existingByTagId = existingRelations.stream()
                .collect(Collectors.toMap(relation -> relation.getId().getTagId(), Function.identity()));
        collectionTagRepository.deleteAll(existingRelations.stream()
                .filter(relation -> !desiredTagIds.contains(relation.getId().getTagId()))
                .toList());
        collectionTagRepository.saveAll(desiredTagIds.stream()
                .filter(tagId -> !existingByTagId.containsKey(tagId))
                .map(tagId -> {
                    CollectionTag relation = new CollectionTag();
                    relation.setId(new CollectionTagId(collectionId, tagId));
                    return relation;
                })
                .toList());
    }

    private ContentCategory getSelectableCategory(Long categoryId, Long currentCategoryId) {
        ContentCategory category = categoryRepository.findByIdAndDeletedFalse(categoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "COLLECTION_CATEGORY_INVALID",
                        "The selected content category is not available"));
        if (!"ACTIVE".equals(category.getStatus()) && !categoryId.equals(currentCategoryId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COLLECTION_CATEGORY_INVALID",
                    "The selected content category is disabled");
        }
        return category;
    }

    private LinkedHashSet<Long> distinctTagIds(List<Long> requestedTagIds) {
        LinkedHashSet<Long> tagIds = new LinkedHashSet<>(requestedTagIds);
        if (tagIds.size() != requestedTagIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COLLECTION_TAGS_DUPLICATED",
                    "Tag ids must not contain duplicates");
        }
        return tagIds;
    }

    private void loadSelectableTags(Set<Long> tagIds, Set<Long> currentTagIds) {
        if (tagIds.isEmpty()) {
            return;
        }
        Map<Long, ContentTag> tags = tagRepository.findAllById(tagIds).stream()
                .filter(tag -> !Boolean.TRUE.equals(tag.getDeleted()))
                .collect(Collectors.toMap(ContentTag::getId, Function.identity()));
        boolean invalid = tags.size() != tagIds.size()
                || tags.values().stream().anyMatch(tag -> !"ACTIVE".equals(tag.getStatus())
                && !currentTagIds.contains(tag.getId()));
        if (invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COLLECTION_TAG_INVALID",
                    "One or more selected tags are missing or disabled");
        }
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
            throw new ApiException(HttpStatus.BAD_REQUEST, "COLLECTION_CREATOR_INVALID",
                    "One or more creator accounts are missing, disabled, or invalid");
        }
    }

    private WeddingProject getAccessibleProject(SystemUser actor, Long projectId) {
        WeddingProject project = projectRepository.findByIdAndDeletedFalse(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "COLLECTION_PROJECT_INVALID",
                        "The linked wedding project is not available"));
        if (!isAdmin(actor)
                && !project.getCreatedBy().equals(actor.getId())
                && !projectCreatorRepository.existsByProjectIdAndCreatorUserId(projectId, actor.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COLLECTION_PROJECT_ACCESS_DENIED",
                    "You do not participate in the linked wedding project");
        }
        return project;
    }

    private void requireProjectParticipants(WeddingProject project, Set<Long> creatorIds) {
        boolean invalid = creatorIds.stream().anyMatch(creatorId ->
                !project.getCreatedBy().equals(creatorId)
                        && !projectCreatorRepository.existsByProjectIdAndCreatorUserId(project.getId(), creatorId));
        if (invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COLLECTION_CREATOR_NOT_IN_PROJECT",
                    "Every collection creator must participate in the linked wedding project");
        }
    }

    private CollectionDtos.CollectionResponse toResponse(WorkCollection collection) {
        ContentCategory category = categoryRepository.findById(collection.getCategoryId()).orElse(null);
        CollectionDtos.CategorySummary categorySummary = category == null
                ? null
                : new CollectionDtos.CategorySummary(category.getId(), category.getName());

        List<CollectionTag> tagRelations = collectionTagRepository.findAllByCollectionId(collection.getId());
        Map<Long, ContentTag> tagsById = tagRepository.findAllById(tagRelations.stream()
                        .map(relation -> relation.getId().getTagId())
                        .toList())
                .stream()
                .collect(Collectors.toMap(ContentTag::getId, Function.identity()));
        List<CollectionDtos.TagSummary> tags = tagRelations.stream()
                .map(relation -> tagsById.get(relation.getId().getTagId()))
                .filter(tag -> tag != null)
                .sorted(Comparator.comparing(ContentTag::getSortOrder).thenComparing(ContentTag::getId))
                .map(tag -> new CollectionDtos.TagSummary(tag.getId(), tag.getName()))
                .toList();

        List<CollectionCreator> creatorRelations = collectionCreatorRepository.findAllByCollectionId(collection.getId());
        Map<Long, SystemUser> usersById = new HashMap<>();
        userRepository.findAllById(creatorRelations.stream()
                        .map(relation -> relation.getId().getCreatorUserId())
                        .toList())
                .forEach(user -> usersById.put(user.getId(), user));
        List<CollectionDtos.CreatorResponse> creators = creatorRelations.stream()
                .map(relation -> toCreatorResponse(relation, usersById.get(relation.getId().getCreatorUserId())))
                .filter(response -> response != null)
                .toList();

        CollectionDtos.ProjectSummary projectSummary = collection.getProjectId() == null
                ? null
                : projectRepository.findById(collection.getProjectId())
                .map(project -> new CollectionDtos.ProjectSummary(
                        project.getId(), project.getProjectCode(), project.getTitle()))
                .orElse(null);

        return new CollectionDtos.CollectionResponse(
                collection.getId(),
                projectSummary,
                collection.getTitle(),
                collection.getDescription(),
                categorySummary,
                tags,
                collection.getCoverPhotoId(),
                collection.getVisibility(),
                collection.getReviewStatus(),
                collection.getPublishStatus(),
                collection.getRejectionReason(),
                collection.getSubmittedAt(),
                collection.getReviewedAt(),
                collection.getReviewedBy(),
                collection.getPublishedAt(),
                collection.getPublishedBy(),
                collection.getOfflineReason(),
                collection.getSortOrder(),
                collection.getFeatured(),
                collection.getPinned(),
                collection.getCreatedBy(),
                collection.getUpdatedBy(),
                collection.getCreatedAt(),
                collection.getUpdatedAt(),
                collection.getVersion(),
                creators
        );
    }

    private CollectionDtos.CreatorResponse toCreatorResponse(CollectionCreator relation, SystemUser user) {
        if (user == null) {
            return null;
        }
        List<String> professionalRoles = user.getProfessionalRoles().stream()
                .filter(role -> !Boolean.TRUE.equals(role.getDeleted()))
                .sorted(Comparator.comparing(ProfessionalRole::getSortOrder))
                .map(ProfessionalRole::getName)
                .toList();
        return new CollectionDtos.CreatorResponse(
                user.getId(),
                user.getDisplayName(),
                user.getAvatarPath(),
                user.getAccountStatus(),
                professionalRoles,
                relation.getJoinedAt()
        );
    }

    private WorkCollection getCollection(Long collectionId) {
        return collectionRepository.findByIdAndDeletedFalse(collectionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COLLECTION_NOT_FOUND",
                        "Work collection was not found"));
    }

    private SystemUser getActor(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .filter(user -> "ACTIVE".equals(user.getAccountStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_NOT_FOUND",
                        "Account is not available"));
    }

    private void requireCollectionAccount(SystemUser actor) {
        if (!isAdmin(actor) && !isCreator(actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COLLECTION_ACCESS_DENIED",
                    "This account cannot manage work collections");
        }
    }

    private void requireCollectionAccess(SystemUser actor, WorkCollection collection) {
        if (isAdmin(actor)
                || collection.getCreatedBy().equals(actor.getId())
                || collectionCreatorRepository.existsByCollectionIdAndCreatorUserId(
                        collection.getId(), actor.getId())) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "COLLECTION_ACCESS_DENIED",
                "You do not participate in this work collection");
    }

    private void requireEditable(WorkCollection collection) {
        if (PublishStatus.PUBLISHED == collection.getPublishStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "COLLECTION_PUBLISHED_LOCKED",
                    "Published collection details are locked until the collection is taken offline");
        }
    }

    private void markContentChanged(WorkCollection collection, Long operatorId) {
        List<CollectionPhoto> photos = photoRepository
                .findAllByCollectionIdAndDeletedFalseOrderBySortOrderAscIdAsc(collection.getId());
        photos.stream()
                .filter(photo -> ReviewStatus.PENDING == photo.getReviewStatus())
                .forEach(photo -> {
                    photo.setReviewStatus(ReviewStatus.DRAFT);
                    photo.setSubmittedAt(null);
                    photo.setUpdatedBy(operatorId);
                });
        photoRepository.saveAll(photos);
        collection.setReviewStatus(ReviewStatus.DRAFT);
        collection.setRejectionReason(null);
        collection.setSubmittedAt(null);
        collection.setReviewedAt(null);
        collection.setReviewedBy(null);
        if (PublishStatus.READY == collection.getPublishStatus()) {
            collection.setPublishStatus(PublishStatus.UNPUBLISHED);
        }
    }

    private void requireVersion(WorkCollection collection, Long requestVersion) {
        if (!collection.getVersion().equals(requestVersion)) {
            throw new ApiException(HttpStatus.CONFLICT, "COLLECTION_VERSION_CONFLICT",
                    "The collection was updated by another user; reload it before saving");
        }
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
