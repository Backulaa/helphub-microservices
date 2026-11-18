package com.helphub.media.media.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import com.helphub.media.common.exception.MediaApiException;
import com.helphub.media.media.entity.Media;
import com.helphub.media.media.entity.MediaResourceType;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

@Service
public class CloudinaryPrivateStorage {

    private final Cloudinary cloudinary;

    public CloudinaryPrivateStorage(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public CloudinaryVerifiedAsset upload(
            Media media,
            byte[] data) throws IOException {

        String resourceType = media.getResourceType() == MediaResourceType.IMAGE
                ? "image"
                : "raw";

        Map<?, ?> result = cloudinary.uploader().upload(
                data,
                ObjectUtils.asMap(
                        "public_id", media.getPublicId(),
                        "resource_type", resourceType,
                        "type", "authenticated",
                        "overwrite", false,
                        "unique_filename", false));

        CloudinaryVerifiedAsset asset = new CloudinaryVerifiedAsset(
                stringField(result, "asset_id"),
                stringField(result, "public_id"),
                stringField(result, "resource_type"),
                stringField(result, "type"),
                stringField(result, "format"),
                longField(result, "bytes"),
                stringField(result, "secure_url"),
                longField(result, "version"));

        validateCloudinaryResult(
                media, asset, data.length, resourceType);

        return asset;
    }

    private void validateCloudinaryResult(
            Media media,
            CloudinaryVerifiedAsset asset,
            long actualSize,
            String expectedResourceType) {

        if (!Objects.equals(
                media.getPublicId(),
                asset.publicId())) {
            throw invalidResponse();
        }

        if (!"authenticated".equals(
                asset.deliveryType())) {
            throw invalidResponse();
        }

        if (!expectedResourceType.equals(
                asset.resourceType())) {
            throw invalidResponse();
        }

        if (asset.sizeBytes() != actualSize) {
            throw invalidResponse();
        }

        String expectedFormat = switch (media.getMimeType()) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "application/pdf" -> "pdf";
            default -> throw invalidResponse();
        };

        if (!expectedFormat.equalsIgnoreCase(
                asset.format())) {
            throw invalidResponse();
        }

        if (!asset.secureUrl().startsWith("https://")) {
            throw invalidResponse();
        }
    }

    private String stringField(
            Map<?, ?> result,
            String key) {

        Object value = result.get(key);

        if (!(value instanceof String text)
                || text.isBlank()) {
            throw invalidResponse();
        }

        return text;
    }

    private long longField(
            Map<?, ?> result,
            String key) {

        Object value = result.get(key);

        if (!(value instanceof Number number)) {
            throw invalidResponse();
        }

        return number.longValue();
    }

    private MediaApiException invalidResponse() {
        return new MediaApiException(
                HttpStatus.BAD_GATEWAY,
                "INVALID_CLOUDINARY_RESPONSE",
                "Cloudinary returned invalid asset metadata");
    }
}
