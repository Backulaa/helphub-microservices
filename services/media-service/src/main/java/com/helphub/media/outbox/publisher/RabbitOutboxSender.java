
package com.helphub.media.outbox.publisher;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Service
public class RabbitOutboxSender {

    public static class PublishException
            extends RuntimeException {

        private final String errorCode;

        public PublishException(
                String errorCode,
                Throwable cause) {

            super(errorCode, cause);
            this.errorCode = errorCode;
        }

        public String errorCode() {
            return errorCode;
        }
    }

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;
    private final int confirmTimeoutSeconds;

    public RabbitOutboxSender(
            RabbitTemplate rabbitTemplate,
            @Value("${app.media.outbox.exchange}") String exchange,
            @Value("${app.media.outbox.routing-key}") String routingKey,
            @Value("${app.media.outbox.confirm-timeout-seconds:10}") int confirmTimeoutSeconds) {

        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.confirmTimeoutSeconds = confirmTimeoutSeconds;
    }

    public void publish(
            OutboxJdbcStore.ClaimedEvent event) {

        MessageProperties properties = new MessageProperties();

        properties.setContentType(
                MessageProperties.CONTENT_TYPE_JSON);

        properties.setDeliveryMode(
                MessageDeliveryMode.PERSISTENT);

        properties.setMessageId(
                event.id().toString());

        properties.setType(event.eventType());

        properties.setHeader(
                "eventId",
                event.id().toString());

        properties.setHeader(
                "eventType",
                event.eventType());

        properties.setHeader(
                "aggregateId",
                event.aggregateId().toString());

        properties.setHeader(
                "aggregateType",
                event.aggregateType());

        Message message = new Message(
                event.payload().getBytes(
                        StandardCharsets.UTF_8),
                properties);

        CorrelationData correlation = new CorrelationData(
                event.id().toString());

        try {
            rabbitTemplate.send(
                    exchange,
                    routingKey,
                    message,
                    correlation);

            CorrelationData.Confirm confirm = correlation.getFuture().get(
                    confirmTimeoutSeconds,
                    TimeUnit.SECONDS);

            if (!confirm.ack()) {
                throw new PublishException(
                        "BROKER_NACK",
                        null);
            }

            // ACK alone is not enough:
            // detect mandatory messages that were
            // returned because no queue matched.
            if (correlation.getReturned() != null) {
                throw new PublishException(
                        "UNROUTABLE_MESSAGE",
                        null);
            }

        } catch (PublishException ex) {
            throw ex;

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();

            throw new PublishException(
                    "PUBLISH_INTERRUPTED",
                    ex);

        } catch (java.util.concurrent.TimeoutException ex) {
            throw new PublishException(
                    "CONFIRM_TIMEOUT",
                    ex);

        } catch (Exception ex) {
            throw new PublishException(
                    "BROKER_PUBLISH_ERROR",
                    ex);
        }
    }
}
