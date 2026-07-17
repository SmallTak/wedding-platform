package com.wedding.platform.content.collection.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class ContentConfigDtos {

    private ContentConfigDtos() {
    }

    public record CreateCategoryRequest(
            @NotBlank(message = "Category name is required")
            @Size(max = 100, message = "Category name is too long")
            String name,
            @Size(max = 500, message = "Category description is too long")
            String description,
            @NotNull(message = "Sort order is required")
            @PositiveOrZero(message = "Sort order must not be negative")
            Integer sortOrder
    ) {
    }

    public record UpdateCategoryRequest(
            @NotNull(message = "Version is required")
            @PositiveOrZero(message = "Version must not be negative")
            Long version,
            @NotBlank(message = "Category name is required")
            @Size(max = 100, message = "Category name is too long")
            String name,
            @Size(max = 500, message = "Category description is too long")
            String description,
            @NotNull(message = "Sort order is required")
            @PositiveOrZero(message = "Sort order must not be negative")
            Integer sortOrder,
            @NotBlank(message = "Status is required")
            @Pattern(regexp = "ACTIVE|DISABLED", message = "Status must be ACTIVE or DISABLED")
            String status
    ) {
    }

    public record CreateTagRequest(
            @NotBlank(message = "Tag name is required")
            @Size(max = 100, message = "Tag name is too long")
            String name,
            @NotNull(message = "Sort order is required")
            @PositiveOrZero(message = "Sort order must not be negative")
            Integer sortOrder
    ) {
    }

    public record UpdateTagRequest(
            @NotNull(message = "Version is required")
            @PositiveOrZero(message = "Version must not be negative")
            Long version,
            @NotBlank(message = "Tag name is required")
            @Size(max = 100, message = "Tag name is too long")
            String name,
            @NotNull(message = "Sort order is required")
            @PositiveOrZero(message = "Sort order must not be negative")
            Integer sortOrder,
            @NotBlank(message = "Status is required")
            @Pattern(regexp = "ACTIVE|DISABLED", message = "Status must be ACTIVE or DISABLED")
            String status
    ) {
    }

    public record CategoryResponse(
            Long id,
            String name,
            String description,
            Integer sortOrder,
            String status,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record TagResponse(
            Long id,
            String name,
            Integer sortOrder,
            String status,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
