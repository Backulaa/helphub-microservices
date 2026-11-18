package com.helphub.media.media.access.service;

import com.cloudinary.Cloudinary;

import java.util.Map;

import com.helphub.media.common.exception.MediaApiException;
import com.helphub.media.media.entity.Media;
import com.helphub.media.media.entity.MediaResourceType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class CloudinaryTimedDownloadService {

    public record SignedLink(
            String url,
            Instant expiresAt,
            long expiresInSeconds) {
    }

    private final Cloudinary cloudinary;
    private final int ttlSeconds;

    public CloudinaryTimedDownloadService(
            Cloudinary cloudinary,
            @Value("${app.media.access-grant.ttl-seconds:120}") int ttlSeconds) {

        if (ttlSeconds < 15 || ttlSeconds > 300) {
            throw new IllegalArgumentException(
                    "Access Grant TTL must be 15-300 seconds");
        }

        this.cloudinary = cloudinary;
        this.ttlSeconds = ttlSeconds;
    }

    public SignedLink issue(Media media) {

        if (media.getAssetId() == null
                || media.getAssetId().isBlank()
                || media.getPublicId() == null
                || media.getPublicId().isBlank()
                || media.getFormat() == null
                || media.getFormat().isBlank()) {

            throw new MediaApiException(
                    HttpStatus.CONFLICT,
                    "MEDIA_ASSET_INCOMPLETE",
                    "Media asset metadata is incomplete");
        }

        String resourceType = switch (media.getResourceType()) {

            case MediaResourceType.IMAGE -> "image";
            case MediaResourceType.RAW -> "raw";
        };

        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);

        try {
            Map<String, Object> options = Map.of(
                    "resource_type", resourceType,
                    "type", "authenticated",
                    "expires_at", expiresAt.getEpochSecond(),
                    "attachment", false);

            String signedUrl = cloudinary.privateDownload(
                    media.getPublicId(),
                    media.getFormat(),
                    options);

            if (signedUrl == null
                    || !signedUrl.startsWith("https://")) {
                throw new IllegalStateException(
                        "Cloudinary returned an invalid URL");
            }

            return new SignedLink(
                    signedUrl,
                    expiresAt,
                    ttlSeconds);

        } catch (Exception ex) {
            // Do not log the signed URL or API secret.
            throw new MediaApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PRIVATE_ACCESS_GRANT_FAILED",
                    "Unable to generate temporary media URL");
        }
    }
}
