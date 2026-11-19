package com.helphub.media.media.access.service;

import com.helphub.media.common.exception.MediaApiException;
import com.helphub.media.media.access.dto.PrivateAccessGrantRequest;
import com.helphub.media.media.access.dto.PrivateAccessGrantResponse;
import com.helphub.media.media.entity.Media;
import com.helphub.media.media.entity.MediaResourceType;
import com.helphub.media.media.entity.MediaStatus;
import com.helphub.media.media.entity.MediaVisibility;
import com.helphub.media.media.repository.MediaRepository;
import com.helphub.media.reference.repository.MediaReferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrivateAccessGrantServiceTest {

    private MediaRepository mediaRepository;
    private MediaReferenceRepository referenceRepository;
    private CloudinaryTimedDownloadService timedDownload;
    private PrivateAccessGrantService service;
    private Media media;
    private UUID mediaId;
    private UUID viewerId;
    private UUID resourceId;

    @BeforeEach
    void setUp() {
        mediaRepository = mock(MediaRepository.class);
        referenceRepository = mock(MediaReferenceRepository.class);
        timedDownload = mock(CloudinaryTimedDownloadService.class);
        service = new PrivateAccessGrantService(
                mediaRepository, referenceRepository, timedDownload);

        mediaId = UUID.randomUUID();
        viewerId = UUID.randomUUID();
        resourceId = UUID.randomUUID();
        media = new Media(UUID.randomUUID(), "test/private-asset", "photo.png",
                "image/png", MediaResourceType.IMAGE, MediaVisibility.PRIVATE);
        ReflectionTestUtils.setField(media, "id", mediaId);
        media.markUploading();
        media.markPrivateReady("cloudinary-asset-id", "png", 256L);

        // Return the media only for its real UUID; all other IDs stay not found.
        when(mediaRepository.findByIdForUpdate(any(UUID.class))).thenAnswer(invocation -> {
            UUID queriedId = invocation.getArgument(0);
            return mediaId.equals(queriedId) ? Optional.of(media) : Optional.empty();
        });
        when(referenceRepository.existsExactBinding(mediaId, "community", "POST", resourceId))
                .thenReturn(true);
        when(timedDownload.issue(media)).thenReturn(
                new CloudinaryTimedDownloadService.SignedLink(
                        "https://example.test/private-image", Instant.now().plusSeconds(120), 120));
    }

    private PrivateAccessGrantRequest request(String resourceType) {
        return new PrivateAccessGrantRequest(mediaId, viewerId, resourceType, resourceId);
    }

    private void assertValidFixture(PrivateAccessGrantRequest input) {
        assertEquals(mediaId, media.getId(), "Fixture Media.id does not match mediaId");
        assertEquals(mediaId, input.mediaId(), "Request.mediaId does not match mediaId");
        assertTrue(mediaRepository.findByIdForUpdate(input.mediaId()).isPresent(),
                "MediaRepository mock must return the READY PRIVATE media");
        assertEquals(MediaStatus.READY, media.getStatus());
        assertEquals(MediaVisibility.PRIVATE, media.getVisibility());
    }

    private void assertRejected(int status, String code, String serviceName,
            PrivateAccessGrantRequest request) {
        MediaApiException ex = assertThrows(MediaApiException.class,
                () -> service.issue(serviceName, request));
        assertEquals(status, ex.getStatus().value());
        assertEquals(code, ex.getCode());
        verifyNoInteractions(timedDownload);
    }

    @Test
    void validExactCommunityReferenceReturnsTimedGrant() {
        PrivateAccessGrantRequest input = request("POST");
        assertValidFixture(input);
        PrivateAccessGrantResponse result = service.issue("community", input);
        assertEquals(mediaId, result.mediaId());
        assertEquals("https://example.test/private-image", result.accessUrl());
        assertEquals(120, result.expiresInSeconds());
        assertNotNull(result.expiresAt());
        verify(referenceRepository).existsExactBinding(mediaId, "community", "POST", resourceId);
        verify(timedDownload).issue(media);
        verify(mediaRepository, never()).save(any(Media.class));
        assertNull(media.getSecureUrl());
    }

    @Test
    void missingExactReferenceIsNotFound() {
        when(referenceRepository.existsExactBinding(mediaId, "community", "POST", resourceId))
                .thenReturn(false);
        assertRejected(404, "MEDIA_NOT_FOUND", "community", request("POST"));
        verify(referenceRepository).existsExactBinding(mediaId, "community", "POST", resourceId);
    }

    @Test
    void wrongResourceIdCannotReuseReference() {
        UUID otherResource = UUID.randomUUID();
        assertRejected(404, "MEDIA_NOT_FOUND", "community",
                new PrivateAccessGrantRequest(mediaId, viewerId, "POST", otherResource));
        verify(referenceRepository).existsExactBinding(mediaId, "community", "POST", otherResource);
    }

    @Test
    void unknownMediaIsNotFound() {
        when(mediaRepository.findByIdForUpdate(mediaId)).thenReturn(Optional.empty());
        assertRejected(404, "MEDIA_NOT_FOUND", "community", request("POST"));
        verify(mediaRepository).findByIdForUpdate(mediaId);
    }

    @Test
    void pendingMediaCannotBeGranted() {
        ReflectionTestUtils.setField(media, "status", MediaStatus.PENDING);
        assertRejected(404, "MEDIA_NOT_FOUND", "community", request("POST"));
    }

    @Test
    void uploadingMediaCannotBeGranted() {
        ReflectionTestUtils.setField(media, "status", MediaStatus.UPLOADING);
        assertRejected(404, "MEDIA_NOT_FOUND", "community", request("POST"));
    }

    @Test
    void deletingMediaCannotBeGranted() {
        ReflectionTestUtils.setField(media, "status", MediaStatus.DELETING);
        assertRejected(404, "MEDIA_NOT_FOUND", "community", request("POST"));
    }

    @Test
    void deletedMediaCannotBeGranted() {
        ReflectionTestUtils.setField(media, "status", MediaStatus.DELETED);
        assertRejected(404, "MEDIA_NOT_FOUND", "community", request("POST"));
    }

    @Test
    void publicMediaCannotReceivePrivateGrant() {
        ReflectionTestUtils.setField(media, "visibility", MediaVisibility.PUBLIC);
        assertRejected(404, "MEDIA_NOT_FOUND", "community", request("POST"));
    }

    @Test
    void communityCannotGrantSupportRequest() {
        assertRejected(403, "INVALID_SERVICE_RESOURCE_SCOPE", "community",
                request("SUPPORT_REQUEST"));
        verifyNoInteractions(mediaRepository, referenceRepository);
    }

    @Test
    void supportCannotGrantPost() {
        assertRejected(403, "INVALID_SERVICE_RESOURCE_SCOPE", "support", request("POST"));
        verifyNoInteractions(mediaRepository, referenceRepository);
    }

    @Test
    void unknownServiceIsForbidden() {
        assertRejected(403, "INVALID_SERVICE_RESOURCE_SCOPE", "untrusted", request("POST"));
        verifyNoInteractions(mediaRepository, referenceRepository);
    }

    @Test
    void supportRequestWithExactReferenceCanBeGranted() {
        when(referenceRepository.existsExactBinding(mediaId, "support", "SUPPORT_REQUEST", resourceId))
                .thenReturn(true);
        PrivateAccessGrantRequest input = request("SUPPORT_REQUEST");
        assertValidFixture(input);
        PrivateAccessGrantResponse result = service.issue("support", input);
        assertEquals(mediaId, result.mediaId());
        verify(referenceRepository).existsExactBinding(mediaId, "support", "SUPPORT_REQUEST", resourceId);
        verify(timedDownload).issue(media);
    }
}
