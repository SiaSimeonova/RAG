package com.example.rag.ingestion;

import com.example.rag.config.RagProperties;
import com.example.rag.exception.IngestionException;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final int VECTOR_STORE_BATCH_SIZE = 50;

    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter;
    private final Tika tika;

    public IngestionService(VectorStore vectorStore, RagProperties properties) {
        this.vectorStore = vectorStore;
        this.splitter = new TokenTextSplitter(
                properties.ingestion().chunkSize(), // target chunk size in tokens
                350,   // min chunk size in chars before a split is allowed
                5,     // min chunk length to embed (skip near-empty fragments)
                10000, // max chunks per document
                true   // keep sentence-boundary separators
        );
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
            Document doc = new Document(text, Map.of("source", source));
            List<Document> chunks = splitter.apply(List.of(doc));
            log.debug("Split into {} chunks for source '{}'", chunks.size(), source);

            // Add in batches to avoid oversized embedding API requests on large documents.
            for (int i = 0; i < chunks.size(); i += VECTOR_STORE_BATCH_SIZE) {
                List<Document> batch = chunks.subList(i, Math.min(i + VECTOR_STORE_BATCH_SIZE, chunks.size()));
                vectorStore.add(batch);
            }

            log.info("Stored {} chunks for source '{}'", chunks.size(), source);
        } catch (Exception e) {
            log.error("Failed to embed/store text from source '{}': {}", source, e.getMessage());
            throw new IngestionException("Failed to embed and store text from source '%s'.".formatted(source), e);
        }
    }
}
