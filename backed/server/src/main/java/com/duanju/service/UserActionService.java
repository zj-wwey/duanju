package com.duanju.service;

import com.duanju.entity.Drama;
import com.duanju.entity.EpisodePlayEvent;
import com.duanju.entity.UserFavorite;
import com.duanju.service.entity.DramaEntityService;
import com.duanju.service.entity.EpisodePlayEventService;
import com.duanju.service.entity.UserFavoriteService;
import com.duanju.service.entity.UserWatchHistoryService;
import com.duanju.util.MapUtil;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class UserActionService {

    private final DramaEntityService dramaEntityService;
    private final UserFavoriteService userFavoriteService;
    private final UserWatchHistoryService userWatchHistoryService;
    private final EpisodePlayEventService episodePlayEventService;
    private final UnlockService unlockService;

    public UserActionService(DramaEntityService dramaEntityService,
                             UserFavoriteService userFavoriteService,
                             UserWatchHistoryService userWatchHistoryService,
                             EpisodePlayEventService episodePlayEventService,
                             UnlockService unlockService) {
        this.dramaEntityService = dramaEntityService;
        this.userFavoriteService = userFavoriteService;
        this.userWatchHistoryService = userWatchHistoryService;
        this.episodePlayEventService = episodePlayEventService;
        this.unlockService = unlockService;
    }

    public Map<String, Object> toggleFavorite(Long userId, Long dramaId) {
        Drama drama = dramaEntityService.getById(dramaId);
        if (drama == null || !Integer.valueOf(1).equals(drama.getStatus())) {
            throw new IllegalArgumentException("drama not found");
        }
        boolean exists = userFavoriteService.lambdaQuery()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getDramaId, dramaId)
                .exists();
        if (exists) {
            userFavoriteService.lambdaUpdate()
                    .eq(UserFavorite::getUserId, userId)
                    .eq(UserFavorite::getDramaId, dramaId)
                    .remove();
            return MapUtil.map("favorite", false);
        }
        userFavoriteService.insertIgnore(userId, dramaId);
        return MapUtil.map("favorite", true);
    }

    public List<Map<String, Object>> getFavorites(Long userId) {
        return userFavoriteService.favorites(userId);
    }

    public void saveHistory(Long userId, Long dramaId, Long episodeId, Integer progressSeconds) {
        Map<String, Object> episode = unlockService.requireAccessibleEpisode(userId, dramaId, episodeId);
        int safeProgress = Math.max(0, progressSeconds == null ? 0 : progressSeconds);
        Long episodePk = MapUtil.lng(episode, "id");
        userWatchHistoryService.saveHistory(userId, dramaId, episodePk, safeProgress);
        EpisodePlayEvent event = new EpisodePlayEvent();
        event.setUserId(userId);
        event.setDramaId(dramaId);
        event.setEpisodeId(episodePk);
        event.setProgressSeconds(safeProgress);
        event.setEventType("PROGRESS");
        episodePlayEventService.save(event);
    }

    public List<Map<String, Object>> getHistories(Long userId) {
        List<Map<String, Object>> histories = userWatchHistoryService.histories(userId);
        for (Map<String, Object> h : histories) {
            Integer progress = MapUtil.integer(h, "progress_seconds");
            h.put("progressText", DramaService.formatDurationSeconds(progress));
        }
        return histories;
    }

    public void deleteHistory(Long userId, Long dramaId) {
        if (userWatchHistoryService.deleteHistory(userId, dramaId) == 0) {
            throw new IllegalArgumentException("history not found");
        }
    }

    public Map<String, Object> clearHistories(Long userId) {
        int deleted = userWatchHistoryService.clearHistories(userId);
        return MapUtil.map("deleted", deleted);
    }
}