package com.helphub.media.cleanup.worker;

import com.helphub.media.media.cloudinary.CloudinaryAssetDeletionService;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;

import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MediaCleanupIntegrationTest {

    private static PGSimpleDataSource dataSource;
    private static JdbcTemplate jdbc;

    private record TestJob(UUID mediaId, UUID jobId) {
    }

    @BeforeAll
    static void connect() {

        String url = System.getenv("MEDIA_IT_JDBC_URL");

        // Safety: refuse to run against the real application DB.
        assertEquals(
                "jdbc:postgresql://helphub-media-it-db:5432/media_it",
                url,
                "Use the isolated media_it PostgreSQL container");

        dataSource = new PGSimpleDataSource();
        dataSource.setUrl(url);
        dataSource.setUser("media_it");
        dataSource.setPassword(
                System.getenv("MEDIA_IT_JDBC_PASSWORD"));

        jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void resetSchema() {

        jdbc.execute("DROP TABLE IF EXISTS media_cleanup_jobs");
        jdbc.execute("DROP TABLE IF EXISTS media_references");
        jdbc.execute("DROP TABLE IF EXISTS media");

        jdbc.execute("""
                CREATE TABLE media (
                    id UUID PRIMARY KEY,
                    public_id VARCHAR(255) NOT NULL,
                    resource_type VARCHAR(20) NOT NULL,
                    visibility VARCHAR(20) NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    deleted_at TIMESTAMPTZ,
                    updated_at TIMESTAMPTZ
                )
                """);

        jdbc.execute("""
                CREATE TABLE media_references (
                    id UUID PRIMARY KEY,
                    media_id UUID NOT NULL REFERENCES media(id)
                )
                """);

        jdbc.execute("""
                CREATE TABLE media_cleanup_jobs (
                    id UUID PRIMARY KEY,
                    media_id UUID NOT NULL UNIQUE REFERENCES media(id),
                    status VARCHAR(20) NOT NULL,
                    attempts INTEGER NOT NULL DEFAULT 0,
                    next_retry_at TIMESTAMPTZ,
                    lease_until TIMESTAMPTZ,
                    last_error TEXT,
                    completed_at TIMESTAMPTZ,
                    version BIGINT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
    }

    private TestJob createPendingJob(int attempts) {

        UUID mediaId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO media (
                    id, public_id, resource_type,
                    visibility, status, deleted_at, updated_at
                )
                VALUES (
                    ?, ?, 'IMAGE', 'PRIVATE',
                    'DELETING', NOW() - INTERVAL '1 day', NOW()
                )
                """, mediaId, "integration/" + mediaId);

        jdbc.update("""
                INSERT INTO media_cleanup_jobs (
                    id, media_id, status, attempts,
                    next_retry_at, version, updated_at
                )
                VALUES (
                    ?, ?, 'PENDING', ?,
                    NOW() - INTERVAL '1 second', 0, NOW()
                )
                """, jobId, mediaId, attempts);

        return new TestJob(mediaId, jobId);
    }

    private MediaCleanupWorker newWorker(
            CloudinaryAssetDeletionService storage) {

        return new MediaCleanupWorker(
                new JdbcTemplate(dataSource),
                new DataSourceTransactionManager(dataSource),
                storage,
                10,
                180,
                8);
    }

    private String jobStatus(UUID jobId) {
        return jdbc.queryForObject(
                "SELECT status FROM media_cleanup_jobs WHERE id = ?",
                String.class,
                jobId);
    }

    private int attempts(UUID jobId) {
        return jdbc.queryForObject(
                "SELECT attempts FROM media_cleanup_jobs WHERE id = ?",
                Integer.class,
                jobId);
    }

    @Test
    void cloudinaryTimeoutPersistsRetry() throws Exception {

        TestJob job = createPendingJob(0);

        CloudinaryAssetDeletionService storage = mock(CloudinaryAssetDeletionService.class);

        doThrow(new IOException("Simulated timeout"))
                .when(storage)
                .delete(anyString(), anyString(), anyString());

        newWorker(storage).run();

        assertEquals("PENDING", jobStatus(job.jobId()));
        assertEquals(1, attempts(job.jobId()));

        String error = jdbc.queryForObject(
                "SELECT last_error FROM media_cleanup_jobs WHERE id = ?",
                String.class,
                job.jobId());

        assertEquals("IOException", error);

        OffsetDateTime retryAt = jdbc.queryForObject(
                "SELECT next_retry_at FROM media_cleanup_jobs WHERE id = ?",
                (rs, rowNum) -> rs.getObject(1, OffsetDateTime.class),
                job.jobId());

        assertNotNull(retryAt);
        assertTrue(retryAt.isAfter(OffsetDateTime.now()));

        assertEquals(
                "DELETING",
                jdbc.queryForObject(
                        "SELECT status FROM media WHERE id = ?",
                        String.class,
                        job.mediaId()));
    }

    @Test
    void maxAttemptsPersistsFailedStatus() throws Exception {

        TestJob job = createPendingJob(7);

        CloudinaryAssetDeletionService storage = mock(CloudinaryAssetDeletionService.class);

        doThrow(new IOException("Simulated timeout"))
                .when(storage)
                .delete(anyString(), anyString(), anyString());

        newWorker(storage).run();

        assertEquals("FAILED", jobStatus(job.jobId()));
        assertEquals(8, attempts(job.jobId()));

        Integer retryCount = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM media_cleanup_jobs
                        WHERE id = ?
                          AND next_retry_at IS NOT NULL
                        """,
                Integer.class,
                job.jobId());

        assertEquals(0, retryCount);
    }

    @Test
    void skipLockedDoesNotWaitForLockedJob() throws Exception {

        TestJob job = createPendingJob(0);

        CloudinaryAssetDeletionService storage = mock(CloudinaryAssetDeletionService.class);

        ExecutorService pool = Executors.newSingleThreadExecutor();

        try (Connection lockConnection = dataSource.getConnection()) {

            lockConnection.setAutoCommit(false);

            try (PreparedStatement statement = lockConnection.prepareStatement(
                    """
                            SELECT id
                            FROM media_cleanup_jobs
                            WHERE id = ?
                            FOR UPDATE
                            """)) {

                statement.setObject(1, job.jobId());

                try (ResultSet ignored = statement.executeQuery()) {
                    assertTrue(ignored.next());
                }
            }

            try {
                Future<?> result = pool.submit(() -> newWorker(storage).run());

                // SKIP LOCKED must return without waiting
                // for the row lock to be released.
                result.get(5, TimeUnit.SECONDS);

                assertEquals("PENDING", jobStatus(job.jobId()));
                assertEquals(0, attempts(job.jobId()));

                verifyNoInteractions(storage);

            } finally {
                lockConnection.rollback();
            }

        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentWorkersDoNotDeleteTwice() throws Exception {

        TestJob job = createPendingJob(0);

        CountDownLatch enteredDeletion = new CountDownLatch(1);
        CountDownLatch releaseDeletion = new CountDownLatch(1);

        CloudinaryAssetDeletionService storage = mock(CloudinaryAssetDeletionService.class);

        doAnswer(invocation -> {
            enteredDeletion.countDown();

            if (!releaseDeletion.await(10, TimeUnit.SECONDS)) {
                throw new IOException("Test timeout");
            }

            return null;
        }).when(storage).delete(
                anyString(), anyString(), anyString());

        MediaCleanupWorker workerA = newWorker(storage);
        MediaCleanupWorker workerB = newWorker(storage);

        ExecutorService pool = Executors.newSingleThreadExecutor();

        try {
            Future<?> first = pool.submit(workerA::run);

            // Worker A has claimed the job and
            // is blocked inside Cloudinary deletion.
            assertTrue(
                    enteredDeletion.await(8, TimeUnit.SECONDS),
                    "Worker A did not reach deletion");

            // Worker B must not claim the same RUNNING job.
            workerB.run();

            assertEquals("RUNNING", jobStatus(job.jobId()));
            assertEquals(1, attempts(job.jobId()));

            releaseDeletion.countDown();
            first.get(10, TimeUnit.SECONDS);

            verify(storage, times(1)).delete(
                    anyString(), anyString(), anyString());

            assertEquals("COMPLETED", jobStatus(job.jobId()));
            assertEquals(1, attempts(job.jobId()));

            String mediaStatus = jdbc.queryForObject(
                    "SELECT status FROM media WHERE id = ?",
                    String.class,
                    job.mediaId());

            assertEquals("DELETED", mediaStatus);

        } finally {
            releaseDeletion.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void cloudinaryDeletedButDatabaseRollbackShouldRetry()
            throws Exception {

        TestJob job = createPendingJob(0);

        // Giả lập Cloudinary:
        // Lần 1: Xóa thành công.
        // Lần 2: Asset đã không còn tồn tại.
        Cloudinary cloudinary = mock(Cloudinary.class);
        Uploader uploader = mock(Uploader.class);

        when(cloudinary.uploader()).thenReturn(uploader);

        when(uploader.destroy(
                eq("integration/" + job.mediaId()),
                anyMap()))
                .thenReturn(
                        Map.of("result", "ok"),
                        Map.of("result", "not found"));

        CloudinaryAssetDeletionService storage = new CloudinaryAssetDeletionService(cloudinary);

        MediaCleanupWorker worker = newWorker(storage);

        // BƯỚC 1:
        // Cố tình làm database từ chối status DELETED.
        jdbc.execute("""
                ALTER TABLE media
                ADD CONSTRAINT block_deleted_state_test
                CHECK (status <> 'DELETED')
                """);

        // Worker gọi Cloudinary thành công,
        // nhưng transaction finalize trong DB thất bại.
        worker.run();

        // Job phải quay về PENDING để retry.
        assertEquals("PENDING", jobStatus(job.jobId()));
        assertEquals(1, attempts(job.jobId()));

        // Media chưa được finalize.
        String mediaStatus = jdbc.queryForObject(
                "SELECT status FROM media WHERE id = ?",
                String.class,
                job.mediaId());

        assertEquals("DELETING", mediaStatus);

        // completed_at phải được rollback.
        OffsetDateTime completedAt = jdbc.queryForObject(
                """
                        SELECT completed_at
                        FROM media_cleanup_jobs
                        WHERE id = ?
                        """,
                (rs, rowNum) -> rs.getObject(1, OffsetDateTime.class),
                job.jobId());

        assertNull(completedAt);

        String lastError = jdbc.queryForObject(
                """
                        SELECT last_error
                        FROM media_cleanup_jobs
                        WHERE id = ?
                        """,
                String.class,
                job.jobId());

        assertNotNull(lastError);

        // Cloudinary đã được gọi một lần.
        verify(uploader, times(1)).destroy(
                eq("integration/" + job.mediaId()),
                anyMap());

        // BƯỚC 2:
        // Khôi phục database.
        jdbc.execute("""
                ALTER TABLE media
                DROP CONSTRAINT block_deleted_state_test
                """);

        // Đưa job đến hạn retry ngay trong DB test.
        int updated = jdbc.update(
                """
                        UPDATE media_cleanup_jobs
                        SET next_retry_at =
                            CURRENT_TIMESTAMP - INTERVAL '1 second'
                        WHERE id = ?
                          AND status = 'PENDING'
                        """,
                job.jobId());

        assertEquals(1, updated);

        // BƯỚC 3:
        // Lần này Cloudinary trả "not found".
        // Worker vẫn phải hoàn thành.
        worker.run();

        assertEquals("COMPLETED", jobStatus(job.jobId()));
        assertEquals(2, attempts(job.jobId()));

        String finalMediaStatus = jdbc.queryForObject(
                "SELECT status FROM media WHERE id = ?",
                String.class,
                job.mediaId());

        assertEquals("DELETED", finalMediaStatus);

        String finalError = jdbc.queryForObject(
                "SELECT last_error FROM media_cleanup_jobs WHERE id = ?",
                String.class,
                job.jobId());

        assertNull(finalError);

        OffsetDateTime finalCompletedAt = jdbc.queryForObject(
                "SELECT completed_at FROM media_cleanup_jobs WHERE id = ?",
                (rs, rowNum) -> rs.getObject(1, OffsetDateTime.class),
                job.jobId());

        assertNotNull(finalCompletedAt);

        // Cloudinary được gọi hai lần,
        // nhưng không làm lỗi quá trình cleanup.
        verify(uploader, times(2)).destroy(
                eq("integration/" + job.mediaId()),
                anyMap());
    }
}
