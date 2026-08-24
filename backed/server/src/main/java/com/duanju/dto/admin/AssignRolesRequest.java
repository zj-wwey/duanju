package com.duanju.dto.admin;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AssignRolesRequest(
        @NotEmpty List<Long> roleIds
) {
}
