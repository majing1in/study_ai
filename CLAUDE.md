# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Compile all modules
mvn compile

# Compile a single module
mvn compile -pl spring-ai-module

# Run full build (compile + test)
mvn clean install

# Run tests only
mvn test

# Start Spring AI module (port 8081)
mvn spring-boot:run -pl spring-ai-module

# Start LangChain4j module (port 8082)
mvn spring-boot:run -pl langchain4j-module
```

Both modules require `OPENAI_API_KEY` environment variable (or edit `application.yml` to hardcode the key).

## Architecture

Multi-module Maven project (`com.study.ai:study_ai`) with two independent Spring Boot modules. Both use Spring Boot 3.4.1 and Java 17.

```
study_ai (parent pom, packaging=pom)
├── spring-ai-module     — Spring AI 1.0.0-M6, document ETL pipeline
└── langchain4j-module   — LangChain4j 0.36.1, sentence-based splitting
```

**The two modules are fully independent** — no cross-module dependencies. Code shared between them is intentionally duplicated (see `RecursiveCharacterTextSplitter` below).

The Spring Milestones repository (`repo.spring.io/milestone`) is configured in the parent POM because Spring AI 1.0.0-M6 is a milestone release.

### spring-ai-module — Document ETL Pipeline (port 8081)

Demonstrates three document splitting strategies for RAG:

| Splitter | Source | Key Feature |
|---|---|---|
| `TokenTextSplitter` | Spring AI built-in | Token-count-based, no overlap support |
| `OverlapParagraphTextSplitter` | Custom (extends `TextSplitter`) | Character-based with configurable overlap |
| `RecursiveCharacterTextSplitter` | Custom (standalone) | Recursive split by separator priority list |

**Pipeline flow** (`DocumentService`): Load → Clean → Split. Uses `TikaDocumentReader` for multi-format ingestion (PDF, Word, HTML, TXT).

**Two cleaning levels by design:**
- `cleanDocuments()` — deep clean: compresses whitespace, collapses newlines. Used before `TokenTextSplitter` and `OverlapParagraphTextSplitter` because these splitters don't rely on semantic markers.
- `basicCleanDocuments()` — light clean: normalizes line endings only, preserves newlines. Used before `RecursiveCharacterTextSplitter` because that splitter depends on `\n\n`, `\n`, `。` etc. as split boundaries.

**Document metadata is always preserved** — new `Document` instances created during splitting inherit all metadata from the source document.

### langchain4j-module — Semantic Splitting (port 8082)

Demonstrates `DocumentBySentenceSplitter` from LangChain4j. **Important caveat:** this splitter is English-only; it does not detect Chinese sentence boundaries. The `/split/recursive-chinese` endpoint provides a Chinese-friendly alternative using a locally-copied `RecursiveCharacterTextSplitter` with Chinese-optimized separators.

### Why RecursiveCharacterTextSplitter exists in both modules

Each module has its own copy under its respective `splitter/` package. This is deliberate — the modules share no common dependency, so adding a shared module for one utility class would over-complicate the Maven structure. If future development adds more shared utilities, extract a `common` module at that point.

## Key Constraints

- **Spring AI 1.0.0-M6** is a milestone release; its `TokenTextSplitter` has no overlap parameter (see [spring-ai#2123](https://github.com/spring-projects/spring-ai/issues/2123)). `OverlapParagraphTextSplitter` was built to fill this gap.
- **Spring AI Alibaba** is mentioned in reference docs as providing additional splitters, but is not included as a dependency — custom implementations are used instead to avoid adding another repository/dependency.
- The OpenAI starter in both modules can be swapped for other model providers (Azure, Ollama, etc.) by changing the Spring Boot starter dependency.
