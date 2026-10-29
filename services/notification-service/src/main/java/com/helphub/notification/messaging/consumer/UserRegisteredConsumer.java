package com.helphub.notification.messaging.consumer;

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

        try {
            // 1. Read RabbitMQ message body
            String payload = new String(
                    message.getBody(),
                    StandardCharsets.UTF_8);

            // 2. Deserialize JSON
            UserRegisteredEvent event = jsonMapper.readValue(
                    payload,
                    UserRegisteredEvent.class);

            log.info(
                    "Received user registered event. eventId={}",
                    event.eventId());

            // 3. Execute business logic
            notificationService.handleUserRegistered(event);

            log.info(
                    "User registered event processed. eventId={}",
                    event.eventId());

        } catch (Exception exception) {

            log.error(
                    "Failed to process user registered event",
                    exception);

            // Important: propagate error to RabbitMQ listener container.
            throw exception;
        }
    }
}
