package com.example.rag.ingestion;

import com.example.rag.config.RagProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// Splits a large text into overlapping chunks.
// Overlap ensures a sentence split across a boundary is still retrievable.
@Component
public class DocumentChunker {

    private final int chunkSize;
    private final int chunkOverlap;

    public DocumentChunker(RagProperties properties) {
        this.chunkSize = properties.ingestion().chunkSize();
        this.chunkOverlap = properties.ingestion().chunkOverlap();
    }

    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start += chunkSize - chunkOverlap;
        }

        return chunks;
    }
}
