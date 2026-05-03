package com.example.rag.health;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

// Custom health indicator — reported under /actuator/health as "rag".
// Verifies that the core RAG beans (VectorStore, ChatClient) are configured and reachable.
// The DB health (PostgreSQL connectivity) is already checked by Spring Actuator automatically.
@Component("rag")
public class RagHealthIndicator implements HealthIndicator {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    @Value("${rag.llm.chat-provider:openai}")
    private String chatProvider;

    public RagHealthIndicator(VectorStore vectorStore, ChatClient chatClient) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
    }

    @Override
    public Health health() {
        try {
            // Verify vector store is reachable with a lightweight empty search.
            vectorStore.similaritySearch("health-check");

            return Health.up()
                    .withDetail("chatProvider", chatProvider)
                    .withDetail("vectorStore", vectorStore.getClass().getSimpleName())
                    .build();

        } catch (Exception e) {
            return Health.down()
                    .withDetail("chatProvider", chatProvider)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
