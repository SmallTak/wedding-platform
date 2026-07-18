package com.wedding.platform.content.project.web;

import com.wedding.platform.content.shared.ContentVisibility;
import com.wedding.platform.content.shared.PublishStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class CustomerProjectDtos {

    private CustomerProjectDtos() {
    }

    public record ApplyRequest(
            @PositiveOrZero(message = "Version must not be negative")
            Long version,
            @NotBlank(message = "Project code is required")
            @Pattern(regexp = "^[A-Za-z0-9_-]{4,32}$", message = "Project code format is invalid")
            String projectCode,
            @NotBlank(message = "Application note is required")
            @Size(max = 1000, message = "Application note is too long")
            String applyNote
    ) {
    }

    public record ReviewRequest(
            @NotNull(message = "Version is required")
            @PositiveOrZero(message = "Version must not be negative")
            Long version,
            @Size(max = 500, message = "Rejection reason is too long")
            String reason
    ) {
    }

    public record ProjectSummary(
            Long id,
            String projectCode,
            String title,
            String coupleDisplayName,
            LocalDate eventDate,
            String locationText,
            ContentVisibility visibility,
            PublishStatus publishStatus,
            boolean detailsVisible,
            boolean publicDetailAvailable
    ) {
    }

    public record CustomerSummary(
            Long id,
            String mobile,
            String nickname
    ) {
    }

    public record ApplicationResponse(
            Long id,
            ProjectSummary project,
            CustomerSummary customer,
            String applyNote,
            String status,
            Long reviewedBy,
            Instant reviewedAt,
            String rejectionReason,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
    }

    public record ApplicationPage(
            List<ApplicationResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}
