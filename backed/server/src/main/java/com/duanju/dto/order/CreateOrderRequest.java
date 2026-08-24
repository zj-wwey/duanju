package com.duanju.dto.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateOrderRequest(
        @NotNull Long productId,
        @Pattern(regexp = "^(APPLE_IAP|GOOGLE_PLAY|STRIPE|PAYPAL)$",
                message = "payChannel must be one of: APPLE_IAP, GOOGLE_PLAY, STRIPE, PAYPAL")
        String payChannel
) {
}
