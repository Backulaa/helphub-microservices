package com.helphub.notification.notification.controller;

import com.helphub.notification.notification.dto.MarkAllReadResponse;
import com.helphub.notification.notification.dto.NotificationPageResponse;
import com.helphub.notification.notification.dto.NotificationResponse;
import com.helphub.notification.notification.dto.UnreadCountResponse;
import com.helphub.notification.notification.service.NotificationService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

        private final NotificationService notificationService;

        public NotificationController(
                        NotificationService notificationService) {
                this.notificationService = notificationService;
        }

        // ==========================================
        // GET /api/v1/notifications
        // ==========================================
        @GetMapping
        public ResponseEntity<NotificationPageResponse> getNotifications(
                        @AuthenticationPrincipal Jwt jwt,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size) {

                UUID userId = currentUserId(jwt);

                return ResponseEntity.ok(
                                notificationService.getNotifications(
                                                userId,
                                                page,
                                                size));
        }

        // ==========================================
        // GET /api/v1/notifications/unread-count
        // ==========================================
        @GetMapping("/unread-count")
        public ResponseEntity<UnreadCountResponse> getUnreadCount(
                        @AuthenticationPrincipal Jwt jwt) {

                UUID userId = currentUserId(jwt);

                return ResponseEntity.ok(
                                notificationService.getUnreadCount(
                                                userId));
        }

        // ==========================================
        // GET /api/v1/notifications/{id}
        // ==========================================
        @GetMapping("/{id}")
        public ResponseEntity<NotificationResponse> getNotification(
                        @PathVariable UUID id,
                        @AuthenticationPrincipal Jwt jwt) {

                UUID userId = currentUserId(jwt);

                return ResponseEntity.ok(
                                notificationService.getNotification(
                                                userId,
                                                id));
        }

        // ==========================================
        // PATCH /api/v1/notifications/{id}/read
        // ==========================================
        @PatchMapping("/{id}/read")
        public ResponseEntity<NotificationResponse> markAsRead(
                        @PathVariable UUID id,
                        @AuthenticationPrincipal Jwt jwt) {

                UUID userId = currentUserId(jwt);

                return ResponseEntity.ok(
                                notificationService.markAsRead(
                                                userId,
                                                id));
        }

        // ==========================================
        // PATCH /api/v1/notifications/read-all
        // ==========================================
        @PatchMapping("/read-all")
        public ResponseEntity<MarkAllReadResponse> markAllAsRead(
                        @AuthenticationPrincipal Jwt jwt) {

                UUID userId = currentUserId(jwt);

                return ResponseEntity.ok(
                                notificationService.markAllAsRead(
                                                userId));
        }

        // ==========================================
        // Extract authenticated user UUID
        // ==========================================
        private UUID currentUserId(Jwt jwt) {
                return UUID.fromString(jwt.getSubject());
        }
}
