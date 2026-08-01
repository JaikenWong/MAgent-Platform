# MAgent-Platform

多智能体协同平台: 把 Dify Agent 包装成 A2A Server, 通过 [Google A2A Protocol](https://a2a-protocol.org) 实现跨 Agent 感知/协作, 飞书为用户入口, 敏感操作走管理员审批 (Human-in-the-Loop).

完整架构见 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## 技术栈

| 层 | 选型 |
|---|---|
| 协议 | Google A2A Protocol v1.0 (JSON-RPC 2.0 over HTTP) |
| Backend | Java 17 / Spring Boot 3.2 / MyBatis-Plus / Flyway |
| DB | PostgreSQL 16 + Redis 7 |
| Frontend | React 18 + TypeScript + Vite + Ant Design 5 (Hallmark 反 AI-slop 设计) |
| 部署 | Docker Compose + Traefik (可选) |

---

## 快速开始

```bash
# 1. 复制环境变量
cp .env.example .env

# 2. 一键起依赖 (postgres + redis)
make up

# 3. 起全栈 (含 backend/frontend)
make up-full

# 4. 看日志
make logs
```

访问:
- Frontend: http://localhost:5173  (默认 admin / admin123)
- Backend API: http://localhost:8080/api/v1
- Traefik dashboard: http://localhost:8081 (仅 `--profile full`)

---

## 开发

| 命令 | 说明 |
|---|---|
| `make build-be` | `mvn package -DskipTests` |
| `make build-fe` | `npm run build` |
| `make lint` | 后端编译 + 前端 eslint/tsc |
| `make test` | 前后端测试 |
| `make migrate` | Flyway 迁移 |
| `make psql` | 进 PostgreSQL shell |
| `make redis-cli` | 进 Redis shell |

---

## 项目结构

```
MAgent-Platform/
├── backend/        # Spring Boot multi-module Maven
├── frontend/       # Vite + React + AntD
├── docs/           # ARCHITECTURE.md
└── docker-compose.yml
```

详见 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) 第 9 节.

---

## Roadmap

- [x] Phase 0 - 全栈脚手架
- [x] Phase 1 - Agent Wrapper (Dify -> A2A Server)
- [x] Phase 2 - Orchestrator (规则 + LLM 编排, 四模式执行)
- [x] Phase 3 - 飞书接入 (oapi-sdk 长连接, 非 webhook)
- [x] Phase 4 - 审批 HITL (飞书卡片按钮闭环 + 超时)
- [x] Phase 5 - 完善 (Dashboard/对话/审计/设置/测试)
- [ ] 端到端验证 (真飞书 + Dify 环境)

---

## License

MIT