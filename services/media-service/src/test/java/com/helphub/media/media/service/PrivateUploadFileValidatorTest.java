package com.helphub.media.media.service;

import com.helphub.media.common.exception.MediaApiException;
import com.helphub.media.media.entity.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PrivateUploadFileValidatorTest {

    private PrivateUploadFileValidator validator;
    private Media media;

    private static final byte[] PNG_HEADER = {
            (byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A
    };

    @BeforeEach
    void setUp() {
        validator = new PrivateUploadFileValidator(
                10485760L,
                20971520L);

        media = new Media(
                UUID.randomUUID(),
                "test/media",
                "test.png",
                "image/png",
                MediaResourceType.IMAGE,
                MediaVisibility.PRIVATE);
    }

    private MockMultipartFile file(
            String filename,
            String contentType,
            byte[] content) {

        return new MockMultipartFile(
                "file",
                filename,
                contentType,
                content);
    }

    @Test
    void validPngSignatureShouldPass() {
        byte[] result = validator.validate(
                media,
                file("test.png", "image/png", PNG_HEADER));

        assertArrayEquals(PNG_HEADER, result);
    }

    @Test
    void validPdfSignatureShouldPass() {
        Media pdf = new Media(
                UUID.randomUUID(),
                "test/document.pdf",
                "document.pdf",
                "application/pdf",
                MediaResourceType.RAW,
                MediaVisibility.PRIVATE);

        byte[] content = "%PDF-1.7\nTest"
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        byte[] result = validator.validate(
                pdf,
                file(
                        "document.pdf",
                        "application/pdf",
                        content));

        assertArrayEquals(content, result);
    }

    @Test
    void nullFileShouldBeRejected() {
        MediaApiException ex = assertThrows(
                MediaApiException.class,
                () -> validator.validate(media, null));

        assertEquals("INVALID_UPLOAD_FILE", ex.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void emptyFileShouldBeRejected() {
        MediaApiException ex = assertThrows(
                MediaApiException.class,
                () -> validator.validate(
                        media,
                        file("test.png", "image/png", new byte[0])));

        assertEquals("INVALID_UPLOAD_FILE", ex.getCode());
    }

    @Test
    void wrongFilenameShouldBeRejected() {
        MediaApiException ex = assertThrows(
                MediaApiException.class,
                () -> validator.validate(
                        media,
                        file("another.png", "image/png", PNG_HEADER)));

        assertEquals("INVALID_UPLOAD_FILE", ex.getCode());
    }

    @Test
    void mismatchedMimeTypeShouldBeRejected() {
        MediaApiException ex = assertThrows(
                MediaApiException.class,
                () -> validator.validate(
                        media,
                        file("test.png", "image/jpeg", PNG_HEADER)));

        assertEquals("INVALID_UPLOAD_FILE", ex.getCode());
    }

    @Test
    void fakePngContentShouldBeRejected() {
        byte[] fakeContent = "This is not a PNG file".getBytes();

        MediaApiException ex = assertThrows(
                MediaApiException.class,
                () -> validator.validate(
                        media,
                        file("test.png", "image/png", fakeContent)));

        assertEquals("INVALID_UPLOAD_FILE", ex.getCode());
    }

    @Test
    void oversizedImageShouldBeRejected() {
        // Small limit used to simulate oversized input.
        PrivateUploadFileValidator smallValidator = new PrivateUploadFileValidator(8L, 20L);

        byte[] oversized = {
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A, 0x00
        };

        MediaApiException ex = assertThrows(
                MediaApiException.class,
                () -> smallValidator.validate(
                        media,
                        file("test.png", "image/png", oversized)));

        assertEquals("FILE_TOO_LARGE", ex.getCode());
        assertEquals(
                HttpStatus.CONTENT_TOO_LARGE,
                ex.getStatus());
    }
}
