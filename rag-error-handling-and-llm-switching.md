# Error Handling & Multi-LLM Support

## Switching LLMs

Change `spring.profiles.active` in `application.yml` (or pass it at startup):

| Profile | Chat model | Embedding model | Requirements |
|---|---|---|---|
| `openai` (default) | GPT-4o | text-embedding-3-small | `OPENAI_API_KEY` |
| `claude` | Claude claude-opus-4-7 | text-embedding-3-small | `ANTHROPIC_API_KEY` + `OPENAI_API_KEY` |
| `qwen3` | Qwen3 (Ollama) | nomic-embed-text (Ollama) | Ollama running locally |

### Option A — change `application.yml` (persistent)

```yaml
spring:
  profiles:
    active: claude   # or openai, qwen3
```

### Option B — pass at startup (one-off)

```bash
# Maven
mvn spring-boot:run -Dspring-boot.run.profiles=claude

# Java JAR
java -jar rag-module.jar --spring.profiles.active=qwen3
```

### Setting up Qwen3 locally (Ollama)

```bash
# Install Ollama: https://ollama.com
ollama pull qwen3
ollama pull nomic-embed-text
```

### Important: switching between openai/claude and qwen3

The pgvector table stores vectors of a fixed size. OpenAI embeddings are **1536 dimensions**; Ollama `nomic-embed-text` is **768 dimensions**. If you switch between these two groups, drop the table first:

```sql
DROP TABLE vector_store;
-- Spring AI will recreate it with the right dimensions on next startup
```

---

## How LLM selection works internally

`LlmConfig.java` reads `rag.llm.chat-provider` and picks the right `ChatModel` bean:

```
rag.llm.chat-provider=openai  →  OpenAiChatModel  →  ChatClient
rag.llm.chat-provider=claude  →  AnthropicChatModel  →  ChatClient
rag.llm.chat-provider=qwen3   →  OllamaChatModel  →  ChatClient
```

Spring AI only auto-configures a provider's bean when its API key (or URL for Ollama) is present in the active profile. If a provider's key is missing, its bean is `null` — `LlmConfig` throws a clear `IllegalStateException` on startup instead of a cryptic NPE at runtime.

---

## Error handling

### HTTP error response shape

All errors return the same JSON envelope:

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

| Situation | HTTP status | Exception |
|---|---|---|
| Missing/blank request field | `400 Bad Request` | `MethodArgumentNotValidException` |
| Uploaded file too large | `413 Payload Too Large` | `MaxUploadSizeExceededException` |
| Document parsing/storage failure | `422 Unprocessable Entity` | `IngestionException` |
| LLM call failure (rate limit, network) | `502 Bad Gateway` | `GenerationException` |
| Anything unexpected | `500 Internal Server Error` | `Exception` |

### Configuring max upload size

Add to `application.yml`:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 25MB
```

### Validation rules on request DTOs

| Field | Rule |
|---|---|
| `IngestTextRequest.text` | Not blank, min 10 characters |
| `IngestTextRequest.source` | Not blank |
| `AskRequest.question` | Not blank, max 1000 characters |
