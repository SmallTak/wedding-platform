package com.wedding.platform.content.review.application;

import com.wedding.platform.content.collection.application.CollectionService;
import com.wedding.platform.content.collection.persistence.entity.ContentCategory;
import com.wedding.platform.content.collection.persistence.entity.WorkCollection;
import com.wedding.platform.content.collection.persistence.repository.ContentCategoryRepository;
import com.wedding.platform.content.collection.persistence.repository.WorkCollectionRepository;
import com.wedding.platform.content.media.application.CollectionPhotoService;
import com.wedding.platform.content.media.persistence.entity.CollectionPhoto;
import com.wedding.platform.content.media.persistence.entity.MediaAsset;
import com.wedding.platform.content.media.persistence.repository.CollectionPhotoRepository;
import com.wedding.platform.content.media.persistence.repository.MediaAssetRepository;
import com.wedding.platform.content.project.persistence.repository.WeddingProjectRepository;
import com.wedding.platform.content.publication.application.PublicContentAccessService;
import com.wedding.platform.content.review.web.ReviewDtos;
import com.wedding.platform.content.review.persistence.entity.ReviewItem;
import com.wedding.platform.content.review.persistence.entity.ReviewItemStatus;
import com.wedding.platform.content.review.persistence.entity.ReviewItemType;
import com.wedding.platform.content.review.persistence.entity.ReviewTargetType;
import com.wedding.platform.content.shared.ContentVisibility;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.content.shared.ReviewStatus;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
public class CollectionReviewService {

    private static final int MAX_PAGE_SIZE = 100;

    private final WorkCollectionRepository collectionRepository;
    private final CollectionPhotoRepository photoRepository;
    private final MediaAssetRepository assetRepository;
    private final ContentCategoryRepository categoryRepository;
    private final WeddingProjectRepository projectRepository;
    private final SystemUserRepository userRepository;
    private final com.wedding.platform.content.collection.persistence.repository.CollectionCreatorRepository
            collectionCreatorRepository;
    private final CollectionService collectionService;
    private final CollectionPhotoService photoService;
    private final AuditLogService auditLogService;
    private final ReviewRevisionService reviewRevisionService;
    private final PublicContentAccessService contentAccessService;

    public CollectionReviewService(
            WorkCollectionRepository collectionRepository,
            CollectionPhotoRepository photoRepository,
            MediaAssetRepository assetRepository,
            ContentCategoryRepository categoryRepository,
            WeddingProjectRepository projectRepository,
            SystemUserRepository userRepository,
            com.wedding.platform.content.collection.persistence.repository.CollectionCreatorRepository
                    collectionCreatorRepository,
            CollectionService collectionService,
            CollectionPhotoService photoService,
            AuditLogService auditLogService,
            ReviewRevisionService reviewRevisionService,
            PublicContentAccessService contentAccessService
    ) {
        this.collectionRepository = collectionRepository;
        this.photoRepository = photoRepository;
        this.assetRepository = assetRepository;
        this.categoryRepository = categoryRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.collectionCreatorRepository = collectionCreatorRepository;
        this.collectionService = collectionService;
        this.photoService = photoService;
        this.auditLogService = auditLogService;
        this.reviewRevisionService = reviewRevisionService;
        this.contentAccessService = contentAccessService;
    }

    @Transactional
    public ReviewDtos.ReviewDetailResponse submit(
            Long operatorId,
            Long collectionId,
            ReviewDtos.VersionRequest request,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        WorkCollection collection = getCollection(collectionId);
        requireAccess(actor, collection);
        requireEditable(collection);
        requireVersion(collection, request.version());
        if (ReviewStatus.PENDING == collection.getReviewStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "COLLECTION_ALREADY_PENDING",
                    "The collection is already pending review");
        }

        List<CollectionPhoto> photos = activePhotos(collectionId);
        requireSubmissionReady(collection, photos);
        reviewRevisionService.submitCollection(collection, photos, operatorId);
        Instant now = Instant.now();
        for (CollectionPhoto photo : photos) {
            if (ReviewStatus.DRAFT == photo.getReviewStatus()
                    || ReviewStatus.REJECTED == photo.getReviewStatus()) {
                photo.setReviewStatus(ReviewStatus.PENDING);
                photo.setRejectionReason(null);
                photo.setSubmittedAt(now);
                photo.setReviewedAt(null);
                photo.setReviewedBy(null);
                photo.setUpdatedBy(operatorId);
            }
        }
        photoRepository.saveAll(photos);

        List<ReviewItem> currentFields = reviewRevisionService.currentItems(
                ReviewTargetType.COLLECTION, collectionId, ReviewItemType.FIELD);
        ReviewItem rejectedField = currentFields.stream()
                .filter(item -> ReviewItemStatus.REJECTED == item.getStatus())
                .findFirst()
                .orElse(null);
        collection.setReviewStatus(rejectedField == null
                ? ReviewStatus.PENDING
                : ReviewStatus.PARTIALLY_REJECTED);
        collection.setRejectionReason(rejectedField == null ? null : rejectedField.getRejectionReason());
        collection.setSubmittedAt(now);
        collection.setReviewedAt(null);
        collection.setReviewedBy(null);
        if (PublishStatus.READY == collection.getPublishStatus()) {
            collection.setPublishStatus(PublishStatus.UNPUBLISHED);
        }
        saveCollection(collection, operatorId);
        auditLogService.record(operatorId, actor.getAccountType(), "COLLECTION_REVIEW", "SUBMIT_COLLECTION",
                "WORK_COLLECTION", collectionId, "Collection submitted for review", ipAddress);
        return detail(operatorId, collectionId);
    }

    @Transactional(readOnly = true)
    public ReviewDtos.ReviewQueueResponse list(
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
        Page<WorkCollection> result = collectionRepository.findWorkflowCollections(
                reviewStatus,
                publishStatus,
                trimToNull(keyword),
                ReviewStatus.PENDING,
                ReviewStatus.PARTIALLY_REJECTED,
                PublishStatus.READY,
                PageRequest.of(page, size)
        );
        return new ReviewDtos.ReviewQueueResponse(
                result.getContent().stream().map(this::toQueueItem).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional
    public ReviewDtos.ReviewDetailResponse detail(Long operatorId, Long collectionId) {
        SystemUser actor = getActor(operatorId);
        WorkCollection collection = getCollection(collectionId);
        if (!"ADMIN".equals(actor.getAccountType())) {
            requireAccess(actor, collection);
        }
        reviewRevisionService.ensureCollectionBaseline(collection, activePhotos(collectionId));
        return new ReviewDtos.ReviewDetailResponse(
                collectionService.getCollection(operatorId, collectionId),
                photoService.listPhotos(operatorId, collectionId),
                reviewRevisionService.history(ReviewTargetType.COLLECTION, collectionId)
        );
    }

    @Transactional
    public ReviewDtos.ReviewDetailResponse reviewPhotos(
            Long operatorId,
            Long collectionId,
            ReviewDtos.ReviewPhotosRequest request,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        requireAdmin(actor);
        WorkCollection collection = getCollection(collectionId);
        requireVersion(collection, request.version());
        reviewRevisionService.ensureCollectionBaseline(collection, activePhotos(collectionId));
        if (ReviewStatus.PENDING != collection.getReviewStatus()
                && ReviewStatus.PARTIALLY_REJECTED != collection.getReviewStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "COLLECTION_NOT_PENDING",
                    "Only a pending collection can receive photo review decisions");
        }
        if (ReviewDtos.PhotoDecision.REJECT == request.decision() && !StringUtils.hasText(request.reason())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REJECTION_REASON_REQUIRED",
                    "A rejection reason is required");
        }
        if (new HashSet<>(request.photoIds()).size() != request.photoIds().size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHOTO_REVIEW_SELECTION_INVALID",
                    "Photo ids must not contain duplicates");
        }

        Map<Long, CollectionPhoto> photosById = new HashMap<>();
        activePhotos(collectionId).forEach(photo -> photosById.put(photo.getId(), photo));
        List<CollectionPhoto> selected = request.photoIds().stream().map(photosById::get).toList();
        if (selected.stream().anyMatch(photo -> photo == null || ReviewStatus.PENDING != photo.getReviewStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHOTO_REVIEW_SELECTION_INVALID",
                    "Every selected photo must be pending review in this collection");
        }

        Instant now = Instant.now();
        ReviewStatus targetStatus = ReviewDtos.PhotoDecision.APPROVE == request.decision()
                ? ReviewStatus.APPROVED
                : ReviewStatus.REJECTED;
        String reason = targetStatus == ReviewStatus.REJECTED ? request.reason().trim() : null;
        Map<Long, ReviewItem> reviewItemsByPhotoId = reviewRevisionService.currentItems(
                        ReviewTargetType.COLLECTION,
                        collectionId,
                        ReviewItemType.PHOTO
                ).stream()
                .collect(java.util.stream.Collectors.toMap(ReviewItem::getBusinessId, item -> item));
        List<Long> reviewItemIds = selected.stream()
                .map(CollectionPhoto::getId)
                .map(reviewItemsByPhotoId::get)
                .filter(item -> item != null && ReviewItemStatus.PENDING == item.getStatus())
                .map(ReviewItem::getId)
                .toList();
        if (reviewItemIds.size() != selected.size()) {
            throw new ApiException(HttpStatus.CONFLICT, "PHOTO_REVIEW_REVISION_MISSING",
                    "Reload the collection because its current photo review revision is incomplete");
        }
        reviewRevisionService.reviewItems(
                ReviewTargetType.COLLECTION,
                collectionId,
                ReviewItemType.PHOTO,
                reviewItemIds,
                ReviewDtos.PhotoDecision.APPROVE == request.decision()
                        ? ReviewDtos.ReviewDecision.APPROVE
                        : ReviewDtos.ReviewDecision.REJECT,
                reason,
                operatorId
        );
        for (CollectionPhoto photo : selected) {
            photo.setReviewStatus(targetStatus);
            photo.setRejectionReason(reason);
            photo.setReviewedAt(now);
            photo.setReviewedBy(operatorId);
            photo.setUpdatedBy(operatorId);
        }
        photoRepository.saveAll(selected);
        recalculateCollection(collection, operatorId, now);
        auditLogService.record(operatorId, actor.getAccountType(), "COLLECTION_REVIEW",
                targetStatus == ReviewStatus.APPROVED ? "APPROVE_PHOTOS" : "REJECT_PHOTOS",
                "WORK_COLLECTION", collectionId,
                (targetStatus == ReviewStatus.APPROVED ? "Approved " : "Rejected ")
                        + selected.size() + " collection photos",
                ipAddress);
        return detail(operatorId, collectionId);
    }

    @Transactional
    public ReviewDtos.ReviewDetailResponse reviewFields(
            Long operatorId,
            Long collectionId,
            ReviewDtos.ReviewFieldsRequest request,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        requireAdmin(actor);
        WorkCollection collection = getCollection(collectionId);
        requireVersion(collection, request.version());
        reviewRevisionService.ensureCollectionBaseline(collection, activePhotos(collectionId));
        if (ReviewStatus.PENDING != collection.getReviewStatus()
                && ReviewStatus.PARTIALLY_REJECTED != collection.getReviewStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "COLLECTION_NOT_PENDING",
                    "Only a submitted collection can receive field review decisions");
        }
        reviewRevisionService.reviewItems(
                ReviewTargetType.COLLECTION,
                collectionId,
                ReviewItemType.FIELD,
                request.reviewItemIds(),
                request.decision(),
                request.reason(),
                operatorId
        );
        recalculateCollection(collection, operatorId, Instant.now());
        auditLogService.record(operatorId, actor.getAccountType(), "COLLECTION_REVIEW",
                request.decision() == ReviewDtos.ReviewDecision.APPROVE
                        ? "APPROVE_COLLECTION_FIELDS"
                        : "REJECT_COLLECTION_FIELDS",
                "WORK_COLLECTION", collectionId,
                request.decision() == ReviewDtos.ReviewDecision.APPROVE
                        ? "Approved " + request.reviewItemIds().size() + " collection fields"
                        : request.reason().trim(),
                ipAddress);
        return detail(operatorId, collectionId);
    }

    @Transactional
    public ReviewDtos.ReviewDetailResponse approveCollection(
            Long operatorId,
            Long collectionId,
            ReviewDtos.VersionRequest request,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        requireAdmin(actor);
        WorkCollection collection = getCollection(collectionId);
        requireVersion(collection, request.version());
        List<CollectionPhoto> photos = activePhotos(collectionId);
        requireSubmissionReady(collection, photos);
        reviewRevisionService.ensureCollectionBaseline(collection, photos);
        if (photos.stream().anyMatch(photo -> ReviewStatus.APPROVED != photo.getReviewStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "COLLECTION_PHOTOS_NOT_APPROVED",
                    "Every active photo must be approved before the collection can be approved");
        }
        List<ReviewItem> pendingFields = reviewRevisionService.currentItems(
                        ReviewTargetType.COLLECTION, collectionId, ReviewItemType.FIELD).stream()
                .filter(item -> ReviewItemStatus.PENDING == item.getStatus())
                .toList();
        if (!pendingFields.isEmpty()) {
            reviewRevisionService.reviewAllPendingFields(
                    ReviewTargetType.COLLECTION,
                    collectionId,
                    ReviewDtos.ReviewDecision.APPROVE,
                    null,
                    operatorId
            );
        }
        if (!reviewRevisionService.allRequiredFieldsApproved(
                ReviewTargetType.COLLECTION,
                collectionId,
                ReviewRevisionService.COLLECTION_FIELD_KEYS)) {
            throw new ApiException(HttpStatus.CONFLICT, "COLLECTION_FIELDS_NOT_APPROVED",
                    "Every current collection field must be approved before the collection can be approved");
        }
        Instant now = Instant.now();
        approveCollectionState(collection, operatorId, now);
        auditLogService.record(operatorId, actor.getAccountType(), "COLLECTION_REVIEW", "APPROVE_COLLECTION",
                "WORK_COLLECTION", collectionId, "Collection metadata approved", ipAddress);
        return detail(operatorId, collectionId);
    }

    @Transactional
    public ReviewDtos.ReviewDetailResponse rejectCollection(
            Long operatorId,
            Long collectionId,
            ReviewDtos.RejectCollectionRequest request,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        requireAdmin(actor);
        WorkCollection collection = getCollection(collectionId);
        requireVersion(collection, request.version());
        reviewRevisionService.ensureCollectionBaseline(collection, activePhotos(collectionId));
        if (ReviewStatus.PENDING != collection.getReviewStatus()
                && ReviewStatus.PARTIALLY_REJECTED != collection.getReviewStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "COLLECTION_NOT_PENDING",
                    "Only a pending collection can be rejected");
        }
        reviewRevisionService.reviewAllPendingFields(
                ReviewTargetType.COLLECTION,
                collectionId,
                ReviewDtos.ReviewDecision.REJECT,
                request.reason(),
                operatorId
        );
        collection.setReviewStatus(ReviewStatus.PARTIALLY_REJECTED);
        collection.setRejectionReason(request.reason().trim());
        collection.setReviewedAt(Instant.now());
        collection.setReviewedBy(operatorId);
        saveCollection(collection, operatorId);
        auditLogService.record(operatorId, actor.getAccountType(), "COLLECTION_REVIEW", "REJECT_COLLECTION",
                "WORK_COLLECTION", collectionId, request.reason().trim(), ipAddress);
        return detail(operatorId, collectionId);
    }

    @Transactional
    public ReviewDtos.ReviewDetailResponse publish(
            Long operatorId,
            Long collectionId,
            ReviewDtos.PublishCollectionRequest request,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        requireAdmin(actor);
        WorkCollection collection = getCollection(collectionId);
        requireVersion(collection, request.version());
        boolean publishableStatus = PublishStatus.READY == collection.getPublishStatus()
                || PublishStatus.OFFLINE == collection.getPublishStatus();
        if (ReviewStatus.APPROVED != collection.getReviewStatus() || !publishableStatus) {
            throw new ApiException(HttpStatus.CONFLICT, "COLLECTION_NOT_READY",
                    "The collection must be fully approved before publishing");
        }
        List<CollectionPhoto> photos = activePhotos(collectionId);
        requireSubmissionReady(collection, photos);
        if (photos.stream().anyMatch(photo -> ReviewStatus.APPROVED != photo.getReviewStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "COLLECTION_PHOTOS_NOT_APPROVED",
                    "Every active photo must be approved before publishing");
        }
        if (!reviewRevisionService.allRequiredFieldsApproved(
                ReviewTargetType.COLLECTION,
                collectionId,
                ReviewRevisionService.COLLECTION_FIELD_KEYS)) {
            throw new ApiException(HttpStatus.CONFLICT, "COLLECTION_FIELDS_NOT_APPROVED",
                    "Every current collection field must be approved before publishing");
        }

        Instant now = Instant.now();
        collection.setVisibility(request.visibility());
        collection.setAccessPasswordHash(ContentVisibility.PASSWORD == request.visibility()
                ? contentAccessService.encodePassword(request.accessPassword())
                : null);
        collection.setFeatured(request.featured());
        collection.setPinned(request.pinned());
        collection.setSortOrder(request.sortOrder());
        collection.setPublishStatus(PublishStatus.PUBLISHED);
        collection.setPublishedAt(now);
        collection.setPublishedBy(operatorId);
        collection.setOfflineReason(null);
        saveCollection(collection, operatorId);
        auditLogService.record(operatorId, actor.getAccountType(), "COLLECTION_PUBLICATION", "PUBLISH_COLLECTION",
                "WORK_COLLECTION", collectionId, "Collection published as " + request.visibility(), ipAddress);
        return detail(operatorId, collectionId);
    }

    @Transactional
    public ReviewDtos.ReviewDetailResponse offline(
            Long operatorId,
            Long collectionId,
            ReviewDtos.OfflineCollectionRequest request,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        requireAdmin(actor);
        WorkCollection collection = getCollection(collectionId);
        requireVersion(collection, request.version());
        if (PublishStatus.PUBLISHED != collection.getPublishStatus()) {
            throw new ApiException(HttpStatus.CONFLICT, "COLLECTION_NOT_PUBLISHED",
                    "Only a published collection can be taken offline");
        }
        collection.setPublishStatus(PublishStatus.OFFLINE);
        collection.setOfflineReason(request.reason().trim());
        saveCollection(collection, operatorId);
        auditLogService.record(operatorId, actor.getAccountType(), "COLLECTION_PUBLICATION", "OFFLINE_COLLECTION",
                "WORK_COLLECTION", collectionId, request.reason().trim(), ipAddress);
        return detail(operatorId, collectionId);
    }

    @Transactional(readOnly = true)
    public ReviewDtos.DashboardOverviewResponse dashboard(Long operatorId) {
        requireAdmin(getActor(operatorId));
        List<ReviewDtos.ReviewQueueItem> recent = collectionRepository
                .findTop5ByDeletedFalseAndReviewStatusInOrderBySubmittedAtDescUpdatedAtDesc(
                        List.of(ReviewStatus.PENDING, ReviewStatus.PARTIALLY_REJECTED))
                .stream()
                .map(this::toQueueItem)
                .toList();
        return new ReviewDtos.DashboardOverviewResponse(
                collectionRepository.countByDeletedFalseAndReviewStatus(ReviewStatus.PENDING)
                        + projectRepository.countByDeletedFalseAndReviewStatus(ReviewStatus.PENDING),
                collectionRepository.countByDeletedFalseAndReviewStatus(ReviewStatus.PARTIALLY_REJECTED)
                        + projectRepository.countByDeletedFalseAndReviewStatus(ReviewStatus.PARTIALLY_REJECTED),
                collectionRepository.countByDeletedFalseAndPublishStatus(PublishStatus.READY),
                collectionRepository.countByDeletedFalseAndPublishStatus(PublishStatus.PUBLISHED),
                recent
        );
    }

    private void recalculateCollection(WorkCollection collection, Long operatorId, Instant reviewedAt) {
        List<CollectionPhoto> photos = activePhotos(collection.getId());
        List<ReviewItem> fields = reviewRevisionService.currentItems(
                ReviewTargetType.COLLECTION, collection.getId(), ReviewItemType.FIELD);
        boolean hasPendingFields = fields.stream()
                .anyMatch(item -> ReviewItemStatus.PENDING == item.getStatus());
        boolean hasRejectedFields = fields.stream()
                .anyMatch(item -> ReviewItemStatus.REJECTED == item.getStatus());
        if (photos.stream().anyMatch(photo -> ReviewStatus.REJECTED == photo.getReviewStatus())
                || hasRejectedFields) {
            collection.setReviewStatus(ReviewStatus.PARTIALLY_REJECTED);
            collection.setRejectionReason(fields.stream()
                    .filter(item -> ReviewItemStatus.REJECTED == item.getStatus())
                    .map(ReviewItem::getRejectionReason)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse(null));
        } else if (photos.stream().anyMatch(photo -> ReviewStatus.PENDING == photo.getReviewStatus())
                || hasPendingFields) {
            collection.setReviewStatus(ReviewStatus.PENDING);
            collection.setRejectionReason(null);
        } else {
            approveCollectionState(collection, operatorId, reviewedAt);
            return;
        }
        collection.setReviewedAt(reviewedAt);
        collection.setReviewedBy(operatorId);
        saveCollection(collection, operatorId);
    }

    private void approveCollectionState(WorkCollection collection, Long operatorId, Instant reviewedAt) {
        collection.setReviewStatus(ReviewStatus.APPROVED);
        collection.setPublishStatus(PublishStatus.READY);
        collection.setRejectionReason(null);
        collection.setReviewedAt(reviewedAt);
        collection.setReviewedBy(operatorId);
        saveCollection(collection, operatorId);
    }

    private ReviewDtos.ReviewQueueItem toQueueItem(WorkCollection collection) {
        List<CollectionPhoto> photos = activePhotos(collection.getId());
        List<ReviewItem> fields = reviewRevisionService.currentItems(
                ReviewTargetType.COLLECTION, collection.getId(), ReviewItemType.FIELD);
        String categoryName = categoryRepository.findById(collection.getCategoryId())
                .map(ContentCategory::getName)
                .orElse("未分类");
        String coverThumbnailUrl = null;
        if (collection.getCoverPhotoId() != null) {
            CollectionPhoto cover = photos.stream()
                    .filter(photo -> photo.getId().equals(collection.getCoverPhotoId()))
                    .findFirst()
                    .orElse(null);
            if (cover != null) {
                coverThumbnailUrl = assetRepository.findById(cover.getAssetId())
                        .map(MediaAsset::getThumbnailPath)
                        .map(this::publicUrl)
                        .orElse(null);
            }
        }
        return new ReviewDtos.ReviewQueueItem(
                collection.getId(),
                collection.getTitle(),
                categoryName,
                coverThumbnailUrl,
                collection.getReviewStatus(),
                collection.getPublishStatus(),
                collection.getRejectionReason(),
                collection.getSubmittedAt(),
                collection.getUpdatedAt(),
                collection.getVersion(),
                countItemStatus(fields, ReviewItemStatus.PENDING),
                countItemStatus(fields, ReviewItemStatus.REJECTED),
                countItemStatus(fields, ReviewItemStatus.APPROVED),
                photos.size(),
                countStatus(photos, ReviewStatus.PENDING),
                countStatus(photos, ReviewStatus.REJECTED),
                countStatus(photos, ReviewStatus.APPROVED)
        );
    }

    private long countStatus(List<CollectionPhoto> photos, ReviewStatus status) {
        return photos.stream().filter(photo -> status == photo.getReviewStatus()).count();
    }

    private long countItemStatus(List<ReviewItem> items, ReviewItemStatus status) {
        return items.stream().filter(item -> status == item.getStatus()).count();
    }

    private void requireSubmissionReady(WorkCollection collection, List<CollectionPhoto> photos) {
        if (photos.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "COLLECTION_PHOTOS_REQUIRED",
                    "Upload at least one photo before submitting the collection");
        }
        boolean coverAvailable = collection.getCoverPhotoId() != null
                && photos.stream().anyMatch(photo -> photo.getId().equals(collection.getCoverPhotoId()));
        if (!coverAvailable) {
            throw new ApiException(HttpStatus.CONFLICT, "COLLECTION_COVER_REQUIRED",
                    "Set an active cover photo before submitting the collection");
        }
    }

    private List<CollectionPhoto> activePhotos(Long collectionId) {
        return photoRepository.findAllByCollectionIdAndDeletedFalseOrderBySortOrderAscIdAsc(collectionId);
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

    private void requireAdmin(SystemUser actor) {
        if (!"ADMIN".equals(actor.getAccountType())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED",
                    "Only administrators can review and publish collections");
        }
    }

    private void requireAccess(SystemUser actor, WorkCollection collection) {
        if ("ADMIN".equals(actor.getAccountType())
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

    private void requireVersion(WorkCollection collection, Long requestVersion) {
        if (requestVersion == null || !collection.getVersion().equals(requestVersion)) {
            throw new ApiException(HttpStatus.CONFLICT, "COLLECTION_VERSION_CONFLICT",
                    "The collection was updated by another user; reload it before saving");
        }
    }

    private void saveCollection(WorkCollection collection, Long operatorId) {
        collection.setUpdatedBy(operatorId);
        collection.setUpdatedAt(Instant.now());
        collectionRepository.saveAndFlush(collection);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String publicUrl(String relativePath) {
        return "/media/" + relativePath.replace('\\', '/');
    }
}
