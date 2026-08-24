package com.duanju.dto.admin;

import jakarta.validation.constraints.NotBlank;

public record RoleCreateRequest(
        @NotBlank String name,
        @NotBlank String code,
        @NotBlank String permissions,
        Integer status
) {
}
