# RAG Module — Claude Code Guide

## Project overview

A self-contained Spring Boot module that adds Retrieval-Augmented Generation (RAG) to a modular monolith.
Other modules interact with it exclusively through `RagFacade` — they must never inject internal services directly.

## Key technology versions

| Technology | Version |
|---|---|
| Java | 21 |
| Spring Boot | 4.0.6 |
| Spring AI | 2.0.0-M4 (GA scheduled 2026-05-28) |
| JJWT | 0.13.0 |
| Apache Tika | 3.3.0 |
| springdoc-openapi | 3.0.3 |

Spring AI 2.0.0-M4 is a milestone release pulled from the Spring Milestones repository (`repo.spring.io/milestone`).
Once Spring AI 2.0.0 GA is released, bump `spring-ai.version` in `pom.xml` and remove the `<repositories>` block.

## How to run tests

Java and Maven must be installed first (they are not available by default on this machine):

```bash
# Install via SDKMAN (no sudo required)
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.7-tem
sdk install maven

# Run tests
mvn test -Dspring.profiles.active=test
```

Tests use H2 in-memory DB and Mockito mock beans — no real LLM or PostgreSQL is needed.

## How to run the application

**Important:** use `-Dspring-boot.run.profiles=` (not `-Dspring.profiles.active=`) with `spring-boot:run`.

```bash
# OpenAI (default)
OPENAI_API_KEY=sk-... mvn spring-boot:run -Dspring-boot.run.profiles=openai

# Anthropic Claude
ANTHROPIC_API_KEY=sk-ant-... mvn spring-boot:run -Dspring-boot.run.profiles=claude

# Qwen3 via Ollama (local, no API key)
mvn spring-boot:run -Dspring-boot.run.profiles=qwen3
```

PostgreSQL with pgvector must be running at `localhost:5432/ragdb` (default credentials: `postgres/postgres`).

Start it with:
```bash
docker run -d --name pgvector \
  -e POSTGRES_DB=ragdb \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  pgvector/pgvector:pg16
```

### Ollama / qwen3 notes

Ollama must be running before starting the app with the `qwen3` profile:
```bash
ollama serve &
ollama pull nomic-embed-text
ollama pull qwen3:0.6b   # use 0.6b on machines with < 2 GB free RAM
```

Update `application-qwen3.yml` to match whichever model variant you pull (`qwen3:0.6b`, `qwen3:1.7b`, etc.).

## Module structure

```
src/main/java/com/example/rag/
├── RagApplication.java          # entry point
├── RagFacade.java               # public API — only class other modules should inject
├── api/
│   ├── RagController.java       # POST /api/rag/ask, /ingest/text, /ingest/file, GET /ingest/status/{jobId}
│   ├── AskRequest.java / AskResponse.java
│   ├── IngestTextRequest.java
│   ├── IngestResponse.java      # { jobId, source, status } — returned by ingest endpoints
│   ├── IngestionStatusResponse.java  # { jobId, source, status, createdAt, updatedAt }
│   ├── ApiError.java
│   ├── auth/AuthController.java # POST /api/auth/login
│   └── GlobalExceptionHandler.java
├── config/
│   ├── LlmConfig.java           # @Profile("!test") — selects ChatModel + EmbeddingModel by provider
│   ├── AsyncConfig.java         # ingestionExecutor thread pool
│   └── RagProperties.java       # @ConfigurationProperties(prefix="rag")
├── ingestion/
│   ├── IngestionService.java    # sync: Tika parse → chunk → VectorStore
│   ├── IngestionAsyncService.java # @Async("ingestionExecutor") wrappers; updates job status
│   ├── IngestionJob.java        # record: jobId, source, status, createdAt, updatedAt
│   ├── IngestionJobStatus.java  # enum: QUEUED, COMPLETED, FAILED
│   └── IngestionJobRepository.java  # JDBC repo for ingestion_jobs table
├── retrieval/RetrievalService.java
├── generation/GenerationService.java
├── health/RagHealthIndicator.java
├── exception/
│   ├── IngestionException.java
│   ├── GenerationException.java
│   └── NotFoundException.java
└── security/
    ├── SecurityConfig.java      # filter chain, CORS, BCrypt, @EnableMethodSecurity
    ├── SecurityBeansConfig.java # JdbcUserDetailsManager + PasswordEncoder (separate to avoid circular dep)
    ├── SecurityUserInitializer.java  # seeds admin/user on ApplicationReadyEvent
    ├── JwtService.java
    ├── JwtAuthFilter.java
    └── SecurityProperties.java

src/main/resources/
├── application.yml
├── application-openai.yml
├── application-claude.yml
├── application-qwen3.yml
└── schema.sql                   # users, authorities, ingestion_jobs tables (IF NOT EXISTS)
```

## Configuration

`application.yml` is the base. LLM-specific settings live in profile overlays:

| Profile | Embedding dims | File |
|---|---|---|
| `openai` | 1536 | `application-openai.yml` |
| `claude` | 1536 | `application-claude.yml` |
| `qwen3` | 768 | `application-qwen3.yml` |

Switching between `openai`/`claude` and `qwen3` changes the embedding dimension. If you switch between these two groups you must drop and recreate the `vector_store` table so pgvector recreates it with the correct dimensions.

Key config knobs in `application.yml`:

```yaml
rag:
  cors:
    allowed-origins:             # YAML list
      - "http://localhost:3000"
  llm:
    chat-provider: openai        # openai | claude | qwen3
  ingestion:
    chunk-size: 500
    chunk-overlap: 100
  retrieval:
    top-k: 5
  security:
    jwt:
      secret: ${JWT_SECRET:change-me-in-production-must-be-32-chars!!}
      expiration-ms: 86400000

spring:
  sql:
    init:
      mode: always               # runs schema.sql on every startup (IF NOT EXISTS guards are safe)
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 52MB
```

## Authentication and roles

JWT Bearer token auth. Obtain a token via `POST /api/auth/login`.

| Role | Can call |
|---|---|
| `ROLE_USER` | `POST /api/rag/ask` |
| `ROLE_ADMIN` | `POST /api/rag/ask`, `POST /api/rag/ingest/text`, `POST /api/rag/ingest/file`, `GET /api/rag/ingest/status/{jobId}` |

Public endpoints (no token required): `/api/auth/**`, `/actuator/health`, `/actuator/info`, `/swagger-ui/**`, `/v3/api-docs/**`.

Users (`admin/admin` and `user/user`) are stored in the PostgreSQL `users`/`authorities` tables (Spring Security's default schema). They are seeded automatically by `SecurityUserInitializer` on first startup. Replace with proper user management before production.

**Production**: always set `JWT_SECRET` as an environment variable — never commit a real secret.

## API reference

Swagger UI: `http://localhost:8080/swagger-ui.html`
OpenAPI JSON: `http://localhost:8080/v3/api-docs`

### POST /api/rag/ingest/text — returns 202 with job ID
```json
{ "jobId": "abc-123", "source": "hr-policy", "status": "QUEUED" }
```

### GET /api/rag/ingest/status/{jobId} — requires ROLE_ADMIN
```json
{ "jobId": "abc-123", "source": "hr-policy", "status": "COMPLETED", "createdAt": "...", "updatedAt": "..." }
```

## Error responses

All errors return `ApiError`:

```json
{
  "timestamp": "...",
  "status": 400,
  "error": "Bad Request",
  "messages": ["question: must not be blank"],
  "path": "/api/rag/ask"
}
```

| Status | Cause |
|---|---|
| 400 | Validation failure (`@NotBlank`, etc.) |
| 401 | Missing or invalid JWT |
| 403 | Authenticated but wrong role |
| 404 | Job ID not found |
| 413 | File upload exceeds size limit |
| 422 | Document ingestion failed |
| 502 | LLM call failed |
| 500 | Unexpected error |

## Test setup

- `@ActiveProfiles("test")` activates `application-test.yml` (H2, no pgvector, no Ollama)
- `@Import(TestBeansConfig.class)` provides mock `ChatClient` and `VectorStore` beans
- `@AutoConfigureTestRestTemplate` is required — Spring Boot 4.0 no longer auto-wires `TestRestTemplate`
- Use `@MockitoBean` (not `@MockBean`, which was removed in Spring Boot 4.0)
- `TestRestTemplate` import: `org.springframework.boot.resttestclient.TestRestTemplate`
- `@AutoConfigureTestRestTemplate` import: `org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate`

## Spring Boot 4.0 / Spring AI 2.0 gotchas

- `HealthIndicator` / `Health` moved to `org.springframework.boot.health.contributor`
- `TestRestTemplate` moved to `spring-boot-resttestclient` module (add test-scope dependency)
- `@MockBean` removed — use `@MockitoBean` from `org.springframework.test.context.bean.override.mockito`
- Spring Security 7 defaults to 403 (not 401) for unauthenticated requests — must set an explicit `AuthenticationEntryPoint`
- All three AI starters (OpenAI, Anthropic, Ollama) create `EmbeddingModel` beans; `LlmConfig` exposes a `@Primary` one to resolve ambiguity
- `@Autowired(required = false)` does NOT protect against beans that fail during creation — use placeholder API keys in profile YAMLs for providers not in use
- Spring AI 2.0 pgvector autoconfigure package: `org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration`
- Spring AI 2.0 Ollama autoconfigure package: `org.springframework.ai.ollama.autoconfigure.OllamaAutoConfiguration`

## Known limitations / future work

- **User store uses default passwords** — `admin/admin` and `user/user` must be replaced with proper credentials before production
- **pgvector dimension conflict** — switching between `openai`/`claude` and `qwen3` requires recreating the `vector_store` table
- **Spring AI 2.0.0 GA** — upgrade scheduled 2026-05-28 (see `scheduled-maintenance.md`)
