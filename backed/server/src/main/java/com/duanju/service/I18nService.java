package com.duanju.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class I18nService {

    private static final Logger log = LoggerFactory.getLogger(I18nService.class);

    private final MessageSource messageSource;

    public I18nService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String get(String code, Locale locale) {
        try {
            return messageSource.getMessage(code, null, locale);
        } catch (NoSuchMessageException e) {
            log.warn("i18n key not found: {}, falling back to zh-CN", code);
            try {
                return messageSource.getMessage(code, null, Locale.SIMPLIFIED_CHINESE);
            } catch (NoSuchMessageException e2) {
                log.error("i18n key not found even in zh-CN: {}", code);
                return code;
            }
        }
    }

    public String get(String code, Locale locale, Object... args) {
        try {
            return messageSource.getMessage(code, args, locale);
        } catch (NoSuchMessageException e) {
            try {
                return messageSource.getMessage(code, args, Locale.SIMPLIFIED_CHINESE);
            } catch (NoSuchMessageException e2) {
                return code;
            }
        }
    }

    public String getDefault(String code) {
        return get(code, Locale.SIMPLIFIED_CHINESE);
    }
}