package com.helphub.media.media.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "media", indexes = {
        @Index(name = "idx_media_owner_id", columnList = "owner_id"),
        @Index(name = "idx_media_status", columnList = "status")
})
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "public_id", nullable = false, unique = true, length = 255)
    private String publicId;

    @Column(name = "asset_id", unique = true, length = 255)
    private String assetId;

    @Column(name = "secure_url", columnDefinition = "TEXT")
    private String secureUrl;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "format", length = 20)
    private String format;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 20)
    private MediaResourceType resourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private MediaVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MediaStatus status = MediaStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Media() {
        // Required by JPA
    }

    public Media(
            UUID ownerId,
            String publicId,
            String originalFilename,
            String mimeType,
            MediaResourceType resourceType,
            MediaVisibility visibility) {
        this.ownerId = Objects.requireNonNull(ownerId);
        this.publicId = Objects.requireNonNull(publicId);
        this.originalFilename = Objects.requireNonNull(originalFilename);
        this.mimeType = Objects.requireNonNull(mimeType);
        this.resourceType = Objects.requireNonNull(resourceType);
        this.visibility = Objects.requireNonNull(visibility);
        this.status = MediaStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getPublicId() {
        return publicId;
    }

    public String getAssetId() {
        return assetId;
    }

    public String getSecureUrl() {
        return secureUrl;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getFormat() {
        return format;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public MediaResourceType getResourceType() {
        return resourceType;
    }

    public MediaVisibility getVisibility() {
        return visibility;
    }

    public MediaStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void markReady(
            String assetId,
            String format,
            long sizeBytes,
            String secureUrl) {

        if (this.status != MediaStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING media can become READY");
        }

        if (sizeBytes <= 0) {
            throw new IllegalArgumentException(
                    "Media size must be positive");
        }

        this.assetId = Objects.requireNonNull(assetId);
        this.format = Objects.requireNonNull(format);
        this.sizeBytes = sizeBytes;
        this.secureUrl = Objects.requireNonNull(secureUrl);
        this.status = MediaStatus.READY;
    }

    // ==========================================
    // PRIVATE UPLOAD - START
    // ==========================================
    public void markUploading() {

        if (this.status != MediaStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING media can start uploading");
        }

        if (this.visibility != MediaVisibility.PRIVATE) {
            throw new IllegalStateException(
                    "Only PRIVATE media can use server upload");
        }

        this.status = MediaStatus.UPLOADING;
    }

    // ==========================================
    // PRIVATE UPLOAD - COMPLETE
    // ==========================================
    public void markPrivateReady(
            String assetId,
            String format,
            long sizeBytes) {

        if (this.status != MediaStatus.UPLOADING) {
            throw new IllegalStateException(
                    "Only UPLOADING media can become READY");
        }

        if (this.visibility != MediaVisibility.PRIVATE) {
            throw new IllegalStateException(
                    "Media must be PRIVATE");
        }

        if (sizeBytes <= 0) {
            throw new IllegalArgumentException(
                    "Media size must be positive");
        }

        this.assetId = Objects.requireNonNull(assetId);
        this.format = Objects.requireNonNull(format);
        this.sizeBytes = sizeBytes;

        // Do not persist a private delivery URL here.
        this.secureUrl = null;

        this.status = MediaStatus.READY;
    }
}
