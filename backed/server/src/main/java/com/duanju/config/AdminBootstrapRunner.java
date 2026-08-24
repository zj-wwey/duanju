package com.duanju.config;

import com.duanju.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

/**
 * 管理员账户启动时引导。
 *
 * <p>解决 init.sql 中种子 admin 密码公开可见的安全问题:
 * init.sql 的 admin 账户 password_hash 留空,首次启动时本 Runner 检测到空密码,
 * 自动生成随机密码并更新到 DB,同时打印到 WARN 日志供运维首次登录使用。</p>
 *
 * <p>运维登录后应立即在后台修改密码。后续重启不会重复生成 (password_hash 已非空)。</p>
 */
@Component
public class AdminBootstrapRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public AdminBootstrapRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select id, password_hash from admin_user where username = 'admin' limit 1");
        if (rows.isEmpty()) {
            // admin 不存在 (干净安装,init.sql 未执行),跳过
            return;
        }
        String passwordHash = (String) rows.get(0).get("password_hash");
        if (passwordHash != null && !passwordHash.isEmpty()) {
            // 密码已设置,跳过
            return;
        }
        // 生成随机密码并更新
        String password = generateRandomPassword();
        String hash = PasswordUtil.hash(password);
        jdbcTemplate.update(
                "update admin_user set password_hash = ? where username = 'admin'", hash);
        log.warn("============================================================");
        log.warn("  首次启动:已为 admin 账户生成随机初始密码");
        log.warn("  用户名: admin");
        log.warn("  密码:   {}", password);
        log.warn("  请立即登录后台修改密码!此密码仅显示一次。");
        log.warn("============================================================");
    }

    /**
     * 生成 16 位随机密码,包含大小写字母+数字+特殊字符,满足密码策略。
     */
    private String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghijkmnpqrstuvwxyz";
        String digit = "23456789";
        String special = "@#$%^&*";
        char[] arr = new char[16];
        // 确保包含每种字符
        arr[0] = upper.charAt(random.nextInt(upper.length()));
        arr[1] = lower.charAt(random.nextInt(lower.length()));
        arr[2] = digit.charAt(random.nextInt(digit.length()));
        arr[3] = special.charAt(random.nextInt(special.length()));
        // 剩余 12 位随机
        String all = upper + lower + digit + special;
        for (int i = 4; i < 16; i++) {
            arr[i] = all.charAt(random.nextInt(all.length()));
        }
        // Fisher-Yates 打乱
        for (int i = arr.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = arr[i];
            arr[i] = arr[j];
            arr[j] = tmp;
        }
        return new String(arr);
    }
}
