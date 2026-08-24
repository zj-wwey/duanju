package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.entity.AutoRenewalSubscription;
import com.duanju.security.RequiresPermission;
import com.duanju.service.AutoRenewalService;
import com.duanju.service.entity.AutoRenewalSubscriptionService;
import com.duanju.util.MapUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiresPermission("order:manage")
@RequestMapping("/api/admin/auto-renewal")
public class AdminAutoRenewalController {

    private final AutoRenewalSubscriptionService subscriptionService;
    private final AutoRenewalService autoRenewalService;

    public AdminAutoRenewalController(AutoRenewalSubscriptionService subscriptionService,
                                       AutoRenewalService autoRenewalService) {
        this.subscriptionService = subscriptionService;
        this.autoRenewalService = autoRenewalService;
    }

    /** 自动续费订阅列表 */
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list(@RequestParam(required = false) String status,
                                              @RequestParam(defaultValue = "100") int limit) {
        var query = subscriptionService.lambdaQuery();
        if (status != null && !status.isBlank()) {
            query.eq(AutoRenewalSubscription::getStatus, status);
        }
        query.orderByDesc(AutoRenewalSubscription::getCreatedAt).last("LIMIT " + limit);
        return R.ok(MapUtil.beansToMaps(query.list()));
    }

    /** 管理员取消自动续费 */
    @PostMapping("/{userId}/cancel")
    public R<Void> cancel(@PathVariable Long userId) {
        autoRenewalService.cancel(userId);
        return R.ok();
    }
}