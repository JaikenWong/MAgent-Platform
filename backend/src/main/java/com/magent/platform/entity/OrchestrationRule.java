package com.magent.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("orchestration_rules")
public class OrchestrationRule extends BaseEntity {

    private String name;
    private String description;

    /** keyword | regex | intent | manual | all */
    private String triggerType;
    /** JSONB: {keywords:[...], intent:"...", regex:"..."} */
    private String triggerConfig;

    /** sequential | parallel | conditional | debate | router */
    private String executionMode;
    /** JSONB: [{agentId, role, inputFrom}] */
    private String agentChain;

    private String fallbackAgentId;
    private Integer priority;
    private Boolean enabled;
}