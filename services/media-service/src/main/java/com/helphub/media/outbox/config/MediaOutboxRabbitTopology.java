package com.helphub.media.outbox.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@ConditionalOnProperty(prefix = "app.media.outbox", name = "enabled", havingValue = "true")
public class MediaOutboxRabbitTopology {

    public static final String DEV_QUEUE = "helphub.media.ready.inspect.v1";

    @Bean
    public TopicExchange mediaOutboxExchange(
            @Value("${app.media.outbox.exchange}") String exchangeName) {

        return new TopicExchange(
                exchangeName,
                true,
                false);
    }

    @Bean
    @Profile("dev")
    public Queue mediaReadyDevQueue() {
        return QueueBuilder
                .durable(DEV_QUEUE)
                .build();
    }

    @Bean
    @Profile("dev")
    public Binding mediaReadyDevBinding(
            @Qualifier("mediaReadyDevQueue") Queue queue,
            @Qualifier("mediaOutboxExchange") TopicExchange exchange,
            @Value("${app.media.outbox.routing-key}") String routingKey) {

        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(routingKey);
    }
}
