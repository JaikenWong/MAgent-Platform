# AGENTS.md — MAgent-Platform working conventions

## Project overview

Full-stack multi-agent collaboration platform. Wraps Dify Agents as Google A2A Protocol
v1.0 servers (JSON-RPC 2.0 over HTTP) so agents can discover/collaborate with each other;
Feishu is the user entry point; sensitive operations pause for admin approval (Human-in-the-Loop).
Admin UI for agents/bots/rules/approvals/audit lives in `frontend`.

- Backend: Java 17 / Spring Boot 3.2 / MyBatis-Plus 3.5.7 / Flyway / PostgreSQL 16 / Redis 7
- Frontend: React 18 + TypeScript (strict) + Vite + AntD 5 + TanStack Query + Zustand
- Deployment: Docker Compose (+ optional Traefik profile); Makefile at repo root
- Detailed design: `docs/ARCHITECTURE.md` (Chinese) — read before touching domain code

Current state: Phase 0 scaffolding only. A2A/orchestrator/Feishu service classes exist
(`backend/.../service/{a2a,dify,orchestrator,llm}`) but most are not yet wired to
controllers; roadmap in README.

## Commands

### Setup / environment

```bash
cp .env.example .env      # required — docker-compose and app read these vars
make up                   # postgres + redis (docker compose up -d)
make up-full              # full stack incl. backend/frontend/traefik (--profile full)
```

### Backend (Java / Maven)

```bash
cd backend && mvn -q -B compile                          # lint gate (compile == lint)
cd backend && mvn -q -B test                             # all tests
cd backend && mvn -q -B package -DskipTests              # package jar
cd backend && mvn spring-boot:run                        # run (needs `make up` first)
make migrate                                             # Flyway only (--magent.migrate-only=true)
make seed                                                # seed only (--magent.seed-only=true)
```

### Frontend (TypeScript / Vite)

```bash
cd frontend && npm install                               # or `npm ci`
cd frontend && npm run lint                              # eslint
cd frontend && npm run typecheck                         # tsc --noEmit
cd frontend && npm run build                             # tsc -b && vite build
cd frontend && npm run dev                               # http://localhost:5173
cd frontend && npm run test -- --run                     # vitest (no tests exist yet)
```

### Makefile shortcuts

- `make lint` = backend compile + frontend eslint/typecheck; `make test` = both test suites
- `make build` / `make build-be` / `make build-fe`; `make psql` / `make redis-cli` / `make logs` / `make clean`
- Rule: backend change → run `mvn -q -B compile`; frontend change → run `npm run typecheck`

## Architecture

```
Feishu user ──events──> FeishuWebhookController ─> orchestrator (Planner/Executor/Aggregator)
                                                          │ A2A SendMessage (JSON-RPC)
Admin UI (React) ──JWT REST /api/v1──> controllers ──> services ──> DB/Redis
A2A client ──/a2a──> A2AHostController ─> A2AServerService/TaskManagerService ─> DifyClient ─> Dify REST
                HITL: task -> TASK_STATE_INPUT_REQUIRED -> Approval -> resume
```

Backend layers (package root `com.magent.platform`):

- `controller/v1/` — REST endpoints under `/api/v1` (one per domain: Auth, Agent, Bot, Rule,
  Policy, Approval, Task, Conversation, Audit, Dashboard, Setting, Orchestrator)
- `controller/webhook/` — `FeishuWebhookController`, `A2AHostController` (external entry)
- `service/` — `auth`, `a2a` (server/client/task manager), `dify` (client + stream handler),
  `orchestrator` (planner/executor/aggregator), `llm`
- `dto/` — Java records: `a2a/` (A2A protocol types), `orchestrator/` (Stage, ExecutionPlan),
  plus `PageQuery`/`PageResult`/login DTOs
- `entity/` — MyBatis-Plus entities (Agent, FeishuBot, OrchestrationRule, Approval,
  ApprovalPolicy, Conversation, Task, Message, AuditLog, Admin, SystemSetting)
- `mapper/` — `BaseMapper<T>` interfaces; XML lives in `resources/mapper/` (dir exists, empty so far)
- `common/` — `R<T>` envelope, `BizException`, `GlobalExceptionHandler`, `JwtUtil`,
  `JwtAuthenticationFilter`, `CryptoUtil` (AES), `AdminInitializer`
- `config/` — `SecurityConfig`, `MyBatisPlusConfig`, `RedisConfig`, `WebSocketConfig`, `AsyncConfig`
- `resources/db/migration/` — Flyway `V1__init_schema.sql`, `V2__seed_data.sql`

Frontend (`frontend/src`):

- `api/` — axios instance + per-domain modules; all data access goes through here
- `pages/` — one file per route (Dashboard, Agents, Bots, Rules, ApprovalPolicies, Approvals,
  Conversations, Tasks, Settings, Audit, Login)
- `layouts/AdminLayout.tsx`, `stores/auth.ts` (Zustand), `theme/{tokens,antdTheme}.ts`
- `types/`, `hooks/`, `components/` — currently sparse; put shared UI there

Data flow: Feishu event → orchestrator plans a chain of A2A calls → each Agent wrapper
(Dify Chatflow) executes via Dify REST → results aggregated → replied back; sensitive steps
park the task and create an `Approval` row until an admin approves/rejects.

## Key files & directories

- `.env.example` — all env vars; copy to `.env` (JWT_SECRET ≥32 bytes, CRYPTO_AES_KEY 16/24/32 bytes,
  DEFAULT_ADMIN_*, DIFY_BASE_URL, LLM_*, FEISHU_BASE_URL, MAGENT_PUBLIC_BASE_URL)
- `docker-compose.yml` — services `postgres`/`redis` (always), `backend`/`frontend` (profile full)
- `backend/src/main/resources/application.yml` — datasource, Flyway (baseline-on-migrate,
  out-of-order false), MyBatis-Plus (uuid ids, logic-delete field `deleted`), `magent.*` business config
- `backend/pom.xml` — note `spring-milestones` repository is required for Spring AI 0.8.1
- `frontend/vite.config.ts` — alias `@/` → `src/`; dev proxy `/api`, `/ws`, `/webhook`, `/a2a` → `:8080`
- `frontend/tsconfig.json` — `strict: true`, `noUnusedLocals`, `noUnusedParameters`
- `docs/ARCHITECTURE.md` — the source of truth for design (A2A, HITL, Hallmark §8, DB schema §10)

## Coding conventions

### Backend

- Controllers are thin: `@RestController` + `@RequestMapping("/api/v1/...")`, delegate to services
- DTOs: Java records + Jakarta Validation annotations (`@Valid` in controllers)
- Responses: `R<T>` record (`code 0` = success); errors via `throw new BizException(...)`,
  caught by `GlobalExceptionHandler` → proper HTTP status + `R.fail`
- Entities: extend `BaseEntity` (id/created_at/updated_at), `@TableName`, MyBatis-Plus camel↔snake
- DB ids are `assign_uuid`; deletion is logical via `deleted` field
- Lombok `@RequiredArgsConstructor` for constructor injection; never field-inject
- Sensitive fields (Dify api_key, Feishu app_secret) must be encrypted — `CryptoUtil` (AES) exists;
  a `@FieldEncrypt` annotation is planned but NOT yet implemented
- Flyway migrations: `V{n}__snake_case_name.sql`, never edit applied migrations

### Frontend

- Function components + hooks only; strict TS, `any` is a lint **warning** — prefer proper types
- API calls only through `src/api/*` (TanStack Query hooks on top of the axios client);
  never import axios directly in pages
- The axios client unwraps `R<T>` (returns `data` on `code===0`) and auto-logouts on 401
- Global state: Zustand (`stores/`); no Context as global store
- Routes are declared inline in `src/App.tsx` with `lazy()` imports + `Protected` wrapper —
  there is NO `src/router.tsx`
- UI follows the Hallmark anti-AI-slop philosophy (ARCHITECTURE.md §8): AntD used only as atomic
  components, custom layout/typography/colors via `src/theme/tokens.ts`; use the
  `frontend-design` / `huashu-design` skills when building pages

## Git workflow

- Single commit (`first commit`) exists; no branch/commit conventions established yet —
  follow conventional style (`feat:`, `fix:`), keep subjects ≤50 chars

## CI/CD

- No CI configuration exists (no `.github/`, no other CI manifests). `make lint` and `make test`
  are the de-facto gates; run both before finishing a change.

## Tips for AI agents

- `make up` (postgres/redis) must be running before backend tests/`mvn spring-boot:run`
- Frontend tests: vitest is configured but **zero test files exist** — `npm run test` will fail
  with "no test files found" unless you add one or pass `--passWithNoTests`
- `resources/mapper/` XML dir is empty — if you add complex SQL, register it there
  (`mapper-locations: classpath*:/mapper/**/*.xml`)
- The service layer (a2a/dify/orchestrator/llm) is scaffolded but largely unwired — check
  controllers before assuming an endpoint exists
- README/ARCHITECTURE/docs are in Chinese; keep new docs consistent with that language
- `.env` is required by docker-compose (`psql` target reads it); missing `.env` falls back to `magent` defaults
- Vite proxies `/api`, `/ws`, `/webhook`, `/a2a` to backend — new external-facing routes should
  follow those prefixes
