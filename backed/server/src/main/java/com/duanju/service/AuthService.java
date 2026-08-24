package com.duanju.service;

import com.duanju.entity.AdminUser;
import com.duanju.entity.AppUser;
import com.duanju.entity.AuthCaptchaRecord;
import com.duanju.entity.PointRecord;
import com.duanju.security.PrincipalHolder;
import com.duanju.service.entity.AdminUserRoleService;
import com.duanju.service.entity.AdminUserService;
import com.duanju.service.entity.AppUserService;
import com.duanju.service.entity.AuthCaptchaRecordService;
import com.duanju.service.entity.PointRecordService;
import com.duanju.util.MapUtil;
import com.duanju.util.PasswordUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.geom.CubicCurve2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int REGISTER_BONUS_POINTS = 20;

    private final AppUserService appUserService;
    private final AuthCaptchaRecordService authCaptchaRecordService;
    private final AdminUserService adminUserService;
    private final AdminUserRoleService adminUserRoleService;
    private final PointRecordService pointRecordService;
    private final TokenService tokenService;
    private final StringRedisTemplate redisTemplate;
    private final String authSecret;
    private final long captchaTtlSeconds;
    private final int maxFailByIp;
    private final int maxFailByUser;
    private final int lockMinutes;

    public AuthService(AppUserService appUserService,
                       AuthCaptchaRecordService authCaptchaRecordService,
                       AdminUserService adminUserService,
                       AdminUserRoleService adminUserRoleService,
                       PointRecordService pointRecordService,
                       TokenService tokenService,
                       StringRedisTemplate redisTemplate,
                       @Value("${duanju.auth.secret}") String authSecret,
                       @Value("${duanju.auth.captcha-ttl-seconds}") long captchaTtlSeconds,
                       @Value("${duanju.auth.login.max-fail-by-ip:20}") int maxFailByIp,
                       @Value("${duanju.auth.login.max-fail-by-user:5}") int maxFailByUser,
                       @Value("${duanju.auth.login.lock-minutes:15}") int lockMinutes) {
        this.appUserService = appUserService;
        this.authCaptchaRecordService = authCaptchaRecordService;
        this.adminUserService = adminUserService;
        this.adminUserRoleService = adminUserRoleService;
        this.pointRecordService = pointRecordService;
        this.tokenService = tokenService;
        this.redisTemplate = redisTemplate;
        this.authSecret = authSecret;
        this.captchaTtlSeconds = captchaTtlSeconds;
        this.maxFailByIp = maxFailByIp;
        this.maxFailByUser = maxFailByUser;
        this.lockMinutes = lockMinutes;
    }

    public Map<String, Object> generateCaptcha(String scene, String receiver, String requestIp) {
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        String code = String.valueOf(100000 + RANDOM.nextInt(900000));
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(captchaTtlSeconds);
        AuthCaptchaRecord record = new AuthCaptchaRecord();
        record.setCaptchaId(captchaId);
        record.setScene(normalizedScene(scene));
        record.setReceiver(receiverValue(receiver));
        record.setCaptchaHash(captchaHash(captchaId, code));
        record.setExpiresAt(expiresAt);
        record.setRequestIp(requestIp);
        record.setStatus(0);
        authCaptchaRecordService.save(record);
        String base64Png = Base64.getEncoder().encodeToString(png(code));
        return MapUtil.map(
                "captchaId", captchaId,
                "imageData", "data:image/png;base64," + base64Png,
                "expiresIn", captchaTtlSeconds
        );
    }

    @Transactional
    public Map<String, Object> register(String username, String password, String nickname,
                                        String captchaId, String captchaCode) {
        username = normalizeUsername(username);
        validateUsername(username);
        validatePassword(password);
        verifyCaptcha("REGISTER", captchaId, captchaCode);
        if (appUserService.lambdaQuery().eq(AppUser::getUsername, username).exists()) {
            throw new IllegalArgumentException("账号已注册，请直接登录");
        }
        if (adminUserService.lambdaQuery().eq(AdminUser::getUsername, username).exists()) {
            throw new IllegalArgumentException("该账号已被管理员使用，请换一个账号");
        }
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPhoneVerified(0);
        user.setPasswordHash(PasswordUtil.hash(password));
        user.setNickname(nickname == null || nickname.isBlank() ? defaultNickname(username) : nickname.trim());
        user.setPoints(REGISTER_BONUS_POINTS);
        user.setAuthProvider("PASSWORD");
        appUserService.save(user);
        Long userId = user.getId();
        recordPoint(userId, REGISTER_BONUS_POINTS, "REGISTER", String.valueOf(userId), "register bonus");
        return MapUtil.map(
                "registered", true,
                "redirect", "login",
                "username", username
        );
    }

    public Map<String, Object> login(String username, String password, String captchaId, String captchaCode,
                                     HttpServletRequest servletRequest) {
        username = normalizeUsername(username);
        validateUsername(username);
        String ip = clientIp(servletRequest);
        checkLoginLimit(username, ip);
        verifyCaptcha("LOGIN", captchaId, captchaCode);

        // 1. 先检查该用户名是否为管理员账号 → 拒绝,强制走管理员登录入口
        if (adminUserService.lambdaQuery().eq(AdminUser::getUsername, username).exists()) {
            recordLoginFailure(username, ip);
            throw new IllegalArgumentException("该账号为管理员账号，请使用管理员登录入口");
        }

        // 2. 查普通用户表
        AppUser user = appUserService.lambdaQuery().eq(AppUser::getUsername, username).one();
        if (user == null || !PasswordUtil.verify(password, user.getPasswordHash())) {
            recordLoginFailure(username, ip);
            throw new IllegalArgumentException("账号或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new IllegalArgumentException("账号已禁用");
        }
        clearLoginFailure(username);
        Long userId = user.getId();
        TokenService.TokenPair tokens = tokenService.createPair(userId, "USER");
        appUserService.lambdaUpdate()
                .set(AppUser::getLastLoginAt, LocalDateTime.now())
                .set(AppUser::getLastLoginIp, clientIp(servletRequest))
                .set(AppUser::getLastTokenJti, tokens.accessJti())
                .set(AppUser::getRefreshTokenJti, tokens.refreshJti())
                .eq(AppUser::getId, userId)
                .update();
        AppUser fresh = appUserService.getById(userId);
        fresh.setPasswordHash(null);
        return authPayload("USER", "app", tokens, MapUtil.beanToMap(fresh), null);
    }

    public Map<String, Object> adminLogin(String username, String password, String captchaId, String captchaCode) {
        username = normalizeUsername(username);
        validateUsername(username);
        // adminLogin 无 HttpServletRequest,仅做账户维度限流
        checkLoginLimit(username, null);
        verifyCaptcha("ADMIN_LOGIN", captchaId, captchaCode);

        // 1. 先检查该用户名是否为普通用户账号 → 拒绝,强制走用户登录入口
        if (appUserService.lambdaQuery().eq(AppUser::getUsername, username).exists()) {
            recordLoginFailure(username, null);
            throw new IllegalArgumentException("该账号为普通用户账号，请使用用户登录入口");
        }

        // 2. 查管理员表
        AdminUser admin = adminUserService.lambdaQuery().eq(AdminUser::getUsername, username).one();
        if (admin == null || !PasswordUtil.verify(password, admin.getPasswordHash())) {
            recordLoginFailure(username, null);
            throw new IllegalArgumentException("管理员账号或密码错误");
        }
        if (admin.getStatus() == null || admin.getStatus() != 1) {
            throw new IllegalArgumentException("管理员账号已禁用");
        }
        clearLoginFailure(username);
        Long adminId = admin.getId();
        return authPayload("ADMIN", "admin", tokenService.createPair(adminId, "ADMIN"), null, MapUtil.map(
                "id", adminId,
                "username", admin.getUsername(),
                "nickname", admin.getNickname()
        ));
    }

    public Map<String, Object> adminMe() {
        Long adminId = PrincipalHolder.adminId();
        AdminUser admin = adminUserService.getById(adminId);
        if (admin == null || admin.getStatus() == null || admin.getStatus() != 1) {
            throw new IllegalArgumentException("管理员账号不存在或已禁用");
        }
        admin.setPasswordHash(null);
        Map<String, Object> adminMap = MapUtil.beanToMap(admin);
        adminMap.put("roles", adminUserRoleService.adminRoles(adminId));
        List<String> rawPermissions = adminUserRoleService.permissions(adminId);
        List<String> flattened = flattenPermissions(rawPermissions);
        adminMap.put("permissions", flattened);
        adminMap.put("isSuperAdmin", flattened.contains("*"));
        return adminMap;
    }

    private List<String> flattenPermissions(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Set<String> set = new HashSet<>();
        for (String row : raw) {
            if (row == null || row.isBlank()) {
                continue;
            }
            String trimmed = row.trim();
            if ("*".equals(trimmed)) {
                return List.of("*");
            }
            set.addAll(Arrays.stream(trimmed.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet()));
        }
        return new ArrayList<>(set);
    }

    public Map<String, Object> refresh(String refreshToken) {
        TokenService.TokenPair tokens = tokenService.refresh(refreshToken);
        if (tokens == null) {
            throw new IllegalArgumentException("refresh token invalid");
        }
        return tokenPayload(tokens);
    }

    public String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // ==================== 登录限流 ====================

    /**
     * 检查登录限流:IP 维度 + 账户维度。
     * 任一维度达到阈值则拒绝登录,提示剩余锁定时间。
     *
     * @param username 用户名 (账户维度,非空)
     * @param ip 客户端 IP (IP 维度,可为 null 表示不检查 IP 维度)
     */
    private void checkLoginLimit(String username, String ip) {
        if (ip != null) {
            String ipBlockKey = "login:block:ip:" + ip;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(ipBlockKey))) {
                Long ttl = redisTemplate.getExpire(ipBlockKey);
                throw new IllegalArgumentException("登录失败次数过多,IP 已被临时封禁,请 " + formatTtl(ttl) + " 后再试");
            }
        }
        String userBlockKey = "login:block:user:" + username;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(userBlockKey))) {
            Long ttl = redisTemplate.getExpire(userBlockKey);
            throw new IllegalArgumentException("账号已锁定,请 " + formatTtl(ttl) + " 后再试");
        }
    }

    /**
     * 记录登录失败:IP 计数 + 账户计数,达到阈值则设置锁定标记。
     */
    private void recordLoginFailure(String username, String ip) {
        Duration lockDuration = Duration.ofMinutes(lockMinutes);
        if (ip != null) {
            String ipFailKey = "login:fail:ip:" + ip;
            Long ipFails = redisTemplate.opsForValue().increment(ipFailKey);
            if (ipFails != null && ipFails == 1L) {
                redisTemplate.expire(ipFailKey, lockDuration);
            }
            if (ipFails != null && ipFails >= maxFailByIp) {
                redisTemplate.opsForValue().set("login:block:ip:" + ip, "1", lockDuration);
            }
        }
        String userFailKey = "login:fail:user:" + username;
        Long userFails = redisTemplate.opsForValue().increment(userFailKey);
        if (userFails != null && userFails == 1L) {
            redisTemplate.expire(userFailKey, lockDuration);
        }
        if (userFails != null && userFails >= maxFailByUser) {
            redisTemplate.opsForValue().set("login:block:user:" + username, "1", lockDuration);
        }
    }

    /**
     * 登录成功后清除账户维度的失败计数和锁定标记。
     * 不清除 IP 维度计数 (避免 NAT 后多用户互相影响,IP 封禁由 TTL 自动过期)。
     */
    private void clearLoginFailure(String username) {
        redisTemplate.delete("login:fail:user:" + username);
        redisTemplate.delete("login:block:user:" + username);
    }

    /**
     * 格式化 Redis TTL 为人类可读的时间描述。
     */
    private String formatTtl(Long ttlSeconds) {
        if (ttlSeconds == null || ttlSeconds < 0) {
            return lockMinutes + " 分钟";
        }
        long minutes = ttlSeconds / 60;
        long seconds = ttlSeconds % 60;
        if (minutes > 0) {
            return minutes + " 分 " + seconds + " 秒";
        }
        return seconds + " 秒";
    }

    // ==================== 验证码 ====================

    private void verifyCaptcha(String scene, String captchaId, String captchaCode) {
        if (captchaId == null || captchaId.isBlank() || captchaCode == null || captchaCode.isBlank()) {
            throw new IllegalArgumentException("验证码不能为空");
        }
        String trimmedCaptchaId = captchaId.trim();
        AuthCaptchaRecord row = authCaptchaRecordService.lambdaQuery()
                .eq(AuthCaptchaRecord::getCaptchaId, trimmedCaptchaId)
                .last("limit 1")
                .one();
        if (row == null || row.getStatus() == null || row.getStatus() != 0) {
            throw new IllegalArgumentException("验证码无效或已使用");
        }
        if (!normalizedScene(scene).equals(row.getScene())) {
            throw new IllegalArgumentException("验证码场景不匹配");
        }
        if (row.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("验证码已过期");
        }
        if ((row.getFailCount() == null ? 0 : row.getFailCount()) >= 5) {
            throw new IllegalArgumentException("验证码错误次数过多，请重新获取");
        }
        if (!captchaHash(trimmedCaptchaId, captchaCode.trim()).equals(row.getCaptchaHash())) {
            authCaptchaRecordService.lambdaUpdate()
                    .setSql("fail_count = fail_count + 1")
                    .eq(AuthCaptchaRecord::getCaptchaId, trimmedCaptchaId)
                    .eq(AuthCaptchaRecord::getStatus, 0)
                    .update();
            throw new IllegalArgumentException("验证码错误");
        }
        authCaptchaRecordService.lambdaUpdate()
                .set(AuthCaptchaRecord::getStatus, 1)
                .set(AuthCaptchaRecord::getVerifiedAt, LocalDateTime.now())
                .eq(AuthCaptchaRecord::getCaptchaId, trimmedCaptchaId)
                .eq(AuthCaptchaRecord::getStatus, 0)
                .update();
    }

    private void recordPoint(Long userId, int delta, String bizType, String bizId, String remark) {
        PointRecord record = new PointRecord();
        record.setUserId(userId);
        record.setDelta(delta);
        record.setBizType(bizType);
        record.setBizId(bizId);
        record.setRemark(remark);
        pointRecordService.save(record);
    }

    private void validateUsername(String username) {
        PasswordUtil.validateUsername(username);
    }

    private void validatePassword(String password) {
        PasswordUtil.validate(password);
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private String normalizedScene(String scene) {
        return scene == null || scene.isBlank() ? "LOGIN" : scene.trim().toUpperCase();
    }

    private String receiverValue(String receiver) {
        return receiver == null || receiver.isBlank() ? null : receiver.trim();
    }

    private String defaultNickname(String account) {
        return "user" + account.substring(Math.max(0, account.length() - 4));
    }

    private Map<String, Object> authPayload(String role, String redirect, TokenService.TokenPair tokens,
                                            Map<String, Object> user, Map<String, Object> admin) {
        Map<String, Object> payload = tokenPayload(tokens);
        payload.put("role", role);
        payload.put("redirect", redirect);
        if (user != null) {
            payload.put("user", user);
        }
        if (admin != null) {
            payload.put("admin", admin);
        }
        return payload;
    }

    private Map<String, Object> tokenPayload(TokenService.TokenPair tokens) {
        return MapUtil.map(
                "token", tokens.accessToken(),
                "accessToken", tokens.accessToken(),
                "refreshToken", tokens.refreshToken(),
                "expiresIn", tokens.expiresIn(),
                "refreshExpiresIn", tokens.refreshExpiresIn()
        );
    }

    private String captchaHash(String captchaId, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String value = captchaId + ":" + code.toUpperCase() + ":" + authSecret;
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash captcha", ex);
        }
    }

    private byte[] png(String code) {
        int w = 168;
        int h = 56;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            // 背景 #111827
            g.setColor(new Color(0x111827));
            g.fillRect(0, 0, w, h);
            // 装饰贝塞尔曲线 #f7c66a
            g.setColor(new Color(0xf7c66a));
            CubicCurve2D curve = new CubicCurve2D.Double(10, 42, 42, 6, 76, 64, 116, 18);
            g.draw(curve);
            CubicCurve2D curve2 = new CubicCurve2D.Double(116, 18, 138, 44, 150, 40, 158, 16);
            g.draw(curve2);
            // 文本白色居中
            g.setColor(Color.WHITE);
            Map<TextAttribute, Object> attrs = new HashMap<>();
            attrs.put(TextAttribute.FAMILY, Font.MONOSPACED);
            attrs.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
            attrs.put(TextAttribute.SIZE, 28);
            attrs.put(TextAttribute.TRACKING, 0.08);
            Font font = Font.getFont(attrs);
            g.setFont(font);
            int textWidth = g.getFontMetrics().stringWidth(code);
            int x = (w - textWidth) / 2;
            int y = 36;
            g.drawString(code, x, y);
            // 若干干扰点
            for (int i = 0; i < 28; i++) {
                int px = RANDOM.nextInt(w);
                int py = RANDOM.nextInt(h);
                int brightness = 80 + RANDOM.nextInt(120);
                g.setColor(new Color(brightness, brightness, brightness));
                g.fillOval(px, py, 2, 2);
            }
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("验证码生成失败", ex);
        }
    }
}
