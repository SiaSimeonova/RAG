package com.example.rag.exception;

// Thrown when the LLM call fails (network error, rate limit, invalid response, etc.).
public class GenerationException extends RagException {

    public GenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
