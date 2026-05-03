package com.example.rag.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IngestTextRequest(
        @NotBlank(message = "must not be blank")
        @Size(min = 10, message = "must be at least 10 characters")
        String text,

        @NotBlank(message = "must not be blank")
        String source
) {}
