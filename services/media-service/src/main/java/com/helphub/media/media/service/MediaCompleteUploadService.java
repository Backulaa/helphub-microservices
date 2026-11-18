package com.helphub.media.media.service;

import com.helphub.media.common.exception.MediaApiException;
import com.helphub.media.media.cloudinary.CloudinaryAssetVerifier;
import com.helphub.media.media.cloudinary.CloudinaryVerifiedAsset;
import com.helphub.media.media.dto.CompleteUploadRequest;
import com.helphub.media.media.dto.CompleteUploadResponse;
import com.helphub.media.media.entity.*;
import com.helphub.media.media.repository.MediaRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MediaCompleteUploadService {

    private final MediaRepository mediaRepository;
    private final CloudinaryAssetVerifier verifier;
    private final MediaCompleteTransactionService transactionService;

    public MediaCompleteUploadService(
            MediaRepository mediaRepository,
            CloudinaryAssetVerifier verifier,
            MediaCompleteTransactionService transactionService) {

        this.mediaRepository = mediaRepository;
        this.verifier = verifier;
        this.transactionService = transactionService;
    }

    public CompleteUploadResponse complete(
            UUID mediaId,
            UUID ownerId,
            CompleteUploadRequest request) {

        // 1. Check media exists.
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(this::notFound);

        // 2. Ownership validation.
        if (!media.getOwnerId().equals(ownerId)) {
            throw notFound();
        }

        // 3. Only public direct-upload in this step.
        if (media.getVisibility() != MediaVisibility.PUBLIC) {
            throw conflict("PRIVATE_UPLOAD_REQUIRED");
        }

        if (!media.getPublicId().equals(request.publicId())) {
            throw conflict("PUBLIC_ID_MISMATCH");
        }

        // 4. Already READY: return same result.
        if (media.getStatus() == MediaStatus.READY) {
            return transactionService.alreadyCompleted(
                    mediaId,
                    ownerId,
                    request);
        }

        if (media.getStatus() != MediaStatus.PENDING) {
            throw conflict("INVALID_MEDIA_STATUS");
        }

        // 5. External Cloudinary call, outside transaction.
        CloudinaryVerifiedAsset verifiedAsset = verifier.verify(media, request);

        // 6. Atomic DB transaction.
        return transactionService.complete(
                mediaId,
                ownerId,
                request,
                verifiedAsset);
    }

    private MediaApiException notFound() {
        return new MediaApiException(
                HttpStatus.NOT_FOUND,
                "MEDIA_NOT_FOUND",
                "Media not found");
    }

    private MediaApiException conflict(String code) {
        return new MediaApiException(
                HttpStatus.CONFLICT,
                code,
                "Media cannot be completed");
    }
}
