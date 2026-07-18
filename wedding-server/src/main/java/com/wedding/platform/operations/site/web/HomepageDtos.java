package com.wedding.platform.operations.site.web;

import com.wedding.platform.content.publication.web.PublicCollectionDtos;
import com.wedding.platform.content.publication.web.PublicProjectDtos;
import com.wedding.platform.operations.feedback.web.PublicFeedbackDtos;
import com.wedding.platform.operations.site.persistence.entity.HomepageFeatureTargetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class HomepageDtos {

    private HomepageDtos() {
    }

    public record FeatureItemRequest(
            @NotNull(message = "Target type is required")
            HomepageFeatureTargetType targetType,
            @NotNull(message = "Target id is required")
            Long targetId,
            @NotNull(message = "Sort order is required")
            @PositiveOrZero(message = "Sort order must not be negative")
            Integer sortOrder,
            @NotNull(message = "Pinned state is required")
            Boolean pinned
    ) {
    }

    public record ReplaceFeaturesRequest(
            @NotNull(message = "Feature items are required")
            @Size(max = 60, message = "Homepage cannot contain more than 60 configured features")
            List<@Valid FeatureItemRequest> items
    ) {
    }

    public record FeatureItem(
            Long id,
            HomepageFeatureTargetType targetType,
            Long targetId,
            Integer sortOrder,
            Boolean pinned,
            Long version
    ) {
    }

    public record FeatureOptions(
            List<PublicProjectDtos.ProjectSummary> projects,
            List<PublicCollectionDtos.CollectionSummary> collections,
            List<PublicFeedbackDtos.Feedback> feedback,
            List<FeatureItem> features
    ) {
    }

    public record CarouselItemRequest(
            @NotNull(message = "Photo id is required")
            Long photoId,
            @NotNull(message = "Sort order is required")
            @PositiveOrZero(message = "Sort order must not be negative")
            Integer sortOrder,
            @NotNull(message = "Horizontal focal point is required")
            @DecimalMin(value = "0.0", message = "Horizontal focal point must be at least 0")
            @DecimalMax(value = "100.0", message = "Horizontal focal point must not exceed 100")
            BigDecimal focalX,
            @NotNull(message = "Vertical focal point is required")
            @DecimalMin(value = "0.0", message = "Vertical focal point must be at least 0")
            @DecimalMax(value = "100.0", message = "Vertical focal point must not exceed 100")
            BigDecimal focalY
    ) {
    }

    public record ReplaceCarouselRequest(
            @NotNull(message = "Carousel items are required")
            @Size(max = 5, message = "Homepage carousel cannot contain more than 5 images")
            List<@Valid CarouselItemRequest> items
    ) {
    }

    public record CarouselCandidate(
            Long photoId,
            Long collectionId,
            String collectionTitle,
            String previewUrl,
            String thumbnailUrl,
            Integer width,
            Integer height,
            Integer photoSortOrder
    ) {
    }

    public record CarouselItem(
            Long id,
            Long photoId,
            Long collectionId,
            String collectionTitle,
            String previewUrl,
            String thumbnailUrl,
            Integer width,
            Integer height,
            Integer sortOrder,
            BigDecimal focalX,
            BigDecimal focalY,
            boolean valid,
            String invalidReason,
            Long version
    ) {
    }

    public record CarouselOptions(
            List<CarouselCandidate> candidates,
            List<CarouselItem> items
    ) {
    }

    public record CarouselSlide(
            Long photoId,
            Long collectionId,
            String collectionTitle,
            String previewUrl,
            String thumbnailUrl,
            Integer width,
            Integer height,
            BigDecimal focalX,
            BigDecimal focalY
    ) {
    }

    public record PublicHomepage(
            List<CarouselSlide> carousel,
            List<PublicProjectDtos.ProjectSummary> projects,
            List<PublicCollectionDtos.CollectionSummary> collections,
            List<PublicFeedbackDtos.Feedback> feedback
    ) {
    }
}
