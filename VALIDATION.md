# Validation record

## Latest local validation — November 17, 2026

The maintainer ran the application and tests on macOS. The results below come from the supplied terminal output, application screenshots, and exported evaluation JSON. They are separate from the earlier preview/build checks.

The pushed source containing the fixes is [commit 26daf7c](https://github.com/Owjash/Stockroom/commit/26daf7c018d30f66eccb12ea685afe410a0d8327).

### Automated tests

Command:

```sh
./mvnw clean test
```

The Maven terminal summary reported **BUILD SUCCESS: 28 tests total, 27 passed, 1 skipped, zero failures, and zero errors**. The skipped test is the opt-in `PgVectorIntegrationTest`, enabled separately with `RUN_PGVECTOR_TESTS=true`.

The [test summary](validation/test-summary.json) is a manual record of that terminal result, not a Surefire report or a new CI run.

Coverage includes:

- Inventory API validation, unique SKUs, supplier references, sale/receipt calculations, oversell rejection, rollback, duplicate order lines, and concurrent last-unit sales.
- PDF extraction, page references, document scope, invalid or blank PDFs, document deletion, and evaluation labels.
- Retrieval math and scripted model-response guards: citation-label mapping, unknown labels, malformed UUID rejection, unsupported verdicts, fabricated or missing quotations, and invalid auditor responses.

The model-response tests use mocked responses. They check application behavior and do not establish the real model's semantic accuracy.

### Live document evaluation

The original export is preserved as [validation/evaluation-results.json](validation/evaluation-results.json). Model and setup details are recorded separately in [validation/evaluation-context.json](validation/evaluation-context.json), because the original export does not contain those settings.

| Setting | Recorded value |
|---|---|
| Chat model | `qwen2.5:7b` through Ollama |
| Embedding model | `nomic-embed-text` |
| Profiles | `postgres,ai` |
| Retrieval | Semantic, pgvector exact cosine search, top 3 |
| Chunk size / overlap | 1,200 / 180 characters, within individual PDF pages |
| Corpus for this run | One fictional Northstar supplier policy; 3 pages, 3 chunks |
| Cases | 12: 9 supported and 3 unsupported |
| Abstention basis | Model decision; **Include model abstention** enabled |
| Export timestamp | `2026-11-18T01:04:32.400601Z` — November 17 in America/Chicago |

| Metric | Result |
|---|---:|
| Expected source page among top-3 results | 9/9 supported questions |
| Supported questions with expected page retrieved and no abstention | 9/9 |
| Supported questions incorrectly refused | 0/9 |
| Unsupported questions refused | 3/3 |
| Rows passing the current retrieval/abstention checks | 12/12 |
| Mean retrieval time | 66.83 ms |
| Median retrieval time | 23.5 ms |
| p95 retrieval time | 346 ms |

The three unsupported questions ask for warranty duration, a bulk-purchase discount, and accepted payment currencies. The fixture does not establish these facts, and the model abstained on all three in this run.

### What the metrics mean

- **Source-page hit rate @3** checks retrieval alone. A supported case contributes when its expected page appears among retrieved passages.
- A supported **row passes** only when its expected page is retrieved and the answer pipeline does not abstain. This prevents a refused supported question from appearing as a successful answer.
- An unsupported row passes when the pipeline abstains. With model abstention disabled, this measures only the retrieval threshold.
- **Retrieval time** includes query embedding and search. It excludes generation and the evidence-audit model call. No total-response latency is established by this export.
- The export does not store generated answers or supporting quotations. It cannot establish factual answer correctness, completeness, or citation entailment.

These 12 questions were used to debug and tune the implementation. They are a development fixture, not a held-out benchmark. Top-3 retrieval over only three chunks also provides limited evidence about ranking on a larger corpus. Repeated uploads of the same sample do not increase the number of distinct documents evaluated.

### Manual application checks

Screenshots and database-tool evidence showed that recording a receipt of five Wireless Mouse units changed its stock from 10 to 15, total stock from 189 to 194, inventory value from $3,636.50 to $3,756.50, and the low-stock product count from three to two. These values persisted after restarting the application.

Live document checks showed page-linked answers for the minimum order quantity and return freight payer. After the citation-label change, the return-window question produced “30 calendar days” with page 2. The final evaluation export records no false abstentions among the nine supported sample questions.

These application flows exercised the local PostgreSQL/Ollama configuration. The separate automated pgvector integration test was still skipped in the recorded Maven run.

## Fixes covered by this validation

- Document service operations run in a transaction so PostgreSQL PDF large objects can be read within a transaction.
- Generator and auditor prompts use short, request-scoped source labels. Accepted labels map back to the original stored sources; unknown labels are rejected.
- Proposed answers undergo a model support audit plus literal supporting-quote validation. The auditor can still make semantic mistakes.
- Evaluation distinguishes source-page retrieval from supported-answer abstention. Model/service failures are errors, not successful refusals.

## Earlier build checks and remaining scope

Earlier checks passed the React production build, executable-package build, and browser core workflows, including global search, low-stock navigation, and the sales-period selector. Those builds were performed before the final citation fixes. The latest command above was `clean test`; it did not rebuild the frontend or package a new executable JAR.

The available evidence does not establish performance on independent PDFs, factual accuracy across a representative corpus, production load, multi-user access controls, hosted deployment, or physical mobile-device rendering. Future evaluations should use distinct documents and new questions, record false abstentions as well as unsupported answers, and review the actual generated answers against their source passages.
