# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Quick reference

`AGENTS.md` has full conventions, architecture, and coding rules - read it first for any non-trivial task. This file covers only what AGENTS.md doesn't: discrepancies, gotchas, and cross-file insight.

## Architecture reality check

`docs/ARCHITECTURE.md` §9 shows a **Python/FastAPI** directory tree (FastAPI, Alembic, Celery, `requirements.txt`). That was the original plan. The actual codebase is **Java 17 / Spring Boot 3.2 / MyBatis-Plus**. When reading ARCHITECTURE.md for design intent (A2A protocol flow, HITL state machine, DB schema, API routes, frontend pages), trust it. Ignore the Python file paths in §9 - the real backend is:

```
backend/src/main/java/com/magent/platform/
  controller/v1/        - 13 REST controllers (Agent, Bot, Rule, Policy, Approval, Task, Conversation, Audit, Dashboard, Setting, Auth, Orchestrator, CrudController)
  controller/webhook/   - FeishuWebhookController (card callbacks only), A2AHostController
  service/              - a2a/ (5), dify/ (4), orchestrator/ (3), approval/ (Engine, Notifier, TimeoutService), feishu/ (Client, Gateway, LongConnectionService, CryptoUtil), llm/ (1), audit/ (AuditService), AuthService
  entity/               - 11 JPA entities matching ARCHITECTURE.md §6 tables
  mapper/               - 11 MyBatis-Plus BaseMapper interfaces
  dto/                  - Java records (a2a/ sub-package for A2A protocol types)
  common/               - R<T>, BizException, GlobalExceptionHandler, JwtUtil, JwtAuthenticationFilter, CryptoUtil (AES), AdminInitializer
  config/               - SecurityConfig, MyBatisPlusConfig, RedisConfig, WebSocketConfig, AsyncConfig
```

## Service wiring status

Service layer is **wired and functional** (Phase 1-4 done):
- Entities + Mappers + Flyway (V1 schema, V2 seed, V3 feishu long_connection): **complete**
- Controllers: **all wired to real services** (Dashboard uses JdbcTemplate SQL; others delegate to services)
- A2A/Dify/Orchestrator/Feishu services: **implemented** - DifyClient (blocking+streaming SSE), A2AServerService (JSON-RPC hand-written, no A2A SDK), Planner (rule match + LLM intent), Executor (sequential/parallel/conditional/router), Aggregator, LLMService (Spring AI ChatClient)
- Feishu: **long-connection mode** via oapi-sdk `ws.Client` (`FeishuLongConnectionService`); HTTP webhook kept only for interactive card callbacks (`/webhook/feishu/card`)
- Approval HITL: **closed loop** - ApprovalEngine creates approval + pushes feishu card (approve/reject buttons) + suspends task; card callback -> `FeishuGateway.handleCardCallback` -> `decide` resumes/cancels; `ApprovalTimeoutService` `@Scheduled` auto-reject on timeout
- Sensitive fields (Agent.difyApiKey, FeishuBot.appSecret/verificationToken/encryptKey): **AES-encrypted** at write (AgentController/BotController), decrypted at read
- Conversation/message persistence: FeishuGateway creates/finds conversation by `external_chat_id`, stores user/agent/system messages
- Auth (JWT login): **working**
- Frontend: **all 11 pages implemented** + vitest (3 tests) + STOMP WebSocket for real-time approval badge

When adding a feature, check if the service class already exists before creating a new one.

## Key gotchas

- **`.env` is mandatory** - docker-compose and Makefile `psql` target read it. Missing `.env` causes opaque failures.
- **Feishu uses long-connection** (`FeishuLongConnectionService`, oapi-sdk `ws.Client`) - event intake over WebSocket, NOT webhook. A bot connects when `long_connection_enabled=true` (toggled via `POST /api/v1/bots/{id}/long-connection/{enable|disable}` or the Bots page switch; auto-restored on app start). Webhook `/webhook/feishu/card` is kept only for interactive card button callbacks (approval).
- **`resources/mapper/` XML dir is empty** - MyBatis-Plus mapper XML goes here if you need complex SQL. Simple CRUD uses BaseMapper annotations, no XML needed.
- **Frontend routes live in `App.tsx`**, not a separate router file. Use `lazy()` + `Protected` wrapper pattern.
- **ARCHITECTURE.md is the source of truth** for design decisions (A2A state machine reuse for HITL, approval strategies, Hallmark design philosophy), but its §9/§11/§12 still reference the original Python plan - the real backend is Java (A2A JSON-RPC hand-written, no A2A SDK; Feishu via `oapi-sdk`). Trust design intent, ignore Python paths/SDK claims there.
- **Spring Milestones repository** is in `pom.xml` - required for Spring AI 0.8.1 dependency.
- **`make up` must run before** backend tests or `mvn spring-boot:run` - tests hit real PostgreSQL.

## Essential commands

```bash
# Prerequisites (once)
cp .env.example .env
make up                    # postgres + redis

# Backend
cd backend && mvn -q -B compile        # lint gate
cd backend && mvn -q -B test           # run tests
cd backend && mvn spring-boot:run      # start dev server

# Frontend
cd frontend && npm run dev             # http://localhost:5173
cd frontend && npm run lint            # eslint
cd frontend && npm run typecheck       # tsc --noEmit

# Full check before committing
make lint && make test
```

## Frontend design constraint

Pages must follow Hallmark anti-AI-slop philosophy (ARCHITECTURE.md §8). Use `frontend-design` or `huashu-design` skills when building UI. AntD components are allowed only as atomic widgets (Table, Form, Modal, etc.) - never use default AntD layout/colors/typography. Design tokens live in `frontend/src/theme/tokens.ts`.
