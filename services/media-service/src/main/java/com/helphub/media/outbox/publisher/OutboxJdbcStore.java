package com.helphub.media.outbox.publisher;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class OutboxJdbcStore {

    public record ClaimedEvent(
            UUID id,
            UUID aggregateId,
            String aggregateType,
            String eventType,
            String payload,
            int retryCount,
            long version) {
    }

    private final JdbcTemplate jdbc;

    public OutboxJdbcStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public List<ClaimedEvent> claimBatch(
            int batchSize,
            int leaseSeconds,
            int maxRetries) {

        String sql = """
                WITH picked AS (
                    SELECT id
                    FROM outbox_events
                    WHERE event_type = 'media.ready.v1'
                      AND status IN ('PENDING', 'FAILED')
                      AND retry_count < ?
                      AND (
                          next_retry_at IS NULL
                          OR next_retry_at <= CURRENT_TIMESTAMP
                      )
                    ORDER BY created_at ASC, id ASC
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE outbox_events AS o
                SET status = 'FAILED',
                    next_retry_at =
                        CURRENT_TIMESTAMP +
                        (? * INTERVAL '1 second'),
                    last_error = NULL,
                    version = o.version + 1
                FROM picked
                WHERE o.id = picked.id
                RETURNING
                    o.id,
                    o.aggregate_id,
                    o.aggregate_type,
                    o.event_type,
                    o.payload::text AS payload_json,
                    o.retry_count,
                    o.version
                """;

        return jdbc.query(
                sql,
                (rs, rowNum) -> new ClaimedEvent(
                        rs.getObject("id", UUID.class),
                        rs.getObject("aggregate_id", UUID.class),
                        rs.getString("aggregate_type"),
                        rs.getString("event_type"),
                        rs.getString("payload_json"),
                        rs.getInt("retry_count"),
                        rs.getLong("version")),
                maxRetries,
                batchSize,
                leaseSeconds);
    }

    @Transactional
    public boolean markPublished(ClaimedEvent event) {

        String sql = """
                UPDATE outbox_events
                SET status = 'PUBLISHED',
                    published_at = CURRENT_TIMESTAMP,
                    next_retry_at = NULL,
                    last_error = NULL,
                    version = version + 1
                WHERE id = ?
                  AND version = ?
                  AND status = 'FAILED'
                """;

        int updated = jdbc.update(
                sql,
                event.id(),
                event.version());

        return updated == 1;
    }

    @Transactional
    public boolean markFailed(
            ClaimedEvent event,
            String errorCode,
            long retryDelaySeconds,
            int maxRetries) {

        int nextAttempt = event.retryCount() + 1;

        OffsetDateTime nextRetryAt = nextAttempt >= maxRetries
                ? null
                : OffsetDateTime.now()
                        .plusSeconds(retryDelaySeconds);

        String sql = """
                UPDATE outbox_events
                SET status = 'FAILED',
                    retry_count = ?,
                    next_retry_at = ?,
                    last_error = ?,
                    version = version + 1
                WHERE id = ?
                  AND version = ?
                  AND status = 'FAILED'
                """;

        int updated = jdbc.update(
                sql,
                nextAttempt,
                nextRetryAt,
                errorCode,
                event.id(),
                event.version());

        return updated == 1;
    }
}
