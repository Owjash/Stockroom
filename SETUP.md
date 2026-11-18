# Run Stockroom on your Mac

Follow these stages in order. Get the inventory app working first; enable AI afterward.

## 1. Install prerequisites

- **JDK 21**: https://adoptium.net/temurin/releases/?version=21 — choose macOS and your Mac's architecture (Apple Silicon is aarch64).
- **Node.js 22 LTS**: https://nodejs.org/ — includes npm. Vite requires a sufficiently recent Node; 22.12 or newer works with this project.
- **VS Code**: https://code.visualstudio.com/ — install Microsoft's **Extension Pack for Java**.

Maven is downloaded by the included Maven Wrapper. You do not have to install Maven separately. First-time setup needs internet access to download dependencies.

Open Terminal and check:

```sh
java -version
node -v
npm -v
```

Java should report 21 and Node should report 22 or newer. If you installed Java 21 but Terminal still uses another Java version:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

## 2. Open the project

Clone the repository, or open your existing checkout:

```sh
git clone https://github.com/Owjash/Stockroom.git
cd Stockroom
```

For an existing checkout, use its actual folder name; it may still be named `smart-inventory-ai`. Open that folder in VS Code and allow Maven to import the project.

Open **Terminal → New Terminal**. The terminal should be inside the project root containing `pom.xml`, `mvnw`, and `frontend`.

## 3. Build the React interface

```sh
cd frontend
npm ci
npm run build
cd ..
```

`npm ci` installs the exact locked dependencies. `npm run build` writes the interface into Spring Boot's static resources directory. Rebuild after changing the frontend before packaging/running the single-server version.

## 4. Start the core app

```sh
chmod +x mvnw
./mvnw spring-boot:run
```

Keep this terminal running. Open **http://localhost:8080**.

You should see a dashboard and eight fictional product records. The default mode uses file-backed H2, so records survive a normal restart. AI is intentionally off and the UI says so. Uploading PDFs works in keyword-retrieval baseline mode.

Stop the server with **Control+C**. To start again, run the same Maven command from the project root.

### Try the core workflows

1. Open **Inventory → Add product**. Choose a supplier, a unique SKU, quantity, reorder level, and price.
2. Open **Orders → Record order**. Record a small sale and confirm stock decreases.
3. Try selling more units than are available. You should get an error and no partial order.
4. Record a receipt and confirm stock increases.
5. Set a product's quantity at or below its reorder level and check Overview.
6. Open **Documents → Load sample policy**. Ask `What is the minimum order quantity?` Click the page chip and **Open PDF**.

The default document response is a retrieval baseline, not an AI-generated answer.

## 5. Run the automated tests

In a second terminal at the project root:

```sh
./mvnw clean test
```

The regular tests use a separate in-memory test database. They do not alter your normal application data and do not require an AI model. The pgvector test is skipped until explicitly enabled.

## 6. Add PostgreSQL and pgvector

Install and open **Docker Desktop**: https://www.docker.com/products/docker-desktop/

In the project root:

```sh
docker compose up -d postgres
docker compose ps
```

Wait for the `postgres` service to be healthy. Stop the running Spring app with Control+C, then restart it with:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

This switches to a separate PostgreSQL database; it does not migrate existing H2 records. A fresh PostgreSQL database gets its own fictional seed data. The pgvector extension/table are initialized on startup. AI remains off until the next stage.

The included development database uses `assistant` / `local-dev-only`, binds only to localhost, and stores data in a Docker volume. Do not use these development credentials for hosting.

## 7. Enable the local AI models

Install **Ollama**: https://ollama.com/download

Open Ollama on your Mac. In a new terminal:

```sh
ollama pull qwen2.5:7b
ollama pull nomic-embed-text
ollama list
```

The first download can take time. Local inference speed depends on your Mac. If Ollama is not running as an app, start `ollama serve` in a separate terminal. If it says the port is already in use, the Ollama server is probably already running.

Stop Spring Boot with Control+C. With PostgreSQL and Ollama running, start:

```sh
OLLAMA_CHAT_MODEL=qwen2.5:7b ./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres,ai
```

The environment-variable prefix selects `qwen2.5:7b` for this launch. Include it when restarting to reproduce the recorded validation. Without an override, the checked-in configuration falls back to `qwen2.5:3b`; that model needs its own download and evaluation.

Open [http://localhost:8080/api/status](http://localhost:8080/api/status) and verify `chatModel` is `qwen2.5:7b`, `aiEnabled` is `true`, `retrieval` is `semantic`, and `vectorStore` is `pgvector`. This endpoint reports configuration; a real request confirms the integrations respond.

Open http://localhost:8080. The badge should read **AI configured**. This means the AI profile is enabled; the first request confirms whether the model server actually responds.

Try **AI Assistant**:

- `Which products need restocking?`
- `Summarize my inventory.`
- `Which products sold most in the last 7 days?`

Expand **Database evidence** and compare it with Inventory/Orders. The assistant is read-only. It cannot create orders or edit stock.

Try **Documents**:

1. Select an existing copy of the fictional sample policy, load it once if absent, or upload a text-based PDF you are allowed to use. Repeatedly clicking **Load sample policy** creates duplicate documents.
2. Ask `What is the minimum order quantity?`
3. Check the answer, supporting passage, and PDF page.
4. Ask an unsupported question, such as `What is the warranty duration?`
5. Inspect whether the system abstains appropriately.

With the `postgres,ai` profiles and the command above, the system embeds text with `nomic-embed-text`, uses pgvector cosine search, and generates answers with `qwen2.5:7b`. The chunk target is 1,200 characters, overlap 180 characters, with top-3 retrieval and a heuristic 0.42 cosine threshold. A second model call audits proposed answers against cited passages, followed by literal supporting-quote checks. These checks can still make semantic mistakes.

If your Mac struggles with the model, use the core app while learning the backend. A different tool-capable Ollama chat model can be selected through `OLLAMA_CHAT_MODEL`; re-evaluate after changing models.

## 8. Run an evaluation and collect real numbers

Open **Evaluation**, select the sample policy, and run its 12 labeled cases. Use sample labels only with the sample policy. For your own PDFs, write new questions and label the correct source pages.

- Without **Include model abstention**, unsupported-question results measure only the retrieval threshold.
- With that checkbox enabled, the system runs the answer pipeline, including the evidence auditor when a draft is eligible for audit. Unsupported rows pass when it abstains. Supported rows need the expected page among retrieved results and no abstention.
- Source-page hit rate @3 measures retrieval independently of answer abstention.
- The evaluation does not score factual correctness, citation entailment, or answer completeness. The model auditor is a fallible guard, not a ground-truth evaluator; review actual answers against their sources.
- Median/p95 retrieval timing includes query embedding and retrieval. It excludes generation and auditing; initial indexing/backfill can affect the first measurement.

Keep the evaluation tab open while the model calls finish. A service/model error is an error, not a successful abstention.

Use **Export results** to save the actual JSON report. It includes document/page/chunk counts and a timestamp, but not the model names or generated answers. Keep the launch configuration and test questions with it. The recorded local sample run and its limits are documented in [VALIDATION.md](VALIDATION.md); do not describe it as a real-world answer-accuracy benchmark.

For stronger evidence, evaluate distinct PDFs with 30–60 independently labeled questions, include unsupported questions, report failures, and compare the keyword baseline with the semantic version. Each UI run evaluates the selected PDF, so its document count is 1. Do not describe a single-document run as a multi-document benchmark.

## 9. Test pgvector itself (optional, disposable database)

Start the separate test service:

```sh
docker compose --profile test up -d postgres-test
```

Wait for it to be healthy, then run:

```sh
RUN_PGVECTOR_TESTS=true ./mvnw -Dtest=PgVectorIntegrationTest test
```

This test uses **localhost:5433/smart_inventory_test** with create/drop schema behavior. It must only target the provided disposable test database. It does not require Ollama. It tests cosine ranking, source text retrieval, document scoping, and vector deletion.

## 10. Development workflow

For frontend hot reload, keep Spring Boot running on 8080. In another terminal:

```sh
cd frontend
npm run dev
```

Open the URL Vite prints (normally http://localhost:5173). Vite proxies `/api` to the backend. Do not enable broad CORS just to connect the frontend.

For an executable application JAR:

```sh
cd frontend
npm ci
npm run build
cd ..
./mvnw verify
java -jar target/smart-inventory-ai-0.1.0.jar
```

For the full AI profile with a packaged JAR:

```sh
OLLAMA_CHAT_MODEL=qwen2.5:7b java -jar target/smart-inventory-ai-0.1.0.jar --spring.profiles.active=postgres,ai
```

## What to study first

1. Trace `GET /api/products` from `InventoryController` into `InventoryService` and the repository.
2. Understand `Product`, `Supplier`, and their JPA relationship.
3. Read `placeOrder()` and run the rollback/concurrency tests.
4. Read `InventoryTools`; explain why the model receives read-only tools instead of arbitrary SQL access.
5. Trace a PDF through `PdfIngestor`, `Retriever`, `PgVectorIndex`, and `AnswerService`.
6. Add your own evaluation cases; inspect errors instead of chasing a perfect score.

The inventory routes and services are a starting point for studying Spring Boot, REST, JPA, transactions, and tests. The document pipeline adds PDF extraction, embeddings, vector retrieval, structured model output, and evaluation.

## Common problems

| Problem | Fix |
|---|---|
| `Permission denied: ./mvnw` | Run `chmod +x mvnw`. |
| `release version 21 not supported` | Check `./mvnw -v` and install/select a full JDK 21, not just a JRE. |
| Blank/missing interface | Run `npm ci && npm run build` inside frontend, then restart Spring Boot. |
| Port 8080 is already used | Stop the other app, or pass `-Dspring-boot.run.arguments=--server.port=8081` and open port 8081. |
| PostgreSQL connection refused | Open Docker Desktop; run `docker compose ps`; wait for PostgreSQL to be healthy. |
| AI is not enabled | Restart Spring Boot with `-Dspring-boot.run.profiles=postgres,ai`. |
| Model not found / 502 | Check `ollama list`, Ollama server status, and the names shown by `/api/status`. Invalid generator/auditor responses also produce 502; inspect the terminal error. |
| PostgreSQL PDF error mentioning large objects and auto-commit | Verify `DocumentService` retains its class-level `@Transactional` annotation. |
| No readable PDF text | Use a PDF with selectable text. OCR is not implemented. |
| Incorrect or missing source | Inspect the PDF extraction and evaluation labels. Tune chunking/retrieval with evidence. |
| New database looks empty/different | H2 and PostgreSQL are separate stores; automatic migration is not included. |

Configuration variables are listed in `.env.example`. Spring Boot does not automatically load that file. Use shell `export` or your IDE run configuration. Keep secrets out of Git.

## Applying the redesigned frontend to an existing checkout

If you already ran the earlier version, copy the updated files from `frontend/src` into your existing project's `frontend/src` folder. Keep your existing `data` directory and database configuration. No backend or database migration is needed for this visual update.

From the project root, stop the current Spring Boot process, then run:

```sh
npm --prefix frontend run build
./mvnw spring-boot:run
```

For the recorded AI configuration, restart with `OLLAMA_CHAT_MODEL=qwen2.5:7b ./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres,ai` instead of the core-only command above. For Vite development mode, frontend source changes reload through `npm run dev`.

## Ollama command not found on macOS

Open Ollama from Applications and allow terminal command installation
if prompted. Then open a new terminal and run:

```bash
ollama --version
```

If the command is still unavailable, add Ollama to the current
terminal session's PATH:

```bash
export PATH="/Applications/Ollama.app/Contents/Resources:$PATH"
ollama --version
```

Keep the Ollama application running when using Stockroom's AI features.

