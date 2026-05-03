package com.example.rag.ingestion;

import com.example.rag.exception.IngestionException;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final VectorStore vectorStore;
    private final DocumentChunker chunker;
    private final Tika tika;

    public IngestionService(VectorStore vectorStore, DocumentChunker chunker) {
        this.vectorStore = vectorStore;
        this.chunker = chunker;
        this.tika = new Tika();
    }

    public void ingest(InputStream inputStream, String source) {
        log.info("Parsing document from source '{}'", source);
        try {
            String rawText = tika.parseToString(inputStream);
            log.debug("Parsed {} characters from source '{}'", rawText.length(), source);
            ingestText(rawText, source);
        } catch (Exception e) {
            log.error("Failed to parse document from source '{}': {}", source, e.getMessage());
            throw new IngestionException("Failed to parse document from source '%s'.".formatted(source), e);
        }
    }

    public void ingestText(String text, String source) {
        log.info("Ingesting text from source '{}' ({} chars)", source, text.length());
        try {
            List<String> chunks = chunker.chunk(text);
            log.debug("Split into {} chunks for source '{}'", chunks.size(), source);

            List<Document> documents = chunks.stream()
                    .map(chunk -> new Document(chunk, Map.of("source", source)))
                    .toList();
            vectorStore.add(documents);

            log.info("Stored {} chunks for source '{}'", documents.size(), source);
        } catch (Exception e) {
            log.error("Failed to embed/store text from source '{}': {}", source, e.getMessage());
            throw new IngestionException("Failed to embed and store text from source '%s'.".formatted(source), e);
        }
    }
}
