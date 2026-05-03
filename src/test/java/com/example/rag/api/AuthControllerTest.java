package com.example.rag.api;

import com.example.rag.api.auth.LoginRequest;
import com.example.rag.api.auth.LoginResponse;
import com.example.rag.config.TestBeansConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

// @AutoConfigureTestRestTemplate is required in Spring Boot 4.0 —
// TestRestTemplate is no longer auto-wired by @SpringBootTest alone.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Import(TestBeansConfig.class)
class AuthControllerTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void loginWithValidCredentialsReturnsToken() {
        ResponseEntity<LoginResponse> response = restTemplate.postForEntity(
                "/api/auth/login",
                new LoginRequest("admin", "admin"),
                LoginResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isNotBlank();
        assertThat(response.getBody().expiresInMs()).isPositive();
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorized() {
        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                "/api/auth/login",
                new LoginRequest("admin", "wrong-password"),
                ApiError.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loginWithBlankUsernameReturnsBadRequest() {
        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                "/api/auth/login",
                new LoginRequest("", "admin"),
                ApiError.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().messages()).anyMatch(m -> m.contains("username"));
    }

    @Test
    void loginWithUnknownUserReturnsUnauthorized() {
        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                "/api/auth/login",
                new LoginRequest("nobody", "password"),
                ApiError.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
