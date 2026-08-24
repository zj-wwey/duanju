package com.duanju.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ad_reward_record")
public class AdRewardRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("ad_slot")
    private String adSlot;

    @TableField("trace_id")
    private String traceId;

    @TableField("point_delta")
    private Integer pointDelta;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
