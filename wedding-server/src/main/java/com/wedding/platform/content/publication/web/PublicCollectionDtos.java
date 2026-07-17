package com.wedding.platform.content.publication.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PublicCollectionDtos {

    private PublicCollectionDtos() {
    }

    public record CategorySummary(Long id, String name) {
    }

    public record TagSummary(Long id, String name) {
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
            LocalDate eventDate,
            String locationText
    ) {
    }

    public record CollectionSummary(
            Long id,
            String title,
            String description,
            CategorySummary category,
            List<TagSummary> tags,
            String coverPreviewUrl,
            String coverThumbnailUrl,
            Instant publishedAt,
            Boolean featured,
            Boolean pinned,
            List<CreatorSummary> creators,
            ProjectSummary project
    ) {
    }

    public record PublicPhoto(
            Long id,
            Integer width,
            Integer height,
            String previewUrl,
            String thumbnailUrl,
            Integer sortOrder
    ) {
    }

    public record CollectionDetail(
            CollectionSummary collection,
            List<PublicPhoto> photos
    ) {
    }

    public record CollectionPage(
            List<CollectionSummary> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}
