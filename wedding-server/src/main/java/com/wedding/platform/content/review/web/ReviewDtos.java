package com.wedding.platform.content.review.web;

import com.wedding.platform.content.collection.web.CollectionDtos;
import com.wedding.platform.content.media.web.PhotoDtos;
import com.wedding.platform.content.project.web.ProjectDtos;
import com.wedding.platform.content.review.persistence.entity.ReviewItemStatus;
import com.wedding.platform.content.review.persistence.entity.ReviewItemType;
import com.wedding.platform.content.review.persistence.entity.ReviewTargetType;
import com.wedding.platform.content.review.persistence.entity.ReviewTaskStatus;
import com.wedding.platform.content.shared.ContentVisibility;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.content.shared.ReviewStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    public enum PhotoDecision {
        APPROVE,
        REJECT
    }

    public enum ReviewDecision {
        APPROVE,
        REJECT
    }

    public record VersionRequest(
            @NotNull(message = "Version is required")
            @PositiveOrZero(message = "Version must not be negative")
            Long version
    ) {
    }

    public record ReviewPhotosRequest(
            @NotNull(message = "Version is required")
            @PositiveOrZero(message = "Version must not be negative")
            Long version,
            @NotEmpty(message = "Photo ids are required")
            @Size(max = 1000, message = "A review batch cannot contain more than 1000 photos")
            List<@NotNull(message = "Photo id is required") Long> photoIds,
            @NotNull(message = "Decision is required")
            PhotoDecision decision,
            @Size(max = 500, message = "Rejection reason is too long")
            String reason
    ) {
    }

    public record ReviewFieldsRequest(
            @NotNull(message = "Version is required")
            @PositiveOrZero(message = "Version must not be negative")
            Long version,
            @NotEmpty(message = "Review item ids are required")
            @Size(max = 100, message = "A field review batch cannot contain more than 100 items")
            List<@NotNull(message = "Review item id is required") Long> reviewItemIds,
            @NotNull(message = "Decision is required")
            ReviewDecision decision,
            @Size(max = 500, message = "Rejection reason is too long")
            String reason
    ) {
    }

    public record RejectCollectionRequest(
            @NotNull(message = "Version is required")
            @PositiveOrZero(message = "Version must not be negative")
            Long version,
            @NotBlank(message = "Rejection reason is required")
            @Size(max = 500, message = "Rejection reason is too long")
            String reason
    ) {
    }

    public record PublishCollectionRequest(
            @NotNull(message = "Version is required")
            @PositiveOrZero(message = "Version must not be negative")
            Long version,
            @NotNull(message = "Visibility is required")
            ContentVisibility visibility,
            @NotNull(message = "Featured flag is required")
            Boolean featured,
            @NotNull(message = "Pinned flag is required")
            Boolean pinned,
            @NotNull(message = "Sort order is required")
            Integer sortOrder
    ) {
    }

    public record PublishProjectRequest(
            @NotNull(message = "Version is required")
            @PositiveOrZero(message = "Version must not be negative")
            Long version,
            @NotNull(message = "Visibility is required")
            ContentVisibility visibility
    ) {
    }

    public record OfflineCollectionRequest(
            @NotNull(message = "Version is required")
            @PositiveOrZero(message = "Version must not be negative")
            Long version,
            @NotBlank(message = "Offline reason is required")
            @Size(max = 500, message = "Offline reason is too long")
            String reason
    ) {
    }

    public record OfflineProjectRequest(
            @NotNull(message = "Version is required")
            @PositiveOrZero(message = "Version must not be negative")
            Long version,
            @NotBlank(message = "Offline reason is required")
            @Size(max = 500, message = "Offline reason is too long")
            String reason
    ) {
    }

    public record ReviewQueueItem(
            Long id,
            String title,
            String categoryName,
            String coverThumbnailUrl,
            ReviewStatus reviewStatus,
            PublishStatus publishStatus,
            String rejectionReason,
            Instant submittedAt,
            Instant updatedAt,
            Long version,
            long pendingFields,
            long rejectedFields,
            long approvedFields,
            long totalPhotos,
            long pendingPhotos,
            long rejectedPhotos,
            long approvedPhotos
    ) {
    }

    public record ReviewQueueResponse(
            List<ReviewQueueItem> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record ReviewDetailResponse(
            CollectionDtos.CollectionResponse collection,
            PhotoDtos.PhotoBatchResponse photoBatch,
            ReviewHistoryResponse reviewHistory
    ) {
    }

    public record ProjectReviewDetailResponse(
            ProjectDtos.ProjectResponse project,
            ReviewHistoryResponse reviewHistory
    ) {
    }

    public record ReviewItemResponse(
            Long id,
            ReviewItemType itemType,
            Long businessId,
            String fieldKey,
            String fieldLabel,
            String snapshotJson,
            String displayValue,
            Integer revisionNo,
            ReviewItemStatus status,
            String rejectionReason,
            Long reviewedBy,
            Instant reviewedAt,
            Boolean current
    ) {
    }

    public record ReviewRevisionResponse(
            Long id,
            ReviewTargetType targetType,
            Long targetId,
            Integer revisionNo,
            Long submittedBy,
            Instant submittedAt,
            ReviewTaskStatus status,
            List<ReviewItemResponse> items
    ) {
    }

    public record ReviewHistoryResponse(
            List<ReviewItemResponse> currentItems,
            List<ReviewRevisionResponse> revisions
    ) {
    }

    public record ProjectReviewQueueItem(
            Long id,
            String projectCode,
            String title,
            String locationText,
            ReviewStatus reviewStatus,
            PublishStatus publishStatus,
            String rejectionReason,
            Instant submittedAt,
            Instant updatedAt,
            Long version,
            long pendingFields,
            long rejectedFields,
            long approvedFields
    ) {
    }

    public record ProjectReviewQueueResponse(
            List<ProjectReviewQueueItem> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record DashboardOverviewResponse(
            long pendingReviews,
            long rejectedCollections,
            long readyToPublish,
            long publishedCollections,
            List<ReviewQueueItem> recentReviews
    ) {
    }
}
