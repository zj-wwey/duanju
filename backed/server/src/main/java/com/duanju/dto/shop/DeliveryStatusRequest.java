package com.duanju.dto.shop;

import jakarta.validation.constraints.NotBlank;

public record DeliveryStatusRequest(
        @NotBlank(message = "deliveryStatus is required") String deliveryStatus
) {}
