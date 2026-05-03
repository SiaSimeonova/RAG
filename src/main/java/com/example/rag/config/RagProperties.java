package com.example.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Binds the 'rag:' section of application.yml to this class.
// Think of it like reading a config array in PHP with $_ENV or a config service.
@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        Cors cors,
        Ingestion ingestion,
        Retrieval retrieval
) {
    public record Cors(java.util.List<String> allowedOrigins) {}
    public record Ingestion(int chunkSize, int chunkOverlap) {}
    public record Retrieval(int topK) {}
}
