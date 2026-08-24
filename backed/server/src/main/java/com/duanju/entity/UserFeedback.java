package com.duanju.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_feedback")
public class UserFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("user_nickname")
    private String userNickname;

    @TableField("user_phone")
    private String userPhone;

    private String type;

    private String title;

    private String content;

    private String screenshots;

    private String contact;

    @TableField("device_info")
    private String deviceInfo;

    @TableField("app_version")
    private String appVersion;

    private String status;

    @TableField("handler_id")
    private Long handlerId;

    @TableField("handler_name")
    private String handlerName;

    private String reply;

    @TableField("replied_at")
    private LocalDateTime repliedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
