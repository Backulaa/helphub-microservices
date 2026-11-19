package com.helphub.media.media.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryAssetDeletionService {

    private final Cloudinary cloudinary;

    public CloudinaryAssetDeletionService(
            Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public void delete(
            String publicId,
            String resourceType,
            String visibility) throws IOException {

        String cloudinaryResourceType = switch (resourceType) {
            case "IMAGE" -> "image";
            case "RAW" -> "raw";
            default -> throw new IllegalArgumentException(
                    "Unsupported resource type");
        };

        String deliveryType = switch (visibility) {
            case "PRIVATE" -> "authenticated";
            case "PUBLIC" -> "upload";
            default -> throw new IllegalArgumentException(
                    "Unsupported visibility");
        };

        Map<?, ?> result = cloudinary.uploader().destroy(
                publicId,
                ObjectUtils.asMap(
                        "resource_type", cloudinaryResourceType,
                        "type", deliveryType,
                        "invalidate", true));

        Object deletionResult = result.get("result");

        if (!"ok".equals(deletionResult)
                && !"not found".equals(deletionResult)) {

            throw new IllegalStateException(
                    "Cloudinary deletion was not confirmed");
        }
    }
}
