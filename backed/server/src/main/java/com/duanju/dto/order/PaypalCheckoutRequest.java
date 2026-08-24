package com.duanju.dto.order;

import jakarta.validation.constraints.NotBlank;

public record PaypalCheckoutRequest(@NotBlank String orderNo) {}
