package com.example.rag.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Pure unit test — no Spring context, no DB, no network. Fast.
class JwtServiceTest {

    private static final String SECRET = "test-secret-key-for-testing-only-32chars";
    private static final long EXPIRATION_MS = 3_600_000L; // 1 hour

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new SecurityProperties(SECRET, EXPIRATION_MS));
    }

    @Test
    void generatedTokenIsValid() {
        String token = jwtService.generateToken("alice");
        assertThat(jwtService.isValid(token)).isTrue();
    }

    @Test
    void extractsCorrectUsername() {
        String token = jwtService.generateToken("alice");
        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    void expiredTokenIsInvalid() {
        // Create a service with 1ms expiration so the token expires immediately.
        JwtService shortLived = new JwtService(new SecurityProperties(SECRET, 1L));
        String token = shortLived.generateToken("alice");

        // Wait for expiry.
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        assertThat(shortLived.isValid(token)).isFalse();
    }

    @Test
    void tamperedTokenIsInvalid() {
        String token = jwtService.generateToken("alice");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtService.isValid(tampered)).isFalse();
    }

    @Test
    void emptyTokenIsInvalid() {
        assertThat(jwtService.isValid("")).isFalse();
        assertThat(jwtService.isValid("not.a.jwt")).isFalse();
    }
}
