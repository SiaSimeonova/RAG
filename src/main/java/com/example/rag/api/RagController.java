package com.example.rag.api;

import com.example.rag.RagFacade;
import com.example.rag.exception.IngestionException;
import com.example.rag.exception.NotFoundException;
import com.example.rag.ingestion.IngestionJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Tag(name = "RAG", description = "Ingestion and question-answering endpoints")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagFacade rag;

    public RagController(RagFacade rag) {
        this.rag = rag;
    }

    @Operation(summary = "Ingest plain text",
               description = "Chunks, embeds, and stores text in the vector store. Processed asynchronously — returns 202 with a job ID.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Accepted — ingestion queued, check status via /ingest/status/{jobId}"),
        @ApiResponse(responseCode = "400", description = "Blank text or source"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/ingest/text")
    public ResponseEntity<IngestResponse> ingestText(@Valid @RequestBody IngestTextRequest request) {
        String jobId = rag.ingestTextAsync(request.text(), request.source());
        return ResponseEntity.accepted().body(new IngestResponse(jobId, request.source(), "QUEUED"));
    }

    @Operation(summary = "Ingest a file",
               description = "Parses (PDF, Word, HTML, etc.), chunks, embeds, and stores the file. Processed asynchronously — returns 202 with a job ID.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Accepted — ingestion queued, check status via /ingest/status/{jobId}"),
        @ApiResponse(responseCode = "400", description = "Missing file or source"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required"),
        @ApiResponse(responseCode = "413", description = "File too large")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/ingest/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestResponse> ingestFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("source") String source
    ) {
        // Read bytes synchronously before the HTTP request closes,
        // then hand off to the background thread.
        try {
            String jobId = rag.ingestFileAsync(file.getBytes(), source);
            return ResponseEntity.accepted().body(new IngestResponse(jobId, source, "QUEUED"));
        } catch (IOException e) {
            throw new IngestionException("Failed to read uploaded file '%s'.".formatted(source), e);
        }
    }

    @Operation(summary = "Get ingestion job status",
               description = "Returns the current status of an async ingestion job.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status returned"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required"),
        @ApiResponse(responseCode = "404", description = "Job not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/ingest/status/{jobId}")
    public ResponseEntity<IngestionStatusResponse> getIngestionStatus(@PathVariable String jobId) {
        IngestionJob job = rag.getIngestionJob(jobId)
                .orElseThrow(() -> new NotFoundException("Ingestion job not found: " + jobId));
        return ResponseEntity.ok(IngestionStatusResponse.from(job));
    }

    @Operation(summary = "Ask a question",
               description = "Retrieves relevant context from the vector store and generates an answer using the configured LLM.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Answer returned"),
        @ApiResponse(responseCode = "400", description = "Blank or too-long question"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
        @ApiResponse(responseCode = "502", description = "LLM service unavailable")
    })
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @PostMapping("/ask")
    public ResponseEntity<AskResponse> ask(@Valid @RequestBody AskRequest request) {
        String answer = rag.ask(request.question());
        return ResponseEntity.ok(new AskResponse(request.question(), answer));
    }
}
