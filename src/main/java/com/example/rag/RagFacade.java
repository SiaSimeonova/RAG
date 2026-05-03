package com.example.rag;

import com.example.rag.generation.GenerationService;
import com.example.rag.ingestion.IngestionAsyncService;
import com.example.rag.ingestion.IngestionJob;
import com.example.rag.ingestion.IngestionJobRepository;
import com.example.rag.ingestion.IngestionService;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Optional;

// Public API of this module. Other modules in the monolith should only
// depend on this class — never on the internal services directly.
@Component
public class RagFacade {

    private final IngestionService ingestionService;
    private final IngestionAsyncService ingestionAsyncService;
    private final IngestionJobRepository jobRepository;
    private final GenerationService generationService;

    public RagFacade(IngestionService ingestionService,
                     IngestionAsyncService ingestionAsyncService,
                     IngestionJobRepository jobRepository,
                     GenerationService generationService) {
        this.ingestionService = ingestionService;
        this.ingestionAsyncService = ingestionAsyncService;
        this.jobRepository = jobRepository;
        this.generationService = generationService;
    }

    // --- Synchronous ingestion (for use by other modules that need to confirm indexing) ---

    public void ingestFile(InputStream inputStream, String source) {
        ingestionService.ingest(inputStream, source);
    }

    public void ingestText(String text, String source) {
        ingestionService.ingestText(text, source);
    }

    // --- Asynchronous ingestion (used by the HTTP controller — returns job ID immediately) ---

    public String ingestFileAsync(byte[] fileBytes, String source) {
        String jobId = jobRepository.create(source);
        ingestionAsyncService.ingestFileAsync(fileBytes, source, jobId);
        return jobId;
    }

    public String ingestTextAsync(String text, String source) {
        String jobId = jobRepository.create(source);
        ingestionAsyncService.ingestTextAsync(text, source, jobId);
        return jobId;
    }

    // --- Job status ---

    public Optional<IngestionJob> getIngestionJob(String jobId) {
        return jobRepository.findById(jobId);
    }

    // --- Query ---

    public String ask(String question) {
        return generationService.answer(question);
    }
}
