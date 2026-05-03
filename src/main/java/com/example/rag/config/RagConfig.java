package com.example.rag.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// Marks this as a Spring configuration class and activates RagProperties binding.
// In PHP terms: this is like your service provider or config bootstrap file.
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {
    // Spring AI auto-configures the ChatClient, EmbeddingModel, and VectorStore
    // beans from application.yml — no manual bean definitions needed here.
}
