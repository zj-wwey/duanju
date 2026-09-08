package com.duanju.controller;

import com.duanju.common.ForbiddenException;
import com.duanju.common.R;
import com.duanju.entity.DramaEpisode;
import com.duanju.security.PrincipalHolder;
import com.duanju.service.CloudflareStreamService;
import com.duanju.service.DramaService;
import com.duanju.service.StorageService;
import com.duanju.service.entity.DramaEntityService;
import com.duanju.service.entity.DramaEpisodeService;
import com.duanju.service.entity.UserEpisodeUnlockService;
import com.duanju.util.MapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 视频上传与播放控制器。
 *
 * <p>核心端点:</p>
 * <ul>
 *   <li>POST /api/user/video/upload - 上传视频到 Cloudflare Stream (管理员/运营使用)</li>
 *   <li>POST /api/user/video/init-upload - 创建上传资源 (大文件直传)</li>
 *   <li>GET /api/user/video/play/{episodeId} - 获取签名播放 URL (需要已解锁/免费的剧集)</li>
 *   <li>GET /api/user/video/signed-url/{uid} - 通过 uid 生成签名 URL (需验证访问权限)</li>
 *   <li>GET /api/user/video/{uid} - 查询视频详情 (需验证访问权限)</li>
 *   <li>DELETE /api/user/video/{uid} - 删除 Cloudflare Stream 视频 (仅管理员)</li>
 * </ul>
 *
 * <p>安全策略:</p>
 * <ul>
 *   <li>所有播放/查询端点均需验证:剧集存在 + 免费/已解锁/管理员</li>
 *   <li>删除端点仅限管理员</li>
 *   <li>签名 URL 有效期 TTL 秒 (默认 1 小时)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/user/video")
public class VideoController {

    private static final Logger log = LoggerFactory.getLogger(VideoController.class);

    private final CloudflareStreamService cloudflareStreamService;
    private final StorageService storageService;
    private final DramaEpisodeService dramaEpisodeService;
    private final DramaEntityService dramaEntityService;
    private final UserEpisodeUnlockService userEpisodeUnlockService;

    public VideoController(CloudflareStreamService cloudflareStreamService,
                          StorageService storageService,
                          DramaEpisodeService dramaEpisodeService,
                          DramaEntityService dramaEntityService,
                          UserEpisodeUnlockService userEpisodeUnlockService) {
        this.cloudflareStreamService = cloudflareStreamService;
        this.storageService = storageService;
        this.dramaEpisodeService = dramaEpisodeService;
        this.dramaEntityService = dramaEntityService;
        this.userEpisodeUnlockService = userEpisodeUnlockService;
    }

    /**
     * 上传视频文件到 Cloudflare Stream。
     */
    @PostMapping("/upload")
    public R<Map<String, Object>> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "dramaId", required = false) Long dramaId) {

        Long userId = PrincipalHolder.userId();
        if (userId == null) {
            throw new IllegalArgumentException("login required");
        }
        if (!cloudflareStreamService.isEnabled()) {
            throw new IllegalStateException("cloudflare stream not enabled");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("video file is empty");
        }

        String videoName = name != null ? name : "video_" + System.currentTimeMillis();

        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("uploaderId", String.valueOf(userId));
        if (dramaId != null) {
            meta.put("dramaId", String.valueOf(dramaId));
        }

        Map<String, Object> result = cloudflareStreamService.uploadVideo(file, videoName);

        String uid = MapUtil.str(result, "uid");
        if (uid != null) {
            String signedUrl = cloudflareStreamService.generateSignedUrl(uid);
            result.put("signed_url", signedUrl);
        }

        log.info("Video uploaded to Cloudflare: uid={}, size={}, uploaderId={}",
                uid, file.getSize(), userId);

        return R.ok(result);
    }

    /**
     * 为已解锁/免费的剧集生成签名播放 URL。
     *
     * <p>权限校验:</p>
     * <ol>
     *   <li>管理员 (admin) 可访问任意剧集</li>
     *   <li>免费剧集 (is_free=1 或 episode_no <= freeEpisodeCount) 所有人可看</li>
     *   <li>已积分解锁: 仅持有者可看</li>
     *   <li>未解锁: 拒绝访问</li>
     * </ol>
     */
    @GetMapping("/play/{episodeId}")
    public R<Map<String, Object>> getPlayUrl(@PathVariable Long episodeId) {
        Long userId = PrincipalHolder.userId();
        Long adminId = PrincipalHolder.adminId();

        DramaEpisode episode = dramaEpisodeService.lambdaQuery()
                .eq(DramaEpisode::getId, episodeId)
                .ge(DramaEpisode::getStatus, 0)
                .one();

        if (episode == null) {
            throw new IllegalArgumentException("episode not found");
        }

        assertEpisodeAccessible(episode, userId, adminId);

        String cloudflareUid = episode.getCloudflareUid();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("episodeId", episode.getId());
        result.put("title", episode.getTitle());
        result.put("durationSeconds", episode.getDurationSeconds());
        result.put("durationText", DramaService.formatDurationSeconds(episode.getDurationSeconds()));

        if (cloudflareUid != null && !cloudflareUid.isBlank()) {
            if (!cloudflareStreamService.isEnabled()) {
                throw new IllegalStateException("cloudflare stream not enabled");
            }
            String signedUrl = cloudflareStreamService.generateSignedUrl(cloudflareUid);
            result.put("type", "cloudflare");
            result.put("signed_url", signedUrl);
            result.put("hls_url", cloudflareStreamService.getHlsUrl(cloudflareUid));
        } else {
            result.put("type", "direct");
            result.put("video_url", episode.getVideoUrl());
            result.put("hls_url", episode.getHlsUrl());
        }

        return R.ok(result);
    }

    /**
     * 通过 Cloudflare UID 生成签名 URL。
     *
     * <p>安全校验:UID 必须关联到一个存在且用户有权访问的剧集。</p>
     */
    @GetMapping("/signed-url/{uid}")
    public R<Map<String, Object>> getSignedUrl(@PathVariable String uid) {
        Long userId = PrincipalHolder.userId();
        Long adminId = PrincipalHolder.adminId();
        assertUidAccessible(uid, userId, adminId);

        String signedUrl = cloudflareStreamService.generateSignedUrl(uid);
        Map<String, Object> result = MapUtil.map(
                "uid", uid,
                "signed_url", signedUrl,
                "hls_url", cloudflareStreamService.getHlsUrl(uid)
        );
        return R.ok(result);
    }

    /**
     * 续期签名播放 URL。
     *
     * <p>解决长视频播放过程中签名 URL 过期 (默认 1 小时) 的问题。
     * 前端在 URL 即将过期前 (如剩余 5 分钟) 调本接口换取新的签名 URL,
     * 无需中断播放重新拉剧集信息。</p>
     *
     * <p>权限校验与 /play/{episodeId} 一致:管理员/免费集/已解锁用户才能续期,
     * 避免未授权用户通过续期接口持续观看付费内容。</p>
     *
     * <p>仅适用于 Cloudflare Stream 视频 (cloudflare_uid 非空)。
     * 本地存储视频无签名 URL,调用返回 400。</p>
     *
     * @param episodeId 剧集 ID
     * @return 新的签名 URL (有效期 signed-url-ttl-seconds,默认 3600 秒)
     */
    @GetMapping("/renew/{episodeId}")
    public R<Map<String, Object>> renewSignedUrl(@PathVariable Long episodeId) {
        Long userId = PrincipalHolder.userId();
        Long adminId = PrincipalHolder.adminId();

        DramaEpisode episode = dramaEpisodeService.lambdaQuery()
                .eq(DramaEpisode::getId, episodeId)
                .ge(DramaEpisode::getStatus, 0)
                .one();

        if (episode == null) {
            throw new IllegalArgumentException("episode not found");
        }

        assertEpisodeAccessible(episode, userId, adminId);

        String cloudflareUid = episode.getCloudflareUid();
        if (cloudflareUid == null || cloudflareUid.isBlank()) {
            throw new IllegalArgumentException("episode is not a cloudflare stream video");
        }
        if (!cloudflareStreamService.isEnabled()) {
            throw new IllegalStateException("cloudflare stream not enabled");
        }

        String signedUrl = cloudflareStreamService.generateSignedUrl(cloudflareUid);
        Map<String, Object> result = MapUtil.map(
                "episodeId", episodeId,
                "uid", cloudflareUid,
                "signed_url", signedUrl,
                "hls_url", cloudflareStreamService.getHlsUrl(cloudflareUid)
        );
        return R.ok(result);
    }

    /**
     * 创建 Cloudflare Stream 上传资源 (仅获取 uploadURL,由客户端自行 PUT)。
     * 适用于大文件场景 (> 200MB)。
     */
    @PostMapping("/init-upload")
    public R<Map<String, Object>> initUpload(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "dramaId", required = false) Long dramaId) {

        Long userId = PrincipalHolder.userId();
        if (userId == null) {
            throw new IllegalArgumentException("login required");
        }
        if (!cloudflareStreamService.isEnabled()) {
            throw new IllegalStateException("cloudflare stream not enabled");
        }

        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("uploaderId", String.valueOf(userId));
        if (dramaId != null) {
            meta.put("dramaId", String.valueOf(dramaId));
        }

        Map<String, Object> result = cloudflareStreamService.createStream(name, meta);
        return R.ok(result);
    }

    /**
     * 删除 Cloudflare Stream 视频 (仅管理员)。
     */
    @DeleteMapping("/{uid}")
    public R<Void> deleteVideo(@PathVariable String uid) {
        Long adminId = PrincipalHolder.adminId();
        if (adminId == null) {
            throw new ForbiddenException("admin required");
        }
        if (!cloudflareStreamService.isEnabled()) {
            throw new IllegalStateException("cloudflare stream not enabled");
        }
        // 验证 uid 关联的剧集存在
        DramaEpisode episode = dramaEpisodeService.lambdaQuery()
                .eq(DramaEpisode::getCloudflareUid, uid)
                .ge(DramaEpisode::getStatus, 0)
                .one();
        if (episode == null) {
            log.warn("Attempted to delete video with uid not linked to any episode: {}", uid);
            throw new IllegalArgumentException("video not found");
        }
        cloudflareStreamService.deleteVideo(uid);
        log.info("Admin {} deleted video uid={}, episodeId={}", adminId, uid, episode.getId());
        return R.ok();
    }

    /**
     * 查询 Cloudflare Stream 视频详情 (需验证访问权限)。
     */
    @GetMapping("/{uid}")
    public R<Map<String, Object>> getVideoInfo(@PathVariable String uid) {
        Long userId = PrincipalHolder.userId();
        Long adminId = PrincipalHolder.adminId();
        assertUidAccessible(uid, userId, adminId);

        Map<String, Object> info = cloudflareStreamService.getVideoInfo(uid);
        if (info == null || info.isEmpty()) {
            return R.fail("video not found");
        }
        return R.ok(info);
    }

    // --- Security helpers ---

    /**
     * 验证用户是否有权访问指定剧集。
     * <p>通过规则:
     * 1) 管理员 → 通过
     * 2) 免费剧集 → 通过
     * 3) 已解锁 → 通过
     * 4) 其他 → 抛出 ForbiddenException
     */
    private void assertEpisodeAccessible(DramaEpisode episode, Long userId, Long adminId) {
        if (adminId != null) {
            return;
        }

        Long dramaId = episode.getDramaId();
        Integer episodeNo = episode.getEpisodeNo();
        Integer isFree = episode.getIsFree();

        // 免费剧集:is_free=1 或 episode_no <= 剧集免费集数
        boolean free = (isFree != null && isFree == 1);
        if (!free && episodeNo != null) {
            Map<String, Object> drama = dramaEntityService.drama(dramaId);
            if (drama != null) {
                int freeCount = MapUtil.integer(drama, "free_episode_count") == null
                        ? 0 : MapUtil.integer(drama, "free_episode_count");
                free = episodeNo <= freeCount;
            }
        }

        if (free) {
            return;
        }

        // 付费解锁检查
        if (userId != null && userEpisodeUnlockService.unlocked(userId, dramaId, episode.getId()) > 0) {
            return;
        }

        log.warn("Access denied to episode: episodeId={}, userId={}, adminId={}",
                episode.getId(), userId, adminId);
        throw new ForbiddenException("access denied");
    }

    /**
     * 通过 Cloudflare UID 查找关联剧集,并验证用户访问权限。
     */
    private void assertUidAccessible(String uid, Long userId, Long adminId) {
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("uid is required");
        }

        DramaEpisode episode = dramaEpisodeService.lambdaQuery()
                .eq(DramaEpisode::getCloudflareUid, uid)
                .ge(DramaEpisode::getStatus, 0)
                .one();

        if (episode == null) {
            log.warn("Access denied: uid not linked to any episode: {}, userId={}", uid, userId);
            throw new ForbiddenException("access denied");
        }

        assertEpisodeAccessible(episode, userId, adminId);
    }
}