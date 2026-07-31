package com.magent.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("system_settings")
public class SystemSetting {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String key;
    /** JSONB */
    private String value;
    private String description;
    private LocalDateTime updatedAt;
}