package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.security.RequiresPermission;
import com.duanju.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiresPermission("analytics:view")
@RequestMapping("/api/admin/analytics")
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;

    public AdminAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(analyticsService.overview());
    }

    @GetMapping("/dramas")
    public R<List<Map<String, Object>>> dramas(@RequestParam(required = false) Integer days,
                                               @RequestParam(defaultValue = "20") int limit) {
        return R.ok(analyticsService.dramaRanking(days, limit));
    }

    @GetMapping("/episodes")
    public R<List<Map<String, Object>>> episodes(@RequestParam(required = false) Long dramaId,
                                                 @RequestParam(required = false) Integer days) {
        return R.ok(analyticsService.episodeStats(dramaId, days));
    }

    @GetMapping("/trend")
    public R<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "30") int days) {
        return R.ok(analyticsService.playTrend(days));
    }

    @GetMapping("/viewers")
    public R<List<Map<String, Object>>> viewers(@RequestParam(defaultValue = "50") int limit) {
        return R.ok(analyticsService.viewerRanking(limit));
    }

    @GetMapping("/episode-reach")
    public R<List<Map<String, Object>>> episodeReach(@RequestParam(required = false) Long dramaId) {
        return R.ok(analyticsService.episodeReach(dramaId));
    }
}
