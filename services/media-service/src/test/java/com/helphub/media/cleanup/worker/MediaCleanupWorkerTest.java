package com.helphub.media.cleanup.worker;

import com.helphub.media.media.cloudinary.CloudinaryAssetDeletionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.io.IOException;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MediaCleanupWorkerTest {

    private JdbcTemplate jdbc;
    private PlatformTransactionManager manager;
    private CloudinaryAssetDeletionService storage;
    private MediaCleanupWorker worker;

    private final UUID jobId = UUID.randomUUID();
    private final UUID mediaId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        manager = mock(PlatformTransactionManager.class);
        storage = mock(CloudinaryAssetDeletionService.class);

        when(manager.getTransaction(
                any(TransactionDefinition.class)))
                .thenReturn(mock(TransactionStatus.class));

        worker = new MediaCleanupWorker(
                jdbc,
                manager,
                storage,
                10,
                180,
                8);
    }

    @SuppressWarnings("unchecked")
    private void arrangeJob(int attempts) throws Exception {

        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);

            RowMapper<Object> mapper = (RowMapper<Object>) invocation.getArgument(1);

            ResultSet rs = mock(ResultSet.class);

            if (sql.contains("WITH picked")) {

                when(rs.getObject("id", UUID.class))
                        .thenReturn(jobId);

                when(rs.getObject("media_id", UUID.class))
                        .thenReturn(mediaId);

                when(rs.getInt("attempts"))
                        .thenReturn(attempts);

                return List.of(mapper.mapRow(rs, 0));
            }

            if (sql.contains("FROM media m")) {

                when(rs.getString("public_id"))
                        .thenReturn("test/retry");

                when(rs.getString("resource_type"))
                        .thenReturn("IMAGE");

                when(rs.getString("visibility"))
                        .thenReturn("PRIVATE");

                when(rs.getString("status"))
                        .thenReturn("DELETING");

                when(rs.getObject(
                        "deleted_at",
                        OffsetDateTime.class))
                        .thenReturn(
                                OffsetDateTime.now(ZoneOffset.UTC));

                when(rs.getBoolean("has_reference"))
                        .thenReturn(false);

                return List.of(mapper.mapRow(rs, 0));
            }

            return List.of();

        }).when(jdbc).query(
                anyString(),
                any(RowMapper.class),
                any(Object[].class));

        when(jdbc.update(
                anyString(),
                any(Object[].class)))
                .thenReturn(1);
    }

    @Test
    void cloudinaryTimeoutShouldScheduleRetry()
            throws Exception {

        arrangeJob(1);

        doThrow(new IOException("Simulated timeout"))
                .when(storage)
                .delete("test/retry", "IMAGE", "PRIVATE");

        worker.run();

        verify(jdbc).update(
                contains("last_error = ?"),
                eq("PENDING"),
                any(OffsetDateTime.class),
                eq("IOException"),
                eq(jobId),
                eq(1));
    }

    @Test
    void maxAttemptsShouldMarkFailed()
            throws Exception {

        arrangeJob(8);

        doThrow(new IOException("Simulated timeout"))
                .when(storage)
                .delete("test/retry", "IMAGE", "PRIVATE");

        worker.run();

        verify(jdbc).update(
                contains("last_error = ?"),
                eq("FAILED"),
                isNull(),
                eq("IOException"),
                eq(jobId),
                eq(8));
    }

    @Test
    void successfulDeletionShouldFinalizeJobAndMedia()
            throws Exception {

        arrangeJob(1);

        worker.run();

        // Cloudinary deletion must be called.
        verify(storage).delete(
                "test/retry",
                "IMAGE",
                "PRIVATE");

        // Cleanup job becomes COMPLETED.
        verify(jdbc).update(
                contains("SET status = 'COMPLETED'"),
                eq(jobId),
                eq(1));

        // Media becomes DELETED.
        verify(jdbc).update(
                contains("SET status = 'DELETED'"),
                eq(mediaId));
    }

    @Test
    void staleWorkerMustNotFinalizeMedia()
            throws Exception {

        arrangeJob(1);

        // Simulate a job already reclaimed
        // by another worker.
        when(jdbc.update(
                contains("SET status = 'COMPLETED'"),
                any(Object[].class))).thenReturn(0);

        worker.run();

        // Current worker must not update media
        // if job finalization affected zero rows.
        verify(jdbc, never()).update(
                contains("SET status = 'DELETED'"),
                any(Object[].class));
    }
}
