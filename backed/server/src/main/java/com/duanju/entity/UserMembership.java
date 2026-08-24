package com.duanju.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("user_membership")
public class UserMembership {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    private String level;

    @TableField("level_source")
    private String levelSource;

    @TableField("spent_upgrade_at")
    private LocalDateTime spentUpgradeAt;

    @TableField("purchase_level")
    private String purchaseLevel;

    @TableField("purchase_expire_at")
    private LocalDateTime purchaseExpireAt;

    @TableField("daily_free_used")
    private Integer dailyFreeUsed;

    @TableField("daily_free_reset_at")
    private LocalDate dailyFreeResetAt;

    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}