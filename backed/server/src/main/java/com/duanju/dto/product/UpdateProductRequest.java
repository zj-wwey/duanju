package com.duanju.dto.product;

public record UpdateProductRequest(
        String name,
        Integer points,
        Integer bonusPoints,
        Integer priceCents,
        String currency,
        String storeProductId,
        String packageType,
        Integer durationDays,
        Integer originalPriceCents,
        String tagText,
        String coverUrl,
        Integer sortOrder,
        Integer status,
        String productCategory,
        String membershipLevel,
        Integer dailyLimit,
        Integer monthlyLimit,
        Integer firstPurchaseBonus
) {
}
