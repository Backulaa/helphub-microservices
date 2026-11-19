package com.helphub.media.media.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CloudinaryAssetDeletionServiceTest {

    private Cloudinary cloudinary;
    private Uploader uploader;
    private CloudinaryAssetDeletionService service;

    @BeforeEach
    void setUp() {
        cloudinary = mock(Cloudinary.class);
        uploader = mock(Uploader.class);

        when(cloudinary.uploader()).thenReturn(uploader);

        service = new CloudinaryAssetDeletionService(
                cloudinary);
    }

    @Test
    void deleteSuccess() throws IOException {
        when(uploader.destroy(anyString(), anyMap()))
                .thenReturn(Map.of("result", "ok"));

        assertDoesNotThrow(() -> service.delete(
                "test/media",
                "IMAGE",
                "PRIVATE"));

        verify(uploader).destroy(
                eq("test/media"),
                argThat(options -> "image".equals(options.get("resource_type"))
                        && "authenticated".equals(
                                options.get("type"))));
    }

    @Test
    void deleteAlreadyMissingAsset() throws IOException {
        when(uploader.destroy(anyString(), anyMap()))
                .thenReturn(Map.of("result", "not found"));

        assertDoesNotThrow(() -> service.delete(
                "test/missing",
                "IMAGE",
                "PRIVATE"));
    }

    @Test
    void deleteUnexpectedResponse() throws IOException {
        when(uploader.destroy(anyString(), anyMap()))
                .thenReturn(Map.of("result", "error"));

        assertThrows(
                IllegalStateException.class,
                () -> service.delete(
                        "test/error",
                        "IMAGE",
                        "PRIVATE"));
    }

    @Test
    void deleteNetworkFailure() throws IOException {
        when(uploader.destroy(anyString(), anyMap()))
                .thenThrow(new IOException("Simulated timeout"));

        assertThrows(
                IOException.class,
                () -> service.delete(
                        "test/timeout",
                        "IMAGE",
                        "PRIVATE"));
    }
}
