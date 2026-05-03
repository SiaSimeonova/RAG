# RAG Module — Structure & Setup

## Project structure

```
RAG/
├── pom.xml                          ← Maven build + dependencies
├── src/main/resources/
│   └── application.yml              ← Config (API keys, DB, chunk sizes)
└── src/main/java/com/example/rag/
    ├── RagApplication.java          ← Spring Boot entry point
    ├── RagFacade.java               ← PUBLIC API (only this is used by other modules)
    ├── config/
    │   ├── RagConfig.java           ← Activates Spring configuration
    │   └── RagProperties.java       ← Typed config from application.yml
    ├── ingestion/
    │   ├── DocumentChunker.java     ← Splits text into overlapping chunks
    │   └── IngestionService.java    ← Parse → chunk → embed → store
    ├── retrieval/
    │   └── RetrievalService.java    ← Vector similarity search
    └── generation/
        └── GenerationService.java   ← Retrieve + build prompt + call LLM
```

## File responsibilities

| File | Responsibility | PHP analogy |
|---|---|---|
| `RagFacade.java` | Public API for other modules | A service class other controllers inject |
| `RagProperties.java` | Typed config binding from yml | Reading a config array / env vars |
| `DocumentChunker.java` | Splits text into overlapping chunks | A utility class / helper function |
| `IngestionService.java` | Parse → chunk → embed → store | A repository + transformer combined |
| `RetrievalService.java` | Vector similarity search | A repository `findSimilar()` method |
| `GenerationService.java` | Build prompt + call LLM | An HTTP client service calling an external API |

## Tech stack

| Concern | Library/Tool |
|---|---|
| Framework | Spring Boot 3.3 + Spring AI 1.0 |
| Chat + Embeddings | OpenAI (`gpt-4o` + `text-embedding-3-small`) |
| Vector store | pgvector (PostgreSQL extension) |
| Document parsing | Apache Tika (PDF, Word, HTML, etc.) |
| Build tool | Maven |
| Java version | 21 |

## Prerequisites

### 1. PostgreSQL with pgvector

```sql
CREATE EXTENSION vector;
CREATE DATABASE ragdb;
```

### 2. Environment variables

```bash
export OPENAI_API_KEY=sk-...
export DB_USERNAME=postgres   # optional, defaults to 'postgres'
export DB_PASSWORD=postgres   # optional, defaults to 'postgres'
```

### 3. Run

```bash
mvn spring-boot:run
```

## How other modules use RagFacade

```java
// Inject like any Spring service (same as injecting a service in a PHP framework)
@Autowired
RagFacade rag;

// Ingest a plain text document
rag.ingestText("Your document content here", "source-label");

// Ingest a file (PDF, Word, HTML...)
rag.ingestFile(inputStream, "source-label");

// Ask a question — returns an LLM-generated answer grounded in your documents
String answer = rag.ask("What does the document say about X?");
```

## Key configuration (application.yml)

```yaml
rag:
  ingestion:
    chunk-size: 500       # characters per chunk — increase for denser documents
    chunk-overlap: 100    # overlap avoids losing context at chunk boundaries
  retrieval:
    top-k: 5              # how many chunks to retrieve per query
```

## How the data flows

```
INGEST (offline / on-demand)
  File/Text → Tika parse → DocumentChunker → VectorStore.add()
                                               └─ Spring AI auto-embeds each chunk
                                               └─ Stored in pgvector table

QUERY (real-time)
  User question → VectorStore.similaritySearch() → top-K chunks
                → ChatClient prompt (context + question) → LLM → answer
```
