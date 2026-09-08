package com.duanju.service;

import com.duanju.entity.DramaEpisode;
import com.duanju.service.entity.DramaEpisodeService;
import com.duanju.util.MapUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
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
    private DramaEpisodeService dramaEpisodeService;
    private CloudflareStreamService service;

    @BeforeEach
    void setUp() throws Exception {
        restTemplate = mock(RestTemplate.class);
        objectMapper = new ObjectMapper();
        dramaEpisodeService = mock(DramaEpisodeService.class);
        service = new CloudflareStreamService(restTemplate, objectMapper, dramaEpisodeService);
        setField(service, "enabled", true);
        setField(service, "accountId", "test-account-id");
        setField(service, "apiToken", "test-api-token");
        setField(service, "signingKey", "test-signing-key");
        setField(service, "signedUrlTtlSeconds", 3600);
        setField(service, "hlsPathPrefix", "https://watch.cloudflarestream.com");
        setField(service, "allowedMimeTypes", "video/mp4,video/x-m4v,video/webm,video/quicktime");
        // TUS 调优字段 (生产中走 @Value 注入,测试手动注入)
        setField(service, "directUploadThresholdBytes", 200L * 1024 * 1024);
        setField(service, "tusChunkSizeBytes", 5 * 1024 * 1024);
        setField(service, "tusMaxRetries", 3);
        setField(service, "tusRetryBaseIntervalMs", 1000L);
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
    @DisplayName("handleStreamCompleted: payload 为 null 安全返回,不查库不回写")
    void handleStreamCompleted_nullPayload_skipsBackfill() {
        assertDoesNotThrow(() -> service.handleStreamCompleted(null));
        verifyNoInteractions(dramaEpisodeService);
    }

    @Test
    @DisplayName("handleStreamCompleted: 缺少 video 字段安全返回,不查库不回写")
    void handleStreamCompleted_missingVideo_skipsBackfill() {
        Map<String, Object> payload = new HashMap<>();
        assertDoesNotThrow(() -> service.handleStreamCompleted(payload));
        verifyNoInteractions(dramaEpisodeService);
    }

    @Test
    @DisplayName("handleStreamCompleted: 缺少 uid 安全返回,不查库不回写")
    void handleStreamCompleted_missingUid_skipsBackfill() {
        Map<String, Object> video = Map.of("status", "ready", "readyToStream", true);
        Map<String, Object> payload = Map.of("video", video);
        assertDoesNotThrow(() -> service.handleStreamCompleted(payload));
        verifyNoInteractions(dramaEpisodeService);
    }

    @Test
    @DisplayName("handleStreamCompleted: readyToStream=false 跳过回写,不查库")
    void handleStreamCompleted_notReadyToStream_skipsBackfill() {
        Map<String, Object> video = new HashMap<>();
        video.put("uid", "test-uid");
        video.put("status", "transcoding");
        video.put("readyToStream", false);
        Map<String, Object> payload = Map.of("video", video);
        assertDoesNotThrow(() -> service.handleStreamCompleted(payload));
        verifyNoInteractions(dramaEpisodeService);
    }

    @Test
    @DisplayName("handleStreamCompleted: 正常 payload 但 uid 未绑定剧集,不回写不抛异常")
    void handleStreamCompleted_uidNotLinked_skipsBackfill() {
        Map<String, Object> video = new HashMap<>();
        video.put("uid", "unlinked-uid");
        video.put("status", "ready");
        video.put("readyToStream", true);
        video.put("duration", 120.5);
        video.put("hls", "https://watch.cloudflarestream.com/unlinked-uid/manifest/video.m3u8");
        Map<String, Object> payload = Map.of("video", video);

        // lambdaQuery 链式 mock:返回找不到 episode
        var queryMock = mock(com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper.class);
        when(queryMock.eq(any(), any())).thenReturn(queryMock);
        when(queryMock.one()).thenReturn(null);
        when(dramaEpisodeService.lambdaQuery()).thenReturn(queryMock);

        assertDoesNotThrow(() -> service.handleStreamCompleted(payload));
        verify(dramaEpisodeService, times(1)).lambdaQuery();
        verify(dramaEpisodeService, never()).lambdaUpdate();
    }

    // --- uploadVideo (TUS / PUT 路由) ---

    @Test
    @DisplayName("uploadVideo: 文件 >= 200MB 走 TUS 断点续传路径")
    void uploadVideo_largeFile_usesTus() throws Exception {
        // 250MB,超过 200MB 阈值,触发 TUS 路径
        long fileSize = 250L * 1024 * 1024;

        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(fileSize);
        when(file.getContentType()).thenReturn("video/mp4");
        when(file.isEmpty()).thenReturn(false);
        when(file.getInputStream()).thenReturn(new FakeInputStream(fileSize));

        // init_upload 返回 201 + Location 头
        HttpHeaders initRespHeaders = new HttpHeaders();
        initRespHeaders.set("Location",
                "https://api.cloudflare.com/client/v4/accounts/test-account-id/stream/test-uid-123/uploads/upload-1");
        ResponseEntity<String> initResponse = new ResponseEntity<>("", initRespHeaders, HttpStatus.CREATED);

        // PATCH 动态返回 204 + Upload-Offset = 当前 offset + chunk length
        when(restTemplate.exchange(contains("/uploads/"), eq(HttpMethod.PATCH), any(), eq(String.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    HttpEntity<byte[]> entity = invocation.getArgument(2);
                    long offset = Long.parseLong(entity.getHeaders().getFirst("Upload-Offset"));
                    long newOffset = offset + entity.getBody().length;
                    HttpHeaders patchRespHeaders = new HttpHeaders();
                    patchRespHeaders.set("Upload-Offset", String.valueOf(newOffset));
                    return new ResponseEntity<>("", patchRespHeaders, HttpStatus.NO_CONTENT);
                });

        // GET /stream/{uid} 返回视频信息
        Map<String, Object> videoInfo = new HashMap<>();
        videoInfo.put("uid", "test-uid-123");
        videoInfo.put("status", "queued");
        ResponseEntity<String> getResponse = new ResponseEntity<>(
                new ObjectMapper().writeValueAsString(Map.of("result", videoInfo)), HttpStatus.OK);

        when(restTemplate.exchange(endsWith("/stream/test-uid-123"), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(getResponse);
        when(restTemplate.exchange(contains("/init_upload"), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(initResponse);

        Map<String, Object> result = service.uploadVideo(file, "test-video-large");

        assertEquals("test-uid-123", MapUtil.str(result, "uid"));
        // 验证走了 TUS 路径
        verify(restTemplate, times(1)).exchange(contains("/init_upload"), eq(HttpMethod.POST), any(), eq(String.class));
        // 250MB / 5MB = 50 个分片
        verify(restTemplate, times(50)).exchange(contains("/uploads/"), eq(HttpMethod.PATCH), any(), eq(String.class));
        // 不应走 PUT 直传路径
        verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.PUT), any(), eq(String.class));
    }

    @Test
    @DisplayName("uploadVideo: 文件 < 200MB 走原 PUT 直传路径")
    void uploadVideo_smallFile_usesDirectPut() throws Exception {
        // 100MB,低于 200MB 阈值,走原 PUT 路径
        long fileSize = 100L * 1024 * 1024;
        byte[] fileBytes = new byte[1024]; // 测试不关心真实内容

        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(fileSize);
        when(file.getContentType()).thenReturn("video/mp4");
        when(file.isEmpty()).thenReturn(false);
        when(file.getBytes()).thenReturn(fileBytes);

        // POST /stream 返回 200 + uploadURL + uid
        Map<String, Object> createResult = new HashMap<>();
        createResult.put("uid", "test-uid-456");
        createResult.put("uploadURL", "https://upload.cloudflare.com/test-456");
        ResponseEntity<String> createResponse = new ResponseEntity<>(
                new ObjectMapper().writeValueAsString(Map.of("result", createResult)), HttpStatus.OK);

        // PUT uploadURL 返回 200 OK
        ResponseEntity<String> putResponse = new ResponseEntity<>("", HttpStatus.OK);

        // GET /stream/{uid} 返回视频信息
        Map<String, Object> videoInfo = new HashMap<>();
        videoInfo.put("uid", "test-uid-456");
        videoInfo.put("status", "queued");
        ResponseEntity<String> getResponse = new ResponseEntity<>(
                new ObjectMapper().writeValueAsString(Map.of("result", videoInfo)), HttpStatus.OK);

        when(restTemplate.exchange(endsWith("/stream"), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(createResponse);
        when(restTemplate.exchange(eq("https://upload.cloudflare.com/test-456"), eq(HttpMethod.PUT), any(), eq(String.class)))
                .thenReturn(putResponse);
        when(restTemplate.exchange(endsWith("/stream/test-uid-456"), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(getResponse);

        Map<String, Object> result = service.uploadVideo(file, "test-video-small");

        assertEquals("test-uid-456", MapUtil.str(result, "uid"));
        // 不应走 TUS 路径
        verify(restTemplate, never()).exchange(contains("/init_upload"), eq(HttpMethod.POST), any(), eq(String.class));
        verify(restTemplate, never()).exchange(contains("/uploads/"), eq(HttpMethod.PATCH), any(), eq(String.class));
        // 应走 PUT 路径
        verify(restTemplate, times(1)).exchange(eq("https://upload.cloudflare.com/test-456"), eq(HttpMethod.PUT), any(), eq(String.class));
    }

    @Test
    @DisplayName("uploadVideo: 300MB 文件走 TUS,模拟第 20 片失败后 HEAD 断点续传成功")
    void uploadVideo_300MB_tusWithRetryAndResume() throws Exception {
        // 300MB 文件,5MB/片,共 60 片
        long fileSize = 300L * 1024 * 1024;
        int chunkSize = 5 * 1024 * 1024;
        int totalChunks = 60;

        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(fileSize);
        when(file.getContentType()).thenReturn("video/mp4");
        when(file.isEmpty()).thenReturn(false);
        when(file.getInputStream()).thenReturn(new FakeInputStream(fileSize));

        // init_upload 返回 201 + Location
        HttpHeaders initRespHeaders = new HttpHeaders();
        initRespHeaders.set("Location",
                "https://api.cloudflare.com/client/v4/accounts/test-account-id/stream/test-uid-300/uploads/up-1");
        ResponseEntity<String> initResponse = new ResponseEntity<>("", initRespHeaders, HttpStatus.CREATED);
        when(restTemplate.exchange(contains("/init_upload"), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(initResponse);

        // PATCH 计数器 + 第 20 次模拟失败触发 HEAD 断点续传
        int[] patchCount = {0};
        when(restTemplate.exchange(contains("/uploads/"), eq(HttpMethod.PATCH), any(), eq(String.class)))
                .thenAnswer(invocation -> {
                    patchCount[0]++;
                    HttpEntity<byte[]> entity = invocation.getArgument(2);
                    long offset = Long.parseLong(entity.getHeaders().getFirst("Upload-Offset"));
                    long newOffset = offset + entity.getBody().length;

                    // 第 20 次模拟网络失败 (覆盖 0..100MB 的片)
                    if (patchCount[0] == 20) {
                        throw new org.springframework.web.client.ResourceAccessException(
                                "simulated network timeout");
                    }

                    HttpHeaders patchRespHeaders = new HttpHeaders();
                    patchRespHeaders.set("Upload-Offset", String.valueOf(newOffset));
                    return new ResponseEntity<>("", patchRespHeaders, HttpStatus.NO_CONTENT);
                });

        // HEAD 返回当前 offset (模拟服务端记录了第 19 片的成功位置)
        // 第 19 片覆盖 90MB..95MB,服务端 offset 应为 95MB
        when(restTemplate.exchange(contains("/uploads/"), eq(HttpMethod.HEAD), any(), eq(String.class)))
                .thenAnswer(invocation -> {
                    HttpHeaders headRespHeaders = new HttpHeaders();
                    headRespHeaders.set("Upload-Offset", String.valueOf(95L * 1024 * 1024));
                    headRespHeaders.set("Upload-Length", String.valueOf(fileSize));
                    return new ResponseEntity<>("", headRespHeaders, HttpStatus.OK);
                });

        // GET /stream/{uid} 返回视频信息
        Map<String, Object> videoInfo = new HashMap<>();
        videoInfo.put("uid", "test-uid-300");
        videoInfo.put("status", "queued");
        ResponseEntity<String> getResponse = new ResponseEntity<>(
                new ObjectMapper().writeValueAsString(Map.of("result", videoInfo)), HttpStatus.OK);
        when(restTemplate.exchange(endsWith("/stream/test-uid-300"), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(getResponse);

        Map<String, Object> result = service.uploadVideo(file, "test-video-300MB");

        assertEquals("test-uid-300", MapUtil.str(result, "uid"));

        // 验证走了 TUS 路径
        verify(restTemplate, times(1)).exchange(contains("/init_upload"), eq(HttpMethod.POST), any(), eq(String.class));
        // HEAD 被调用过 (断点续传查询)
        verify(restTemplate, atLeastOnce()).exchange(contains("/uploads/"), eq(HttpMethod.HEAD), any(), eq(String.class));
        // PATCH 调用次数 >= 60 (失败重试的片会多调一次)
        verify(restTemplate, atLeast(totalChunks)).exchange(contains("/uploads/"), eq(HttpMethod.PATCH), any(), eq(String.class));
        // 不应走 PUT 直传
        verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.PUT), any(), eq(String.class));
    }

    @Test
    @DisplayName("uploadVideo: 300MB 文件走 TUS,模拟 init_upload 阶段直接失败 (不触发重试)")
    void uploadVideo_300MB_tusInitFails() throws Exception {
        long fileSize = 300L * 1024 * 1024;

        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(fileSize);
        when(file.getContentType()).thenReturn("video/mp4");
        when(file.isEmpty()).thenReturn(false);
        when(file.getInputStream()).thenReturn(new FakeInputStream(fileSize));

        // init_upload 抛异常 (例如 API token 无效)
        when(restTemplate.exchange(contains("/init_upload"), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(new org.springframework.web.client.HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        // 期望抛 IllegalStateException
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.uploadVideo(file, "test-init-fail"));

        // 验证没走 PATCH 路径 (init_upload 就失败了)
        verify(restTemplate, never()).exchange(contains("/uploads/"), eq(HttpMethod.PATCH), any(), eq(String.class));
        verify(restTemplate, never()).exchange(contains("/uploads/"), eq(HttpMethod.HEAD), any(), eq(String.class));
        // init_upload 调用 1 次
        verify(restTemplate, times(1)).exchange(contains("/init_upload"), eq(HttpMethod.POST), any(), eq(String.class));
    }

    /**
     * 用于 TUS 测试的 InputStream:返回指定长度的零字节流,但不占用对应大小的内存。
     * 让 250MB 的 TUS 测试不需要真的分配 250MB 字节数组。
     */
    private static class FakeInputStream extends InputStream {
        private long remaining;

        FakeInputStream(long size) {
            this.remaining = size;
        }

        @Override
        public int read() {
            if (remaining <= 0) return -1;
            remaining--;
            return 0;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (remaining <= 0) return -1;
            int toRead = (int) Math.min(len, remaining);
            Arrays.fill(b, off, off + toRead, (byte) 0);
            remaining -= toRead;
            return toRead;
        }
    }
}