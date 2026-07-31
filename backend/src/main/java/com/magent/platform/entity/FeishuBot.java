package com.magent.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("feishu_bots")
public class FeishuBot extends BaseEntity {

    private String name;
    private String appId;
    /** AES 加密 */
    private String appSecret;
    /** AES 加密 */
    private String verificationToken;
    /** AES 加密 */
    private String encryptKey;
    private String webhookUrl;
    /** 默认接待 Agent */
    private String boundAgentId;
    /** active | inactive */
    private String status;
}