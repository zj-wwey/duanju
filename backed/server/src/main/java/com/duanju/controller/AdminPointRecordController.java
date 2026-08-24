package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.entity.PointRecord;
import com.duanju.security.RequiresPermission;
import com.duanju.service.entity.PointRecordService;
import com.duanju.util.MapUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiresPermission("point:manage")
@RequestMapping("/api/admin/point-records")
public class AdminPointRecordController {

    private final PointRecordService pointRecordService;

    public AdminPointRecordController(PointRecordService pointRecordService) {
        this.pointRecordService = pointRecordService;
    }

    /** 积分记录列表 */
    @GetMapping
    public R<List<Map<String, Object>>> list(@RequestParam(required = false) Long userId,
                                              @RequestParam(required = false) String bizType,
                                              @RequestParam(required = false) String startDate,
                                              @RequestParam(required = false) String endDate,
                                              @RequestParam(defaultValue = "100") int limit) {
        var query = pointRecordService.lambdaQuery();
        if (userId != null) {
            query.eq(PointRecord::getUserId, userId);
        }
        if (bizType != null && !bizType.isBlank()) {
            query.eq(PointRecord::getBizType, bizType);
        }
        if (startDate != null && !startDate.isBlank()) {
            query.ge(PointRecord::getCreatedAt, startDate + " 00:00:00");
        }
        if (endDate != null && !endDate.isBlank()) {
            query.le(PointRecord::getCreatedAt, endDate + " 23:59:59");
        }
        query.orderByDesc(PointRecord::getCreatedAt).last("LIMIT " + limit);
        return R.ok(MapUtil.beansToMaps(query.list()));
    }

    /** 积分统计（按 bizType 汇总） */
    @GetMapping("/stats")
    public R<Map<String, Object>> stats(@RequestParam(required = false) String startDate,
                                         @RequestParam(required = false) String endDate) {
        var query = pointRecordService.lambdaQuery();
        if (startDate != null && !startDate.isBlank()) {
            query.ge(PointRecord::getCreatedAt, startDate + " 00:00:00");
        }
        if (endDate != null && !endDate.isBlank()) {
            query.le(PointRecord::getCreatedAt, endDate + " 23:59:59");
        }
        List<PointRecord> records = query.list();
        long totalEarned = records.stream().filter(r -> r.getDelta() > 0).mapToLong(PointRecord::getDelta).sum();
        long totalSpent = records.stream().filter(r -> r.getDelta() < 0).mapToLong(r -> -r.getDelta()).sum();
        return R.ok(MapUtil.map(
                "total_earned", totalEarned,
                "total_spent", totalSpent,
                "total_records", records.size()
        ));
    }
}