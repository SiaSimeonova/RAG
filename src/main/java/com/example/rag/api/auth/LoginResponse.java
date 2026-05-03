package com.example.rag.api.auth;

public record LoginResponse(String token, long expiresInMs) {}
