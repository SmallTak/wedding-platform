package com.wedding.platform.content.media.application;

import com.wedding.platform.content.collection.persistence.entity.WorkCollection;
import com.wedding.platform.content.collection.persistence.repository.CollectionCreatorRepository;
import com.wedding.platform.content.collection.persistence.repository.WorkCollectionRepository;
import com.wedding.platform.content.media.persistence.entity.CollectionPhoto;
import com.wedding.platform.content.media.persistence.entity.MediaAsset;
import com.wedding.platform.content.media.persistence.repository.CollectionPhotoRepository;
import com.wedding.platform.content.media.persistence.repository.MediaAssetRepository;
import com.wedding.platform.content.media.web.PhotoDtos;
import com.wedding.platform.content.review.application.ReviewRevisionService;
import com.wedding.platform.content.review.persistence.entity.ReviewTargetType;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.content.shared.ReviewStatus;
import com.wedding.platform.platform.audit.AuditLogService;
import com.wedding.platform.platform.file.CollectionImageStorageService;
import com.wedding.platform.platform.web.ApiException;
import com.wedding.platform.system.account.persistence.entity.SystemUser;
import com.wedding.platform.system.account.persistence.repository.SystemUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
public class CollectionPhotoService {

    private static final int MAX_BATCH_FILES = 50;

    private final WorkCollectionRepository collectionRepository;
    private final CollectionCreatorRepository collectionCreatorRepository;
    private final CollectionPhotoRepository photoRepository;
    private final MediaAssetRepository assetRepository;
    private final SystemUserRepository userRepository;
    private final CollectionImageStorageService storageService;
    private final AuditLogService auditLogService;
    private final ReviewRevisionService reviewRevisionService;

    public CollectionPhotoService(
            WorkCollectionRepository collectionRepository,
            CollectionCreatorRepository collectionCreatorRepository,
            CollectionPhotoRepository photoRepository,
            MediaAssetRepository assetRepository,
            SystemUserRepository userRepository,
            CollectionImageStorageService storageService,
            AuditLogService auditLogService,
            ReviewRevisionService reviewRevisionService
    ) {
        this.collectionRepository = collectionRepository;
        this.collectionCreatorRepository = collectionCreatorRepository;
        this.photoRepository = photoRepository;
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.auditLogService = auditLogService;
        this.reviewRevisionService = reviewRevisionService;
    }

    @Transactional(readOnly = true)
    public PhotoDtos.PhotoBatchResponse listPhotos(Long operatorId, Long collectionId) {
        SystemUser actor = getActor(operatorId);
        WorkCollection collection = getCollection(collectionId);
        requireAccess(actor, collection);
        return toResponse(collection);
    }

    @Transactional
    public PhotoDtos.PhotoBatchResponse upload(
            Long operatorId,
            Long collectionId,
            List<MultipartFile> files,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        WorkCollection collection = getCollection(collectionId);
        requireAccess(actor, collection);
        requireEditable(collection);
        if (files == null || files.isEmpty() || files.size() > MAX_BATCH_FILES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_BATCH_INVALID",
                    "Upload between 1 and " + MAX_BATCH_FILES + " images at a time");
        }
        prepareContentChange(collection, operatorId);

        List<CollectionImageStorageService.StoredImage> storedImages = new ArrayList<>();
        cleanupStoredImagesAfterRollback(storedImages);
        try {
            int sortOrder = photoRepository.findMaxSortOrder(collectionId) + 1;
            for (MultipartFile file : files) {
                CollectionImageStorageService.StoredImage stored = storageService.store(file);
                storedImages.add(stored);

                MediaAsset asset = new MediaAsset();
                asset.setOriginalName(stored.originalName());
                asset.setStorageName(stored.storageName());
                asset.setMimeType(stored.mimeType());
                asset.setFileSize(stored.fileSize());
                asset.setWidth(stored.width());
                asset.setHeight(stored.height());
                asset.setOriginalPath(stored.originalPath());
                asset.setPreviewPath(stored.previewPath());
                asset.setThumbnailPath(stored.thumbnailPath());
                asset.setChecksum(stored.checksum());
                asset.setProcessStatus("SUCCESS");
                asset.setCreatedBy(operatorId);
                asset.setUpdatedBy(operatorId);
                asset = assetRepository.saveAndFlush(asset);

                CollectionPhoto photo = new CollectionPhoto();
                photo.setCollectionId(collectionId);
                photo.setAssetId(asset.getId());
                photo.setSortOrder(sortOrder++);
                photo.setReviewStatus(ReviewStatus.DRAFT);
                photo.setCreatedBy(operatorId);
                photo.setUpdatedBy(operatorId);
                photoRepository.save(photo);
            }
            photoRepository.flush();
            markContentChanged(collection, operatorId);
            bumpCollection(collection, operatorId);
            auditLogService.record(operatorId, actor.getAccountType(), "COLLECTION_PHOTO", "UPLOAD_PHOTOS",
                    "WORK_COLLECTION", collectionId, "Uploaded " + files.size() + " collection photos", ipAddress);
            return toResponse(collection);
        } catch (RuntimeException exception) {
            storedImages.forEach(storageService::cleanup);
            throw exception;
        }
    }

    private void cleanupStoredImagesAfterRollback(
            List<CollectionImageStorageService.StoredImage> storedImages
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storedImages.forEach(storageService::cleanup);
                }
            }
        });
    }

    @Transactional
    public PhotoDtos.PhotoBatchResponse reorder(
            Long operatorId,
            Long collectionId,
            PhotoDtos.ReorderPhotosRequest request,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        WorkCollection collection = getCollection(collectionId);
        requireAccess(actor, collection);
        requireEditable(collection);
        requireVersion(collection, request.version());

        List<CollectionPhoto> photos = photoRepository
                .findAllByCollectionIdAndDeletedFalseOrderBySortOrderAscIdAsc(collectionId);
        if (photos.size() != request.photoIds().size()
                || new HashSet<>(request.photoIds()).size() != request.photoIds().size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHOTO_ORDER_INVALID",
                    "Photo order must contain every active photo exactly once");
        }
        Map<Long, CollectionPhoto> photosById = new HashMap<>();
        photos.forEach(photo -> photosById.put(photo.getId(), photo));
        List<CollectionPhoto> changedPhotos = new ArrayList<>();
        for (int index = 0; index < request.photoIds().size(); index++) {
            CollectionPhoto photo = photosById.get(request.photoIds().get(index));
            if (photo == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PHOTO_ORDER_INVALID",
                        "Photo order contains a photo outside this collection");
            }
            if (photo.getSortOrder() != index) {
                changedPhotos.add(photo);
            }
        }
        if (changedPhotos.isEmpty()) {
            return toResponse(collection);
        }

        prepareContentChange(collection, operatorId);
        for (int index = 0; index < request.photoIds().size(); index++) {
            CollectionPhoto photo = photosById.get(request.photoIds().get(index));
            photo.setSortOrder(index);
            photo.setUpdatedBy(operatorId);
        }
        changedPhotos.forEach(photo -> resetPhotoReview(photo, operatorId));
        photoRepository.saveAll(photos);
        markContentChanged(collection, operatorId);
        bumpCollection(collection, operatorId);
        auditLogService.record(operatorId, actor.getAccountType(), "COLLECTION_PHOTO", "REORDER_PHOTOS",
                "WORK_COLLECTION", collectionId, "Collection photos reordered", ipAddress);
        return toResponse(collection);
    }

    @Transactional
    public PhotoDtos.PhotoBatchResponse setCover(
            Long operatorId,
            Long collectionId,
            PhotoDtos.SetCoverRequest request,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        WorkCollection collection = getCollection(collectionId);
        requireAccess(actor, collection);
        requireEditable(collection);
        requireVersion(collection, request.version());
        photoRepository.findByIdAndCollectionIdAndDeletedFalse(request.photoId(), collectionId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "COVER_PHOTO_INVALID",
                        "Cover photo must belong to this collection"));
        if (request.photoId().equals(collection.getCoverPhotoId())) {
            return toResponse(collection);
        }

        prepareContentChange(collection, operatorId);
        collection.setCoverPhotoId(request.photoId());
        markContentChanged(collection, operatorId);
        bumpCollection(collection, operatorId);
        auditLogService.record(operatorId, actor.getAccountType(), "COLLECTION_PHOTO", "SET_COLLECTION_COVER",
                "WORK_COLLECTION", collectionId, "Collection cover photo updated", ipAddress);
        return toResponse(collection);
    }

    @Transactional
    public PhotoDtos.PhotoBatchResponse deletePhoto(
            Long operatorId,
            Long collectionId,
            Long photoId,
            Long version,
            String ipAddress
    ) {
        SystemUser actor = getActor(operatorId);
        WorkCollection collection = getCollection(collectionId);
        requireAccess(actor, collection);
        requireEditable(collection);
        requireVersion(collection, version);
        CollectionPhoto photo = photoRepository.findByIdAndCollectionIdAndDeletedFalse(photoId, collectionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PHOTO_NOT_FOUND",
                        "Collection photo was not found"));
        prepareContentChange(collection, operatorId);
        boolean approvedPhotoRemoved = ReviewStatus.APPROVED == photo.getReviewStatus();

        photo.setDeleted(true);
        photo.setDeletedAt(Instant.now());
        photo.setUpdatedBy(operatorId);
        photoRepository.save(photo);
        reviewRevisionService.markPhotoRemoved(collectionId, photoId, operatorId);
        if (photoId.equals(collection.getCoverPhotoId())) {
            collection.setCoverPhotoId(null);
        }
        markContentChanged(collection, operatorId);
        if (approvedPhotoRemoved) {
            List<CollectionPhoto> remainingPhotos = photoRepository
                    .findAllByCollectionIdAndDeletedFalseOrderBySortOrderAscIdAsc(collectionId);
            remainingPhotos.forEach(activePhoto -> resetPhotoReview(activePhoto, operatorId));
            photoRepository.saveAll(remainingPhotos);
        } else {
            recalculateAfterDeletion(collection);
        }
        bumpCollection(collection, operatorId);
        auditLogService.record(operatorId, actor.getAccountType(), "COLLECTION_PHOTO", "DELETE_PHOTO",
                "COLLECTION_PHOTO", photoId, "Collection photo logically deleted", ipAddress);
        return toResponse(collection);
    }

    private PhotoDtos.PhotoBatchResponse toResponse(WorkCollection collection) {
        List<CollectionPhoto> photos = photoRepository
                .findAllByCollectionIdAndDeletedFalseOrderBySortOrderAscIdAsc(collection.getId());
        Map<Long, MediaAsset> assets = new HashMap<>();
        assetRepository.findAllById(photos.stream().map(CollectionPhoto::getAssetId).toList())
                .forEach(asset -> assets.put(asset.getId(), asset));
        List<PhotoDtos.PhotoResponse> responses = photos.stream()
                .map(photo -> toResponse(photo, assets.get(photo.getAssetId())))
                .filter(response -> response != null)
                .toList();
        return new PhotoDtos.PhotoBatchResponse(
                collection.getId(),
                collection.getVersion(),
                collection.getCoverPhotoId(),
                responses
        );
    }

    private PhotoDtos.PhotoResponse toResponse(CollectionPhoto photo, MediaAsset asset) {
        if (asset == null) {
            return null;
        }
        return new PhotoDtos.PhotoResponse(
                photo.getId(),
                asset.getOriginalName(),
                asset.getMimeType(),
                asset.getFileSize(),
                asset.getWidth(),
                asset.getHeight(),
                publicUrl(asset.getPreviewPath()),
                publicUrl(asset.getThumbnailPath()),
                asset.getChecksum(),
                photo.getSortOrder(),
                photo.getReviewStatus(),
                photo.getRejectionReason(),
                photo.getSubmittedAt(),
                photo.getReviewedAt(),
                photo.getReviewedBy(),
                photo.getCreatedBy(),
                photo.getCreatedAt()
        );
    }

    private SystemUser getActor(Long userId) {
        SystemUser actor = userRepository.findById(userId)
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .filter(user -> "ACTIVE".equals(user.getAccountStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_NOT_FOUND",
                        "Account is not available"));
        if (!"ADMIN".equals(actor.getAccountType()) && !"CREATOR".equals(actor.getAccountType())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COLLECTION_ACCESS_DENIED",
                    "This account cannot manage collection photos");
        }
        return actor;
    }

    private WorkCollection getCollection(Long collectionId) {
        return collectionRepository.findByIdAndDeletedFalse(collectionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COLLECTION_NOT_FOUND",
                        "Work collection was not found"));
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

    private void bumpCollection(WorkCollection collection, Long operatorId) {
        collection.setUpdatedBy(operatorId);
        collection.setUpdatedAt(Instant.now());
        collectionRepository.saveAndFlush(collection);
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

    private void recalculateAfterDeletion(WorkCollection collection) {
        List<CollectionPhoto> photos = photoRepository
                .findAllByCollectionIdAndDeletedFalseOrderBySortOrderAscIdAsc(collection.getId());
        boolean coverAvailable = collection.getCoverPhotoId() != null
                && photos.stream().anyMatch(photo -> photo.getId().equals(collection.getCoverPhotoId()));
        boolean allApproved = !photos.isEmpty()
                && photos.stream().allMatch(photo -> ReviewStatus.APPROVED == photo.getReviewStatus());
        boolean fieldsApproved = reviewRevisionService.allRequiredFieldsApproved(
                ReviewTargetType.COLLECTION,
                collection.getId(),
                ReviewRevisionService.COLLECTION_FIELD_KEYS
        );
        if (coverAvailable && allApproved && fieldsApproved) {
            collection.setReviewStatus(ReviewStatus.APPROVED);
            collection.setPublishStatus(PublishStatus.READY);
            collection.setRejectionReason(null);
            return;
        }
        boolean hasRejected = photos.stream().anyMatch(photo -> ReviewStatus.REJECTED == photo.getReviewStatus());
        boolean hasPending = photos.stream().anyMatch(photo -> ReviewStatus.PENDING == photo.getReviewStatus());
        collection.setReviewStatus(hasRejected
                ? ReviewStatus.PARTIALLY_REJECTED
                : hasPending ? ReviewStatus.PENDING : ReviewStatus.DRAFT);
        if (PublishStatus.READY == collection.getPublishStatus()) {
            collection.setPublishStatus(PublishStatus.UNPUBLISHED);
        }
    }

    private void resetPhotoReview(CollectionPhoto photo, Long operatorId) {
        photo.setReviewStatus(ReviewStatus.DRAFT);
        photo.setRejectionReason(null);
        photo.setSubmittedAt(null);
        photo.setReviewedAt(null);
        photo.setReviewedBy(null);
        photo.setUpdatedBy(operatorId);
    }

    private void prepareContentChange(WorkCollection collection, Long operatorId) {
        List<CollectionPhoto> photos = photoRepository
                .findAllByCollectionIdAndDeletedFalseOrderBySortOrderAscIdAsc(collection.getId());
        reviewRevisionService.ensureCollectionBaseline(collection, photos);
        reviewRevisionService.cancelPendingSubmission(
                ReviewTargetType.COLLECTION,
                collection.getId(),
                operatorId
        );
    }

    private String publicUrl(String relativePath) {
        return "/media/" + relativePath.replace('\\', '/');
    }
}
