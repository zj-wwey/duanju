package com.duanju.dto.order;

import jakarta.validation.constraints.NotBlank;

public record AdRewardRequest(
        @NotBlank String adSlot,
        @NotBlank String traceId
) {
}
