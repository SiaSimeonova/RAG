# RAG Module — Developer Guide

## Table of contents

1. [What this module does](#1-what-this-module-does)
2. [Technology stack](#2-technology-stack)
3. [Prerequisites and local setup](#3-prerequisites-and-local-setup)
4. [Project structure](#4-project-structure)
5. [How RAG works — step by step](#5-how-rag-works--step-by-step)
6. [Configuration reference](#6-configuration-reference)
7. [Switching LLM providers](#7-switching-llm-providers)
8. [Authentication and authorisation](#8-authentication-and-authorisation)
9. [REST API reference](#9-rest-api-reference)
10. [Error handling](#10-error-handling)
11. [Async ingestion and job tracking](#11-async-ingestion-and-job-tracking)
12. [Health check](#12-health-check)
13. [Testing](#13-testing)
14. [Key design decisions](#14-key-design-decisions)
15. [Extending the module](#15-extending-the-module)
16. [Pending work and known limitations](#16-pending-work-and-known-limitations)

---

## 1. What this module does

This is a **Retrieval-Augmented Generation (RAG)** module designed to be embedded in a larger modular monolith.

The idea is simple: instead of asking an LLM a question from scratch, you first load your own documents into a vector database, and then — when a user asks a question — you pull the most relevant pieces of those documents and hand them to the LLM as context. This keeps the LLM's answer grounded in your data rather than its general training knowledge.

The three-step flow at runtime:

```
User question
    → Embed the question → search the vector store for similar chunks
    → Inject retrieved chunks as context into the LLM prompt
    → Return the LLM's answer to the user
```

Before answering questions, an admin must first **ingest** documents:

```
Admin uploads text / file
    → Apache Tika parses it to plain text
    → DocumentChunker splits it into overlapping chunks
    → Each chunk is embedded and stored in PostgreSQL (pgvector)
```

---

## 2. Technology stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 4.0.6 |
| AI integration | Spring AI | 2.0.0-M4 |
| LLM options | OpenAI GPT-4o, Anthropic Claude, Qwen3 (Ollama) | — |
| Vector store | PostgreSQL + pgvector extension | — |
| Document parsing | Apache Tika | 3.3.0 |
| Authentication | JWT via JJWT | 0.13.0 |
| API docs | springdoc-openapi | 3.0.3 |
| Test DB | H2 in-memory | (Spring Boot BOM) |

> **Spring AI note:** 2.0.0-M4 is a milestone release. GA is scheduled for 2026-05-28.
> Once released, update `spring-ai.version` in `pom.xml` to `2.0.0` and remove the
> `<repositories>` block that points to the Spring Milestones repository.
> Full instructions are in `scheduled-maintenance.md`.

---

## 3. Prerequisites and local setup

### Install Java 21 and Maven

Java and Maven are not installed by default on this machine. Use SDKMAN (no sudo needed):

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.7-tem
sdk install maven
```

Or with apt:

```bash
sudo apt update && sudo apt install -y maven
```

### Start PostgreSQL with pgvector

```bash
docker run -d \
  --name pgvector \
  -e POSTGRES_DB=ragdb \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  pgvector/pgvector:pg16
```

The `-p 5432:5432` flag is required — without it the container runs but is not reachable from the host.

Spring Boot will create the `vector_store`, `users`, `authorities`, and `ingestion_jobs` tables
automatically on first startup via `schema.sql` (`spring.sql.init.mode: always`).

### Run the application

**Important:** use `-Dspring-boot.run.profiles=` with `spring-boot:run` — not `-Dspring.profiles.active=`
(which is a Maven property and is ignored by the JVM process).

```bash
# OpenAI (default profile)
OPENAI_API_KEY=sk-... mvn spring-boot:run -Dspring-boot.run.profiles=openai

# Anthropic Claude
ANTHROPIC_API_KEY=sk-ant-... mvn spring-boot:run -Dspring-boot.run.profiles=claude

# Qwen3 via Ollama (local, no API key — Ollama must be running)
mvn spring-boot:run -Dspring-boot.run.profiles=qwen3
```

Once started:
- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

### Ollama setup (qwen3 profile)

```bash
# Start Ollama
ollama serve &

# Pull required models
ollama pull nomic-embed-text      # embedding model (always needed for qwen3 profile)
ollama pull qwen3:0.6b            # chat model — use 0.6b on machines with < 2 GB free RAM
                                   # alternatives: qwen3:1.7b (needs ~2 GB), qwen3 (8B, needs ~6 GB)
```

Update `spring.ai.ollama.chat.options.model` in `application-qwen3.yml` to match the variant you pull.

---

## 4. Project structure

```
src/main/java/com/example/rag/
│
├── RagApplication.java              # Spring Boot entry point; Swagger/OpenAPI global config
├── RagFacade.java                   # ★ Public API — the ONLY class other modules may inject
│
├── api/
│   ├── RagController.java           # POST /api/rag/ask, /ingest/text, /ingest/file
│   │                                #   GET /api/rag/ingest/status/{jobId}
│   ├── AskRequest.java              # { question }
│   ├── AskResponse.java             # { question, answer }
│   ├── IngestTextRequest.java       # { text, source }
│   ├── IngestResponse.java          # { jobId, source, status } — returned by ingest endpoints (202)
│   ├── IngestionStatusResponse.java # { jobId, source, status, createdAt, updatedAt }
│   ├── ApiError.java                # unified error response shape
│   ├── GlobalExceptionHandler.java  # @RestControllerAdvice — maps all exceptions to ApiError
│   └── auth/
│       ├── AuthController.java      # POST /api/auth/login
│       ├── LoginRequest.java        # { username, password }
│       └── LoginResponse.java       # { token, expiresInMs }
│
├── config/
│   ├── LlmConfig.java               # @Profile("!test") — picks @Primary ChatModel + EmbeddingModel
│   ├── AsyncConfig.java             # @EnableAsync + ingestionExecutor thread pool
│   ├── RagConfig.java               # Registers RagProperties with Spring
│   └── RagProperties.java           # @ConfigurationProperties(prefix="rag")
│
├── ingestion/
│   ├── IngestionService.java        # Tika parse → chunk → VectorStore.add()
│   ├── IngestionAsyncService.java   # @Async wrappers; updates ingestion_jobs on success/failure
│   ├── IngestionJob.java            # record: jobId, source, status, createdAt, updatedAt
│   ├── IngestionJobStatus.java      # enum: QUEUED, COMPLETED, FAILED
│   ├── IngestionJobRepository.java  # JDBC repository for ingestion_jobs table
│   └── DocumentChunker.java        # splits text into overlapping chunks
│
├── retrieval/
│   └── RetrievalService.java       # VectorStore.similaritySearch() with topK
│
├── generation/
│   └── GenerationService.java      # builds RAG prompt, calls ChatClient, returns answer
│
├── health/
│   └── RagHealthIndicator.java     # /actuator/health "rag" component
│
├── exception/
│   ├── RagException.java           # base runtime exception
│   ├── IngestionException.java     # thrown when parsing or storing a document fails
│   ├── GenerationException.java    # thrown when the LLM call fails
│   └── NotFoundException.java      # thrown when a job ID is not found (→ 404)
│
└── security/
    ├── SecurityConfig.java         # filter chain, CORS, BCrypt, @EnableMethodSecurity
    ├── SecurityBeansConfig.java    # JdbcUserDetailsManager + PasswordEncoder beans
    │                               # (separate class to avoid circular dependency with SecurityConfig)
    ├── SecurityUserInitializer.java # seeds admin/user on ApplicationReadyEvent
    ├── JwtService.java             # generate / validate JWT tokens (JJWT 0.13)
    ├── JwtAuthFilter.java          # OncePerRequestFilter — reads Bearer header
    └── SecurityProperties.java     # @ConfigurationProperties(prefix="rag.security.jwt")

src/main/resources/
├── application.yml                 # base config (datasource, logging, actuator, springdoc)
├── application-openai.yml          # OpenAI API key, GPT-4o, text-embedding-3-small, 1536 dims
├── application-claude.yml          # Anthropic API key, claude-opus-4-7, 1536 dims
├── application-qwen3.yml           # Ollama base URL, qwen3:0.6b, nomic-embed-text, 768 dims
└── schema.sql                      # DDL for users, authorities, ingestion_jobs (IF NOT EXISTS)

src/test/
├── java/com/example/rag/
│   ├── RagFacadeTest.java              # smoke test: context loads + ingestText
│   ├── security/JwtServiceTest.java    # pure unit test: token generation/validation
│   ├── api/AuthControllerTest.java     # login endpoint — 4 scenarios
│   ├── api/RagControllerTest.java      # ask + ingest endpoints — 8 scenarios
│   └── config/TestBeansConfig.java     # mock ChatClient + VectorStore for test profile
└── resources/
    └── application-test.yml            # H2 in-memory DB, autoconfigure exclusions, placeholder keys
```

---

## 5. How RAG works — step by step

### Ingestion pipeline

```
HTTP POST /api/rag/ingest/text  { text, source }
         │
         ▼
RagFacade.ingestTextAsync(text, source)
         │  creates an ingestion_jobs row with status=QUEUED
         │  returns the jobId immediately
         │
         ▼  @Async on ingestionExecutor thread pool → controller returns 202
IngestionAsyncService.ingestTextAsync(jobId, text, source)
         │
         ▼
IngestionService.ingestText(text, source)
         │
         ├─ DocumentChunker.chunk(text)
         │    • splits text into chunks of `chunkSize` characters
         │    • each consecutive chunk overlaps the previous by `chunkOverlap` characters
         │    • overlap prevents sentences from being cut and lost between chunks
         │
         ├─ Wraps each chunk in a Spring AI Document with metadata { source: "..." }
         │
         └─ VectorStore.add(documents)
              • Spring AI automatically calls the configured EmbeddingModel
              • stores vectors in the PostgreSQL vector_store table via pgvector
         │
         ▼  on success: ingestion_jobs row updated to COMPLETED
            on failure: ingestion_jobs row updated to FAILED
```

For file uploads, `IngestionService.ingest(InputStream, source)` calls Apache Tika first to extract plain text from the file (PDF, Word, HTML, etc.), then follows the same path.

### Query pipeline

```
HTTP POST /api/rag/ask  { question }
         │
         ▼
RagFacade.ask(question)
         │
         ▼
GenerationService.answer(question)
         │
         ├─ RetrievalService.retrieve(question)
         │    • embeds the question using the configured EmbeddingModel
         │    • calls VectorStore.similaritySearch with topK (default: 5)
         │    • returns the topK most semantically similar chunks
         │
         ├─ Formats retrieved chunks as a bullet-point context string
         │
         ├─ Builds a prompt:
         │    SYSTEM: "Answer using ONLY the provided context. If not in context, say so."
         │    USER:   "Context:\n<chunks>\n\nQuestion: <question>"
         │
         └─ ChatClient.prompt().call().content()
              • sends the prompt to the active LLM
              • returns the generated answer as a String
```

---

## 6. Configuration reference

### application.yml (base, always loaded)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ragdb
    username: ${DB_USERNAME:postgres}    # override with env var in prod
    password: ${DB_PASSWORD:postgres}
  profiles:
    active: openai                       # change to claude or qwen3
  sql:
    init:
      mode: always                       # runs schema.sql every startup (IF NOT EXISTS is safe)
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 52MB
  ai:
    vectorstore:
      pgvector:
        initialize-schema: true          # auto-creates vector_store table
        distance-type: COSINE_DISTANCE

rag:
  cors:
    allowed-origins:                     # YAML list — add entries per environment
      - "http://localhost:3000"
  llm:
    chat-provider: openai                # must match active profile
  ingestion:
    chunk-size: 500                      # characters per chunk
    chunk-overlap: 100                   # shared characters between consecutive chunks
  retrieval:
    top-k: 5                             # how many chunks to retrieve per question
  security:
    jwt:
      secret: ${JWT_SECRET:change-me-in-production-must-be-32-chars!!}
      expiration-ms: 86400000            # 24 hours

management:
  endpoints.web.exposure.include: health, info
  endpoint.health.show-details: when_authorized
```

### Per-LLM profile overlays

| Profile | Chat model | Embedding model | Dimensions | Required |
|---|---|---|---|---|
| `openai` | GPT-4o | text-embedding-3-small | 1536 | `OPENAI_API_KEY` env var |
| `claude` | claude-opus-4-7 | text-embedding-3-small (OpenAI) | 1536 | `ANTHROPIC_API_KEY` + `OPENAI_API_KEY` |
| `qwen3` | qwen3:0.6b (Ollama) | nomic-embed-text (Ollama) | 768 | Ollama running locally |

Each profile YAML includes placeholder keys for providers not used in that profile (e.g. `spring.ai.openai.api-key: "not-used-in-qwen3-profile"`). This is required because all three AI starters are on the classpath and will fail to initialize without a key, even if `@Autowired(required = false)` is used.

> **Dimension warning:** switching between `openai`/`claude` (1536 dims) and `qwen3` (768 dims) requires
> dropping and recreating the `vector_store` table. Run:
> ```sql
> DROP TABLE vector_store;
> ```
> Spring Boot will recreate it with the correct dimensions on next startup.

### Environment variables for production

| Variable | Purpose |
|---|---|
| `JWT_SECRET` | JWT signing key — must be at least 32 characters |
| `DB_USERNAME` | PostgreSQL username |
| `DB_PASSWORD` | PostgreSQL password |
| `OPENAI_API_KEY` | Required for `openai` and `claude` profiles |
| `ANTHROPIC_API_KEY` | Required for `claude` profile |

Generate a strong JWT secret:
```bash
openssl rand -base64 32
```

---

## 7. Switching LLM providers

The provider selection uses Spring profiles. Two things must stay in sync:

1. The active Spring profile
2. The `rag.llm.chat-provider` property

```bash
# Switch to Claude
ANTHROPIC_API_KEY=sk-ant-... \
OPENAI_API_KEY=sk-...        \
  mvn spring-boot:run -Dspring-boot.run.profiles=claude
```

Alternatively edit `application.yml`:

```yaml
spring:
  profiles:
    active: claude
rag:
  llm:
    chat-provider: claude
```

`LlmConfig` reads `rag.llm.chat-provider` and selects the matching `@Primary ChatModel` and `@Primary EmbeddingModel` bean. If the required API key is missing, startup fails immediately with a clear error message.

`LlmConfig` is annotated `@Profile("!test")` so it is excluded during tests — `TestBeansConfig` provides a mock `ChatClient` instead.

---

## 8. Authentication and authorisation

### Login flow

```
POST /api/auth/login  { username, password }
  → AuthController validates credentials via AuthenticationManager
  → JwtService.generateToken(username)  — HMAC-SHA256 signed, 24h expiry
  → returns { token, expiresInMs }
```

### Request authentication

Every protected request must include:

```
Authorization: Bearer <token>
```

`JwtAuthFilter` intercepts every request, validates the token with `JwtService.isValid()`, and sets the `SecurityContext` if valid.

### Users and roles

Users are stored in the PostgreSQL `users` and `authorities` tables using Spring Security's default JDBC schema. `SecurityUserInitializer` seeds the default users on first startup (it checks `userExists()` before inserting, so restarts are safe).

| User | Password | Role |
|---|---|---|
| `admin` | `admin` | `ROLE_ADMIN` |
| `user` | `user` | `ROLE_USER` |

| Endpoint | Required role |
|---|---|
| `POST /api/rag/ask` | `ROLE_USER` or `ROLE_ADMIN` |
| `POST /api/rag/ingest/text` | `ROLE_ADMIN` |
| `POST /api/rag/ingest/file` | `ROLE_ADMIN` |
| `GET /api/rag/ingest/status/{jobId}` | `ROLE_ADMIN` |

Role enforcement is done with `@PreAuthorize` on each controller method (`@EnableMethodSecurity` is active in `SecurityConfig`).

### Public endpoints (no token required)

```
GET  /actuator/health
GET  /actuator/info
GET  /swagger-ui/**
GET  /swagger-ui.html
GET  /v3/api-docs/**
POST /api/auth/login
```

### Circular dependency note

`UserDetailsService` and `PasswordEncoder` are defined in `SecurityBeansConfig` (not in `SecurityConfig`). This breaks the circular dependency: `SecurityConfig` → `JwtAuthFilter` → `UserDetailsService` → `SecurityConfig`.

### Replacing default users

Replace `SecurityUserInitializer` with a `UserDetailsService` that delegates to your monolith's user module:

```java
@Bean
public UserDetailsService userDetailsService() {
    return username -> {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException(username));
        return org.springframework.security.core.userdetails.User
            .withUsername(user.username())
            .password(user.encodedPassword())
            .roles(user.role())
            .build();
    };
}
```

---

## 9. REST API reference

The full interactive documentation is at `http://localhost:8080/swagger-ui.html`.

### POST /api/auth/login

No token required.

**Request**
```json
{ "username": "admin", "password": "admin" }
```

**Response 200**
```json
{ "token": "eyJ...", "expiresInMs": 86400000 }
```

**Errors:** 400 blank field, 401 wrong credentials.

---

### POST /api/rag/ingest/text

Requires `ROLE_ADMIN`. Ingestion is asynchronous — the endpoint returns `202 Accepted` immediately with a job ID.

**Request**
```json
{ "text": "The leave policy allows 25 days of paid leave per year.", "source": "hr-policy-2025" }
```

**Response 202**
```json
{ "jobId": "550e8400-e29b-41d4-a716-446655440000", "source": "hr-policy-2025", "status": "QUEUED" }
```

**Errors:** 400 blank field, 401 no/invalid token, 403 wrong role.

---

### POST /api/rag/ingest/file

Requires `ROLE_ADMIN`. `multipart/form-data`. Ingestion is asynchronous.

**Form fields**
- `file` — the file to ingest (PDF, Word, HTML, plain text, etc.)
- `source` — a label identifying where this document came from

**Response 202**
```json
{ "jobId": "550e8400-e29b-41d4-a716-446655440000", "source": "hr-policy.pdf", "status": "QUEUED" }
```

**Errors:** 400 missing field, 401, 403, 413 file too large (limit: 50 MB).

---

### GET /api/rag/ingest/status/{jobId}

Requires `ROLE_ADMIN`. Check the status of a previously submitted ingestion job.

**Response 200**
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "source": "hr-policy-2025",
  "status": "COMPLETED",
  "createdAt": "2026-04-28T05:44:27Z",
  "updatedAt": "2026-04-28T05:44:31Z"
}
```

Possible `status` values: `QUEUED`, `COMPLETED`, `FAILED`.

**Errors:** 401, 403, 404 job not found.

---

### POST /api/rag/ask

Requires `ROLE_USER` or `ROLE_ADMIN`. Synchronous.

**Request**
```json
{ "question": "How many days of annual leave do employees get?" }
```

**Response 200**
```json
{
  "question": "How many days of annual leave do employees get?",
  "answer": "Employees get 25 days of paid leave per year."
}
```

**Errors:** 400 blank question, 401, 502 LLM unreachable.

---

## 10. Error handling

All errors return a consistent `ApiError` JSON body:

```json
{
  "timestamp": "2026-04-28T12:00:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "messages": ["question: must not be blank"],
  "path": "/api/rag/ask"
}
```

`GlobalExceptionHandler` (`api/GlobalExceptionHandler.java`) maps each exception type:

| Exception | HTTP status | When |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | `@Valid` constraint violated (blank field, etc.) |
| `BadCredentialsException` / `AuthenticationException` | 401 | Wrong username or password at login |
| `AccessDeniedException` | 403 | Authenticated user lacks required role |
| `NotFoundException` | 404 | Ingestion job ID not found |
| `MaxUploadSizeExceededException` | 413 | Uploaded file exceeds size limit |
| `IngestionException` | 422 | Tika parsing or VectorStore write failed |
| `GenerationException` | 502 | LLM call failed (network error, rate limit, etc.) |
| `Exception` (catch-all) | 500 | Anything unexpected — stack trace is never sent to the client |

The exception hierarchy is:
```
RuntimeException
  └── RagException
        ├── IngestionException
        └── GenerationException
  └── NotFoundException  (separate — not a RagException)
```

---

## 11. Async ingestion and job tracking

Ingestion is deliberately asynchronous. Parsing a large PDF and calling an embedding API can take several seconds — returning 202 immediately keeps the HTTP response fast.

**How it works:**

1. `RagController` calls `RagFacade.ingestTextAsync()` / `ingestFileAsync()`.
2. The facade creates an `ingestion_jobs` row with `status=QUEUED` and returns the `jobId`.
3. The controller returns `202 Accepted` with the job ID in the body.
4. The facade delegates to `IngestionAsyncService`, whose methods are annotated `@Async("ingestionExecutor")`.
5. Spring AOP intercepts the call and submits it to the `ingestionExecutor` thread pool (defined in `AsyncConfig`).
6. The actual parsing, chunking, embedding, and storing runs on a background thread.
7. On completion: `IngestionJobRepository.updateStatus()` sets the row to `COMPLETED` or `FAILED`.
8. The caller can poll `GET /api/rag/ingest/status/{jobId}` to check the outcome.

**Thread pool settings** (`AsyncConfig`):

| Setting | Value | Meaning |
|---|---|---|
| `corePoolSize` | 2 | Always-on threads |
| `maxPoolSize` | 5 | Burst capacity |
| `queueCapacity` | 50 | Requests buffered before rejection |
| Thread name prefix | `ingestion-` | Visible in logs |

**Why bytes instead of InputStream for file uploads:**
The HTTP request's `InputStream` is closed once the controller returns. If we handed the stream to a background thread, the thread would try to read an already-closed stream. The controller reads `file.getBytes()` synchronously before handing off to the async layer.

**Error handling for async methods:**
`@Async void` methods cannot propagate exceptions back to the caller. `AsyncConfig` registers an `AsyncUncaughtExceptionHandler` that logs them so they are never silently swallowed. The job status is also updated to `FAILED` so the caller can observe the outcome via the status endpoint.

---

## 12. Health check

```
GET /actuator/health
```

Returns overall application health. The `rag` component is reported by `RagHealthIndicator`:

**Healthy response** (authenticated user):
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "rag": {
      "status": "UP",
      "details": {
        "chatProvider": "qwen3",
        "vectorStore": "PgVectorStore"
      }
    }
  }
}
```

**Unhealthy** (vector store unreachable):
```json
{
  "status": "DOWN",
  "components": {
    "rag": {
      "status": "DOWN",
      "details": {
        "chatProvider": "qwen3",
        "error": "Connection refused"
      }
    }
  }
}
```

`show-details: when_authorized` means unauthenticated callers only see the top-level `UP`/`DOWN` status — the component breakdown requires a valid token.

---

## 13. Testing

### Running tests

```bash
mvn test -Dspring.profiles.active=test
```

No real LLM API keys or PostgreSQL are needed — the test profile uses H2 and Mockito mocks.

### Test classes

| Class | Type | What it covers |
|---|---|---|
| `JwtServiceTest` | Pure unit test | Token generation, username extraction, expiry, tampering, malformed tokens |
| `RagFacadeTest` | Spring context smoke test | Context loads, `ingestText` does not throw |
| `AuthControllerTest` | Integration (RANDOM_PORT) | Login: valid credentials, wrong password, blank username, unknown user |
| `RagControllerTest` | Integration (RANDOM_PORT) | Ask: no token, user token, admin token, blank question. Ingest: user token (403), admin token (202), blank source (400), no token (401) |

### Test infrastructure

- `@ActiveProfiles("test")` activates `application-test.yml`
  - H2 in-memory database
  - Excludes `PgVectorStoreAutoConfiguration` and `OllamaAutoConfiguration`
  - Placeholder API keys so OpenAI/Anthropic auto-configurations start without errors
  - JWT secret and `rag.*` properties set to test values
- `@Import(TestBeansConfig.class)` provides:
  - `@Primary ChatClient` — Mockito mock
  - `@Primary VectorStore` — Mockito mock
- `LlmConfig` is excluded in the test profile (`@Profile("!test")`), so no real LLM beans are created

### Spring Boot 4.0 migration — what changed from 3.x

| Area | Change |
|---|---|
| `HealthIndicator` / `Health` | Moved to `org.springframework.boot.health.contributor` |
| `TestRestTemplate` | Moved to `spring-boot-resttestclient` module — add test-scope dependency and use new import |
| `@AutoConfigureTestRestTemplate` | Same new module — `org.springframework.boot.resttestclient.autoconfigure` |
| `@MockBean` | **Removed** — use `@MockitoBean` from `org.springframework.test.context.bean.override.mockito` |
| Spring Security 7 | Unauthenticated requests return 403 by default — must add an explicit `AuthenticationEntryPoint` for 401 |

### Spring AI 2.0 migration — autoconfigure package renames

| Bean | Old package (1.x) | New package (2.0) |
|---|---|---|
| `PgVectorStoreAutoConfiguration` | `org.springframework.ai.autoconfigure.vectorstore.pgvector.*` | `org.springframework.ai.vectorstore.pgvector.autoconfigure.*` |
| `OllamaAutoConfiguration` | `org.springframework.ai.autoconfigure.ollama.*` | `org.springframework.ai.ollama.autoconfigure.*` |

These are referenced in `application-test.yml` under `spring.autoconfigure.exclude`.

### Multiple EmbeddingModel beans

All three AI starters (OpenAI, Anthropic, Ollama) register `EmbeddingModel` beans when on the classpath. `LlmConfig` exposes a `@Primary EmbeddingModel primaryEmbeddingModel()` that picks the right one based on the active provider. Without this, Spring fails with "expected single matching bean but found N".

---

## 14. Key design decisions

### RagFacade as the module boundary

`RagFacade` is the only public entry point. Other modules in the monolith inject `RagFacade` and never reference internal classes like `IngestionService` or `GenerationService`. This keeps the module's internals free to change without affecting callers.

### Profile-based LLM switching

Each LLM has its own `application-{profile}.yml` overlay. `LlmConfig` reads `rag.llm.chat-provider` and selects the active `@Primary ChatModel` and `@Primary EmbeddingModel` beans at startup. Adding a new LLM means adding a new profile yml and a new `case` in `LlmConfig`.

### Placeholder API keys in profile YAMLs

All three AI starters are on the classpath (to support switching providers without code changes). Each starter tries to create its beans at startup and fails if the API key property is missing — even if the bean will not be used. The fix is to include placeholder key values in every profile YAML for providers not active in that profile. `LlmConfig` then discards the unused beans.

### SecurityBeansConfig to avoid circular dependency

`SecurityConfig` depends on `JwtAuthFilter`. `JwtAuthFilter` depends on `UserDetailsService`. If `UserDetailsService` were defined in `SecurityConfig`, Spring would detect a cycle. Extracting `JdbcUserDetailsManager` and `PasswordEncoder` to `SecurityBeansConfig` breaks the cycle.

### Chunking with overlap

`DocumentChunker` uses a sliding-window approach: each chunk starts `(chunkSize - chunkOverlap)` characters after the previous one. This means sentences that fall near a chunk boundary appear in two consecutive chunks, making them retrievable regardless of where the query lands. Default: 500 character chunks, 100 character overlap.

### Why the controller reads file bytes before async hand-off

When `POST /api/rag/ingest/file` is called, the `MultipartFile` wraps the HTTP request's input stream. Once the controller method returns and the response is written, that stream is closed. A background thread starting later would read from an already-closed stream. The fix is `file.getBytes()` in the controller — this copies the file into a byte array in memory before the response is sent, and the byte array is safe to pass to a background thread.

### Stateless JWT security

There is no server-side session state. Each request carries a self-contained signed token. The `JwtAuthFilter` verifies the signature and sets the `SecurityContext` fresh for every request. This is well-suited for horizontal scaling.

---

## 15. Extending the module

### Add a new LLM provider

1. Add a new profile yml `application-{name}.yml` with the provider's API key, model name, and pgvector dimensions. Include placeholder keys for all other providers.
2. Add a new `case` in `LlmConfig.primaryChatModel()` and `LlmConfig.primaryEmbeddingModel()`.
3. Add a new Spring AI starter dependency in `pom.xml` if the provider is not already included.

### Replace default users with database users

Replace `SecurityUserInitializer` with a `UserDetailsService` that loads users from your database or from another module. `JwtService`, `JwtAuthFilter`, and role-based access control all continue working unchanged.

### Add a new ingestion source type

If you need to ingest from a URL or an S3 bucket, add a method to `RagFacade` that accepts the source-specific parameters, and add a corresponding async wrapper in `IngestionAsyncService`. The core `IngestionService.ingest(InputStream, source)` already accepts any `InputStream`, so new sources just need to open a stream and call through.

### Add source-filtered retrieval

`RetrievalService.retrieveFromSource(query, source)` retrieves only chunks whose `source` metadata matches the given value. This method is available but not currently exposed via the REST API. To expose it, add a `source` field to `AskRequest` and pass it through `RagFacade` to `GenerationService`.

---

## 16. Pending work and known limitations

| Item | Detail |
|---|---|
| **Default credentials in production** | `admin/admin` and `user/user` are seeded by `SecurityUserInitializer`. Must be replaced with proper credentials or a real user management system before production. |
| **pgvector dimension conflict** | Switching between `openai`/`claude` (1536 dims) and `qwen3` (768 dims) requires `DROP TABLE vector_store` so Spring AI can recreate it with the correct dimensions. |
| **Spring AI 2.0.0 GA** | Once GA ships (scheduled 2026-05-28), update `spring-ai.version` in `pom.xml` to `2.0.0` and remove the Spring Milestones `<repositories>` block. Full instructions in `scheduled-maintenance.md`. |
| **No ingestion job pagination** | `GET /api/rag/ingest/status/{jobId}` looks up a single job. There is no endpoint to list all jobs or filter by status. |
| **No webhook / push notification on job completion** | Callers must poll the status endpoint. Consider adding a callback URL or Server-Sent Events for long-running ingestion jobs. |
| **Qwen3 memory requirements** | The default `qwen3` (8B) model requires ~6 GB RAM. On machines with limited memory, use `qwen3:0.6b` (~600 MB) or `qwen3:1.7b` (~2 GB) and update `application-qwen3.yml` accordingly. |
