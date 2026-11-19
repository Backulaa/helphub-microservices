package com.helphub.media.cleanup.worker;

import com.helphub.media.media.cloudinary.CloudinaryAssetDeletionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "app.media.cleanup", name = "enabled", havingValue = "true")
public class MediaCleanupWorker {

    private static final Logger log = LoggerFactory.getLogger(MediaCleanupWorker.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CloudinaryAssetDeletionService storage;

    private final int batchSize;
    private final int leaseSeconds;
    private final int maxAttempts;

    private record Job(
            UUID id,
            UUID mediaId,
            int attempts) {
    }

    private record Asset(
            String publicId,
            String resourceType,
            String visibility,
            String status,
            OffsetDateTime deletedAt,
            boolean hasReference) {
    }

    public MediaCleanupWorker(
            JdbcTemplate jdbc,
            PlatformTransactionManager manager,
            CloudinaryAssetDeletionService storage,
            @Value("${app.media.cleanup.batch-size:10}") int batchSize,
            @Value("${app.media.cleanup.lease-seconds:180}") int leaseSeconds,
            @Value("${app.media.cleanup.max-attempts:8}") int maxAttempts) {

        if (batchSize <= 0
                || leaseSeconds <= 0
                || maxAttempts <= 0) {
            throw new IllegalArgumentException(
                    "Invalid cleanup configuration");
        }

        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(manager);
        this.storage = storage;
        this.batchSize = batchSize;
        this.leaseSeconds = leaseSeconds;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(initialDelayString = "${app.media.cleanup.initial-delay-ms:30000}", fixedDelayString = "${app.media.cleanup.fixed-delay-ms:60000}")
    public void run() {

        List<Job> jobs;

        try {
            jobs = tx.execute(status -> jdbc.query(
                    """
                            WITH picked AS (
                                SELECT id
                                FROM media_cleanup_jobs
                                WHERE
                                    (
                                        status = 'PENDING'
                                        AND next_retry_at <= CURRENT_TIMESTAMP
                                    )
                                    OR (
                                        status = 'RUNNING'
                                        AND lease_until <= CURRENT_TIMESTAMP
                                    )
                                ORDER BY next_retry_at ASC NULLS FIRST, id ASC
                                LIMIT ?
                                FOR UPDATE SKIP LOCKED
                            )
                            UPDATE media_cleanup_jobs AS j
                            SET status = 'RUNNING',
                                attempts = j.attempts + 1,
                                lease_until =
                                    CURRENT_TIMESTAMP +
                                    (? * INTERVAL '1 second'),
                                updated_at = CURRENT_TIMESTAMP,
                                version = COALESCE(j.version, 0) + 1
                            FROM picked
                            WHERE j.id = picked.id
                            RETURNING j.id, j.media_id, j.attempts
                            """,
                    (rs, rowNum) -> new Job(
                            rs.getObject("id", UUID.class),
                            rs.getObject("media_id", UUID.class),
                            rs.getInt("attempts")),
                    batchSize,
                    leaseSeconds));

        } catch (Exception ex) {
            log.error("MEDIA_CLEANUP_CLAIM_FAILED", ex);
            return;
        }

        if (jobs == null || jobs.isEmpty()) {
            return;
        }

        for (Job job : jobs) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            process(job);
        }
    }

    private void process(Job job) {

        if (job.attempts() > maxAttempts) {
            markFailed(job, "MAX_ATTEMPTS_RECOVERED");
            return;
        }

        try {
            Asset asset = loadAsset(job.mediaId());

            if (!"DELETING".equals(asset.status())
                    || asset.deletedAt() == null) {
                throw new IllegalStateException(
                        "MEDIA_NOT_DELETING");
            }

            if (asset.hasReference()) {
                throw new IllegalStateException(
                        "MEDIA_STILL_REFERENCED");
            }

            // External call: NOT inside a DB transaction.
            storage.delete(
                    asset.publicId(),
                    asset.resourceType(),
                    asset.visibility());

            // Finalize job and media atomically.
            Boolean finalized = tx.execute(status -> {

                int updated = jdbc.update(
                        """
                                UPDATE media_cleanup_jobs
                                SET status = 'COMPLETED',
                                    completed_at = CURRENT_TIMESTAMP,
                                    next_retry_at = NULL,
                                    lease_until = NULL,
                                    last_error = NULL,
                                    updated_at = CURRENT_TIMESTAMP,
                                    version = COALESCE(version, 0) + 1
                                WHERE id = ?
                                  AND status = 'RUNNING'
                                  AND attempts = ?
                                """,
                        job.id(),
                        job.attempts());

                // The lease may have been reclaimed.
                if (updated == 0) {
                    return false;
                }

                int mediaUpdated = jdbc.update(
                        """
                                UPDATE media
                                SET status = 'DELETED',
                                    updated_at = CURRENT_TIMESTAMP
                                WHERE id = ?
                                  AND status = 'DELETING'
                                """,
                        job.mediaId());

                if (mediaUpdated != 1) {
                    throw new IllegalStateException(
                            "MEDIA_FINALIZE_STATE_MISMATCH");
                }

                return true;
            });

            if (Boolean.TRUE.equals(finalized)) {
                log.info(
                        "MEDIA_CLEANUP_PROCESSED jobId={} mediaId={}",
                        job.id(),
                        job.mediaId());
            } else {
                log.warn(
                        "MEDIA_CLEANUP_STALE_CLAIM jobId={} mediaId={}",
                        job.id(),
                        job.mediaId());
            }

        } catch (Exception ex) {

            log.warn(
                    "MEDIA_CLEANUP_ERROR jobId={} errorType={}",
                    job.id(),
                    ex.getClass().getSimpleName());

            try {
                markFailed(
                        job,
                        ex.getClass().getSimpleName());
            } catch (Exception dbEx) {
                log.error(
                        "MEDIA_CLEANUP_FAILURE_RECORD_FAILED jobId={}",
                        job.id(),
                        dbEx);
            }
        }
    }

    private Asset loadAsset(UUID mediaId) {

        return jdbc.query(
                """
                        SELECT
                            m.public_id,
                            m.resource_type,
                            m.visibility,
                            m.status,
                            m.deleted_at,
                            EXISTS (
                                SELECT 1
                                FROM media_references r
                                WHERE r.media_id = m.id
                            ) AS has_reference
                        FROM media m
                        WHERE m.id = ?
                        """,
                (rs, rowNum) -> new Asset(
                        rs.getString("public_id"),
                        rs.getString("resource_type"),
                        rs.getString("visibility"),
                        rs.getString("status"),
                        rs.getObject(
                                "deleted_at",
                                OffsetDateTime.class),
                        rs.getBoolean("has_reference")),
                mediaId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "MEDIA_ROW_MISSING"));
    }

    private void markFailed(
            Job job,
            String errorCode) {

        boolean terminal = job.attempts() >= maxAttempts;

        long delaySeconds = Math.min(
                3600L,
                30L * (1L << Math.min(job.attempts() - 1, 10)));

        OffsetDateTime retryAt = terminal
                ? null
                : OffsetDateTime.now(ZoneOffset.UTC)
                        .plusSeconds(delaySeconds);

        String nextStatus = terminal
                ? "FAILED"
                : "PENDING";

        tx.executeWithoutResult(status -> jdbc.update(
                """
                        UPDATE media_cleanup_jobs
                        SET status = ?,
                            next_retry_at = ?,
                            lease_until = NULL,
                            last_error = ?,
                            updated_at = CURRENT_TIMESTAMP,
                            version = COALESCE(version, 0) + 1
                        WHERE id = ?
                          AND status = 'RUNNING'
                          AND attempts = ?
                        """,
                nextStatus,
                retryAt,
                errorCode,
                job.id(),
                job.attempts()));

        log.warn(
                "MEDIA_CLEANUP_RETRY jobId={} status={} attempt={}",
                job.id(),
                nextStatus,
                job.attempts());
    }
}
