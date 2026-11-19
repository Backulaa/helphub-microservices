package com.helphub.media.media.access.service;

import com.cloudinary.Cloudinary;
import com.helphub.media.common.exception.MediaApiException;
import com.helphub.media.media.entity.Media;
import com.helphub.media.media.entity.MediaResourceType;
import com.helphub.media.media.entity.MediaVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CloudinaryTimedDownloadServiceTest {

    private Cloudinary cloudinary;
    private CloudinaryTimedDownloadService service;

    @BeforeEach
    void setUp() {
        cloudinary = mock(Cloudinary.class);
        service = new CloudinaryTimedDownloadService(cloudinary, 120);
    }

    private Media readyPrivateMedia(MediaResourceType resourceType) {
        boolean raw = resourceType == MediaResourceType.RAW;
        Media media = new Media(UUID.randomUUID(),
                raw ? "test/private-document.pdf" : "test/private-image",
                raw ? "document.pdf" : "photo.png",
                raw ? "application/pdf" : "image/png",
                resourceType, MediaVisibility.PRIVATE);
        ReflectionTestUtils.setField(media, "id", UUID.randomUUID());
        media.markUploading();
        media.markPrivateReady("asset-id", raw ? "pdf" : "png", 512);
        return media;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void signedImageUrlUsesAuthenticatedTypeAnd120SecondExpiry() throws Exception {
        Media media = readyPrivateMedia(MediaResourceType.IMAGE);
        when(cloudinary.privateDownload(eq(media.getPublicId()), eq("png"), anyMap()))
                .thenReturn("https://example.test/signed-image");

        Instant before = Instant.now();
        CloudinaryTimedDownloadService.SignedLink result = service.issue(media);
        Instant after = Instant.now();

        assertEquals("https://example.test/signed-image", result.url());
        assertEquals(120, result.expiresInSeconds());
        assertFalse(result.expiresAt().isBefore(before.plusSeconds(120)));
        assertFalse(result.expiresAt().isAfter(after.plusSeconds(120)));

        ArgumentCaptor<Map> optionsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(cloudinary).privateDownload(eq(media.getPublicId()), eq("png"),
                optionsCaptor.capture());
        Map options = optionsCaptor.getValue();
        assertEquals("image", options.get("resource_type"));
        assertEquals("authenticated", options.get("type"));
        assertEquals(false, options.get("attachment"));
        assertEquals(result.expiresAt().getEpochSecond(), options.get("expires_at"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void pdfUsesRawAuthenticatedType() throws Exception {
        Media pdf = readyPrivateMedia(MediaResourceType.RAW);
        when(cloudinary.privateDownload(eq(pdf.getPublicId()), eq("pdf"), anyMap()))
                .thenReturn("https://example.test/signed-document");

        assertTrue(service.issue(pdf).url().startsWith("https://"));
        ArgumentCaptor<Map> optionsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(cloudinary).privateDownload(eq(pdf.getPublicId()), eq("pdf"),
                optionsCaptor.capture());
        assertEquals("raw", optionsCaptor.getValue().get("resource_type"));
        assertEquals("authenticated", optionsCaptor.getValue().get("type"));
    }

    @Test
    void ttlOutside15To300SecondsIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new CloudinaryTimedDownloadService(cloudinary, 14));
        assertThrows(IllegalArgumentException.class,
                () -> new CloudinaryTimedDownloadService(cloudinary, 301));
    }

    @Test
    void missingAssetIdIsRejectedBeforeSigning() {
        Media media = readyPrivateMedia(MediaResourceType.IMAGE);
        ReflectionTestUtils.setField(media, "assetId", null);
        assertMediaException(409, "MEDIA_ASSET_INCOMPLETE", () -> service.issue(media));
        verifyNoInteractions(cloudinary);
    }

    @Test
    void missingFormatIsRejectedBeforeSigning() {
        Media media = readyPrivateMedia(MediaResourceType.IMAGE);
        ReflectionTestUtils.setField(media, "format", null);
        assertMediaException(409, "MEDIA_ASSET_INCOMPLETE", () -> service.issue(media));
        verifyNoInteractions(cloudinary);
    }

    @Test
    void insecureDownloadUrlIsRejected() throws Exception {
        Media media = readyPrivateMedia(MediaResourceType.IMAGE);
        when(cloudinary.privateDownload(anyString(), anyString(), anyMap()))
                .thenReturn("http://example.test/insecure");
        assertMediaException(502, "PRIVATE_ACCESS_GRANT_FAILED", () -> service.issue(media));
    }

    @Test
    void nullDownloadUrlIsRejected() throws Exception {
        Media media = readyPrivateMedia(MediaResourceType.IMAGE);
        when(cloudinary.privateDownload(anyString(), anyString(), anyMap()))
                .thenReturn(null);
        assertMediaException(502, "PRIVATE_ACCESS_GRANT_FAILED", () -> service.issue(media));
    }

    @Test
    void cloudinaryFailureReturnsSafeError() throws Exception {
        Media media = readyPrivateMedia(MediaResourceType.IMAGE);
        when(cloudinary.privateDownload(anyString(), anyString(), anyMap()))
                .thenThrow(new IllegalStateException("Simulated Cloudinary outage"));
        assertMediaException(502, "PRIVATE_ACCESS_GRANT_FAILED", () -> service.issue(media));
    }

    private void assertMediaException(int expectedStatus, String expectedCode,
                                      org.junit.jupiter.api.function.Executable action) {
        MediaApiException ex = assertThrows(MediaApiException.class, action);
        assertEquals(expectedStatus, ex.getStatus().value());
        assertEquals(expectedCode, ex.getCode());
    }
}
