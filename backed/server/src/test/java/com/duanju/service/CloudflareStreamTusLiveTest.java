package com.duanju.service;

import com.duanju.util.MapUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Cloudflare Stream TUS 真实 API 集成测试。
 *
 * <p>仅在以下环境变量都存在时运行 (部署后在生产/测试环境执行):
 * <ul>
 *   <li>TUS_TEST_CLOUDFLARE_ACCOUNT_ID</li>
 *   <li>TUS_TEST_CLOUDFLARE_API_TOKEN</li>
 *   <li>TUS_TEST_VIDEO_FILE (本地视频文件路径,如 /tmp/test-300mb.mp4)</li>
 * </ul>
 *
 * <p>运行命令:
 * <pre>
 * mvn test -Dtest=CloudflareStreamTusLiveTest \
 *   -DTUS_TEST_CLOUDFLARE_ACCOUNT_ID=xxx \
 *   -DTUS_TEST_CLOUDFLARE_API_TOKEN=xxx \
 *   -DTUS_TEST_VIDEO_FILE=/tmp/test-300mb.mp4
 * </pre>
 *
 * <p>注意事项:
 * <ul>
 *   <li>会产生真实 Cloudflare Stream 计费 (存储 + 流量)</li>
 *   <li>上传的视频会保留在 Cloudflare,测试后请手动删除</li>
 *   <li>测试用视频需要是合法 mp4 文件,大小建议 200-500MB</li>
 * </ul>
 */
@DisplayName("Cloudflare Stream TUS 真实 API 集成测试")
@EnabledIfEnvironmentVariable(named = "TUS_TEST_CLOUDFLARE_ACCOUNT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TUS_TEST_CLOUDFLARE_API_TOKEN", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TUS_TEST_VIDEO_FILE", matches = ".+")
class CloudflareStreamTusLiveTest {

    @Test
    @DisplayName("真实上传 300MB 视频到 Cloudflare Stream (TUS 协议)")
    void uploadLargeVideo_viaTus_realApi() throws Exception {
        // 1. 从环境变量读取凭证
        String accountId = System.getenv("TUS_TEST_CLOUDFLARE_ACCOUNT_ID");
        String apiToken = System.getenv("TUS_TEST_CLOUDFLARE_API_TOKEN");
        String videoFilePath = System.getenv("TUS_TEST_VIDEO_FILE");

        // 2. 构造 CloudflareStreamService (不走 Spring 启动)
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(60_000);
        factory.setReadTimeout(600_000); // 10 分钟,大文件上传慢
        RestTemplate restTemplate = new RestTemplate(factory);

        // objectMapper + dramaEpisodeService 本测试不调用,用 mock 占位
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.duanju.service.entity.DramaEpisodeService mockEpisodeService =
            org.mockito.Mockito.mock(com.duanju.service.entity.DramaEpisodeService.class);

        CloudflareStreamService service = new CloudflareStreamService(restTemplate, objectMapper, mockEpisodeService);
        // 通过反射注入配置 (生产中走 @Value 注入,测试手动注入)
        injectField(service, "enabled", true);
        injectField(service, "accountId", accountId);
        injectField(service, "apiToken", apiToken);
        injectField(service, "signingKey", ""); // 本测试不验证签名,留空
        injectField(service, "signedUrlTtlSeconds", 3600L);
        injectField(service, "hlsPathPrefix", "https://watch.cloudflarestream.com");
        injectField(service, "uploadTimeoutSeconds", 3600L);
        // TUS 调优字段 (生产中走 @Value 注入,测试手动注入默认值)
        injectField(service, "directUploadThresholdBytes", 200L * 1024 * 1024);
        injectField(service, "tusChunkSizeBytes", 5 * 1024 * 1024);
        injectField(service, "tusMaxRetries", 3);
        injectField(service, "tusRetryBaseIntervalMs", 1000L);

        // 3. 校验视频文件存在
        Path videoFile = Path.of(videoFilePath);
        if (!Files.exists(videoFile)) {
            throw new IllegalStateException("test video file not found: " + videoFilePath);
        }
        long fileSize = Files.size(videoFile);
        System.out.println("=== TUS Live Test ===");
        System.out.println("Video file: " + videoFilePath);
        System.out.println("File size: " + (fileSize / 1024 / 1024) + " MB");

        // 4. 真实上传 (走 TUS 路径,文件 > 200MB)
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("test", "tus-live-test");
        meta.put("source", "integration-test");

        long startMs = System.currentTimeMillis();
        Map<String, Object> result = service.uploadVideoFromFile(videoFile, "tus-live-test-" + startMs, meta);
        long durationMs = System.currentTimeMillis() - startMs;

        // 5. 验证返回结果
        String uid = MapUtil.str(result, "uid");
        assertNotNull(uid, "返回的 uid 不应为空");

        System.out.println("=== Upload Success ===");
        System.out.println("UID: " + uid);
        System.out.println("Duration: " + (durationMs / 1000) + "s");
        System.out.println("Average speed: " + (fileSize / 1024 / 1024 / (durationMs / 1000)) + " MB/s");
        System.out.println();
        System.out.println("!!! 请手动到 Cloudflare Dashboard 删除此测试视频:");
        System.out.println("    https://dash.cloudflare.com/" + accountId + "/stream/" + uid);
        System.out.println("    或调 API: DELETE /accounts/" + accountId + "/stream/" + uid);

        // uid 非空即视为成功
        assertNotNull(uid);
    }

    /** 通过反射注入字段值 (测试用,绕过 Spring @Value 注入)。 */
    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
