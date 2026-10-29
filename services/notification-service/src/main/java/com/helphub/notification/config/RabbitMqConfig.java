package com.helphub.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.AmqpAdmin;

import org.springframework.boot.ApplicationRunner;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

        // ==========================================
        // Main Domain Event Exchange
        // ==========================================
        public static final String EVENTS_EXCHANGE = "helphub.events";

        // ==========================================
        // Notification Queue
        // ==========================================
        public static final String NOTIFICATION_QUEUE = "notification.identity-events.v1.q";

        // Event emitted by Identity Service
        public static final String USER_REGISTERED_KEY = "identity.user.registered.v1";

        // ==========================================
        // Dead Letter Configuration
        // ==========================================
        public static final String DEAD_LETTER_EXCHANGE = "helphub.dlx";

        public static final String DEAD_LETTER_QUEUE = "notification.identity-events.v1.dlq";

        public static final String DEAD_LETTER_ROUTING_KEY = "notification.identity-events.dead";

        // ==========================================
        // 1. Main Topic Exchange
        // ==========================================
        @Bean
        public TopicExchange eventsExchange() {
                return new TopicExchange(
                                EVENTS_EXCHANGE,
                                true,
                                false);
        }

        // ==========================================
        // 2. Dead Letter Exchange
        // ==========================================
        @Bean
        public DirectExchange deadLetterExchange() {
                return new DirectExchange(
                                DEAD_LETTER_EXCHANGE,
                                true,
                                false);
        }

        // ==========================================
        // 3. Main Notification Queue
        // ==========================================
        @Bean
        public Queue notificationQueue() {
                return QueueBuilder
                                .durable(NOTIFICATION_QUEUE)
                                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                                .build();
        }

        // ==========================================
        // 4. Dead Letter Queue
        // ==========================================
        @Bean
        public Queue notificationDeadLetterQueue() {
                return QueueBuilder
                                .durable(DEAD_LETTER_QUEUE)
                                .build();
        }

        // ==========================================
        // 5. Bind Main Queue to Topic Exchange
        // ==========================================
        @Bean
        public Binding notificationBinding(
                        @Qualifier("notificationQueue") Queue notificationQueue,
                        @Qualifier("eventsExchange") TopicExchange eventsExchange) {

                return BindingBuilder
                                .bind(notificationQueue)
                                .to(eventsExchange)
                                .with(USER_REGISTERED_KEY);
        }

        // ==========================================
        // 6. Bind DLQ to Dead Letter Exchange
        // ==========================================
        @Bean
        public Binding deadLetterBinding(
                        @Qualifier("notificationDeadLetterQueue") Queue notificationDeadLetterQueue,
                        @Qualifier("deadLetterExchange") DirectExchange deadLetterExchange) {

                return BindingBuilder
                                .bind(notificationDeadLetterQueue)
                                .to(deadLetterExchange)
                                .with(DEAD_LETTER_ROUTING_KEY);
        }

        @Bean
        public ApplicationRunner initializeRabbitTopology(
                        AmqpAdmin amqpAdmin,
                        @Qualifier("eventsExchange") TopicExchange eventsExchange,
                        @Qualifier("deadLetterExchange") DirectExchange deadLetterExchange,
                        @Qualifier("notificationQueue") Queue notificationQueue,
                        @Qualifier("notificationDeadLetterQueue") Queue deadLetterQueue,
                        @Qualifier("notificationBinding") Binding notificationBinding,
                        @Qualifier("deadLetterBinding") Binding deadLetterBinding) {

                return args -> {
                        amqpAdmin.declareExchange(eventsExchange);
                        amqpAdmin.declareExchange(deadLetterExchange);

                        amqpAdmin.declareQueue(notificationQueue);
                        amqpAdmin.declareQueue(deadLetterQueue);

                        amqpAdmin.declareBinding(notificationBinding);
                        amqpAdmin.declareBinding(deadLetterBinding);
                };
        }
}
