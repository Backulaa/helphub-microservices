package com.helphub.media.common.exception;

import com.helphub.media.common.dto.ApiResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(
            GlobalExceptionHandler.class);

    @ExceptionHandler(MediaApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleMedia(
            MediaApiException ex) {

        return ResponseEntity
                .status(ex.getStatus())
                .body(ApiResponse.error(
                        ex.getCode(),
                        ex.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            Exception ex) {

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(
                        "INVALID_REQUEST",
                        "Invalid request body"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(
            Exception ex) {

        log.error("Unexpected Media Service error", ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                        "INTERNAL_ERROR",
                        "An unexpected error occurred"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(
            MaxUploadSizeExceededException ex) {

        return ResponseEntity
                .status(HttpStatus.CONTENT_TOO_LARGE)
                .body(ApiResponse.error(
                        "FILE_TOO_LARGE",
                        "Uploaded file exceeds the server limit"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingFile(
            MissingServletRequestPartException ex) {

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(
                        "MISSING_FILE",
                        "Multipart request must contain a file"));
    }
}
