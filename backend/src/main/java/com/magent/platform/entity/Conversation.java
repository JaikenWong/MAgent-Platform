package com.magent.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("conversations")
public class Conversation extends BaseEntity {

    /** feishu | web | api */
    private String source;
    private String externalChatId;
    private String externalUserId;
    /** A2A contextId, 跨 Task 共享 */
    private String a2aContextId;
    /** active | completed | closed */
    private String status;
    private LocalDateTime closedAt;
}