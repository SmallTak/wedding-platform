package com.wedding.platform.content.collection.web;

import com.wedding.platform.content.shared.ContentVisibility;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.content.shared.ReviewStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class CollectionDtos {

    private CollectionDtos() {
    }

    public record CreateCollectionRequest(
            Long projectId,
            @NotBlank(message = "Title is required")
            @Size(max = 200, message = "Title is too long")
            String title,
            @Size(max = 5000, message = "Description is too long")
            String description,
            @NotNull(message = "Category id is required")
            Long categoryId,
            @NotNull(message = "Tag ids are required")
            @Size(max = 20, message = "A collection cannot have more than 20 tags")
            List<@NotNull(message = "Tag id is required") Long> tagIds
    ) {
    }

    public record UpdateCollectionRequest(
            @NotNull(message = "Version is required")
            @PositiveOrZero(message = "Version must not be negative")
            Long version,
            Long projectId,
            @NotBlank(message = "Title is required")
            @Size(max = 200, message = "Title is too long")
            String title,
            @Size(max = 5000, message = "Description is too long")
            String description,
            @NotNull(message = "Category id is required")
            Long categoryId,
            @NotNull(message = "Tag ids are required")
            @Size(max = 20, message = "A collection cannot have more than 20 tags")
            List<@NotNull(message = "Tag id is required") Long> tagIds
    ) {
    }

    public record AssignCollectionCreatorsRequest(
            @NotNull(message = "Version is required")
            @PositiveOrZero(message = "Version must not be negative")
            Long version,
            @NotNull(message = "Creator user ids are required")
            @Size(max = 100, message = "A collection cannot have more than 100 creators")
            List<@NotNull(message = "Creator user id is required") Long> creatorUserIds
    ) {
    }

    public record CategorySummary(Long id, String name) {
    }

    public record TagSummary(Long id, String name) {
    }

    public record ProjectSummary(Long id, String projectCode, String title) {
    }

    public record CreatorResponse(
            Long userId,
            String displayName,
            String avatarPath,
            String accountStatus,
            List<String> professionalRoles,
            Instant joinedAt
    ) {
    }

    public record CollectionOptionsResponse(
            List<CategorySummary> categories,
            List<TagSummary> tags
    ) {
    }

    public record CollectionResponse(
            Long id,
            ProjectSummary project,
            String title,
            String description,
            CategorySummary category,
            List<TagSummary> tags,
            Long coverPhotoId,
            ContentVisibility visibility,
            ReviewStatus reviewStatus,
            PublishStatus publishStatus,
            Integer sortOrder,
            Boolean featured,
            Boolean pinned,
            Long createdBy,
            Long updatedBy,
            Instant createdAt,
            Instant updatedAt,
            Long version,
            List<CreatorResponse> creators
    ) {
    }

    public record CollectionPageResponse(
            List<CollectionResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}
