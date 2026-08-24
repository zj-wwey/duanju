package com.duanju.common;

/**
 * 访问被拒绝 (HTTP 403)。
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}