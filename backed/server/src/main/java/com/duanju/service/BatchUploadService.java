package com.duanju.service;

import com.duanju.entity.Drama;
import com.duanju.service.entity.DramaEntityService;
import com.duanju.service.entity.DramaEpisodeService;
import com.duanju.util.MapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * VPS 中转批量上传服务。
 *
 * <p>适用场景:管理员家宽上行受限,直传对象存储慢。
 * 改为先 scp 到 VPS,后端读取本地文件 → 走 {@link StorageService} (R2 / local) 上传。</p>
 *
 * <p>历史:旧实现把文件推送到 Cloudflare Stream(中国大陆观众播放需走 Stream 签名/HLS,链路不稳)。
 * 按运营要求彻底放弃 Stream,统一走对象存储:</p>
 * <ul>
 *   <li>若 R2 已开 ({@code r2.enabled=true} + 配置齐全):流式上传到 R2,公开 URL = cdn.marastel.com/r2/...,
 *       经 Cloudflare 全球/CN 边缘节点分发,国内播放稳定。</li>
 *   <li>若 R2 未开:回落到本地磁盘(local),URL 走 public-base-url;适合单机 + 反代直连 VPS 的场景。</li>
 * </ul>
 *
 * <p>流程:</p>
 * <ol>
 *   <li>管理员 scp 视频到 {@code /opt/duanju/batch-upload/{dramaId}/*.mp4}</li>
 *   <li>前端调 {@code POST /api/admin/batch-upload/scan?dramaId=X} 扫描目录列出文件</li>
 *   <li>前端调 {@code POST /api/admin/batch-upload/start?dramaId=X} 启动异步上传任务</li>
 *   <li>前端轮询 {@code GET /api/admin/batch-upload/status?dramaId=X} 看进度</li>
 * </ol>
 *
 * <p>幂等性:若某集 episode_no 已存在且 video_url 非空,跳过该文件避免重复上传 / 重复计费。
 * 若 video_url 为空 (上传中断恢复场景),重新上传。</p>
 */
@Service
public class BatchUploadService {
    private static final Logger log = LoggerFactory.getLogger(BatchUploadService.class);

    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "m4v", "mov", "webm");

    /**
     * 保留原 cloudflare 依赖只用于兼容老剧:删除剧集时,若有 cloudflareUid 还需要清 Stream 对象。
     * 新批量上传逻辑已不再写入 cloudflareUid/storageProvider=cloudflare。
     */
    private final CloudflareStreamService cloudflareStreamService;
    private final StorageService storageService;
    private final DramaService dramaService;
    private final DramaEntityService dramaEntityService;
    private final DramaEpisodeService dramaEpisodeService;

    private final Path batchUploadDir;
    private final int concurrency;

    // 任务状态存储 (key=dramaId, value=任务状态),内存级,重启丢失
    private final ConcurrentHashMap<Long, BatchUploadTask> taskStatus = new ConcurrentHashMap<>();
    // 线程池 (固定大小,避免无限并发触发对象存储 429 / 打爆 VPS 带宽)
    private final ExecutorService executor;

    public BatchUploadService(
            CloudflareStreamService cloudflareStreamService,
            StorageService storageService,
            DramaService dramaService,
            DramaEntityService dramaEntityService,
            DramaEpisodeService dramaEpisodeService,
            @Value("${duanju.cloudflare.batch-upload-dir:/opt/duanju/batch-upload}") String batchUploadDir,
            @Value("${duanju.cloudflare.batch-upload-concurrency:3}") int concurrency) {
        this.cloudflareStreamService = cloudflareStreamService;
        this.storageService = storageService;
        this.dramaService = dramaService;
        this.dramaEntityService = dramaEntityService;
        this.dramaEpisodeService = dramaEpisodeService;
        this.batchUploadDir = Paths.get(batchUploadDir).toAbsolutePath().normalize();
        this.concurrency = Math.max(1, concurrency);
        this.executor = Executors.newFixedThreadPool(this.concurrency,
                r -> {
                    Thread t = new Thread(r, "batch-upload-worker");
                    t.setDaemon(true);
                    return t;
                });
        log.info("BatchUploadService initialized: dir={}, concurrency={}, storageProvider={}, storageEnabled={}",
                this.batchUploadDir, this.concurrency,
                storageService.currentProviderName(), storageService.isStorageEnabled());
    }

    /**
     * 扫描指定 dramaId 目录下的视频文件,按文件名自然排序返回。
     *
     * @param dramaId 剧名 ID
     * @return 文件清单 (path, name, sizeBytes)
     */
    public List<Map<String, Object>> scanFiles(Long dramaId) {
        Path dramaDir = resolveDramaDir(dramaId);
        if (!Files.isDirectory(dramaDir)) {
            return List.of();
        }
        List<Path> files;
        try (Stream<Path> stream = Files.list(dramaDir)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String ext = extOf(p.getFileName().toString());
                        return VIDEO_EXTENSIONS.contains(ext);
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString(),
                            Comparator.nullsFirst(Comparator.naturalOrder())))
                    .toList();
        } catch (Exception e) {
            log.warn("Scan batch upload dir failed: dramaId={}, dir={}", dramaId, dramaDir, e);
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            Path f = files.get(i);
            long size;
            try {
                size = Files.size(f);
            } catch (Exception e) {
                size = 0;
            }
            result.add(MapUtil.map(
                    "sortOrder", i + 1,
                    "fileName", f.getFileName().toString(),
                    "path", f.toString(),
                    "sizeBytes", size
            ));
        }
        return result;
    }

    /**
     * 启动批量上传任务 (异步执行)。
     *
     * @param dramaId        剧名 ID
     * @param startEpisodeNo 起始集号 (默认 1)
     * @return 已接受的任务概览
     */
    public Map<String, Object> startUpload(Long dramaId, Integer startEpisodeNo) {
        if (!storageService.isStorageEnabled()) {
            throw new IllegalStateException("存储未就绪(当前 provider="
                    + storageService.currentProviderName()
                    + ")。若使用 R2,请检查 application.yml 中 duanju.storage.r2.* 配置项,并重启容器。");
        }
        // 同一 dramaId 同时只允许一个上传任务
        BatchUploadTask existing = taskStatus.get(dramaId);
        if (existing != null && existing.status.equals("running")) {
            throw new IllegalStateException("Batch upload already running for dramaId=" + dramaId);
        }

        // 校验 drama 存在
        Drama drama = dramaEntityService.lambdaQuery()
                .select(Drama::getId, Drama::getTitle, Drama::getEpisodePricePoints)
                .eq(Drama::getId, dramaId)
                .ge(Drama::getStatus, 0)
                .one();
        if (drama == null) {
            throw new IllegalArgumentException("drama not found: " + dramaId);
        }

        List<Map<String, Object>> files = scanFiles(dramaId);
        if (files.isEmpty()) {
            throw new IllegalStateException("no video files in " + resolveDramaDir(dramaId));
        }

        int startNo = startEpisodeNo == null || startEpisodeNo < 1 ? 1 : startEpisodeNo;
        BatchUploadTask task = new BatchUploadTask();
        task.dramaId = dramaId;
        task.dramaTitle = drama.getTitle();
        task.totalFiles = files.size();
        task.successCount = new AtomicInteger(0);
        task.failCount = new AtomicInteger(0);
        task.processedCount = new AtomicInteger(0);
        task.status = "running";
        task.startTime = System.currentTimeMillis();
        task.files = files.stream()
                .map(f -> new FileStatus(
                        MapUtil.str(f, "fileName"),
                        MapUtil.str(f, "path"),
                        ((Number) f.get("sortOrder")).intValue(),
                        "pending", null, null))
                .toList();
        taskStatus.put(dramaId, task);

        // 异步执行上传,按顺序串行 (避免对象存储 429 + 内存占用可控)
        // 并发由 ExecutorService 池大小控制 (同时处理多个 dramaId 任务)
        executor.submit(() -> runUploadTask(task, drama, startNo));

        return MapUtil.map(
                "dramaId", dramaId,
                "dramaTitle", drama.getTitle(),
                "totalFiles", files.size(),
                "startEpisodeNo", startNo,
                "status", "running",
                "storageProvider", storageService.currentProviderName()
        );
    }

    /**
     * 查询任务状态。
     */
    public Map<String, Object> getStatus(Long dramaId) {
        BatchUploadTask task = taskStatus.get(dramaId);
        if (task == null) {
            return MapUtil.map("dramaId", dramaId, "status", "none");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dramaId", task.dramaId);
        result.put("dramaTitle", task.dramaTitle);
        result.put("status", task.status);
        result.put("totalFiles", task.totalFiles);
        result.put("processed", task.processedCount.get());
        result.put("success", task.successCount.get());
        result.put("fail", task.failCount.get());
        result.put("elapsedMillis", System.currentTimeMillis() - task.startTime);
        result.put("files", task.files.stream()
                .map(f -> MapUtil.map(
                        "fileName", f.fileName,
                        "sortOrder", f.sortOrder,
                        "status", f.status,
                        "episodeNo", f.episodeNo,
                        "error", f.error))
                .toList());
        return result;
    }

    // ================= 内部实现 =================

    private void runUploadTask(BatchUploadTask task, Drama drama, int startNo) {
        log.info("Batch upload started: dramaId={}, title={}, files={}, startNo={}, storageProvider={}",
                task.dramaId, task.dramaTitle, task.totalFiles, startNo, storageService.currentProviderName());
        // Spring 事务:每集创建单独事务,即使单集失败,后续集仍能继续
        try {
            for (FileStatus fs : task.files) {
                int episodeNo = startNo + fs.sortOrder - 1;
                fs.episodeNo = episodeNo;
                try {
                    // 幂等:同集 video_url 已存在则跳过 (R2/local 新方案不再写 cloudflareUid,
                    // 所以不能用 cloudflareUid 做幂等依据)。
                    if (isEpisodeUploaded(task.dramaId, episodeNo)) {
                        fs.status = "skipped";
                        task.processedCount.incrementAndGet();
                        log.info("Batch upload skip existing episode: dramaId={}, episodeNo={}, file={}",
                                task.dramaId, episodeNo, fs.fileName);
                        continue;
                    }
                    fs.status = "uploading";

                    // ===== 上传到对象存储(R2 / local),全程流式,500MB 也不会 OOM =====
                    long fileSize = 0L;
                    try {
                        fileSize = Files.size(Path.of(fs.path));
                    } catch (Exception ignored) {
                    }
                    Map<String, Object> storageResult = storageService.uploadFromLocalFile(
                            Path.of(fs.path), fs.fileName, "video");
                    String videoUrl = MapUtil.str(storageResult, "url");
                    String storageProvider = MapUtil.str(storageResult, "storageProvider");
                    if (videoUrl == null || videoUrl.isBlank()) {
                        throw new IllegalStateException("storage returned empty url. provider="
                                + (storageProvider == null ? storageService.currentProviderName() : storageProvider));
                    }
                    fs.uid = MapUtil.str(storageResult, "objectKey");

                    // R2 侧也尝试探测时长(仅在 provider=local 或 public-url 指向本地时才能测到)。
                    // 海外对象存储没在本机,探测不到 duration 是常态,运营在剧集列表手动填即可,不为 0 就用上。
                    Integer duration = MapUtil.integer(storageResult, "durationSeconds");

                    // 创建 episode 记录:videoUrl 填 R2 的 CDN 公开 URL,
                    // cloudflareUid=null, storageProvider=r2 (或 local)。
                    // DramaService.getDramaDetail 播放侧逻辑会直接把 video_url 透出给前端播放器 (video / hls)。
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("dramaId", task.dramaId);
                    row.put("episodeNo", episodeNo);
                    row.put("title", stripExt(fs.fileName));
                    row.put("description", null);
                    row.put("coverUrl", null);
                    row.put("videoUrl", videoUrl);
                    // 新批量上传已彻底放弃 Stream,cloudflare_uid 永远清空。
                    row.put("cloudflareUid", null);
                    row.put("pricePoints", drama.getEpisodePricePoints() == null ? 0 : drama.getEpisodePricePoints());
                    row.put("durationSeconds", duration == null ? 0 : duration);
                    row.put("videoDurationSeconds", duration == null ? 0 : duration);
                    row.put("isFree", 0);
                    row.put("accessType", "POINTS");
                    row.put("sortOrder", episodeNo);
                    // r2 / local,取决于 StorageProvider 当前激活的实现
                    row.put("storageProvider", (storageProvider == null || storageProvider.isBlank())
                            ? storageService.currentProviderName()
                            : storageProvider);
                    row.put("status", 0);
                    dramaService.createEpisode(row, task.dramaId);

                    fs.status = "success";
                    task.successCount.incrementAndGet();
                    log.info("Batch upload success: dramaId={}, episodeNo={}, url={}, size={}B, file={}",
                            task.dramaId, episodeNo, videoUrl, fileSize, fs.fileName);
                } catch (Exception e) {
                    fs.status = "fail";
                    // 用户要求异常兜底不返回 Exception 原始消息:保留简短 message,
                    // 去掉堆栈/SDK 长串 cause 原文,避免把后端内部详情泄露到前端。
                    fs.error = safeErrorMessage(e);
                    task.failCount.incrementAndGet();
                    log.warn("Batch upload fail: dramaId={}, episodeNo={}, file={}, error={}",
                            task.dramaId, episodeNo, fs.fileName, safeErrorMessage(e), e);
                } finally {
                    task.processedCount.incrementAndGet();
                }
            }
            task.status = "done";
        } catch (Exception e) {
            task.status = "error";
            log.error("Batch upload task fatal error: dramaId={}", task.dramaId, e);
        } finally {
            task.endTime = System.currentTimeMillis();
            log.info("Batch upload finished: dramaId={}, status={}, success={}, fail={}, total={}, elapsed={}ms, storageProvider={}",
                    task.dramaId, task.status, task.successCount.get(), task.failCount.get(),
                    task.totalFiles, task.endTime - task.startTime, storageService.currentProviderName());
        }
    }

    /**
     * 用户硬约束:异常兜底不返回 Exception 原始消息/cause 全量内容。
     * 批量上传返回给前端管理员看的错误只保留「类型 + 简短说明」,不泄露堆栈/SDK 长串。
     */
    private static String safeErrorMessage(Exception e) {
        if (e == null) return "未知错误";
        String type = e.getClass().getSimpleName();
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            return type;
        }
        // 去掉任何换行,截断到 160 字符,避免刷屏
        String shortMsg = msg.replace('\r', ' ').replace('\n', ' ').trim();
        if (shortMsg.length() > 160) {
            shortMsg = shortMsg.substring(0, 160) + "…";
        }
        return type + ": " + shortMsg;
    }

    /**
     * 检查某集是否已上传 (video_url 非空视为已上传,可跳过)。
     *
     * <p>历史:旧 Stream 方案用 cloudflare_uid 判定,但新 R2/local 对象存储方案
     * 不填 cloudflareUid,只填 videoUrl(公开 CDN 链接)。所以兼容两种:任一非空都算已上传。</p>
     */
    private boolean isEpisodeUploaded(Long dramaId, int episodeNo) {
        Long count = dramaEpisodeService.lambdaQuery()
                .eq(com.duanju.entity.DramaEpisode::getDramaId, dramaId)
                .eq(com.duanju.entity.DramaEpisode::getEpisodeNo, episodeNo)
                .ge(com.duanju.entity.DramaEpisode::getStatus, 0)
                .and(w -> w.isNotNull(com.duanju.entity.DramaEpisode::getCloudflareUid)
                        .or().isNotNull(com.duanju.entity.DramaEpisode::getVideoUrl))
                .count();
        return count != null && count > 0;
    }

    private Path resolveDramaDir(Long dramaId) {
        Path dir = batchUploadDir.resolve(String.valueOf(dramaId)).normalize();
        // 路径穿越防护
        if (!dir.startsWith(batchUploadDir)) {
            throw new IllegalArgumentException("invalid dramaId");
        }
        return dir;
    }

    private static String extOf(String filename) {
        if (filename == null) return "";
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) return "";
        return filename.substring(idx + 1).toLowerCase();
    }

    private static String stripExt(String filename) {
        if (filename == null) return "";
        int idx = filename.lastIndexOf('.');
        return idx > 0 ? filename.substring(0, idx) : filename;
    }

    // ================= 内部状态对象 =================

    private static class BatchUploadTask {
        Long dramaId;
        String dramaTitle;
        String status; // running | done | error
        int totalFiles;
        AtomicInteger processedCount;
        AtomicInteger successCount;
        AtomicInteger failCount;
        long startTime;
        long endTime;
        List<FileStatus> files;
    }

    private static class FileStatus {
        final String fileName;
        final String path;
        final int sortOrder;
        String status; // pending | uploading | success | fail | skipped
        Integer episodeNo;
        String uid;
        String error;

        FileStatus(String fileName, String path, int sortOrder, String status, Integer episodeNo, String error) {
            this.fileName = fileName;
            this.path = path;
            this.sortOrder = sortOrder;
            this.status = status;
            this.episodeNo = episodeNo;
            this.error = error;
        }
    }
}
