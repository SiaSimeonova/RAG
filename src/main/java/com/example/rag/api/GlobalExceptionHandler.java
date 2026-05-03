package com.example.rag.api;

import com.example.rag.exception.GenerationException;
import com.example.rag.exception.IngestionException;
import com.example.rag.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

// Centralized error handler — equivalent to a PHP framework's exception handler / middleware.
// Each @ExceptionHandler method maps a specific exception type to an HTTP response.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Validation errors from @Valid on request bodies (e.g. blank question, blank text).
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<String> messages = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "Bad Request", messages, request.getRequestURI()));
    }

    // Uploaded file is too large (configured via spring.servlet.multipart.max-file-size).
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleFileTooLarge(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {

        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiError.of(413, "Payload Too Large",
                        "Uploaded file exceeds the maximum allowed size.", request.getRequestURI()));
    }

    // Wrong username or password at login.
    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<ApiError> handleBadCredentials(
            AuthenticationException ex, HttpServletRequest request) {

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, "Unauthorized", "Invalid username or password.", request.getRequestURI()));
    }

    // Authenticated user does not have the required role (e.g. USER trying to ingest).
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(403, "Forbidden",
                        "You do not have permission to perform this action.", request.getRequestURI()));
    }

    // Requested resource (e.g. ingestion job) does not exist.
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            NotFoundException ex, HttpServletRequest request) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "Not Found", ex.getMessage(), request.getRequestURI()));
    }

    // Document parsing or vector store failure.
    @ExceptionHandler(IngestionException.class)
    public ResponseEntity<ApiError> handleIngestion(
            IngestionException ex, HttpServletRequest request) {

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiError.of(422, "Unprocessable Entity", ex.getMessage(), request.getRequestURI()));
    }

    // LLM call failure (network, rate limit, invalid response).
    @ExceptionHandler(GenerationException.class)
    public ResponseEntity<ApiError> handleGeneration(
            GenerationException ex, HttpServletRequest request) {

        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(ApiError.of(502, "Bad Gateway",
                        "LLM service error: " + ex.getMessage(), request.getRequestURI()));
    }

    // Catch-all for unexpected errors — never leak stack traces to the client.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(
            Exception ex, HttpServletRequest request) {

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "Internal Server Error",
                        "An unexpected error occurred.", request.getRequestURI()));
    }
}
