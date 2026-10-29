package com.helphub.notification.notification.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.helphub.notification.notification.dto.NotificationPageResponse;
import com.helphub.notification.notification.dto.NotificationResponse;
import com.helphub.notification.notification.dto.UnreadCountResponse;
import com.helphub.notification.notification.dto.MarkAllReadResponse;

import com.helphub.notification.messaging.dto.UserRegisteredEvent;
import com.helphub.notification.notification.entity.Notification;
import com.helphub.notification.notification.repository.NotificationRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.time.Instant;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    public NotificationService(
            NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void handleUserRegistered(UserRegisteredEvent event) {

        // 1. Validate event
        validateUserRegisteredEvent(event);

        // 2. Idempotency check
        if (notificationRepository.existsByEventId(event.eventId())) {
            log.info(
                    "Duplicate user registered event ignored. eventId={}",
                    event.eventId());
            return;
        }

        // 3. Create notification
        Notification notification = new Notification(
                event.eventId(),
                event.data().userId(),
                "WELCOME",
                "Chào mừng đến với HELPhub",
                "Tài khoản của bạn đã được tạo thành công.");

        // 4. Persist notification
        notificationRepository.saveAndFlush(notification);

        log.info(
                "Welcome notification saved. eventId={}, recipientUserId={}",
                event.eventId(),
                event.data().userId());
    }

    // ==========================================
    // 1. List notifications
    // ==========================================
    @Transactional(readOnly = true)
    public NotificationPageResponse getNotifications(
            UUID recipientUserId,
            int page,
            int size) {

        validatePagination(page, size);

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")));

        Page<Notification> result = notificationRepository.findByRecipientUserId(
                recipientUserId,
                pageable);

        return NotificationPageResponse.from(result);
    }

    // ==========================================
    // 2. Get notification detail
    // ==========================================
    @Transactional(readOnly = true)
    public NotificationResponse getNotification(
            UUID recipientUserId,
            UUID notificationId) {

        Notification notification = findOwnedNotification(
                recipientUserId,
                notificationId);

        return NotificationResponse.from(notification);
    }

    // ==========================================
    // 3. Count unread notifications
    // ==========================================
    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(
            UUID recipientUserId) {

        long count = notificationRepository
                .countByRecipientUserIdAndReadFalse(
                        recipientUserId);

        return new UnreadCountResponse(count);
    }

    // ==========================================
    // 4. Mark one notification as read
    // ==========================================
    @Transactional
    public NotificationResponse markAsRead(
            UUID recipientUserId,
            UUID notificationId) {

        Notification notification = findOwnedNotification(
                recipientUserId,
                notificationId);

        if (!notification.isRead()) {
            notification.markAsRead();
        }

        // JPA dirty checking persists the change
        // when the transaction is committed.
        return NotificationResponse.from(notification);
    }

    // ==========================================
    // 5. Mark all notifications as read
    // ==========================================
    @Transactional
    public MarkAllReadResponse markAllAsRead(
            UUID recipientUserId) {

        int updatedCount = notificationRepository.markAllAsRead(
                recipientUserId);

        return new MarkAllReadResponse(updatedCount);
    }

    // ==========================================
    // Helpers
    // ==========================================
    private Notification findOwnedNotification(
            UUID recipientUserId,
            UUID notificationId) {

        return notificationRepository
                .findByIdAndRecipientUserId(
                        notificationId,
                        recipientUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Notification not found"));
    }

    private void validatePagination(int page, int size) {

        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be greater than or equal to 0");
        }

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Size must be between 1 and 100");
        }
    }

    private void validateUserRegisteredEvent(
            UserRegisteredEvent event) {

        if (event == null) {
            throw new IllegalArgumentException(
                    "Event must not be null");
        }

        if (event.eventId() == null) {
            throw new IllegalArgumentException(
                    "Event ID is required");
        }

        if (!"identity.user.registered".equals(event.eventType())) {
            throw new IllegalArgumentException(
                    "Unsupported event type: " + event.eventType());
        }

        if (event.eventVersion() != 1) {
            throw new IllegalArgumentException(
                    "Unsupported event version: " + event.eventVersion());
        }

        if (event.data() == null
                || event.data().userId() == null) {
            throw new IllegalArgumentException(
                    "Recipient user ID is required");
        }

        if (event.occurredAt() == null
                || event.occurredAt().isBlank()) {
            throw new IllegalArgumentException(
                    "Event occurredAt is required");
        }

        // Validate ISO-8601 timestamp
        Instant.parse(event.occurredAt());
    }
}
