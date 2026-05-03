package com.example.rag.config;

import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Replaces AI infrastructure beans in the 'test' profile.
// This allows tests to run without real OpenAI API keys or a PostgreSQL database.
// LlmConfig is excluded by @Profile("!test"), so ChatClient must be provided here.
@TestConfiguration
public class TestBeansConfig {

    @Bean
    @Primary
    public ChatClient chatClient() {
        return Mockito.mock(ChatClient.class);
    }

    @Bean
    @Primary
    public VectorStore vectorStore() {
        return Mockito.mock(VectorStore.class);
    }
}
