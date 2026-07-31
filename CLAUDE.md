# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Quick reference

`AGENTS.md` has full conventions, architecture, and coding rules — read it first for any non-trivial task. This file covers only what AGENTS.md doesn't: discrepancies, gotchas, and cross-file insight.

## Architecture reality check

`docs/ARCHITECTURE.md` §9 shows a **Python/FastAPI** directory tree (FastAPI, Alembic, Celery, `requirements.txt`). That was the original plan. The actual codebase is **Java 17 / Spring Boot 3.2 / MyBatis-Plus**. When reading ARCHITECTURE.md for design intent (A2A protocol flow, HITL state machine, DB schema, API routes, frontend pages), trust it. Ignore the Python file paths in §9 — the real backend is:

```
backend/src/main/java/com/magent/platform/
  controller/v1/        — 13 REST controllers (Agent, Bot, Rule, Policy, Approval, Task, Conversation, Audit, Dashboard, Setting, Auth, Orchestrator, CrudController)
  controller/webhook/   — FeishuWebhookController, A2AHostController
  service/              — a2a/ (5 classes), dify/ (4 classes), orchestrator/ (3 classes), approval/ (2 classes), feishu/ (2 classes), llm/ (1 class), AuthService
  entity/               — 11 JPA entities matching ARCHITECTURE.md §6 tables
  mapper/               — 11 MyBatis-Plus BaseMapper interfaces
  dto/                  — Java records (a2a/ sub-package for A2A protocol types)
  common/               — R<T>, BizException, GlobalExceptionHandler, JwtUtil, JwtAuthenticationFilter, CryptoUtil (AES), AdminInitializer
  config/               — SecurityConfig, MyBatisPlusConfig, RedisConfig, WebSocketConfig, AsyncConfig
```

## Service wiring status

Most service classes exist but are **not wired to controllers**. The scaffold has:
- Entities + Mappers + Flyway migrations (V1 schema, V2 seed): **complete**
- Controllers (REST endpoints): **declared but mostly return stubs**
- A2A/Dify/Orchestrator/Feishu services: **class skeletons exist, logic incomplete**
- Auth (JWT login): **working** (AuthController → AuthService → AdminMapper)
- Frontend pages: **all 11 pages exist as files, most are placeholders**

When adding a feature, check if the service class already exists before creating a new one.

## Key gotchas

- **`.env` is mandatory** — docker-compose and Makefile `psql` target read it. Missing `.env` causes opaque failures.
- **Frontend tests fail without `--passWithNoTests`** — vitest is configured but zero test files exist. Running `npm run test` or `make test` will error with "no test files found".
- **`resources/mapper/` XML dir is empty** — MyBatis-Plus mapper XML goes here if you need complex SQL. Simple CRUD uses BaseMapper annotations, no XML needed.
- **Frontend routes live in `App.tsx`**, not a separate router file. Use `lazy()` + `Protected` wrapper pattern.
- **ARCHITECTURE.md is the source of truth** for design decisions (A2A state machine reuse for HITL, approval strategies, Hallmark design philosophy). Code should follow its intent even though the implementation language differs.
- **Spring Milestones repository** is in `pom.xml` — required for Spring AI 0.8.1 dependency.
- **`make up` must run before** backend tests or `mvn spring-boot:run` — tests hit real PostgreSQL.

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

Pages must follow Hallmark anti-AI-slop philosophy (ARCHITECTURE.md §8). Use `frontend-design` or `huashu-design` skills when building UI. AntD components are allowed only as atomic widgets (Table, Form, Modal, etc.) — never use default AntD layout/colors/typography. Design tokens live in `frontend/src/theme/tokens.ts`.
