package com.duanju.dto.admin;

import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(
        @NotNull Integer status
) {
}
