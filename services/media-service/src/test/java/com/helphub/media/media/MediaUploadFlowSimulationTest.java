package com.helphub.media.media;

import com.helphub.media.media.cloudinary.*;
import com.helphub.media.media.dto.*;
import com.helphub.media.media.entity.*;
import com.helphub.media.media.repository.MediaRepository;
import com.helphub.media.media.service.*;
import com.helphub.media.outbox.entity.OutboxEvent;
import com.helphub.media.outbox.repository.OutboxEventRepository;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MediaUploadFlowSimulationTest {

    @Test
    void simulatePublicUploadAndComplete() {

        UUID ownerId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();

        // Mock all external dependencies.
        MediaRepository mediaRepo = mock(MediaRepository.class);

        OutboxEventRepository outboxRepo = mock(OutboxEventRepository.class);

        CloudinarySigningService signing = mock(CloudinarySigningService.class);

        CloudinaryAssetVerifier verifier = mock(CloudinaryAssetVerifier.class);

        AtomicReference<Media> stored = new AtomicReference<>();

        // Simulate saving Media to PostgreSQL.
        when(mediaRepo.save(any(Media.class)))
                .thenAnswer(invocation -> {
                    Media media = invocation.getArgument(0);
                    ReflectionTestUtils.setField(
                            media, "id", mediaId);
                    stored.set(media);
                    return media;
                });

        when(mediaRepo.findById(mediaId))
                .thenAnswer(invocation -> Optional.of(stored.get()));

        when(mediaRepo.findByIdForUpdate(mediaId))
                .thenAnswer(invocation -> Optional.of(stored.get()));

        when(signing.signUpload(
                anyString(), eq("image/png"))).thenReturn(Map.of(
                        "api_key", "fake-api-key",
                        "signature", "fake-signature"));

        when(signing.uploadUrl(
                MediaResourceType.IMAGE)).thenReturn(
                        "https://example.invalid/image/upload");

        // =====================================
        // TEST 1 - Upload Session
        // =====================================

        MediaUploadSessionService sessionService = new MediaUploadSessionService(
                mediaRepo,
                signing,
                10485760L,
                20971520L);

        UploadSessionResponse session = sessionService.createSession(
                ownerId,
                new CreateUploadSessionRequest(
                        "test.png",
                        "image/png",
                        83L,
                        MediaVisibility.PUBLIC));

        assertEquals(
                MediaStatus.PENDING, session.status());
        assertEquals(mediaId, session.mediaId());
        assertEquals("DIRECT", session.uploadMethod());

        // =====================================
        // TEST 2 - Fake Cloudinary Asset
        // =====================================

        CloudinaryVerifiedAsset fakeAsset = new CloudinaryVerifiedAsset(
                "fake-asset-id",
                stored.get().getPublicId(),
                "image",
                "upload",
                "png",
                83L,
                "https://example.invalid/test.png",
                12345L);

        when(verifier.verify(
                any(Media.class),
                any(CompleteUploadRequest.class))).thenReturn(fakeAsset);

        // =====================================
        // TEST 3 - Complete Upload
        // =====================================

        MediaCompleteTransactionService transaction = new MediaCompleteTransactionService(
                mediaRepo, outboxRepo);

        MediaCompleteUploadService completeService = new MediaCompleteUploadService(
                mediaRepo, verifier, transaction);

        CompleteUploadRequest request = new CompleteUploadRequest(
                "fake-asset-id",
                stored.get().getPublicId(),
                12345L);

        CompleteUploadResponse result = completeService.complete(
                mediaId, ownerId, request);

        assertEquals(
                MediaStatus.READY, result.status());

        assertEquals(
                "fake-asset-id",
                stored.get().getAssetId());

        verify(outboxRepo, times(1))
                .save(argThat(event -> "media.ready.v1".equals(
                        event.getEventType())));

        // Repeat to verify idempotency.
        completeService.complete(
                mediaId, ownerId, request);

        verify(outboxRepo, times(1))
                .save(any(OutboxEvent.class));
    }
}
