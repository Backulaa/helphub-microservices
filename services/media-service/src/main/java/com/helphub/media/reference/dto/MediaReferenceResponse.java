package com.helphub.media.reference.dto;

import java.util.UUID;

public record MediaReferenceResponse(
        UUID mediaId,
        String serviceName,
        String resourceType,
        UUID resourceId,
        boolean created) {
}
