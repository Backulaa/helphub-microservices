package com.helphub.media.media.service;

import com.helphub.media.media.cloudinary.CloudinaryRecoveryLookup;
import com.helphub.media.media.cloudinary.CloudinaryVerifiedAsset;
import com.helphub.media.media.dto.CompleteUploadResponse;
import com.helphub.media.media.entity.Media;
import com.helphub.media.media.entity.MediaStatus;
import com.helphub.media.media.entity.MediaVisibility;
import com.helphub.media.media.repository.MediaRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "app.media.recovery", name = "enabled", havingValue = "true")
public class MediaPrivateRecoveryWorker {

    private static final Logger log = LoggerFactory.getLogger(
            MediaPrivateRecoveryWorker.class);

    private final MediaRepository mediaRepository;
    private final CloudinaryRecoveryLookup lookup;
    private final MediaPrivateUploadTransactionService transactions;

    private final long staleAfterSeconds;
    private final int batchSize;

    public MediaPrivateRecoveryWorker(
            MediaRepository mediaRepository,
            CloudinaryRecoveryLookup lookup,
            MediaPrivateUploadTransactionService transactions,
            @Value("${app.media.recovery.stale-after-seconds:900}") long staleAfterSeconds,
            @Value("${app.media.recovery.batch-size:10}") int batchSize) {

        this.mediaRepository = mediaRepository;
        this.lookup = lookup;
        this.transactions = transactions;
        this.staleAfterSeconds = staleAfterSeconds;
        this.batchSize = batchSize;
    }

    @Scheduled(initialDelayString = "${app.media.recovery.initial-delay-ms:30000}", fixedDelayString = "${app.media.recovery.fixed-delay-ms:300000}")
    public void reconcile() {

        if (staleAfterSeconds <= 0 || batchSize <= 0) {
            log.error(
                    "Recovery configuration is invalid");
            return;
        }

        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC)
                .minusSeconds(staleAfterSeconds);

        List<UUID> mediaIds;

        try {
            mediaIds = mediaRepository.findStalePrivateUploadingIds(
                    cutoff,
                    batchSize);

        } catch (Exception ex) {
            log.error(
                    "Private recovery scan failed",
                    ex);
            return;
        }

        if (mediaIds.isEmpty()) {
            log.debug(
                    "Private recovery: no stale uploads");
            return;
        }

        log.info(
                "Private recovery found {} stale upload(s)",
                mediaIds.size());

        for (UUID mediaId : mediaIds) {
            try {
                reconcileOne(mediaId);

            } catch (Exception ex) {
                // One problematic media must not block
                // the rest of the batch.
                log.error(
                        "Private recovery failed for mediaId={}",
                        mediaId,
                        ex);
            }
        }
    }

    private void reconcileOne(UUID mediaId) {

        Optional<Media> current = mediaRepository.findById(mediaId);

        if (current.isEmpty()) {
            return;
        }

        Media media = current.get();

        if (media.getStatus() != MediaStatus.UPLOADING
                || media.getVisibility() != MediaVisibility.PRIVATE) {
            return;
        }

        // No database lock while calling Cloudinary.
        Optional<CloudinaryVerifiedAsset> asset = lookup.find(media);

        if (asset.isEmpty()) {
            log.warn(
                    "Private recovery: asset not found yet, "
                            + "leaving media UPLOADING. mediaId={}",
                    mediaId);
            return;
        }

        // Reuses the existing @Transactional method,
        // which locks and rechecks the Media row.
        CompleteUploadResponse result = transactions.complete(
                mediaId,
                media.getOwnerId(),
                asset.get());

        if (result.status() == MediaStatus.READY) {
            log.info(
                    "Private recovery reconciled mediaId={} "
                            + "to READY",
                    mediaId);
        }
    }
}
