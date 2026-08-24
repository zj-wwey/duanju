package com.duanju.dto.order;

import jakarta.validation.constraints.NotBlank;

public record PaypalCaptureRequest(
        @NotBlank String orderNo,
        @NotBlank String paypalOrderId
) {}
