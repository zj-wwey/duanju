package com.duanju.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_invite")
public class UserInvite {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("inviter_id")
    private Long inviterId;

    @TableField("invitee_id")
    private Long inviteeId;

    private String status;

    @TableField("rewarded_at")
    private LocalDateTime rewardedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;
}