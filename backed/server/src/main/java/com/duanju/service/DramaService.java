package com.duanju.service;

import com.duanju.dto.content.BatchEpisodeRequest;
import com.duanju.entity.Drama;
import com.duanju.entity.DramaCategoryFilter;
import com.duanju.entity.DramaEpisode;
import com.duanju.entity.UserFavorite;
import com.duanju.security.PrincipalHolder;
import com.duanju.service.entity.DramaCategoryFilterService;
import com.duanju.service.entity.DramaEntityService;
import com.duanju.service.entity.DramaEpisodeService;
import com.duanju.service.entity.UserEpisodeUnlockService;
import com.duanju.service.entity.UserFavoriteService;
import com.duanju.util.MapUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DramaService {

    private final DramaEntityService dramaEntityService;
    private final DramaEpisodeService dramaEpisodeService;
    private final DramaCategoryFilterService dramaCategoryFilterService;
    private final UserFavoriteService userFavoriteService;
    private final UserEpisodeUnlockService userEpisodeUnlockService;
    private final CloudflareStreamService cloudflareStreamService;
    private final ContentTranslationService contentTranslationService;

    public DramaService(DramaEntityService dramaEntityService,
                        DramaEpisodeService dramaEpisodeService,
                        DramaCategoryFilterService dramaCategoryFilterService,
                        UserFavoriteService userFavoriteService,
                        UserEpisodeUnlockService userEpisodeUnlockService,
                        CloudflareStreamService cloudflareStreamService,
                        ContentTranslationService contentTranslationService) {
        this.dramaEntityService = dramaEntityService;
        this.dramaEpisodeService = dramaEpisodeService;
        this.dramaCategoryFilterService = dramaCategoryFilterService;
        this.userFavoriteService = userFavoriteService;
        this.userEpisodeUnlockService = userEpisodeUnlockService;
        this.cloudflareStreamService = cloudflareStreamService;
        this.contentTranslationService = contentTranslationService;
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
            }
        }
        drama.put("episodes", episodes);
        drama.put("favorite", userId != null && userFavoriteService.lambdaQuery()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getDramaId, id)
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
        dramaEpisodeService.lambdaUpdate()
                .set(DramaEpisode::getStatus, -1)
                .eq(DramaEpisode::getDramaId, id)
                .update();
        dramaEntityService.lambdaUpdate()
                .set(Drama::getStatus, -1)
                .eq(Drama::getId, id)
                .update();
    }

    // --- Admin: episodes ---

    public List<Map<String, Object>> getAdminEpisodes(Long dramaId, String keyword, String accessType, Integer status) {
        return dramaEpisodeService.adminEpisodes(dramaId, filterValue(keyword), accessTypeValue(accessType), status);
    }

    public Map<String, Object> createEpisode(Map<String, Object> row, Long dramaId) {
        dramaEpisodeService.insertEpisode(row);
        dramaEntityService.syncDramaEpisodeTotal(dramaId);
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
        dramaEpisodeService.updateEpisode(row);
        dramaEntityService.syncDramaEpisodeTotal(dramaId);
    }

    public void deleteEpisode(Long id) {
        DramaEpisode ep = dramaEpisodeService.lambdaQuery()
                .select(DramaEpisode::getDramaId)
                .eq(DramaEpisode::getId, id)
                .ge(DramaEpisode::getStatus, 0)
                .one();
        Long dramaId = ep == null ? null : ep.getDramaId();
        dramaEpisodeService.lambdaUpdate()
                .set(DramaEpisode::getStatus, -1)
                .eq(DramaEpisode::getId, id)
                .update();
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
                dramaEpisodeService.lambdaUpdate()
                        .set(DramaEpisode::getDurationSeconds, duration)
                        .eq(DramaEpisode::getId, ep.getId())
                        .update();
                fixed++;
            }
        }
        return fixed;
    }

    // --- Private helpers ---

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
