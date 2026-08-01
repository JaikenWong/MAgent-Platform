# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Quick reference

`AGENTS.md` has full conventions, architecture, and coding rules - read it first for any non-trivial task. This file covers only what AGENTS.md doesn't: discrepancies, gotchas, and cross-file insight.

## Architecture reality check

`docs/ARCHITECTURE.md` §9 shows a **Python/FastAPI** directory tree (FastAPI, Alembic, Celery, `requirements.txt`). That was the original plan. The actual codebase is **Java 17 / Spring Boot 3.2 / MyBatis-Plus**. Trust ARCHITECTURE.md for design intent (A2A protocol flow, HITL state machine, DB schema, API routes, frontend pages); ignore its §9/§11/§12 Python paths and SDK claims - the real backend is:

```
backend/src/main/java/com/magent/platform/
  controller/v1/        - 13 REST controllers (Agent, Bot, Rule, Policy, Approval, Task,
                          Conversation, Audit, Dashboard, Setting, Auth, Orchestrator, CrudController)
  controller/webhook/   - FeishuWebhookController (card callbacks only), A2AHostController
  service/              - a2a/ (5), dify/ (4), orchestrator/ (3), approval/ (Engine, Notifier,
                          TimeoutService), feishu/ (Client, Gateway, LongConnectionService,
                          CryptoUtil), llm/ (1), audit/ (1), AuthService
  entity/ mapper/       - 11 entities + 11 BaseMapper interfaces (ARCHITECTURE.md §6 tables)
  dto/                  - Java records (a2a/ + orchestrator/ sub-packages)
  common/               - R<T>, BizException, GlobalExceptionHandler, JwtUtil,
                          JwtAuthenticationFilter, CryptoUtil (AES-GCM), AdminInitializer
  config/               - SecurityConfig, MyBatisPlusConfig, RedisConfig, WebSocketConfig, AsyncConfig
```

## Service wiring status

Phase 1-4 implemented and wired end-to-end **in code** (not yet validated against live Feishu/Dify):

- **A2A wrapper**: hand-written JSON-RPC (no A2A SDK). `A2AHostController` exposes `/a2a/{agentId}` +
  `/.well-known/agent-card.json` + `/stream` (SSE). `A2AServerService` bridges to `DifyClient`
  (blocking + streaming SSE), auto-detecting Chatflow vs Workflow by `difyAppId` prefix `chat-`.
- **Orchestrator**: `PlannerService` (rule match -> LLM intent -> fallback) produces `ExecutionPlan`;
  `ExecutorService` runs sequential/parallel/conditional/router with per-stage approval interception;
  `AggregatorService` merges (single -> passthrough, >2 stages -> LLM synthesis).
- **Feishu**: **long-connection mode** via oapi-sdk `ws.Client` (`FeishuLongConnectionService`).
  Webhook kept only for interactive card callbacks.
- **Approval HITL**: closed loop - `ApprovalEngine` creates approval + pushes Feishu card with
  approve/reject buttons + suspends task (`input_required`); card callback -> `FeishuGateway
  .handleCardCallback` -> `decide` resumes/cancels; `ApprovalTimeoutService` `@Scheduled` (60s)
  auto-rejects on timeout.
- **Encryption**: `Agent.difyApiKey`, `FeishuBot.appSecret/verificationToken/encryptKey` AES-GCM
  encrypted at write (Agent/BotController), decrypted at read.
- **Persistence**: `FeishuGateway` finds/creates conversation by `external_chat_id`, stores
  user/agent/system messages.
- **Frontend**: all 11 pages implemented + STOMP WebSocket for the live approval badge.

Check whether a service class already exists before creating a new one.

## Key gotchas

- **`.env` is mandatory** - docker-compose and Makefile `psql` read it. Missing `.env` = opaque failures.
- **Feishu uses long-connection, NOT webhook** for events. A bot connects when
  `long_connection_enabled=true` (toggle: `POST /api/v1/bots/{id}/long-connection/{enable|disable}`,
  or the Bots page switch; auto-restored on `ApplicationReadyEvent`). `/webhook/feishu/card` exists
  only for approval card buttons.
- **Two different crypto classes, do not confuse**: `common/CryptoUtil` = AES-256-GCM for DB field
  encryption (ciphertext format `Base64(IV[12] || ct+tag)` - IV **must** be prepended, a missing IV
  was a real bug that broke every decrypt). `service/feishu/FeishuCryptoUtil` = Feishu's own
  AES-256-CBC event decrypt + SHA-256 signature verify.
- **`resources/mapper/` XML dir is empty** - add MyBatis-Plus XML there only for complex SQL.
- **Frontend routes live in `App.tsx`** (`lazy()` + `Protected` wrapper), there is no `router.tsx`.
- **Spring Milestones repo** in `pom.xml` is required for Spring AI 0.8.1. LLM config is bridged in
  `application.yml` as `spring.ai.openai.*` (fed by `LLM_*` env vars) - `magent.llm.*` alone is not
  read by Spring AI.
- **`make up` must run before** backend tests or `mvn spring-boot:run`.

## Essential commands

```bash
# Prerequisites (once)
cp .env.example .env
make up                                  # postgres + redis

# Backend
mvn -q -B -f backend/pom.xml compile     # lint gate (compile == lint)
mvn -q -B -f backend/pom.xml test        # all tests (26)
mvn -B -f backend/pom.xml test -Dtest=ExecutorServiceTest              # single class
mvn -B -f backend/pom.xml test -Dtest=ExecutorServiceTest#methodName   # single method
cd backend && mvn spring-boot:run        # dev server (needs `make up`)

# Frontend
cd frontend && npm run dev               # http://localhost:5173
cd frontend && npm run typecheck         # tsc --noEmit
cd frontend && npm run test -- --run     # vitest (3 tests)
cd frontend && npm run test -- --run src/pages/__tests__/Agents.test.tsx   # single file

# Full check before committing
make lint && make test
```

Backend tests are plain JUnit + Mockito (no Spring context, no DB) - `MockRestServiceServer` for
HTTP, mocked mappers elsewhere. They run without `make up`; only `spring-boot:run` needs it.

## Planned work

`docs/ORCHESTRATOR-DAG.md` is an approved, not-yet-implemented plan to upgrade the orchestrator from
4 fixed topologies to a real DAG (`Stage.dependsOn` + topological layered execution, keeping the 4
modes as sugar). Read it before touching `Stage` / `ExecutorService` / `PlannerService`.

## Frontend design constraint

Pages follow the Hallmark anti-AI-slop philosophy (ARCHITECTURE.md §8). Use the `frontend-design` or
`huashu-design` skills when building UI. AntD is allowed only as atomic widgets (Table, Form, Modal);
never use default AntD layout/colors/typography. Design tokens live in `frontend/src/theme/tokens.ts`.
