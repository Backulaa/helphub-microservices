package com.helphub.media.media.service;

import com.helphub.media.common.exception.MediaApiException;
import com.helphub.media.media.entity.Media;
import com.helphub.media.media.entity.MediaResourceType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

@Component
public class PrivateUploadFileValidator {

    private final long maxImageSize;
    private final long maxPdfSize;

    public PrivateUploadFileValidator(
            @Value("${app.media.upload.max-image-size}") long maxImageSize,
            @Value("${app.media.upload.max-pdf-size}") long maxPdfSize) {

        this.maxImageSize = maxImageSize;
        this.maxPdfSize = maxPdfSize;
    }

    public byte[] validate(Media media, MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw invalid("File is required");
        }

        if (!media.getOriginalFilename().equals(
                file.getOriginalFilename())) {
            throw invalid(
                    "Filename does not match upload session");
        }

        String contentType = file.getContentType();

        if (contentType == null ||
                !media.getMimeType().equalsIgnoreCase(
                        contentType.trim())) {
            throw invalid(
                    "MIME type does not match upload session");
        }

        long maxSize = media.getResourceType() == MediaResourceType.IMAGE
                ? maxImageSize
                : maxPdfSize;

        if (file.getSize() <= 0 ||
                file.getSize() > maxSize) {
            throw new MediaApiException(
                    HttpStatus.CONTENT_TOO_LARGE,
                    "FILE_TOO_LARGE",
                    "File exceeds the allowed size");
        }

        try {
            byte[] bytes = file.getBytes();

            if (bytes.length == 0 ||
                    bytes.length > maxSize) {
                throw invalid("Invalid file size");
            }

            validateSignature(
                    bytes,
                    media.getMimeType().toLowerCase(
                            Locale.ROOT));

            return bytes;

        } catch (IOException ex) {
            throw new MediaApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "FILE_READ_FAILED",
                    "Could not read uploaded file");
        }
    }

    private void validateSignature(
            byte[] bytes,
            String mimeType) {

        boolean valid = switch (mimeType) {
            case "image/jpeg" -> isJpeg(bytes);
            case "image/png" -> isPng(bytes);
            case "image/webp" -> isWebp(bytes);
            case "application/pdf" -> isPdf(bytes);
            default -> false;
        };

        if (!valid) {
            throw invalid(
                    "File content does not match MIME type");
        }
    }

    private boolean isJpeg(byte[] b) {
        return b.length >= 3
                && (b[0] & 0xFF) == 0xFF
                && (b[1] & 0xFF) == 0xD8
                && (b[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] b) {
        byte[] signature = {
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
        };

        return b.length >= signature.length
                && Arrays.equals(
                        Arrays.copyOf(b, signature.length),
                        signature);
    }

    private boolean isWebp(byte[] b) {
        return b.length >= 12
                && ascii(b, 0, 4).equals("RIFF")
                && ascii(b, 8, 4).equals("WEBP");
    }

    private boolean isPdf(byte[] b) {
        return b.length >= 5
                && ascii(b, 0, 5).equals("%PDF-");
    }

    private String ascii(
            byte[] b,
            int offset,
            int length) {

        return new String(
                b,
                offset,
                length,
                StandardCharsets.US_ASCII);
    }

    private MediaApiException invalid(String message) {
        return new MediaApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_UPLOAD_FILE",
                message);
    }
}
