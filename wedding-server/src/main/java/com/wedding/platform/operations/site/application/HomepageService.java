package com.wedding.platform.operations.site.application;

import com.wedding.platform.content.collection.persistence.entity.WorkCollection;
import com.wedding.platform.content.collection.persistence.repository.WorkCollectionRepository;
import com.wedding.platform.content.media.persistence.entity.CollectionPhoto;
import com.wedding.platform.content.media.persistence.entity.MediaAsset;
import com.wedding.platform.content.media.persistence.repository.CollectionPhotoRepository;
import com.wedding.platform.content.media.persistence.repository.MediaAssetRepository;
import com.wedding.platform.content.publication.application.PublicCollectionService;
import com.wedding.platform.content.publication.application.PublicProjectService;
import com.wedding.platform.content.publication.web.PublicCollectionDtos;
import com.wedding.platform.content.publication.web.PublicProjectDtos;
import com.wedding.platform.content.shared.ContentVisibility;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.content.shared.ReviewStatus;
import com.wedding.platform.operations.feedback.application.PublicFeedbackService;
import com.wedding.platform.operations.feedback.web.PublicFeedbackDtos;
import com.wedding.platform.operations.site.persistence.entity.HomepageCarouselItem;
import com.wedding.platform.operations.site.persistence.entity.HomepageFeature;
import com.wedding.platform.operations.site.persistence.entity.HomepageFeatureTargetType;
import com.wedding.platform.operations.site.persistence.repository.HomepageCarouselItemRepository;
import com.wedding.platform.operations.site.persistence.repository.HomepageFeatureRepository;
import com.wedding.platform.operations.site.web.HomepageDtos;
import com.wedding.platform.platform.audit.AuditLogService;
import com.wedding.platform.platform.web.ApiException;
import com.wedding.platform.system.account.persistence.entity.SystemUser;
import com.wedding.platform.system.account.persistence.repository.SystemUserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class HomepageService {

    private static final int PROJECT_LIMIT = 6;
    private static final int COLLECTION_LIMIT = 12;
    private static final int FEEDBACK_LIMIT = 6;
    private static final int CAROUSEL_LIMIT = 5;
    private static final int CAROUSEL_CANDIDATE_LIMIT = 300;
    private static final String ACTIVE = "ACTIVE";
    private static final String INACTIVE = "INACTIVE";

    private final HomepageFeatureRepository featureRepository;
    private final HomepageCarouselItemRepository carouselRepository;
    private final CollectionPhotoRepository photoRepository;
    private final MediaAssetRepository assetRepository;
    private final WorkCollectionRepository workCollectionRepository;
    private final PublicProjectService projectService;
    private final PublicCollectionService collectionService;
    private final PublicFeedbackService feedbackService;
    private final SystemUserRepository userRepository;
    private final AuditLogService auditLogService;

    public HomepageService(
            HomepageFeatureRepository featureRepository,
            HomepageCarouselItemRepository carouselRepository,
            CollectionPhotoRepository photoRepository,
            MediaAssetRepository assetRepository,
            WorkCollectionRepository workCollectionRepository,
            PublicProjectService projectService,
            PublicCollectionService collectionService,
            PublicFeedbackService feedbackService,
            SystemUserRepository userRepository,
            AuditLogService auditLogService
    ) {
        this.featureRepository = featureRepository;
        this.carouselRepository = carouselRepository;
        this.photoRepository = photoRepository;
        this.assetRepository = assetRepository;
        this.workCollectionRepository = workCollectionRepository;
        this.projectService = projectService;
        this.collectionService = collectionService;
        this.feedbackService = feedbackService;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public HomepageDtos.PublicHomepage publicHomepage() {
        List<PublicProjectDtos.ProjectSummary> projects = configuredProjects();
        if (projects.isEmpty()) {
            projects = projectService.latestProjects(3);
        }
        List<PublicCollectionDtos.CollectionSummary> collections = configuredCollections();
        if (collections.isEmpty()) {
            collections = collectionService.latestCollections(6);
        }
        List<PublicFeedbackDtos.Feedback> feedback = configuredFeedback();
        if (feedback.isEmpty()) {
            feedback = feedbackService.latest(3);
        }
        return new HomepageDtos.PublicHomepage(configuredCarousel(), projects, collections, feedback);
    }

    @Transactional(readOnly = true)
    public HomepageDtos.FeatureOptions options(Long adminId) {
        requireAdmin(adminId);
        return new HomepageDtos.FeatureOptions(
                projectService.latestProjects(100),
                collectionService.latestCollections(100),
                feedbackService.latest(100),
                featureRepository.findAllByDeletedFalseOrderByTargetTypeAscPinnedDescSortOrderAscIdAsc()
                        .stream()
                        .filter(feature -> "ACTIVE".equals(feature.getStatus()))
                        .filter(this::isPublicFeature)
                        .map(this::toItem)
                        .toList()
        );
    }

    @Transactional
    public HomepageDtos.FeatureOptions replace(
            Long adminId,
            HomepageDtos.ReplaceFeaturesRequest request,
            String ipAddress
    ) {
        SystemUser admin = requireAdmin(adminId);
        validateItems(request.items());
        Instant now = Instant.now();
        Map<String, HomepageFeature> existingByTarget = new LinkedHashMap<>();
        featureRepository.findAll().forEach(feature -> {
            existingByTarget.put(featureKey(feature.getTargetType(), feature.getTargetId()), feature);
            feature.setStatus("INACTIVE");
            feature.setDeleted(true);
            feature.setDeletedAt(now);
            feature.setUpdatedBy(adminId);
        });
        List<HomepageFeature> features = request.items().stream()
                .map(item -> {
                    HomepageFeature feature = existingByTarget.getOrDefault(
                            featureKey(item.targetType(), item.targetId()),
                            new HomepageFeature());
                    feature.setTargetType(item.targetType());
                    feature.setTargetId(item.targetId());
                    feature.setSortOrder(item.sortOrder());
                    feature.setPinned(item.pinned());
                    feature.setStatus("ACTIVE");
                    if (feature.getCreatedBy() == null) {
                        feature.setCreatedBy(adminId);
                    }
                    feature.setUpdatedBy(adminId);
                    feature.setDeleted(false);
                    feature.setDeletedAt(null);
                    return feature;
                })
                .toList();
        featureRepository.saveAll(existingByTarget.values());
        featureRepository.saveAll(features);
        auditLogService.record(
                adminId,
                admin.getAccountType(),
                "SITE",
                "REPLACE_HOMEPAGE_FEATURES",
                "HOMEPAGE_FEATURE",
                null,
                "Configured " + features.size() + " homepage features",
                ipAddress
        );
        return options(adminId);
    }

    @Transactional(readOnly = true)
    public HomepageDtos.CarouselOptions carouselOptions(Long adminId) {
        requireAdmin(adminId);
        return buildCarouselOptions();
    }

    @Transactional
    public HomepageDtos.CarouselOptions replaceCarousel(
            Long adminId,
            HomepageDtos.ReplaceCarouselRequest request,
            String ipAddress
    ) {
        SystemUser admin = requireAdmin(adminId);
        validateCarouselItems(request.items());
        Instant now = Instant.now();
        Map<Long, HomepageCarouselItem> existingByPhoto = new LinkedHashMap<>();
        carouselRepository.findAll().forEach(item -> {
            existingByPhoto.put(item.getPhotoId(), item);
            item.setStatus(INACTIVE);
            item.setDeleted(true);
            item.setDeletedAt(now);
            item.setUpdatedBy(adminId);
        });
        List<HomepageCarouselItem> items = request.items().stream()
                .map(requestItem -> {
                    HomepageCarouselItem item = existingByPhoto.getOrDefault(
                            requestItem.photoId(),
                            new HomepageCarouselItem());
                    item.setPhotoId(requestItem.photoId());
                    item.setSortOrder(requestItem.sortOrder());
                    item.setFocalX(requestItem.focalX());
                    item.setFocalY(requestItem.focalY());
                    item.setStatus(ACTIVE);
                    if (item.getCreatedBy() == null) {
                        item.setCreatedBy(adminId);
                    }
                    item.setUpdatedBy(adminId);
                    item.setDeleted(false);
                    item.setDeletedAt(null);
                    return item;
                })
                .toList();
        carouselRepository.saveAll(existingByPhoto.values());
        carouselRepository.saveAll(items);
        auditLogService.record(
                adminId,
                admin.getAccountType(),
                "SITE",
                "REPLACE_HOMEPAGE_CAROUSEL",
                "HOMEPAGE_CAROUSEL",
                null,
                "Configured " + items.size() + " homepage carousel images",
                ipAddress
        );
        return buildCarouselOptions();
    }

    private List<PublicProjectDtos.ProjectSummary> configuredProjects() {
        List<Long> ids = featureRepository
                .findAllByTargetTypeAndStatusAndDeletedFalseOrderByPinnedDescSortOrderAscIdAsc(
                        HomepageFeatureTargetType.PROJECT,
                        "ACTIVE")
                .stream()
                .map(HomepageFeature::getTargetId)
                .toList();
        return projectService.projectsByIds(ids).stream().limit(PROJECT_LIMIT).toList();
    }

    private List<PublicCollectionDtos.CollectionSummary> configuredCollections() {
        List<Long> ids = featureRepository
                .findAllByTargetTypeAndStatusAndDeletedFalseOrderByPinnedDescSortOrderAscIdAsc(
                        HomepageFeatureTargetType.COLLECTION,
                        "ACTIVE")
                .stream()
                .map(HomepageFeature::getTargetId)
                .toList();
        return collectionService.collectionsByIds(ids).stream().limit(COLLECTION_LIMIT).toList();
    }

    private List<PublicFeedbackDtos.Feedback> configuredFeedback() {
        List<Long> ids = featureRepository
                .findAllByTargetTypeAndStatusAndDeletedFalseOrderByPinnedDescSortOrderAscIdAsc(
                        HomepageFeatureTargetType.FEEDBACK,
                        "ACTIVE")
                .stream()
                .map(HomepageFeature::getTargetId)
                .toList();
        return feedbackService.byIds(ids).stream().limit(FEEDBACK_LIMIT).toList();
    }

    private List<HomepageDtos.CarouselSlide> configuredCarousel() {
        return carouselRepository.findAllByStatusAndDeletedFalseOrderBySortOrderAscIdAsc(ACTIVE)
                .stream()
                .map(this::toCarouselItem)
                .filter(HomepageDtos.CarouselItem::valid)
                .limit(CAROUSEL_LIMIT)
                .map(item -> new HomepageDtos.CarouselSlide(
                        item.photoId(),
                        item.collectionId(),
                        item.collectionTitle(),
                        item.previewUrl(),
                        item.thumbnailUrl(),
                        item.width(),
                        item.height(),
                        item.focalX(),
                        item.focalY()
                ))
                .toList();
    }

    private HomepageDtos.CarouselOptions buildCarouselOptions() {
        List<CollectionPhoto> photos = photoRepository.findHomepageCarouselCandidates(
                ReviewStatus.APPROVED,
                PublishStatus.PUBLISHED,
                ContentVisibility.PUBLIC,
                PageRequest.of(0, CAROUSEL_CANDIDATE_LIMIT)
        );
        Map<Long, WorkCollection> collections = workCollectionRepository.findAllById(
                        photos.stream().map(CollectionPhoto::getCollectionId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(WorkCollection::getId, Function.identity()));
        Map<Long, MediaAsset> assets = assetRepository.findAllById(
                        photos.stream().map(CollectionPhoto::getAssetId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(MediaAsset::getId, Function.identity()));
        List<HomepageDtos.CarouselCandidate> candidates = photos.stream()
                .map(photo -> toCarouselCandidate(
                        photo,
                        collections.get(photo.getCollectionId()),
                        assets.get(photo.getAssetId())))
                .filter(candidate -> candidate != null)
                .toList();
        List<HomepageDtos.CarouselItem> items = carouselRepository
                .findAllByStatusAndDeletedFalseOrderBySortOrderAscIdAsc(ACTIVE)
                .stream()
                .map(this::toCarouselItem)
                .toList();
        return new HomepageDtos.CarouselOptions(candidates, items);
    }

    private void validateCarouselItems(List<HomepageDtos.CarouselItemRequest> items) {
        Set<Long> photoIds = new HashSet<>();
        for (HomepageDtos.CarouselItemRequest item : items) {
            if (!photoIds.add(item.photoId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "HOMEPAGE_CAROUSEL_DUPLICATE",
                        "The same photo cannot be configured twice");
            }
            requirePublicCarouselCandidate(item.photoId());
        }
    }

    private HomepageDtos.CarouselCandidate requirePublicCarouselCandidate(Long photoId) {
        CollectionPhoto photo = photoRepository.findById(photoId)
                .orElseThrow(() -> invalidCarouselTarget(photoId));
        WorkCollection collection = workCollectionRepository.findById(photo.getCollectionId())
                .orElseThrow(() -> invalidCarouselTarget(photoId));
        MediaAsset asset = assetRepository.findById(photo.getAssetId())
                .orElseThrow(() -> invalidCarouselTarget(photoId));
        HomepageDtos.CarouselCandidate candidate = toCarouselCandidate(photo, collection, asset);
        if (candidate == null) {
            throw invalidCarouselTarget(photoId);
        }
        return candidate;
    }

    private HomepageDtos.CarouselCandidate toCarouselCandidate(
            CollectionPhoto photo,
            WorkCollection collection,
            MediaAsset asset
    ) {
        if (!isPublicCarouselPhoto(photo, collection, asset)) {
            return null;
        }
        return new HomepageDtos.CarouselCandidate(
                photo.getId(),
                collection.getId(),
                collection.getTitle(),
                publicUrl(asset.getPreviewPath()),
                publicUrl(asset.getThumbnailPath()),
                asset.getWidth(),
                asset.getHeight(),
                photo.getSortOrder()
        );
    }

    private HomepageDtos.CarouselItem toCarouselItem(HomepageCarouselItem item) {
        CollectionPhoto photo = photoRepository.findById(item.getPhotoId()).orElse(null);
        WorkCollection collection = photo == null
                ? null
                : workCollectionRepository.findById(photo.getCollectionId()).orElse(null);
        MediaAsset asset = photo == null
                ? null
                : assetRepository.findById(photo.getAssetId()).orElse(null);
        String invalidReason = carouselInvalidReason(photo, collection, asset);
        return new HomepageDtos.CarouselItem(
                item.getId(),
                item.getPhotoId(),
                collection == null ? null : collection.getId(),
                collection == null ? null : collection.getTitle(),
                asset == null ? null : publicUrl(asset.getPreviewPath()),
                asset == null ? null : publicUrl(asset.getThumbnailPath()),
                asset == null ? null : asset.getWidth(),
                asset == null ? null : asset.getHeight(),
                item.getSortOrder(),
                item.getFocalX(),
                item.getFocalY(),
                invalidReason == null,
                invalidReason,
                item.getVersion()
        );
    }

    private boolean isPublicCarouselPhoto(
            CollectionPhoto photo,
            WorkCollection collection,
            MediaAsset asset
    ) {
        return carouselInvalidReason(photo, collection, asset) == null;
    }

    private String carouselInvalidReason(
            CollectionPhoto photo,
            WorkCollection collection,
            MediaAsset asset
    ) {
        if (photo == null || Boolean.TRUE.equals(photo.getDeleted())) {
            return "PHOTO_NOT_AVAILABLE";
        }
        if (ReviewStatus.APPROVED != photo.getReviewStatus()) {
            return "PHOTO_NOT_APPROVED";
        }
        if (collection == null
                || Boolean.TRUE.equals(collection.getDeleted())
                || PublishStatus.PUBLISHED != collection.getPublishStatus()
                || ContentVisibility.PUBLIC != collection.getVisibility()) {
            return "COLLECTION_NOT_PUBLIC";
        }
        if (asset == null
                || Boolean.TRUE.equals(asset.getDeleted())
                || !"SUCCESS".equals(asset.getProcessStatus())) {
            return "ASSET_NOT_AVAILABLE";
        }
        return null;
    }

    private ApiException invalidCarouselTarget(Long photoId) {
        return new ApiException(HttpStatus.BAD_REQUEST, "HOMEPAGE_CAROUSEL_TARGET_INVALID",
                "Homepage carousel images must belong to currently published public collections: " + photoId);
    }

    private String publicUrl(String relativePath) {
        return "/media/" + relativePath.replace('\\', '/');
    }

    private void validateItems(List<HomepageDtos.FeatureItemRequest> items) {
        Set<String> uniqueTargets = new HashSet<>();
        int projectCount = 0;
        int collectionCount = 0;
        int feedbackCount = 0;
        for (HomepageDtos.FeatureItemRequest item : items) {
            String key = item.targetType() + ":" + item.targetId();
            if (!uniqueTargets.add(key)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "HOMEPAGE_FEATURE_DUPLICATE",
                        "The same homepage target cannot be configured twice");
            }
            switch (item.targetType()) {
                case PROJECT -> {
                    projectCount++;
                    if (!projectService.isPublicProject(item.targetId())) {
                        throw invalidTarget(item);
                    }
                }
                case COLLECTION -> {
                    collectionCount++;
                    if (!collectionService.isPublicCollection(item.targetId())) {
                        throw invalidTarget(item);
                    }
                }
                case FEEDBACK -> {
                    feedbackCount++;
                    if (!feedbackService.isPublicFeedback(item.targetId())) {
                        throw invalidTarget(item);
                    }
                }
            }
        }
        if (projectCount > PROJECT_LIMIT || collectionCount > COLLECTION_LIMIT || feedbackCount > FEEDBACK_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HOMEPAGE_FEATURE_LIMIT",
                    "Homepage feature limits are 6 projects, 12 collections, and 6 feedback items");
        }
    }

    private ApiException invalidTarget(HomepageDtos.FeatureItemRequest item) {
        return new ApiException(HttpStatus.BAD_REQUEST, "HOMEPAGE_FEATURE_TARGET_INVALID",
                "Homepage features must reference currently published public content: "
                        + item.targetType() + " " + item.targetId());
    }

    private boolean isPublicFeature(HomepageFeature feature) {
        return switch (feature.getTargetType()) {
            case PROJECT -> projectService.isPublicProject(feature.getTargetId());
            case COLLECTION -> collectionService.isPublicCollection(feature.getTargetId());
            case FEEDBACK -> feedbackService.isPublicFeedback(feature.getTargetId());
        };
    }

    private String featureKey(HomepageFeatureTargetType targetType, Long targetId) {
        return targetType + ":" + targetId;
    }

    private HomepageDtos.FeatureItem toItem(HomepageFeature feature) {
        return new HomepageDtos.FeatureItem(
                feature.getId(),
                feature.getTargetType(),
                feature.getTargetId(),
                feature.getSortOrder(),
                feature.getPinned(),
                feature.getVersion()
        );
    }

    private SystemUser requireAdmin(Long adminId) {
        SystemUser admin = userRepository.findById(adminId)
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .filter(user -> "ACTIVE".equals(user.getAccountStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_NOT_FOUND",
                        "Account is not available"));
        if (!"ADMIN".equals(admin.getAccountType())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "HOMEPAGE_ACCESS_DENIED",
                    "Only administrators can configure the public homepage");
        }
        return admin;
    }
}
