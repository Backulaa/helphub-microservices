package com.helphub.media.media.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.api.ApiResponse;
import com.cloudinary.utils.ObjectUtils;

import com.helphub.media.common.exception.MediaApiException;
import com.helphub.media.media.dto.CompleteUploadRequest;
import com.helphub.media.media.entity.Media;
import com.helphub.media.media.entity.MediaResourceType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

@Service
public class CloudinaryAssetVerifier {

    private static final Logger log = LoggerFactory.getLogger(
            CloudinaryAssetVerifier.class);

    private final Cloudinary cloudinary;
    private final long maxImageSize;
    private final long maxPdfSize;

    public CloudinaryAssetVerifier(
            Cloudinary cloudinary,

            @Value("${app.media.upload.max-image-size}") long maxImageSize,

            @Value("${app.media.upload.max-pdf-size}") long maxPdfSize) {

        this.cloudinary = cloudinary;
        this.maxImageSize = maxImageSize;
        this.maxPdfSize = maxPdfSize;
    }

    public CloudinaryVerifiedAsset verify(
            Media media,
            CompleteUploadRequest request) {

        // Always query the public ID stored by backend.
        // Never use an arbitrary client-provided ID
        // as the lookup target.

        if (!Objects.equals(
                media.getPublicId(),
                request.publicId())) {
            throw conflict("PUBLIC_ID_MISMATCH");
        }

        String expectedResourceType = media.getResourceType() == MediaResourceType.IMAGE
                ? "image"
                : "raw";

        ApiResponse response;

        try {
            response = cloudinary.api().resource(
                    media.getPublicId(),
                    ObjectUtils.asMap(
                            "resource_type", expectedResourceType,
                            "type", "upload"));
        } catch (Exception ex) {
            log.warn(
                    "Cloudinary verification failed for media {}",
                    media.getId(),
                    ex);

            throw new MediaApiException(
                    HttpStatus.BAD_GATEWAY,
                    "CLOUDINARY_VERIFICATION_FAILED",
                    "Could not verify the uploaded asset");
        }

        CloudinaryVerifiedAsset asset;

        try {
            asset = toAsset(response);
        } catch (RuntimeException ex) {
            log.warn(
                    "Invalid Cloudinary metadata for media {}",
                    media.getId(),
                    ex);

            throw new MediaApiException(
                    HttpStatus.BAD_GATEWAY,
                    "INVALID_CLOUDINARY_RESPONSE",
                    "Cloudinary returned incomplete metadata");
        }

        validateAsset(media, request, asset);

        return asset;
    }

    private CloudinaryVerifiedAsset toAsset(
            Map<?, ?> data) {

        return new CloudinaryVerifiedAsset(
                requiredString(data, "asset_id"),
                requiredString(data, "public_id"),
                requiredString(data, "resource_type"),
                requiredString(data, "type"),
                requiredString(data, "format"),
                requiredLong(data, "bytes"),
                requiredString(data, "secure_url"),
                requiredLong(data, "version"));
    }

    private String requiredString(
            Map<?, ?> data,
            String key) {

        Object value = data.get(key);

        if (!(value instanceof String text)
                || text.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing Cloudinary field: " + key);
        }

        return text;
    }

    private long requiredLong(
            Map<?, ?> data,
            String key) {

        Object value = data.get(key);

        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    "Invalid Cloudinary field: " + key);
        }

        return number.longValue();
    }

    private void validateAsset(
            Media media,
            CompleteUploadRequest request,
            CloudinaryVerifiedAsset asset) {

        if (!media.getPublicId().equals(
                asset.publicId())) {
            throw conflict("PUBLIC_ID_MISMATCH");
        }

        if (!request.assetId().equals(
                asset.assetId())) {
            throw conflict("ASSET_ID_MISMATCH");
        }

        if (request.version() != asset.version()) {
            throw conflict("ASSET_VERSION_MISMATCH");
        }

        String expectedType = media.getResourceType() == MediaResourceType.IMAGE
                ? "image"
                : "raw";

        if (!expectedType.equals(
                asset.resourceType())) {
            throw conflict("RESOURCE_TYPE_MISMATCH");
        }

        if (!"upload".equals(asset.deliveryType())) {
            throw conflict("INVALID_DELIVERY_TYPE");
        }

        String expectedFormat = switch (media.getMimeType()) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "application/pdf" -> "pdf";
            default -> throw conflict(
                    "UNSUPPORTED_FILE_TYPE");
        };

        if (!expectedFormat.equalsIgnoreCase(
                asset.format())) {
            throw conflict("FILE_FORMAT_MISMATCH");
        }

        long maxSize = media.getResourceType() == MediaResourceType.IMAGE
                ? maxImageSize
                : maxPdfSize;

        if (asset.sizeBytes() <= 0
                || asset.sizeBytes() > maxSize) {
            throw conflict("FILE_SIZE_INVALID");
        }

        if (!asset.secureUrl().startsWith("https://")) {
            throw conflict("INSECURE_ASSET_URL");
        }
    }

    private MediaApiException conflict(String code) {
        return new MediaApiException(
                HttpStatus.CONFLICT,
                code,
                "Uploaded asset does not match the session");
    }
}
