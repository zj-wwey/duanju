package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.security.RequiresPermission;
import com.duanju.service.AnalyticsService;
import com.duanju.service.DramaService;
import com.duanju.service.OrderService;
import com.duanju.service.StorageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiresPermission("dashboard:view")
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AnalyticsService analyticsService;
    private final DramaService dramaService;
    private final OrderService orderService;
    private final StorageService storageService;

    public AdminDashboardController(AnalyticsService analyticsService,
                                    DramaService dramaService,
                                    OrderService orderService,
                                    StorageService storageService) {
        this.analyticsService = analyticsService;
        this.dramaService = dramaService;
        this.orderService = orderService;
        this.storageService = storageService;
    }

    @GetMapping
    public R<Map<String, Object>> overview(@RequestParam(defaultValue = "7") int days) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kpis", analyticsService.overview());
        result.put("dramaRanking", analyticsService.dramaRanking(days, 10));
        result.put("playTrend", analyticsService.playTrend(days));
        result.put("recentOrders", orderService.getAdminOrders(null, null, 5));
        return R.ok(result);
    }

    /**
     * 修复所有时长为0的剧集：从视频文件中重新提取时长。
     */
    @PostMapping("/fix-durations")
    public R<Map<String, Object>> fixDurations() {
        int fixed = dramaService.fixEpisodeDurations(storageService);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fixed", fixed);
        return R.ok(result);
    }
}
