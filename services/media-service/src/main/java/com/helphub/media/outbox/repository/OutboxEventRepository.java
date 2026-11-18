package com.helphub.media.outbox.repository;

import com.helphub.media.outbox.entity.OutboxEvent;
import com.helphub.media.outbox.entity.OutboxStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, UUID> {

    // Events currently eligible for publishing
    List<OutboxEvent> findTop100ByStatusAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(
            OutboxStatus status,
            Instant now);

    // Monitoring
    long countByStatus(OutboxStatus status);

    // Trace events related to one aggregate
    List<OutboxEvent> findByAggregateId(UUID aggregateId);
}
