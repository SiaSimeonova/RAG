package com.example.rag.api;

import java.time.Instant;
import java.util.List;

// Standard error envelope returned for all error responses.
// Shape: { "timestamp": "...", "status": 400, "error": "Bad Request", "messages": [...], "path": "..." }
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        List<String> messages,
        String path
) {
    public static ApiError of(int status, String error, List<String> messages, String path) {
        return new ApiError(Instant.now(), status, error, messages, path);
    }

    public static ApiError of(int status, String error, String message, String path) {
        return of(status, error, List.of(message), path);
    }
}
