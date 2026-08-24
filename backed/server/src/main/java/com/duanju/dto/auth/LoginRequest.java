package com.duanju.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        String username,
        String account,
        @NotBlank String password,
        @NotBlank String captchaId,
        @NotBlank String captchaCode
) {
    public LoginRequest {
        if ((username == null || username.isBlank()) && (account == null || account.isBlank())) {
            throw new IllegalArgumentException("username or account is required");
        }
    }

    public String username() {
        return username == null || username.isBlank() ? account : username;
    }
}
