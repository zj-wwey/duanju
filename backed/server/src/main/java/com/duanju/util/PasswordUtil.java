package com.duanju.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.HexFormat;
import java.util.regex.Pattern;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PasswordUtil {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int PBKDF2_KEY_LENGTH = 256;
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=\\S+$)(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,64}$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{4,31}$");

    private static final Logger log = LoggerFactory.getLogger(PasswordUtil.class);

    private PasswordUtil() {
    }

    public static void validate(String password) {
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("密码需8-64位，包含大小写字母、数字和特殊符号，且不能包含空白字符");
        }
    }

    public static boolean isValid(String password) {
        return password != null && PASSWORD_PATTERN.matcher(password).matches();
    }

    public static void validateUsername(String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("用户名需以字母开头，5-32位，仅允许字母、数字、下划线");
        }
    }

    public static boolean isUsernameValid(String username) {
        return username != null && USERNAME_PATTERN.matcher(username).matches();
    }

    public static String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return "pbkdf2:" + PBKDF2_ITERATIONS + ":" + HexFormat.of().formatHex(salt) + ":" + pbkdf2(salt, password, PBKDF2_ITERATIONS);
    }

    public static boolean verify(String password, String encoded) {
        try {
            if (encoded == null || !encoded.contains(":")) {
                return false;
            }
            if (encoded.startsWith("pbkdf2:")) {
                String[] parts = encoded.split(":");
                if (parts.length != 4) {
                    return false;
                }
                int iterations = Integer.parseInt(parts[1]);
                byte[] salt = HexFormat.of().parseHex(parts[2]);
                return pbkdf2(salt, password, iterations).equals(parts[3]);
            }
            String[] parts = encoded.split(":");
            byte[] salt = HexFormat.of().parseHex(parts[0]);
            return digest(salt, password).equals(parts[1]);
        } catch (RuntimeException ex) {
            log.warn("Password verification failed: {}", ex.getMessage());
            return false;
        }
    }

    private static String digest(byte[] salt, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            return HexFormat.of().formatHex(md.digest(password.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash password", ex);
        }
    }

    private static String pbkdf2(byte[] salt, String password, int iterations) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, PBKDF2_KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return HexFormat.of().formatHex(factory.generateSecret(spec).getEncoded());
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash password", ex);
        }
    }
}
