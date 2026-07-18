package com.wedding.platform.operations.notification.web;

import com.wedding.platform.operations.notification.persistence.entity.UserNotificationRelatedType;
import com.wedding.platform.operations.notification.persistence.entity.UserNotificationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.List;

public final class UserNotificationDtos {

    private UserNotificationDtos() {
    }

    public record MarkReadRequest(
            @NotNull(message = "Version is required")
            @PositiveOrZero(message = "Version must not be negative")
            Long version
    ) {
    }

    public record NotificationResponse(
            Long id,
            UserNotificationType type,
            String title,
            String content,
            UserNotificationRelatedType relatedType,
            Long relatedId,
            Instant readAt,
            Instant createdAt,
            Long version
    ) {
    }

    public record NotificationPage(
            List<NotificationResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            long unreadCount
    ) {
    }

    public record UnreadCountResponse(long unreadCount) {
    }

    public record MarkAllReadResponse(long updatedCount, Instant readAt) {
    }
}
