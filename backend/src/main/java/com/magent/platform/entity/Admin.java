package com.magent.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("admins")
public class Admin {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String username;
    private String passwordHash;
    /** super_admin | approver | viewer */
    private String role;
    private String feishuUserId;
    private LocalDateTime createdAt;
}