package com.duanju.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("app_user")
public class AppUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String phone;

    @TableField("phone_verified")
    private Integer phoneVerified;

    @TableField("auth_provider")
    private String authProvider;

    @TableField("password_hash")
    private String passwordHash;

    private String nickname;

    @TableField("avatar_url")
    private String avatarUrl;

    private Integer points;

    @TableField("notice_enabled")
    private Integer noticeEnabled;

    @TableField("auto_next_enabled")
    private Integer autoNextEnabled;

    @TableField("last_token_jti")
    private String lastTokenJti;

    @TableField("refresh_token_jti")
    private String refreshTokenJti;

    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    @TableField("last_login_ip")
    private String lastLoginIp;

    @TableField("total_spent_cents")
    private Integer totalSpentCents;

    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
