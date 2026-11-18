package com.helphub.media.media.dto;

import com.helphub.media.media.entity.*;

import java.util.UUID;

public record MediaMetadataResponse(
        UUID mediaId,
        MediaStatus status,
        MediaVisibility visibility,
        MediaResourceType resourceType,
        String mimeType,
        String format,
        Long sizeBytes,
        String secureUrl) {
    public static MediaMetadataResponse from(Media media) {
        return new MediaMetadataResponse(
                media.getId(),
                media.getStatus(),
                media.getVisibility(),
                media.getResourceType(),
                media.getMimeType(),
                media.getFormat(),
                media.getSizeBytes(),
                media.getVisibility() == MediaVisibility.PUBLIC
                        ? media.getSecureUrl()
                        : null);
    }
}
