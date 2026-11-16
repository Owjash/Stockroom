# Stockroom — Smart Inventory AI

A learning project by Owjash Acharya: a Java/Spring Boot inventory app with a React interface, read-only AI tool calling, document RAG, and a retrieval evaluation harness.

The interface uses a charcoal sidebar, a soft-gray workspace, rounded white cards, and lime/lavender charts. The overview visualizes actual stock categories, health and recorded sales; global search, stock alerts, and the 7/30-day selector are connected to app data.

**Start with [SETUP.md](SETUP.md).** The core application works without Docker, an API key, or an AI model. Enable the optional AI and PostgreSQL profiles after the core works.

## What is implemented

- Product and supplier CRUD, SKU uniqueness, searchable inventory, reorder thresholds and dashboard totals.
- Multi-line sales/receipts with database transactions and ordered pessimistic row locks. Failed orders roll back every line. Historical order names/prices are snapshots.
- Sales summaries based on recorded sales, not forecasts. Currency is USD and reporting days use UTC.
- Spring AI tool calling through predefined, read-only Java methods; the UI displays tool evidence. The model has no arbitrary SQL or write tools.
- PDF ingestion using PDFBox, page-preserving chunks of at most 1,200 characters with 180-character overlap, top-3 retrieval and page references.
- Ollama chat (`qwen2.5:3b`) and embeddings (`nomic-embed-text`) through Spring AI. Models are configurable.
- PostgreSQL/pgvector exact cosine retrieval with document filtering. For local learning, file-backed H2 and a clearly labeled keyword baseline are available.
- Persistent document question/answer history, PDF viewing, document deletion, a 3-page fictional policy and 12 labeled sample questions.
- Evaluation: source-page hit rate @3, unsupported-question abstention, median/p95/mean retrieval latency, corpus counts and JSON export. Retrieval-only and end-to-end modes are distinguished.
- Automated tests and a GitHub Actions workflow.

This is a **local, single-workspace prototype**. It does not include user accounts, role-based access, multi-tenant isolation, deployed hosting, billing or demand forecasting. The server binds to localhost by default. Add authentication, authorization and operational hardening before making it a shared service.

## Stack and profiles

| Profile | Persistence | Retrieval | Generation |
|---|---|---|---|
| default | File-backed H2 | Keyword overlap baseline | Off |
| `postgres` | PostgreSQL | Keyword overlap baseline | Off |
| `ai` | File-backed H2 | In-Java cosine over persisted embeddings | Ollama |
| `postgres,ai` | PostgreSQL | pgvector exact cosine search | Ollama |

Embeddings in the AI profile are cached by model name. If you replace a model's weights under the same model name, delete/re-upload documents to rebuild embeddings. Changing the name triggers re-embedding. This implementation scans a small corpus; it does not claim billion-vector scale or ANN indexing.

## Request flow

Inventory question → Spring AI selects a predefined Java tool → transactional service reads current records → tool results return to the model → answer + raw evidence reach React.

Document question → query embedding → document-filtered top-3 pgvector cosine search → selected passages → model answer → citation-ID validation → answer + cited passages reach React.

PDF chunks remain within individual pages. Citation validation checks that IDs came from retrieved passages; it **does not prove that every generated claim is true**. Read the sources and evaluate factual support separately.

## Code map

| File | What to learn |
|---|---|
| `InventoryController.java` | REST routes, request bodies and validation |
| `InventoryModels.java` / `InventoryRepositories.java` | JPA entities, relationships, repositories |
| `InventoryService.java` | Business logic, transactions, row locks and sales calculations |
| `InventoryTools.java` / `InventoryAssistant.java` | Read-only tools and Spring AI integration |
| `PdfIngestor.java` | PDF extraction and page-aware chunking |
| `Retriever.java` / `PgVectorIndex.java` | Embeddings, cosine similarity and SQL retrieval |
| `AnswerService.java` | Context assembly, structured answers and citation checks |
| `DocumentService.java` | Document lifecycle, history and evaluation |
| `frontend/src/main.jsx` | React state, forms and API integration |
| `src/test/java/` | API, rollback, concurrency, PDF and citation tests |

All Java files are in `src/main/java/com/owjash/assistant/`. The package name is kept simple for the learning project.

## Testing and limits

See `VALIDATION.md` for what was actually checked in the build environment. Mock model tests validate application behavior; they are not measurements of Ollama's answer quality. Run the opt-in pgvector test and real-model evaluations on your machine before claiming those results on a resume.

Limits: 10 documents, 10 MB per PDF, 150 pages and 500 chunks per document; 1,000 characters per question; 50 evaluation cases per request; 100 recent orders and 30 recent document answers shown. PDFs must contain selectable text. Scans, diagrams, table structure and equations may require OCR or specialized parsing, which are not implemented.

## Documentation

- Spring AI tool calling: https://docs.spring.io/spring-ai/reference/1.1/api/tools.html
- Spring AI RAG: https://docs.spring.io/spring-ai/reference/1.1/api/retrieval-augmented-generation.html
- Ollama: https://ollama.com/download
- Chat model: https://ollama.com/library/qwen2.5
- Embedding model: https://ollama.com/library/nomic-embed-text
- pgvector: https://github.com/pgvector/pgvector

Versions are pinned for reproducibility: Java 21, Spring Boot 3.5.7, Spring AI 1.1.8, React 19.2.0, Vite 7.1.12, PDFBox 3.0.5. These are a compatible baseline, not a claim that every dependency is the latest release.
