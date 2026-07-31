# Public evaluation results

Argus includes a small, auditable smoke-test dataset in [`samples/eval/`](../samples/eval/). It covers SQL injection, null handling, resource management, and a clean change. The manifest contains **4 cases and 4 expected findings**.

## Published baseline

| Run | Mode | Verifier | Recall | Precision | Purpose |
| --- | --- | --- | ---: | ---: | --- |
| `mock-2026-07-30` | deterministic built-in mock | off | 0.0% | 0.0% | Reproducible pipeline smoke test |

The machine-readable response is published in [`evaluation-results/mock-2026-07-30.json`](evaluation-results/mock-2026-07-30.json). The mock reviewer intentionally emits a fixed example rather than trying to solve the dataset, so these numbers **must not be interpreted as model quality**. They demonstrate that dataset loading, matching, aggregation, persistence, and the public evaluation endpoint work end to end.

No current real-model score is claimed in this release. Model results are sensitive to provider revisions, configuration, and sampling; a future real-model baseline will be published only with the exact model identifier, configuration, date, raw endpoint response, and commit SHA.

## Reproduce

Start the public, no-key-required configuration:

```bash
docker compose up --build -d
curl --fail --request POST http://localhost:18080/api/eval/run
```

To evaluate a real OpenAI-compatible model:

```bash
ARGUS_LLM_MOCK=false \
ARGUS_LLM_BASE_URL=https://api.example.com \
ARGUS_LLM_API_KEY=replace-me \
ARGUS_LLM_MODEL=your-model \
docker compose up --build -d

curl --fail --request POST http://localhost:18080/api/eval/run \
  | tee docs/evaluation-results/real-model-YYYY-MM-DD.json
```

## Method

- An expected finding matches on file path and within ±2 lines; category is also matched when the fixture specifies one.
- Recall is matched expected findings divided by all expected findings.
- Precision is matched emitted findings divided by all emitted findings.
- A clean case penalizes precision when the reviewer reports a false positive.
- This tiny suite is a regression/smoke suite, not evidence of general production accuracy.

The implementation is in `EvalService`; fixtures and expected labels are public so results can be independently inspected.
