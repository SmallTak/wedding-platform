package com.wedding.platform.operations.site.application;

import com.wedding.platform.content.publication.application.PublicCollectionService;
import com.wedding.platform.content.publication.application.PublicProjectService;
import com.wedding.platform.content.publication.web.PublicCollectionDtos;
import com.wedding.platform.content.publication.web.PublicProjectDtos;
import com.wedding.platform.operations.feedback.application.PublicFeedbackService;
import com.wedding.platform.operations.feedback.web.PublicFeedbackDtos;
import com.wedding.platform.operations.site.persistence.entity.HomepageFeature;
import com.wedding.platform.operations.site.persistence.entity.HomepageFeatureTargetType;
import com.wedding.platform.operations.site.persistence.repository.HomepageFeatureRepository;
import com.wedding.platform.operations.site.web.HomepageDtos;
import com.wedding.platform.platform.audit.AuditLogService;
import com.wedding.platform.platform.web.ApiException;
import com.wedding.platform.system.account.persistence.entity.SystemUser;
import com.wedding.platform.system.account.persistence.repository.SystemUserRepository;
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
    private static final int COLLECTION_LIMIT = 12;
    private static final int FEEDBACK_LIMIT = 6;

    private final HomepageFeatureRepository featureRepository;
    private final PublicProjectService projectService;
    private final PublicCollectionService collectionService;
    private final PublicFeedbackService feedbackService;
    private final SystemUserRepository userRepository;
    private final AuditLogService auditLogService;

    public HomepageService(
            HomepageFeatureRepository featureRepository,
            PublicProjectService projectService,
            PublicCollectionService collectionService,
            PublicFeedbackService feedbackService,
            SystemUserRepository userRepository,
            AuditLogService auditLogService
    ) {
        this.featureRepository = featureRepository;
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
        return new HomepageDtos.PublicHomepage(projects, collections, feedback);
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
