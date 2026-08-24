package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.security.RequiresPermission;
import com.duanju.service.MembershipService;
import com.duanju.service.PointService;
import com.duanju.service.VipService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiresPermission("user:manage")
@RequestMapping("/api/admin/membership")
public class AdminMembershipController {

    private final MembershipService membershipService;
    private final VipService vipService;
    private final PointService pointService;

    public AdminMembershipController(MembershipService membershipService,
                                     VipService vipService, PointService pointService) {
        this.membershipService = membershipService;
        this.vipService = vipService;
        this.pointService = pointService;
    }

    /** 会员用户列表 */
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(defaultValue = "100") int limit) {
        return R.ok(vipService.getAllVipRecords(keyword, status, limit));
    }

    /** 单用户会员详情 */
    @GetMapping("/{userId}")
    public R<Map<String, Object>> detail(@PathVariable Long userId) {
        return R.ok(membershipService.getMembershipInfo(userId));
    }

    /** 活动赠送积分 */
    @PostMapping("/points/grant")
    @RequiresPermission("point:manage")
    public R<Map<String, Object>> grantPoints(@RequestBody Map<String, Object> body) {
        long userId = body.get("userId") instanceof Integer ? ((Integer) body.get("userId")).longValue()
                : (Long) body.get("userId");
        int points = body.get("points") instanceof Integer ? (Integer) body.get("points") : 0;
        String activityId = (String) body.getOrDefault("activityId", "ADMIN_GRANT");
        int expireDays = body.get("expireDays") instanceof Integer ? (Integer) body.get("expireDays") : 90;
        return R.ok(pointService.grantPromotionPoints(userId, points, activityId, expireDays));
    }

    /** 手动调整用户会员等级 */
    @PostMapping("/{userId}/adjust")
    public R<Map<String, Object>> adjust(@PathVariable Long userId,
                                          @RequestBody Map<String, Object> body) {
        String level = (String) body.get("level");
        if (level == null || level.isBlank()) {
            throw new IllegalArgumentException("level is required");
        }
        return R.ok(membershipService.adjustMembership(userId, level));
    }

    /** 会员统计：总人数、各等级人数、各来源人数 */
    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        return R.ok(membershipService.getMembershipStats());
    }
}