package com.magent.platform.service.dify;

import java.util.Map;

/**
 * Dify SSE 事件回调. 每个方法默认空, 子类只覆盖关心的.
 * Phase 1 主要是 onText / onWorkflowFinished / onError / onHumanInputRequired.
 */
public interface DifyStreamHandler {

    /** chat: `message` / `agent_message` 事件; workflow: `text_chunk` 事件. */
    default void onText(String text) {}

    /** workflow `node_finished`: 节点产出. */
    default void onNodeFinished(String nodeId, String status, Map<String, Object> outputs) {}

    /** chat/agent: `agent_thought`: 思考过程. */
    default void onAgentThought(String thought, String tool, String toolInput, String observation) {}

    /** 追加 reasoning chunk. */
    default void onReasoning(String reasoning, boolean isFinal) {}

    /** `workflow_paused` / `human_input_required`: 暂停等待人输入. */
    default void onHumanInputRequired(String formToken, String formContent, String nodeId, String expiration) {}

    /** 终态: workflow_finished. status: succeeded | failed | stopped | partial-succeeded | paused. */
    default void onWorkflowFinished(String status, Map<String, Object> outputs, String error) {}

    /** chat message_end (含 usage / retriever_resources). */
    default void onMessageEnd(Map<String, Object> metadata) {}

    /** inline error event (HTTP 200). */
    default void onError(String code, String message) {}
}