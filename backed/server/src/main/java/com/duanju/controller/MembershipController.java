package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.security.PrincipalHolder;
import com.duanju.service.MembershipService;
import com.duanju.service.VipService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/membership")
public class MembershipController {

    private final MembershipService membershipService;
    private final VipService vipService;

    public MembershipController(MembershipService membershipService, VipService vipService) {
        this.membershipService = membershipService;
        this.vipService = vipService;
    }

    /** 当前会员等级、权益、进度条 */
    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        return R.ok(membershipService.getMembershipInfo(PrincipalHolder.userId()));
    }

    /** 各等级权益对比表 */
    @GetMapping("/benefits")
    public R<Map<String, Object>> benefits() {
        return R.ok(Map.of(
                "NONE", buildBenefitMap("NONE"),
                "SILVER", buildBenefitMap("SILVER"),
                "GOLD", buildBenefitMap("GOLD"),
                "DIAMOND", buildBenefitMap("DIAMOND")
        ));
    }

    private Map<String, Object> buildBenefitMap(String level) {
        return Map.of(
                "daily_free", MembershipService.getDailyFreeEpisodes(level),
                "point_multiplier", MembershipService.getPointMultiplier(level),
                "episode_discount", MembershipService.getEpisodeDiscount(level),
                "drama_discount", MembershipService.getDramaDiscount(level),
                "ad_free", MembershipService.getAdFreeLevel(level),
                "offline_cache", MembershipService.getMaxOfflineCache(level),
                "premium_support", MembershipService.hasPremiumSupport(level),
                "birthday_gift", MembershipService.getBirthdayGiftPoints(level),
                "badge", MembershipService.getBadgeInfo(level)
        );
    }

    /** 积分兑换会员天数 */
    @PostMapping("/exchange")
    public R<Map<String, Object>> exchange(@RequestBody Map<String, Object> body) {
        String level = (String) body.get("level");
        int days = body.get("days") instanceof Integer ? (Integer) body.get("days") : 0;
        return R.ok(vipService.exchangeVipByPoints(PrincipalHolder.userId(), level, days));
    }

    /** 会员等级变更历史 */
    @GetMapping("/history")
    public R<?> history(@RequestParam(defaultValue = "50") int limit) {
        return R.ok(vipService.getMyVipRecords(limit));
    }
}