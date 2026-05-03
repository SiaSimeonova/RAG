package com.example.rag.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskRequest(
        @NotBlank(message = "must not be blank")
        @Size(max = 1000, message = "must be 1000 characters or fewer")
        String question
) {}
