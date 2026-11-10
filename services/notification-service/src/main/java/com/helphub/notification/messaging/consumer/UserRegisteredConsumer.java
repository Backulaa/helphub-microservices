package com.helphub.notification.messaging.consumer;

import com.helphub.observability.CorrelationContext;

import com.helphub.notification.config.RabbitMqConfig;
import com.helphub.notification.messaging.dto.UserRegisteredEvent;
import com.helphub.notification.notification.service.NotificationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

@Component
public class UserRegisteredConsumer {

        private static final Logger log = LoggerFactory.getLogger(UserRegisteredConsumer.class);

        private final JsonMapper jsonMapper;
        private final NotificationService notificationService;

        public UserRegisteredConsumer(
                        JsonMapper jsonMapper,
                        NotificationService notificationService) {

                this.jsonMapper = jsonMapper;
                this.notificationService = notificationService;
        }

        @RabbitListener(queues = RabbitMqConfig.NOTIFICATION_QUEUE)
        public void consume(Message message) throws Exception {

                // 1. Read Request ID from AMQP headers.
                Object headerValue = message.getMessageProperties()
                                .getHeaders()
                                .get(CorrelationContext.HEADER_NAME);

                String requestId = CorrelationContext.fromHeaderOrNew(headerValue);

                // 2. Set MDC for the entire message processing lifecycle.
                try (CorrelationContext.Scope ignored = CorrelationContext.open(requestId)) {

                        try {
                                if (!(headerValue instanceof String value)
                                                || !CorrelationContext.isValid(value)) {

                                        log.warn(
                                                        "RabbitMQ message has no valid X-Request-ID. Generated a new correlation ID.");
                                }

                                // 3. Read RabbitMQ message body.
                                String payload = new String(
                                                message.getBody(),
                                                StandardCharsets.UTF_8);

                                // 4. Deserialize JSON.
                                UserRegisteredEvent event = jsonMapper.readValue(
                                                payload,
                                                UserRegisteredEvent.class);

                                log.info(
                                                "Received user registered event. eventId={}",
                                                event.eventId());

                                // 5. Execute existing business logic.
                                notificationService.handleUserRegistered(event);

                                log.info(
                                                "User registered event processed. eventId={}",
                                                event.eventId());

                        } catch (Exception exception) {

                                log.error(
                                                "Failed to process user registered event",
                                                exception);

                                // Keep existing RabbitMQ error handling.
                                throw exception;
                        }
                }
        }
}
