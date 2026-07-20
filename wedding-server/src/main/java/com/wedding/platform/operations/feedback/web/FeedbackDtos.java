package com.wedding.platform.operations.feedback.web;

import com.wedding.platform.operations.feedback.persistence.entity.FeedbackPublishStatus;
import com.wedding.platform.operations.feedback.persistence.entity.FeedbackReviewStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class FeedbackDtos {

    private FeedbackDtos() {
    }

    public record UpsertFeedbackRequest(
            @PositiveOrZero(message = "Version must not be negative") Long version,
            @NotNull(message = "Collection id is required") Long collectionId,
            @NotNull(message = "Creator user id is required") Long creatorUserId,
            @NotBlank(message = "Customer display name is required")
            @Size(max = 100, message = "Customer display name is too long")
            String customerDisplayName,
            @NotNull(message = "Rating is required")
            @Min(value = 1, message = "Rating must be between 1 and 5")
            @Max(value = 5, message = "Rating must be between 1 and 5")
            Integer rating,
            @NotBlank(message = "Feedback content is required")
            @Size(max = 2000, message = "Feedback content is too long")
            String content
    ) {
    }

    public record CustomerFeedbackRequest(
            @PositiveOrZero(message = "Version must not be negative") Long version,
            @NotNull(message = "Collection id is required") Long collectionId,
            @NotNull(message = "Creator user id is required") Long creatorUserId,
            @NotNull(message = "Rating is required")
            @Min(value = 1, message = "Rating must be between 1 and 5")
            @Max(value = 5, message = "Rating must be between 1 and 5")
            Integer rating,
            @NotBlank(message = "Feedback content is required")
            @Size(max = 2000, message = "Feedback content is too long")
            String content
    ) {
    }

    public record RejectRequest(
            @NotNull(message = "Version is required")
            @PositiveOrZero(message = "Version must not be negative")
            Long version,
            @NotBlank(message = "Rejection reason is required")
            @Size(max = 500, message = "Rejection reason is too long")
            String reason
    ) {
    }

    public record VersionRequest(
            @NotNull(message = "Version is required")
            @PositiveOrZero(message = "Version must not be negative")
            Long version
    ) {
    }

    public record OfflineRequest(
            @NotNull(message = "Version is required")
            @PositiveOrZero(message = "Version must not be negative")
            Long version,
            @NotBlank(message = "Offline reason is required")
            @Size(max = 500, message = "Offline reason is too long")
            String reason
    ) {
    }

    public record UpsertReplyRequest(
            @PositiveOrZero(message = "Version must not be negative") Long version,
            @NotBlank(message = "Reply content is required")
            @Size(max = 2000, message = "Reply content is too long")
            String content
    ) {
    }

    public record CollectionSummary(
            Long id,
            String title,
            LocalDate eventDate,
            String locationText,
            List<Long> creatorUserIds
    ) {
    }

    public record CreatorSummary(Long id, String displayName, List<String> professionalRoles) {
    }

    public record ReplyResponse(
            Long id,
            String content,
            FeedbackReviewStatus reviewStatus,
            String rejectionReason,
            Long reviewedBy,
            Instant reviewedAt,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
    }

    public record FeedbackResponse(
            Long id,
            CollectionSummary collection,
            CreatorSummary creator,
            String customerDisplayName,
            Integer rating,
            String content,
            FeedbackReviewStatus reviewStatus,
            String rejectionReason,
            Long reviewedBy,
            Instant reviewedAt,
            FeedbackPublishStatus publishStatus,
            Instant publishedAt,
            String offlineReason,
            Long submittedBy,
            Instant createdAt,
            Instant updatedAt,
            Long version,
            ReplyResponse reply
    ) {
    }

    public record FeedbackPage(
            List<FeedbackResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record FeedbackOptions(
            List<CollectionSummary> collections,
            List<CreatorSummary> creators
    ) {
    }

    public record CustomerFeedbackOptions(
            List<CollectionSummary> collections,
            List<CreatorSummary> creators
    ) {
    }
}
