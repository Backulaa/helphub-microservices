
package com.helphub.media.media.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import com.helphub.media.media.entity.MediaResourceType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CloudinarySigningService {

    private final Cloudinary cloudinary;
    private final String cloudName;

    public CloudinarySigningService(
            Cloudinary cloudinary,
            @Value("${app.cloudinary.cloud-name}") String cloudName) {

        this.cloudinary = cloudinary;
        this.cloudName = cloudName;
    }

    public String uploadUrl(
            MediaResourceType resourceType) {

        String type = switch (resourceType) {
            case IMAGE -> "image";
            case RAW -> "raw";
        };

        return "https://api.cloudinary.com/v1_1/"
                + cloudName + "/" + type + "/upload";
    }

    public Map<String, String> signUpload(
            String publicId,
            String mimeType) {

        Map<String, Object> params = new LinkedHashMap<>();

        params.put(
                "timestamp",
                Instant.now().getEpochSecond());

        params.put("public_id", publicId);
        params.put("overwrite", "false");
        params.put("unique_filename", "false");

        params.put(
                "allowed_formats",
                allowedFormats(mimeType));

        // Adds signature and api_key to params.
        // API secret is never returned.
        cloudinary.uploader().signRequestParams(
                params,
                ObjectUtils.emptyMap());

        Map<String, String> signed = new LinkedHashMap<>();

        params.forEach((key, value) -> signed.put(key, String.valueOf(value)));

        return Map.copyOf(signed);
    }

    private String allowedFormats(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> "jpg,jpeg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "application/pdf" -> "pdf";
            default -> throw new IllegalArgumentException(
                    "Unsupported MIME type");
        };
    }
}
