package com.example.rag;

import com.example.rag.config.TestBeansConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

// Smoke test: verifies the Spring context loads and RagFacade is wired correctly.
// Real AI calls are replaced by the mocks in TestBeansConfig.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestBeansConfig.class)
class RagFacadeTest {

    @Autowired
    RagFacade rag;

    @Test
    void contextLoads() {
        assertThat(rag).isNotNull();
    }

    @Test
    void ingestTextDoesNotThrow() {
        // VectorStore is mocked — no real embedding or DB call.
        rag.ingestText("Some document content for testing purposes.", "test-source");
    }
}
