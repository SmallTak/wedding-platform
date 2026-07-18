package com.wedding.platform.operations.notification.persistence.repository;

import com.wedding.platform.operations.notification.persistence.entity.UserNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

    @Query("""
            SELECT notification
            FROM UserNotification notification
            WHERE notification.recipientUserId = :recipientUserId
              AND notification.deleted = false
              AND (:unreadOnly = false OR notification.readAt IS NULL)
            ORDER BY notification.createdAt DESC, notification.id DESC
            """)
    Page<UserNotification> findUserNotifications(
            @Param("recipientUserId") Long recipientUserId,
            @Param("unreadOnly") boolean unreadOnly,
            Pageable pageable
    );

    Optional<UserNotification> findByIdAndRecipientUserIdAndDeletedFalse(
            Long id,
            Long recipientUserId
    );

    long countByRecipientUserIdAndReadAtIsNullAndDeletedFalse(Long recipientUserId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE UserNotification notification
            SET notification.readAt = :readAt,
                notification.updatedAt = :readAt,
                notification.updatedBy = :recipientUserId,
                notification.version = notification.version + 1
            WHERE notification.recipientUserId = :recipientUserId
              AND notification.deleted = false
              AND notification.readAt IS NULL
            """)
    int markAllRead(
            @Param("recipientUserId") Long recipientUserId,
            @Param("readAt") Instant readAt
    );
}
