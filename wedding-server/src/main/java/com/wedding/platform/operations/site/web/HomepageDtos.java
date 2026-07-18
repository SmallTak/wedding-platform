package com.wedding.platform.operations.site.web;

import com.wedding.platform.content.publication.web.PublicCollectionDtos;
import com.wedding.platform.content.publication.web.PublicProjectDtos;
import com.wedding.platform.operations.feedback.web.PublicFeedbackDtos;
import com.wedding.platform.operations.site.persistence.entity.HomepageFeatureTargetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

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

    public record PublicHomepage(
            List<PublicProjectDtos.ProjectSummary> projects,
            List<PublicCollectionDtos.CollectionSummary> collections,
            List<PublicFeedbackDtos.Feedback> feedback
    ) {
    }
}
