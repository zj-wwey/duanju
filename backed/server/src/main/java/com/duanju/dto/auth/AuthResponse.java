package com.duanju.dto.auth;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        Long expiresIn,
        String tokenType
) {
}
