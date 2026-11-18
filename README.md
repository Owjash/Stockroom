# Stockroom — Smart Inventory AI

Stockroom is a local inventory application with a Spring Boot REST API, a React dashboard, read-only AI tools for database questions, and page-cited PDF question answering.

Built by [Owjash Acharya](https://github.com/Owjash). The application starts with fictional sample data and supports both a core mode without AI and an optional local Ollama/PostgreSQL configuration.

## Features

- **Inventory and suppliers:** product and supplier CRUD, unique SKUs, search, stock quantities, reorder thresholds, and dashboard totals.
- **Sales and receipts:** multi-line orders, transactional stock updates, oversell rejection, rollback, ordered pessimistic row locks, and historical product/price snapshots.
- **Inventory assistant:** Spring AI selects predefined read-only Java tools. Responses include database evidence from current records. The model has no arbitrary SQL or inventory write tools.
- **Document assistant:** PDFBox extraction, page-preserving chunks of up to 1,200 characters with 180-character overlap, top-3 retrieval, page citations, and saved question/answer history.
- **Answer checks:** short passage labels mapped back to stored sources, citation validation, a second model call to audit the proposed answer, and literal supporting-quote checks.
- **Evaluation:** labeled questions, source-page hit rate @3, unsupported-question abstention, retrieval timing, and JSON export.

Inventory questions and document questions use separate evidence. Ask about stock, products, suppliers, and recorded sales in **AI Assistant**. Ask about the selected PDF in **Documents**.

## Run locally

Prerequisites: a full JDK 21 and Node.js 22.12 or a compatible later version. The Maven Wrapper is included.

```sh
git clone https://github.com/Owjash/Stockroom.git
cd Stockroom
npm --prefix frontend ci
npm --prefix frontend run build
chmod +x mvnw
./mvnw spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080). This starts the inventory app with file-backed H2 and a keyword document-retrieval baseline. It does not require Docker, an API key, or an AI model.

For the local AI configuration used in the sample validation, install and start Docker Desktop and Ollama, then run:

```sh
docker compose up -d postgres
docker compose ps
ollama pull nomic-embed-text
ollama pull qwen2.5:7b
```

Wait for PostgreSQL to be healthy. Stop the running Spring Boot process with **Control+C**, then start it with:

```sh
OLLAMA_CHAT_MODEL=qwen2.5:7b ./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres,ai
```

Check [http://localhost:8080/api/status](http://localhost:8080/api/status) for the active model and retrieval configuration. The checked-in fallback chat model is `qwen2.5:3b`; the command above explicitly selects the `7b` model used for the recorded sample evaluation.

See [SETUP.md](SETUP.md) for the staged setup, troubleshooting, development server, and optional PostgreSQL integration test.

## Stack and profiles

Java 21, Spring Boot 3.5.7, Spring AI 1.1.8, React 19.2.0, Vite 7.1.12, JPA, PDFBox 3.0.5, PostgreSQL, pgvector, H2, and Ollama. Versions listed here describe the project's pinned dependencies.

| Profile | Persistence | Retrieval | Generation |
|---|---|---|---|
| Default | File-backed H2 | Keyword overlap | Off |
| `postgres` | PostgreSQL | Keyword overlap | Off |
| `ai` | File-backed H2 | Java cosine search over persisted embeddings | Ollama |
| `postgres,ai` | PostgreSQL | pgvector exact cosine search | Ollama |

Embeddings are cached by model name. Changing the configured embedding model name triggers re-embedding. If weights change under the same name, re-upload documents to rebuild their embeddings.

## How answers are checked

For inventory questions, Spring AI selects a predefined Java tool, the service reads the current database records, and React displays the response with the tool evidence.

For document questions, retrieval is scoped to the selected document. The answer model receives short labels such as `S1` rather than database UUIDs. The application validates the returned labels and maps them to the original sources. A separate call audits whether the proposed answer addresses the question and is supported by the cited passages. Supporting quotations must occur in those passages after whitespace normalization.

Missing evidence or rejected support leads to an abstention. Model or auditor service errors return an error instead of being counted as successful abstentions. The model auditor can still misjudge meaning; matching citations and quotations do not prove factual correctness.

## Recorded validation

Local validation completed on **November 17, 2026**:

| Check | Observed result |
|---|---:|
| `./mvnw clean test` | 27 passed, 1 optional pgvector test skipped; zero failures or errors |
| Supported sample questions: expected page retrieved and no abstention | 9/9 |
| Unsupported sample questions refused | 3/3 |
| Supported sample questions incorrectly refused | 0/9 |
| Median retrieval time in that run | 23.5 ms |

The live run used `qwen2.5:7b`, `nomic-embed-text`, and PostgreSQL/pgvector against **one fictional PDF with three pages and three chunks**. These 12 questions were used during debugging. The export records retrieval and abstention, not generated-answer factual accuracy. Retrieval timing excludes answer generation and auditing.

See [VALIDATION.md](VALIDATION.md), the [original evaluation export](validation/evaluation-results.json), its [configuration record](validation/evaluation-context.json), and the [test summary](validation/test-summary.json).

## Code map

Java source files are under `src/main/java/com/owjash/assistant/`.

| File | Responsibility |
|---|---|
| `InventoryController.java` | REST endpoints and request validation |
| `InventoryModels.java` / `InventoryRepositories.java` | JPA entities and repositories |
| `InventoryService.java` | Stock updates, transactions, locks, and sales calculations |
| `InventoryTools.java` / `InventoryAssistant.java` | Read-only database tools and inventory AI responses |
| `PdfIngestor.java` | PDF extraction and page-aware chunking |
| `Retriever.java` / `PgVectorIndex.java` | Embeddings, similarity search, and document scoping |
| `AnswerService.java` | Structured document answers and citation-label mapping |
| `GroundingVerifier.java` | Model audit and supporting-quote checks |
| `DocumentService.java` | Transactional document operations, history, and evaluation |
| `frontend/src/main.jsx` | React interface, state, and API calls |
| `src/test/java/` | Inventory integration, retrieval, and answer-guard tests |

## Scope

This is a local, single-workspace prototype. It has no accounts, role-based access, multi-tenant isolation, hosted deployment, or demand forecasting. The server binds to localhost by default. Reports use USD and UTC days.

Limits include 10 documents, 10 MB per PDF, 150 pages and 500 chunks per document, 1,000 characters per question, and 50 cases per evaluation request. The interface shows 100 recent orders and 30 recent document answers. PDFs need selectable text; OCR and specialized diagram, equation, or table extraction are not implemented.

## References

- [Spring AI tool calling](https://docs.spring.io/spring-ai/reference/1.1/api/tools.html)
- [Spring AI RAG](https://docs.spring.io/spring-ai/reference/1.1/api/retrieval-augmented-generation.html)
- [Ollama](https://ollama.com/download)
- [Qwen2.5 model](https://ollama.com/library/qwen2.5)
- [nomic-embed-text model](https://ollama.com/library/nomic-embed-text)
- [pgvector](https://github.com/pgvector/pgvector)
