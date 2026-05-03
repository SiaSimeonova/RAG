package com.example.rag.exception;

// Base exception for all RAG module errors.
// Extend this for specific failure categories so the handler can map them to HTTP status codes.
public class RagException extends RuntimeException {

    public RagException(String message) {
        super(message);
    }

    public RagException(String message, Throwable cause) {
        super(message, cause);
    }
}
