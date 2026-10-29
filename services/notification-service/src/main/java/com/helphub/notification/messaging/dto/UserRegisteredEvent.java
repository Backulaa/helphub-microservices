package com.helphub.notification.messaging.dto;

import java.util.UUID;

public record UserRegisteredEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        String occurredAt,
        Data data) {

    public record Data(
            UUID userId) {
    }
}
