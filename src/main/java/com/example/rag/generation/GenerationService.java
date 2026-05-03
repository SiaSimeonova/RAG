package com.example.rag.generation;

import com.example.rag.exception.GenerationException;
import com.example.rag.retrieval.RetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

    private final ChatClient chatClient;
    private final RetrievalService retrievalService;

    public GenerationService(ChatClient chatClient, RetrievalService retrievalService) {
        this.chatClient = chatClient;
        this.retrievalService = retrievalService;
    }

    public String answer(String userQuestion) {
        log.info("Processing question: '{}'", userQuestion);
        try {
            List<Document> context = retrievalService.retrieve(userQuestion);
            log.debug("Using {} context chunks for generation", context.size());

            String contextText = formatContext(context);
            String answer = chatClient.prompt()
                    .system("""
                            You are a helpful assistant. Answer the user's question using ONLY
                            the provided context. If the answer is not in the context, say so.
                            """)
                    .user("""
                            Context:
                            %s

                            Question: %s
                            """.formatted(contextText, userQuestion))
                    .call()
                    .content();

            log.info("Answer generated ({} chars) for question: '{}'", answer.length(), userQuestion);
            return answer;
        } catch (Exception e) {
            log.error("LLM call failed for question '{}': {}", userQuestion, e.getMessage());
            throw new GenerationException("LLM call failed for question: '%s'.".formatted(userQuestion), e);
        }
    }

    private String formatContext(List<Document> documents) {
        return documents.stream()
                .map(doc -> "- " + doc.getText())
                .collect(Collectors.joining("\n"));
    }
}
