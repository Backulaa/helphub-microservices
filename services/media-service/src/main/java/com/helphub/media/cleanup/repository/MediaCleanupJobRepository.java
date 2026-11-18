package com.helphub.media.cleanup.repository;

import com.helphub.media.cleanup.entity.CleanupStatus;
import com.helphub.media.cleanup.entity.MediaCleanupJob;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaCleanupJobRepository
        extends JpaRepository<MediaCleanupJob, UUID> {

    // One cleanup job per media
    Optional<MediaCleanupJob> findByMedia_Id(UUID mediaId);

    boolean existsByMedia_Id(UUID mediaId);

    // Jobs that are due
    List<MediaCleanupJob> findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            CleanupStatus status,
            Instant now);

    // Find expired worker leases
    List<MediaCleanupJob> findTop100ByStatusAndLeaseUntilLessThanEqual(
            CleanupStatus status,
            Instant now);

    // Monitoring
    long countByStatus(CleanupStatus status);
}
