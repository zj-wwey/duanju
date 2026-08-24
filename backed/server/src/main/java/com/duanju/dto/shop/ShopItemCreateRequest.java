package com.duanju.dto.shop;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ShopItemCreateRequest(
        @NotBlank(message = "name is required") String name,
        String description,
        String itemType,
        @NotNull @Min(0) Integer pointsCost,
        @Min(0) Integer vipPointsCost,
        String imageUrl,
        @Min(-1) Integer stock,
        Integer sortOrder
) {}
