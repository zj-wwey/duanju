package com.duanju.service.storage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

/**
 * Cloudflare R2 对象存储实现,在 {@code duanju.storage.r2.enabled=true} 时启用。
 *
 * <p>使用 AWS S3 SDK v2 通过 S3 兼容协议访问 R2。region 固定为
 * {@link Region#AWS_GLOBAL} (Cloudflare R2 内部按 "auto" 处理),
 * endpoint 为 {@code https://<account-id>.r2.cloudflarestorage.com}。</p>
 *
 * <p>构造器中初始化 {@link S3Client},失败不抛异常 (只记 warn 日志),
 * 由 {@link #init()} 在 {@code @PostConstruct} 阶段再次检查 {@link #isEnabled()}
 * 并打印启动状态。若配置不全但 bean 已创建 (因 {@code r2.enabled=true} 触发条件注入),
 * {@link #upload(byte[], String, String, String)} 在调用时会抛
 * {@link IllegalStateException} 提示运维补全配置。</p>
 */
@Component
@ConditionalOnProperty(name = "duanju.storage.r2.enabled", havingValue = "true")
public class R2StorageProvider implements StorageProvider {

    private static final Logger log = LoggerFactory.getLogger(R2StorageProvider.class);

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "m4v", "mov", "webm", "m3u8");

    private final String accountId;
    private final String accessKeyId;
    private final String secretAccessKey;
    private final String bucket;
    private final String endpoint;
    private final String publicBaseUrl;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public R2StorageProvider(@Value("${duanju.storage.r2.account-id:}") String accountId,
                            @Value("${duanju.storage.r2.access-key-id:}") String accessKeyId,
                            @Value("${duanju.storage.r2.secret-access-key:}") String secretAccessKey,
                            @Value("${duanju.storage.r2.bucket:duanju}") String bucket,
                            @Value("${duanju.storage.r2.endpoint:}") String endpoint,
                            @Value("${duanju.storage.r2.public-base-url:}") String publicBaseUrl) {
        this.accountId = accountId == null ? "" : accountId.trim();
        this.accessKeyId = accessKeyId == null ? "" : accessKeyId.trim();
        this.secretAccessKey = secretAccessKey == null ? "" : secretAccessKey.trim();
        this.bucket = bucket == null ? "" : bucket.trim();
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
        this.s3Client = buildClient();
        this.s3Presigner = buildPresigner();
    }

    private S3Client buildClient() {
        if (!isEnabled()) {
            return null;
        }
        try {
            return S3Client.builder()
                    .region(Region.of("auto"))
                    .endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build())
                    .build();
        } catch (Exception ex) {
            log.warn("R2 S3Client 初始化失败,上传/删除将抛异常: {}", ex.getMessage());
            return null;
        }
    }

    private S3Presigner buildPresigner() {
        if (!isEnabled()) {
            return null;
        }
        try {
            return S3Presigner.builder()
                    .region(Region.of("auto"))
                    .endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                    .build();
        } catch (Exception ex) {
            log.warn("R2 S3Presigner 初始化失败,预签名直传将不可用(会 fallback 到后端 multipart 上传): {}", ex.getMessage());
            return null;
        }
    }

    @PostConstruct
    public void init() {
        if (!isEnabled()) {
            log.warn("R2 bean 已创建但配置不全 (account-id/access-key-id/secret-access-key/bucket/endpoint 至少一项为空),isEnabled=false");
            return;
        }
        if (s3Client == null) {
            log.warn("R2 配置完整但 S3Client 初始化失败,上传/删除将抛异常");
            return;
        }
        log.info("R2 存储已启用: bucket={}, endpoint={}, public-base-url={}, presigner-enabled={}",
                bucket, endpoint, publicBaseUrl, s3Presigner != null);
    }

    @PreDestroy
    public void destroy() {
        if (s3Presigner != null) {
            s3Presigner.close();
        }
        if (s3Client != null) {
            s3Client.close();
        }
    }

    @Override
    public String getName() {
        return "r2";
    }

    @Override
    public boolean isEnabled() {
        return isNotEmpty(accountId) && isNotEmpty(accessKeyId) && isNotEmpty(secretAccessKey)
                && isNotEmpty(bucket) && isNotEmpty(endpoint);
    }

    @Override
    public UploadResult upload(byte[] bytes, String fileName, String contentType, String type) {
        if (s3Client == null) {
            throw new IllegalStateException("R2 S3Client 未初始化,请检查 duanju.storage.r2.* 配置");
        }
        String extension = extension(fileName);
        String folder = folder(type, extension);
        String objectKey = buildObjectKey(folder, extension);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType)
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(bytes == null ? new byte[0] : bytes));

        String url = publicBaseUrl + "/" + objectKey;
        long size = bytes == null ? 0L : bytes.length;
        return new UploadResult(url, objectKey, objectKey, size, contentType, "r2");
    }

    /**
     * 重写 MultipartFile 上传,避免默认实现把整个文件读入 byte[] 导致 500MB 级文件 OOM。
     * 使用 {@link RequestBody#fromInputStream(java.io.InputStream, Long)} 流式上传到 R2。
     */
    @Override
    public UploadResult upload(MultipartFile file, String type) {
        if (s3Client == null) {
            throw new IllegalStateException("R2 S3Client 未初始化,请检查 duanju.storage.r2.* 配置");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is empty");
        }
        String fileName = file.getOriginalFilename();
        String contentType = file.getContentType();
        String extension = extension(fileName);
        String folder = folder(type, extension);
        String objectKey = buildObjectKey(folder, extension);
        long size = file.getSize();

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType)
                .build();
        try (InputStream in = file.getInputStream()) {
            s3Client.putObject(request, RequestBody.fromInputStream(in, size));
        } catch (IOException ex) {
            throw new IllegalStateException("R2 upload: read MultipartFile stream failed", ex);
        }

        String url = publicBaseUrl + "/" + objectKey;
        return new UploadResult(url, objectKey, objectKey, size, contentType, "r2");
    }

    /**
     * 签发 R2 预签名 PUT URL,允许浏览器直传到 R2,绕过本服务和 Cloudflare 代理的 100MB / WAF 限制。
     *
     * <p>说明:中国大陆 → Cloudflare Stream 直传链路长期不稳定(HTTP/2 PING_FAILED),
     * 且经 dash.marastel.com(橙云代理)中转上传容易命中 WAF 挑战页,导致 500MB 级大文件上传必失败。
     * 而 R2 预签名 PUT 请求直连 S3 兼容端点,body 一次性 PUT 即可,实测国内浏览器到 R2 S3 端口的链路比
     * Stream uploadURL 稳得多;并且上传公开走自定义域名 cdn.marastel.com,走 CF 全球边缘节点
     * (中国访问通过 Cloudflare CN 缓存,分发稳定)。</p>
     *
     * <p>默认签名有效期 30 分钟(防止传 500MB 视频超时)。调用方传更大值不会超过 6 小时。</p>
     */
    @Override
    public PresignResult presignUpload(String fileName, String contentType, String type, long expireSeconds) {
        if (s3Presigner == null) {
            // Presigner 没初始化:返回 null,前端自动 fallback 到后端 multipart 上传。
            return null;
        }
        String extension = extension(fileName);
        String folder = folder(type, extension);
        String objectKey = buildObjectKey(folder, extension);

        String actualContentType = (contentType == null || contentType.isBlank())
                ? "application/octet-stream"
                : contentType;

        long expire = Math.max(60L, Math.min(expireSeconds, 6L * 3600L));

        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(actualContentType)
                .build();
        PutObjectPresignRequest presign = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expire))
                .putObjectRequest(put)
                .build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presign);

        String presignedUrl = presigned.url().toString();
        String publicUrl = publicBaseUrl + "/" + objectKey;

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", actualContentType);
        if (log.isInfoEnabled()) {
            String presignedHost = "";
            try {
                presignedHost = presigned.url().toURI().getHost();
            } catch (Exception ignored) {
            }
            log.info("R2 presign PUT: bucket={} type={} objectKey={} expireSecs={} publicUrl={} presignedHost={}",
                    bucket, type == null ? "" : type, objectKey, expire, publicUrl, presignedHost);
        }
        return new PresignResult(presignedUrl, objectKey, publicUrl, headers);
    }

    /**
     * 批量上传(VPS本地文件)直传到 R2:使用 {@link RequestBody#fromInputStream(InputStream, Long)} 流式读取,
     * 500MB 级视频也不会把整个文件读入 byte[] (避免 OOM)。
     */
    @Override
    public UploadResult uploadFromFile(Path file, String fileName, String contentType, String type) {
        if (s3Client == null) {
            throw new IllegalStateException("R2 S3Client 未初始化,请检查 duanju.storage.r2.* 配置");
        }
        if (file == null) throw new IllegalArgumentException("file is null");
        String actualName = (fileName == null || fileName.isBlank())
                ? String.valueOf(file.getFileName())
                : fileName;
        String extension = extension(actualName);
        String folder = folder(type, extension);
        String objectKey = buildObjectKey(folder, extension);
        long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            throw new IllegalStateException("R2 uploadFromFile: size probe failed: " + file, ex);
        }
        String actualContentType = (contentType == null || contentType.isBlank())
                ? probeContentType(file, actualName)
                : contentType;
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(actualContentType)
                .build();
        try (InputStream in = Files.newInputStream(file)) {
            s3Client.putObject(request, RequestBody.fromInputStream(in, size));
        } catch (IOException ex) {
            throw new IllegalStateException("R2 uploadFromFile: stream failed: " + file, ex);
        }
        String publicUrl = publicBaseUrl + "/" + objectKey;
        return new UploadResult(publicUrl, objectKey, objectKey, size, actualContentType, "r2");
    }

    private static String probeContentType(Path file, String fileName) {
        try {
            String t = Files.probeContentType(file);
            if (t != null && !t.isBlank()) return t;
        } catch (IOException ignored) {
        }
        // Files.probeContentType 在不同 JDK/OS 上识别 mp4 可能为 null,做一个后缀兜底
        String ext = extension(fileName);
        switch (ext) {
            case "mp4": return "video/mp4";
            case "m4v": return "video/x-m4v";
            case "mov": return "video/quicktime";
            case "webm": return "video/webm";
            case "ts": return "video/mp2t";
            case "m3u8": return "application/vnd.apple.mpegurl";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            default: return "application/octet-stream";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof R2StorageProvider that)) return false;
        return Objects.equals(accountId, that.accountId)
                && Objects.equals(accessKeyId, that.accessKeyId)
                && Objects.equals(bucket, that.bucket);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, accessKeyId, bucket);
    }

    @Override
    public void download(String objectKey, Path destination) {
        if (s3Client == null) {
            throw new IllegalStateException("R2 S3Client 未初始化,无法下载");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey is empty");
        }
        try {
            Files.createDirectories(destination.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("创建目录失败: " + destination.getParent(), e);
        }
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        s3Client.getObject(request, destination);
    }

    @Override
    public UploadResult uploadToKey(Path file, String objectKey, String contentType) {
        if (s3Client == null) {
            throw new IllegalStateException("R2 S3Client 未初始化,无法上传");
        }
        if (file == null || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("file not found: " + file);
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey is empty");
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            throw new IllegalStateException("获取文件大小失败: " + file, e);
        }
        String actualContentType = (contentType == null || contentType.isBlank())
                ? probeContentType(file, objectKey) : contentType;
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(actualContentType)
                .build();
        try (InputStream in = Files.newInputStream(file)) {
            s3Client.putObject(request, RequestBody.fromInputStream(in, size));
        } catch (IOException e) {
            throw new IllegalStateException("R2 uploadToKey: stream failed: " + file, e);
        }
        String publicUrl = publicBaseUrl + "/" + objectKey;
        return new UploadResult(publicUrl, objectKey, objectKey, size, actualContentType, "r2");
    }

    @Override
    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        if (s3Client == null) {
            return;
        }
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        try {
            s3Client.deleteObject(request);
        } catch (S3Exception ex) {
            // 404 视为已删除,不抛异常;其他错误向上抛
            if (ex.statusCode() != 404) {
                throw ex;
            }
        }
    }

    @Override
    public int deleteByPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return 0;
        }
        if (s3Client == null) {
            throw new IllegalStateException("R2 S3Client 未初始化,无法按前缀删除");
        }
        int totalDeleted = 0;
        String continuationToken = null;
        do {
            // 1) 列举前缀下的对象 (R2 单次 list 最多 1000 个)
            ListObjectsV2Request.Builder listBuilder = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .maxKeys(1000);
            if (continuationToken != null) {
                listBuilder.continuationToken(continuationToken);
            }
            var listResp = s3Client.listObjectsV2(listBuilder.build());
            if (listResp.contents() == null || listResp.contents().isEmpty()) {
                break;
            }

            // 2) 批量删除 (最多 1000 个)
            List<ObjectIdentifier> toDelete = listResp.contents().stream()
                    .map(S3Object::key)
                    .map(key -> ObjectIdentifier.builder().key(key).build())
                    .toList();

            DeleteObjectsRequest delRequest = DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(b -> b.objects(toDelete))
                    .build();

            try {
                var delResp = s3Client.deleteObjects(delRequest);
                if (delResp.deleted() != null) {
                    totalDeleted += delResp.deleted().size();
                }
            } catch (S3Exception ex) {
                // 404 视为已删除
                if (ex.statusCode() != 404) {
                    throw ex;
                }
            }

            continuationToken = listResp.nextContinuationToken();
        } while (continuationToken != null);

        return totalDeleted;
    }

    private String buildObjectKey(String folder, String extension) {
        String date = LocalDate.now().toString();
        String filename = UUID.randomUUID() + (extension.isBlank() ? "" : "." + extension);
        return folder + "/" + date + "/" + filename;
    }

    private static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index + 1).toLowerCase();
    }

    private static String folder(String type, String extension) {
        if ("avatar".equalsIgnoreCase(type)) {
            return "avatars";
        }
        if ("image".equalsIgnoreCase(type) || IMAGE_EXTENSIONS.contains(extension)) {
            return "images";
        }
        if ("video".equalsIgnoreCase(type) || VIDEO_EXTENSIONS.contains(extension)) {
            return "videos";
        }
        return "files";
    }

    private static boolean isNotEmpty(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("/+$", "");
    }
}
