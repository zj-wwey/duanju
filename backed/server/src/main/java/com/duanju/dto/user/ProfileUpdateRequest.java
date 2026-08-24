package com.duanju.dto.user;

import jakarta.validation.constraints.NotNull;

public record ProfileUpdateRequest(
        @NotNull String nickname,
        String avatarUrl
) {
}
