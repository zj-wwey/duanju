package com.duanju.service;

import com.duanju.service.storage.StorageProvider;
import com.duanju.service.storage.StorageProvider.UploadResult;
import com.duanju.util.MapUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 存储服务,统一封装头像 / 文件上传 / 时长探测 / 对象删除等业务操作。
 *
 * <p>底层通过 {@link StorageProvider} 抽象,根据 {@code duanju.storage.r2.enabled}
 * 自动路由到本地磁盘或 Cloudflare R2。本服务保持原有方法签名兼容,
 * 仅在返回 Map 中新增 {@code objectKey} 字段供后续落库使用。</p>
 */
@Service
public class StorageService {
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "m4v", "mov", "webm", "m3u8");

    private final StorageProvider storageProvider;
    private final boolean r2Enabled;
    // 以下两个字段仅在本地模式 probeDurationFromUrl 时使用,R2 模式下不读取
    private final Path localUploadDir;
    private final String publicBaseUrl;

    public StorageService(StorageProvider storageProvider,
                          @Value("${duanju.storage.r2.enabled:false}") boolean r2Enabled,
                          @Value("${duanju.storage.local-upload-dir}") String localUploadDir,
                          @Value("${duanju.storage.public-base-url}") String publicBaseUrl) {
        this.storageProvider = storageProvider;
        this.r2Enabled = r2Enabled;
        this.localUploadDir = resolveUploadPath(localUploadDir);
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
    }

    /**
     * 将 Base64 Data URL 头像保存到存储,返回可访问的 URL。
     * 如果 avatarUrl 不是 data URL (已经是 http 链接),则原样返回。
     */
    public String saveAvatar(String avatarUrl) {
        Map<String, String> result = saveAvatarWithKey(avatarUrl);
        return result.get("url");
    }

    /**
     * 与 {@link #saveAvatar(String)} 相同,但同时返回 objectKey 便于落库。
     *
     * <p>返回 Map 含:</p>
     * <ul>
     *   <li>{@code url}:可访问 URL (始终存在,除非入参为空)</li>
     *   <li>{@code objectKey}:对象在存储后端的唯一标识;外部 http URL 场景为 null</li>
     * </ul>
     */
    public Map<String, String> saveAvatarWithKey(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return new LinkedHashMap<>();
        }
        if (!avatarUrl.startsWith("data:image/")) {
            // 外部 http URL,不实际上传,无 objectKey
            Map<String, String> result = new LinkedHashMap<>();
            result.put("url", avatarUrl);
            return result;
        }

        int metadataEnd = avatarUrl.indexOf(',');
        if (metadataEnd < 0 || !avatarUrl.substring(0, metadataEnd).endsWith(";base64")) {
            throw new IllegalArgumentException("invalid avatar image");
        }

        String metadata = avatarUrl.substring(0, metadataEnd);
        String extension = extensionFromDataUrl(metadata);
        String contentType = contentTypeFromDataUrl(metadata);
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(avatarUrl.substring(metadataEnd + 1));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid avatar image");
        }
        if (bytes.length == 0) {
            throw new IllegalArgumentException("invalid avatar image");
        }

        UploadResult uploaded = storageProvider.upload(bytes, "avatar." + extension, contentType, "avatar");
        Map<String, String> result = new LinkedHashMap<>();
        result.put("url", uploaded.url());
        result.put("objectKey", uploaded.objectKey());
        return result;
    }

    /**
     * 上传头像文件 (multipart 模式),返回包含 url + objectKey 的 Map。
     * 替代旧的 {@link #saveAvatar(String)} (Base64 Data URL) 方式,大图上传更稳定。
     *
     * <p>返回 Map 含:</p>
     * <ul>
     *   <li>{@code url}:可访问 URL</li>
     *   <li>{@code objectKey}:对象在存储后端的唯一标识,用于后续级联删除</li>
     * </ul>
     */
    public Map<String, String> saveAvatarWithFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is empty");
        }
        UploadResult uploaded = storageProvider.upload(file, "avatar");
        Map<String, String> result = new LinkedHashMap<>();
        result.put("url", uploaded.url());
        result.put("objectKey", uploaded.objectKey());
        return result;
    }

    /**
     * 保存 MultipartFile 上传的文件,返回包含 URL 和元信息的 Map。
     *
     * <p>返回 Map 含:storageProvider / url / objectKey / path / name / size / contentType,
     * 视频文件额外含 durationSeconds (仅本地模式可探测;R2 模式不探测)。</p>
     */
    public Map<String, Object> uploadFile(MultipartFile file, String type) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is empty");
        }
        UploadResult uploaded = storageProvider.upload(file, type);
        return uploadResultToMap(file.getOriginalFilename(), type, uploaded);
    }

    /**
     * 从 VPS 本地文件路径直接上传 (批量上传场景),全程流式处理,支持 500MB+ 大文件。
     *
     * <p>默认实现走 StorageProvider.uploadFromFile:R2 实现会调用 S3 SDK 的
     * {@code RequestBody.fromInputStream},避免把大文件读入 byte[] 导致 OOM。</p>
     *
     * @return 同 {@link #uploadFile(MultipartFile, String)} 返回结构一致的 Map
     */
    public Map<String, Object> uploadFromLocalFile(java.nio.file.Path file, String fileName, String type) {
        if (file == null) throw new IllegalArgumentException("file is null");
        if (!java.nio.file.Files.isRegularFile(file)) {
            throw new IllegalArgumentException("file not found or not regular: " + file);
        }
        String actualName = (fileName == null || fileName.isBlank())
                ? String.valueOf(file.getFileName())
                : fileName;
        UploadResult uploaded = storageProvider.uploadFromFile(file, actualName, null, type);
        return uploadResultToMap(actualName, type, uploaded);
    }

    /**
     * 当前存储提供方是否启用且可用。
     * 批量上传入口应先查此值,false 时直接给用户清晰报错。
     */
    public boolean isStorageEnabled() {
        return storageProvider != null && storageProvider.isEnabled();
    }

    public String currentProviderName() {
        return storageProvider == null ? null : storageProvider.getName();
    }

    private Map<String, Object> uploadResultToMap(String originalFileName, String type, UploadResult uploaded) {
        Map<String, Object> result = MapUtil.map(
                "storageProvider", uploaded.storageProvider(),
                "url", uploaded.url(),
                "objectKey", uploaded.objectKey(),
                "path", uploaded.path(),
                "name", originalFileName,
                "size", uploaded.size(),
                "contentType", uploaded.contentType()
        );
        String extension = extension(originalFileName);
        if ("video".equalsIgnoreCase(type) || VIDEO_EXTENSIONS.contains(extension)) {
            int duration = probeDurationFromUrl(uploaded.url());
            if (duration > 0) {
                result.put("durationSeconds", duration);
            }
        }
        return result;
    }

    /**
     * 从 URL 中提取本地文件路径并探测时长。URL 格式为 publicBaseUrl/relativePath。
     * R2 模式或非本地 URL 返回 0 (R2 对象不本地探测时长)。
     */
    public int probeDurationFromUrl(String videoUrl) {
        if (r2Enabled) {
            return 0;
        }
        if (videoUrl == null || videoUrl.isBlank()) return 0;
        String prefix = publicBaseUrl + "/";
        if (!videoUrl.startsWith(prefix)) return 0;
        String relativePath = videoUrl.substring(prefix.length());
        Path videoPath = localUploadDir.resolve(relativePath).normalize();
        if (!videoPath.startsWith(localUploadDir) || !Files.exists(videoPath)) return 0;
        return (int) Math.round(probeDuration(videoPath));
    }

    /**
     * 记录外部存储 (OSS/COS) 的 URL,不做实际上传。
     */
    public Map<String, Object> saveStorageObject(String storageProvider, String url) {
        return MapUtil.map(
                "storageProvider", storageProvider == null ? "oss" : storageProvider,
                "url", url,
                "message", "Use this URL as episode.videoUrl or coverUrl after uploading to OSS/COS"
        );
    }

    /**
     * 签发预签名 PUT URL,允许浏览器直传到 R2 等支持预签名的对象存储。
     * 本地存储模式返回 null,调用方应 fallback 到 multipart 上传。
     *
     * @param fileName    原始文件名
     * @param contentType MIME 类型
     * @param type        业务类型 (image/video/avatar/file)
     * @return 预签名结果;不支持时返回 null
     */
    public Map<String, Object> presignUpload(String fileName, String contentType, String type) {
        StorageProvider.PresignResult result = storageProvider.presignUpload(fileName, contentType, type, 1800);
        if (result == null) {
            return null;
        }
        return MapUtil.map(
                "storageProvider", storageProvider.getName(),
                "presignedUrl", result.presignedUrl(),
                "objectKey", result.objectKey(),
                "url", result.publicUrl(),
                "headers", result.headers() == null ? Map.of() : result.headers()
        );
    }

    /**
     * 删除存储对象。objectKey 由 uploadFile / saveAvatarWithKey 返回;
     * 不存在不报错 (由 provider 实现吞掉 404)。
     */
    public void deleteObject(String objectKey) {
        storageProvider.delete(objectKey);
    }

    /**
     * 从视频/封面的公开 URL 反向还原出存储 objectKey,供级联删除使用。
     *
     * <p>我们的落库规则:videoUrl/coverUrl 形如(历史上存在两种 R2 前缀,都要兼容):</p>
     * <ul>
     *   <li>R2(新版,推荐): {@code https://cdn.marastel.com/videos/2026-08-28/uuid.mp4}
     *        → 还原成 {@code videos/2026-08-28/uuid.mp4}。
     *        对应配置 {@code CLOUDFLARE_R2_PUBLIC_BASE_URL=https://cdn.marastel.com} (末尾不写虚拟目录)。</li>
     *   <li>R2(旧版,兼容): {@code https://cdn.marastel.com/r2/videos/2026-08-28/uuid.mp4}
     *        → 需要先剥离历史前缀 {@code r2/},再还原成 {@code videos/2026-08-28/uuid.mp4}。
     *        对应旧配置 {@code CLOUDFLARE_R2_PUBLIC_BASE_URL=https://cdn.marastel.com/r2}。</li>
     *   <li>本地: {@code https://dash.marastel.com/uploads/videos/...}
     *        → 还原成相对路径 {@code videos/...}。</li>
     *   <li>历史 Stream / 外部 OSS/COS: {@code https://videodelivery.net/...}
     *        → 不识别,返回 null,交给各业务自己走对应 deleteVideo API。</li>
     * </ul>
     *
     * <p>还原原则:返回的 objectKey 一定是「R2 桶根相对路径」(不含 bucket/endpoint)或本地 uploads 目录相对路径,
     * 能直接传给 {@link StorageProvider#delete(String)}。
     * 还原失败返回 null,调用方直接跳过删除,避免误删其他对象。</p>
     */
    public String objectKeyFromPublicUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        // 1) 直接就是相对路径(罕见,但兼容旧数据):/uploads/videos/xxx 或 videos/xxx
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            if (trimmed.startsWith("/")) {
                trimmed = trimmed.substring(1);
            }
            // 去掉常见前缀 /uploads/ 或 uploads/
            String localPrefix1 = "uploads/";
            if (trimmed.startsWith(localPrefix1)) {
                return trimmed.substring(localPrefix1.length());
            }
            return trimmed.isEmpty() ? null : trimmed;
        }
        // 2) 去掉协议 + host,拿到 path;兼容带端口/带查询参数/#锚点
        int proto = trimmed.indexOf("://");
        if (proto < 0) return null;
        String withoutProto = trimmed.substring(proto + 3);
        int slash = withoutProto.indexOf('/');
        String path = (slash < 0) ? "/" : withoutProto.substring(slash); // 形如 /r2/videos/... 或 /uploads/... 或 /videos/...
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        int hash = path.indexOf('#');
        if (hash >= 0) path = path.substring(0, hash);
        if (path.isEmpty() || "/".equals(path)) return null;
        // 去掉前缀斜杠
        if (path.startsWith("/")) path = path.substring(1);
        if (path.isEmpty()) return null;

        // 2a) R2 历史兼容: publicBaseUrl 末尾曾配置 /r2,导致 URL 里多出一层虚拟 r2/ 目录,
        //     但 R2 bucket 里实际 object key 不带 r2/ 前缀。这里必须先剥掉,否则级联删除会找不到对象。
        String legacyR2Prefix = "r2/";
        if (path.startsWith(legacyR2Prefix)) {
            String stripped = path.substring(legacyR2Prefix.length());
            if (!stripped.isEmpty()) path = stripped;
        }
        // 2b) 本地: storage.public-base-url=https://dash.marastel.com/uploads → path 前缀是 uploads/
        String localPrefix = "uploads/";
        if (path.startsWith(localPrefix)) {
            String key = path.substring(localPrefix.length());
            return key.isEmpty() ? null : key;
        }
        // 2c) 新版 R2 / 其它合法相对路径(例如 /videos/.../xx.mp4):直接作为 objectKey 返回。
        //     仅排除几个常见的「不可能是我们对象存储」的标志性前缀/域名兜底,
        //     避免把 Cloudflare Stream(videodelivery.net) / 外部 OSS 桶签名路径当成自己的 key 删掉。
        //     注意:此处 path 已经是去 host 后的纯路径,所以用 host 排除在前面「协议」那一步做不到,
        //     这里用「路径是否是已知 provider 生成形式」判断更可靠——未知一律不删。
        Set<String> knownFolders = Set.of("avatars/", "images/", "videos/", "files/");
        for (String f : knownFolders) {
            if (path.startsWith(f)) {
                return path;
            }
        }
        // 3) 其它未知前缀 / 第三方域名(例如 Cloudflare Stream videodelivery.net / 外部 OSS):
        //    我们无法保证 path == provider objectKey,保守处理:返回 null,跳过文件删除。
        return null;
    }

    /**
     * 返回当前启用的存储提供方名称 (local / r2)。
     */
    public String getProviderName() {
        return storageProvider.getName();
    }

    /**
     * 使用 ffprobe 提取视频时长 (秒)。失败时返回 0。
     */
    private double probeDuration(Path videoPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe", "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    videoPath.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            int exitCode = process.waitFor();
            if (exitCode == 0 && !output.isEmpty()) {
                return Double.parseDouble(output);
            }
        } catch (Exception ignored) {
            // ffprobe not available or extraction failed
        }
        return 0;
    }

    private String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index + 1).toLowerCase();
    }

    private String extensionFromDataUrl(String metadata) {
        if (metadata.startsWith("data:image/png")) {
            return "png";
        }
        if (metadata.startsWith("data:image/webp")) {
            return "webp";
        }
        if (metadata.startsWith("data:image/gif")) {
            return "gif";
        }
        return "jpg";
    }

    private String contentTypeFromDataUrl(String metadata) {
        if (metadata.startsWith("data:image/png")) {
            return "image/png";
        }
        if (metadata.startsWith("data:image/webp")) {
            return "image/webp";
        }
        if (metadata.startsWith("data:image/gif")) {
            return "image/gif";
        }
        return "image/jpeg";
    }

    private static Path resolveUploadPath(String localUploadDir) {
        Path path = Paths.get(localUploadDir).toAbsolutePath().normalize();
        if (Files.exists(path)) {
            return path;
        }
        Path found = findUploadDir();
        if (found != null) {
            return found;
        }
        return path;
    }

    private static Path findUploadDir() {
        try {
            Path classPath = Paths.get(
                StorageService.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            ).toAbsolutePath().normalize();
            Path highestLevelMatch = null;
            int highestLevel = -1;
            Path current = classPath;
            int depth = 0;
            while (current != null && depth < 10) {
                Path backedUploads = current.resolve("backed").resolve("uploads");
                if (Files.isDirectory(backedUploads)) {
                    if (depth > highestLevel) {
                        highestLevel = depth;
                        highestLevelMatch = backedUploads;
                    }
                }
                Path directUploads = current.resolve("uploads");
                if (Files.isDirectory(directUploads)) {
                    if (depth > highestLevel) {
                        highestLevel = depth;
                        highestLevelMatch = directUploads;
                    }
                }
                current = current.getParent();
                depth++;
            }
            return highestLevelMatch;
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "/uploads";
        }
        return value.replaceAll("/+$", "");
    }
}
