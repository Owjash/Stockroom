# Validation record

## Verified in this build

- React production build passed.
- Maven verification/package build passed with Java 21.
- **18 automated tests passed; 1 optional PostgreSQL/pgvector test was skipped.** See `validation/test-summary.json`.
- Inventory tests cover sale/receipt calculations, rollback, concurrent last-unit sales, duplicate order lines, supplier references, SKU uniqueness, API validation and oversell errors.
- Document tests cover actual PDF upload/extraction, page references, scope isolation, invalid/blank PDFs, deletion and evaluation labels.
- Mock-model tests cover accepted citations, rejection of invented citations and abstention when no passages are found. These do not test model intelligence.
- Browser checks verified dashboard totals, creating/searching a product, loading the sample policy, asking a document question, viewing a source passage and running the evaluation screen.

## Observed baseline, not a resume benchmark

During the browser check, the keyword baseline on the **fictional 3-page sample policy** achieved:

- 8/9 expected source pages found (88.9% source-page hit rate @3).
- 2/3 unsupported questions rejected by the retrieval threshold (66.7%).
- Median retrieval time of 1 ms in that small, warm, in-memory preview environment.

This is a tiny hand-authored fixture, not a representative corpus or an AI-quality result. The failures are intentionally visible: wording such as `recorded` versus `record` can defeat the simple keyword baseline, and overlapping words can retrieve irrelevant passages. Do not generalize these values or put them on a resume as real-world RAG performance. Build an independent evaluation set and measure the semantic system on your machine.

## Not verified here

- Live Ollama chat/embedding model responses and end-to-end generated-answer quality.
- Live PostgreSQL/pgvector behavior (Docker/PostgreSQL were unavailable in the build environment). An opt-in real database test is included.
- Mobile-device browser rendering, hosted deployment, multi-user authentication or production load.

The local run guide includes the steps to verify the model and database integrations. The app is a local single-workspace prototype; it is not presented as production-ready.

## Visual update

The React frontend was restyled with a charcoal sidebar, light-gray canvas, rounded panels, lime/lavender charts and a dark sales card. The production frontend and executable package builds passed. Browser checks verified global inventory search, stock-alert navigation, low-stock filtering and the 7/30-day sales selector. Inventory and sales visualizations use actual API records. The backend was unchanged, so the earlier backend test results remain the applicable checks.
