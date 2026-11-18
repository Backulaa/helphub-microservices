package com.helphub.media.media.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.api.ApiResponse;
import com.cloudinary.api.exceptions.NotFound;
import com.cloudinary.utils.ObjectUtils;

import com.helphub.media.media.entity.Media;
import com.helphub.media.media.entity.MediaResourceType;
import com.helphub.media.media.entity.MediaVisibility;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class CloudinaryRecoveryLookup {

    private final Cloudinary cloudinary;
    private final long maxImageSize;
    private final long maxPdfSize;

    public CloudinaryRecoveryLookup(
            Cloudinary cloudinary,
            @Value("${app.media.upload.max-image-size}") long maxImageSize,
            @Value("${app.media.upload.max-pdf-size}") long maxPdfSize) {

        this.cloudinary = cloudinary;
        this.maxImageSize = maxImageSize;
        this.maxPdfSize = maxPdfSize;
    }

    public Optional<CloudinaryVerifiedAsset> find(
            Media media) {

        if (media.getVisibility() != MediaVisibility.PRIVATE) {
            throw new IllegalArgumentException(
                    "Recovery supports only PRIVATE media");
        }

        String resourceType = media.getResourceType() == MediaResourceType.IMAGE
                ? "image"
                : "raw";

        ApiResponse response;

        try {
            response = cloudinary.api().resource(
                    media.getPublicId(),
                    ObjectUtils.asMap(
                            "resource_type", resourceType,
                            "type", "authenticated"));

        } catch (NotFound ex) {
            // A confirmed 404 is not the same as
            // a network error or rate limiting.
            return Optional.empty();

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Cloudinary recovery lookup failed",
                    ex);
        }

        CloudinaryVerifiedAsset asset = toAsset(response);

        validate(media, asset, resourceType);

        return Optional.of(asset);
    }

    private CloudinaryVerifiedAsset toAsset(
            Map<?, ?> data) {

        return new CloudinaryVerifiedAsset(
                stringField(data, "asset_id"),
                stringField(data, "public_id"),
                stringField(data, "resource_type"),
                stringField(data, "type"),
                stringField(data, "format"),
                longField(data, "bytes"),
                stringField(data, "secure_url"),
                longField(data, "version"));
    }

    private void validate(
            Media media,
            CloudinaryVerifiedAsset asset,
            String expectedResourceType) {

        if (!Objects.equals(
                media.getPublicId(),
                asset.publicId())) {
            throw invalid("Public ID mismatch");
        }

        if (!expectedResourceType.equals(
                asset.resourceType())) {
            throw invalid("Resource type mismatch");
        }

        if (!"authenticated".equals(
                asset.deliveryType())) {
            throw invalid("Asset is not authenticated");
        }

        String expectedFormat = switch (media.getMimeType()) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "application/pdf" -> "pdf";
            default -> throw invalid("Unsupported MIME type");
        };

        if (!expectedFormat.equalsIgnoreCase(
                asset.format())) {
            throw invalid("Asset format mismatch");
        }

        long maxSize = media.getResourceType() == MediaResourceType.IMAGE
                ? maxImageSize
                : maxPdfSize;

        if (asset.sizeBytes() <= 0 ||
                asset.sizeBytes() > maxSize) {
            throw invalid("Invalid asset size");
        }

        if (!asset.secureUrl().startsWith("https://")) {
            throw invalid("Invalid asset URL");
        }

        // Never expose asset.secureUrl() to a client.
    }

    private String stringField(
            Map<?, ?> data,
            String name) {

        Object value = data.get(name);

        if (!(value instanceof String text)
                || text.isBlank()) {
            throw invalid(
                    "Missing Cloudinary metadata: " + name);
        }

        return text;
    }

    private long longField(
            Map<?, ?> data,
            String name) {

        Object value = data.get(name);

        if (!(value instanceof Number number)) {
            throw invalid(
                    "Invalid Cloudinary metadata: " + name);
        }

        return number.longValue();
    }

    private IllegalStateException invalid(String message) {
        return new IllegalStateException(
                "Private asset verification failed: " + message);
    }
}
