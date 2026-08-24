package com.duanju.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("point_expire")
public class PointExpire {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("point_type")
    private String pointType;

    @TableField("total_points")
    private Integer totalPoints;

    private Integer remaining;

    @TableField("expire_at")
    private LocalDate expireAt;

    @TableField("created_at")
    private LocalDateTime createdAt;
}