package com.magent.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("audit_logs")
public class AuditLog {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String actorId;
    private String action;
    private String entityType;
    private String entityId;
    /** JSONB */
    private String details;
    private LocalDateTime createdAt;
}