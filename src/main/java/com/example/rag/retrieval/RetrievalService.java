package com.example.rag.retrieval;

import com.example.rag.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final VectorStore vectorStore;
    private final int topK;

    public RetrievalService(VectorStore vectorStore, RagProperties properties) {
        this.vectorStore = vectorStore;
        this.topK = properties.retrieval().topK();
    }

    public List<Document> retrieve(String query) {
        log.debug("Retrieving top-{} chunks for query: '{}'", topK, query);
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build()
        );
        log.debug("Retrieved {} chunks", results.size());
        return results;
    }

    public List<Document> retrieveFromSource(String query, String source) {
        log.debug("Retrieving top-{} chunks for query '{}' filtered to source '{}'", topK, query, source);
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .filterExpression("source == '" + source.replace("'", "\\'") + "'")
                        .build()
        );
        log.debug("Retrieved {} chunks from source '{}'", results.size(), source);
        return results;
    }
}
