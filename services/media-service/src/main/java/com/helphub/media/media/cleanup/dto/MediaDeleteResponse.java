package com.helphub.media.media.cleanup.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MediaDeleteResponse(
        UUID mediaId,
        String status,
        OffsetDateTime deletedAt,
        OffsetDateTime cleanupAfter) {
}
