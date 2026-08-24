package com.duanju.service;

import com.duanju.util.MapUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class StorageService {
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "m4v", "mov", "webm", "m3u8");

    private final Path localUploadDir;
    private final String publicBaseUrl;

    public StorageService(@Value("${duanju.storage.local-upload-dir}") String localUploadDir,
                          @Value("${duanju.storage.public-base-url}") String publicBaseUrl) {
        this.localUploadDir = resolveUploadPath(localUploadDir);
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
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

    /**
     * 将 Base64 Data URL 头像保存到本地，返回可访问的 URL。
     * 如果 avatarUrl 不是 data URL（已经是 http 链接），则原样返回。
     */
    public String saveAvatar(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return null;
        }
        if (!avatarUrl.startsWith("data:image/")) {
            return avatarUrl;
        }

        int metadataEnd = avatarUrl.indexOf(',');
        if (metadataEnd < 0 || !avatarUrl.substring(0, metadataEnd).endsWith(";base64")) {
            throw new IllegalArgumentException("invalid avatar image");
        }

        String metadata = avatarUrl.substring(0, metadataEnd);
        String extension = extensionFromDataUrl(metadata);
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(avatarUrl.substring(metadataEnd + 1));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid avatar image");
        }
        if (bytes.length == 0) {
            throw new IllegalArgumentException("invalid avatar image");
        }

        Path target = resolveTarget("avatars", extension);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException ex) {
            throw new IllegalArgumentException("avatar upload failed");
        }

        return buildUrl(target);
    }

    /**
     * 保存 MultipartFile 上传的文件，返回包含 URL 和元信息的 Map。
     */
    public Map<String, Object> uploadFile(MultipartFile file, String type) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("file is empty");
        }
        String extension = extension(file.getOriginalFilename());
        String folder = folder(type, extension);
        Path target = resolveTarget(folder, extension);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException ex) {
            throw new IllegalArgumentException("upload failed");
        }

        String relativePath = localUploadDir.relativize(target).toString().replace('\\', '/');
        String url = publicBaseUrl + "/" + relativePath;
        Map<String, Object> result = MapUtil.map(
                "storageProvider", "local",
                "url", url,
                "path", relativePath,
                "name", file.getOriginalFilename(),
                "size", file.getSize(),
                "contentType", file.getContentType()
        );

        if ("video".equalsIgnoreCase(type) || VIDEO_EXTENSIONS.contains(extension)) {
            double duration = probeDuration(target);
            if (duration > 0) {
                result.put("durationSeconds", (int) Math.round(duration));
            }
        }

        return result;
    }

    /**
     * 使用 ffprobe 提取视频时长（秒）。失败时返回 0。
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

    /**
     * 从 URL 中提取本地文件路径并探测时长。URL 格式为 publicBaseUrl/relativePath。
     */
    public int probeDurationFromUrl(String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) return 0;
        String prefix = publicBaseUrl + "/";
        if (!videoUrl.startsWith(prefix)) return 0;
        String relativePath = videoUrl.substring(prefix.length());
        Path videoPath = localUploadDir.resolve(relativePath).normalize();
        if (!videoPath.startsWith(localUploadDir) || !Files.exists(videoPath)) return 0;
        return (int) Math.round(probeDuration(videoPath));
    }

    /**
     * 记录外部存储（OSS/COS）的 URL，不做实际上传。
     */
    public Map<String, Object> saveStorageObject(String storageProvider, String url) {
        return MapUtil.map(
                "storageProvider", storageProvider == null ? "oss" : storageProvider,
                "url", url,
                "message", "Use this URL as episode.videoUrl or coverUrl after uploading to OSS/COS"
        );
    }

    private Path resolveTarget(String folder, String extension) {
        Path targetDir = localUploadDir.resolve(folder).resolve(LocalDate.now().toString()).normalize();
        if (!targetDir.startsWith(localUploadDir)) {
            throw new IllegalArgumentException("invalid upload path");
        }
        String filename = UUID.randomUUID() + (extension.isBlank() ? "" : "." + extension);
        return targetDir.resolve(filename).normalize();
    }

    private String buildUrl(Path target) {
        String relativePath = localUploadDir.relativize(target).toString().replace('\\', '/');
        return publicBaseUrl + "/" + relativePath;
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

    private String folder(String type, String extension) {
        if ("image".equalsIgnoreCase(type) || IMAGE_EXTENSIONS.contains(extension)) {
            return "images";
        }
        if ("video".equalsIgnoreCase(type) || VIDEO_EXTENSIONS.contains(extension)) {
            return "videos";
        }
        return "files";
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

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "/uploads";
        }
        return value.replaceAll("/+$", "");
    }
}
