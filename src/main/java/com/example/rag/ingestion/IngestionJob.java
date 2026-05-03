package com.example.rag.ingestion;

import java.time.LocalDateTime;

public record IngestionJob(
        String id,
        String source,
        IngestionJobStatus status,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
