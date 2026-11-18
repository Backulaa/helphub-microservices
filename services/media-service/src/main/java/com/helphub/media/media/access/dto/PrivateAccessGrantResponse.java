package com.helphub.media.media.access.dto;

import java.time.Instant;
import java.util.UUID;

public record PrivateAccessGrantResponse(
        UUID mediaId,
        String accessUrl,
        Instant expiresAt,
        long expiresInSeconds) {
}
