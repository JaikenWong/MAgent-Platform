package com.magent.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("approvals")
public class Approval {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String taskId;
    private String policyId;
    private String requestedBy;
    private String skillName;
    /** JSONB: 待审批操作详情 */
    private String payload;

    /** pending | approved | rejected | expired */
    private String status;
    private String decisionBy;
    private LocalDateTime decisionAt;
    /** feishu | web | timeout */
    private String decisionChannel;
    private String comment;
    private LocalDateTime createdAt;
}