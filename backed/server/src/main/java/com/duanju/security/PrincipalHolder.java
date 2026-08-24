package com.duanju.security;

public final class PrincipalHolder {
    private static final ThreadLocal<Principal> HOLDER = new ThreadLocal<>();

    private PrincipalHolder() {
    }

    public static void set(Principal principal) {
        HOLDER.set(principal);
    }

    public static Principal get() {
        return HOLDER.get();
    }

    public static Long userId() {
        Principal principal = HOLDER.get();
        return principal == null || principal.admin() ? null : principal.id();
    }

    public static Long adminId() {
        Principal principal = HOLDER.get();
        return principal != null && principal.admin() ? principal.id() : null;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
