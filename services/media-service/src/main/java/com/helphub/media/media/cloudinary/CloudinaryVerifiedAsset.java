
package com.helphub.media.media.cloudinary;

public record CloudinaryVerifiedAsset(
        String assetId,
        String publicId,
        String resourceType,
        String deliveryType,
        String format,
        long sizeBytes,
        String secureUrl,
        long version) {
}
