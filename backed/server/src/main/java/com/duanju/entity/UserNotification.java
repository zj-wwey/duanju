package com.duanju.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_notification")
public class UserNotification {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    /** comment / reply / like / system */
    private String type;

    @TableField("source_user_id")
    private Long sourceUserId;

    @TableField("drama_id")
    private Long dramaId;

    @TableField("comment_id")
    private Long commentId;

    private String content;

    @TableField("is_read")
    private Boolean isRead;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
