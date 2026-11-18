package com.helphub.media.media.cleanup.service;

import com.helphub.media.common.exception.MediaApiException;
import com.helphub.media.media.cleanup.dto.MediaDeleteResponse;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class MediaDeleteService {

    private final JdbcTemplate jdbc;

    public MediaDeleteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private record MediaRow(
            UUID id,
            UUID ownerId,
            String status,
            OffsetDateTime deletedAt) {
    }

    @Transactional
    public MediaDeleteResponse delete(
            UUID mediaId,
            UUID requesterId) {

        List<MediaRow> rows = jdbc.query(
                """
                        SELECT id, owner_id, status, deleted_at
                        FROM media
                        WHERE id = ?
                        FOR UPDATE
                        """,
                (rs, rowNum) -> new MediaRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("owner_id", UUID.class),
                        rs.getString("status"),
                        rs.getObject(
                                "deleted_at",
                                OffsetDateTime.class)),
                mediaId);

        if (rows.isEmpty()) {
            throw notFound();
        }

        MediaRow media = rows.get(0);

        if (!media.ownerId().equals(requesterId)) {
            throw notFound();
        }

        // Idempotent DELETE.
        if (media.deletedAt() != null) {
            return new MediaDeleteResponse(
                    media.id(),
                    "DELETION_SCHEDULED",
                    media.deletedAt(),
                    media.deletedAt().plusHours(24));
        }

        if (!"READY".equals(media.status())) {
            throw new MediaApiException(
                    HttpStatus.CONFLICT,
                    "MEDIA_NOT_READY",
                    "Only READY media can be deleted by this API");
        }

        Long referenceCount = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM media_references
                        WHERE media_id = ?
                        """,
                Long.class,
                mediaId);

        if (referenceCount != null
                && referenceCount > 0) {

            throw new MediaApiException(
                    HttpStatus.CONFLICT,
                    "MEDIA_STILL_REFERENCED",
                    "Remove media references before deletion");
        }

        OffsetDateTime deletedAt = OffsetDateTime.now(ZoneOffset.UTC);

        OffsetDateTime cleanupAfter = deletedAt.plusHours(24);

        jdbc.update(
                """
                        UPDATE media
                        SET deleted_at = ?,
                            updated_at = ?
                        WHERE id = ?
                        """,
                deletedAt,
                deletedAt,
                mediaId);

        // Requires the verified media_cleanup_jobs
        // schema and UNIQUE(media_id).
        jdbc.update(
                """
                        INSERT INTO media_cleanup_jobs (
                            id,
                            media_id,
                            status,
                            run_after,
                            attempts,
                            created_at,
                            updated_at
                        )
                        VALUES (?, ?, 'PENDING', ?, 0, ?, ?)
                        ON CONFLICT (media_id) DO NOTHING
                        """,
                UUID.randomUUID(),
                mediaId,
                cleanupAfter,
                deletedAt,
                deletedAt);

        return new MediaDeleteResponse(
                mediaId,
                "DELETION_SCHEDULED",
                deletedAt,
                cleanupAfter);
    }

    private MediaApiException notFound() {
        return new MediaApiException(
                HttpStatus.NOT_FOUND,
                "MEDIA_NOT_FOUND",
                "Media not found");
    }
}
