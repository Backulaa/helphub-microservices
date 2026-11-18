package com.helphub.media.media.dto;

import com.helphub.media.media.entity.Media;
import com.helphub.media.media.entity.MediaStatus;
import com.helphub.media.media.entity.MediaVisibility;

import java.util.UUID;

public record CompleteUploadResponse(
        UUID mediaId,
        MediaStatus status,
        MediaVisibility visibility,
        String format,
        Long sizeBytes,
        String secureUrl) {

    public static CompleteUploadResponse from(Media media) {
        return new CompleteUploadResponse(
                media.getId(),
                media.getStatus(),
                media.getVisibility(),
                media.getFormat(),
                media.getSizeBytes(),
                media.getVisibility() == MediaVisibility.PUBLIC
                        ? media.getSecureUrl()
                        : null);
    }
}
