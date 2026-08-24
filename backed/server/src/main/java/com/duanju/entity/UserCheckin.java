package com.duanju.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("user_checkin")
public class UserCheckin {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("checkin_date")
    private LocalDate checkinDate;

    @TableField("point_delta")
    private Integer pointDelta;

    @TableField("consecutive_days")
    private Integer consecutiveDays;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
