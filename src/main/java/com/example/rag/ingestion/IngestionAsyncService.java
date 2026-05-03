package com.example.rag.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

// Fire-and-forget wrapper around IngestionService.
// The controller queues a job, returns 202 immediately, and the actual work
// (parsing, embedding, storing) runs on a background thread.
// Job status is updated in the database on success or failure.
@Service
public class IngestionAsyncService {

    private static final Logger log = LoggerFactory.getLogger(IngestionAsyncService.class);

    private final IngestionService ingestionService;
    private final IngestionJobRepository jobRepository;

    public IngestionAsyncService(IngestionService ingestionService,
                                 IngestionJobRepository jobRepository) {
        this.ingestionService = ingestionService;
        this.jobRepository = jobRepository;
    }

    // File bytes are passed (not InputStream) because the HTTP request — and its
    // stream — is closed before the background thread runs.
    @Async("ingestionExecutor")
    public void ingestFileAsync(byte[] fileBytes, String source, String jobId) {
        log.info("Background ingestion started for file source '{}' (job {})", source, jobId);
        try {
            ingestionService.ingest(new ByteArrayInputStream(fileBytes), source);
            jobRepository.updateStatus(jobId, IngestionJobStatus.COMPLETED, null);
            log.info("Ingestion completed for job {}", jobId);
        } catch (Exception e) {
            log.error("Ingestion failed for job {}: {}", jobId, e.getMessage());
            jobRepository.updateStatus(jobId, IngestionJobStatus.FAILED, e.getMessage());
        }
    }

    @Async("ingestionExecutor")
    public void ingestTextAsync(String text, String source, String jobId) {
        log.info("Background ingestion started for text source '{}' (job {})", source, jobId);
        try {
            ingestionService.ingestText(text, source);
            jobRepository.updateStatus(jobId, IngestionJobStatus.COMPLETED, null);
            log.info("Ingestion completed for job {}", jobId);
        } catch (Exception e) {
            log.error("Ingestion failed for job {}: {}", jobId, e.getMessage());
            jobRepository.updateStatus(jobId, IngestionJobStatus.FAILED, e.getMessage());
        }
    }
}
