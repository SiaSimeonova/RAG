# Authentication — JWT Bearer Token

## How it works

```
POST /api/auth/login  { username, password }
        │
        ▼
  Spring Security validates credentials
        │
        ▼
  JwtService signs a token (HMAC-SHA256)
        │
        ▼
  { "token": "<jwt>", "expiresInMs": 86400000 }

On every subsequent request:
  Authorization: Bearer <token>
        │
        ▼
  JwtAuthFilter validates signature + expiry
        │
        ▼
  Sets authenticated user in SecurityContext
        │
        ▼
  Controller runs normally
```

## Endpoints

| Endpoint | Auth required | Description |
|---|---|---|
| `POST /api/auth/login` | No | Returns a JWT token |
| `POST /api/rag/ingest/text` | Yes | Ingest plain text |
| `POST /api/rag/ingest/file` | Yes | Ingest a file |
| `POST /api/rag/ask` | Yes | Ask a question |

## Usage

### 1. Login

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin"}'

# Response:
# { "token": "eyJ...", "expiresInMs": 86400000 }
```

### 2. Use the token

```bash
curl -X POST http://localhost:8080/api/rag/ask \
  -H "Authorization: Bearer eyJ..." \
  -H "Content-Type: application/json" \
  -d '{"question": "How many days of leave do employees get?"}'
```

## Configuration

```yaml
rag:
  security:
    jwt:
      secret: ${JWT_SECRET:change-me-in-production-must-be-32-chars!!}
      expiration-ms: 86400000   # 24 hours
```

Generate a strong secret for production:
```bash
openssl rand -base64 32
```

## Default users (in-memory — for development only)

| Username | Password | Role |
|---|---|---|
| `admin` | `admin` | ADMIN |
| `user` | `user` | USER |

**Replace `SecurityConfig.userDetailsService()` with a real database-backed implementation before going to production.** In a modular monolith, this would delegate to your user module's service:

```java
@Bean
public UserDetailsService userDetailsService() {
    // Replace with: return username -> userModule.findByUsername(username);
}
```

## Error responses

| Situation | Status |
|---|---|
| Missing or no `Authorization` header | `401 Unauthorized` |
| Wrong username or password | `401 Unauthorized` |
| Expired or tampered token | `401 Unauthorized` (token rejected by filter, request blocked) |

## File structure

```
security/
├── SecurityConfig.java      ← Spring Security setup, user store, filter chain
├── JwtService.java          ← Token creation and validation (HMAC-SHA256)
├── JwtAuthFilter.java       ← Reads Bearer header, validates token per request
└── SecurityProperties.java  ← Typed config: secret + expiration

api/auth/
├── AuthController.java      ← POST /api/auth/login
├── LoginRequest.java        ← { username, password }
└── LoginResponse.java       ← { token, expiresInMs }
```
