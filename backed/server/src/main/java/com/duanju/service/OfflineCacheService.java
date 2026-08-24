package com.duanju.service;

import com.duanju.util.MapUtil;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 离线缓存管理服务 - 根据会员等级限制缓存数量。
 *
 * <pre>
 * NONE:    0 部
 * SILVER:  3 部
 * GOLD:    10 部
 * DIAMOND: 无限 (-1)
 * </pre>
 */
@Service
public class OfflineCacheService {

    private final MembershipService membershipService;

    public OfflineCacheService(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    /** 获取用户离线缓存配额 */
    public Map<String, Object> getCacheQuota(Long userId) {
        String level = membershipService.getCurrentLevel(userId);
        int maxCache = MembershipService.getMaxOfflineCache(level);

        return MapUtil.map(
                "level", level,
                "max_cache", maxCache,
                "unlimited", maxCache < 0
        );
    }

    /** 检查用户是否可以缓存更多 */
    public boolean canCache(Long userId, int currentCachedCount) {
        String level = membershipService.getCurrentLevel(userId);
        int maxCache = MembershipService.getMaxOfflineCache(level);
        return maxCache < 0 || currentCachedCount < maxCache;
    }
}