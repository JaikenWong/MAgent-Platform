# MAgent-Platform 架构规划

> 多智能体协同平台: 把 Dify 上的 Agent 包装成 A2A Server, 通过 A2A 协议实现 Agent 间感知/对话/协作, 飞书作为用户入口, 关键操作支持管理员审批 (人机协同).

---

## 1. 系统定位

| 维度 | 说明 |
|---|---|
| 协议 | Google A2A Protocol v1.0 (JSON-RPC 2.0 over HTTP) |
| Agent 来源 | Dify Workflow/Chatflow (通过 Dify REST API 包装成 A2A Server) |
| 用户入口 | 飞书机器人 (多 Bot, 统一 Gateway) |
| 协作模式 | Orchestrator 编排 + Agent 间 A2A SendMessage 互调 |
| 人机协同 | A2A `TASK_STATE_INPUT_REQUIRED` 挂起任务, 管理员审批后恢复 |
| 可配置性 | 全栈管理界面, Agent/Bot/规则/审批 全部可视化配置 |

---

## 2. 核心架构

```
                          ┌─────────────────────────┐
                          │   飞书用户 (群聊/私聊)    │
                          └────────────┬─────────────┘
                                       │ Event
                          ┌────────────▼─────────────┐
│   Feishu Gateway          │  Spring Boot
                           │   - 多 Bot 事件路由        │  统一 webhook
                           │   - 消息加密/校验          │
                           └────────────┬─────────────┘
                                        │
                           ┌────────────▼─────────────┐
                           │   Orchestrator            │  Java 自研
                           │   1. LLM 意图解析          │  基于 a2a-java-sdk
                           │   2. Agent Card 发现       │
                          │   3. Task 规划/委派        │
                          │   4. 审批拦截 (HITL)       │
                          │   5. 结果聚合 → 回飞书      │
                          └─┬──────┬──────┬───────────┘
            A2A SendMessage │      │      │  Agent 间也能互调
                  ┌─────────▼┐ ┌───▼────┐ ┌▼─────────┐
                  │ Dify     │ │ Dify   │ │ Dify     │
                  │ Agent A  │ │ Agent B│ │ Agent C  │
                  │ A2A Srv  │ │ A2A Srv│ │ A2A Srv  │  每个 Agent
                  └────┬─────┘ └────────┘ └──────────┘  独立进程
                       │ Dify REST API
                       ▼
                  ┌─────────────┐
                  │   Dify 平台   │  外部, 已有
                  └─────────────┘

         ┌───────────────────────────────────────────┐
         │            PostgreSQL + Redis              │
         │  Agent/Bot/规则/对话/Task/审批 全持久化       │
         └───────────────────────────────────────────┘

         ┌───────────────────────────────────────────┐
         │            管理前端 (React + AntD)           │
         │  Dashboard / Agent / Bot / 规则 / 审批队列     │
         └───────────────────────────────────────────┘
```

### 数据流: 典型协同场景

用户: "帮我分析竞品 X 的定价, 然后写一份内部建议书"

1. 飞书 Gateway 收消息 → Orchestrator
2. Orchestrator LLM 解析: 意图 = [竞品分析 → 文档撰写], 需 Agent "研究员" + "撰稿人"
3. Orchestrator 通过 A2A 调 "研究员" Agent (SendMessage), Task `working`
4. "研究员" (Dify 内部调用搜索/爬虫) 返回竞品定价数据, Task `completed`, Artifact = 结构化数据
5. Orchestrator 拿 Artifact, 通过 A2A 调 "撰稿人", 入参含研究员 Artifact
6. "撰稿人" 产出建议书草稿 → 触发敏感操作 (要发邮件? 要外发?). Task 进入 `input_required`
7. 管理员在飞书卡片 / 前端审批队列 看到 → 批准
8. Task 恢复 `working` → `completed`, 结果回飞书

---

## 3. 技术栈

| 层 | 选型 | 理由 |
|---|---|---|
| A2A 协议 | Google A2A v1.0 | 行业标准, 25k★, Linux Foundation |
| A2A SDK | `a2a-java-sdk` (官方 Java SDK) | 官方维护, 827 commits, 支持 JSON-RPC/gRPC/REST, 自带 JPA Task Store |
| Backend | Java 17+ / Spring Boot 3.2 | 中国 Java 最主流, 学习成本低, 生态成熟 |
| ORM | MyBatis-Plus 3.5 | 中国 Java 首选, SQL 透明可控, 代码生成 |
| DB 迁移 | Flyway | Spring Boot 集成, SQL 版本化 |
| DB | PostgreSQL 16 | 主存储, JSONB 字段支持 |
| Cache/Queue | Redis 7 (Spring Data Redis / Lettuce) | Task 缓存, SSE pub/sub, 分布式锁 |
| 异步任务 | Spring Scheduling + Redis 延迟队列 | 替代 Celery, 免额外组件; 后续可升 Quartz/RabbitMQ |
| HTTP 客户端 | Spring 6 HTTP Interface / WebClient | 调 Dify + 飞书 + A2A 跨 Agent |
| LLM 调用 | OpenAI 兼容 (Spring AI 或 RestClient) | Orchestrator 意图解析, 支持 Dify/OpenAI/通义等 |
| 安全 | Spring Security + JWT | 鉴权, 字段加密 (AES via Spring Security Crypto) |
| WebSocket | Spring WebSocket (STOMP) | 前端审批角标 + 对话实时推送 |
| 构建 | Maven | 多模块, A2A SDK 已是 Maven |
| 前端 | React 18 + TypeScript + Vite | 主流 |
| 前端 UI | Ant Design 5 | 中后台标杆 |
| 前端状态 | TanStack Query + Zustand | 轻量 |
| 前端图表 | @ant-design/charts | Dashboard |
| 部署 | Docker Compose | 单机起步 |
| 反代 | Traefik | 自动 SSL, 路由 A2A endpoint |

---

## 4. 关键设计: 人机协同审批 (HITL)

### 4.1 为什么不另造状态机

A2A 协议 Task 状态机已含 `TASK_STATE_INPUT_REQUIRED`:
```
submitted → working → completed
                    → input_required → (admin approve) → working
                    → failed
                    → canceled
```
直接复用, 不引入额外状态框架.

### 4.2 审批触发

- **配置驱动**: 每个 Agent 在其 Agent Card 声明 `requires_approval` skills (如 "发邮件", "改数据库", "外发文件")
- Orchestrator 在调此类 skill 前先发 `approval_request` 给审批子系统
- 不阻塞: Task 转为 `input_required`, 飞书卡片即时推给管理员

### 4.3 审批渠道 (双通道)

1. **飞书互动卡片**: 卡片含 [批准] [拒绝] [修改参数] 按钮, 回调直接更新 Task
2. **前端审批队列**: 列表 + 详情, 支持批量审批, 留痕

### 4.4 审批策略 (可配置)

| 策略 | 行为 |
|---|---|
| `auto` | 预批准 (仅日志) |
| `notify` | 通知不阻塞 (异步抄送) |
| `require_one` | 任一管理员批准 |
| `require_quorum` | 需 N 人批准 (可配阈值) |
| `require_role` | 需特定角色 (如 "ops") |

存 `approval_policies` 表, 按规则匹配.

### 4.5 超时与 escalation

- 每个审批有 `timeout` (默认 30min)
- 超时: 按配置 `auto_reject` 或 escalation 升级通知
- Spring `@Scheduled` 定时检查审批超时

---

## 5. 模块划分

### Backend (`backend/` — Spring Boot 多模块 Maven)

| 模块 (package) | 职责 |
|---|---|
| `controller.v1` | REST API (Agent/Bot/规则/对话/审批/系统) |
| `controller.webhook` | 飞书 webhook + A2A 动态路由入口 |
| `config` | Spring 配置, 安全 (JWT), Redis, WebSocket, 异步线程池 |
| `entity` | MyBatis-Plus 实体 (`@TableName`) |
| `mapper` | MyBatis-Plus Mapper (BaseMapper) |
| `dto` | 请求/响应 DTO (records + Jakarta Validation) |
| `service.orchestrator` | 意图解析, Task 规划, 编排执行 |
| `service.a2a` | Dify → A2A Server 包装 (Agent Card + JSON-RPC handlers), A2A Client |
| `service.dify` | Dify REST API 封装 (HTTP Interface) |
| `service.feishu` | 飞书 SDK + 互动卡片 + 事件 |
| `service.approval` | 审批引擎 (策略匹配 + 飞书卡片 + 超时) |
| `service.llm` | LLM 抽象 (Spring AI / RestClient) |
| `task` | `@Scheduled` + `@Async` 异步任务 (推送, 超时检查) |
| `common` | 工具, 异常, 统一响应 |

### Frontend (`frontend/src/pages/`)

见第 8 节.

---

## 6. 数据库 Schema

### 核心 11 张表

```sql
-- 1. Agent 注册 (Dify → A2A 映射)
agents
  id            UUID PK
  name          VARCHAR(100)
  description   TEXT
  dify_base_url VARCHAR(255)         -- Dify 服务地址
  dify_app_id   VARCHAR(100)
  dify_api_key  VARCHAR(255)         -- 加密 (AES)
  skills        JSONB                 -- Agent Skill 列表 (A2A AgentCard.skills)
  capabilities  JSONB                 -- streaming/push 配置
  approval_skills JSONB              -- 需审批的 skill 名列表
  status        ENUM(active,inactive,error)
  last_health_at TIMESTAMP
  created_at / updated_at

-- 2. 飞书机器人
feishu_bots
  id              UUID PK
  name            VARCHAR(100)
  app_id          VARCHAR(100)
  app_secret      VARCHAR(255)       -- 加密
  verification_token VARCHAR(255)
  encrypt_key     VARCHAR(255)       -- 加密
  webhook_url     VARCHAR(255)
  bound_agent_id  UUID FK -> agents.id  -- 默认接待 Agent
  status          ENUM(active,inactive)
  created_at / updated_at

-- 3. 编排规则
orchestration_rules
  id              UUID PK
  name            VARCHAR(100)
  description     TEXT
  trigger_type    ENUM(keyword,regex,intent,manual,all)
  trigger_config  JSONB              -- {keywords:[...], intent:"...", regex:"..."}
  execution_mode  ENUM(sequential,parallel,conditional,debate,router)
  agent_chain     JSONB              -- [{agent_id, role, input_from}]
  fallback_agent_id UUID FK
  priority        INT                -- 多规则匹配时排序
  enabled         BOOLEAN
  created_at / updated_at

-- 4. 审批策略
approval_policies
  id              UUID PK
  name            VARCHAR(100)
  strategy        ENUM(auto,notify,require_one,require_quorum,require_role)
  quorum          INT                 -- require_quorum 用
  required_role   VARCHAR(50)
  timeout_seconds INT                 -- 默认 1800
  timeout_action  ENUM(auto_reject,escalate)
  escalation_channel JSONB           -- {feishu_chat_id, feishu_user_id}
  applies_to      JSONB              -- {agent_id?:, skill?:, skill_tag?:}
  enabled         BOOLEAN

-- 5. 对话
conversations
  id              UUID PK
  source          ENUM(feishu,web,api)
  external_chat_id VARCHAR(100)      -- 飞书 chat_id
  external_user_id VARCHAR(100)
  a2a_context_id  VARCHAR(100)       -- A2A context (跨 Task 共享)
  status          ENUM(active,completed,closed)
  created_at / updated_at

-- 6. 消息 (对话流)
messages
  id              UUID PK
  conversation_id UUID FK
  role            ENUM(user,agent,orchestrator,system)
  agent_id        UUID FK nullable
  parts           JSONB              -- A2A Part 数组 (text/file/data)
  created_at

-- 7. A2A Task
tasks
  id              VARCHAR(100) PK    -- A2A task id (UUID)
  conversation_id UUID FK
  context_id      VARCHAR(100)
  assigned_agent_id UUID FK
  parent_task_id  VARCHAR(100) nullable -- 父 Task (编排链)
  status          ENUM(submitted,working,input_required,completed,failed,canceled)
  message_history JSONB              -- A2A Messages 历史
  artifacts       JSONB              -- 产出物
  error_detail    TEXT
  created_at / updated_at / completed_at

-- 8. 审批记录
approvals
  id              UUID PK
  task_id         VARCHAR(100) FK
  policy_id       UUID FK
  requested_by    VARCHAR(100)       -- 触发 Agent 或 orchestrator
  skill_name      VARCHAR(100)
  payload         JSONB              -- 待审批的操作详情
  status          ENUM(pending,approved,rejected,expired)
  decision_by     VARCHAR(100) nullable
  decision_at     TIMESTAMP nullable
  decision_channel ENUM(feishu,web,timeout)
  comment         TEXT
  created_at

-- 9. 系统设置
system_settings
  id              UUID PK
  key             VARCHAR(100) UNIQUE
  value           JSONB
  description     TEXT
  updated_at

-- 10. 审计日志
audit_logs
  id              UUID PK
  actor_id        VARCHAR(100)
  action          VARCHAR(50)        -- create_agent, update_rule, approve...
  entity_type     VARCHAR(50)
  entity_id       VARCHAR(100)
  details         JSONB
  created_at

-- 11. 管理员 (前端登录)
admins
  id              UUID PK
  username        VARCHAR(50) UNIQUE
  password_hash   VARCHAR(255)
  role            ENUM(super_admin,approver,viewer)
  feishu_user_id  VARCHAR(100) nullable  -- 飞书审批关联
  created_at
```

---

## 7. API 设计

### 7.1 管理 API (`/api/v1/`, JWT 鉴权)

| 分组 | Method | Path |
|---|---|---|
| Agent | GET | `/agents` |
| Agent | POST | `/agents` |
| Agent | GET | `/agents/{id}` |
| Agent | PUT | `/agents/{id}` |
| Agent | DELETE | `/agents/{id}` |
| Agent | POST | `/agents/{id}/test` (健康检查 + 拉取真实 Agent Card) |
| Agent | GET | `/agents/{id}/card` (A2A Agent Card 预览) |
| Bot | GET/POST/PUT/DELETE | `/bots[/{id}]` |
| Bot | POST | `/bots/{id}/test` |
| Rule | GET/POST/PUT/DELETE | `/rules[/{id}]` |
| Policy | GET/POST/PUT/DELETE | `/approval-policies[/{id}]` |
| Conversation | GET | `/conversations` |
| Conversation | GET | `/conversations/{id}` |
| Conversation | GET | `/conversations/{id}/messages` |
| Conversation | GET | `/conversations/{id}/tasks` |
| Task | GET | `/tasks` |
| Task | GET | `/tasks/{id}` |
| Task | POST | `/tasks/{id}/cancel` |
| Approval | GET | `/approvals` (含 status 过滤) |
| Approval | POST | `/approvals/{id}/decide` |
| Approval | GET | `/approvals/pending/count` |
| Dashboard | GET | `/dashboard/stats` |
| Setting | GET/PUT | `/settings` |
| Auth | POST | `/auth/login` |

### 7.2 飞书 Webhook

| Method | Path | 说明 |
|---|---|---|
| GET | `/webhook/feishu/{bot_id}` | URL 校验 (火山引擎 challenge) |
| POST | `/webhook/feishu/{bot_id}` | 消息/事件回调 |
| POST | `/webhook/feishu/card` | 互动卡片按钮回调 (审批用) |

### 7.3 A2A Protocol Endpoints (每个 Agent)

| Method | Path | 说明 |
|---|---|---|
| GET | `/a2a/{agent_id}/.well-known/agent-card.json` | Agent Card 发现 |
| POST | `/a2a/{agent_id}` | JSON-RPC: `message/send`, `tasks/get`, `tasks/list`, `tasks/cancel`, `tasks/subscribe` |
| POST | `/a2a/{agent_id}/stream` | JSON-RPC streaming (`message/stream` SSE) |

---

## 8. 前端页面 (React + Ant Design, Hallmark 设计哲学)

### 8.1 设计哲学: 反 AI-slop (参考 Nutlope/hallmark)

Hallmark (20k★) 是反 AI 千篇一律设计 skill. 我们前台照此哲学, **拒用 AntD 默认外观**, 做出有指纹的管理界面:

| Hallmark 原则 | 落到本平台 |
|---|---|
| 拒绝 on-distribution defaults | 不用 AntD 默认 `daybreak` 配色, 不用默认 Layout 头尾 |
| macrostructure first | 每页先定骨架, 不是堆 card; Dashboard ≠ Agents ≠ Approvals 视觉差别大 |
| type pairing ≠ system-ui | 主字体 IBM Plex Sans / Sora, 数字 JetBrains Mono, 不用 system-ui |
| color anchor 中心锚色 | 选一锚色 (如 #6F3FF5 紫调), 全局只此一条主链, 辅色不喧宾夺主 |
| 57 slop-test gates | 每页生成前自检: 居中 hero? 紫蓝渐变? Inter 默认? grid 三列平衡? → 命中即改 |
| 每页像量身定制 | Approvals 用队列视觉 (像 Linear), Agents 用网格画廊 (像 Vercel projects), Conversations 用三栏 (像 Supabase logs) |
| 自带 critique 二道门 | 生成后自评: "能一眼看出是 AI 写的吗?" 能 → 重做 |

**工具分工:**
- AntD 5 只當**原子组件**用: Table/Form/Drawer/Modal/Upload/Tooltip 这类功能件
- 外壳/布局/色彩/字体 全自定义, 不引默认 admin 模板
- 实际画页面时调用 `frontend-design` 或 `huashu-design` skill 生成, 而非手堆
- 配色 / 字体 / 间距 走设计 token (`@theme` CSS vars), 全局可换肤

### 8.2 页面路由

| 页面 | 路由 | 功能 | Hallmark macrostructure |
|---|---|---|---|
| 登录 | `/login` | JWT 登录 | 左右分栏: 左侧大插图/品牌叙事, 右侧单卡表单 |
| **Dashboard** | `/` | 统计 hero (Agent数/今日Task/待审批/飞书消息), 频次图, 最近对话 | 顶部巨数字 hero (不堆 4 个 card), 下方 7:3 split (图表/列表) |
| **Agent 管理** | `/agents` | 网格画廊; 表单含 Dify 配置/技能/审批技能标记; 测试连通; Agent Card 预览抽屉 | 网格画廊 (像 Vercel projects), 不可用表格 |
| **飞书机器人** | `/bots` | 列表 + 配置; Agent 绑定下拉; Webhook URL 复制; 测试推送 | 紧凑表格左侧 + 详情右侧 (Supabase 风) |
| **编排规则** | `/rules` | 列表; 触发条件配置; Agent 链 DAG 拖拽 | 列表 + flow 画布双区, 不折叠面板 |
| **审批策略** | `/approval-policies` | 策略 CRUD; 适用范围配置 | 简单表格 + 抽屉 |
| **审批队列** | `/approvals` | 待审批列表 (高亮); 详情抽屉; 批准/拒绝/备注; 历史 | 队列视觉 (像 Linear inbox), 不用普表 |
| **对话记录** | `/conversations` | 列表; 详情: 消息流时间线 + 内嵌 Task + Agent 调用链路图 | 三栏: 列表/详情/调用图 (像 Supabase logs) |
| **任务监控** | `/tasks` | 任务列表 (状态/Agent/耗时过滤); 详情: artifacts, 消息历史, 状态转换日志 | 表格 + 侧滑详情 (像 Sentry) |
| **系统设置** | `/settings` | LLM 配置; Dify 默认地址; 飞书默认; 通知开关 | 上下分节, 不分 4 卡 |
| **审计日志** | `/audit` | 操作流水查询 | 终端样式 monospace 表格, 像日志流 |

### 8.3 关键交互

- **审批队列** 顶栏常驻角标 (待审批数, WebSocket 实时推送), 数字跳动有微动效 (非 AntD 默认 Badge)
- **对话详情** 用时间线竖向 + Agent 调用链路横向小图 ( nesting 暗示 Task 层级 ), 不屏搬 AntD Timeline 默认
- **规则编辑** 用 `@ant-design/x` flow 或 reactflow 做 Agent 链拖拽 (MVP 可先用表单)
- **主题切换** 设计 token 化, 支持 light/dark + 一键换锚色 (Hallmark `study` 可提取别站 DNA 应用)

### 8.4 设计 Token 概要 (写到 `src/theme/tokens.ts`)

```ts
export const tokens = {
  color: {
    anchor:    '#6F3FF5',   // 唯一主色, 不申第二个主色
    ink:       '#0B0B0E',   // 文本, 不用纯黑
    surface:   '#FAFAFB',
    border:    '#E8E6EF',
    critical:  '#D64545',   // 拒绝
    ok:        '#22C55E',
    warn:      '#F59E0B'
  },
  font: {
    sans:  'IBM Plex Sans, -apple-system, sans-serif',
    mono:  'JetBrains Mono, ui-monospace, monospace',
    display: 'Sora, IBM Plex Sans'  // hero 数字用
  },
  radius: { sm: 6, md: 10, lg: 16 },    // 不用 AntD default 6/8
  space:  { 1:4,2:8,3:12,4:16,5:24,6:32,8:48 }
} as const
```

rev: 拒 `rgb(99,102,241)` 紫蓝渐变, 拒 `Inter` 默认, 拒居中 hero 三件套, 拒 3 列等宽 card 网格

---

## 9. 项目结构

```
MAgent-Platform/
├── backend/
│   ├── app/
│   │   ├── __init__.py
│   │   ├── main.py                      # FastAPI 入口
│   │   ├── api/
│   │   │   ├── deps.py                  # 依赖注入
│   │   │   └── v1/
│   │   │       ├── __init__.py
│   │   │       ├── router.py
│   │   │       ├── auth.py
│   │   │       ├── agents.py
│   │   │       ├── bots.py
│   │   │       ├── rules.py
│   │   │       ├── policies.py
│   │   │       ├── conversations.py
│   │   │       ├── tasks.py
│   │   │       ├── approvals.py
│   │   │       ├── dashboard.py
│   │   │       ├── settings.py
│   │   │       └── audit.py
│   │   ├── core/
│   │   │   ├── config.py                # pydantic-settings
│   │   │   ├── security.py              # JWT, 密码哈希, 字段加密
│   │   │   ├── database.py
│   │   │   ├── redis.py
│   │   │   └── logging.py
│   │   ├── models/
│   │   │   ├── __init__.py
│   │   │   ├── base.py                  # DeclarativeBase
│   │   │   ├── agent.py
│   │   │   ├── bot.py
│   │   │   ├── rule.py
│   │   │   ├── policy.py
│   │   │   ├── conversation.py
│   │   │   ├── message.py
│   │   │   ├── task.py
│   │   │   ├── approval.py
│   │   │   ├── setting.py
│   │   │   ├── audit.py
│   │   │   └── admin.py
│   │   ├── schemas/
│   │   │   ├── agent.py
│   │   │   ├── bot.py
│   │   │   ├── rule.py
│   │   │   ├── policy.py
│   │   │   ├── conversation.py
│   │   │   ├── task.py
│   │   │   ├── approval.py
│   │   │   └── common.py
│   │   ├── services/
│   │   │   ├── orchestrator/
│   │   │   │   ├── planner.py          # LLM 意图解析 + 规则匹配 → 执行计划
│   │   │   │   ├── executor.py         # 执行 Task 链 (顺/并/条件)
│   │   │   │   └── aggregator.py       # 结果聚合 → 回复
│   │   │   ├── a2a/
│   │   │   │   ├── server.py            # Dify Agent 包成 A2A Server
│   │   │   │   ├── agent_card.py        # Agent Card 生成
│   │   │   │   ├── task_manager.py      # Task 状态机 + Redis 持久化
│   │   │   │   └── client.py            # 调其他 Agent 的 A2A Client
│   │   │   ├── dify/
│   │   │   │   └── client.py            # Dify REST API 封装
│   │   │   ├── feishu/
│   │   │   │   ├── client.py            # 飞书开放平台 SDK 封装
│   │   │   │   ├── cards.py             # 互动卡片模板 (审批/结果展示)
│   │   │   │   └── gateway.py           # 事件入口 + 路由
│   │   │   ├── approval/
│   │   │   │   ├── engine.py            # 策略匹配 → 创建审批
│   │   │   │   ├── notifier.py          # 飞书卡片 + WebSocket 推前端
│   │   │   │   └── timeout.py           # Celery 超时处理
│   │   │   └── llm/
│   │   │       └── provider.py          # OpenAI 兼容抽象
│   │   ├── tasks/                       # Celery 任务
│   │   │   ├── celery_app.py
│   │   │   ├── approval_tasks.py
│   │   │   └── feishu_tasks.py
│   │   └── a2a_hosts.py                 # 动态挂载各 Agent 的 A2A Server 路由
│   ├── alembic/
│   │   ├── env.py
│   │   └── versions/
│   ├── tests/
│   ├── requirements.txt
│   ├── alembic.ini
│   └── Dockerfile
├── frontend/
│   ├── src/
│   │   ├── main.tsx
│   │   ├── App.tsx
│   │   ├── router.tsx
│   │   ├── api/                         # axios + TanStack Query hooks
│   │   ├── components/                  # 通用组件
│   │   ├── layouts/                     # AdminLayout (侧栏 + 审批角标)
│   │   ├── pages/
│   │   │   ├── Login/
│   │   │   ├── Dashboard/
│   │   │   ├── Agents/
│   │   │   ├── Bots/
│   │   │   ├── Rules/
│   │   │   ├── ApprovalPolicies/
│   │   │   ├── Approvals/               # 审批队列
│   │   │   ├── Conversations/
│   │   │   ├── Tasks/
│   │   │   ├── Settings/
│   │   │   └── Audit/
│   │   ├── stores/                      # Zustand
│   │   └── types/
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   └── Dockerfile
├── docker-compose.yml                   # postgres + redis + backend + frontend + traefik
├── .env.example
├── Makefile                             # make dev / make migrate / make seed
└── README.md
```

---

## 10. 开发阶段

### Phase 0 — 脚手架 (本次执行)
- [x] Docker Compose: postgres + redis + backend + frontend (+ traefik 可选)
- [x] Backend: Java 17 / Spring Boot 3 / MyBatis-Plus / Flyway 起骨架, JWT 鉴权
- [x] DB: 全 11 表 Flyway migration (V1__init_schema.sql), seed (V2)
- [x] Frontend: Vite + React + AntD + 路由 + AdminLayout + Login + Dashboard 空壳
- [x] .env.example, Makefile, README, AGENTS.md, docs/ARCHITECTURE.md
- [x] 验证: `mvn compile` / `mvn test` / `npm run build` / `npm run lint` / `npm run typecheck` 全通过
- [ ] 端到端 Docker (需本地启动 OrbStack: `make up-full`)

### Phase 1 — Agent Wrapper (把 Dify 包成 A2A Server)
- [x] `dify_client`: Dify REST API (chat/workflow, blocking + streaming SSE)
- [x] `a2a_server`: Agent Card 生成 + JSON-RPC handlers (send/get/list/cancel)
- [x] SSE streaming (`message/stream`) + `tasks/subscribe`
- [x] `a2a_hosts`: 动态路由 `/a2a/{agent_id}` + 公开 Agent Card
- [x] 管理端 Agent CRUD + AES 加密存储 + 测试连通 + Card 预览抽屉
- [x] 前端 Agents 页: 网格画廊 + Dify 表单 + 测试连通 + Card 预览

### Phase 2 - Orchestrator
- [x] `planner`: LLM 意图解析 (Spring AI ChatClient) + 规则匹配 -> ExecutionPlan
- [x] `executor`: 顺/并/条件/路由 四模式, 通过 A2A Client 调各 Agent
- [x] `aggregator`: 结果聚合 (单 Agent 直返 / 多 Agent LLM 合成)
- [x] 管理端规则 CRUD (CrudController); DAG 可视化未做 (MVP 表单)

### Phase 3 - 飞书接入
- [x] `feishu/long_connection`: oapi-sdk ws.Client 长连接接收事件 (替代 webhook), 多 Bot
- [x] `feishu/gateway`: 事件 -> Orchestrator -> 回复; 对话/消息持久化
- [x] `feishu/client`: 消息发送 + 审批互动卡片 (带按钮)
- [x] `feishu/crypto`: 加密回调解密 + 签名校验
- [ ] 端到端: 飞书消息 -> 编排 -> 多 Agent -> 飞书回复 (未真环境验证)

### Phase 4 - 审批 (HITL)
- [x] `approval/engine`: 策略匹配 (auto/notify/require_one/quorum/require_role), 创建 `approvals`, Task 转 `input_required`
- [x] `approval/notifier`: 飞书互动卡片 (带批准/拒绝按钮) + 前端 WebSocket 推送
- [x] `approval/timeout`: `@Scheduled` 定时 (auto_reject/escalate)
- [x] 前端审批队列 + 顶栏角标 (STOMP) + 飞书卡片按钮回调闭环
- [ ] 端到端: 敏感操作 -> 挂起 -> 双渠道审批 -> 恢复 (未真环境验证)

### Phase 5 - 完善
- [x] Dashboard 统计 (JdbcTemplate SQL) + 分布图
- [x] 对话/Task 列表 + 详情 (调用链路图未做)
- [x] 审计日志查询 (AuditService 记录)
- [x] 系统设置页
- [x] 测试 (JUnit 25 + Vitest 3)
- [x] 文档 (CLAUDE.md/AGENTS.md/ARCHITECTURE.md/README)
- [ ] 端到端 Docker 部署验证

---

## 11. 关键技术决策

| 决策 | 选择 | 备选 | 理由 |
|---|---|---|---|
| A2A 协议 | 手写 JSON-RPC (Java) | a2a-java-sdk / a2a-sdk (Python) | 协议简单, 不引 SDK 自控; 原 Python 计划已废弃 |
| 编排器 | 自研 Java (Spring AI) | LangGraph | 避免依赖链; A2A Task 即编排抽象, HITL 原生支持 |
| HITL 状态机 | A2A `input_required` | 自造审批表 | 协议原生, Agent 不用感知审批存在 |
| 审批触发 | Agent Card `approval_skills` | 规则匹配 | Agent 自声明更灵活, 规则可覆盖 |
| 前端图表 | @ant-design/charts | ECharts | 与 AntD 设计一致 |
| 反代 | Traefik | Nginx | 自动 SSL, 声明式路由, A2A 多 endpoint 路由方便 |
| LLM provider | OpenAI 兼容 (Dify/通义/自建) | 锁死一家 | Orchestrator 解析意图用, 可配 |
| 加密字段 | AES-256 (app_secret/api_key 等) | 明文 | 安全 |
| WebSocket | 前端审批角标 + 对话实时 | 轮询 | 体验 |

---

## 12. 风险与缓解

| 风险 | 缓解 |
|---|---|
| A2A 协议变更 | 手写 JSON-RPC 集中在 A2AServerService/A2AHostController, 协议简单可控 |
| Dify API 变化 | `dify_client` 集中封装, 版本锁 |
| 飞书卡片回调延迟 | 审批链路异步化, WebSocket 推前端兜底 |
| 长任务飞书超时 | SSE 流式更新卡片 + A2A Push Notification |
| 多 Agent 死循环 | Task 链深度限制 (配置项, 默认 5); 循环检测 |

---

下一步: Phase 1 — 把 Dify Workflow/Chatflow 包成 A2A Server.