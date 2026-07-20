package com.wedding.platform.operations.notification.application;

import com.wedding.platform.content.collection.persistence.entity.CollectionCreator;
import com.wedding.platform.content.collection.persistence.repository.CollectionCreatorRepository;
import com.wedding.platform.operations.notification.persistence.entity.UserNotification;
import com.wedding.platform.operations.notification.persistence.entity.UserNotificationRelatedType;
import com.wedding.platform.operations.notification.persistence.entity.UserNotificationType;
import com.wedding.platform.operations.notification.persistence.repository.UserNotificationRepository;
import com.wedding.platform.operations.notification.web.UserNotificationDtos;
import com.wedding.platform.platform.web.ApiException;
import com.wedding.platform.system.account.persistence.entity.SystemUser;
import com.wedding.platform.system.account.persistence.repository.SystemUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class UserNotificationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserNotificationRepository notificationRepository;
    private final SystemUserRepository userRepository;
    private final CollectionCreatorRepository collectionCreatorRepository;

    public UserNotificationService(
            UserNotificationRepository notificationRepository,
            SystemUserRepository userRepository,
            CollectionCreatorRepository collectionCreatorRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.collectionCreatorRepository = collectionCreatorRepository;
    }

    @Transactional(readOnly = true)
    public UserNotificationDtos.NotificationPage customerNotifications(
            Long customerId,
            int page,
            int size,
            boolean unreadOnly
    ) {
        requireAccount(customerId, Set.of("CUSTOMER"));
        return list(customerId, page, size, unreadOnly);
    }

    @Transactional(readOnly = true)
    public UserNotificationDtos.NotificationPage workbenchNotifications(
            Long userId,
            int page,
            int size,
            boolean unreadOnly
    ) {
        requireAccount(userId, Set.of("ADMIN", "CREATOR"));
        return list(userId, page, size, unreadOnly);
    }

    @Transactional(readOnly = true)
    public UserNotificationDtos.UnreadCountResponse customerUnreadCount(Long customerId) {
        requireAccount(customerId, Set.of("CUSTOMER"));
        return unreadCount(customerId);
    }

    @Transactional(readOnly = true)
    public UserNotificationDtos.UnreadCountResponse workbenchUnreadCount(Long userId) {
        requireAccount(userId, Set.of("ADMIN", "CREATOR"));
        return unreadCount(userId);
    }

    @Transactional
    public UserNotificationDtos.NotificationResponse markCustomerRead(
            Long customerId,
            Long notificationId,
            UserNotificationDtos.MarkReadRequest request
    ) {
        requireAccount(customerId, Set.of("CUSTOMER"));
        return markRead(customerId, notificationId, request);
    }

    @Transactional
    public UserNotificationDtos.NotificationResponse markWorkbenchRead(
            Long userId,
            Long notificationId,
            UserNotificationDtos.MarkReadRequest request
    ) {
        requireAccount(userId, Set.of("ADMIN", "CREATOR"));
        return markRead(userId, notificationId, request);
    }

    @Transactional
    public UserNotificationDtos.MarkAllReadResponse markAllCustomerRead(Long customerId) {
        requireAccount(customerId, Set.of("CUSTOMER"));
        return markAllRead(customerId);
    }

    @Transactional
    public UserNotificationDtos.MarkAllReadResponse markAllWorkbenchRead(Long userId) {
        requireAccount(userId, Set.of("ADMIN", "CREATOR"));
        return markAllRead(userId);
    }

    public void notifyUser(
            Long recipientUserId,
            Long actorId,
            UserNotificationType type,
            String title,
            String content,
            UserNotificationRelatedType relatedType,
            Long relatedId
    ) {
        create(recipientUserId, actorId, type, title, content, relatedType, relatedId);
    }

    public void notifyAdmins(
            Long actorId,
            UserNotificationType type,
            String title,
            String content,
            UserNotificationRelatedType relatedType,
            Long relatedId
    ) {
        Set<Long> recipients = activeUsers("ADMIN");
        recipients.remove(actorId);
        notifyUsers(recipients, actorId, type, title, content, relatedType, relatedId);
    }

    public void notifyCollectionCreators(
            Long collectionId,
            Long actorId,
            UserNotificationType type,
            String title,
            String content,
            UserNotificationRelatedType relatedType
    ) {
        Set<Long> recipients = collectionCreatorRepository.findAllByCollectionId(collectionId).stream()
                .map(CollectionCreator::getId)
                .map(id -> id.getCreatorUserId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        recipients.retainAll(activeUsers("CREATOR"));
        recipients.remove(actorId);
        notifyUsers(recipients, actorId, type, title, content, relatedType, collectionId);
    }

    public void notifyFeedbackApproved(Long customerId, Long actorId, Long feedbackId) {
        create(
                customerId,
                actorId,
                UserNotificationType.FEEDBACK_APPROVED,
                "客户评价已通过审核",
                "您提交的客户评价已通过审核并公开展示。",
                UserNotificationRelatedType.FEEDBACK,
                feedbackId
        );
    }

    public void notifyFeedbackRejected(Long customerId, Long actorId, Long feedbackId, String reason) {
        create(
                customerId,
                actorId,
                UserNotificationType.FEEDBACK_REJECTED,
                "客户评价未通过审核",
                "您提交的客户评价未通过审核。原因：" + reason.trim(),
                UserNotificationRelatedType.FEEDBACK,
                feedbackId
        );
    }

    public void notifyFeedbackOffline(Long customerId, Long actorId, Long feedbackId, String reason) {
        create(
                customerId,
                actorId,
                UserNotificationType.FEEDBACK_OFFLINE,
                "客户评价已下架",
                "您已公开的客户评价已下架。原因：" + reason.trim(),
                UserNotificationRelatedType.FEEDBACK,
                feedbackId
        );
    }

    private UserNotificationDtos.NotificationPage list(
            Long userId,
            int page,
            int size,
            boolean unreadOnly
    ) {
        validatePage(page, size);
        Page<UserNotification> result = notificationRepository.findUserNotifications(
                userId,
                unreadOnly,
                PageRequest.of(page, size)
        );
        return new UserNotificationDtos.NotificationPage(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                notificationRepository.countByRecipientUserIdAndReadAtIsNullAndDeletedFalse(userId)
        );
    }

    private UserNotificationDtos.UnreadCountResponse unreadCount(Long userId) {
        return new UserNotificationDtos.UnreadCountResponse(
                notificationRepository.countByRecipientUserIdAndReadAtIsNullAndDeletedFalse(userId)
        );
    }

    private UserNotificationDtos.NotificationResponse markRead(
            Long userId,
            Long notificationId,
            UserNotificationDtos.MarkReadRequest request
    ) {
        UserNotification notification = notificationRepository
                .findByIdAndRecipientUserIdAndDeletedFalse(notificationId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOTIFICATION_NOT_FOUND",
                        "User notification was not found"));
        if (!notification.getVersion().equals(request.version())) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_NOTIFICATION_VERSION_CONFLICT",
                    "The notification was updated; reload it before continuing");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notification.setUpdatedBy(userId);
            notification = notificationRepository.saveAndFlush(notification);
        }
        return toResponse(notification);
    }

    private UserNotificationDtos.MarkAllReadResponse markAllRead(Long userId) {
        Instant readAt = Instant.now();
        int updatedCount = notificationRepository.markAllRead(userId, readAt);
        return new UserNotificationDtos.MarkAllReadResponse(updatedCount, readAt);
    }

    private void notifyUsers(
            Collection<Long> recipientUserIds,
            Long actorId,
            UserNotificationType type,
            String title,
            String content,
            UserNotificationRelatedType relatedType,
            Long relatedId
    ) {
        recipientUserIds.forEach(recipientUserId ->
                create(recipientUserId, actorId, type, title, content, relatedType, relatedId));
    }

    private void create(
            Long recipientUserId,
            Long actorId,
            UserNotificationType type,
            String title,
            String content,
            UserNotificationRelatedType relatedType,
            Long relatedId
    ) {
        UserNotification notification = new UserNotification();
        notification.setRecipientUserId(recipientUserId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRelatedType(relatedType);
        notification.setRelatedId(relatedId);
        Long auditActorId = actorId == null ? recipientUserId : actorId;
        notification.setCreatedBy(auditActorId);
        notification.setUpdatedBy(auditActorId);
        notificationRepository.save(notification);
    }

    private Set<Long> activeUsers(String accountType) {
        return userRepository.findAllByAccountTypeAndDeletedFalseOrderByCreatedAtDesc(accountType).stream()
                .filter(user -> "ACTIVE".equals(user.getAccountStatus()))
                .map(SystemUser::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private UserNotificationDtos.NotificationResponse toResponse(UserNotification notification) {
        return new UserNotificationDtos.NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getRelatedType(),
                notification.getRelatedId(),
                notification.getReadAt(),
                notification.getCreatedAt(),
                notification.getVersion()
        );
    }

    private SystemUser requireAccount(Long userId, Set<String> accountTypes) {
        SystemUser user = userRepository.findById(userId)
                .filter(item -> !Boolean.TRUE.equals(item.getDeleted()))
                .filter(item -> "ACTIVE".equals(item.getAccountStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_NOT_FOUND",
                        "Account is not available"));
        if (!accountTypes.contains(user.getAccountType())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_NOTIFICATION_ACCESS_DENIED",
                    "This account cannot access these notifications");
        }
        return user;
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAGE_INVALID",
                    "Page must be at least 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
