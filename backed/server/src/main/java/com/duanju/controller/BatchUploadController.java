package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.security.RequiresPermission;
import com.duanju.service.BatchUploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * VPS 中转批量上传 API。
 *
 * <p>适用场景:管理员家宽上行受限,直传 Cloudflare 慢。
 * 改为先 scp 到 VPS,后端读取本地文件高速推送到 Cloudflare。</p>
 *
 * <p>使用流程:</p>
 * <ol>
 *   <li>管理员用 scp/rsync 上传视频到 VPS: {@code /opt/duanju/batch-upload/{dramaId}/*.mp4}</li>
 *   <li>调 {@code POST /api/admin/batch-upload/scan?dramaId=X} 扫描目录</li>
 *   <li>调 {@code POST /api/admin/batch-upload/start?dramaId=X&startEpisodeNo=1} 启动上传</li>
 *   <li>轮询 {@code GET /api/admin/batch-upload/status?dramaId=X} 查询进度</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/admin/batch-upload")
@RequiresPermission("content:manage")
public class BatchUploadController {
    private static final Logger log = LoggerFactory.getLogger(BatchUploadController.class);

    private final BatchUploadService batchUploadService;

    public BatchUploadController(BatchUploadService batchUploadService) {
        this.batchUploadService = batchUploadService;
    }

    /**
     * 扫描指定 dramaId 目录下的视频文件清单。
     * 管理员在 VPS 上传完文件后调此接口确认文件已就位。
     */
    @GetMapping("/scan")
    public R<List<Map<String, Object>>> scan(@RequestParam Long dramaId) {
        log.info("Scan batch upload dir: dramaId={}", dramaId);
        return R.ok(batchUploadService.scanFiles(dramaId));
    }

    /**
     * 启动批量上传任务 (异步执行,立即返回任务概览)。
     * 后端会按文件顺序依次上传到 Cloudflare Stream,并自动创建 episode 记录。
     *
     * @param dramaId        剧名 ID
     * @param startEpisodeNo 起始集号 (默认 1)
     */
    @PostMapping("/start")
    public R<Map<String, Object>> start(@RequestParam Long dramaId,
                                        @RequestParam(required = false, defaultValue = "1") Integer startEpisodeNo) {
        log.info("Start batch upload: dramaId={}, startEpisodeNo={}", dramaId, startEpisodeNo);
        return R.ok(batchUploadService.startUpload(dramaId, startEpisodeNo));
    }

    /**
     * 查询批量上传任务进度。
     * 前端可每 3-5 秒轮询一次。
     */
    @GetMapping("/status")
    public R<Map<String, Object>> status(@RequestParam Long dramaId) {
        return R.ok(batchUploadService.getStatus(dramaId));
    }
}
