package com.magent.platform.service.dify;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** Dify 阻塞模式返回.
 *  Chat: answer + conversationId
 *  Workflow: outputs + workflowRunId
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DifyBlockingResult(
    String answer,
    String conversationId,
    String messageId,
    String taskId,
    String workflowRunId,
    Map<String, Object> outputs,
    String error
) {}