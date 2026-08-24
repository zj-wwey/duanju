package com.duanju.dto.admin;

import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(min = 3, max = 32) String username,
        String phone,
        String nickname,
        String avatarUrl,
        Integer status
) {
}
