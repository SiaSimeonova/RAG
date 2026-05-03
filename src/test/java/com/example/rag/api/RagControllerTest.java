package com.example.rag.api;

import com.example.rag.RagFacade;
import com.example.rag.api.auth.LoginRequest;
import com.example.rag.api.auth.LoginResponse;
import com.example.rag.config.TestBeansConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Import(TestBeansConfig.class)
class RagControllerTest {

    @Autowired TestRestTemplate restTemplate;

    @MockitoBean RagFacade ragFacade;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void obtainTokens() {
        adminToken = login("admin", "admin");
        userToken  = login("user", "user");
    }

    // --- /api/rag/ask ---

    @Test
    void askWithoutTokenReturnsUnauthorized() {
        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                "/api/rag/ask",
                new AskRequest("What is the leave policy?"),
                ApiError.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void askWithUserTokenReturnsAnswer() {
        when(ragFacade.ask(anyString())).thenReturn("You get 25 days of paid leave.");

        ResponseEntity<AskResponse> response = postWithToken(
                "/api/rag/ask",
                new AskRequest("How many days of leave?"),
                AskResponse.class,
                userToken
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().answer()).isEqualTo("You get 25 days of paid leave.");
    }

    @Test
    void askWithAdminTokenReturnsAnswer() {
        when(ragFacade.ask(anyString())).thenReturn("Some answer.");

        ResponseEntity<AskResponse> response = postWithToken(
                "/api/rag/ask",
                new AskRequest("Any question?"),
                AskResponse.class,
                adminToken
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void askWithBlankQuestionReturnsBadRequest() {
        ResponseEntity<ApiError> response = postWithToken(
                "/api/rag/ask",
                new AskRequest(""),
                ApiError.class,
                userToken
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().messages()).anyMatch(m -> m.contains("question"));
    }

    // --- /api/rag/ingest/text ---

    @Test
    void ingestTextWithUserTokenReturnsForbidden() {
        ResponseEntity<ApiError> response = postWithToken(
                "/api/rag/ingest/text",
                new IngestTextRequest("Some document content here.", "doc"),
                ApiError.class,
                userToken
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void ingestTextWithAdminTokenReturnsAccepted() {
        when(ragFacade.ingestTextAsync(anyString(), anyString())).thenReturn("test-job-id");

        ResponseEntity<IngestResponse> response = postWithToken(
                "/api/rag/ingest/text",
                new IngestTextRequest("Some document content here.", "doc"),
                IngestResponse.class,
                adminToken
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().jobId()).isEqualTo("test-job-id");
        assertThat(response.getBody().status()).isEqualTo("QUEUED");
    }

    @Test
    void ingestTextWithBlankSourceReturnsBadRequest() {
        ResponseEntity<ApiError> response = postWithToken(
                "/api/rag/ingest/text",
                new IngestTextRequest("Some document content here.", ""),
                ApiError.class,
                adminToken
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().messages()).anyMatch(m -> m.contains("source"));
    }

    @Test
    void ingestTextWithoutTokenReturnsUnauthorized() {
        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                "/api/rag/ingest/text",
                new IngestTextRequest("Some content.", "doc"),
                ApiError.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- Helpers ---

    private String login(String username, String password) {
        ResponseEntity<LoginResponse> response = restTemplate.postForEntity(
                "/api/auth/login",
                new LoginRequest(username, password),
                LoginResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().token();
    }

    private <T> ResponseEntity<T> postWithToken(String url, Object body, Class<T> responseType, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), responseType);
    }
}
