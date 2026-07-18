package com.wedding.platform.content.publication.web;

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
            String coverPreviewUrl,
            String coverThumbnailUrl,
            Instant publishedAt
    ) {
    }

    public record ProjectDetail(
            ProjectSummary project,
            List<CreatorSummary> creators,
            List<PublicCollectionDtos.CollectionSummary> collections
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
