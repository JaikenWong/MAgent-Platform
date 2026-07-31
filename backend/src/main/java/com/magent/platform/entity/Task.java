package com.magent.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("a2a_tasks")
public class Task {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String conversationId;
    private String contextId;
    private String assignedAgentId;
    private String parentTaskId;

    /** submitted | working | input_required | completed | failed | canceled */
    private String status;
    /** JSONB: A2A Messages 历史 */
    private String messageHistory;
    /** JSONB: 产出物 */
    private String artifacts;
    private String errorDetail;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}