package com.helphub.media.outbox.entity;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_status_retry", columnList = "status, next_retry_at"),
        @Index(name = "idx_outbox_aggregate", columnList = "aggregate_id")
})
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    // PostgreSQL JSONB
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Version
    @Column(name = "version")
    private Long version;

    protected OutboxEvent() {
    }

    public OutboxEvent(
            UUID aggregateId,
            String aggregateType,
            String eventType,
            Map<String, Object> payload) {
        this.aggregateId = Objects.requireNonNull(aggregateId);
        this.aggregateType = Objects.requireNonNull(aggregateType);
        this.eventType = Objects.requireNonNull(eventType);
        this.payload = new LinkedHashMap<>(
                Objects.requireNonNull(payload));
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (nextRetryAt == null) {
            nextRetryAt = now;
        }
    }

    // RabbitMQ publisher confirm received
    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.nextRetryAt = null;
        this.lastError = null;
    }

    // Schedule retry after publish failure
    public void scheduleRetry(
            Instant retryAt,
            String error,
            boolean terminalFailure) {
        this.retryCount++;
        this.lastError = error;

        if (terminalFailure) {
            this.status = OutboxStatus.FAILED;
            this.nextRetryAt = null;
        } else {
            this.status = OutboxStatus.PENDING;
            this.nextRetryAt = Objects.requireNonNull(retryAt);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getEventType() {
        return eventType;
    }

    public Map<String, Object> getPayload() {
        return Collections.unmodifiableMap(payload);
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Long getVersion() {
        return version;
    }
}
