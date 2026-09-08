package com.duanju.service.storage;

import com.duanju.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 本地磁盘存储实现,在 {@code duanju.storage.r2.enabled=false} (或属性缺失) 时启用。
 *
 * <p>文件落在 {@code localUploadDir/{folder}/{date}/{uuid}.{ext}},
 * URL 为 {@code publicBaseUrl + "/" + relativePath}。</p>
 *
 * <p>路径解析逻辑与 {@link StorageService} 保持一致:配置的目录不存在时,
 * 从 classpath 向上查找 {@code backed/uploads} 或 {@code uploads} 目录,
 * 兼容从 IDE / jar / 不同工作目录启动的场景。</p>
 */
@Component
@ConditionalOnProperty(name = "duanju.storage.r2.enabled", havingValue = "false", matchIfMissing = true)
public class LocalStorageProvider implements StorageProvider {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "m4v", "mov", "webm", "m3u8");

    private final Path localUploadDir;
    private final String publicBaseUrl;

    public LocalStorageProvider(@Value("${duanju.storage.local-upload-dir}") String localUploadDir,
                                @Value("${duanju.storage.public-base-url}") String publicBaseUrl) {
        this.localUploadDir = resolveUploadPath(localUploadDir);
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
    }

    @Override
    public String getName() {
        return "local";
    }

    @Override
    public boolean isEnabled() {
        // 本地存储始终可用
        return true;
    }

    @Override
    public UploadResult upload(byte[] bytes, String fileName, String contentType, String type) {
        String extension = extension(fileName);
        Path target = resolveTarget(folder(type, extension), extension);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes == null ? new byte[0] : bytes);
        } catch (IOException ex) {
            throw new IllegalArgumentException("upload failed", ex);
        }
        return buildResult(target, bytes == null ? 0L : bytes.length, contentType);
    }

    /**
     * 重写以使用 {@link MultipartFile#transferTo(Path)} 流式写入,避免大视频全量加载到内存。
     */
    @Override
    public UploadResult upload(MultipartFile file, String type) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is empty");
        }
        String extension = extension(file.getOriginalFilename());
        Path target = resolveTarget(folder(type, extension), extension);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException ex) {
            throw new IllegalArgumentException("upload failed", ex);
        }
        return buildResult(target, file.getSize(), file.getContentType());
    }

    /**
     * 重写以使用 {@link Files#copy(Path, Path, java.nio.file.CopyOption...)} 流式拷贝,避免 500MB 级
     * 批量上传(VPS中转)走默认实现 {@link Files#readAllBytes(Path)} 导致 JVM OOM。
     */
    @Override
    public UploadResult uploadFromFile(Path file, String fileName, String contentType, String type) {
        if (file == null) throw new IllegalArgumentException("file is null");
        String actualName = (fileName == null || fileName.isBlank())
                ? String.valueOf(file.getFileName())
                : fileName;
        String extension = extension(actualName);
        Path target = resolveTarget(folder(type, extension), extension);
        long size;
        try {
            Files.createDirectories(target.getParent());
            size = Files.size(file);
            Files.copy(file, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalArgumentException("uploadFromFile failed: " + file, ex);
        }
        return buildResult(target, size, contentType);
    }

    @Override
    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        Path target = localUploadDir.resolve(objectKey).normalize();
        if (!target.startsWith(localUploadDir)) {
            // 防止路径穿越,非法 objectKey 静默忽略
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // 删除失败不抛异常,避免影响调用方主流程
        }
    }

    @Override
    public void download(String objectKey, Path destination) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey is empty");
        }
        Path source = localUploadDir.resolve(objectKey).normalize();
        if (!source.startsWith(localUploadDir)) {
            throw new IllegalArgumentException("invalid objectKey (path traversal): " + objectKey);
        }
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("file not found: " + objectKey);
        }
        try {
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("download failed: " + objectKey, e);
        }
    }

    @Override
    public UploadResult uploadToKey(Path file, String objectKey, String contentType) {
        if (file == null || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("file not found: " + file);
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey is empty");
        }
        Path target = localUploadDir.resolve(objectKey).normalize();
        if (!target.startsWith(localUploadDir)) {
            throw new IllegalArgumentException("invalid objectKey (path traversal): " + objectKey);
        }
        long size;
        try {
            Files.createDirectories(target.getParent());
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            size = Files.size(target);
        } catch (IOException e) {
            throw new IllegalStateException("uploadToKey failed: " + objectKey, e);
        }
        String relativePath = localUploadDir.relativize(target).toString().replace('\\', '/');
        String url = publicBaseUrl + "/" + relativePath;
        String actualContentType = (contentType == null || contentType.isBlank())
                ? probeContentType(objectKey) : contentType;
        return new UploadResult(url, relativePath, relativePath, size, actualContentType, "local");
    }

    @Override
    public int deleteByPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return 0;
        }
        Path prefixDir = localUploadDir.resolve(prefix).normalize();
        if (!prefixDir.startsWith(localUploadDir)) {
            // 防止路径穿越
            return 0;
        }
        if (!Files.exists(prefixDir)) {
            // 前缀可能不是目录而是文件名前缀:直接匹配父目录找文件
            Path parent = prefixDir.getParent();
            if (parent == null || !Files.isDirectory(parent)) {
                return 0;
            }
            String fileNamePrefix = prefixDir.getFileName().toString();
            try (Stream<Path> stream = Files.list(parent)) {
                return (int) stream
                        .filter(p -> p.getFileName().toString().startsWith(fileNamePrefix))
                        .filter(Files::isRegularFile)
                        .mapToInt(p -> {
                            try {
                                Files.deleteIfExists(p);
                                return 1;
                            } catch (IOException e) {
                                return 0;
                            }
                        })
                        .sum();
            } catch (IOException e) {
                return 0;
            }
        }
        // 是目录:递归删除目录下所有内容
        int[] count = {0};
        try (Stream<Path> walk = Files.walk(prefixDir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            if (Files.isRegularFile(p)) {
                                count[0]++;
                            }
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {
        }
        return count[0];
    }

    private String probeContentType(String objectKey) {
        if (objectKey == null) return "application/octet-stream";
        if (objectKey.endsWith(".m3u8")) return "application/vnd.apple.mpegurl";
        if (objectKey.endsWith(".ts")) return "video/mp2t";
        if (objectKey.endsWith(".mp4")) return "video/mp4";
        if (objectKey.endsWith(".jpg") || objectKey.endsWith(".jpeg")) return "image/jpeg";
        if (objectKey.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    private UploadResult buildResult(Path target, long size, String contentType) {
        String relativePath = localUploadDir.relativize(target).toString().replace('\\', '/');
        String url = publicBaseUrl + "/" + relativePath;
        return new UploadResult(url, relativePath, relativePath, size, contentType, "local");
    }

    private Path resolveTarget(String folder, String extension) {
        Path targetDir = localUploadDir.resolve(folder).resolve(LocalDate.now().toString()).normalize();
        if (!targetDir.startsWith(localUploadDir)) {
            throw new IllegalArgumentException("invalid upload path");
        }
        String filename = UUID.randomUUID() + (extension.isBlank() ? "" : "." + extension);
        return targetDir.resolve(filename).normalize();
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
                LocalStorageProvider.class.getProtectionDomain().getCodeSource().getLocation().toURI()
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
