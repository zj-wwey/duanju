package com.duanju.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 启动时安全配置校验。
 *
 * <p>阻止生产环境使用已知弱密钥/密码启动,避免 application.yml 默认值裸奔到生产。</p>
 * <ul>
 *   <li>所有环境:校验 secret 非空且长度 ≥ 32 (HMAC-SHA256 安全性要求)</li>
 *   <li>生产环境 (prod/production profile):额外校验 secret 不是开发默认值、DB 密码非空</li>
 *   <li>开发环境:允许使用开发默认值,但打 WARN 日志提醒</li>
 * </ul>
 */
@Component
public class SecurityStartupValidator {
    private static final Logger log = LoggerFactory.getLogger(SecurityStartupValidator.class);

    /** 已知的不安全密钥,生产环境禁止使用 */
    private static final Set<String> KNOWN_WEAK_SECRETS = Set.of(
            "change-this-secret-in-production",
            "duanju-secret",
            "secret",
            "dev-only-do-not-use-in-production-please-change-it-2024"
    );

    private static final int MIN_SECRET_LENGTH = 32;

    @Value("${duanju.auth.secret:}")
    private String authSecret;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @PostConstruct
    void validate() {
        boolean isProd = "prod".equalsIgnoreCase(activeProfile)
                || "production".equalsIgnoreCase(activeProfile);

        // 1. 所有环境:secret 不能为空
        if (authSecret == null || authSecret.isBlank()) {
            throw new IllegalStateException(
                    "启动失败:duanju.auth.secret 未配置。请通过环境变量 DUANJU_AUTH_SECRET 注入 (至少 "
                            + MIN_SECRET_LENGTH + " 字符的随机字符串)");
        }

        // 2. 所有环境:secret 长度 ≥ 32 (HMAC-SHA256 安全性要求)
        if (authSecret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "启动失败:duanju.auth.secret 长度不足,当前 " + authSecret.length()
                            + " 字符,要求至少 " + MIN_SECRET_LENGTH + " 字符");
        }

        // 3. 生产环境:禁止使用已知弱密钥
        if (isProd && KNOWN_WEAK_SECRETS.contains(authSecret)) {
            throw new IllegalStateException(
                    "启动失败:生产环境 duanju.auth.secret 使用了已知弱值/开发默认值,请通过环境变量 "
                            + "DUANJU_AUTH_SECRET 注入独立的随机密钥 (建议 openssl rand -base64 48 生成)");
        }

        // 4. 生产环境:DB 密码必须配置
        if (isProd && (dbPassword == null || dbPassword.isBlank())) {
            throw new IllegalStateException(
                    "启动失败:生产环境 spring.datasource.password 未配置。请通过环境变量 DB_PASSWORD 注入");
        }

        // 5. 开发环境使用弱密钥时打警告
        if (KNOWN_WEAK_SECRETS.contains(authSecret)) {
            log.warn("⚠ 开发环境使用默认密钥,生产环境请通过 DUANJU_AUTH_SECRET 环境变量注入独立密钥");
        } else {
            log.info("SecurityStartupValidator passed: profile={}, secretLen={}", activeProfile, authSecret.length());
        }
    }
}
