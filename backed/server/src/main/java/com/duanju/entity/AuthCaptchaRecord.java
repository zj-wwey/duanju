package com.duanju.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("auth_captcha_record")
public class AuthCaptchaRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("captcha_id")
    private String captchaId;

    private String scene;

    private String receiver;

    @TableField("captcha_hash")
    private String captchaHash;

    @TableField("fail_count")
    private Integer failCount;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("verified_at")
    private LocalDateTime verifiedAt;

    @TableField("request_ip")
    private String requestIp;

    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
