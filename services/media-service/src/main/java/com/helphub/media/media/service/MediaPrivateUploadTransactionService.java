package com.helphub.media.media.service;

import com.helphub.media.common.exception.MediaApiException;
import com.helphub.media.media.cloudinary.CloudinaryVerifiedAsset;
import com.helphub.media.media.dto.CompleteUploadResponse;
import com.helphub.media.media.entity.*;
import com.helphub.media.media.repository.MediaRepository;
import com.helphub.media.outbox.entity.OutboxEvent;
import com.helphub.media.outbox.repository.OutboxEventRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class MediaPrivateUploadTransactionService {

    private final MediaRepository mediaRepository;
    private final OutboxEventRepository outboxRepository;

    public MediaPrivateUploadTransactionService(
            MediaRepository mediaRepository,
            OutboxEventRepository outboxRepository) {

        this.mediaRepository = mediaRepository;
        this.outboxRepository = outboxRepository;
    }

    // ==========================================
    // TRANSACTION 1 - CLAIM UPLOAD SESSION
    // ==========================================
    @Transactional
    public Media begin(
            UUID mediaId,
            UUID ownerId) {

        Media media = mediaRepository
                .findByIdForUpdate(mediaId)
                .orElseThrow(this::notFound);

        checkOwner(media, ownerId);

        if (media.getVisibility() != MediaVisibility.PRIVATE) {
            throw conflict("MEDIA_NOT_PRIVATE");
        }

        if (media.getStatus() != MediaStatus.PENDING) {
            throw conflict("INVALID_MEDIA_STATUS");
        }

        media.markUploading();

        return media;
    }

    // ==========================================
    // TRANSACTION 2 - READY + OUTBOX
    // ==========================================
    @Transactional
    public CompleteUploadResponse complete(
            UUID mediaId,
            UUID ownerId,
            CloudinaryVerifiedAsset asset) {

        Media media = mediaRepository
                .findByIdForUpdate(mediaId)
                .orElseThrow(this::notFound);

        checkOwner(media, ownerId);

        if (media.getVisibility() != MediaVisibility.PRIVATE) {
            throw conflict("MEDIA_NOT_PRIVATE");
        }

        // No duplicate event for an already completed asset.
        if (media.getStatus() == MediaStatus.READY) {

            if (!Objects.equals(
                    media.getAssetId(),
                    asset.assetId())) {
                throw conflict("ASSET_ID_MISMATCH");
            }

            return CompleteUploadResponse.from(media);
        }

        if (media.getStatus() != MediaStatus.UPLOADING) {
            throw conflict("INVALID_MEDIA_STATUS");
        }

        if (!media.getPublicId().equals(asset.publicId())
                || !"authenticated".equals(
                        asset.deliveryType())) {
            throw conflict("ASSET_MISMATCH");
        }

        String expectedResourceType = media.getResourceType() == MediaResourceType.IMAGE
                ? "image"
                : "raw";

        if (!expectedResourceType.equals(
                asset.resourceType())) {
            throw conflict("RESOURCE_TYPE_MISMATCH");
        }

        // Update Media.
        media.markPrivateReady(
                asset.assetId(),
                asset.format(),
                asset.sizeBytes());

        // Save OutboxEvent within the SAME transaction.
        OutboxEvent event = new OutboxEvent(
                media.getId(),
                "MEDIA",
                "media.ready.v1",
                Map.of(
                        "mediaId", media.getId().toString(),
                        "ownerId", media.getOwnerId().toString(),
                        "visibility", "PRIVATE",
                        "resourceType",
                        media.getResourceType().name(),
                        "mimeType", media.getMimeType(),
                        "sizeBytes", media.getSizeBytes()));

        outboxRepository.save(event);

        return CompleteUploadResponse.from(media);
    }

    private void checkOwner(
            Media media,
            UUID ownerId) {

        if (!media.getOwnerId().equals(ownerId)) {
            throw notFound();
        }
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
                "Media is not available for this operation");
    }
}
