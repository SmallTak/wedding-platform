package com.wedding.platform.content.publication.web;

import com.wedding.platform.operations.feedback.web.PublicFeedbackDtos;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PublicProjectDtos {

    private PublicProjectDtos() {
    }

    public record CreatorSummary(
            Long id,
            String displayName,
            List<String> professionalRoles
    ) {
    }

    public record ProjectSummary(
            Long id,
            String title,
            String coupleDisplayName,
            LocalDate eventDate,
            String regionCode,
            String locationText,
            String description,
            String coverOriginalUrl,
            String coverPreviewUrl,
            String coverThumbnailUrl,
            Instant publishedAt
    ) {
    }

    public record ProjectDetail(
            ProjectSummary project,
            List<CreatorSummary> creators,
            List<PublicCollectionDtos.CollectionSummary> collections,
            List<PublicFeedbackDtos.Feedback> feedback
    ) {
    }

    public record ProjectPage(
            List<ProjectSummary> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}
