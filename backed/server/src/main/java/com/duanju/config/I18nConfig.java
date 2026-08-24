package com.duanju.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

@Configuration
public class I18nConfig {

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:i18n/messages");
        ms.setDefaultEncoding("UTF-8");
        ms.setFallbackToSystemLocale(false);
        ms.setCacheSeconds(3600);
        return ms;
    }

    @Bean
    public LocaleResolver localeResolver() {
        final List<Locale> supported = List.of(
            Locale.SIMPLIFIED_CHINESE,
            Locale.TRADITIONAL_CHINESE,
            Locale.US,
            Locale.JAPAN,
            Locale.KOREA,
            new Locale("th", "TH"),
            new Locale("vi", "VN"),
            new Locale("id", "ID"),
            new Locale("ms", "MY"),
            new Locale("es", "ES"),
            new Locale("fr", "FR"),
            new Locale("de", "DE"),
            new Locale("pt", "BR"),
            new Locale("ru", "RU"),
            new Locale("it", "IT"),
            new Locale("tr", "TR"),
            new Locale("ar", "SA"),
            new Locale("hi", "IN"),
            new Locale("sv", "SE")
        );
        return new AcceptHeaderLocaleResolver() {
            {
                setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
                setSupportedLocales(supported);
            }

            @Override
            public Locale resolveLocale(HttpServletRequest request) {
                String localeHeader = request.getHeader("X-Locale");
                if (localeHeader != null && !localeHeader.isBlank()) {
                    try {
                        String[] parts = localeHeader.split("[_-]");
                        Locale parsed = (parts.length >= 2)
                            ? new Locale(parts[0], parts[1].toUpperCase())
                            : new Locale(parts[0]);
                        if (parsed.getLanguage() != null) return parsed;
                    } catch (Exception ignored) { }
                }
                return super.resolveLocale(request);
            }

            @Override
            public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
                LocaleContextHolder.setLocale(locale);
            }
        };
    }
}