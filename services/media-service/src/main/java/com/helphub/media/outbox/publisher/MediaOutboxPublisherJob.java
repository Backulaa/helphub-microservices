package com.helphub.media.outbox.publisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.media.outbox", name = "enabled", havingValue = "true")
public class MediaOutboxPublisherJob {

    private static final Logger log = LoggerFactory.getLogger(
            MediaOutboxPublisherJob.class);

    private final OutboxJdbcStore store;
    private final RabbitOutboxSender sender;

    private final int batchSize;
    private final int leaseSeconds;
    @SuppressWarnings("unused")
    private final int confirmTimeoutSeconds;
    private final int maxRetries;
    private final int retryBaseSeconds;
    private final int maxBackoffSeconds;

    public MediaOutboxPublisherJob(
            OutboxJdbcStore store,
            RabbitOutboxSender sender,

            @Value("${app.media.outbox.batch-size:10}") int batchSize,

            @Value("${app.media.outbox.lease-seconds:180}") int leaseSeconds,

            @Value("${app.media.outbox.confirm-timeout-seconds:10}") int confirmTimeoutSeconds,

            @Value("${app.media.outbox.max-retries:10}") int maxRetries,

            @Value("${app.media.outbox.retry-base-seconds:10}") int retryBaseSeconds,

            @Value("${app.media.outbox.max-backoff-seconds:300}") int maxBackoffSeconds) {

        if (batchSize <= 0
                || leaseSeconds <= batchSize * (long) confirmTimeoutSeconds
                || confirmTimeoutSeconds <= 0
                || maxRetries <= 0
                || retryBaseSeconds <= 0
                || maxBackoffSeconds <= 0) {

            throw new IllegalArgumentException(
                    "Invalid Media Outbox publisher configuration");
        }

        this.store = store;
        this.sender = sender;
        this.batchSize = batchSize;
        this.leaseSeconds = leaseSeconds;
        this.confirmTimeoutSeconds = confirmTimeoutSeconds;
        this.maxRetries = maxRetries;
        this.retryBaseSeconds = retryBaseSeconds;
        this.maxBackoffSeconds = maxBackoffSeconds;
    }

    @Scheduled(initialDelayString = "${app.media.outbox.initial-delay-ms:15000}", fixedDelayString = "${app.media.outbox.fixed-delay-ms:5000}")
    public void publishPendingEvents() {

        List<OutboxJdbcStore.ClaimedEvent> events;

        try {
            events = store.claimBatch(
                    batchSize,
                    leaseSeconds,
                    maxRetries);

        } catch (Exception ex) {
            log.error(
                    "Media Outbox claim failed",
                    ex);
            return;
        }

        for (OutboxJdbcStore.ClaimedEvent event : events) {

            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            try {
                sender.publish(event);

                boolean updated = store.markPublished(event);

                if (updated) {
                    log.info(
                            "OUTBOX_PUBLISHED eventId={} "
                                    + "eventType={} mediaId={}",
                            event.id(),
                            event.eventType(),
                            event.aggregateId());
                } else {
                    log.warn(
                            "OUTBOX_STALE_CONFIRM eventId={}",
                            event.id());
                }

            } catch (RabbitOutboxSender.PublishException ex) {

                long delay = retryDelaySeconds(
                        event.retryCount());

                boolean updated = store.markFailed(
                        event,
                        ex.errorCode(),
                        delay,
                        maxRetries);

                if (updated) {
                    log.warn(
                            "OUTBOX_RETRY eventId={} "
                                    + "errorCode={} attempt={}/{}",
                            event.id(),
                            ex.errorCode(),
                            event.retryCount() + 1,
                            maxRetries);
                } else {
                    log.warn(
                            "OUTBOX_STALE_FAILURE eventId={}",
                            event.id());
                }

            } catch (Exception ex) {
                log.error(
                        "OUTBOX_PROCESSING_ERROR eventId={}",
                        event.id(),
                        ex);

                // The lease will eventually expire,
                // allowing another attempt.
            }
        }
    }

    private long retryDelaySeconds(int retryCount) {

        int exponent = Math.min(retryCount, 10);

        long delay = (long) retryBaseSeconds << exponent;

        return Math.min(delay, maxBackoffSeconds);
    }
}
