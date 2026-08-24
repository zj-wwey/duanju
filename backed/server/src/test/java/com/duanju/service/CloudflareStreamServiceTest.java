package com.duanju.service;

import com.duanju.util.MapUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CloudflareStreamService 单元测试。
 */
class CloudflareStreamServiceTest {

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private CloudflareStreamService service;

    @BeforeEach
    void setUp() throws Exception {
        restTemplate = mock(RestTemplate.class);
        objectMapper = new ObjectMapper();
        service = new CloudflareStreamService(restTemplate, objectMapper);
        setField(service, "enabled", true);
        setField(service, "accountId", "test-account-id");
        setField(service, "apiToken", "test-api-token");
        setField(service, "signingKey", "test-signing-key");
        setField(service, "signedUrlTtlSeconds", 3600);
        setField(service, "hlsPathPrefix", "https://watch.cloudflarestream.com");
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    // --- isEnabled ---

    @Test
    @DisplayName("isEnabled: 配置完整返回 true")
    void isEnabled_configured_returnsTrue() {
        assertTrue(service.isEnabled());
    }

    @Test
    @DisplayName("isEnabled: enabled=false 返回 false")
    void isEnabled_disabled_returnsFalse() throws Exception {
        setField(service, "enabled", false);
        assertFalse(service.isEnabled());
    }

    @Test
    @DisplayName("isEnabled: accountId 为空返回 false")
    void isEnabled_noAccountId_returnsFalse() throws Exception {
        setField(service, "accountId", "");
        assertFalse(service.isEnabled());
    }

    @Test
    @DisplayName("isEnabled: apiToken 为空返回 false")
    void isEnabled_noApiToken_returnsFalse() throws Exception {
        setField(service, "apiToken", "");
        assertFalse(service.isEnabled());
    }

    // --- createStream ---

    @Test
    @DisplayName("createStream: 成功创建视频资源")
    void createStream_success_returnsResult() throws Exception {
        Map<String, Object> cloudflareResult = Map.of(
                "uid", "test-uid-123",
                "uploadURL", "https://upload.cloudflare.com/test",
                "signed", true,
                "public", false,
                "ready", false,
                "status", "new"
        );
        Map<String, Object> response = Map.of("result", cloudflareResult);
        ResponseEntity<String> httpResponse = new ResponseEntity<>(
                new ObjectMapper().writeValueAsString(response), HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(httpResponse);

        Map<String, String> meta = Map.of("dramaId", "1");
        Map<String, Object> result = service.createStream("test-video", meta);

        assertEquals("test-uid-123", MapUtil.str(result, "uid"));
        assertEquals("https://upload.cloudflare.com/test", MapUtil.str(result, "uploadURL"));
    }

    @Test
    @DisplayName("createStream: 未启用抛异常")
    void createStream_notEnabled_throwsException() throws Exception {
        setField(service, "enabled", false);
        assertThrows(IllegalStateException.class, () ->
                service.createStream("test", null));
    }

    // --- generateSignedUrl ---

    @Test
    @DisplayName("generateSignedUrl: 生成有效的签名URL")
    void generateSignedUrl_returnsValidUrl() {
        String signedUrl = service.generateSignedUrl("test-uid-123");
        assertNotNull(signedUrl);
        assertTrue(signedUrl.startsWith("https://watch.cloudflarestream.com/test-uid-123/manifest?token="));
        // Token 是 base64url 编码的 JWT 格式 (header.payload.signature)
        String token = signedUrl.substring(signedUrl.indexOf("token=") + 6);
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "Signed token should have 3 parts (header.payload.signature)");
    }

    @Test
    @DisplayName("generateSignedUrl: uid 为 null 抛异常")
    void generateSignedUrl_nullUid_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                service.generateSignedUrl(null));
    }

    @Test
    @DisplayName("generateSignedUrl: uid 为空字符串抛异常")
    void generateSignedUrl_blankUid_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                service.generateSignedUrl(""));
    }

    @Test
    @DisplayName("generateSignedToken: 相同 uid 和 exp 生成相同 token (确定性)")
    void generateSignedToken_deterministic() {
        long expTime = Instant.now().getEpochSecond() + 3600;
        String token1 = service.generateSignedToken("uid-1", expTime);
        String token2 = service.generateSignedToken("uid-1", expTime);
        assertEquals(token1, token2);
    }

    @Test
    @DisplayName("generateSignedToken: 不同 exp 生成不同 token")
    void generateSignedToken_differentExp_differentToken() {
        long exp1 = Instant.now().getEpochSecond() + 3600;
        long exp2 = Instant.now().getEpochSecond() + 7200;
        String token1 = service.generateSignedToken("uid-1", exp1);
        String token2 = service.generateSignedToken("uid-1", exp2);
        assertNotEquals(token1, token2);
    }

    // --- getHlsUrl ---

    @Test
    @DisplayName("getHlsUrl: 返回正确的 HLS URL")
    void getHlsUrl_returnsCorrectUrl() {
        String url = service.getHlsUrl("test-uid");
        assertEquals("https://watch.cloudflarestream.com/test-uid/manifest", url);
    }

    // --- deleteVideo ---

    @Test
    @DisplayName("deleteVideo: 成功删除")
    void deleteVideo_success_noException() {
        ResponseEntity<String> okResponse = new ResponseEntity<>(HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(), eq(String.class)))
                .thenReturn(okResponse);

        assertDoesNotThrow(() -> service.deleteVideo("test-uid"));
    }

    @Test
    @DisplayName("deleteVideo: uid 为 null 抛异常")
    void deleteVideo_nullUid_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                service.deleteVideo(null));
    }

    // --- handleStreamCompleted ---

    @Test
    @DisplayName("handleStreamCompleted: 缺少 video 字段安全返回")
    void handleStreamCompleted_missingVideo_logsAndReturns() {
        Map<String, Object> payload = new HashMap<>();
        assertDoesNotThrow(() -> service.handleStreamCompleted(payload));
    }

    @Test
    @DisplayName("handleStreamCompleted: 正常处理")
    void handleStreamCompleted_normalPayload_logsInfo() {
        Map<String, Object> video = Map.of("uid", "test-uid", "status", "ready");
        Map<String, Object> payload = Map.of("video", video);
        assertDoesNotThrow(() -> service.handleStreamCompleted(payload));
    }
}