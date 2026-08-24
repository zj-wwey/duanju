package com.duanju.dto.shop;

import jakarta.validation.constraints.Min;

public record ShopItemUpdateRequest(
        String name,
        String description,
        String itemType,
        @Min(0) Integer pointsCost,
        @Min(0) Integer vipPointsCost,
        String imageUrl,
        @Min(-1) Integer stock,
        Integer status,
        Integer sortOrder
) {}
