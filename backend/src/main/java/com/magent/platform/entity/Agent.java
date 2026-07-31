package com.magent.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("agents")
public class Agent extends BaseEntity {

    private String name;
    private String description;

    private String difyBaseUrl;
    private String difyAppId;
    /** AES 加密 */
    private String difyApiKey;

    /** JSONB: AgentSkill 列表 (A2A AgentCard.skills) */
    private String skills;
    /** JSONB: streaming/push 配置 */
    private String capabilities;
    /** JSONB: 需审批 skill 名列表 */
    private String approvalSkills;

    /** active | inactive | error */
    private String status;
    private java.time.LocalDateTime lastHealthAt;
}