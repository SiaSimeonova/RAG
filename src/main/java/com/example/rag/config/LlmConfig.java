package com.example.rag.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

// Selects the active ChatModel and EmbeddingModel based on 'rag.llm.chat-provider'
// and exposes them as @Primary beans so PgVectorStore and other consumers get exactly one.
// Excluded in the 'test' profile — TestBeansConfig provides mock beans instead.
@Configuration
@Profile("!test")
public class LlmConfig {

    @Value("${rag.llm.chat-provider:openai}")
    private String chatProvider;

    // Each model is optional — only the ones whose providers are configured will be present.
    @Autowired(required = false) private OpenAiChatModel openAiChatModel;
    @Autowired(required = false) private AnthropicChatModel anthropicChatModel;
    @Autowired(required = false) private OllamaChatModel ollamaChatModel;

    @Autowired(required = false) private OpenAiEmbeddingModel openAiEmbeddingModel;
    @Autowired(required = false) private OllamaEmbeddingModel ollamaEmbeddingModel;

    @Bean
    @Primary
    public ChatModel primaryChatModel() {
        return switch (chatProvider) {
            case "claude" -> requireConfigured(anthropicChatModel,
                    "Claude selected but ANTHROPIC_API_KEY is not set.");
            case "qwen3"  -> requireConfigured(ollamaChatModel,
                    "Qwen3 selected but Ollama is not running or not configured.");
            default       -> requireConfigured(openAiChatModel,
                    "OpenAI selected but OPENAI_API_KEY is not set.");
        };
    }

    @Bean
    @Primary
    public EmbeddingModel primaryEmbeddingModel() {
        return switch (chatProvider) {
            case "qwen3"  -> requireConfigured(ollamaEmbeddingModel,
                    "Qwen3 selected but Ollama embedding model is not configured.");
            default       -> requireConfigured(openAiEmbeddingModel,
                    "OpenAI embedding selected but OPENAI_API_KEY is not set.");
        };
    }

    @Bean
    public ChatClient chatClient(ChatModel primaryChatModel) {
        return ChatClient.builder(primaryChatModel).build();
    }

    private <T> T requireConfigured(T model, String errorMessage) {
        if (model == null) {
            throw new IllegalStateException(errorMessage);
        }
        return model;
    }
}
