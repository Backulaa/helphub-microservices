package com.helphub.media.media.service;

import com.helphub.media.common.exception.MediaApiException;
import com.helphub.media.media.cloudinary.CloudinaryPrivateStorage;
import com.helphub.media.media.cloudinary.CloudinaryVerifiedAsset;
import com.helphub.media.media.dto.CompleteUploadResponse;
import com.helphub.media.media.entity.*;
import com.helphub.media.media.repository.MediaRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
public class MediaPrivateUploadService {

    private static final Logger log = LoggerFactory.getLogger(
            MediaPrivateUploadService.class);

    private final MediaRepository mediaRepository;
    private final PrivateUploadFileValidator validator;
    private final CloudinaryPrivateStorage storage;
    private final MediaPrivateUploadTransactionService transactions;
    private final ObjectProvider<DevPrivateUploadFaultInjector> devFaultInjector;

    public MediaPrivateUploadService(
            MediaRepository mediaRepository,
            PrivateUploadFileValidator validator,
            CloudinaryPrivateStorage storage,
            MediaPrivateUploadTransactionService transactions,
            ObjectProvider<DevPrivateUploadFaultInjector> devFaultInjector) {

        this.mediaRepository = mediaRepository;
        this.validator = validator;
        this.storage = storage;
        this.transactions = transactions;
        this.devFaultInjector = devFaultInjector;
    }

    public CompleteUploadResponse upload(
            UUID mediaId,
            UUID ownerId,
            MultipartFile file) {

        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(this::notFound);

        if (!media.getOwnerId().equals(ownerId)) {
            throw notFound();
        }

        if (media.getVisibility() != MediaVisibility.PRIVATE) {
            throw conflict("PRIVATE_UPLOAD_REQUIRED");
        }

        if (media.getStatus() != MediaStatus.PENDING) {
            throw conflict("INVALID_MEDIA_STATUS");
        }

        byte[] bytes = validator.validate(media, file);

        // Transaction 1: PENDING -> UPLOADING
        Media claimed = transactions.begin(
                mediaId,
                ownerId);

        CloudinaryVerifiedAsset asset;

        try {
            // Cloudinary type = authenticated
            asset = storage.upload(claimed, bytes);

        } catch (IOException | RuntimeException ex) {

            log.error(
                    "Private upload failed, mediaId={}",
                    mediaId,
                    ex);

            throw new MediaApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PRIVATE_UPLOAD_FAILED",
                    "Private upload could not be completed");
        }

        // DEV ONLY:
        // Simulates a failure AFTER successful Cloudinary
        // upload and BEFORE the final DB transaction.
        devFaultInjector.ifAvailable(
                injector -> injector.afterCloudinaryUpload(
                        mediaId));

        // Transaction 2:
        // UPLOADING -> READY + OutboxEvent
        return transactions.complete(
                mediaId,
                ownerId,
                asset);
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
                "Media cannot be uploaded");
    }
}
