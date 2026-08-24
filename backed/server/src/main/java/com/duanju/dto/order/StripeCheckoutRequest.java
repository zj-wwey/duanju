package com.duanju.dto.order;

import jakarta.validation.constraints.NotBlank;

public record StripeCheckoutRequest(@NotBlank String orderNo) {}
