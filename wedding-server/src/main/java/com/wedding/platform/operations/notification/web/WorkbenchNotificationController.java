package com.wedding.platform.operations.notification.web;

import com.wedding.platform.operations.notification.application.UserNotificationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasAnyRole('ADMIN', 'CREATOR') and hasAuthority('/notifications')")
public class WorkbenchNotificationController {

    private final UserNotificationService notificationService;

    public WorkbenchNotificationController(UserNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public UserNotificationDtos.NotificationPage notifications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly
    ) {
        return notificationService.workbenchNotifications(userId(jwt), page, size, unreadOnly);
    }

    @GetMapping("/unread-count")
    public UserNotificationDtos.UnreadCountResponse unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return notificationService.workbenchUnreadCount(userId(jwt));
    }

    @PostMapping("/{notificationId}/read")
    public UserNotificationDtos.NotificationResponse markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long notificationId,
            @Valid @RequestBody UserNotificationDtos.MarkReadRequest request
    ) {
        return notificationService.markWorkbenchRead(userId(jwt), notificationId, request);
    }

    @PostMapping("/read-all")
    public UserNotificationDtos.MarkAllReadResponse markAllRead(@AuthenticationPrincipal Jwt jwt) {
        return notificationService.markAllWorkbenchRead(userId(jwt));
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getClaimAsString("uid"));
    }
}
