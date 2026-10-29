package com.helphub.notification.notification.dto;

import com.helphub.notification.notification.entity.Notification;

import org.springframework.data.domain.Page;

import java.util.List;

public record NotificationPageResponse(
        List<NotificationResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {

    public static NotificationPageResponse from(
            Page<Notification> result) {

        return new NotificationPageResponse(
                result.getContent()
                        .stream()
                        .map(NotificationResponse::from)
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext());
    }
}
