package com.helphub.media.media.service;

import com.helphub.media.common.exception.MediaApiException;
import com.helphub.media.media.cloudinary.CloudinarySigningService;
import com.helphub.media.media.dto.CreateUploadSessionRequest;
import com.helphub.media.media.dto.UploadSessionResponse;
import com.helphub.media.media.entity.*;
import com.helphub.media.media.repository.MediaRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MediaUploadSessionServiceTest {

    private static final long MAX_IMAGE = 10485760L;
    private static final long MAX_PDF = 20971520L;

    private MediaRepository repository;
    private CloudinarySigningService signingService;
    private MediaUploadSessionService service;

    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {

        repository = mock(MediaRepository.class);
        signingService = mock(CloudinarySigningService.class);

        service = new MediaUploadSessionService(
                repository,
                signingService,
                MAX_IMAGE,
                MAX_PDF);

        when(repository.save(any(Media.class)))
                .thenAnswer(invocation -> {

                    Media media = invocation.getArgument(0);

                    ReflectionTestUtils.setField(
                            media,
                            "id",
                            UUID.randomUUID());

                    return media;
                });
    }

    private CreateUploadSessionRequest request(
            String filename,
            String mimeType,
            long sizeBytes,
            MediaVisibility visibility) {

        return new CreateUploadSessionRequest(
                filename,
                mimeType,
                sizeBytes,
                visibility);
    }

    private void assertRejected(
            CreateUploadSessionRequest input,
            HttpStatus status,
            String code) {

        MediaApiException exception = assertThrows(
                MediaApiException.class,
                () -> service.createSession(ownerId, input));

        assertEquals(status, exception.getStatus());
        assertEquals(code, exception.getCode());

        verifyNoInteractions(repository, signingService);
    }

    @Test
    void executableFileMustBeRejected() {

        assertRejected(
                request(
                        "malware.exe",
                        "application/octet-stream",
                        1024,
                        MediaVisibility.PRIVATE),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_FILE_TYPE");
    }

    @Test
    void mismatchedExtensionAndMimeMustBeRejected() {

        assertRejected(
                request(
                        "photo.png",
                        "image/jpeg",
                        1024,
                        MediaVisibility.PRIVATE),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_FILE_TYPE");
    }

    @Test
    void pathTraversalMustBeRejected() {

        assertRejected(
                request(
                        "../photo.png",
                        "image/png",
                        1024,
                        MediaVisibility.PRIVATE),
                HttpStatus.BAD_REQUEST,
                "INVALID_FILENAME");
    }

    @Test
    void oversizedImageMustBeRejected() {

        assertRejected(
                request(
                        "photo.png",
                        "image/png",
                        MAX_IMAGE + 1,
                        MediaVisibility.PRIVATE),
                HttpStatus.CONTENT_TOO_LARGE,
                "FILE_TOO_LARGE");
    }

    @Test
    void oversizedPdfMustBeRejected() {

        assertRejected(
                request(
                        "document.pdf",
                        "application/pdf",
                        MAX_PDF + 1,
                        MediaVisibility.PRIVATE),
                HttpStatus.CONTENT_TOO_LARGE,
                "FILE_TOO_LARGE");
    }

    @Test
    void zeroSizeMustBeRejected() {

        assertRejected(
                request(
                        "photo.png",
                        "image/png",
                        0,
                        MediaVisibility.PRIVATE),
                HttpStatus.CONTENT_TOO_LARGE,
                "FILE_TOO_LARGE");
    }

    @Test
    void validPrivateUploadMustUseServer() {

        UploadSessionResponse response = service.createSession(
                ownerId,
                request(
                        "photo.png",
                        "image/png",
                        1024,
                        MediaVisibility.PRIVATE));

        assertNotNull(response.mediaId());
        assertEquals(MediaStatus.PENDING, response.status());
        assertEquals("SERVER", response.uploadMethod());

        assertNull(response.uploadUrl());
        assertNull(response.parameters());

        verify(repository).save(any(Media.class));
        verifyNoInteractions(signingService);
    }

    @Test
    void validPublicImageMustUseDirectUpload() {

        when(signingService.signUpload(
                anyString(),
                eq("image/png")))
                .thenReturn(Map.of(
                        "signature", "mock-signature"));

        when(signingService.uploadUrl(
                MediaResourceType.IMAGE))
                .thenReturn(
                        "https://example.test/image/upload");

        UploadSessionResponse response = service.createSession(
                ownerId,
                request(
                        "photo.png",
                        "image/png",
                        1024,
                        MediaVisibility.PUBLIC));

        assertEquals(MediaStatus.PENDING, response.status());
        assertEquals("DIRECT", response.uploadMethod());

        assertEquals(
                "https://example.test/image/upload",
                response.uploadUrl());

        assertEquals(
                "mock-signature",
                response.parameters().get("signature"));

        verify(signingService).signUpload(
                startsWith("helphub/media/"),
                eq("image/png"));

        verify(signingService).uploadUrl(
                MediaResourceType.IMAGE);
    }

    @Test
    void validPublicPdfMustUseRawResource() {

        when(signingService.signUpload(
                anyString(),
                eq("application/pdf")))
                .thenReturn(Map.of(
                        "signature", "mock-signature"));

        when(signingService.uploadUrl(
                MediaResourceType.RAW))
                .thenReturn(
                        "https://example.test/raw/upload");

        UploadSessionResponse response = service.createSession(
                ownerId,
                request(
                        "document.pdf",
                        "application/pdf",
                        2048,
                        MediaVisibility.PUBLIC));

        assertEquals("DIRECT", response.uploadMethod());

        ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);

        verify(repository).save(captor.capture());

        Media saved = captor.getValue();

        assertEquals(MediaResourceType.RAW,
                saved.getResourceType());

        assertTrue(saved.getPublicId().endsWith(".pdf"));

        verify(signingService).uploadUrl(
                MediaResourceType.RAW);
    }

    @Test
    void imageAtMaximumAllowedSizeMustPass() {

        UploadSessionResponse response = service.createSession(
                ownerId,
                request(
                        "max-image.png",
                        "image/png",
                        MAX_IMAGE,
                        MediaVisibility.PRIVATE));

        assertEquals(MediaStatus.PENDING, response.status());
        assertNotNull(response.mediaId());
    }

    @Test
    void filenameAndMimeShouldBeNormalized() {

        service.createSession(
                ownerId,
                request(
                        "  photo.PNG  ",
                        " IMAGE/PNG ",
                        1024,
                        MediaVisibility.PRIVATE));

        ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);

        verify(repository).save(captor.capture());

        Media saved = captor.getValue();

        assertEquals(
                "photo.PNG",
                saved.getOriginalFilename());

        assertEquals(
                "image/png",
                saved.getMimeType());
    }
}
