package com.helphub.media.outbox.publisher;

import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MediaOutboxRabbitIntegrationTest {

    private static JdbcTemplate jdbc;
    private static CachingConnectionFactory factory;
    private static RabbitTemplate template;
    private static RabbitAdmin admin;

    private static final String ROUTING_KEY = "media.ready.v1";

    @BeforeAll
    static void setup() {
        String url = System.getenv("MEDIA_IT_JDBC_URL");
        String rabbitHost = System.getenv("MEDIA_IT_RABBIT_HOST");

        assertEquals(
                "jdbc:postgresql://helphub-media-it-db:5432/media_it",
                url);

        assertEquals(
                "helphub-media-it-rabbit",
                rabbitHost);

        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(url);
        ds.setUser("media_it");
        ds.setPassword(
                System.getenv("MEDIA_IT_JDBC_PASSWORD"));

        jdbc = new JdbcTemplate(ds);

        factory = new CachingConnectionFactory(rabbitHost);
        factory.setUsername("media_it");
        factory.setPassword(
                System.getenv("MEDIA_IT_RABBIT_PASSWORD"));

        factory.setPublisherConfirmType(
                CachingConnectionFactory.ConfirmType.CORRELATED);

        factory.setPublisherReturns(true);

        template = new RabbitTemplate(factory);
        template.setMandatory(true);

        admin = new RabbitAdmin(factory);
    }

    @AfterAll
    static void close() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @BeforeEach
    void createSchema() {
        jdbc.execute("DROP TABLE IF EXISTS outbox_events");

        jdbc.execute("""
                CREATE TABLE outbox_events (
                    id UUID PRIMARY KEY,
                    aggregate_id UUID NOT NULL,
                    aggregate_type VARCHAR(100) NOT NULL,
                    event_type VARCHAR(100) NOT NULL,
                    payload JSONB NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    retry_count INTEGER NOT NULL DEFAULT 0,
                    next_retry_at TIMESTAMPTZ,
                    last_error TEXT,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    published_at TIMESTAMPTZ,
                    version BIGINT NOT NULL DEFAULT 0
                )
                """);
    }

    private UUID insertPendingEvent() {
        UUID eventId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO outbox_events (
                    id, aggregate_id, aggregate_type,
                    event_type, payload, status,
                    retry_count, next_retry_at,
                    created_at, version
                )
                VALUES (
                    ?, ?, 'MEDIA', 'media.ready.v1',
                    CAST(? AS jsonb), 'PENDING',
                    0, CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP, 0
                )
                """,
                eventId,
                mediaId,
                "{\"mediaId\":\"" + mediaId + "\"}");

        return eventId;
    }

    private MediaOutboxPublisherJob newJob(
            String exchangeName) {

        OutboxJdbcStore store = new OutboxJdbcStore(jdbc);

        RabbitOutboxSender sender = new RabbitOutboxSender(
                template, exchangeName, ROUTING_KEY, 10);

        return new MediaOutboxPublisherJob(
                store,
                sender,
                10, // batchSize
                180, // leaseSeconds
                10, // confirmTimeoutSeconds
                10, // maxRetries
                10, // retryBaseSeconds
                300 // maxBackoffSeconds
        );
    }

    @Test
    void unroutableMessageMustNotBePublished() {

        String exchangeName = "media.it.unroutable." + UUID.randomUUID();

        TopicExchange exchange = new TopicExchange(exchangeName, false, false);

        admin.declareExchange(exchange);

        try {
            // Exchange exists, but no queue is bound.
            UUID eventId = insertPendingEvent();

            newJob(exchangeName).publishPendingEvents();

            String status = jdbc.queryForObject(
                    "SELECT status FROM outbox_events WHERE id = ?",
                    String.class,
                    eventId);

            String lastError = jdbc.queryForObject(
                    "SELECT last_error FROM outbox_events WHERE id = ?",
                    String.class,
                    eventId);

            Integer retries = jdbc.queryForObject(
                    "SELECT retry_count FROM outbox_events WHERE id = ?",
                    Integer.class,
                    eventId);

            Integer publishedCount = jdbc.queryForObject(
                    """
                            SELECT COUNT(*)
                            FROM outbox_events
                            WHERE id = ?
                              AND published_at IS NOT NULL
                            """,
                    Integer.class,
                    eventId);

            assertEquals("FAILED", status);
            assertEquals("UNROUTABLE_MESSAGE", lastError);
            assertEquals(1, retries);
            assertEquals(0, publishedCount);

        } finally {
            admin.deleteExchange(exchangeName);
        }
    }

    @Test
    void routableMessageMustBePublished() {

        String exchangeName = "media.it.routable." + UUID.randomUUID();

        String queueName = "media.it.queue." + UUID.randomUUID();

        TopicExchange exchange = new TopicExchange(exchangeName, false, false);

        Queue queue = new Queue(
                queueName, true, false, false);

        admin.declareExchange(exchange);
        admin.declareQueue(queue);

        admin.declareBinding(
                BindingBuilder.bind(queue)
                        .to(exchange)
                        .with(ROUTING_KEY));

        try {
            UUID eventId = insertPendingEvent();

            newJob(exchangeName).publishPendingEvents();

            String status = jdbc.queryForObject(
                    "SELECT status FROM outbox_events WHERE id = ?",
                    String.class,
                    eventId);

            assertEquals("PUBLISHED", status);

            Integer publishedCount = jdbc.queryForObject(
                    """
                            SELECT COUNT(*)
                            FROM outbox_events
                            WHERE id = ?
                              AND published_at IS NOT NULL
                            """,
                    Integer.class,
                    eventId);

            assertEquals(1, publishedCount);

            Message message = template.receive(queueName);

            assertNotNull(message);

            assertEquals(
                    eventId.toString(),
                    message.getMessageProperties().getMessageId());

        } finally {
            admin.deleteQueue(queueName);
            admin.deleteExchange(exchangeName);
        }
    }
}
