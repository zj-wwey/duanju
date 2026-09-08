package com.duanju.config;

import com.duanju.common.ForbiddenException;
import com.duanju.common.R;
import com.duanju.service.I18nService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Locale;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final I18nService i18nService;

    public ApiExceptionHandler(I18nService i18nService) {
        this.i18nService = i18nService;
    }

    @ExceptionHandler(ForbiddenException.class)
    public R<Void> forbidden(ForbiddenException ex, Locale locale) {
        log.warn("Forbidden: {}", ex.getMessage());
        return R.forbidden();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public R<Void> badRequest(IllegalArgumentException ex, Locale locale) {
        log.warn("Bad request: {}", ex.getMessage());
        return R.failWithCode("INVALID_PARAMS", ex.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public R<Void> duplicate(DuplicateKeyException ex, Locale locale) {
        log.warn("Duplicate key: {}", ex.getMessage());
        return R.failWithCode("DUPLICATE_RECORD");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> validation(MethodArgumentNotValidException ex, Locale locale) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse(null);
        log.warn("Validation failed: {}", message);
        return R.failWithCode("INVALID_PARAMS", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> unreadable(HttpMessageNotReadableException ex, Locale locale) {
        log.warn("Unreadable HTTP message: {}", ex.getMessage());
        return R.failWithCode("INVALID_PARAMS");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> typeMismatch(MethodArgumentTypeMismatchException ex, Locale locale) {
        log.warn("Type mismatch on {}: {}", ex.getParameter(), ex.getMessage());
        return R.failWithCode("PARAM_TYPE_ERROR");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> notFound(NoResourceFoundException ex, HttpServletRequest request, Locale locale) {
        log.warn("Resource not found: method={} uri={} path={}", request.getMethod(), request.getRequestURI(), ex.getResourcePath());
        return R.failWithCode("RESOURCE_NOT_FOUND");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> methodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request, Locale locale) {
        log.error("HTTP method not supported: method={} uri={} supported={} query={}",
                request.getMethod(), request.getRequestURI(), ex.getSupportedHttpMethods(), request.getQueryString());
        return R.failWithCode("METHOD_NOT_ALLOWED", "方法不允许: " + request.getMethod() + " " + request.getRequestURI());
    }

    @ExceptionHandler(DataAccessException.class)
    public R<Void> dataAccess(DataAccessException ex, Locale locale) {
        String message = ex.getMostSpecificCause() == null ? ex.getMessage() : ex.getMostSpecificCause().getMessage();
        if (message != null && (message.contains("Unknown column") || message.contains("doesn't have a default value"))) {
            log.error("数据库结构未升级,请重启后端或执行初始化SQL;原因: {}", message);
        } else {
            log.error("数据库操作失败", ex);
        }
        return R.failWithCode("DATA_ACCESS_FAILED");
    }

    @ExceptionHandler(Exception.class)
    public R<Void> fallback(Exception ex, HttpServletRequest request, Locale locale) {
        log.error("未处理的异常: method={} uri={} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getClass().getName(), ex.getMessage(), ex);
        return R.failWithCode("SERVER_BUSY");
    }
}
