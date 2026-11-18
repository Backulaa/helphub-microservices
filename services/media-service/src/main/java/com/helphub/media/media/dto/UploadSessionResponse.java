
package com.helphub.media.media.dto;

import com.helphub.media.media.entity.MediaStatus;

import java.util.Map;
import java.util.UUID;

public record UploadSessionResponse(
        UUID mediaId,
        MediaStatus status,
        String uploadMethod,
        String uploadUrl,
        Map<String, String> parameters) {
}
