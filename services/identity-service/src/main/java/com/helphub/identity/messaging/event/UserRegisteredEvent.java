package com.helphub.identity.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record UserRegisteredEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        String occurredAt,
        Data data) {

    public record Data(UUID userId) {
    }

    public static UserRegisteredEvent create(UUID userId) {
        return new UserRegisteredEvent(
                UUID.randomUUID(),
                "identity.user.registered",
                1,
                Instant.now().toString(),
                new Data(userId));
    }
}
