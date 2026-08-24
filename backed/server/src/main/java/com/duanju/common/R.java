package com.duanju.common;

public record R<T>(int code, String message, T data, String errorCode) {

    public static <T> R<T> ok(T data) {
        return new R<>(0, "ok", data, null);
    }

    public static R<Void> ok() {
        return new R<>(0, "ok", null, null);
    }

    public static <T> R<T> fail(String message) {
        return new R<>(400, message, null, null);
    }

    public static <T> R<T> failWithCode(String errorCode) {
        return new R<>(400, null, null, errorCode);
    }

    public static <T> R<T> failWithCode(String errorCode, String message) {
        return new R<>(400, message, null, errorCode);
    }

    public static <T> R<T> unauthorized() {
        return new R<>(401, "unauthorized", null, "UNAUTHORIZED");
    }

    public static <T> R<T> forbidden() {
        return new R<>(403, "forbidden", null, "FORBIDDEN");
    }

    public boolean isOk() {
        return code == 0;
    }

    public boolean hasErrorCode() {
        return errorCode != null && !errorCode.isBlank();
    }
}
