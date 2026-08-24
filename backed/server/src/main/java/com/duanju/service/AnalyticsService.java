package com.duanju.service;

import com.duanju.mapper.AnalyticsMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {

    private final AnalyticsMapper analyticsMapper;

    public AnalyticsService(AnalyticsMapper analyticsMapper) {
        this.analyticsMapper = analyticsMapper;
    }

    public Map<String, Object> overview() {
        List<Map<String, Object>> rows = analyticsMapper.overview();
        if (rows != null && !rows.isEmpty()) {
            return rows.get(0);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalUsers", 0);
        result.put("totalDramas", 0);
        result.put("totalEpisodes", 0);
        result.put("watchedDramas", 0);
        result.put("activeViewers", 0);
        result.put("playEvents", 0);
        result.put("unlockPoints", 0);
        result.put("unlockCount", 0);
        result.put("paidOrders", 0);
        result.put("paidAmountCents", 0);
        return result;
    }

    public List<Map<String, Object>> dramaRanking(Integer days, int limit) {
        return analyticsMapper.dramaRanking(safeDays(days), safeLimit(limit, 100));
    }

    public List<Map<String, Object>> episodeStats(Long dramaId, Integer days) {
        return analyticsMapper.episodeStats(dramaId, safeDays(days));
    }

    public List<Map<String, Object>> playTrend(int days) {
        Integer safe = safeDays(days);
        return analyticsMapper.playTrend(safe == null ? 30 : safe);
    }

    public List<Map<String, Object>> viewerRanking(int limit) {
        return analyticsMapper.viewerRanking(safeLimit(limit, 200));
    }

    public List<Map<String, Object>> episodeReach(Long dramaId) {
        return analyticsMapper.episodeReach(dramaId);
    }

    private Integer safeDays(Integer days) {
        if (days == null) {
            return null;
        }
        return Math.max(1, Math.min(days, 365));
    }

    private int safeLimit(int limit, int max) {
        return Math.max(1, Math.min(limit, max));
    }
}
