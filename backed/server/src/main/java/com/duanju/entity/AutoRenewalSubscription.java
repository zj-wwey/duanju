package com.duanju.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("auto_renewal_subscription")
public class AutoRenewalSubscription {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("product_id")
    private Long productId;

    @TableField("pay_channel")
    private String payChannel;

    @TableField("pay_method_token")
    private String payMethodToken;

    @TableField("next_charge_at")
    private LocalDateTime nextChargeAt;

    private String status;

    @TableField("last_charge_order_no")
    private String lastChargeOrderNo;

    @TableField("fail_count")
    private Integer failCount;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}