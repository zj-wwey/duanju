package com.duanju.dto.shop;

import jakarta.validation.constraints.NotNull;

public record ShopItemStatusRequest(
        @NotNull Integer status
) {}
