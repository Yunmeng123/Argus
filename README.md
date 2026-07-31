# Argus — Self-hosted AI code review

Argus is a self-hosted AI code review platform built with **Spring Boot 3, Spring AI, and Vue 3**. Review a pasted unified diff or a local Git repository, or connect GitHub, GitLab, and Gitee webhooks to review pull requests automatically. Argus can publish line-level findings and a Markdown summary back to the code host.

![Animated Argus review workflow](docs/demo/argus-demo.svg)

> The demo uses an animated SVG instead of a binary GIF, allowing GitHub and patch-based PR tools to render and review it without binary-file support.

> **Try it without an API key:** the Docker Compose setup starts in deterministic mock mode, so the complete workflow is available immediately.

![Argus architecture](docs/architecture.jpg)

## Why Argus?

| Area | Capability |
| --- | --- |
| Review inputs | Web UI, local Git repositories, GitHub/GitLab/Gitee webhooks, and MCP tools |
| Review quality | Finder–Verifier false-positive filtering, changed-line validation, and Java method context |
| Delivery | Line-level PR/MR comments, Markdown summaries, dashboard records, and DingTalk/WeCom notifications |
| Measurement | 0–100 quality score, token accounting, public evaluation fixtures, and statistics dashboard |
| Operations | Runtime configuration, masked secrets, access token, daily token budget, persistent H2 and RabbitMQ |
| Developer insight | Per-author score trends, issue categories, skill tags, and AI-generated coaching summaries |

## Quick start with Docker

Requirements: Docker Engine with the Compose plugin.

```bash
git clone <repository-url>
cd Argus
docker compose up --build -d
```

Open **<http://localhost:18080>**. RabbitMQ management is available at <http://localhost:15672> (`argus` / `argus` for local use).

Compose enables `ARGUS_LLM_MOCK=true` by default, so no model key is required. To use a real OpenAI-compatible provider:

```bash
ARGUS_LLM_MOCK=false \
ARGUS_LLM_BASE_URL=https://api.deepseek.com \
ARGUS_LLM_API_KEY=replace-me \
ARGUS_LLM_MODEL=deepseek-chat \
  docker compose up --build -d
```

Stop the stack while retaining data:

```bash
docker compose down
```

Delete all local Argus, report, and RabbitMQ volumes:

```bash
docker compose down --volumes
```

> Replace the default RabbitMQ credentials and set `ARGUS_ACCESS_TOKEN` before exposing Argus to a network.

## How it works

```text
Web UI / local Git / VCS webhook / MCP
                  │
                  ▼
       Parse and filter unified diff
                  │
                  ▼
       Add Java method-level context
                  │
                  ▼
       Finder reviews files in parallel
                  │
                  ▼
       Verifier challenges false positives
                  │
                  ▼
  Validate changed lines, score, and persist
          ┌───────┼────────┐
          ▼       ▼        ▼
      Web UI   PR comments  IM notification
```

Webhook requests return `202 Accepted`; RabbitMQ processes the review asynchronously. A persisted review record deduplicates the same commit. Failed messages retry three times and then move to a dead-letter queue.

## Public evaluation

The repository contains an inspectable four-case smoke dataset covering SQL injection, null handling, resource management, and clean code. The reproducible built-in mock baseline currently reports **0% recall and 0% precision**. This result verifies the evaluation pipeline—it is explicitly **not a claim about real-model quality**.

See the [methodology, limitations, and reproduction commands](docs/evaluation.md) and the [machine-readable result](docs/evaluation-results/mock-2026-07-30.json). Run it yourself after startup:

```bash
curl --fail --request POST http://localhost:18080/api/eval/run
```

## Configuration

Runtime settings can be changed in the **System configuration** page and are persisted to `data/argus-config.json`. Environment variables seed the initial configuration.

| Environment variable | Default | Description |
| --- | --- | --- |
| `ARGUS_LLM_MOCK` | `false` (`true` in Compose) | Use the deterministic reviewer without calling a model |
| `ARGUS_LLM_BASE_URL` | `https://api.deepseek.com` | OpenAI-compatible base URL; do not append `/v1` |
| `ARGUS_LLM_API_KEY` | empty | Model provider API key |
| `ARGUS_LLM_MODEL` | `deepseek-chat` | Finder model |
| `ARGUS_LLM_VERIFIER_MODEL` | empty | Verifier model; empty uses the Finder model |
| `ARGUS_ACCESS_TOKEN` | empty | Protect `/api/**` and `/sse` with `X-Argus-Token` |
| `ARGUS_RABBITMQ_HOST` | `localhost` | RabbitMQ host |
| `ARGUS_RABBITMQ_PORT` | `5672` | RabbitMQ AMQP port |
| `ARGUS_RABBITMQ_USERNAME` | `argus` | RabbitMQ username |
| `ARGUS_RABBITMQ_PASSWORD` | `argus` | RabbitMQ password |
| `ARGUS_GITLAB_BASE_URL` | empty | GitLab instance URL |
| `ARGUS_GITLAB_TOKEN` | empty | GitLab API token |
| `ARGUS_GITLAB_WEBHOOK_SECRET` | empty | GitLab webhook secret |
| `ARGUS_GITHUB_TOKEN` | empty | GitHub token |
| `ARGUS_GITHUB_WEBHOOK_SECRET` | empty | GitHub webhook secret |
| `ARGUS_GITEE_TOKEN` | empty | Gitee token |
| `ARGUS_GITEE_WEBHOOK_SECRET` | empty | Gitee webhook secret |
| `ARGUS_NOTIFY_WEBHOOK_URL` | empty | DingTalk or WeCom bot webhook |

Persistent paths are `data/` for H2/configuration and `reports/` for Markdown reports. Compose stores both in named volumes.

## VCS integration

Configure the provider token and secret, then register this endpoint:

```text
http://<argus-host>:18080/api/webhook/{platform}
```

| Provider | `platform` | Event | Verification | Result delivery |
| --- | --- | --- | --- | --- |
| GitHub | `github` | Pull requests | `X-Hub-Signature-256` HMAC-SHA256 | Line comments + summary |
| GitLab | `gitlab` | Merge request events | `X-Gitlab-Token` | Line comments + summary |
| Gitee | `gitee` | Pull Request | `X-Gitee-Token` | Summary (line comments planned) |

## MCP

Argus exposes an MCP SSE endpoint at `http://localhost:18080/sse`. Example client configuration:

```json
{
  "mcpServers": {
    "argus": {
      "url": "http://localhost:18080/sse"
    }
  }
}
```

The `argus_review_diff` tool accepts a Git unified diff and returns a structured result. Send `X-Argus-Token` when access control is enabled.

## Local development

Requirements: Java 17+, Maven 3.6+, Node.js 18+, npm 9+, Git, and RabbitMQ 3.8+.

```bash
# Terminal 1: infrastructure and backend
docker compose up -d rabbitmq
mvn spring-boot:run

# Terminal 2: Vite development server
cd web
npm ci
npm run dev
```

Open <http://localhost:5173>; Vite proxies `/api` to port `18080`. For a production-style local build:

```bash
cd web && npm ci && npm run build && cd ..
mvn clean package
java -jar target/argus-0.1.0-SNAPSHOT.jar
```

## API overview

| Method | Endpoint | Description |
| --- | --- | --- |
| `POST` | `/api/review/jobs` | Submit an asynchronous diff or local-repository review |
| `GET` | `/api/review/jobs/{id}` | Read job progress/result |
| `POST` | `/api/review/diff` | Review a unified diff synchronously |
| `POST` | `/api/review/local` | Review a local repository synchronously |
| `POST` | `/api/webhook/{platform}` | Receive a VCS webhook |
| `GET` | `/api/reviews`, `/api/reviews/{id}` | List/read persisted reviews |
| `GET` | `/api/authors`, `/api/authors/{author}` | List/read developer profiles |
| `GET` | `/api/stats` | Read dashboard statistics |
| `POST` | `/api/eval/run` | Run the bundled evaluation dataset |
| `GET`, `PUT` | `/api/config` | Read/update runtime configuration |
| `GET` | `/sse` | MCP Server SSE endpoint |

## Testing

```bash
mvn test
cd web && npm ci && npm run build
```

## Release and roadmap

- [v0.1.0 release notes](docs/releases/v0.1.0.md)
- [Product roadmap](docs/product/roadmap.md)
- [Reliable review queue PRD](docs/product/prd-reliable-review-queue.md)
- [Reliable review queue test plan](docs/testing/reliable-review-queue-test-plan.md)
- [Architecture skill](docs/skills/argus-architecture/SKILL.md)
- [Code implementation skill](docs/skills/argus-code/SKILL.md)

### Current limitations

- The public dataset has only four cases and is a smoke/regression suite, not a production benchmark.
- Gitee delivery currently falls back to a summary comment.
- The default embedded H2 database is intended for a simple single-instance deployment.

## License

No license file is currently included. Until one is added, normal copyright restrictions apply.
