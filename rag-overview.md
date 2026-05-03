# RAG (Retrieval-Augmented Generation) Overview

## What is RAG?

RAG is a pattern for making AI/LLM responses more accurate and grounded in your own data. Instead of relying solely on what an LLM was trained on, you **retrieve relevant context from your own knowledge base** and feed it to the LLM alongside the user's question.

### The core problem RAG solves

LLMs (like Claude, GPT) have a knowledge cutoff and don't know about **your** data — your product docs, internal policies, customer records, etc. RAG bridges that gap.

---

## How RAG works — step by step

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

---

## The "offline" part — ingesting your documents

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

---

## Key concepts as a PHP developer would understand them

| RAG concept | PHP analogy |
|---|---|
| **Embedding** | Like a hash, but instead of uniqueness it captures *meaning* — similar texts get similar vectors |
| **Vector DB** | Like a MySQL full-text index, but for semantic meaning instead of exact keywords |
| **Chunk** | Like splitting a blog post into paragraphs before indexing them |
| **Context window** | Like a request body size limit — you can only send so much to the LLM |
| **LLM call** | Like an HTTP POST to an external API (OpenAI, Claude, etc.) |

---

## Java module responsibilities

In a modular monolith, the RAG module would expose these interfaces:

1. **Ingestion** — `ingestDocument(doc)` → chunks → embeds → stores
2. **Retrieval** — `retrieve(query, topK)` → returns top N relevant chunks
3. **Generation** — `answer(query)` → calls retrieve + builds prompt + calls LLM → returns answer

---

## Recommended Java stack

| Concern | Library/Tool |
|---|---|
| Orchestration | **Spring AI** (Spring Boot module, very mature) |
| Embeddings | OpenAI `text-embedding-3-small` or a local model via Ollama |
| Vector store | **pgvector** (PostgreSQL extension) — easy if you already use Postgres |
| LLM | Claude API, OpenAI, or local via Ollama |
| Document parsing | Apache Tika (PDFs, Word, etc.) |

**Spring AI** is the best starting point for Java — it has built-in abstractions for all of these and works naturally in a Spring modular monolith.
