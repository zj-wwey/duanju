package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.dto.content.BatchEpisodeRequest;
import com.duanju.dto.content.CategoryFilterRequest;
import com.duanju.dto.content.DramaRequest;
import com.duanju.dto.content.EpisodeRequest;
import com.duanju.dto.content.FreePreviewRequest;
import com.duanju.dto.content.StorageObjectRequest;
import com.duanju.security.PrincipalHolder;
import com.duanju.security.RequiresPermission;
import com.duanju.service.CloudflareStreamService;
import com.duanju.service.DramaService;
import com.duanju.service.StorageService;
import com.duanju.service.VideoTranscodeService;
import com.duanju.util.MapUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiresPermission("content:manage")
@RequestMapping("/api/admin")
public class AdminContentController {

    private static final Logger log = LoggerFactory.getLogger(AdminContentController.class);

    private final DramaService dramaService;
    private final StorageService storageService;
    private final CloudflareStreamService cloudflareStreamService;
    private final VideoTranscodeService videoTranscodeService;

    public AdminContentController(DramaService dramaService,
                                  StorageService storageService,
                                  CloudflareStreamService cloudflareStreamService,
                                  VideoTranscodeService videoTranscodeService) {
        this.dramaService = dramaService;
        this.storageService = storageService;
        this.cloudflareStreamService = cloudflareStreamService;
        this.videoTranscodeService = videoTranscodeService;
    }

    @GetMapping("/category-filters")
    public R<List<Map<String, Object>>> categoryFilters() {
        return R.ok(dramaService.getAdminCategoryFilters());
    }

    @PostMapping("/category-filters")
    public R<Map<String, Object>> createCategoryFilter(@Valid @RequestBody CategoryFilterRequest request) {
        return R.ok(dramaService.createCategoryFilter(request.toMap(null)));
    }

    @PutMapping("/category-filters/{id}")
    public R<Void> updateCategoryFilter(@PathVariable Long id, @Valid @RequestBody CategoryFilterRequest request) {
        dramaService.updateCategoryFilter(request.toMap(id));
        return R.ok();
    }

    @DeleteMapping("/category-filters/{id}")
    public R<Void> deleteCategoryFilter(@PathVariable Long id) {
        dramaService.deleteCategoryFilter(id);
        return R.ok();
    }

    @GetMapping("/dramas")
    public R<List<Map<String, Object>>> dramas(@RequestParam(required = false) String contentType,
                                               @RequestParam(required = false) Integer status,
                                               @RequestParam(required = false) String keyword) {
        return R.ok(dramaService.getAdminDramas(contentType, status, keyword));
    }

    @PostMapping("/dramas")
    public R<Map<String, Object>> createDrama(@Valid @RequestBody DramaRequest request) {
        return R.ok(dramaService.createDrama(request.toMap(null)));
    }

    @PutMapping("/dramas/{id}")
    public R<Void> updateDrama(@PathVariable Long id, @Valid @RequestBody DramaRequest request) {
        dramaService.updateDrama(request.toMap(id));
        return R.ok();
    }

    @DeleteMapping("/dramas/{id}")
    public R<Void> deleteDrama(@PathVariable Long id) {
        dramaService.deleteDrama(id);
        return R.ok();
    }

    @GetMapping("/dramas/{id}/episodes")
    public R<List<Map<String, Object>>> episodes(@PathVariable Long id,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String accessType,
                                                 @RequestParam(required = false) Integer status) {
        return R.ok(dramaService.getAdminEpisodes(id, keyword, accessType, status));
    }

    @PostMapping("/dramas/{id}/episodes/free-preview")
    public R<Map<String, Object>> applyFreePreview(@PathVariable Long id,
                                                   @Valid @RequestBody FreePreviewRequest request) {
        int updated = dramaService.applyFreePreview(id, request.freeCount(), request.pricePoints());
        return R.ok(MapUtil.map("updated", updated));
    }

    @PostMapping("/episodes")
    public R<Map<String, Object>> createEpisode(@Valid @RequestBody EpisodeRequest request) {
        return R.ok(dramaService.createEpisode(request.toMap(null), request.dramaId()));
    }

    @PostMapping("/episodes/batch")
    public R<Map<String, Object>> batchCreateEpisodes(@Valid @RequestBody BatchEpisodeRequest request) {
        return R.ok(dramaService.batchCreateEpisodes(request));
    }

    @PutMapping("/episodes/{id}")
    public R<Void> updateEpisode(@PathVariable Long id, @Valid @RequestBody EpisodeRequest request) {
        dramaService.updateEpisode(request.toMap(id), request.dramaId());
        return R.ok();
    }

    @DeleteMapping("/episodes/{id}")
    public R<Void> deleteEpisode(@PathVariable Long id) {
        dramaService.deleteEpisode(id);
        return R.ok();
    }

    @PostMapping("/storage/object")
    public R<Map<String, Object>> saveStorageObject(@Valid @RequestBody StorageObjectRequest request) {
        return R.ok(storageService.saveStorageObject(request.storageProvider(), request.url()));
    }

    @PostMapping(value = "/storage/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam(defaultValue = "file") String type) {
        return R.ok(storageService.uploadFile(file, type));
    }

    /**
     * 签发预签名 PUT URL,允许浏览器直传到 R2,绕过 Cloudflare 代理 100MB 限制。
     *
     * <p>流程:</p>
     * <ol>
     *   <li>前端调用本接口,传入文件名/类型/业务类型,后端生成 objectKey 并签发 PUT URL</li>
     *   <li>前端用 fetch PUT 文件到 presignedUrl (不经本服务/橙云代理)</li>
     *   <li>上传完成后,前端用返回的 url/objectKey 填入表单字段,保存剧集/短剧时透传落库</li>
     * </ol>
     *
     * <p>本地存储模式返回 {@code supported=false},前端应 fallback 到 /storage/upload。</p>
     *
     * @param fileName    原始文件名 (用于生成 objectKey)
     * @param contentType MIME 类型 (作为签名约束)
     * @param type        业务类型 (image/video/avatar/file)
     */
    @PostMapping(value = "/storage/presign")
    public R<Map<String, Object>> presignStorage(@RequestParam("fileName") String fileName,
                                                  @RequestParam(value = "contentType", required = false) String contentType,
                                                  @RequestParam(value = "type", defaultValue = "file") String type) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName is required");
        }
        Map<String, Object> result = storageService.presignUpload(fileName, contentType, type);
        if (result == null) {
            // 本地存储不支持预签名,前端 fallback 到 multipart
            return R.ok(MapUtil.map("supported", false, "presignedUrl", ""));
        }
        result.put("supported", true);
        return R.ok(result);
    }

    /**
     * 上传视频到 Cloudflare Stream (管理员专用)。
     *
     * <p>返回 Cloudflare 视频 UID,前端拿到后填入 episodeForm.cloudflareUid,
     * 保存剧集时由后端写入 drama_episode.cloudflare_uid 列。</p>
     *
     * <p>HLS URL 和 video_duration 由 Cloudflare webhook 异步回写,
     * 详见 CloudflareStreamService.handleStreamCompleted。</p>
     *
     * @param file     视频文件 (mp4/m4v/webm/mov)
     * @param name     视频名称 (可选,默认用文件名)
     * @param dramaId  关联剧集 ID (可选,写入 Cloudflare meta 便于运营追溯)
     */
    @PostMapping(value = "/video/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Map<String, Object>> uploadStreamVideo(@RequestParam("file") MultipartFile file,
                                                     @RequestParam(value = "name", required = false) String name,
                                                     @RequestParam(value = "dramaId", required = false) Long dramaId) {
        if (!cloudflareStreamService.isEnabled()) {
            throw new IllegalStateException("cloudflare stream not enabled");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("video file is empty");
        }

        Long adminId = PrincipalHolder.adminId();
        String videoName = (name != null && !name.isBlank()) ? name : "video_" + System.currentTimeMillis();

        // meta 写入管理员 ID 和剧集 ID,便于在 Cloudflare Dashboard 追溯来源
        Map<String, String> meta = new LinkedHashMap<>();
        if (adminId != null) {
            meta.put("uploaderId", "admin:" + adminId);
        }
        if (dramaId != null) {
            meta.put("dramaId", String.valueOf(dramaId));
        }

        Map<String, Object> result = cloudflareStreamService.uploadVideo(file, videoName);
        String uid = MapUtil.str(result, "uid");
        if (uid != null) {
            // 返回签名 URL 供管理员即时预览,落库时不存此字段(会过期)
            result.put("signed_url", cloudflareStreamService.generateSignedUrl(uid));
        }

        log.info("Admin {} uploaded video to Cloudflare: uid={}, size={}, dramaId={}",
                adminId, uid, file.getSize(), dramaId);
        return R.ok(result);
    }

    /**
     * 创建 Cloudflare Stream 上传资源 (管理员专用,仅返回 uploadURL,客户端直传)。
     *
     * <p>用于绕过 Cloudflare 代理 100MB 限制:浏览器拿到 uploadURL 后,
     * 直接 POST 文件到 Cloudflare Stream 端点,不经本服务。</p>
     *
     * <p>流程:</p>
     * <ol>
     *   <li>前端调用本接口,后端调 Cloudflare Stream API 创建视频资源,返回 uid + uploadURL</li>
     *   <li>前端用 fetch POST 文件到 uploadURL (不经本服务/橙云代理)</li>
     *   <li>上传完成后,前端将 uid 填入 episodeForm.cloudflareUid,保存剧集时落库</li>
     *   <li>Cloudflare 异步处理视频,完成后通过 webhook 回写 HLS URL 和时长</li>
     * </ol>
     *
     * @param name    视频名称 (可选,默认用时间戳)
     * @param dramaId 关联剧集 ID (可选,写入 Cloudflare meta 便于追溯)
     */
    @PostMapping(value = "/video/init-upload")
    public R<Map<String, Object>> initStreamUpload(@RequestParam(value = "name", required = false) String name,
                                                    @RequestParam(value = "dramaId", required = false) Long dramaId) {
        if (!cloudflareStreamService.isEnabled()) {
            throw new IllegalStateException("cloudflare stream not enabled");
        }

        Long adminId = PrincipalHolder.adminId();
        String videoName = (name != null && !name.isBlank()) ? name : "video_" + System.currentTimeMillis();

        Map<String, String> meta = new LinkedHashMap<>();
        if (adminId != null) {
            meta.put("uploaderId", "admin:" + adminId);
        }
        if (dramaId != null) {
            meta.put("dramaId", String.valueOf(dramaId));
        }

        Map<String, Object> result = cloudflareStreamService.createStream(videoName, meta);
        String uid = MapUtil.str(result, "uid");
        if (uid != null) {
            result.put("signed_url", cloudflareStreamService.generateSignedUrl(uid));
        }

        log.info("Admin {} init video upload to Cloudflare: uid={}, dramaId={}", adminId, uid, dramaId);
        return R.ok(result);
    }

    @GetMapping("/episodes/{id}/transcode-status")
    public R<Map<String, Object>> transcodeStatus(@PathVariable Long id) {
        return R.ok(dramaService.getTranscodeStatus(id));
    }

    @PostMapping("/episodes/{id}/retranscode")
    public R<Map<String, Object>> retranscode(@PathVariable Long id) {
        return R.ok(dramaService.retranscodeEpisode(id));
    }
}
