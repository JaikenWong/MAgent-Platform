package com.magent.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("messages")
public class Message {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String conversationId;
    /** user | agent | orchestrator | system */
    private String role;
    private String agentId;
    /** JSONB: A2A Part 数组 (text/file/data) */
    private String parts;
    private LocalDateTime createdAt;
}