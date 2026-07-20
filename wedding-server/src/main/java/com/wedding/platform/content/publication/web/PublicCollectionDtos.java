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

    public record CollectionSummary(
            Long id,
            String title,
            String description,
            LocalDate eventDate,
            String regionCode,
            String locationText,
            CategorySummary category,
            List<TagSummary> tags,
            String coverOriginalUrl,
            String coverPreviewUrl,
            String coverThumbnailUrl,
            Instant publishedAt,
            Boolean featured,
            Boolean pinned,
            List<CreatorSummary> creators
    ) {
    }

    public record PublicPhoto(
            Long id,
            Integer width,
            Integer height,
            String originalUrl,
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
