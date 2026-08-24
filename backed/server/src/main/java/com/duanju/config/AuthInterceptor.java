package com.duanju.config;

import com.duanju.common.R;
import com.duanju.security.Principal;
import com.duanju.security.PrincipalHolder;
import com.duanju.security.RequiresPermission;
import com.duanju.service.AdminService;
import com.duanju.service.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    /**
     * 白名单路径 (不需要 JWT 鉴权)
     * 第三方 webhook 回调不带系统 JWT,靠服务商自带签名做安全校验。
     * 公开 API (商品列表、剧集列表、会员权益对比) 不需要登录。
     */
    private static final List<String> WHITE_LIST_PREFIXES = List.of(
            "/api/webhooks/",            // Apple/Google/Stripe/PayPal/Cloudflare 支付回调
            "/api/auth/",                // 用户登录/注册/刷新
            "/api/admin/auth/login",     // 管理员登录
            "/api/admin/auth/captcha",   // 管理员登录验证码
            "/api/public/videos/",       // 公开视频下载（Android App 代理下载）
            "/api/point-products",        // 充值商品列表 (公开浏览)
            "/api/dramas",               // 剧集列表和详情 (公开浏览,登录后可看解锁状态)
            "/api/category-filters",     // 分类筛选列表 (公开)
            "/api/membership/benefits",  // 会员权益对比表 (公开)
            "/api/shop/items"            // 积分商城商品列表 (公开浏览)
    );

    private final TokenService tokenService;
    private final AdminService adminService;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(TokenService tokenService, AdminService adminService, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.adminService = adminService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();

        // 白名单:直接放行,由下游控制器自行处理安全校验(如 webhook 签名)
        if (isWhiteListed(path)) {
            return true;
        }

        Principal principal = tokenService.parse(request.getHeader("Authorization"));
        PrincipalHolder.set(principal);

        boolean adminRequired = path.startsWith("/api/admin/") && !path.equals("/api/admin/auth/login");
        boolean userRequired = path.startsWith("/api/") && !adminRequired;
        if (userRequired && principal == null) {
            return reject(response, 401, R.unauthorized());
        }
        if (userRequired && principal.admin()) {
            return reject(response, 403, R.forbidden());
        }
        if (adminRequired && principal == null) {
            return reject(response, 401, R.unauthorized());
        }
        if (adminRequired && !principal.admin()) {
            return reject(response, 403, R.forbidden());
        }
        if (adminRequired && handler instanceof HandlerMethod handlerMethod && !permitted(principal.id(), handlerMethod)) {
            return reject(response, 403, R.forbidden());
        }
        return true;
    }

    private boolean isWhiteListed(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        for (String prefix : WHITE_LIST_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean reject(HttpServletResponse response, int status, R<Void> body) {
        try {
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            log.warn("Failed to write auth rejection response: status={}", status, e);
        }
        return false;
    }

    private boolean permitted(Long adminId, HandlerMethod handlerMethod) {
        RequiresPermission required = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (required == null) {
            required = handlerMethod.getBeanType().getAnnotation(RequiresPermission.class);
        }
        if (required == null) {
            return true;
        }
        final String requiredValue = required.value();
        List<String> rows = adminService.getPermissions(adminId);
        for (String row : rows) {
            if (row == null || row.isBlank()) {
                continue;
            }
            if ("*".equals(row.trim())) {
                return true;
            }
            boolean matched = Arrays.stream(row.split(","))
                    .map(String::trim)
                    .anyMatch(permission -> permission.equals(requiredValue));
            if (matched) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Principal principal = PrincipalHolder.get();
        if (principal != null && principal.admin() && request.getRequestURI().startsWith("/api/admin/")
                && !"GET".equalsIgnoreCase(request.getMethod())) {
            adminService.logOperation(principal.id(), request.getMethod(), request.getRequestURI(),
                    response.getStatus(), clientIp(request));
        }
        PrincipalHolder.clear();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
