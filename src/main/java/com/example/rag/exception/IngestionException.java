package com.example.rag.exception;

// Thrown when document parsing, chunking, or storing into the vector store fails.
public class IngestionException extends RagException {

    public IngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
