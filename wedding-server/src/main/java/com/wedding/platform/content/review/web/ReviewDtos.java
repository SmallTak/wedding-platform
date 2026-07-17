package com.wedding.platform.content.review.web;

import com.wedding.platform.content.collection.web.CollectionDtos;
import com.wedding.platform.content.media.web.PhotoDtos;
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

    public record OfflineCollectionRequest(
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
            PhotoDtos.PhotoBatchResponse photoBatch
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
