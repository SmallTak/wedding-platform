package com.wedding.platform.content.media.web;

import com.wedding.platform.content.shared.ReviewStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class PhotoDtos {

    private PhotoDtos() {
    }

    public record ReorderPhotosRequest(
            @NotNull(message = "Version is required")
            @PositiveOrZero(message = "Version must not be negative")
            Long version,
            @NotNull(message = "Photo ids are required")
            @Size(max = 1000, message = "A collection cannot reorder more than 1000 photos at once")
            List<@NotNull(message = "Photo id is required") Long> photoIds
    ) {
    }

    public record SetCoverRequest(
            @NotNull(message = "Version is required")
            @PositiveOrZero(message = "Version must not be negative")
            Long version,
            @NotNull(message = "Photo id is required")
            Long photoId
    ) {
    }

    public record PhotoResponse(
            Long id,
            String originalName,
            String mimeType,
            Long fileSize,
            Integer width,
            Integer height,
            String previewUrl,
            String thumbnailUrl,
            String checksum,
            Integer sortOrder,
            ReviewStatus reviewStatus,
            String rejectionReason,
            Instant submittedAt,
            Instant reviewedAt,
            Long reviewedBy,
            Long createdBy,
            Instant createdAt
    ) {
    }

    public record PhotoBatchResponse(
            Long collectionId,
            Long collectionVersion,
            Long coverPhotoId,
            List<PhotoResponse> photos
    ) {
    }
}
