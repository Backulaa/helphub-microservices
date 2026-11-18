package com.helphub.media.media.repository;

import com.helphub.media.media.entity.Media;
import com.helphub.media.media.entity.MediaStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaRepository
        extends JpaRepository<Media, UUID> {

    Optional<Media> findByPublicId(String publicId);

    boolean existsByPublicId(String publicId);

    List<Media> findByOwnerId(UUID ownerId);

    List<Media> findByStatus(MediaStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Media m WHERE m.id = :id")
    Optional<Media> findByIdForUpdate(
            @Param("id") UUID id);

    @Query(value = """
            SELECT id
            FROM media
            WHERE visibility = 'PRIVATE'
              AND status = 'UPLOADING'
              AND updated_at < :cutoff
            ORDER BY updated_at ASC, id ASC
            LIMIT :batchSize
            """, nativeQuery = true)
    List<UUID> findStalePrivateUploadingIds(
            @Param("cutoff") OffsetDateTime cutoff,
            @Param("batchSize") int batchSize);
}
