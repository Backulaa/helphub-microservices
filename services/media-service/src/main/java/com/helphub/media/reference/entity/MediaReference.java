package com.helphub.media.reference.entity;

import com.helphub.media.media.entity.Media;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "media_references", uniqueConstraints = {
        @UniqueConstraint(name = "uk_media_references_binding", columnNames = {
                "media_id",
                "service_name",
                "resource_type",
                "resource_id"
        })
}, indexes = {
        @Index(name = "idx_media_references_media", columnList = "media_id"),
        @Index(name = "idx_media_references_resource", columnList = "service_name, resource_type, resource_id")
})
public class MediaReference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Foreign key within Media DB
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false, foreignKey = @ForeignKey(name = "fk_media_references_media"))
    private Media media;

    // Example: COMMUNITY, SUPPORT, IDENTITY
    @Column(name = "service_name", nullable = false, length = 50)
    private String serviceName;

    // Example: POST, SUPPORT_REQUEST, AVATAR
    @Column(name = "resource_type", nullable = false, length = 50)
    private String resourceType;

    // UUID of the business resource
    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MediaReference() {
    }

    public MediaReference(
            Media media,
            String serviceName,
            String resourceType,
            UUID resourceId) {
        this.media = Objects.requireNonNull(media);
        this.serviceName = Objects.requireNonNull(serviceName);
        this.resourceType = Objects.requireNonNull(resourceType);
        this.resourceId = Objects.requireNonNull(resourceId);
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public Media getMedia() {
        return media;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getResourceType() {
        return resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
