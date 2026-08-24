package com.duanju.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("currency_rate")
public class CurrencyRate {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("currency_code")
    private String currencyCode;

    @TableField("currency_name")
    private String currencyName;

    private String locale;

    @TableField("rate_to_usd")
    private BigDecimal rateToUsd;

    private String symbol;

    private Integer decimals;

    private Integer enabled;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}