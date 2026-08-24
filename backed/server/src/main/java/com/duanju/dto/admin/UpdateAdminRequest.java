package com.duanju.dto.admin;

import jakarta.validation.constraints.Size;

public record UpdateAdminRequest(
        @Size(max = 64) String nickname,
        @Size(min = 8, max = 64) String password
) {
}
