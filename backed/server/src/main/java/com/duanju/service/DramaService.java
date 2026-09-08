package com.duanju.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.duanju.dto.content.BatchEpisodeRequest;
import com.duanju.entity.Drama;
import com.duanju.entity.DramaCategoryFilter;
import com.duanju.entity.DramaEpisode;
import com.duanju.entity.UserFavorite;
import com.duanju.entity.UserLike;
import com.duanju.security.PrincipalHolder;
import com.duanju.service.entity.DramaCategoryFilterService;
import com.duanju.service.entity.DramaEntityService;
import com.duanju.service.entity.DramaEpisodeService;
import com.duanju.service.entity.UserEpisodeUnlockService;
import com.duanju.service.entity.UserFavoriteService;
import com.duanju.service.entity.UserLikeService;
import com.duanju.util.MapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DramaService {

    private static final Logger log = LoggerFactory.getLogger(DramaService.class);

    private final DramaEntityService dramaEntityService;
    private final DramaEpisodeService dramaEpisodeService;
    private final DramaCategoryFilterService dramaCategoryFilterService;
    private final UserFavoriteService userFavoriteService;
    private final UserLikeService userLikeService;
    private final UserEpisodeUnlockService userEpisodeUnlockService;
    private final CloudflareStreamService cloudflareStreamService;
    private final ContentTranslationService contentTranslationService;
    private final StorageService storageService;
    private final VideoTranscodeService videoTranscodeService;

    public DramaService(DramaEntityService dramaEntityService,
                        DramaEpisodeService dramaEpisodeService,
                        DramaCategoryFilterService dramaCategoryFilterService,
                        UserFavoriteService userFavoriteService,
                        UserLikeService userLikeService,
                        UserEpisodeUnlockService userEpisodeUnlockService,
                        CloudflareStreamService cloudflareStreamService,
                        ContentTranslationService contentTranslationService,
                        StorageService storageService,
                        VideoTranscodeService videoTranscodeService) {
        this.dramaEntityService = dramaEntityService;
        this.dramaEpisodeService = dramaEpisodeService;
        this.dramaCategoryFilterService = dramaCategoryFilterService;
        this.userFavoriteService = userFavoriteService;
        this.userLikeService = userLikeService;
        this.userEpisodeUnlockService = userEpisodeUnlockService;
        this.cloudflareStreamService = cloudflareStreamService;
        this.contentTranslationService = contentTranslationService;
        this.storageService = storageService;
        this.videoTranscodeService = videoTranscodeService;
    }

    // --- Public browsing ---

    public List<Map<String, Object>> getCategoryFilters() {
        return getCategoryFilters(null);
    }

    public List<Map<String, Object>> getCategoryFilters(String locale) {
        Map<String, Map<String, Object>> groups = new LinkedHashMap<>();
        for (Map<String, Object> row : dramaCategoryFilterService.categoryFilters()) {
            String groupKey = MapUtil.str(row, "group_key");
            Map<String, Object> group = groups.computeIfAbsent(groupKey, key -> MapUtil.map(
                    "key", key,
                    "label", MapUtil.str(row, "group_label_key"),
                    "options", new ArrayList<Map<String, Object>>()
            ));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> options = (List<Map<String, Object>>) group.get("options");
            String optionKey = MapUtil.str(row, "option_key");
            String optionLabelKey = MapUtil.str(row, "option_label_key");
            Map<String, Object> option = MapUtil.map(
                    "key", optionKey,
                    "label", optionLabelKey
            );
            if (locale != null && !"zh-CN".equalsIgnoreCase(locale)) {
                String translatedLabel = contentTranslationService.translate(
                        "category_option", groupKey + ":" + optionKey, "label", locale);
                if (translatedLabel != null) {
                    option.put("labelLocalized", translatedLabel);
                }
            }
            options.add(option);
        }
        return new ArrayList<>(groups.values());
    }

    public List<Map<String, Object>> getDramas(String contentType, String keyword, String background,
                                               String theme, String setting, String audience,
                                               String time, String sort) {
        return getDramas(contentType, keyword, background, theme, setting, audience, time, sort, null);
    }

    public List<Map<String, Object>> getDramas(String contentType, String keyword, String background,
                                               String theme, String setting, String audience,
                                               String time, String sort, String locale) {
        List<Map<String, Object>> dramas = dramaEntityService.dramas(
                contentTypeValue(contentType),
                keywordValue(keyword),
                normalizedValue(background),
                normalizedValue(theme),
                normalizedValue(setting),
                normalizedValue(audience),
                timeDays(time),
                sortValue(sort)
        );
        if (locale != null && !"zh-CN".equalsIgnoreCase(locale)) {
            for (Map<String, Object> drama : dramas) {
                enrichDramaTranslation(drama, locale);
            }
        }
        return dramas;
    }

    /** Feed 流：每剧一条，附带一集播放信息，分页 */
    public Map<String, Object> getFeed(String contentType, Boolean recommended, int page, int size, String locale) {
        if (page < 1) page = 1;
        if (size < 1) size = 10;
        if (size > 30) size = 30;
        int offset = (page - 1) * size;
        // contentType 可能是前端传的 "ai" 等，也可能是实际 DB 值
        String dbContentType = contentType;
        List<Map<String, Object>> items = dramaEntityService.feedDramas(dbContentType, recommended, offset, size);
        int total = dramaEntityService.countFeedDramas(dbContentType, recommended);
        Long userId = PrincipalHolder.userId();
        for (Map<String, Object> item : items) {
            enrichFeedItem(item, userId);
            if (locale != null && !"zh-CN".equalsIgnoreCase(locale)) {
                enrichDramaTranslation(item, locale);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("page", page);
        result.put("size", size);
        result.put("total", total);
        result.put("hasMore", offset + items.size() < total);
        result.put("list", items);
        return result;
    }

    private void enrichFeedItem(Map<String, Object> item, Long userId) {
        // 构造 playbackUrl
        String cloudflareUid = MapUtil.str(item, "cloudflare_uid");
        String hlsUrl = MapUtil.str(item, "hls_url");
        if (cloudflareUid != null && !cloudflareUid.isBlank()) {
            if (hlsUrl == null || hlsUrl.isBlank()) {
                hlsUrl = cloudflareStreamService.getHlsUrl(cloudflareUid);
            }
            item.put("playback_url", hlsUrl);
            item.put("playback_type", "hls");
        } else {
            item.put("playback_url", MapUtil.str(item, "video_url"));
            item.put("playback_type", "direct");
        }
        // 重命名 drama_id / episode_id 给前端 camelCase 友好的 key
        item.put("dramaId", item.get("drama_id"));
        item.put("episodeId", item.get("episode_id"));
        // 用户态：点赞 / 收藏 / 解锁
        Long dramaId = MapUtil.lng(item, "drama_id");
        Long episodeId = MapUtil.lng(item, "episode_id");
        boolean liked = false;
        boolean favorite = false;
        boolean unlocked = false;
        if (userId != null && dramaId != null) {
            liked = userLikeService.lambdaQuery()
                    .eq(UserLike::getUserId, userId).eq(UserLike::getDramaId, dramaId).exists();
            favorite = userFavoriteService.lambdaQuery()
                    .eq(UserFavorite::getUserId, userId).eq(UserFavorite::getDramaId, dramaId).exists();
            if (episodeId != null) {
                Integer isFree = MapUtil.integer(item, "is_free");
                if (isFree != null && isFree == 1) {
                    unlocked = true;
                } else {
                    unlocked = userEpisodeUnlockService.lambdaQuery()
                            .eq(com.duanju.entity.UserEpisodeUnlock::getUserId, userId)
                            .eq(com.duanju.entity.UserEpisodeUnlock::getEpisodeId, episodeId)
                            .exists();
                }
            }
        }
        item.put("liked", liked);
        item.put("favorite", favorite);
        item.put("unlocked", unlocked);
    }

    public Map<String, Object> getDramaDetail(Long id) {
        return getDramaDetail(id, null);
    }

    public Map<String, Object> getDramaDetail(Long id, String locale) {
        Map<String, Object> drama = dramaEntityService.drama(id);
        if (drama == null) {
            throw new IllegalArgumentException("drama not found");
        }
        if (locale != null && !"zh-CN".equalsIgnoreCase(locale)) {
            enrichDramaTranslation(drama, locale);
        }
        Long userId = PrincipalHolder.userId();
        int freeCount = MapUtil.integer(drama, "free_episode_count") == null ? 0 : MapUtil.integer(drama, "free_episode_count");
        List<Map<String, Object>> episodes = dramaEpisodeService.episodes(id);
        for (Map<String, Object> episode : episodes) {
            int episodeNo = MapUtil.integer(episode, "episode_no");
            Long episodeId = MapUtil.lng(episode, "id");
            Integer isFree = MapUtil.integer(episode, "is_free");
            boolean free = (isFree != null && isFree == 1) || episodeNo <= freeCount;
            boolean unlocked = userId != null && userEpisodeUnlockService.unlocked(userId, id, episodeId) > 0;
            episode.put("free", free);
            episode.put("unlocked", free || unlocked);
            episode.put("durationText", formatDurationSeconds(MapUtil.integer(episode, "duration_seconds")));
            String cloudflareUid = MapUtil.str(episode, "cloudflareUid");
            if (!free && !unlocked) {
                episode.put("video_url", null);
                episode.put("signed_url", null);
                episode.put("hls_url", null);
            } else if (cloudflareUid != null && !cloudflareUid.isBlank() && cloudflareStreamService.isEnabled()) {
                String signedUrl = cloudflareStreamService.generateSignedUrl(cloudflareUid);
                episode.put("video_url", signedUrl);
                episode.put("signed_url", signedUrl);
                episode.put("hls_url", cloudflareStreamService.getHlsUrl(cloudflareUid));
                episode.remove("cloudflareUid");
            } else {
                // R2 / OSS / COS / local 对象存储(新方案,或运营在后台填的外链):
                // 直接把 episode.videoUrl 透出给前端播放器,video_url 就用这个公开 URL。
                String videoUrl = MapUtil.str(episode, "videoUrl");
                if (videoUrl == null || videoUrl.isBlank()) {
                    videoUrl = MapUtil.str(episode, "video_url");
                }
                String videoUrlOut = (videoUrl == null || videoUrl.isBlank()) ? null : videoUrl;
                episode.put("video_url", videoUrlOut);
                episode.put("signed_url", videoUrlOut);
                String hlsUrl = MapUtil.str(episode, "hlsUrl");
                if (hlsUrl == null || hlsUrl.isBlank()) {
                    hlsUrl = MapUtil.str(episode, "hls_url");
                }
                if (hlsUrl == null || hlsUrl.isBlank()) {
                    hlsUrl = (videoUrlOut != null && videoUrlOut.toLowerCase().contains(".m3u8")) ? videoUrlOut : null;
                }
                episode.put("hls_url", hlsUrl);
            }
        }
        drama.put("episodes", episodes);
        drama.put("favorite", userId != null && userFavoriteService.lambdaQuery()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getDramaId, id)
                .exists());
        drama.put("liked", userId != null && userLikeService.lambdaQuery()
                .eq(UserLike::getUserId, userId)
                .eq(UserLike::getDramaId, id)
                .exists());
        return drama;
    }

    private void enrichDramaTranslation(Map<String, Object> drama, String locale) {
        Long dramaId = MapUtil.lng(drama, "id");
        if (dramaId == null) {
            return;
        }
        String translatedTitle = contentTranslationService.translate(
                "drama", String.valueOf(dramaId), "title", locale);
        if (translatedTitle != null) {
            drama.put("titleLocalized", translatedTitle);
        }
        String translatedSynopsis = contentTranslationService.translate(
                "drama", String.valueOf(dramaId), "synopsis", locale);
        if (translatedSynopsis != null) {
            drama.put("synopsisLocalized", translatedSynopsis);
        }
        translateCategoryOption(drama, "background", locale);
        translateCategoryOption(drama, "theme", locale);
        translateCategoryOption(drama, "setting", locale);
        translateCategoryOption(drama, "audience", locale);
        translateCategoryOption(drama, "content_type", locale);
    }

    private void translateCategoryOption(Map<String, Object> drama, String field, String locale) {
        String value = MapUtil.str(drama, field);
        if (value == null || value.isBlank()) {
            return;
        }
        String translated = contentTranslationService.translate(
                "category_option", field + ":" + value, "label", locale);
        if (translated != null) {
            drama.put(field + "Localized", translated);
        }
    }

    // --- Admin: category filters ---

    public List<Map<String, Object>> getAdminCategoryFilters() {
        return dramaCategoryFilterService.adminCategoryFilters();
    }

    public Map<String, Object> createCategoryFilter(Map<String, Object> row) {
        dramaCategoryFilterService.insertCategoryFilter(row);
        return row;
    }

    public void updateCategoryFilter(Map<String, Object> row) {
        dramaCategoryFilterService.updateCategoryFilter(row);
    }

    public void deleteCategoryFilter(Long id) {
        dramaCategoryFilterService.lambdaUpdate()
                .set(DramaCategoryFilter::getStatus, -1)
                .eq(DramaCategoryFilter::getId, id)
                .update();
    }

    // --- Admin: dramas ---

    public List<Map<String, Object>> getAdminDramas(String contentType, Integer status, String keyword) {
        return dramaEntityService.adminDramas(filterValue(contentType), status, filterValue(keyword));
    }

    public Map<String, Object> createDrama(Map<String, Object> row) {
        dramaEntityService.insertDrama(row);
        return row;
    }

    public void updateDrama(Map<String, Object> row) {
        dramaEntityService.updateDrama(row);
    }

    @Transactional
    public void deleteDrama(Long id) {
        // 查询该剧集下所有需要级联删除的 Cloudflare 视频 uid (仅 cloudflare 模式且未软删的)
        // 先查后删:若先软删 DB (status=-1) 再查,就捞不到 uid 了
        List<String> cloudflareUids = List.of();
        boolean streamEnabled = cloudflareStreamService.isEnabled();
        if (streamEnabled) {
            cloudflareUids = dramaEpisodeService.lambdaQuery()
                    .select(DramaEpisode::getCloudflareUid)
                    .eq(DramaEpisode::getDramaId, id)
                    .ge(DramaEpisode::getStatus, 0)
                    .isNotNull(DramaEpisode::getCloudflareUid)
                    .list()
                    .stream()
                    .map(DramaEpisode::getCloudflareUid)
                    .filter(uid -> uid != null && !uid.isBlank())
                    .toList();
        }

        // 逐个删除 Cloudflare 视频 (Cloudflare 无批量删 API)
        // 单个失败不阻断其他视频删除和 DB 软删,最后统计成功/失败数便于运维追溯
        int successCount = 0;
        int failCount = 0;
        for (String uid : cloudflareUids) {
            try {
                cloudflareStreamService.deleteVideo(uid);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.warn("Failed to delete Cloudflare video uid={} when deleting dramaId={}, skip and continue. Manual cleanup required.",
                        uid, id, e);
            }
        }
        if (!cloudflareUids.isEmpty()) {
            log.info("Delete dramaId={}: Cloudflare video cascade delete done, success={}, fail={}, total={}",
                    id, successCount, failCount, cloudflareUids.size());
        }

        // 级联删除该剧下所有分集的视频存储对象(R2 / 本地对象存储),避免产生 500MB 级的孤儿文件
        // 同时清理 HLS 分片文件
        // 只捞 video_url 非空的记录;同时把 cover url 也拿出来统一删,和封面一起处理
        List<DramaEpisode> episodes = dramaEpisodeService.lambdaQuery()
                .select(DramaEpisode::getId, DramaEpisode::getCoverObjectKey, DramaEpisode::getCoverUrl,
                        DramaEpisode::getVideoUrl, DramaEpisode::getStorageProvider)
                .eq(DramaEpisode::getDramaId, id)
                .ge(DramaEpisode::getStatus, 0)
                .list();
        int epVideoDeleted = 0;
        int epHlsDeleted = 0;
        for (DramaEpisode ep : episodes) {
            String videoObjectKey = storageService.objectKeyFromPublicUrl(ep.getVideoUrl());
            if (videoObjectKey != null) {
                try {
                    storageService.deleteObject(videoObjectKey);
                    epVideoDeleted++;
                } catch (Exception ex) {
                    log.warn("Failed to delete drama episode video (dramaId={}, storageProvider={}, objectKey={}), skip",
                            id, ep.getStorageProvider(), videoObjectKey, ex);
                }
            }
            // 分集封面:与主流程剧封面保持一致,统一安静删除
            deleteR2ObjectQuietly(preferObjectKey(ep.getCoverObjectKey(), ep.getCoverUrl()),
                    "episode cover drama-cascade delete", id);

            // 级联删除 HLS 分片 (非 Cloudflare 模式)
            if (ep.getStorageProvider() == null
                    || !"cloudflare".equalsIgnoreCase(ep.getStorageProvider())) {
                try {
                    videoTranscodeService.deleteHlsFiles(ep.getId());
                    epHlsDeleted++;
                } catch (Exception ex) {
                    log.warn("Failed to delete HLS files for episodeId={} (dramaId={}), skip", ep.getId(), id, ex);
                }
            }
        }
        if (epVideoDeleted > 0 || epHlsDeleted > 0) {
            log.info("Delete dramaId={}: cascaded video removed={}, HLS removed={}", id, epVideoDeleted, epHlsDeleted);
        }

        // 级联删除该剧的 3 个 R2 封面对象 (cover/horizontal/vertical),避免孤儿对象
        // 软删 DB 之前查询,因为软删后查询条件若带 status 过滤会捞不到
        Drama drama = dramaEntityService.lambdaQuery()
                .select(Drama::getCoverObjectKey, Drama::getHorizontalCoverObjectKey, Drama::getVerticalCoverObjectKey,
                        Drama::getCoverUrl, Drama::getHorizontalCoverUrl, Drama::getVerticalCoverUrl)
                .eq(Drama::getId, id)
                .one();
        if (drama != null) {
            deleteR2ObjectQuietly(preferObjectKey(drama.getCoverObjectKey(), drama.getCoverUrl()),
                    "drama cover", id);
            deleteR2ObjectQuietly(preferObjectKey(drama.getHorizontalCoverObjectKey(), drama.getHorizontalCoverUrl()),
                    "drama horizontal cover", id);
            deleteR2ObjectQuietly(preferObjectKey(drama.getVerticalCoverObjectKey(), drama.getVerticalCoverUrl()),
                    "drama vertical cover", id);
        }

        // 软删所有 episode
        // 注意:这里使用 MyBatis-Plus 普通 UpdateWrapper(非 LambdaUpdateWrapper) + 列名字符串 "status"。
        // 1) LambdaUpdateWrapper.set() 的 API 签名仅接受 SFunction(方法引用),不接受 String 列名,
        //    强写 .set("status", -1) 会报 JDT 67108979(类型不匹配),服务器 mvn 编译也会同样失败;
        // 2) 而 DramaEpisode.status 字段未单独标注 @TableField,部分 IDE 对 lombok @Data 生成的
        //    DramaEpisode::setStatus 方法引用也会偶发报 "does not define setStatus" 假阳性(603979983);
        // 所以这里干脆走普通 UpdateWrapper:用实体字段/DB 列都兼容的字符串 "status" + "drama_id"
        // (对应 DramaEpisode.dramaId 的 @TableField("drama_id") 实际列名),两种报错路径都彻底规避,
        // 肉眼看 SQL 列 = 数据库列 一致,0 歧义。
        UpdateWrapper<DramaEpisode> epWrapper = Wrappers.update();
        epWrapper.set("status", -1);
        epWrapper.eq("drama_id", id);
        dramaEpisodeService.update(epWrapper);
        // 软删 drama 本身
        dramaEntityService.lambdaUpdate()
                .set(Drama::getStatus, -1)
                .eq(Drama::getId, id)
                .update();
    }

    // --- Admin: episodes ---

    public List<Map<String, Object>> getAdminEpisodes(Long dramaId, String keyword, String accessType, Integer status) {
        return dramaEpisodeService.adminEpisodes(dramaId, filterValue(keyword), accessTypeValue(accessType), status);
    }

    private void triggerHlsTranscode(Map<String, Object> row) {
        try {
            Long episodeId = MapUtil.lng(row, "id");
            String videoUrl = MapUtil.str(row, "videoUrl");
            String storageProvider = MapUtil.str(row, "storageProvider");
            String cloudflareUid = MapUtil.str(row, "cloudflareUid");

            if (episodeId == null || videoUrl == null || videoUrl.isBlank()) {
                return;
            }
            // Cloudflare Stream 模式: 视频不在 R2/本地, 无法用 FFmpeg 转码, 直接跳过
            // (Cloudflare Stream 本身就有 HLS 播放能力, 不需要我们转码)
            if ("cloudflare".equalsIgnoreCase(storageProvider)
                    || (cloudflareUid != null && !cloudflareUid.isBlank())) {
                log.debug("HLS转码跳过: Cloudflare Stream 模式, episodeId={}", episodeId);
                return;
            }
            String objectKey = storageService.objectKeyFromPublicUrl(videoUrl);
            if (objectKey == null) {
                log.warn("HLS转码跳过: 无法从videoUrl还原objectKey, episodeId={}, videoUrl={}", episodeId, videoUrl);
                return;
            }
            videoTranscodeService.transcodeToHls(episodeId, videoUrl, objectKey);
        } catch (Exception e) {
            log.warn("触发HLS转码失败(不影响保存): {}", e.getMessage());
        }
    }

    public Map<String, Object> createEpisode(Map<String, Object> row, Long dramaId) {
        dramaEpisodeService.insertEpisode(row);
        dramaEntityService.syncDramaEpisodeTotal(dramaId);
        triggerHlsTranscode(row);
        return row;
    }

    @Transactional
    public Map<String, Object> batchCreateEpisodes(BatchEpisodeRequest request) {
        Long dramaId = request.dramaId();
        int startNo = request.startEpisodeNo() == null ? 1 : request.startEpisodeNo();
        int successCount = 0;
        List<Map<String, Object>> created = new ArrayList<>();
        int total = request.episodes() == null ? 0 : request.episodes().size();
        int idx = 0;
        for (BatchEpisodeRequest.EpisodePayload ep : request.episodes()) {
            int episodeNo = ep.episodeNo() != null ? ep.episodeNo() : startNo + idx;
            String title = ep.title() != null && !ep.title().isBlank()
                    ? ep.title()
                    : "第" + episodeNo + "集";
            Map<String, Object> row = MapUtil.map(
                    "dramaId", dramaId,
                    "episodeNo", episodeNo,
                    "title", title,
                    "description", ep.description(),
                    "coverUrl", ep.coverUrl(),
                    "videoUrl", ep.videoUrl(),
                    "pricePoints", ep.pricePoints() == null ? 10 : ep.pricePoints(),
                    "durationSeconds", ep.durationSeconds() == null ? 0 : ep.durationSeconds(),
                    "isFree", "FREE".equalsIgnoreCase(ep.accessType()) ? 1 : 0,
                    "accessType", ep.accessType() == null ? "POINTS" : ep.accessType().toUpperCase(),
                    "sortOrder", ep.sortOrder() == null ? episodeNo : ep.sortOrder(),
                    "storageProvider", ep.storageProvider() == null ? "oss" : ep.storageProvider(),
                    "status", ep.status() == null ? 1 : ep.status()
            );
            dramaEpisodeService.insertEpisode(row);
            triggerHlsTranscode(row);
            created.add(row);
            successCount++;
            idx++;
        }
        dramaEntityService.syncDramaEpisodeTotal(dramaId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("success", successCount);
        result.put("created", created);
        return result;
    }

    public void updateEpisode(Map<String, Object> row, Long dramaId) {
        Long episodeId = MapUtil.lng(row, "id");
        String newVideoUrl = MapUtil.str(row, "videoUrl");
        String newStorageProvider = MapUtil.str(row, "storageProvider");

        // 先查出旧数据,判断视频URL是否变化
        String oldVideoUrl = null;
        if (episodeId != null && newVideoUrl != null) {
            DramaEpisode old = dramaEpisodeService.lambdaQuery()
                    .select(DramaEpisode::getId, DramaEpisode::getVideoUrl)
                    .eq(DramaEpisode::getId, episodeId)
                    .one();
            if (old != null) {
                oldVideoUrl = old.getVideoUrl();
            }
        }

        dramaEpisodeService.updateEpisode(row);
        dramaEntityService.syncDramaEpisodeTotal(dramaId);

        // 如果视频URL变了,触发重新转码(旧HLS分片会被新转码覆盖)
        if (episodeId != null && newVideoUrl != null && !newVideoUrl.isBlank()
                && !newVideoUrl.equals(oldVideoUrl)) {
            // 确保 row 里有 storageProvider 字段供 triggerHlsTranscode 判断
            if (newStorageProvider != null && !newStorageProvider.isBlank()
                    && row.get("storageProvider") == null) {
                row.put("storageProvider", newStorageProvider);
            }
            triggerHlsTranscode(row);
        }
    }

    public Map<String, Object> getTranscodeStatus(Long episodeId) {
        DramaEpisode ep = dramaEpisodeService.lambdaQuery()
                .select(DramaEpisode::getId, DramaEpisode::getHlsUrl,
                        DramaEpisode::getTranscodeStatus, DramaEpisode::getVideoUrl)
                .eq(DramaEpisode::getId, episodeId)
                .one();
        if (ep == null) {
            return MapUtil.map("status", -1, "message", "剧集不存在");
        }
        Integer ts = ep.getTranscodeStatus();
        if (ts == null) ts = 0;
        String statusText = switch (ts) {
            case 0 -> "未转码";
            case 1 -> "转码中";
            case 2 -> "转码完成";
            case -1 -> "转码失败";
            default -> "未知";
        };
        Map<String, Object> result = MapUtil.map(
                "status", ts,
                "statusText", statusText,
                "hlsUrl", ep.getHlsUrl(),
                "videoUrl", ep.getVideoUrl()
        );
        return result;
    }

    public Map<String, Object> retranscodeEpisode(Long episodeId) {
        DramaEpisode ep = dramaEpisodeService.lambdaQuery()
                .select(DramaEpisode::getId, DramaEpisode::getVideoUrl)
                .eq(DramaEpisode::getId, episodeId)
                .ge(DramaEpisode::getStatus, 0)
                .one();
        if (ep == null) {
            return MapUtil.map("success", false, "message", "剧集不存在");
        }
        String videoUrl = ep.getVideoUrl();
        if (videoUrl == null || videoUrl.isBlank()) {
            return MapUtil.map("success", false, "message", "视频URL为空");
        }
        String objectKey = storageService.objectKeyFromPublicUrl(videoUrl);
        if (objectKey == null) {
            return MapUtil.map("success", false, "message", "无法还原objectKey");
        }
        videoTranscodeService.transcodeToHls(episodeId, videoUrl, objectKey);
        return MapUtil.map("success", true, "message", "已触发重新转码");
    }

    public void deleteEpisode(Long id) {
        DramaEpisode ep = dramaEpisodeService.lambdaQuery()
                .select(DramaEpisode::getDramaId, DramaEpisode::getCloudflareUid, DramaEpisode::getCoverObjectKey,
                        DramaEpisode::getCoverUrl, DramaEpisode::getVideoUrl, DramaEpisode::getStorageProvider)
                .eq(DramaEpisode::getId, id)
                .ge(DramaEpisode::getStatus, 0)
                .one();
        if (ep == null) {
            return;
        }
        Long dramaId = ep.getDramaId();
        String cloudflareUid = ep.getCloudflareUid();

        // 级联删除 Cloudflare Stream 视频 (避免 DB 删了但 Cloudflare 侧视频持续计费)
        // 失败不阻断 DB 软删:Cloudflare API 偶发抖动不应让管理员无法删 episode
        // 残留视频可后续通过运营脚本或 Cloudflare Dashboard 手动清理
        if (cloudflareUid != null && !cloudflareUid.isBlank() && cloudflareStreamService.isEnabled()) {
            try {
                cloudflareStreamService.deleteVideo(cloudflareUid);
            } catch (Exception e) {
                log.warn("Failed to delete Cloudflare video uid={} when deleting episodeId={}, DB soft-delete continues. Manual cleanup required.",
                        cloudflareUid, id, e);
            }
        }

        // 级联删除分集封面 (优先 coverObjectKey,回退 coverUrl→objectKey),避免孤儿对象
        deleteR2ObjectQuietly(preferObjectKey(ep.getCoverObjectKey(), ep.getCoverUrl()), "episode cover", id);

        // 级联删除分集视频 (R2 / 本地对象存储 / 外部 OSS 都走这里)
        // - 新上传:videoUrl = cdn.marastel.com/r2/... 或 dash.marastel.com/uploads/...,用 objectKeyFromPublicUrl 还原
        // - 历史 Stream:videoUrl 可能为空或为 videodelivery.net(还原返回 null,不删,交给上面 CloudflareStream.deleteVideo)
        String videoObjectKey = storageService.objectKeyFromPublicUrl(ep.getVideoUrl());
        deleteR2ObjectQuietly(videoObjectKey,
                "episode video storageProvider=" + ep.getStorageProvider(), id);

        // 级联删除 HLS 分片文件 (hls/{episodeId}/ 目录下所有 .ts/.m3u8)
        // 仅当 storageProvider 不是 cloudflare 时才删 (Cloudflare Stream 有自己的 HLS,不由我们管理)
        if (ep.getStorageProvider() == null
                || !"cloudflare".equalsIgnoreCase(ep.getStorageProvider())) {
            videoTranscodeService.deleteHlsFiles(id);
        }

        // 软删 episode 行:status=-1 + episode_no 改为负值(避免 uk_episode_no 唯一约束冲突)
        // 软删后 episode_no 设为 -(id)，因为 id 是自增主键，保证全局唯一，不会再和新建的集数冲突
        UpdateWrapper<DramaEpisode> oneEpWrapper = Wrappers.update();
        oneEpWrapper.set("status", -1);
        oneEpWrapper.setSql("episode_no = -id");
        oneEpWrapper.eq("id", id);
        dramaEpisodeService.update(oneEpWrapper);
        if (dramaId != null) {
            dramaEntityService.syncDramaEpisodeTotal(dramaId);
        }
    }

    public int applyFreePreview(Long dramaId, Integer freeCount, Integer pricePoints) {
        int fc = Math.max(freeCount == null ? 0 : freeCount, 0);
        int pp = Math.max(pricePoints == null ? 10 : pricePoints, 0);
        return dramaEpisodeService.applyFreePreview(dramaId, fc, pp);
    }

    /**
     * 修复所有时长为0的剧集：从视频文件中重新提取时长。
     */
    public int fixEpisodeDurations(StorageService storageService) {
        List<DramaEpisode> episodes = dramaEpisodeService.lambdaQuery()
                .eq(DramaEpisode::getDurationSeconds, 0)
                .ge(DramaEpisode::getStatus, 0)
                .list();
        int fixed = 0;
        for (DramaEpisode ep : episodes) {
            String videoUrl = ep.getVideoUrl();
            int duration = storageService.probeDurationFromUrl(videoUrl);
            if (duration > 0) {
                // 同 deleteEpisode/deleteDrama:
                // 1) LambdaUpdateWrapper.set() API 签名仅接受 SFunction(方法引用),不接受 String,
                //    用之前的 lambdaUpdate + set("duration_seconds", duration) 会 JDT 67108979,
                //    javac/mvn 也同样编译不过;
                // 2) 换用普通 UpdateWrapper + 列名字符串:duration_seconds 是实体
                //    @TableField("duration_seconds") 标注的真实 DB 列名,100% 对应。
                UpdateWrapper<DramaEpisode> durWrapper = Wrappers.update();
                durWrapper.set("duration_seconds", duration);
                durWrapper.eq("id", ep.getId());
                dramaEpisodeService.update(durWrapper);
                fixed++;
            }
        }
        return fixed;
    }

    // --- Private helpers ---

    /**
     * 优先使用数据库显式保存的 objectKey;如果为空再从公开 URL 还原 objectKey。
     *
     * <p>历史数据只存了 coverUrl/videoUrl(没存 *_object_key),这时就需要 URL → objectKey
     * 的还原;新数据建议同时落 objectKey,避免 URL 规则变动导致删错对象。两者都拿不到
     * 则返回 null,调用方安静跳过删除,不阻断主流程。</p>
     */
    private String preferObjectKey(String objectKey, String publicUrl) {
        if (objectKey != null && !objectKey.isBlank()) {
            return objectKey.trim();
        }
        return storageService.objectKeyFromPublicUrl(publicUrl);
    }

    /**
     * 静默删除 R2 对象:objectKey 为空直接返回;删除失败仅 warn 不抛异常,
     * 不阻断调用方主流程 (如删剧/删分集的 DB 软删)。
     */
    private void deleteR2ObjectQuietly(String objectKey, String desc, Long entityId) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            storageService.deleteObject(objectKey);
        } catch (Exception e) {
            log.warn("Failed to delete R2 object: desc={}, entityId={}, key={}, skip",
                    desc, entityId, objectKey, e);
        }
    }

    private String normalizedValue(String value) {
        if (value == null || value.isBlank() || "all".equalsIgnoreCase(value)) {
            return null;
        }
        return value.trim();
    }

    private String keywordValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String contentTypeValue(String value) {
        String normalized = normalizedValue(value);
        if (normalized == null) {
            return null;
        }
        String lower = normalized.toLowerCase();
        if ("ai".equals(lower) || "aiDrama".equals(normalized) || "comic".equals(lower) || "comics".equals(lower)) {
            return "ai";
        }
        if ("real".equals(lower) || "realDrama".equals(normalized) || "short".equals(lower) || "shorts".equals(lower) || "drama".equals(lower)) {
            return "real";
        }
        return normalized;
    }

    private Integer timeDays(String value) {
        String normalized = normalizedValue(value);
        return switch (normalized == null ? "" : normalized) {
            case "d7" -> 7;
            case "d14" -> 14;
            case "d30" -> 30;
            case "d90" -> 90;
            default -> null;
        };
    }

    private String sortValue(String value) {
        String normalized = normalizedValue(value);
        if ("newest".equals(normalized) || "hottest".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private static String filterValue(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String accessTypeValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if (Set.of("FREE", "POINTS").contains(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("unsupported accessType");
    }

    public static String normalizedKey(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z][A-Za-z0-9_]{0,31}")) {
            throw new IllegalArgumentException("invalid category filter key");
        }
        return normalized;
    }

    public static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public static String normalizeContentType(String value) {
        if (value == null || value.isBlank()) {
            return "real";
        }
        String trimmed = value.trim();
        String normalized = trimmed.toLowerCase();
        if ("ai".equals(normalized) || "comic".equals(normalized) || "comics".equals(normalized)) {
            return "ai";
        }
        if ("real".equals(normalized) || "short".equals(normalized) || "shorts".equals(normalized) || "drama".equals(normalized)) {
            return "real";
        }
        return trimmed;
    }

    public static String normalizeAccessType(String accessType, Boolean isFree) {
        if (Boolean.TRUE.equals(isFree)) {
            return "FREE";
        }
        String normalized = accessTypeValue(accessType);
        return normalized == null ? "POINTS" : normalized;
    }

    /**
     * 将秒数格式化为带单位的可读字符串。
     * <p>示例: 45 → "45秒", 90 → "1分30秒", 3665 → "1时1分5秒"</p>
     */
    public static String formatDurationSeconds(Integer seconds) {
        if (seconds == null || seconds <= 0) {
            return "0秒";
        }
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) {
            sb.append(h).append("时");
        }
        if (m > 0 || h > 0) {
            sb.append(m).append("分");
        }
        sb.append(s).append("秒");
        return sb.toString();
    }
}
