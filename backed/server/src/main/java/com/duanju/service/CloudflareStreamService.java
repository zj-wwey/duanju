package com.duanju.service;

import com.duanju.util.MapUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Cloudflare Stream 视频托管服务。
 *
 * <p>功能:</p>
 * <ul>
 *   <li>创建 Cloudflare Stream 视频 (direct upload URL 或 direct upload)</li>
 *   <li>上传视频文件到 Cloudflare Stream</li>
 *   <li>生成签名播放 URL (Signed Delivery URL) 用于限时授权访问</li>
 *   <li>获取 HLS 播放清单 (m3u8 URL)</li>
 *   <li>查询视频状态、删除视频</li>
 * </ul>
 *
 * <p>Cloudflare Stream API 文档:</p>
 * <a href="https://developers.cloudflare.com/stream/">https://developers.cloudflare.com/stream/</a>
 */
@Service
public class CloudflareStreamService {

    private static final Logger log = LoggerFactory.getLogger(CloudflareStreamService.class);

    private static final String API_BASE = "https://api.cloudflare.com/client/v4";

    @Value("${duanju.cloudflare.enabled:false}")
    private boolean enabled;

    @Value("${duanju.cloudflare.account-id:}")
    private String accountId;

    @Value("${duanju.cloudflare.api-token:}")
    private String apiToken;

    @Value("${duanju.cloudflare.signing-key:}")
    private String signingKey;

    @Value("${duanju.cloudflare.signed-url-ttl-seconds:3600}")
    private int signedUrlTtlSeconds;

    @Value("${duanju.cloudflare.hls-path-prefix:https://watch.cloudflarestream.com}")
    private String hlsPathPrefix;

    @Value("${duanju.cloudflare.upload-timeout-seconds:3600}")
    private int uploadTimeoutSeconds;

    @Value("${duanju.cloudflare.allowed-mime-types:video/mp4,video/x-m4v,video/webm,video/quicktime}")
    private String allowedMimeTypes;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public CloudflareStreamService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 检查 Cloudflare Stream 是否启用。
     */
    public boolean isEnabled() {
        return enabled && accountId != null && !accountId.isBlank()
                && apiToken != null && !apiToken.isBlank();
    }

    private void assertEnabled() {
        if (!isEnabled()) {
            throw new IllegalStateException("cloudflare stream not enabled or not configured");
        }
    }

    /**
     * 创建一个 Cloudflare Stream 视频资源 (direct upload URL)。
     *
     * <p>返回的 Map 包含:</p>
     * <ul>
     *   <li>uid - Cloudflare 视频 ID</li>
     *   <li>uploadURL - 上传视频的 PUT URL</li>
     *   <li>signingKey - 当前签名密钥ID</li>
     *   <li>requireSignedURLs - 是否需要签名URL</li>
     * </ul>
     *
     * @param name 视频名称 (可为 null)
     * @param meta 元数据 (可为 null,会存储在 Cloudflare)
     */
    public Map<String, Object> createStream(String name, Map<String, String> meta) {
        assertEnabled();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            if (name != null && !name.isBlank()) {
                body.put("name", name);
            }
            body.put("requireSignedURLs", true);
            body.put("public", false);
            if (meta != null && !meta.isEmpty()) {
                body.put("meta", meta);
            }

            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            String url = API_BASE + "/accounts/" + accountId + "/stream";

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            return parseStreamResponse(response.getBody());
        } catch (Exception e) {
            log.error("Cloudflare Stream create failed: accountId={}", accountId, e);
            throw new IllegalStateException("cloudflare stream create failed: " + e.getMessage(), e);
        }
    }

    /**
     * 上传视频文件到 Cloudflare Stream (使用 multipart/form-data direct upload)。
     *
     * <p>适用于小文件 (< 200MB)。大文件建议使用 createStream() 返回的 uploadURL 做 PUT 上传。</p>
     */
    public Map<String, Object> uploadVideo(MultipartFile file, String name) {
        assertEnabled();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("video file is empty");
        }
        validateMimeType(file.getContentType());
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("requireSignedURLs", true);
            body.put("public", false);
            if (name != null && !name.isBlank()) {
                body.put("name", name);
            }

            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            String url = API_BASE + "/accounts/" + accountId + "/stream";

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            Map<String, Object> result = parseStreamResponse(response.getBody());
            String uploadUrl = MapUtil.str(result, "uploadURL");
            String uid = MapUtil.str(result, "uid");

            if (uploadUrl == null || uid == null) {
                throw new IllegalStateException("cloudflare stream create failed: missing uploadURL or uid");
            }

            // Step 2: PUT the video file to the upload URL
            HttpHeaders putHeaders = new HttpHeaders();
            putHeaders.setContentType(MediaType.parseMediaType(file.getContentType()));
            HttpEntity<byte[]> putEntity = new HttpEntity<>(file.getBytes(), putHeaders);
            restTemplate.exchange(uploadUrl, HttpMethod.PUT, putEntity, String.class);

            log.info("Cloudflare video uploaded: uid={}, size={}, name={}", uid, file.getSize(), name);
            return getVideoInfo(uid);
        } catch (Exception e) {
            log.error("Cloudflare upload failed: name={}", name, e);
            throw new IllegalStateException("cloudflare upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * 直接 PUT 上传视频字节到 uploadURL (适合大文件)。
     */
    public Map<String, Object> putUpload(String uploadUrl, byte[] videoData, String contentType) {
        assertEnabled();
        try {
            HttpHeaders headers = new HttpHeaders();
            MediaType mediaType = contentType != null
                    ? MediaType.parseMediaType(contentType)
                    : MediaType.APPLICATION_OCTET_STREAM;
            headers.setContentType(mediaType);
            headers.setContentLength(videoData.length);

            HttpEntity<byte[]> entity = new HttpEntity<>(videoData, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    uploadUrl, HttpMethod.PUT, entity, String.class);

            log.info("Cloudflare direct PUT upload completed, size={}", videoData.length);
            return MapUtil.map("uploadCompleted", true);
        } catch (Exception e) {
            log.error("Cloudflare PUT upload failed", e);
            throw new IllegalStateException("cloudflare put upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * 查询 Cloudflare Stream 视频详情 (包括 HLS URL、duration、ready 状态)。
     */
    public Map<String, Object> getVideoInfo(String uid) {
        assertEnabled();
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("uid is required");
        }
        try {
            HttpHeaders headers = buildAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            String url = API_BASE + "/accounts/" + accountId + "/stream/" + uid;

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            return parseStreamResponse(response.getBody());
        } catch (Exception e) {
            log.error("Cloudflare get video info failed: uid={}", uid, e);
            throw new IllegalStateException("cloudflare get video info failed: " + e.getMessage(), e);
        }
    }

    /**
     * 删除 Cloudflare Stream 视频。
     */
    public void deleteVideo(String uid) {
        assertEnabled();
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("uid is required");
        }
        try {
            HttpHeaders headers = buildAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            String url = API_BASE + "/accounts/" + accountId + "/stream/" + uid;

            restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
            log.info("Cloudflare video deleted: uid={}", uid);
        } catch (Exception e) {
            log.error("Cloudflare delete video failed: uid={}", uid, e);
            throw new IllegalStateException("cloudflare delete video failed: " + e.getMessage(), e);
        }
    }

    /**
     * 生成 Cloudflare Stream 签名播放 URL。
     *
     * <p>签名 URL 的有效期由 signed-url-ttl-seconds 配置。
     * 格式:{hlsPathPrefix}/{uid}/manifest?token={signature}</p>
     *
     * @param uid Cloudflare 视频 UID
     * @return 签名后的播放 URL
     */
    public String generateSignedUrl(String uid) {
        assertEnabled();
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("uid is required");
        }

        try {
            long expTime = Instant.now().getEpochSecond() + signedUrlTtlSeconds;
            String token = generateSignedToken(uid, expTime);
            return hlsPathPrefix + "/" + uid + "/manifest?token=" + token;
        } catch (Exception e) {
            log.error("Generate signed URL failed: uid={}", uid, e);
            throw new IllegalStateException("generate signed URL failed: " + e.getMessage(), e);
        }
    }

    /**
     * 生成带有效期的 Cloudflare Stream 签名 token。
     *
     * <p>Token 格式: base64url(header) + "." + base64url(payload) + "." + base64url(signature)</p>
     * <p>Payload: { "uid": "...", "exp": timestamp, "downloadable": false }</p>
     */
    public String generateSignedToken(String uid, long expTime) {
        try {
            // Header: {"alg":"HS256","typ":"JWT"}
            String header = Base64Url.encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");

            // Payload
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("uid", uid);
            payload.put("exp", expTime);
            payload.put("downloadable", false);
            String payloadJson = objectMapper.writeValueAsString(payload);
            String payloadB64 = Base64Url.encode(payloadJson);

            // Signing input
            String signingInput = header + "." + payloadB64;

            // HMAC-SHA256
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] signature = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            String signatureB64 = Base64Url.encode(signature);

            return signingInput + "." + signatureB64;
        } catch (Exception e) {
            throw new IllegalStateException("generate signed token failed: " + e.getMessage(), e);
        }
    }

    /**
     * 获取视频的 HLS 播放 URL (未签名,仅限内部管理使用)。
     * 用户播放应通过 generateSignedUrl() 获取签名版本。
     */
    public String getHlsUrl(String uid) {
        return hlsPathPrefix + "/" + uid + "/manifest";
    }

    /**
     * 处理 Cloudflare Stream 异步完成的通知 (video.completed 回调)。
     *
     * @param payload Cloudflare Stream webhook payload
     */
    @Transactional
    public void handleStreamCompleted(Map<String, Object> payload) {
        assertEnabled();
        Map<String, Object> video = MapUtil.castMap(payload.get("video"));
        if (video == null) {
            log.warn("Cloudflare Stream completed: missing video in payload");
            return;
        }
        String uid = MapUtil.str(video, "uid");
        String status = MapUtil.str(video, "status");
        log.info("Cloudflare Stream webhook: uid={}, status={}", uid, status);
    }

    // --- Private helpers ---

    private HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);
        return headers;
    }

    private Map<String, Object> parseStreamResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode result = root.get("result");
            if (result == null) {
                throw new IllegalStateException("cloudflare response missing 'result': " + responseBody);
            }
            Map<String, Object> map = objectMapper.convertValue(result, Map.class);

            // 提取有用的顶层字段
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("uid", MapUtil.str(map, "uid"));
            output.put("uploadURL", MapUtil.str(map, "uploadURL"));
            output.put("signed", MapUtil.bool(map, "signed", false));
            output.put("public", MapUtil.bool(map, "public", false));
            output.put("ready", MapUtil.bool(map, "ready", false));
            output.put("status", MapUtil.str(map, "status"));
            output.put("name", MapUtil.str(map, "name"));
            output.put("size", MapUtil.lng(map, "size"));
            output.put("durationSeconds", MapUtil.lng(map, "duration"));

            // HLS URL
            String uid = MapUtil.str(map, "uid");
            if (uid != null) {
                output.put("hls_url", getHlsUrl(uid));
                output.put("signed_hls_url", hlsPathPrefix + "/" + uid + "/manifest");
            }

            Map<String, Object> meta = MapUtil.castMap(map.get("meta"));
            if (meta != null) {
                output.put("meta", meta);
            }

            return output;
        } catch (Exception e) {
            throw new IllegalStateException("parse cloudflare response failed: " + e.getMessage(), e);
        }
    }

    private void validateMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return;
        }
        List<String> allowed = Arrays.asList(allowedMimeTypes.split(","));
        if (allowed.contains(mimeType)) {
            return;
        }
        // 允许常见的视频前缀匹配
        if (mimeType.startsWith("video/")) {
            return;
        }
        throw new IllegalArgumentException("unsupported video MIME type: " + mimeType);
    }

    /**
     * Base64 URL-safe encoding/decoding utility。
     */
    private static final class Base64Url {
        private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

        static String encode(String data) {
            return encode(data.getBytes(StandardCharsets.UTF_8));
        }

        static String encode(byte[] data) {
            return ENCODER.encodeToString(data);
        }

        static byte[] decode(String data) {
            return Base64.getUrlDecoder().decode(data);
        }
    }
}