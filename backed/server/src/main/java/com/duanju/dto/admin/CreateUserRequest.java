package com.duanju.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(min = 5, max = 32)
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{4,31}$", message = "username must be 5-32 chars, starting with a letter")
        String username,
        String phone,
        @NotBlank @Size(min = 8, max = 64)
        @Pattern(regexp = "^(?=\\S+$)(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,64}$",
                message = "password must be 8-64 chars with upper, lower, number, and special symbol")
        String password,
        String nickname,
        String avatarUrl,
        Integer points
) {
}
