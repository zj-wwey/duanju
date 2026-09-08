package com.duanju.service;

import com.duanju.entity.DramaEpisode;
import com.duanju.service.entity.DramaEpisodeService;
import com.duanju.util.MapUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /** Cloudflare Stream 直传 maxDurationSeconds 上限: 10 小时 (36000 秒) */
    private static final int MAX_VIDEO_DURATION_SECONDS = 36000;

    // --- TUS 断点续传上传相关常量 ---
    /** TUS 协议版本号 (协议规范常量,不配置化) */
    private static final String TUS_PROTOCOL_VERSION = "1.0.0";

    // TUS 调优建议:
    // - tus-chunk-size-bytes: 网络稳定用 10MB (10485760),网络差用 2MB (2097152),默认 5MB
    // - tus-max-retries: 偶发抖动 3 次,极差网络 5 次,默认 3
    // - tus-retry-base-interval-ms: 指数退避基础间隔,默认 1000ms (重试间隔 1s/2s/4s)

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

    @Value("${duanju.cloudflare.direct-upload-threshold-bytes:209715200}")
    private long directUploadThresholdBytes;  // 直传阈值:文件 >= 此大小走 TUS,默认 200MB

    @Value("${duanju.cloudflare.tus-chunk-size-bytes:5242880}")
    private int tusChunkSizeBytes;  // TUS 分片大小,默认 5MB

    @Value("${duanju.cloudflare.tus-max-retries:3}")
    private int tusMaxRetries;  // TUS 单分片最大重试次数

    @Value("${duanju.cloudflare.tus-retry-base-interval-ms:1000}")
    private long tusRetryBaseIntervalMs;  // 指数退避基础间隔 (毫秒)

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final DramaEpisodeService dramaEpisodeService;

    public CloudflareStreamService(RestTemplate restTemplate,
                                   ObjectMapper objectMapper,
                                   DramaEpisodeService dramaEpisodeService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.dramaEpisodeService = dramaEpisodeService;
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
     *   <li>uploadURL - 上传视频的 POST URL (直传模式,客户端一次性 POST 原始字节)</li>
     *   <li>signed_url - 可选,签名播放 URL</li>
     * </ul>
     *
     * @param name 视频名称 (可为 null)
     * @param meta 元数据 (可为 null,会存储在 Cloudflare)
     */
    public Map<String, Object> createStream(String name, Map<String, String> meta) {
        return createDirectUpload(name, meta);
    }

    /**
     * 创建 Cloudflare Stream 直传资源 (direct upload URL)。
     *
     * <p>必须用 POST /stream/direct_upload 端点:POST /stream 只接受 multipart 文件
     * 或 {"url":...} 复制,直接发 JSON 会返回 10004 Decoding Error。</p>
     *
     * <p>direct_upload 不支持 name 字段,视频名称写入 meta.name 便于追溯。</p>
     */
    private Map<String, Object> createDirectUpload(String name, Map<String, ?> meta) {
        assertEnabled();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            // Cloudflare 限制 maxDurationSeconds 最大 36000 秒 (10 小时)
            body.put("maxDurationSeconds", MAX_VIDEO_DURATION_SECONDS);
            body.put("requireSignedURLs", true);
            Map<String, Object> mergedMeta = new LinkedHashMap<>();
            if (name != null && !name.isBlank()) {
                mergedMeta.put("name", name);
            }
            if (meta != null) {
                mergedMeta.putAll(meta);
            }
            if (!mergedMeta.isEmpty()) {
                body.put("meta", mergedMeta);
            }

            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            String url = API_BASE + "/accounts/" + accountId + "/stream/direct_upload";

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            return parseStreamResponse(response.getBody());
        } catch (Exception e) {
            log.error("Cloudflare Stream direct upload create failed: accountId={}", accountId, e);
            throw new IllegalStateException("cloudflare stream create failed: " + e.getMessage(), e);
        }
    }

    /**
     * 上传视频文件到 Cloudflare Stream (使用 multipart/form-data direct upload)。
     *
     * <p>路由策略:</p>
     * <ul>
     *   <li>文件 &lt; 200MB:走 multipart direct upload (POST /stream 拿 uploadURL → PUT 上传字节)</li>
     *   <li>文件 &gt;= 200MB:走 TUS 断点续传 (POST /stream/init_upload → 循环 PATCH 5MB 分片)</li>
     * </ul>
     *
     * <p>大文件改走 TUS 是因为 Cloudflare Stream direct PUT 上限 200MB,且原实现会把整个文件读到 byte[] 容易 OOM。</p>
     */
    public Map<String, Object> uploadVideo(MultipartFile file, String name) {
        assertEnabled();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("video file is empty");
        }
        validateMimeType(file.getContentType());
        long fileSize = file.getSize();
        try {
            // 文件 >= 直传阈值:走 TUS 断点续传 (避开直传上限,避免 OOM)
            if (fileSize >= directUploadThresholdBytes) {
                log.info("uploading video via TUS (size={}MB, exceeds {}MB threshold)",
                        fileSize / (1024 * 1024), directUploadThresholdBytes / (1024 * 1024));
                String mimeType = file.getContentType() != null
                        ? file.getContentType()
                        : MediaType.APPLICATION_OCTET_STREAM_VALUE;
                try (InputStream is = file.getInputStream()) {
                    return uploadVideoViaTus(is, fileSize, name, null, mimeType);
                }
            }

            // 文件 < 200MB:走 direct upload 逻辑 (小文件直传快,TUS 协议开销反而大)
            Map<String, Object> result = createDirectUpload(name, null);
            String uploadUrl = MapUtil.str(result, "uploadURL");
            String uid = MapUtil.str(result, "uid");

            if (uploadUrl == null || uid == null) {
                throw new IllegalStateException("cloudflare stream create failed: missing uploadURL or uid");
            }

            // Step 2: POST the video file to the upload URL
            // Cloudflare Stream direct upload requires POST (not PUT) per latest API
            HttpHeaders putHeaders = new HttpHeaders();
            putHeaders.setContentType(MediaType.parseMediaType(file.getContentType()));
            HttpEntity<byte[]> putEntity = new HttpEntity<>(file.getBytes(), putHeaders);
            restTemplate.exchange(uploadUrl, HttpMethod.POST, putEntity, String.class);

            log.info("Cloudflare video uploaded: uid={}, size={}, name={}", uid, file.getSize(), name);
            return getVideoInfo(uid);
        } catch (Exception e) {
            log.error("Cloudflare upload failed: name={}", name, e);
            throw new IllegalStateException("cloudflare upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * 从本地文件路径上传到 Cloudflare Stream (VPS 中转批量上传模式)。
     *
     * <p>适用于 VPS 中转场景:管理员先 scp 视频到 VPS,后端读取本地文件直接 PUT 到 Cloudflare。
     * 跳过 multipart 转换,大文件场景内存占用低。</p>
     *
     * @param file 本地文件路径
     * @param name Cloudflare 视频显示名称 (可为 null)
     * @param meta 视频元信息 (写入 Cloudflare meta 字段,便于追溯)
     * @return Cloudflare 视频信息 (含 uid)
     */
    public Map<String, Object> uploadVideoFromFile(Path file, String name, Map<String, Object> meta) {
        assertEnabled();
        if (file == null) {
            throw new IllegalArgumentException("file path is required");
        }
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("file not found: " + file);
        }
        try {
            long fileSize = Files.size(file);

            // 文件 >= 直传阈值:走 TUS 断点续传 (避开直传上限,避免 OOM)
            if (fileSize >= directUploadThresholdBytes) {
                log.info("uploading video from file via TUS (size={}MB, threshold={}MB)",
                        fileSize / (1024 * 1024), directUploadThresholdBytes / (1024 * 1024));
                try (InputStream is = Files.newInputStream(file)) {
                    return uploadVideoViaTus(is, fileSize, name, meta, MediaType.APPLICATION_OCTET_STREAM_VALUE);
                }
            }

            // Step 1: create direct upload resource, get uploadURL + uid
            Map<String, Object> result = createDirectUpload(name, meta);
            String uploadUrl = MapUtil.str(result, "uploadURL");
            String uid = MapUtil.str(result, "uid");
            if (uploadUrl == null || uid == null) {
                throw new IllegalStateException("cloudflare stream create failed: missing uploadURL or uid");
            }

            // Step 2: POST file bytes to uploadURL (流式 InputStreamResource,避免大文件 OOM)
            // Cloudflare Stream direct upload requires POST (not PUT) per latest API
            try (InputStream fileInputStream = Files.newInputStream(file)) {
                InputStreamResource resource = new InputStreamResource(fileInputStream);
                HttpHeaders putHeaders = new HttpHeaders();
                putHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                putHeaders.setContentLength(fileSize);
                HttpEntity<InputStreamResource> putEntity = new HttpEntity<>(resource, putHeaders);
                restTemplate.exchange(uploadUrl, HttpMethod.POST, putEntity, String.class);
            }

            log.info("Cloudflare video uploaded from file: uid={}, size={}, name={}", uid, fileSize, name);
            return getVideoInfo(uid);
        } catch (Exception e) {
            log.error("Cloudflare upload from file failed: file={}, name={}", file, name, e);
            throw new IllegalStateException("cloudflare upload from file failed: " + e.getMessage(), e);
        }
    }

    /**
     * 使用 TUS 协议上传视频 (支持大文件断点续传,无 200MB 限制)。
     *
     * <p>流程:</p>
     * <ol>
     *   <li>POST /stream/init_upload 创建 TUS session,从响应头 Location 拿到 upload URL + uid</li>
     *   <li>循环 PATCH 分片上传 (5MB/片),每次从响应头读取 Upload-Offset 确认</li>
     *   <li>失败时 HEAD 查询当前 offset,从断点续传 (最多重试 3 次)</li>
     *   <li>全部上传完后调用 getVideoInfo(uid) 返回视频信息</li>
     * </ol>
     *
     * <p>关键点:</p>
     * <ul>
     *   <li>不把整个文件读到 byte[],用 InputStream 流式读取分片 byte[5MB],避免 OOM</li>
     *   <li>每片 PATCH 失败重试 3 次,每次先 HEAD 查询当前 offset 再 PATCH</li>
     *   <li>断点续传:HEAD 返回的 offset 可能 &gt; 期望 offset,需切片跳过已上传字节</li>
     * </ul>
     *
     * @param inputStream 视频输入流 (调用方负责关闭)
     * @param fileSize 文件总字节数
     * @param name 视频显示名 (可为 null)
     * @param meta 元信息 (可为 null,目前未在 TUS init 阶段写入,保留参数兼容)
     * @param mimeType MIME 类型 (如 video/mp4,用于日志记录)
     * @return Cloudflare 视频信息 (含 uid)
     */
    private Map<String, Object> uploadVideoViaTus(
            InputStream inputStream, long fileSize,
            String name, Map<String, Object> meta, String mimeType) throws Exception {

        long startTime = System.currentTimeMillis();
        // TUS 协议要求 filename metadata,无 name 时用默认名
        String fileName = (name != null && !name.isBlank()) ? name : "video.mp4";

        // --- Step 1: POST init_upload 创建 TUS session ---
        HttpHeaders initHeaders = buildAuthHeaders();
        initHeaders.set("TUS-Resumable", TUS_PROTOCOL_VERSION);
        initHeaders.set("Upload-Length", String.valueOf(fileSize));

        // Upload-Metadata: filename <base64(name)>,size <base64(fileSize)> (参考 TUS spec)
        String metadata = "filename " + Base64.getEncoder().encodeToString(fileName.getBytes(StandardCharsets.UTF_8))
                + ",size " + Base64.getEncoder().encodeToString(String.valueOf(fileSize).getBytes(StandardCharsets.UTF_8));
        initHeaders.set("Upload-Metadata", metadata);

        HttpEntity<Void> initEntity = new HttpEntity<>(initHeaders);
        String initUrl = API_BASE + "/accounts/" + accountId + "/stream/init_upload";

        log.info("TUS init_upload: url={}, fileSize={}, mimeType={}, name={}", initUrl, fileSize, mimeType, name);

        ResponseEntity<String> initResponse = restTemplate.exchange(
                initUrl, HttpMethod.POST, initEntity, String.class);

        if (initResponse.getStatusCode().value() != HttpStatus.CREATED.value()) {
            throw new IllegalStateException("TUS init_upload failed, status=" + initResponse.getStatusCode()
                    + ", body=" + initResponse.getBody());
        }

        // 从 Location 头拿 TUS upload URL
        String uploadLocation = initResponse.getHeaders().getFirst("Location");
        if (uploadLocation == null || uploadLocation.isBlank()) {
            throw new IllegalStateException("TUS init_upload response missing Location header, status="
                    + initResponse.getStatusCode());
        }
        // Location URL 格式: .../stream/{uid}/uploads/{upload_id},从中提取 uid
        String uid = extractUidFromTusLocation(uploadLocation);
        log.info("TUS session created: uid={}, uploadLocation={}", uid, uploadLocation);

        // --- Step 2: 流式循环 PATCH 分片 ---
        long offset = 0;
        int chunksUploaded = 0;
        byte[] buffer = new byte[tusChunkSizeBytes];

        while (offset < fileSize) {
            int bytesRead = readChunk(inputStream, buffer);
            if (bytesRead == -1) {
                throw new IllegalStateException("TUS upload: stream ended before all bytes uploaded, "
                        + "offset=" + offset + ", fileSize=" + fileSize);
            }
            // 复制出当前分片 (避免 retry 时切片污染 buffer)
            byte[] chunk = Arrays.copyOf(buffer, bytesRead);

            long newOffset = patchChunkWithRetry(uploadLocation, offset, chunk);
            offset = newOffset;
            chunksUploaded++;

            // 进度日志 (每片都打,便于排查卡住的分片)
            log.info("TUS chunk uploaded: uid={}, chunk={}, offset={}/{}, progress={}%",
                    uid, chunksUploaded, offset, fileSize,
                    fileSize == 0 ? 100 : (offset * 100 / fileSize));
        }

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("Cloudflare video uploaded via TUS: uid={}, size={}, chunks={}, durationMs={}",
                uid, fileSize, chunksUploaded, durationMs);

        // --- Step 3: 返回视频信息 (此时 Cloudflare 已开始异步转码) ---
        return getVideoInfo(uid);
    }

    /**
     * PATCH 一个分片到 TUS upload location,失败时通过 HEAD 查询当前 offset 续传,最多重试 tusMaxRetries 次。
     *
     * <p>断点续传逻辑:</p>
     * <ul>
     *   <li>PATCH 失败 → 指数退避等待 → HEAD 查询服务端实际 offset</li>
     *   <li>若 actualOffset &gt; 期望 offset,说明部分字节已上传,跳过这些字节切片后重试</li>
     *   <li>若 actualOffset &gt;= 期望 offset + chunk.length,说明整片已上传 (尽管 PATCH 报错),无需重试</li>
     * </ul>
     *
     * @param uploadLocation TUS upload URL (init_upload 返回的 Location)
     * @param expectedOffset 期望的起始 offset
     * @param chunk 待上传的分片字节
     * @return PATCH 成功后的新 offset (服务端返回的 Upload-Offset)
     */
    private long patchChunkWithRetry(String uploadLocation, long expectedOffset, byte[] chunk) throws Exception {
        Exception lastException = null;
        long currentOffset = expectedOffset;
        byte[] remainingChunk = chunk;

        for (int attempt = 1; attempt <= tusMaxRetries; attempt++) {
            try {
                HttpHeaders patchHeaders = buildAuthHeaders();
                patchHeaders.set("TUS-Resumable", TUS_PROTOCOL_VERSION);
                patchHeaders.set("Upload-Offset", String.valueOf(currentOffset));
                patchHeaders.setContentType(MediaType.parseMediaType("application/offset+octet-stream"));
                patchHeaders.setContentLength(remainingChunk.length);

                HttpEntity<byte[]> patchEntity = new HttpEntity<>(remainingChunk, patchHeaders);
                ResponseEntity<String> patchResponse = restTemplate.exchange(
                        uploadLocation, HttpMethod.PATCH, patchEntity, String.class);

                if (patchResponse.getStatusCode().value() != HttpStatus.NO_CONTENT.value()) {
                    throw new IllegalStateException("TUS PATCH failed, status=" + patchResponse.getStatusCode()
                            + ", body=" + patchResponse.getBody());
                }

                String newOffsetStr = patchResponse.getHeaders().getFirst("Upload-Offset");
                if (newOffsetStr == null) {
                    // 服务端未返回 Upload-Offset,fallback 计算
                    return currentOffset + remainingChunk.length;
                }
                return Long.parseLong(newOffsetStr);
            } catch (Exception e) {
                lastException = e;
                // 指数退避:第 attempt 次重试的间隔 = base * 2^(attempt-1)
                // attempt=1: base, attempt=2: base*2, attempt=3: base*4 (默认 1s/2s/4s)
                long backoffMs = tusRetryBaseIntervalMs * (1L << (attempt - 1));
                log.warn("TUS PATCH failed attempt {}/{}, offset={}, backing off {}ms: {}",
                        attempt, tusMaxRetries, currentOffset, backoffMs, e.getMessage());

                if (attempt < tusMaxRetries) {
                    // 重试前指数退避 (避免网络抖动连续失败)
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("TUS upload interrupted", ie);
                    }

                    // HEAD 查询当前 offset,判断是否需要切片续传
                    try {
                        long actualOffset = headUploadOffset(uploadLocation);
                        log.info("TUS HEAD current offset={}, expected offset={}", actualOffset, currentOffset);

                        if (actualOffset > currentOffset) {
                            // 部分字节已上传,跳过这些字节切片
                            int skip = (int) (actualOffset - currentOffset);
                            if (skip >= remainingChunk.length) {
                                // 整片已上传 (尽管 PATCH 报错),无需重试,直接返回 actualOffset
                                log.info("TUS chunk already fully uploaded despite PATCH error, skipping, actualOffset={}", actualOffset);
                                return actualOffset;
                            }
                            remainingChunk = Arrays.copyOfRange(remainingChunk, skip, remainingChunk.length);
                            currentOffset = actualOffset;
                        }
                    } catch (Exception headEx) {
                        log.warn("TUS HEAD failed, will retry PATCH at original offset: {}", headEx.getMessage());
                    }
                }
            }
        }

        throw new IllegalStateException("TUS PATCH failed after " + tusMaxRetries
                + " retries, offset=" + currentOffset, lastException);
    }

    /**
     * HEAD 查询 TUS upload 当前 offset (用于断点续传)。
     *
     * @param uploadLocation TUS upload URL
     * @return 服务端当前 offset (Upload-Offset 响应头)
     */
    private long headUploadOffset(String uploadLocation) throws Exception {
        HttpHeaders headers = buildAuthHeaders();
        headers.set("TUS-Resumable", TUS_PROTOCOL_VERSION);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                uploadLocation, HttpMethod.HEAD, entity, String.class);
        String offsetStr = response.getHeaders().getFirst("Upload-Offset");
        if (offsetStr == null) {
            throw new IllegalStateException("TUS HEAD response missing Upload-Offset header, status=" + response.getStatusCode());
        }
        return Long.parseLong(offsetStr);
    }

    /**
     * 从 TUS Location URL 提取 Cloudflare 视频 uid。
     *
     * <p>Location URL 格式:</p>
     * <pre>https://api.cloudflare.com/client/v4/accounts/{account_id}/stream/{uid}/uploads/{upload_id}</pre>
     *
     * @param locationUrl TUS init_upload 返回的 Location URL
     * @return 视频 uid
     */
    private String extractUidFromTusLocation(String locationUrl) {
        int streamIdx = locationUrl.indexOf("/stream/");
        if (streamIdx < 0) {
            throw new IllegalStateException("TUS Location URL missing /stream/ segment: " + locationUrl);
        }
        String afterStream = locationUrl.substring(streamIdx + "/stream/".length());
        int uploadsIdx = afterStream.indexOf("/uploads/");
        if (uploadsIdx < 0) {
            throw new IllegalStateException("TUS Location URL missing /uploads/ segment: " + locationUrl);
        }
        return afterStream.substring(0, uploadsIdx);
    }

    /**
     * 从 InputStream 读取一个分片到 buffer,尽量填满 buffer (循环读直到 buffer 满或 EOF)。
     *
     * @param inputStream 输入流
     * @param buffer 目标缓冲区 (容量 = tusChunkSizeBytes)
     * @return 实际读取的字节数;若到 EOF 且未读到任何字节,返回 -1
     */
    private int readChunk(InputStream inputStream, byte[] buffer) throws IOException {
        int totalRead = 0;
        while (totalRead < buffer.length) {
            int read = inputStream.read(buffer, totalRead, buffer.length - totalRead);
            if (read == -1) {
                return totalRead == 0 ? -1 : totalRead;
            }
            totalRead += read;
        }
        return totalRead;
    }

    /**
     * 直接 POST 上传视频字节到 uploadURL (适合大文件)。
     */
    public Map<String, Object> postUpload(String uploadUrl, byte[] videoData, String contentType) {
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
                    uploadUrl, HttpMethod.POST, entity, String.class);

            log.info("Cloudflare direct POST upload completed, size={}", videoData.length);
            return MapUtil.map("uploadCompleted", true);
        } catch (Exception e) {
            log.error("Cloudflare POST upload failed", e);
            throw new IllegalStateException("cloudflare post upload failed: " + e.getMessage(), e);
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
            // 优先使用 /token 端点 (API 方式,不需要 Signing Key)
            // 如果 Signing Key 已配置且有效,则用本地 HMAC-SHA256 签名
            if (signingKey != null && !signingKey.isBlank()) {
                long expTime = Instant.now().getEpochSecond() + signedUrlTtlSeconds;
                String token = generateSignedToken(uid, expTime);
                return hlsPathPrefix + "/" + uid + "/manifest?token=" + token;
            }
            // 回退:调用 Cloudflare /token 端点生成签名 (适合日签名量 <1000 次)
            String token = fetchTokenFromEndpoint(uid);
            return hlsPathPrefix + "/" + uid + "/manifest?token=" + token;
        } catch (Exception e) {
            log.error("Generate signed URL failed: uid={}", uid, e);
            throw new IllegalStateException("generate signed URL failed: " + e.getMessage(), e);
        }
    }

    /**
     * 生成带有效期的 Cloudflare Stream 签名 token (RSA-SHA256 签名)。
     *
     * <p>Token 格式: base64url(header) + "." + base64url(payload) + "." + base64url(signature)</p>
     * <p>Header: {"alg":"RS256","typ":"JWT"}</p>
     * <p>Payload: { "uid": "...", "exp": timestamp, "downloadable": false }</p>
     * <p>Signing Key: Cloudflare Stream Signing Key (RSA PEM 格式私钥)</p>
     */
    public String generateSignedToken(String uid, long expTime) {
        try {
            // Header: {"alg":"RS256","typ":"JWT"}
            String header = Base64Url.encode("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");

            // Payload
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("uid", uid);
            payload.put("exp", expTime);
            payload.put("downloadable", false);
            String payloadJson = objectMapper.writeValueAsString(payload);
            String payloadB64 = Base64Url.encode(payloadJson);

            // Signing input
            String signingInput = header + "." + payloadB64;

            // RSA-SHA256 签名
            java.security.Signature rsaSignature = java.security.Signature.getInstance("SHA256withRSA");
            java.security.PrivateKey privateKey = parseRSAPrivateKey(signingKey);
            rsaSignature.initSign(privateKey);
            rsaSignature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            byte[] signature = rsaSignature.sign();
            String signatureB64 = Base64Url.encode(signature);

            return signingInput + "." + signatureB64;
        } catch (Exception e) {
            throw new IllegalStateException("generate signed token failed: " + e.getMessage(), e);
        }
    }

    /**
     * 解析 PEM 格式 RSA 私钥 (PKCS#1)。
     * <p>手动解析 ASN.1 DER 结构,避免对 sun.security.util 内部 API 的依赖。</p>
     */
    private java.security.PrivateKey parseRSAPrivateKey(String pemKey) throws Exception {
        String key = pemKey.trim();
        key = key.replace("-----BEGIN RSA PRIVATE KEY-----", "")
                 .replace("-----END RSA PRIVATE KEY-----", "")
                 .replaceAll("\\s", "");

        byte[] der = java.util.Base64.getDecoder().decode(key);

        // 跳过 SEQUENCE tag + length,获取 content
        int[] pos = {0};
        readTag(der, pos, 0x30); // SEQUENCE
        int len = readLength(der, pos);
        // 读取各 INTEGER 字段 (跳过第0个version, 取第1个modulus和第3个privateExponent)
        java.math.BigInteger modulus = null;
        java.math.BigInteger privateExponent = null;
        for (int i = 0; i < 9; i++) {
            int tag = readTag(der, pos, 0x02); // INTEGER
            int ilen = readLength(der, pos);
            byte[] val = new byte[ilen];
            System.arraycopy(der, pos[0], val, 0, ilen);
            pos[0] += ilen;
            java.math.BigInteger bi = new java.math.BigInteger(val);
            if (i == 1) modulus = bi;          // index 1 = modulus
            else if (i == 3) { privateExponent = bi; break; } // index 3 = privateExponent
        }

        java.security.spec.RSAPrivateKeySpec keySpec =
                new java.security.spec.RSAPrivateKeySpec(modulus, privateExponent);
        return java.security.KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    /** 读取 ASN.1 TLV 的 tag 字节并前进指针 */
    private static int readTag(byte[] der, int[] pos, int expected) {
        int tag = der[pos[0]] & 0xFF;
        pos[0]++;
        if (tag != expected) throw new IllegalStateException("Expected tag 0x" + Integer.toHexString(expected) + " got 0x" + Integer.toHexString(tag));
        return tag;
    }

    /** 读取 ASN.1 TLV 的 length 字段并前进指针 */
    private static int readLength(byte[] der, int[] pos) {
        int first = der[pos[0]] & 0xFF;
        pos[0]++;
        if ((first & 0x80) == 0) return first; // 短格式
        int numBytes = first & 0x7F;
        int len = 0;
        for (int i = 0; i < numBytes; i++) { len = (len << 8) | (der[pos[0]] & 0xFF); pos[0]++; }
        return len;
    }

    /**
     * 通过 Cloudflare /token 端点生成签名 (不需要 Signing Key)。
     * <p>适合日签名量 < 1000 次的场景。</p>
     * <p>POST /accounts/{accountId}/stream/{uid}/token</p>
     */
    public String fetchTokenFromEndpoint(String uid) {
        try {
            Map<String, Object> body = Map.of(
                    "exp", Instant.now().getEpochSecond() + signedUrlTtlSeconds,
                    "downloadable", false
            );

            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            String url = API_BASE + "/accounts/" + accountId + "/stream/" + uid + "/token";

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            Map<String, Object> result = parseStreamResponse(response.getBody());
            String token = MapUtil.str(result, "token");
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("no token in response");
            }
            return token;
        } catch (Exception e) {
            log.error("Fetch token from endpoint failed: uid={}", uid, e);
            throw new IllegalStateException("fetch token failed: " + e.getMessage(), e);
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
     * <p>Cloudflare Stream webhook payload 结构 (关键字段):</p>
     * <pre>
     * {
     *   "type": "video",
     *   "occurred_at": "2024-...",
     *   "video": {
     *     "uid": "abc123",
     *     "status": "ready",
     *     "readyToStream": true,
     *     "duration": 123.45,
     *     "hls": "https://watch.cloudflarestream.com/abc123/manifest/video.m3u8",
     *     "dash": "...",
     *     "thumbnail": "..."
     *   }
     * }
     * </pre>
     *
     * <p>处理流程:</p>
     * <ol>
     *   <li>从 payload.video 提取 uid / duration / hls / readyToStream</li>
     *   <li>通过 cloudflare_uid 反查 drama_episode 记录</li>
     *   <li>回写 hls_url 和 video_duration 字段 (仅当 readyToStream=true)</li>
     * </ol>
     *
     * <p>幂等性:webhook 可能重试,update by cloudflare_uid 是幂等的,
     * 重复回写不会产生副作用。</p>
     *
     * @param payload Cloudflare Stream webhook payload
     */
    @Transactional
    public void handleStreamCompleted(Map<String, Object> payload) {
        assertEnabled();
        if (payload == null) {
            log.warn("Cloudflare Stream webhook: empty payload");
            return;
        }
        // 入口打印完整 payload JSON,便于核对 Cloudflare 字段名/格式 (截断到 2000 字符避免日志爆炸)
        log.info("Cloudflare Stream webhook received: payloadJson={}", safePayloadJson(payload));

        Map<String, Object> video = MapUtil.castMap(payload.get("video"));
        if (video == null) {
            log.warn("Cloudflare Stream webhook: missing 'video' field in payload, top-level keys={}",
                    payload.keySet());
            return;
        }
        String uid = MapUtil.str(video, "uid");
        String status = MapUtil.str(video, "status");
        boolean readyToStream = MapUtil.bool(video, "readyToStream", false);

        if (uid == null || uid.isBlank()) {
            log.warn("Cloudflare Stream webhook: missing uid in video, status={}, videoKeys={}",
                    status, video.keySet());
            return;
        }

        // duration 提取过程打印中间值,便于排查"duration 没回写"问题
        // Cloudflare 返回的 duration 是 double (秒,带小数),如 123.45
        Object durationRaw = video.get("duration");
        Long durationSeconds = MapUtil.lng(video, "duration");
        Integer durationInt = durationSeconds == null ? null : Math.toIntExact(Math.round(durationSeconds.doubleValue()));
        log.info("Cloudflare Stream webhook: uid={}, status={}, readyToStream={}, durationRaw={}, durationSeconds={}, durationInt={}",
                uid, status, readyToStream, durationRaw, durationSeconds, durationInt);

        // 优先使用 webhook 返回的完整 hls URL,缺失时用本服务约定的路径格式兜底
        String hlsUrlFromPayload = MapUtil.str(video, "hls");
        String hlsUrl = hlsUrlFromPayload;
        if (hlsUrl == null || hlsUrl.isBlank()) {
            hlsUrl = getHlsUrl(uid);
            log.info("Cloudflare Stream webhook: uid={} 'hls' field missing or blank in payload, fallback to generated hlsUrl={}",
                    uid, hlsUrl);
        } else {
            log.info("Cloudflare Stream webhook: uid={} using hls from payload: {}", uid, hlsUrl);
        }

        // 仅当视频可流式播放时才回写,避免在 transcode 失败时用脏数据覆盖
        if (!readyToStream) {
            log.info("Cloudflare Stream webhook: uid={} not readyToStream, skip updating episode", uid);
            return;
        }

        // 反查关联的剧集 (cloudflare_uid 唯一索引保证最多一条)
        DramaEpisode episode = dramaEpisodeService.lambdaQuery()
                .eq(DramaEpisode::getCloudflareUid, uid)
                .one();

        if (episode == null) {
            log.warn("Cloudflare Stream webhook: uid={} not linked to any episode (no row in drama_episode with this cloudflare_uid), skip backfill", uid);
            return;
        }

        // 打印 update 前的 episode 现状,便于回写后对比 (排查"回写后字段仍为空"问题)
        log.info("Cloudflare Stream webhook: before update episodeId={}, uid={}, currentHlsUrl={}, currentVideoDuration={}",
                episode.getId(), uid, episode.getHlsUrl(), episode.getVideoDurationSeconds());

        // 只回写非空字段,避免覆盖管理员手动设置的值
        boolean updated = dramaEpisodeService.lambdaUpdate()
                .eq(DramaEpisode::getId, episode.getId())
                .set(DramaEpisode::getHlsUrl, hlsUrl)
                .set(durationInt != null, DramaEpisode::getVideoDurationSeconds, durationInt)
                .update();

        log.info("Cloudflare Stream webhook: update result={}, episodeId={}, uid={}, newHlsUrl={}, newDuration={}s",
                updated, episode.getId(), uid, hlsUrl, durationInt);
    }

    /**
     * 将 webhook payload 序列化为 JSON 字符串用于日志打印。
     * 序列化失败时返回 Map.toString() 兜底,避免日志本身抛异常。
     * 截断到 2000 字符防止超大 payload 刷爆日志。
     */
    private String safePayloadJson(Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            return json.length() > 2000 ? json.substring(0, 2000) + "...(truncated)" : json;
        } catch (Exception e) {
            log.warn("Cloudflare Stream webhook: failed to serialize payload to JSON: {}", e.getMessage());
            return String.valueOf(payload);
        }
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