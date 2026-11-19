package com.helphub.media.media.service;

import com.helphub.media.common.exception.MediaApiException;
import com.helphub.media.media.cloudinary.CloudinaryPrivateStorage;
import com.helphub.media.media.entity.*;
import com.helphub.media.media.repository.MediaRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaPrivateUploadServiceTest {

    private MediaRepository repository;
    private CloudinaryPrivateStorage storage;
    private MediaPrivateUploadTransactionService transactions;
    private ObjectProvider<DevPrivateUploadFaultInjector> injector;
    private MediaPrivateUploadService service;

    private UUID ownerId;
    private UUID mediaId;
    private Media media;

    private static final byte[] PNG_HEADER = {
            (byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A
    };

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ownerId = UUID.randomUUID();
        mediaId = UUID.randomUUID();

        repository = mock(MediaRepository.class);
        storage = mock(CloudinaryPrivateStorage.class);
        transactions = mock(
                MediaPrivateUploadTransactionService.class);
        injector = mock(ObjectProvider.class);

        PrivateUploadFileValidator validator = new PrivateUploadFileValidator(
                10485760L,
                20971520L);

        service = new MediaPrivateUploadService(
                repository,
                validator,
                storage,
                transactions,
                injector);

        media = new Media(
                ownerId,
                "test/media",
                "photo.png",
                "image/png",
                MediaResourceType.IMAGE,
                MediaVisibility.PRIVATE);

        ReflectionTestUtils.setField(media, "id", mediaId);

        when(repository.findById(mediaId))
                .thenReturn(Optional.of(media));
    }

    private MockMultipartFile file(
            String name, String mime, byte[] bytes) {

        return new MockMultipartFile(
                "file", name, mime, bytes);
    }

    private void assertRejected(
            String expectedCode,
            UUID caller,
            MockMultipartFile upload) {

        MediaApiException error = assertThrows(
                MediaApiException.class,
                () -> service.upload(mediaId, caller, upload));

        assertEquals(expectedCode, error.getCode());

        // Rejected requests must not reach
        // Cloudinary or database transactions.
        verifyNoInteractions(storage, transactions);
    }

    @Test
    void fakePngMustNotReachCloudinary() {

        assertRejected(
                "INVALID_UPLOAD_FILE",
                ownerId,
                file("photo.png", "image/png",
                        "Fake image content".getBytes()));
    }

    @Test
    void mismatchedMimeMustNotReachCloudinary() {

        assertRejected(
                "INVALID_UPLOAD_FILE",
                ownerId,
                file("photo.png", "image/jpeg", PNG_HEADER));
    }

    @Test
    void mismatchedFilenameMustNotReachCloudinary() {

        assertRejected(
                "INVALID_UPLOAD_FILE",
                ownerId,
                file("different.png", "image/png", PNG_HEADER));
    }

    @Test
    void differentOwnerMustBeRejected() {

        assertRejected(
                "MEDIA_NOT_FOUND",
                UUID.randomUUID(),
                file("photo.png", "image/png", PNG_HEADER));
    }

    @Test
    void publicMediaCannotUsePrivateUpload() {

        Media publicMedia = new Media(
                ownerId,
                "test/public",
                "photo.png",
                "image/png",
                MediaResourceType.IMAGE,
                MediaVisibility.PUBLIC);

        ReflectionTestUtils.setField(
                publicMedia, "id", mediaId);

        when(repository.findById(mediaId))
                .thenReturn(Optional.of(publicMedia));

        assertRejected(
                "PRIVATE_UPLOAD_REQUIRED",
                ownerId,
                file("photo.png", "image/png", PNG_HEADER));
    }

    @Test
    void uploadingMediaCannotBeUploadedAgain() {

        media.markUploading();

        assertRejected(
                "INVALID_MEDIA_STATUS",
                ownerId,
                file("photo.png", "image/png", PNG_HEADER));
    }
}
