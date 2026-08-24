package com.duanju.security;

public record Principal(Long id, String role) {
    public boolean admin() {
        return "ADMIN".equals(role);
    }
}
