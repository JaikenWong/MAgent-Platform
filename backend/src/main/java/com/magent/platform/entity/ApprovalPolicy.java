package com.magent.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("approval_policies")
public class ApprovalPolicy extends BaseEntity {

    private String name;

    /** auto | notify | require_one | require_quorum | require_role */
    private String strategy;
    private Integer quorum;
    private String requiredRole;

    private Integer timeoutSeconds;
    /** auto_reject | escalate */
    private String timeoutAction;
    /** JSONB: {feishu_chat_id, feishu_user_id} */
    private String escalationChannel;

    /** JSONB: {agentId?, skill?, skillTag?} */
    private String appliesTo;
    private Boolean enabled;
}