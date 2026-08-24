package com.duanju.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProductRequest(
        @NotBlank(message = "套餐名称不能为空") String name,
        @NotNull(message = "积分数量不能为空") Integer points,
        Integer bonusPoints,
        @NotNull(message = "价格不能为空") Integer priceCents,
        String currency,
        String storeProductId,
        String packageType,
        Integer durationDays,
        Integer originalPriceCents,
        String tagText,
        String coverUrl,
        Integer sortOrder,
        String productCategory,
        String membershipLevel,
        Integer dailyLimit,
        Integer monthlyLimit,
        Integer firstPurchaseBonus
) {
}
