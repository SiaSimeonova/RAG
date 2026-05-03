package com.example.rag.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.security.jwt")
public record SecurityProperties(String secret, long expirationMs) {}
