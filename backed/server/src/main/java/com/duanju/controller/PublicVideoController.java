package com.duanju.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 公开视频/静态资源下载控制器
 * <p>
 * 绕过 Spring 静态资源处理器可能的限制，
 * 提供稳定的 API 代理下载端点，适用于 Android App 等对
 * HTTP 明文 / 跨域 / 路径格式敏感的客户端。
 */
@RestController
@RequestMapping("/api/public/videos")
public class PublicVideoController {

    private static final Logger log = LoggerFactory.getLogger(PublicVideoController.class);

    private final Path uploadPath;

    public PublicVideoController(
            @Value("${duanju.storage.local-upload-dir}") String localUploadDir) {
        Path path = resolveUploadPath(localUploadDir);
        this.uploadPath = path;
        log.info("PublicVideoController upload path: {}", this.uploadPath);
    }

    private Path resolveUploadPath(String localUploadDir) {
        Path path = Paths.get(localUploadDir).toAbsolutePath().normalize();
        if (Files.exists(path)) {
            return path;
        }
        Path found = findUploadDir();
        if (found != null) {
            return found;
        }
        // fallback
        return Paths.get("backed/uploads").toAbsolutePath().normalize();
    }

    private Path findUploadDir() {
        try {
            Path classPath = Paths.get(
                    PublicVideoController.class.getProtectionDomain().getCodeSource().getLocation().toURI()
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
     * 通过 API 代理下载视频文件
     * <p>
     * 请求示例: GET /api/public/videos/download?path=videos/2026-08-17/xxx.mp4
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> download(
            @RequestParam("path") String relativePath,
            @RequestHeader(value = "Range", required = false) String rangeHeader,
            HttpServletResponse response) throws IOException {

        // 安全校验：防止路径穿越
        Path resolved = uploadPath.resolve(relativePath).normalize();
        if (!resolved.startsWith(uploadPath)) {
            log.warn("Attempted path traversal: {}", relativePath);
            return ResponseEntity.badRequest().build();
        }

        if (!Files.exists(resolved) || !Files.isRegularFile(resolved)) {
            log.warn("File not found: {}", resolved);
            return ResponseEntity.notFound().build();
        }

        File file = resolved.toFile();
        String fileName = file.getName();
        long fileSize = file.length();

        // 推断 MIME 类型
        String contentType = Files.probeContentType(resolved);
        if (contentType == null || contentType.isBlank()) {
            contentType = guessContentType(fileName);
        }

        // 处理 Range 请求（支持断点续传和部分下载）
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String range = rangeHeader.substring(6);
            long start = 0;
            long end = fileSize - 1;

            if (range.contains("-")) {
                String[] parts = range.split("-");
                if (!parts[0].isEmpty()) {
                    start = Long.parseLong(parts[0]);
                }
                if (!parts[1].isEmpty()) {
                    end = Long.parseLong(parts[1]);
                }
            }

            if (start >= fileSize) {
                return ResponseEntity.status(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
                        .build();
            }

            end = Math.min(end, fileSize - 1);
            long contentLength = end - start + 1;

            long finalStart = start;
            long finalEnd = end;
            return ResponseEntity.status(HttpServletResponse.SC_PARTIAL_CONTENT)
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE,
                            "bytes " + finalStart + "-" + finalEnd + "/" + fileSize)
                    .contentLength(contentLength)
                    .body(new FileSystemResource(file) {
                        @Override
                        public java.io.InputStream getInputStream() throws IOException {
                            RandomAccessFile raf = new RandomAccessFile(file, "r");
                            raf.seek(finalStart);
                            return new java.io.InputStream() {
                                private long remaining = contentLength;

                                @Override
                                public int read() throws IOException {
                                    if (remaining <= 0) return -1;
                                    remaining--;
                                    return raf.read();
                                }

                                @Override
                                public int read(byte[] b, int off, int len) throws IOException {
                                    if (remaining <= 0) return -1;
                                    int toRead = (int) Math.min(len, remaining);
                                    int read = raf.read(b, off, toRead);
                                    if (read > 0) remaining -= read;
                                    return read;
                                }

                                @Override
                                public void close() throws IOException {
                                    raf.close();
                                }
                            };
                        }
                    });
        }

        // 完整文件下载
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .contentLength(fileSize)
                .body(new FileSystemResource(file));
    }

    /**
     * 简单的视频流端点（不下载、直接 inline 播放）
     * <p>
     * 请求示例: GET /api/public/videos/stream?path=videos/2026-08-17/xxx.mp4
     */
    @GetMapping("/stream")
    public StreamingResponseBody stream(
            @RequestParam("path") String relativePath,
            HttpServletResponse response) throws IOException {

        Path resolved = uploadPath.resolve(relativePath).normalize();
        if (!resolved.startsWith(uploadPath)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        if (!Files.exists(resolved) || !Files.isRegularFile(resolved)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }

        File file = resolved.toFile();
        String contentType = Files.probeContentType(resolved);
        if (contentType == null || contentType.isBlank()) {
            contentType = guessContentType(file.getName());
        }

        response.setContentType(contentType);
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=3600");
        response.setContentLengthLong(file.length());

        return outputStream -> {
            try (var fis = new java.io.FileInputStream(file);
                 var bis = new java.io.BufferedInputStream(fis)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }
        };
    }

    private String guessContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".m3u8")) return "application/x-mpegURL";
        if (lower.endsWith(".ts")) return "video/mp2t";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mkv")) return "video/x-matroska";
        if (lower.endsWith(".avi")) return "video/x-msvideo";
        if (lower.endsWith(".mov")) return "video/quicktime";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }
}
