package com.duanju.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Locale;

@Component
public class LocaleInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LocaleInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String localeHeader = request.getHeader("X-Locale");
        if (localeHeader != null && !localeHeader.isBlank()) {
            try {
                String[] parts = localeHeader.split("[_-]");
                Locale locale;
                if (parts.length >= 2) {
                    locale = new Locale(parts[0], parts[1].toUpperCase());
                } else {
                    locale = new Locale(parts[0]);
                }
                LocaleContextHolder.setLocale(locale);
            } catch (Exception e) {
                log.warn("Failed to parse locale from header: {}", localeHeader, e);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        LocaleContextHolder.resetLocaleContext();
    }
}