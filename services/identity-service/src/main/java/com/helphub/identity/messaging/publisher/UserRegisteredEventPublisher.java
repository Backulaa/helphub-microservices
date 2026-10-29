package com.helphub.identity.messaging.publisher;

import com.helphub.identity.messaging.event.UserRegisteredEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

@Component
public class UserRegisteredEventPublisher {

        private static final Logger log = LoggerFactory.getLogger(UserRegisteredEventPublisher.class);

        private static final String EVENTS_EXCHANGE = "helphub.events";

        private static final String USER_REGISTERED_ROUTING_KEY = "identity.user.registered.v1";

        private final RabbitTemplate rabbitTemplate;
        private final JsonMapper jsonMapper;

        public UserRegisteredEventPublisher(
                        RabbitTemplate rabbitTemplate,
                        JsonMapper jsonMapper) {
                this.rabbitTemplate = rabbitTemplate;
                this.jsonMapper = jsonMapper;
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void publish(UserRegisteredEvent event) {

                try {
                        // 1. Serialize the event to JSON.
                        byte[] payload = jsonMapper.writeValueAsBytes(event);

                        // 2. Set AMQP message properties.
                        MessageProperties properties = new MessageProperties();
                        properties.setContentType(
                                        MessageProperties.CONTENT_TYPE_JSON);
                        properties.setContentEncoding(
                                        StandardCharsets.UTF_8.name());
                        properties.setDeliveryMode(
                                        MessageDeliveryMode.PERSISTENT);
                        properties.setMessageId(
                                        event.eventId().toString());

                        // 3. Build the RabbitMQ message.
                        Message message = new Message(payload, properties);

                        // 4. Correlate publisher confirms with the event.
                        CorrelationData correlationData = new CorrelationData(event.eventId().toString());

                        // 5. Publish after PostgreSQL transaction commit.
                        rabbitTemplate.send(
                                        EVENTS_EXCHANGE,
                                        USER_REGISTERED_ROUTING_KEY,
                                        message,
                                        correlationData);

                        log.info(
                                        "User registered event submitted to RabbitMQ. eventId={}, userId={}",
                                        event.eventId(),
                                        event.data().userId());

                        // 6. Check asynchronous publisher confirmation.
                        correlationData.getFuture().whenComplete(
                                        (confirm, error) -> {

                                                if (error != null) {
                                                        log.error(
                                                                        "RabbitMQ publisher confirmation failed. eventId={}",
                                                                        event.eventId(),
                                                                        error);
                                                        return;
                                                }

                                                if (correlationData.getReturned() != null) {
                                                        log.error(
                                                                        "RabbitMQ message was not routed. eventId={}",
                                                                        event.eventId());
                                                        return;
                                                }

                                                if (confirm == null || !confirm.ack()) {
                                                        log.error(
                                                                        "RabbitMQ did not ACK published event. eventId={}, reason={}",
                                                                        event.eventId(),
                                                                        confirm != null ? confirm.reason()
                                                                                        : "No confirmation received");
                                                        return;
                                                }

                                                log.info(
                                                                "RabbitMQ confirmed user registered event. eventId={}",
                                                                event.eventId());
                                        });

                } catch (Exception exception) {

                        // The user registration has already committed.
                        // Log the failure without changing the API response.
                        log.error(
                                        "Unable to publish user registered event. eventId={}",
                                        event.eventId(),
                                        exception);
                }
        }
}
