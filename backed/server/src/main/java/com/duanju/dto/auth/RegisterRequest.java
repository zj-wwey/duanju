package com.duanju.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        String username,
        String account,
        @NotBlank @Size(min = 8, max = 64) String password,
        String nickname,
        @NotBlank String captchaId,
        @NotBlank String captchaCode
) {
    @NotBlank
    public String username() {
        return username == null || username.isBlank() ? account : username;
    }
}
