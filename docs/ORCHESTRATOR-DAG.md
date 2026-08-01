# Orchestrator DAG 编排升级方案

> 目标：让 Orchestrator 支持**任意依赖图（DAG）**编排 —— 一个 agent 完成 → 分叉给多个 agent 并行处理（各自依赖上游结果）→ 汇合点等所有依赖完成再合并给下游。
> 约束：**完全兼容现有 4 种执行模式（sequential/parallel/conditional/router）**，现有规则数据与前端零改动。

---

## 1. 现状与痛点

### 现有编排模型

`ExecutionPlan` = `record(executionMode, List<Stage> stages, reasoning)`。
`Stage` = `record(agentId, agentName, inputFrom, description, order, condition)`。

`ExecutorService.execute()` 按 `executionMode` 硬编码分发 4 种拓扑：

| mode | 行为 | 局限 |
|---|---|---|
| `sequential` | A→B→C，`inputFrom=previous` 喂上一节点 | 一条线，无分支 |
| `parallel` | 所有 stage 并发，全吃用户输入 | 无法表达"B/C 依赖 A 完成后并行" |
| `conditional` | 线性 + condition 跳过 | 同 sequential |
| `router` | 只执行第一个 stage | 单选 |

### 做不到的场景（本方案要解决的）

```
调研 ──┬→ 分析师A ┐
       └→ 分析师B ┘→ 汇总（依赖 A 和 B 都完成）
```

- A、B **都依赖调研**，但**彼此独立可并发**
- 汇合点"汇总"要**等 A、B 都完成**，且**接收两者输出**

现状：串行会退化为 调研→A→B→汇总（B 白等 A）；并行让 A、B 吃不到调研结果。**两者都表达不了分叉+汇合。**

---

## 2. 设计

### 2.1 数据结构：`Stage` 加依赖字段

`backend/src/main/java/com/magent/platform/dto/orchestrator/Stage.java`

```java
public record Stage(
    String stageId,           // 新增：节点唯一 id（默认 = UUID，或 agentId+index）
    String agentId,
    String agentName,
    String inputFrom,         // 兼容保留："user" | "previous"
    List<String> dependsOn,   // 新增：依赖哪些 stageId；非空时优先于 inputFrom
    String description,
    int order,
    String condition
) {
    // 保留旧 5 参便利构造器（向后兼容所有现有调用）：
    public Stage(String agentId, String agentName, String inputFrom, String description, int order) {
        this(UUID.randomUUID().toString(), agentId, agentName, inputFrom, null, description, order, null);
    }
}
```

**输入解析优先级**：
1. `dependsOn` 非空 → 从 `dependsOn` 各节点输出取数据
2. 否则 `inputFrom=previous` → 取编排序列上一个 stage 输出（= 旧行为）
3. 否则 `inputFrom=user` → 用 `description`（用户原始输入）

### 2.2 执行器：switch(mode) → 拓扑分层执行

`backend/src/main/java/com/magent/platform/service/orchestrator/ExecutorService.java`

删除 `execute()` 里的 `switch(executionMode)` 及 `executeSequential/executeParallel/executeConditional/executeRouter` 四个方法，替换为：

```java
public List<Map<String, String>> execute(ExecutionPlan plan, String contextId) {
    Map<String, String> outputs = new HashMap<>();   // stageId -> 节点文本输出
    for (List<Stage> layer : topoSort(plan.stages())) {   // 依赖分层
        layer.parallelStream().forEach(stage -> {         // 同层并发
            if (!evaluateCondition(stage.condition(), outputs)) return;  // 条件跳过
            String input = resolveInput(stage, outputs);
            // ── 审批横切不变 ──
            String skill = checkApprovalNeeded(agent, stage);
            if (skill != null) { /* requestApproval + 挂起，逻辑不动 */ }
            // ── 调 agent（逻辑不动）──
            outputs.put(stage.stageId(), runAgent(stage, input, contextId));
        });
    }
    return orderByStage(plan, outputs);
}
```

**`topoSort`（DAG 分层 + 环检测）**：

```java
private List<List<Stage>> topoSort(List<Stage> stages) {
    Map<String, Stage> byId = stages.stream().collect(toMap(Stage::stageId, s -> s));
    Map<String, Integer> indegree = ...;   // 每节点未满足的依赖数
    Queue<Stage> ready = stages.stream().filter(s -> deps(s).isEmpty()).collect(...);
    List<List<Stage>> layers = new ArrayList<>();
    while (!ready.isEmpty()) {
        List<Stage> layer = ready.stream().toList();
        layers.add(layer);
        for (Stage s : layer) for (String down : dependents.get(s.stageId())) {
            if (--indegree[down] == 0) ready.add(byId.get(down));
        }
    }
    if (layers.stream().mapToInt(List::size).sum() < stages.size())
        throw new BizException("编排存在循环依赖");   // Kahn 剩节点 = 环
    return layers;
}
```

**`resolveInput`（单依赖 / 汇合）**：

```java
private String resolveInput(Stage stage, Map<String, String> outputs) {
    List<String> deps = nonNull(dependsOf(stage));
    if (deps.isEmpty()) return stage.description();            // user
    if (deps.size() == 1) return outputs.get(deps.get(0));     // 单依赖：直传
    // 汇合：拼成结构化 JSON {stageId: output} 传下游
    return om.writeValueAsString(deps.stream().collect(toMap(d -> d, outputs::get)));
}
```

### 2.3 Planner：四种 mode 降级为"自动填 dependsOn"

`backend/src/main/java/com/magent/platform/service/orchestrator/PlannerService.java`，`buildPlanFromRule()` / LLM fallback 生成 Stage 时按 mode 填依赖，**行为与现在完全一致**：

```java
switch (mode) {
    case "sequential"  -> stages[i] = withDepends(stages[i], List.of(stages[i-1].id)); // 链
    case "parallel"    -> stages[i] = withDepends(stages[i], List.of());                 // 全独立
    case "conditional" -> stages[i] = withDepends(stages[i], List.of(stages[i-1].id));   // 链 + 手动 condition
    case "router"      -> stages = List.of(stages.get(0));                                // 单选
}
```

规则表 `agent_chain` JSONB 保持现有格式，但**支持新增可选字段**，启用 DAG：
```json
[
  {"agentId":"research-1","role":"research","inputFrom":"user"},
  {"agentId":"analyst-a","role":"analysis","dependsOn":["research-1"]},
  {"agentId":"analyst-b","role":"analysis","dependsOn":["research-1"]},
  {"agentId":"summary","role":"summarize","dependsOn":["analyst-a","analyst-b"]}
]
```
> `inputFrom` 与 `dependsOn` 都缺省时：第一个 stage=`user`，其余=`previous`（旧语义）。

### 2.4 LLM：支持输出 DAG

`backend/src/main/java/com/magent/platform/service/llm/LLMService.java`，prompt（`buildSystemPrompt`）加 DAG 示例，`parsePlan()` 支持 stages 带 `id`/`dependsOn`：

```json
{
  "mode": "custom",
  "reasoning": "先调研, 两路分析并行, 再汇总",
  "stages": [
    {"id":"research","agentId":"research-1","dependsOn":[]},
    {"id":"analyst-a","agentId":"analyst-a","dependsOn":["research"]},
    {"id":"analyst-b","agentId":"analyst-b","dependsOn":["research"]},
    {"id":"summary","agentId":"summary-1","dependsOn":["analyst-a","analyst-b"]}
  ]
}
```

解析规则：`id` 缺省自动生成；`dependsOn` 缺省沿用 mode 语义；未知 `mode` 一律按 `custom`（纯 dependsOn）处理。

### 2.5 审批横切（不变）

每个 stage 调 agent 前 `checkApprovalNeeded(agent, stage)`（现有逻辑，`ExecutorService` 内），命中挂起**整个 DAG**，批准后继续剩余层。`createApprovalTask` 返回真实 taskId（已修复）。

---

## 3. 实施步骤（按顺序执行，每步可独立验收）

### Step 1：`Stage` 加字段（波及面最大，先做）

**文件**：
- `backend/src/main/java/com/magent/platform/dto/orchestrator/Stage.java`（改 record）
- 全仓所有 `new Stage(...)` 调用点编译修（`PlannerService`、`LLMService`、`ExecutorService`、测试）

**做法**：按 2.1 改 record，保留旧便利构造器。旧 5 参调用不变；新增参数通过新的全参构造器使用。

**验收**：`mvn -B -f backend/pom.xml compile` 通过，现有测试全绿。

### Step 2：Executor 拓扑化

**文件**：`backend/src/main/java/com/magent/platform/service/orchestrator/ExecutorService.java`

**做法**：按 2.2 删 switch + 4 个 executeXxx 方法，实现 `topoSort` / `resolveInput` / 通用 `runAgent`。`checkApprovalNeeded`、`evaluateCondition`、`createApprovalTask`、`loadAgent`、`extractTaskOutput`、`resultMap` 全部保留。返回 `List<Map<String,String>>`（agentId/agentName/output，按 stage.order 排序）契约不变，`AggregatorService` 不受影响。

**验收**：
- 现有 4 模式行为不变：`ExecutorServiceTest` 现有 4 个测试全绿
- 新增测试：A→(B,C 并行)→D 汇合，验证执行顺序 + D 收到 B、C 拼接输入

### Step 3：Planner 自动填 dependsOn

**文件**：`backend/src/main/java/com/magent/platform/service/orchestrator/PlannerService.java`

**做法**：按 2.3，`buildPlanFromRule()` 与 LLM fallback 生成 stages 后按 mode 填 dependsOn（helper `withDepends`）。解析 agent_chain 时支持 `dependsOn` 字段。

**验收**：`PlannerServiceTest` 现有测试全绿 + 新增"agent_chain 带 dependsOn 生成 DAG stages"用例。

### Step 4：LLM 支持 DAG

**文件**：`backend/src/main/java/com/magent/platform/service/llm/LLMService.java`

**做法**：按 2.4，`buildSystemPrompt` 加 DAG 示例，`parsePlan` 解析 `id`/`dependsOn`。

**验收**：解析 LLM 返回的 DAG JSON 生成正确 stages（含 dependsOn）。

### Step 5：环检测 + 测试补全

**文件**：`ExecutorService.topoSort`（已含 Kahn 环检测）、`backend/src/test/java/com/magent/platform/service/orchestrator/ExecutorServiceTest.java`

**新增测试用例**：
1. 并行汇合：`[A(deps=[]), B(deps=[A]), C(deps=[A]), D(deps=[B,C])]` → 断言层序 A → (B,C 并发) → D
2. 环检测：`[A(deps=[B]), B(deps=[A])]` → 断言抛 BizException
3. 单依赖直传：B 输入 == A 输出
4. 汇合拼接：D 输入含 B、C 两个 stageId

**验收**：`mvn -B -f backend/pom.xml test` 全部通过（现有 25 + 新增）。

### Step 6：前端 Rules 页配置依赖（可选）

**文件**：`frontend/src/pages/Rules.tsx`、`frontend/src/api/rules.ts`、类型文件

**做法**：agent_chain 编辑器允许给每步填 `dependsOn`（下拉多选前面 step）或手输 `input_from: "stage:xxx"`。MVP 用表单，不做 reactflow 拖拽。

**验收**：`npm run typecheck` + `npm run test -- --run` 通过。

---

## 4. 风险与注意

| 风险 | 缓解 |
|---|---|
| `Stage` 是核心 record，改动波及 6+ 文件 | Step 1 保留旧构造器，编译期全部暴露，逐文件修 |
| 现有 4 模式回归 | 每步跑 `ExecutorServiceTest`，mode 自动填 dependsOn 语义与旧行为逐条对照 |
| 汇合输入格式变化 | 单依赖直传文本（不变），多依赖才 JSON 拼接；下游 agent 感知不到（只是文本变长） |
| 环导致死循环 | `topoSort` Kahn 算法检测，抛 `BizException` |
| parallel 旧语义（全独立） | 保持不变：parallel 的 dependsOn 全空，效果等同现在 |
| A2A 层无改动 | 本方案只动 orchestrator 三层 + DTO，`A2AClientService`/`A2AServerService`/Dify 不动 |

---

## 5. 验收总标准

- `mvn -B -f backend/pom.xml test` 全绿（现有 25 + 新增 DAG 用例）
- `npm run typecheck` + `npm run test -- --run` 全绿
- 端到端（真环境）：配"调研→双分析→汇总"规则，飞书触发，日志显示调研先完成 → A、B 并发 → 汇总收到两者输出

## 6. 关键文件索引

| 文件 | 改动 |
|---|---|
| `backend/.../dto/orchestrator/Stage.java` | 加 `stageId` + `dependsOn`，保留旧构造器 |
| `backend/.../service/orchestrator/ExecutorService.java` | switch→topoSort 分层并发执行 |
| `backend/.../service/orchestrator/PlannerService.java` | mode→自动填 dependsOn |
| `backend/.../service/llm/LLMService.java` | prompt + parsePlan 支持 DAG |
| `backend/src/test/.../orchestrator/ExecutorServiceTest.java` | DAG 用例 + 环检测 |
| `frontend/src/pages/Rules.tsx` | 配置 dependsOn（可选 Step 6） |
