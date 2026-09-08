package com.duanju.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.duanju.entity.DramaEpisode;
import com.duanju.service.entity.DramaEpisodeService;
import com.duanju.service.storage.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
public class VideoTranscodeService {

    private static final Logger log = LoggerFactory.getLogger(VideoTranscodeService.class);

    /** FFmpeg 转码超时时间(分钟),防止异常视频卡死进程 */
    private static final int FFMPEG_TIMEOUT_MINUTES = 30;

    private final StorageProvider storageProvider;
    private final DramaEpisodeService dramaEpisodeService;
    private final String publicBaseUrl;

    public VideoTranscodeService(StorageProvider storageProvider,
                                DramaEpisodeService dramaEpisodeService,
                                @Value("${duanju.storage.r2.public-base-url:}") String publicBaseUrl) {
        this.storageProvider = storageProvider;
        this.dramaEpisodeService = dramaEpisodeService;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    /**
     * 异步转码为 HLS。
     * <p>前置检查: 若当前已是转码中状态,直接跳过(防并发重入)。</p>
     */
    @Async("hlsTranscodeExecutor")
    public void transcodeToHls(Long episodeId, String videoUrl, String objectKey) {
        if (episodeId == null || videoUrl == null || objectKey == null) {
            log.warn("HLS转码参数不完整,跳过: episodeId={}, videoUrl={}, objectKey={}", episodeId, videoUrl, objectKey);
            return;
        }

        // 防并发重入: 若已经是转码中,直接返回,避免同一集同时跑多次FFmpeg
        DramaEpisode existing = dramaEpisodeService.lambdaQuery()
                .select(DramaEpisode::getId, DramaEpisode::getTranscodeStatus)
                .eq(DramaEpisode::getId, episodeId)
                .one();
        if (existing != null && existing.getTranscodeStatus() != null
                && existing.getTranscodeStatus() == 1) {
            log.info("HLS转码跳过: 该集已在转码中, episodeId={}", episodeId);
            return;
        }

        Path tempDir = null;
        try {
            // CAS 式设置状态: 只有状态不是"转码中"时才更新为转码中
            // 用 WHERE 条件 + 更新行数判断,防止两个并发请求同时把状态改成1
            boolean acquired = trySetTranscoding(episodeId);
            if (!acquired) {
                log.info("HLS转码跳过: 并发抢占失败, episodeId={}", episodeId);
                return;
            }

            log.info("开始HLS转码: episodeId={}, objectKey={}", episodeId, objectKey);

            tempDir = Files.createTempDirectory("hls_" + episodeId + "_");
            Path videoFile = tempDir.resolve("source.mp4");

            log.info("从R2下载原始视频: {} → {}", objectKey, videoFile);
            storageProvider.download(objectKey, videoFile);
            long videoSize = Files.size(videoFile);
            log.info("下载完成: {} bytes", videoSize);

            Path hlsDir = tempDir.resolve("hls");
            Files.createDirectories(hlsDir);

            String hlsPrefix = "hls/" + episodeId + "/";
            runFfmpegHls(videoFile, hlsDir, hlsPrefix);

            String playlistKey = hlsPrefix + "playlist.m3u8";
            String playlistUrl = publicBaseUrl + "/" + playlistKey;

            File[] hlsFiles = hlsDir.toFile().listFiles();
            if (hlsFiles == null || hlsFiles.length == 0) {
                throw new IllegalStateException("FFmpeg未生成HLS文件");
            }

            log.info("上传HLS分片到R2: {} 个文件", hlsFiles.length);
            for (File f : hlsFiles) {
                String fileName = f.getName();
                String key = hlsPrefix + fileName;
                String contentType;
                if (fileName.endsWith(".m3u8")) {
                    contentType = "application/vnd.apple.mpegurl";
                } else if (fileName.endsWith(".ts")) {
                    contentType = "video/mp2t";
                } else {
                    contentType = "application/octet-stream";
                }
                storageProvider.uploadToKey(f.toPath(), key, contentType);
            }

            setTranscodeStatus(episodeId, 2, playlistUrl);
            log.info("HLS转码完成: episodeId={}, playlistUrl={}", episodeId, playlistUrl);

        } catch (Exception e) {
            log.error("HLS转码失败: episodeId={}", episodeId, e);
            setTranscodeStatus(episodeId, -1, null);
        } finally {
            if (tempDir != null) {
                cleanupTempDir(tempDir);
            }
        }
    }

    /**
     * 尝试将转码状态设置为"转码中"(CAS风格)。
     * @return true 表示成功抢占到转码权,false 表示已有转码在进行
     */
    private boolean trySetTranscoding(Long episodeId) {
        UpdateWrapper<DramaEpisode> wrapper = new UpdateWrapper<>();
        wrapper.set("transcode_status", 1);
        wrapper.eq("id", episodeId);
        // 只有当状态不是"转码中"时才更新(0/-1/2/null 都允许抢占)
        wrapper.ne("transcode_status", 1);
        return dramaEpisodeService.update(wrapper);
    }

    private void runFfmpegHls(Path inputFile, Path outputDir, String hlsPrefix) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-i", inputFile.toAbsolutePath().toString(),
                "-c:v", "libx264",
                "-preset", "fast",
                "-crf", "23",
                "-maxrate", "2M",
                "-bufsize", "4M",
                "-vf", "scale='min(1280,iw)':-2",
                "-c:a", "aac",
                "-b:a", "128k",
                "-ac", "2",
                "-f", "hls",
                "-hls_time", "6",
                "-hls_list_size", "0",
                "-hls_segment_filename", outputDir.resolve("seg_%04d.ts").toAbsolutePath().toString(),
                outputDir.resolve("playlist.m3u8").toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // 流式读取 FFmpeg 输出,只保留最后 N 行用于错误日志,
        // 避免 readAllBytes() 把几百 MB 输出全读进内存。
        final int tailLines = 50;
        List<String> tailBuffer = new ArrayList<>(tailLines);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (tailBuffer.size() >= tailLines) {
                    tailBuffer.remove(0);
                }
                tailBuffer.add(line);
            }
        }

        // 带超时等待,防止 FFmpeg 卡死永久占住线程
        boolean finished = process.waitFor(FFMPEG_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            // 超时: 强制销毁进程
            process.destroyForcibly();
            String tailLog = String.join("\n", tailBuffer);
            throw new IllegalStateException("FFmpeg转码超时(" + FFMPEG_TIMEOUT_MINUTES
                    + "分钟),已强制终止。最后输出:\n" + tailLog);
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            String tailLog = String.join("\n", tailBuffer);
            throw new IllegalStateException("FFmpeg转码失败(exitCode=" + exitCode + ")。最后输出:\n" + tailLog);
        }
        log.info("FFmpeg转码成功,输出目录: {}", outputDir);
    }

    private void setTranscodeStatus(Long episodeId, int status, String hlsUrl) {
        UpdateWrapper<DramaEpisode> wrapper = new UpdateWrapper<>();
        wrapper.set("transcode_status", status);
        if (hlsUrl != null) {
            wrapper.set("hls_url", hlsUrl);
        }
        wrapper.eq("id", episodeId);
        dramaEpisodeService.update(wrapper);
    }

    /**
     * 删除某集的 HLS 分片文件(删除剧集时级联清理)。
     * <p>静默失败: 仅记录 warn,不抛异常,避免影响主流程。</p>
     */
    public void deleteHlsFiles(Long episodeId) {
        if (episodeId == null) return;
        try {
            String prefix = "hls/" + episodeId + "/";
            int deleted = storageProvider.deleteByPrefix(prefix);
            log.info("删除HLS分片: episodeId={}, prefix={}, deleted={}", episodeId, prefix, deleted);
        } catch (Exception e) {
            log.warn("删除HLS分片失败(忽略), episodeId={}", episodeId, e);
        }
    }

    private void cleanupTempDir(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                    });
        } catch (Exception ignored) {
        }
    }
}
