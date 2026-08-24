package com.duanju.service;

import com.duanju.util.MapUtil;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 广告展示控制服务 - 根据会员等级返回广告配置。
 */
@Service
public class AdService {

    private final MembershipService membershipService;

    public AdService(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    /**
     * 获取用户的广告配置。
     *
     * <pre>
     * NONE:    片头广告 ✅ | 插播广告 ✅ | 广告激励 ✅
     * SILVER:  片头广告 ✅ | 插播广告 ✅ | 广告激励 ✅
     * GOLD:    片头广告 ✅ | 插播广告 ❌ | 广告激励 ✅
     * DIAMOND: 片头广告 ❌ | 插播广告 ❌ | 广告激励 ✅
     * </pre>
     */
    public Map<String, Object> getAdConfig(Long userId) {
        String level = membershipService.getCurrentLevel(userId);
        return getAdConfigByLevel(level);
    }

    /** 按等级获取广告配置 */
    public static Map<String, Object> getAdConfigByLevel(String level) {
        boolean preRoll = true;
        boolean midRoll = true;
        boolean rewarded = true;

        if (MembershipService.LEVEL_DIAMOND.equals(level)) {
            preRoll = false;
            midRoll = false;
        } else if (MembershipService.LEVEL_GOLD.equals(level)) {
            midRoll = false;
        }

        return MapUtil.map(
                "pre_roll_ad", preRoll,
                "mid_roll_ad", midRoll,
                "rewarded_ad", rewarded,
                "level", level
        );
    }
}