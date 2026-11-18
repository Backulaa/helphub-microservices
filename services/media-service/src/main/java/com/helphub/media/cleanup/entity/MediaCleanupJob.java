package com.helphub.media.cleanup.entity;

import com.helphub.media.media.entity.Media;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "media_cleanup_jobs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_media_cleanup_jobs_media", columnNames = "media_id")
}, indexes = {
        @Index(name = "idx_cleanup_status_retry", columnList = "status, next_retry_at"),
        @Index(name = "idx_cleanup_status_lease", columnList = "status, lease_until")
})
public class MediaCleanupJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false, foreignKey = @ForeignKey(name = "fk_media_cleanup_jobs_media"))
    private Media media;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CleanupStatus status = CleanupStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    // Used to recover abandoned RUNNING jobs
    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version")
    private Long version;

    protected MediaCleanupJob() {
    }

    public MediaCleanupJob(
            Media media,
            Instant nextRetryAt) {
        this.media = Objects.requireNonNull(media);
        this.nextRetryAt = Objects.requireNonNull(nextRetryAt);
        this.status = CleanupStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Worker claims the job with a limited lease
    public void markRunning(Instant leaseUntil) {
        this.status = CleanupStatus.RUNNING;
        this.leaseUntil = Objects.requireNonNull(leaseUntil);
        this.attempts++;
    }

    // Cloudinary deletion completed
    public void markCompleted() {
        this.status = CleanupStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.nextRetryAt = null;
        this.leaseUntil = null;
        this.lastError = null;
    }

    // Retry or terminal failure
    public void scheduleRetry(
            Instant retryAt,
            String error,
            boolean terminalFailure) {
        this.lastError = error;
        this.leaseUntil = null;

        if (terminalFailure) {
            this.status = CleanupStatus.FAILED;
            this.nextRetryAt = null;
        } else {
            this.status = CleanupStatus.PENDING;
            this.nextRetryAt = Objects.requireNonNull(retryAt);
        }
    }

    // Defer without increasing attempt count
    public void deferUntil(
            Instant retryAt,
            String reason) {
        this.status = CleanupStatus.PENDING;
        this.nextRetryAt = Objects.requireNonNull(retryAt);
        this.leaseUntil = null;
        this.lastError = reason;
    }

    public UUID getId() {
        return id;
    }

    public Media getMedia() {
        return media;
    }

    public CleanupStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Long getVersion() {
        return version;
    }
}
