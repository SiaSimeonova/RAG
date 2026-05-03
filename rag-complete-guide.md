# RAG Module — Complete Guide

---

## 1. What is RAG?

RAG (Retrieval-Augmented Generation) makes AI/LLM responses accurate and grounded in your own data. Instead of relying solely on what an LLM was trained on, you **retrieve relevant context from your own knowledge base** and feed it to the LLM alongside the user's question.

### The core problem RAG solves

LLMs (like Claude, GPT) have a knowledge cutoff and don't know about **your** data — your product docs, internal policies, customer records, etc. RAG bridges that gap.

### How RAG works — step by step

```
User question
     │
     ▼
┌─────────────────────┐
│  1. EMBED question  │  → Convert question to a vector (list of numbers)
│     (Embedding)     │    that captures its semantic meaning
└─────────────────────┘
     │
     ▼
┌─────────────────────┐
│  2. RETRIEVE        │  → Search a vector database for the most
│     (Search)        │    semantically similar chunks of your documents
└─────────────────────┘
     │
     ▼
┌─────────────────────┐
│  3. AUGMENT         │  → Build a prompt: "Given this context: [retrieved
│     (Prompt build)  │    chunks]... answer the question: [user question]"
└─────────────────────┘
     │
     ▼
┌─────────────────────┐
│  4. GENERATE        │  → Send the augmented prompt to an LLM
│     (LLM call)      │    and return its response
└─────────────────────┘
```

### The "offline" part — ingesting your documents

Before the above flow works, you need to **index** your knowledge base once (and re-run when data changes):

```
Your documents (PDFs, HTML, DB records…)
     │
     ▼
┌─────────────────────┐
│  CHUNK              │  → Split documents into smaller pieces
│                     │    (e.g. 500-token paragraphs with overlap)
└─────────────────────┘
     │
     ▼
┌─────────────────────┐
│  EMBED each chunk   │  → Convert each chunk to a vector
└─────────────────────┘
     │
     ▼
┌─────────────────────┐
│  STORE in vector DB │  → Save vectors + original text in a
│  (e.g. pgvector,    │    searchable store
│   Qdrant, Weaviate) │
└─────────────────────┘
```

### Key concepts as a PHP developer would understand them

| RAG concept | PHP analogy |
|---|---|
| **Embedding** | Like a hash, but instead of uniqueness it captures *meaning* — similar texts get similar vectors |
| **Vector DB** | Like a MySQL full-text index, but for semantic meaning instead of exact keywords |
| **Chunk** | Like splitting a blog post into paragraphs before indexing them |
| **Context window** | Like a request body size limit — you can only send so much to the LLM |
| **LLM call** | Like an HTTP POST to an external API (OpenAI, Claude, etc.) |

---

## 2. Module Structure

### Project file tree

```
RAG/
├── pom.xml
├── src/main/resources/
│   ├── application.yml              ← Base config
│   ├── application-openai.yml       ← OpenAI profile
│   ├── application-claude.yml       ← Claude profile
│   └── application-qwen3.yml        ← Qwen3/Ollama profile
└── src/main/java/com/example/rag/
    ├── RagApplication.java          ← Spring Boot entry point
    ├── RagFacade.java               ← PUBLIC API for other modules
    ├── config/
    │   ├── RagConfig.java           ← Activates configuration
    │   ├── RagProperties.java       ← Typed config from application.yml
    │   └── LlmConfig.java           ← Selects the right ChatModel per profile
    ├── ingestion/
    │   ├── DocumentChunker.java     ← Splits text into overlapping chunks
    │   └── IngestionService.java    ← Parse → chunk → embed → store
    ├── retrieval/
    │   └── RetrievalService.java    ← Vector similarity search
    ├── generation/
    │   └── GenerationService.java   ← Retrieve + build prompt + call LLM
    ├── security/
    │   ├── SecurityConfig.java      ← Spring Security setup
    │   ├── JwtService.java          ← JWT creation & validation
    │   ├── JwtAuthFilter.java       ← Validates Bearer token per request
    │   └── SecurityProperties.java  ← Typed config: secret + expiration
    ├── api/
    │   ├── RagController.java       ← RAG REST endpoints
    │   ├── ApiError.java            ← Standard error response envelope
    │   ├── GlobalExceptionHandler.java ← Maps exceptions to HTTP responses
    │   ├── AskRequest.java / AskResponse.java
    │   ├── IngestTextRequest.java
    │   └── auth/
    │       ├── AuthController.java  ← POST /api/auth/login
    │       ├── LoginRequest.java
    │       └── LoginResponse.java
    └── exception/
        ├── RagException.java        ← Base exception
        ├── IngestionException.java  ← Document/storage failures → 422
        └── GenerationException.java ← LLM call failures → 502
```

### File responsibilities

| File | Responsibility | PHP analogy |
|---|---|---|
| `RagFacade.java` | Public API — the only class other modules should inject | A service class other controllers depend on |
| `RagProperties.java` | Typed config binding | Reading config arrays / env vars |
| `LlmConfig.java` | Selects chat model based on active profile | A factory/strategy for external API clients |
| `DocumentChunker.java` | Splits text into overlapping chunks | A utility/helper class |
| `IngestionService.java` | Parses, chunks, embeds, stores | Repository + transformer combined |
| `RetrievalService.java` | Vector similarity search | A repository `findSimilar()` method |
| `GenerationService.java` | Builds prompt + calls LLM | An HTTP client service calling an external API |
| `SecurityConfig.java` | Spring Security filter chain + user store | Auth middleware + user provider |
| `JwtService.java` | Token creation and validation | A JWT library wrapper |
| `JwtAuthFilter.java` | Reads and validates Bearer token per request | Auth middleware |
| `GlobalExceptionHandler.java` | Centralized error → HTTP response mapping | Framework exception handler |

### Tech stack

| Concern | Library/Tool |
|---|---|
| Framework | Spring Boot 3.3 + Spring AI 1.0 |
| Chat (OpenAI profile) | GPT-4o |
| Chat (Claude profile) | Claude claude-opus-4-7 |
| Chat (Qwen3 profile) | Qwen3 via Ollama (local) |
| Embeddings (openai/claude) | OpenAI text-embedding-3-small (1536 dims) |
| Embeddings (qwen3) | nomic-embed-text via Ollama (768 dims) |
| Vector store | pgvector (PostgreSQL extension) |
| Document parsing | Apache Tika |
| Authentication | Spring Security + JWT (JJWT 0.12) |
| Build | Maven, Java 21 |

### How other modules use RagFacade

```java
@Autowired
RagFacade rag;

rag.ingestText("Your document content here", "source-label");
rag.ingestFile(inputStream, "source-label");
String answer = rag.ask("What does the document say about X?");
```

---

## 3. Prerequisites & Setup

### PostgreSQL with pgvector

```sql
CREATE EXTENSION vector;
CREATE DATABASE ragdb;
```

### Environment variables

```bash
# OpenAI profile
export OPENAI_API_KEY=sk-...

# Claude profile
export ANTHROPIC_API_KEY=sk-ant-...
export OPENAI_API_KEY=sk-...      # still needed for embeddings

# JWT secret (production — generate a strong one)
export JWT_SECRET=$(openssl rand -base64 32)

# Database (optional, defaults to postgres/postgres)
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
```

### Qwen3 — local setup via Ollama

```bash
# Install Ollama from https://ollama.com, then:
ollama pull qwen3
ollama pull nomic-embed-text
```

### Build and run

```bash
# Default (OpenAI)
mvn spring-boot:run

# With a specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=claude
mvn spring-boot:run -Dspring-boot.run.profiles=qwen3

# As a JAR
java -jar rag-module.jar --spring.profiles.active=claude
```

---

## 4. Switching LLMs

Change `spring.profiles.active` in `application.yml` or pass it at startup.

| Profile | Chat model | Embedding model | Requirements |
|---|---|---|---|
| `openai` (default) | GPT-4o | text-embedding-3-small | `OPENAI_API_KEY` |
| `claude` | Claude claude-opus-4-7 | text-embedding-3-small | `ANTHROPIC_API_KEY` + `OPENAI_API_KEY` |
| `qwen3` | Qwen3 (Ollama) | nomic-embed-text (Ollama) | Ollama running locally |

### How LLM selection works internally

`LlmConfig.java` reads `rag.llm.chat-provider` and selects the right `ChatModel` bean:

```
rag.llm.chat-provider=openai  →  OpenAiChatModel  →  ChatClient
rag.llm.chat-provider=claude  →  AnthropicChatModel  →  ChatClient
rag.llm.chat-provider=qwen3   →  OllamaChatModel  →  ChatClient
```

Spring AI only auto-configures a provider when its API key / URL is present in the active profile. If a provider's key is missing, its bean is `null` — `LlmConfig` throws a clear `IllegalStateException` on startup.

### Important: switching between openai/claude and qwen3

OpenAI embeddings are **1536 dimensions**; Ollama `nomic-embed-text` is **768 dimensions**. Switching between these groups requires recreating the pgvector table:

```sql
DROP TABLE vector_store;
-- Spring AI recreates it with the correct dimensions on next startup
```

---

## 5. REST API

### Endpoints

| Method | Path | Auth | Role | Description |
|---|---|---|---|---|
| `POST` | `/api/auth/login` | No | — | Get a JWT token |
| `POST` | `/api/rag/ingest/text` | Yes | `ADMIN` | Ingest plain text |
| `POST` | `/api/rag/ingest/file` | Yes | `ADMIN` | Ingest a file (PDF, Word, etc.) |
| `POST` | `/api/rag/ask` | Yes | `USER` or `ADMIN` | Ask a question |

### Login

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin"}'
# → { "token": "eyJ...", "expiresInMs": 86400000 }
```

### Ingest text (ADMIN only)

```bash
curl -X POST http://localhost:8080/api/rag/ingest/text \
  -H "Authorization: Bearer eyJ..." \
  -H "Content-Type: application/json" \
  -d '{"text": "Employees get 25 days paid leave per year.", "source": "hr-policy"}'
```

### Ingest a file (ADMIN only)

```bash
curl -X POST http://localhost:8080/api/rag/ingest/file \
  -H "Authorization: Bearer eyJ..." \
  -F "file=@/path/to/document.pdf" \
  -F "source=hr-policy"
```

### Ask a question (USER or ADMIN)

```bash
curl -X POST http://localhost:8080/api/rag/ask \
  -H "Authorization: Bearer eyJ..." \
  -H "Content-Type: application/json" \
  -d '{"question": "How many days of leave do employees get?"}'
# → { "question": "...", "answer": "..." }
```

---

## 6. Authentication

JWT Bearer token flow:

1. `POST /api/auth/login` → server returns a signed JWT
2. Client sends `Authorization: Bearer <token>` on every subsequent request
3. `JwtAuthFilter` validates the token and sets the user in the security context

### Default users (in-memory — development only)

| Username | Password | Role |
|---|---|---|
| `admin` | `admin` | `ADMIN` |
| `user` | `user` | `USER` |

**Replace `SecurityConfig.userDetailsService()` with a database-backed implementation before production.** In the monolith, this means delegating to your user module.

### JWT configuration

```yaml
rag:
  security:
    jwt:
      secret: ${JWT_SECRET:change-me-in-production-must-be-32-chars!!}
      expiration-ms: 86400000   # 24 hours
```

---

## 7. Role-Based Access Control

| Role | Can ingest | Can ask |
|---|---|---|
| `ADMIN` | Yes | Yes |
| `USER` | No | Yes |

Enforced with `@PreAuthorize` on controller methods. Method security is enabled in `SecurityConfig` via `@EnableMethodSecurity`.

---

## 8. Error Handling

### Error response shape

```json
{
  "timestamp": "2026-04-28T10:15:30Z",
  "status": 400,
  "error": "Bad Request",
  "messages": ["question: must not be blank"],
  "path": "/api/rag/ask"
}
```

### Status code mapping

| Situation | HTTP status |
|---|---|
| Blank/invalid request field | `400 Bad Request` |
| Wrong username or password | `401 Unauthorized` |
| Missing or invalid Bearer token | `401 Unauthorized` |
| Insufficient role (e.g. USER trying to ingest) | `403 Forbidden` |
| Uploaded file too large | `413 Payload Too Large` |
| Document parse / vector store failure | `422 Unprocessable Entity` |
| LLM call failure (rate limit, network) | `502 Bad Gateway` |
| Unexpected server error | `500 Internal Server Error` |

### Validation rules

| Field | Rule |
|---|---|
| `IngestTextRequest.text` | Not blank, min 10 characters |
| `IngestTextRequest.source` | Not blank |
| `AskRequest.question` | Not blank, max 1000 characters |
| `LoginRequest.username` | Not blank |
| `LoginRequest.password` | Not blank |

### Max upload size (add to application.yml if needed)

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 25MB
```

---

## 9. Key Configuration Reference (`application.yml`)

```yaml
spring:
  profiles:
    active: openai          # openai | claude | qwen3
  datasource:
    url: jdbc:postgresql://localhost:5432/ragdb
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
  ai:
    vectorstore:
      pgvector:
        initialize-schema: true
        distance-type: COSINE_DISTANCE
        # dimensions: set per profile (1536 for openai/claude, 768 for qwen3)

rag:
  llm:
    chat-provider: openai   # must match active profile
  ingestion:
    chunk-size: 500         # characters per chunk
    chunk-overlap: 100      # overlap to avoid losing context at boundaries
  retrieval:
    top-k: 5                # number of chunks retrieved per query
  security:
    jwt:
      secret: ${JWT_SECRET}
      expiration-ms: 86400000
```
