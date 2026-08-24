package com.duanju.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("point_product")
public class PointProduct {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Integer points;

    @TableField("bonus_points")
    private Integer bonusPoints;

    @TableField("price_cents")
    private Integer priceCents;

    private String currency;

    @TableField("store_product_id")
    private String storeProductId;

    @TableField("package_type")
    private String packageType;

    @TableField("duration_days")
    private Integer durationDays;

    @TableField("original_price_cents")
    private Integer originalPriceCents;

    @TableField("product_category")
    private String productCategory;

    @TableField("membership_level")
    private String membershipLevel;

    @TableField("daily_limit")
    private Integer dailyLimit;

    @TableField("monthly_limit")
    private Integer monthlyLimit;

    @TableField("first_purchase_bonus")
    private Integer firstPurchaseBonus;

    @TableField("tag_text")
    private String tagText;

    @TableField("cover_url")
    private String coverUrl;

    private Integer status;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
