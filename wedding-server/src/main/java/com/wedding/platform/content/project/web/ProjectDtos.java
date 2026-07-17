package com.wedding.platform.content.project.web;

import com.wedding.platform.content.shared.ContentVisibility;
import com.wedding.platform.content.shared.PublishStatus;
import com.wedding.platform.content.shared.ReviewStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record CreateProjectRequest(
            @NotBlank(message = "Title is required")
            @Size(max = 200, message = "Title is too long")
            String title,
            @Size(max = 100, message = "Couple display name is too long")
            String coupleDisplayName,
            @NotNull(message = "Event date is required")
            LocalDate eventDate,
            @NotBlank(message = "Region code is required")
            @Size(max = 64, message = "Region code is too long")
            String regionCode,
            @NotBlank(message = "Location is required")
            @Size(max = 300, message = "Location is too long")
            String locationText,
            @Size(max = 5000, message = "Description is too long")
            String description
    ) {
    }

    public record UpdateProjectRequest(
            @NotNull(message = "Version is required")
            @PositiveOrZero(message = "Version must not be negative")
            Long version,
            @NotBlank(message = "Title is required")
            @Size(max = 200, message = "Title is too long")
            String title,
            @Size(max = 100, message = "Couple display name is too long")
            String coupleDisplayName,
            @NotNull(message = "Event date is required")
            LocalDate eventDate,
            @NotBlank(message = "Region code is required")
            @Size(max = 64, message = "Region code is too long")
            String regionCode,
            @NotBlank(message = "Location is required")
            @Size(max = 300, message = "Location is too long")
            String locationText,
            @Size(max = 5000, message = "Description is too long")
            String description
    ) {
    }

    public record AssignProjectCreatorsRequest(
            @NotNull(message = "Version is required")
            @PositiveOrZero(message = "Version must not be negative")
            Long version,
            @NotNull(message = "Creator user ids are required")
            @Size(max = 100, message = "A project cannot have more than 100 creators")
            List<@NotNull(message = "Creator user id is required") Long> creatorUserIds
    ) {
    }

    public record ProjectCreatorResponse(
            Long userId,
            String displayName,
            String avatarPath,
            String accountStatus,
            List<String> professionalRoles,
            Instant assignedAt
    ) {
    }

    public record ProjectResponse(
            Long id,
            String projectCode,
            String title,
            String coupleDisplayName,
            LocalDate eventDate,
            String regionCode,
            String locationText,
            String description,
            Long coverAssetId,
            ContentVisibility visibility,
            ReviewStatus reviewStatus,
            PublishStatus publishStatus,
            String rejectionReason,
            Instant submittedAt,
            Instant reviewedAt,
            Long reviewedBy,
            Instant publishedAt,
            Long publishedBy,
            String offlineReason,
            Long createdBy,
            Long updatedBy,
            Instant createdAt,
            Instant updatedAt,
            Long version,
            List<ProjectCreatorResponse> creators
    ) {
    }

    public record ProjectPageResponse(
            List<ProjectResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}
