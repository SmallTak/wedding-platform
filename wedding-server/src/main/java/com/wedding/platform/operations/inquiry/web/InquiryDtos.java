package com.wedding.platform.operations.inquiry.web;

import com.wedding.platform.operations.inquiry.persistence.entity.InquiryFollowStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class InquiryDtos {

    private InquiryDtos() {
    }

    public record CreateInquiryRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 100, message = "Name is too long")
            String name,
            @NotBlank(message = "Contact is required")
            @Size(min = 5, max = 120, message = "Contact must contain 5 to 120 characters")
            String contact,
            LocalDate weddingDate,
            @Size(max = 200, message = "Region is too long") String region,
            @NotBlank(message = "Service needs are required")
            @Size(max = 1000, message = "Service needs are too long")
            String serviceNeeds,
            @Size(max = 2000, message = "Remark is too long") String remark,
            @Size(max = 200, message = "Website field is too long") String website
    ) {
    }

    public record InquiryReceipt(String referenceCode, Instant submittedAt) {
    }

    public record UpdateInquiryRequest(
            @NotNull(message = "Version is required")
            @PositiveOrZero(message = "Version must not be negative")
            Long version,
            @NotNull(message = "Follow status is required")
            InquiryFollowStatus followStatus,
            @Size(max = 2000, message = "Follow note is too long") String followNote
    ) {
    }

    public record InquiryResponse(
            Long id,
            String referenceCode,
            String name,
            String contact,
            LocalDate weddingDate,
            String region,
            String serviceNeeds,
            String remark,
            InquiryFollowStatus followStatus,
            String followNote,
            Long assignedAdminId,
            String source,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
    }

    public record InquiryPage(
            List<InquiryResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}
