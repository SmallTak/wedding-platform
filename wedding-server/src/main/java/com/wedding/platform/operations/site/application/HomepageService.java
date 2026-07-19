package com.wedding.platform.operations.site.application;

import com.wedding.platform.content.collection.persistence.entity.WorkCollection;
import com.wedding.platform.content.collection.persistence.repository.WorkCollectionRepository;
import com.wedding.platform.content.media.persistence.entity.CollectionPhoto;
import com.wedding.platform.content.media.persistence.entity.MediaAsset;
import com.wedding.platform.content.media.persistence.repository.CollectionPhotoRepository;
import com.wedding.platform.content.media.persistence.repository.MediaAssetRepository;
import com.wedding.platform.content.project.persistence.entity.WeddingProject;
import com.wedding.platform.content.project.persistence.repository.WeddingProjectRepository;
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

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class HomepageService {

    private static final int PROJECT_LIMIT = 6;
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
    private final WeddingProjectRepository projectRepository;
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
            WeddingProjectRepository projectRepository,
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
        this.projectRepository = projectRepository;
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
        List<PublicCollectionDtos.CollectionSummary> collections = collectionService.latestCollections(6);
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
                List.of(),
                feedbackService.latest(100),
                featureRepository.findAllByDeletedFalseOrderByTargetTypeAscPinnedDescSortOrderAscIdAsc()
                        .stream()
                        .filter(feature -> "ACTIVE".equals(feature.getStatus()))
                        .filter(feature -> HomepageFeatureTargetType.COLLECTION != feature.getTargetType())
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
        Map<Long, HomepageCarouselItem> existingByCollection = new LinkedHashMap<>();
        carouselRepository.findAll().forEach(item -> {
            existingByCollection.put(item.getCollectionId(), item);
            item.setStatus(INACTIVE);
            item.setDeleted(true);
            item.setDeletedAt(now);
            item.setUpdatedBy(adminId);
        });
        List<HomepageCarouselItem> items = request.items().stream()
                .map(requestItem -> {
                    HomepageCarouselItem item = existingByCollection.getOrDefault(
                            requestItem.collectionId(),
                            new HomepageCarouselItem());
                    item.setCollectionId(requestItem.collectionId());
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
        carouselRepository.saveAll(existingByCollection.values());
        carouselRepository.saveAll(items);
        auditLogService.record(
                adminId,
                admin.getAccountType(),
                "SITE",
                "REPLACE_HOMEPAGE_CAROUSEL",
                "HOMEPAGE_CAROUSEL",
                null,
                "Configured " + items.size() + " homepage carousel collections",
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
                        item.collectionId(),
                        item.collectionTitle(),
                        item.description(),
                        item.eventDate(),
                        item.locationText(),
                        item.originalUrl(),
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
        List<WorkCollection> collections = workCollectionRepository.findHomepageCarouselCandidates(
                ReviewStatus.APPROVED,
                PublishStatus.PUBLISHED,
                ContentVisibility.PUBLIC,
                PageRequest.of(0, CAROUSEL_CANDIDATE_LIMIT)
        );
        List<HomepageDtos.CarouselCandidate> candidates = collections.stream()
                .map(this::toCarouselCandidate)
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
        Set<Long> collectionIds = new HashSet<>();
        for (HomepageDtos.CarouselItemRequest item : items) {
            if (!collectionIds.add(item.collectionId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "HOMEPAGE_CAROUSEL_DUPLICATE",
                        "The same collection cannot be configured twice");
            }
            requirePublicCarouselCandidate(item.collectionId());
        }
    }

    private HomepageDtos.CarouselCandidate requirePublicCarouselCandidate(Long collectionId) {
        WorkCollection collection = workCollectionRepository.findById(collectionId)
                .orElseThrow(() -> invalidCarouselTarget(collectionId));
        HomepageDtos.CarouselCandidate candidate = toCarouselCandidate(collection);
        if (candidate == null) {
            throw invalidCarouselTarget(collectionId);
        }
        return candidate;
    }

    private HomepageDtos.CarouselCandidate toCarouselCandidate(WorkCollection collection) {
        CollectionPhoto cover = collection.getCoverPhotoId() == null
                ? null
                : photoRepository.findById(collection.getCoverPhotoId()).orElse(null);
        MediaAsset asset = cover == null
                ? null
                : assetRepository.findById(cover.getAssetId()).orElse(null);
        if (carouselInvalidReason(collection, cover, asset) != null) {
            return null;
        }
        WeddingProject project = publicProject(collection.getProjectId());
        return new HomepageDtos.CarouselCandidate(
                collection.getId(),
                collection.getTitle(),
                collection.getDescription(),
                project == null ? null : project.getEventDate(),
                project == null ? null : project.getLocationText(),
                publicUrl(asset.getOriginalPath()),
                publicUrl(asset.getPreviewPath()),
                publicUrl(asset.getThumbnailPath()),
                asset.getWidth(),
                asset.getHeight()
        );
    }

    private HomepageDtos.CarouselItem toCarouselItem(HomepageCarouselItem item) {
        WorkCollection collection = workCollectionRepository.findById(item.getCollectionId()).orElse(null);
        CollectionPhoto cover = collection == null || collection.getCoverPhotoId() == null
                ? null
                : photoRepository.findById(collection.getCoverPhotoId()).orElse(null);
        MediaAsset asset = cover == null
                ? null
                : assetRepository.findById(cover.getAssetId()).orElse(null);
        WeddingProject project = collection == null ? null : publicProject(collection.getProjectId());
        String invalidReason = carouselInvalidReason(collection, cover, asset);
        return new HomepageDtos.CarouselItem(
                item.getId(),
                item.getCollectionId(),
                collection == null ? null : collection.getTitle(),
                collection == null ? null : collection.getDescription(),
                project == null ? null : project.getEventDate(),
                project == null ? null : project.getLocationText(),
                asset == null ? null : publicUrlOrNull(asset.getOriginalPath()),
                asset == null ? null : publicUrlOrNull(asset.getPreviewPath()),
                asset == null ? null : publicUrlOrNull(asset.getThumbnailPath()),
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

    private String carouselInvalidReason(
            WorkCollection collection,
            CollectionPhoto cover,
            MediaAsset asset
    ) {
        if (collection == null
                || Boolean.TRUE.equals(collection.getDeleted())) {
            return "COLLECTION_NOT_AVAILABLE";
        }
        if (ReviewStatus.APPROVED != collection.getReviewStatus()
                || PublishStatus.PUBLISHED != collection.getPublishStatus()
                || ContentVisibility.PUBLIC != collection.getVisibility()) {
            return "COLLECTION_NOT_PUBLIC";
        }
        if (collection.getCoverPhotoId() == null) {
            return "COLLECTION_COVER_REQUIRED";
        }
        if (cover == null
                || !collection.getId().equals(cover.getCollectionId())
                || Boolean.TRUE.equals(cover.getDeleted())
                || ReviewStatus.APPROVED != cover.getReviewStatus()) {
            return "COLLECTION_COVER_NOT_AVAILABLE";
        }
        if (asset == null
                || Boolean.TRUE.equals(asset.getDeleted())
                || !"SUCCESS".equals(asset.getProcessStatus())
                || asset.getOriginalPath() == null
                || asset.getPreviewPath() == null
                || asset.getThumbnailPath() == null) {
            return "ASSET_NOT_AVAILABLE";
        }
        return null;
    }

    private WeddingProject publicProject(Long projectId) {
        if (projectId == null) {
            return null;
        }
        return projectRepository.findByIdAndDeletedFalse(projectId)
                .filter(project -> PublishStatus.PUBLISHED == project.getPublishStatus())
                .filter(project -> ContentVisibility.PUBLIC == project.getVisibility())
                .orElse(null);
    }

    private ApiException invalidCarouselTarget(Long collectionId) {
        return new ApiException(HttpStatus.BAD_REQUEST, "HOMEPAGE_CAROUSEL_TARGET_INVALID",
                "Homepage carousel collections must be approved, public, published, and have a valid cover: "
                        + collectionId);
    }

    private String publicUrl(String relativePath) {
        return "/media/" + relativePath.replace('\\', '/');
    }

    private String publicUrlOrNull(String relativePath) {
        return relativePath == null ? null : publicUrl(relativePath);
    }

    private void validateItems(List<HomepageDtos.FeatureItemRequest> items) {
        Set<String> uniqueTargets = new HashSet<>();
        int projectCount = 0;
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
                    throw invalidTarget(item);
                }
                case FEEDBACK -> {
                    feedbackCount++;
                    if (!feedbackService.isPublicFeedback(item.targetId())) {
                        throw invalidTarget(item);
                    }
                }
            }
        }
        if (projectCount > PROJECT_LIMIT || feedbackCount > FEEDBACK_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HOMEPAGE_FEATURE_LIMIT",
                    "Homepage feature limits are 6 projects and 6 feedback items");
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
            case COLLECTION -> false;
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
