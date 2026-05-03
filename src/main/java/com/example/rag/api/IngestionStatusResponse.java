package com.example.rag.api;

import com.example.rag.ingestion.IngestionJob;

import java.time.LocalDateTime;

public record IngestionStatusResponse(
        String jobId,
        String source,
        String status,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static IngestionStatusResponse from(IngestionJob job) {
        return new IngestionStatusResponse(
                job.id(),
                job.source(),
                job.status().name(),
                job.errorMessage(),
                job.createdAt(),
                job.updatedAt()
        );
    }
}
