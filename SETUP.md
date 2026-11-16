# Run Smart Inventory AI on your Mac

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

Download and expand `smart-inventory-ai.zip`. Move the extracted `smart-inventory-ai` folder into Downloads if needed. In VS Code, choose **File → Open Folder** and select that folder. Allow Maven to import when prompted.

Open **Terminal → New Terminal** in VS Code. It should be inside the folder containing `pom.xml`, `mvnw`, and `frontend`.

If using the macOS Terminal instead:

```sh
cd ~/Downloads/smart-inventory-ai
```

If macOS prompts about terminal access to Downloads, allow it for the folder you selected.

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
./mvnw test
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
ollama pull qwen2.5:3b
ollama pull nomic-embed-text
ollama list
```

The first download can take time. Local inference speed depends on your Mac. If Ollama is not running as an app, start `ollama serve` in a separate terminal. If it says the port is already in use, the Ollama server is probably already running.

Stop Spring Boot with Control+C. With PostgreSQL and Ollama running, start:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres,ai
```

Open http://localhost:8080. The badge should read **AI configured**. This means the AI profile is enabled; the first request confirms whether the model server actually responds.

Try **AI Assistant**:

- `Which products need restocking?`
- `Summarize my inventory.`
- `Which products sold most in the last 7 days?`

Expand **Database evidence** and compare it with Inventory/Orders. The assistant is read-only. It cannot create orders or edit stock.

Try **Documents**:

1. Load the fictional sample policy or upload a text-based PDF you are allowed to use.
2. Ask `What is the minimum order quantity?`
3. Check the answer, supporting passage, and PDF page.
4. Ask an unsupported question, such as `What is the warranty duration?`
5. Inspect whether the system abstains appropriately.

With the `postgres,ai` profiles, the system embeds text with `nomic-embed-text`, uses pgvector cosine search, and generates answers with `qwen2.5:3b`. The default chunk target is 1,200 characters, overlap 180 characters, with top-3 retrieval and a heuristic 0.42 cosine threshold.

If your Mac struggles with the model, use the core app while learning the backend. A different tool-capable Ollama chat model can be selected through `OLLAMA_CHAT_MODEL`; re-evaluate after changing models.

## 8. Run an evaluation and collect real numbers

Open **Evaluation**, select the sample policy, and run its 12 labeled cases. Use sample labels only with the sample policy. For your own PDFs, write new questions and label the correct source pages.

- Without **Include model abstention**, unsupported-question results measure only the retrieval threshold.
- With that checkbox enabled, the system runs the answer model and scores its abstention on negative cases.
- Source-page hit rate @3 checks whether the expected page appears among retrieved passages.
- It does not automatically verify factual correctness, citation entailment or answer completeness. Review answers manually for those properties.
- Median/p95 latency includes query embedding and retrieval; initial indexing/backfill can affect the first measurement.

Use **Export results** to save the actual JSON report. The report includes the document/page/chunk counts and timestamp. Keep these with your model configuration and test questions. Do not put example percentages or sample-fixture performance on your resume as a real-world benchmark.

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
java -jar target/smart-inventory-ai-0.1.0.jar --spring.profiles.active=postgres,ai
```

## What to study first

1. Trace `GET /api/products` from `InventoryController` into `InventoryService` and the repository.
2. Understand `Product`, `Supplier`, and their JPA relationship.
3. Read `placeOrder()` and run the rollback/concurrency tests.
4. Read `InventoryTools`; explain why the model receives read-only tools instead of arbitrary SQL access.
5. Trace a PDF through `PdfIngestor`, `Retriever`, `PgVectorIndex`, and `AnswerService`.
6. Add your own evaluation cases; inspect errors instead of chasing a perfect score.

The Spring course you shared supports stages 1–3: Spring Boot, REST, JPA, React and tests. Spring AI tools, PDF processing, embeddings, vector retrieval and evaluation are additional learning topics.

## Common problems

| Problem | Fix |
|---|---|
| `Permission denied: ./mvnw` | Run `chmod +x mvnw`. |
| `release version 21 not supported` | Check `./mvnw -v` and install/select a full JDK 21, not just a JRE. |
| Blank/missing interface | Run `npm ci && npm run build` inside frontend, then restart Spring Boot. |
| Port 8080 is already used | Stop the other app, or pass `-Dspring-boot.run.arguments=--server.port=8081` and open port 8081. |
| PostgreSQL connection refused | Open Docker Desktop; run `docker compose ps`; wait for PostgreSQL to be healthy. |
| AI is not enabled | Restart Spring Boot with `-Dspring-boot.run.profiles=postgres,ai`. |
| Model not found / 502 | Check `ollama list`, Ollama server status, and the configured model names. |
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

Use your existing `-Dspring-boot.run.profiles=postgres,ai` flag when restarting if that is the configuration you were using. For Vite development mode, the frontend source changes reload through `npm run dev`.

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

