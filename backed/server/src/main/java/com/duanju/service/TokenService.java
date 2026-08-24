package com.duanju.service;

import com.duanju.security.Principal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class TokenService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final StringRedisTemplate redisTemplate;
    private final String secret;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;

    public TokenService(
            StringRedisTemplate redisTemplate,
            @Value("${duanju.auth.secret}") String secret,
            @Value("${duanju.auth.token-ttl-seconds}") long accessTtlSeconds,
            @Value("${duanju.auth.refresh-token-ttl-seconds}") long refreshTtlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.secret = secret;
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    public String create(Long id, String role) {
        return createPair(id, role).accessToken();
    }

    public TokenPair createPair(Long id, String role) {
        String accessJti = nonce();
        String refreshJti = nonce();
        String accessToken = createJwt(id, role, "access", accessJti, accessTtlSeconds);
        String refreshToken = createJwt(id, role, "refresh", refreshJti, refreshTtlSeconds);
        redisTemplate.opsForValue().set(accessKey(accessJti), id + ":" + role, Duration.ofSeconds(accessTtlSeconds));
        redisTemplate.opsForValue().set(refreshKey(refreshJti), id + ":" + role, Duration.ofSeconds(refreshTtlSeconds));
        return new TokenPair(accessToken, refreshToken, accessTtlSeconds, refreshTtlSeconds, accessJti, refreshJti);
    }

    public TokenPair refresh(String refreshToken) {
        JwtPayload payload = parseJwt(refreshToken, "refresh");
        if (payload == null) {
            return null;
        }
        String value = redisTemplate.opsForValue().get(refreshKey(payload.jti()));
        if (value == null) {
            return null;
        }
        String[] parts = value.split(":");
        if (parts.length != 2) {
            return null;
        }
        redisTemplate.delete(refreshKey(payload.jti()));
        return createPair(Long.valueOf(parts[0]), parts[1]);
    }

    public Principal parse(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7);
        JwtPayload payload = parseJwt(token, "access");
        if (payload == null) {
            return null;
        }
        String value = redisTemplate.opsForValue().get(accessKey(payload.jti()));
        if (value == null) {
            return null;
        }
        String[] parts = value.split(":");
        return new Principal(Long.valueOf(parts[0]), parts[1]);
    }

    private String createJwt(Long id, String role, String type, String jti, long ttlSeconds) {
        String header = base64("alg=HS256;typ=JWT");
        long exp = Instant.now().getEpochSecond() + ttlSeconds;
        String payload = base64("sub=" + id + ";role=" + role + ";type=" + type + ";jti=" + jti + ";exp=" + exp);
        String signingInput = header + "." + payload;
        return signingInput + "." + base64(hmac(signingInput));
    }

    private JwtPayload parseJwt(String token, String expectedType) {
        try {
            String[] segments = token.split("\\.");
            if (segments.length != 3) {
                return null;
            }
            String signingInput = segments[0] + "." + segments[1];
            if (!base64(hmac(signingInput)).equals(segments[2])) {
                return null;
            }
            String payload = new String(Base64.getUrlDecoder().decode(segments[1]), StandardCharsets.UTF_8);
            Long id = null;
            String role = null;
            String type = null;
            String jti = null;
            long exp = 0;
            for (String item : payload.split(";")) {
                int index = item.indexOf('=');
                if (index <= 0) {
                    continue;
                }
                String key = item.substring(0, index);
                String value = item.substring(index + 1);
                switch (key) {
                    case "sub" -> id = Long.valueOf(value);
                    case "role" -> role = value;
                    case "type" -> type = value;
                    case "jti" -> jti = value;
                    case "exp" -> exp = Long.parseLong(value);
                    default -> {
                    }
                }
            }
            if (id == null || role == null || jti == null || !expectedType.equals(type) || exp < Instant.now().getEpochSecond()) {
                return null;
            }
            return new JwtPayload(id, role, jti);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String accessKey(String jti) {
        return "auth:access:" + jti;
    }

    private String refreshKey(String jti) {
        return "auth:refresh:" + jti;
    }

    private String nonce() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign token", ex);
        }
    }

    public record TokenPair(String accessToken, String refreshToken, long expiresIn, long refreshExpiresIn,
                            String accessJti, String refreshJti) {
    }

    private record JwtPayload(Long id, String role, String jti) {
    }
}
