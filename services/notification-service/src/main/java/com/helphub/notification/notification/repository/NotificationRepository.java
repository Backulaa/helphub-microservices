package com.helphub.notification.notification.repository;

import com.helphub.notification.notification.entity.Notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository
                extends JpaRepository<Notification, UUID> {

        // Check whether a RabbitMQ event was processed
        boolean existsByEventId(UUID eventId);

        // List notifications belonging to one user
        Page<Notification> findByRecipientUserId(
                        UUID recipientUserId,
                        Pageable pageable);

        // Find a specific notification owned by a user
        Optional<Notification> findByIdAndRecipientUserId(
                        UUID id,
                        UUID recipientUserId);

        // Count unread notifications of a user
        long countByRecipientUserIdAndReadFalse(
                        UUID recipientUserId);

        // Mark all unread notifications as read.
        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("""
                        UPDATE Notification n
                        SET n.read = true
                        WHERE n.recipientUserId = :recipientUserId
                          AND n.read = false
                        """)
        int markAllAsRead(
                        @Param("recipientUserId") UUID recipientUserId);
}
