package com.helphub.media.media.service;

import com.helphub.media.common.exception.MediaApiException;
import com.helphub.media.media.cloudinary.CloudinarySigningService;
import com.helphub.media.media.dto.CreateUploadSessionRequest;
import com.helphub.media.media.dto.UploadSessionResponse;
import com.helphub.media.media.entity.*;
import com.helphub.media.media.repository.MediaRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class MediaUploadSessionService {

    private final MediaRepository mediaRepository;
    private final CloudinarySigningService signingService;

    private final long maxImageSize;
    private final long maxPdfSize;

    public MediaUploadSessionService(
            MediaRepository mediaRepository,
            CloudinarySigningService signingService,

            @Value("${app.media.upload.max-image-size}") long maxImageSize,

            @Value("${app.media.upload.max-pdf-size}") long maxPdfSize) {

        this.mediaRepository = mediaRepository;
        this.signingService = signingService;
        this.maxImageSize = maxImageSize;
        this.maxPdfSize = maxPdfSize;
    }

    @Transactional
    public UploadSessionResponse createSession(
            UUID ownerId,
            CreateUploadSessionRequest request) {

        // 1. Normalize and validate input.
        String filename = validateFilename(
                request.filename());

        String mimeType = request.mimeType()
                .trim()
                .toLowerCase(Locale.ROOT);

        MediaResourceType resourceType = validateFile(
                filename,
                mimeType,
                request.sizeBytes());

        // 2. Generate immutable Cloudinary public ID.
        String fileId = UUID.randomUUID()
                .toString()
                .replace("-", "");

        String publicId = "helphub/media/" + fileId;

        // Raw assets must include the file extension.
        if (resourceType == MediaResourceType.RAW) {
            publicId += ".pdf";
        }

        // 3. Create pending media.
        Media media = new Media(
                ownerId,
                publicId,
                filename,
                mimeType,
                resourceType,
                request.visibility());

        Media saved = mediaRepository.save(media);

        // 4. Private upload will use a backend endpoint.
        if (request.visibility() == MediaVisibility.PRIVATE) {
            return new UploadSessionResponse(
                    saved.getId(),
                    MediaStatus.PENDING,
                    "SERVER",
                    null,
                    null);
        }

        // 5. Generate signed direct-upload parameters.
        Map<String, String> parameters = signingService.signUpload(
                publicId,
                mimeType);

        String uploadUrl = signingService.uploadUrl(resourceType);

        return new UploadSessionResponse(
                saved.getId(),
                MediaStatus.PENDING,
                "DIRECT",
                uploadUrl,
                parameters);
    }

    private String validateFilename(String filename) {

        if (filename == null) {
            throw badRequest("Filename is required");
        }

        String normalized = filename.trim();

        if (normalized.isEmpty()
                || normalized.length() > 255
                || normalized.startsWith(".")
                || normalized.contains("..")
                || normalized.contains("/")
                || normalized.contains("\\")
                || normalized.chars().anyMatch(
                        Character::isISOControl)) {

            throw badRequest("Invalid filename");
        }

        return normalized;
    }

    private MediaResourceType validateFile(
            String filename,
            String mimeType,
            long sizeBytes) {

        String lowerName = filename.toLowerCase(Locale.ROOT);

        MediaResourceType type;

        boolean valid = switch (mimeType) {

            case "image/jpeg" ->
                lowerName.endsWith(".jpg")
                        || lowerName.endsWith(".jpeg");

            case "image/png" ->
                lowerName.endsWith(".png");

            case "image/webp" ->
                lowerName.endsWith(".webp");

            case "application/pdf" ->
                lowerName.endsWith(".pdf");

            default -> false;
        };

        if (!valid) {
            throw new MediaApiException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "UNSUPPORTED_FILE_TYPE",
                    "Only JPEG, PNG, WebP and PDF are supported");
        }

        type = mimeType.equals("application/pdf")
                ? MediaResourceType.RAW
                : MediaResourceType.IMAGE;

        long maxSize = type == MediaResourceType.RAW
                ? maxPdfSize
                : maxImageSize;

        if (sizeBytes <= 0 || sizeBytes > maxSize) {
            throw new MediaApiException(
                    HttpStatus.CONTENT_TOO_LARGE,
                    "FILE_TOO_LARGE",
                    "File exceeds the allowed size");
        }

        return type;
    }

    private MediaApiException badRequest(String message) {
        return new MediaApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_FILENAME",
                message);
    }
}
