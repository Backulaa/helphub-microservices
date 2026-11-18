package com.helphub.media.media.service;

import com.helphub.media.common.exception.MediaApiException;
import com.helphub.media.media.cloudinary.CloudinaryVerifiedAsset;
import com.helphub.media.media.dto.CompleteUploadRequest;
import com.helphub.media.media.dto.CompleteUploadResponse;
import com.helphub.media.media.entity.*;
import com.helphub.media.media.repository.MediaRepository;
import com.helphub.media.outbox.entity.OutboxEvent;
import com.helphub.media.outbox.repository.OutboxEventRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class MediaCompleteTransactionService {

    private final MediaRepository mediaRepository;
    private final OutboxEventRepository outboxRepository;

    public MediaCompleteTransactionService(
            MediaRepository mediaRepository,
            OutboxEventRepository outboxRepository) {

        this.mediaRepository = mediaRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public CompleteUploadResponse complete(
            UUID mediaId,
            UUID ownerId,
            CompleteUploadRequest request,
            CloudinaryVerifiedAsset verifiedAsset) {

        // Lock Media until transaction commits.
        Media media = mediaRepository
                .findByIdForUpdate(mediaId)
                .orElseThrow(this::notFound);

        // Only the original owner may complete.
        if (!media.getOwnerId().equals(ownerId)) {
            throw notFound();
        }

        if (media.getVisibility() != MediaVisibility.PUBLIC) {
            throw conflict("PRIVATE_UPLOAD_REQUIRED");
        }

        if (!media.getPublicId().equals(request.publicId())) {
            throw conflict("PUBLIC_ID_MISMATCH");
        }

        // Idempotency: do not create another event.
        if (media.getStatus() == MediaStatus.READY) {

            if (!request.assetId().equals(media.getAssetId())) {
                throw conflict("ASSET_ID_MISMATCH");
            }

            return CompleteUploadResponse.from(media);
        }

        if (media.getStatus() != MediaStatus.PENDING) {
            throw conflict("INVALID_MEDIA_STATUS");
        }

        // Ensure the validated asset is still the same.
        if (!media.getPublicId().equals(
                verifiedAsset.publicId())
                || !request.assetId().equals(
                        verifiedAsset.assetId())) {
            throw conflict("ASSET_MISMATCH");
        }

        // Update managed JPA entity.
        media.markReady(
                verifiedAsset.assetId(),
                verifiedAsset.format(),
                verifiedAsset.sizeBytes(),
                verifiedAsset.secureUrl());

        // Create event in the SAME database transaction.
        OutboxEvent event = new OutboxEvent(
                media.getId(),
                "MEDIA",
                "media.ready.v1",
                Map.of(
                        "mediaId", media.getId().toString(),
                        "ownerId", media.getOwnerId().toString(),
                        "visibility", media.getVisibility().name(),
                        "resourceType", media.getResourceType().name(),
                        "mimeType", media.getMimeType(),
                        "sizeBytes", media.getSizeBytes()));

        outboxRepository.save(event);

        // Hibernate flushes entity changes on commit.
        return CompleteUploadResponse.from(media);
    }

    public CompleteUploadResponse alreadyCompleted(
            UUID mediaId,
            UUID ownerId,
            CompleteUploadRequest request) {

        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(this::notFound);

        if (!media.getOwnerId().equals(ownerId)) {
            throw notFound();
        }

        if (media.getStatus() != MediaStatus.READY
                || !media.getPublicId().equals(request.publicId())
                || !request.assetId().equals(media.getAssetId())) {

            throw conflict("INVALID_MEDIA_STATE");
        }

        return CompleteUploadResponse.from(media);
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
