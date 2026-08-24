package com.duanju.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAdminRequest(
        @NotBlank @Size(min = 5, max = 32) @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{4,31}$", message = "用户名需以字母开头，5-32位，仅允许字母、数字、下划线") String username,
        @NotBlank @Size(min = 8, max = 64) String password,
        @Size(max = 64) String nickname
) {
}
