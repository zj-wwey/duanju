package com.duanju.dto.admin;

import jakarta.validation.constraints.NotNull;

public record PointAdjustRequest(
        @NotNull Integer delta,
        String remark
) {
}
