package com.duanju.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import com.duanju.interceptor.LocaleInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;
    private final LocaleInterceptor localeInterceptor;
    private final String localUploadDir;

    @Value("${duanju.cors.allowed-origins:}")
    private String allowedOriginsConfig;

    public WebConfig(AuthInterceptor authInterceptor,
                     LocaleInterceptor localeInterceptor,
                     @Value("${duanju.storage.local-upload-dir}") String localUploadDir) {
        this.authInterceptor = authInterceptor;
        this.localeInterceptor = localeInterceptor;
        this.localUploadDir = localUploadDir;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeInterceptor).addPathPatterns("/**");
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/webhooks/**",
                        "/api/auth/**",
                        "/api/admin/auth/login",
                        "/api/admin/auth/captcha",
                        "/api/public/videos/**"   // 公开视频下载代理端点
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = parseAllowedOrigins();
        boolean isPattern = origins.stream().anyMatch(o -> o.contains("*"));
        // API 接口跨域
        configureApiCors(registry.addMapping("/api/**"), origins, isPattern);
        // 静态资源跨域 (仅 GET)
        configureStaticCors(registry.addMapping("/uploads/**"), origins, isPattern);
        configureStaticCors(registry.addMapping("/upload/**"), origins, isPattern);
    }

    private void configureApiCors(CorsRegistration reg, List<String> origins, boolean isPattern) {
        String[] headers = {"Authorization", "Content-Type", "X-Requested-With", "X-Locale", "X-Captcha-Id"};
        String[] methods = {"GET", "POST", "PUT", "DELETE", "OPTIONS"};
        reg.allowedMethods(methods)
                .allowedHeaders(headers)
                .exposedHeaders("X-Total-Count")
                .maxAge(3600)
                .allowCredentials(true);
        applyOrigins(reg, origins, isPattern);
    }

    private void configureStaticCors(CorsRegistration reg, List<String> origins, boolean isPattern) {
        String[] headers = {"Authorization", "Content-Type", "X-Requested-With"};
        String[] methods = {"GET", "OPTIONS"};
        reg.allowedMethods(methods)
                .allowedHeaders(headers)
                .exposedHeaders("X-Total-Count")
                .maxAge(3600)
                .allowCredentials(true);
        applyOrigins(reg, origins, isPattern);
    }

    private void applyOrigins(CorsRegistration reg, List<String> origins, boolean isPattern) {
        if (isPattern) {
            reg.allowedOriginPatterns(origins.toArray(new String[0]));
        } else {
            reg.allowedOrigins(origins.toArray(new String[0]));
        }
    }

    /**
     * 解析允许的跨域来源。
     * <p>配置了 duanju.cors.allowed-origins 时用精确白名单 (生产环境);
     * 未配置时 fallback 到开发环境默认值 (允许 localhost、127.0.0.1 和内网地址)。</p>
     */
    private List<String> parseAllowedOrigins() {
        if (allowedOriginsConfig == null || allowedOriginsConfig.isBlank()) {
            return List.of(
                    "http://localhost:*",
                    "http://127.0.0.1:*",
                    "http://0.0.0.0:*",
                    // 允许主机局域网 IP (App 模拟器/真机通过局域网访问)
                    "http://192.168.0.108:*",
                    // 允许内网 IP (如 192.168.x.x, 172.x.x.x, 10.x.x.x)
                    "http://192.168.*.*:*",
                    "http://172.*.*.*:*",
                    "http://10.*.*.*:*"
            );
        }
        return Arrays.stream(allowedOriginsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = resolveUploadPath();
        // 使用 Path.toUri() 生成平台无关的 file: URL
        // Windows: file:/C:/Users/.../uploads/
        // Unix: file:/path/to/uploads/
        String location = uploadPath.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
        registry.addResourceHandler("/upload/**").addResourceLocations(location);
    }

    private Path resolveUploadPath() {
        Path path = Paths.get(localUploadDir).toAbsolutePath().normalize();
        if (Files.exists(path)) {
            return path;
        }
        Path found = findUploadDir();
        if (found != null) {
            return found;
        }
        return path;
    }

    private Path findUploadDir() {
        try {
            Path classPath = Paths.get(
                WebConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            ).toAbsolutePath().normalize();
            Path highestLevelMatch = null;
            int highestLevel = -1;
            Path current = classPath;
            int depth = 0;
            while (current != null && depth < 10) {
                Path backedUploads = current.resolve("backed").resolve("uploads");
                if (Files.isDirectory(backedUploads)) {
                    if (depth > highestLevel) {
                        highestLevel = depth;
                        highestLevelMatch = backedUploads;
                    }
                }
                Path directUploads = current.resolve("uploads");
                if (Files.isDirectory(directUploads)) {
                    if (depth > highestLevel) {
                        highestLevel = depth;
                        highestLevelMatch = directUploads;
                    }
                }
                current = current.getParent();
                depth++;
            }
            return highestLevelMatch;
        } catch (Exception ignored) {
        }
        return null;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
