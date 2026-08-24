package com.duanju.service;

import com.duanju.entity.AppUser;
import com.duanju.entity.UserMembership;
import com.duanju.service.entity.AppUserService;
import com.duanju.service.entity.UserMembershipService;
import com.duanju.util.MapUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class MembershipService {

    public static final String LEVEL_NONE = "NONE";
    public static final String LEVEL_SILVER = "SILVER";
    public static final String LEVEL_GOLD = "GOLD";
    public static final String LEVEL_DIAMOND = "DIAMOND";

    private static final int SILVER_THRESHOLD = 9900;
    private static final int GOLD_THRESHOLD = 29900;
    private static final int DIAMOND_THRESHOLD = 99900;

    private final UserMembershipService membershipService;
    private final AppUserService appUserService;

    public MembershipService(UserMembershipService membershipService,
                             AppUserService appUserService) {
        this.membershipService = membershipService;
        this.appUserService = appUserService;
    }

    // ==================== Level Query ====================

    public String getCurrentLevel(Long userId) {
        UserMembership membership = membershipService.lambdaQuery()
                .eq(UserMembership::getUserId, userId)
                .one();
        if (membership == null) {
            membership = createDefaultMembership(userId);
            return LEVEL_NONE;
        }
        String level = membership.getLevel();
        String purchaseLevel = membership.getPurchaseLevel();
        LocalDateTime purchaseExpireAt = membership.getPurchaseExpireAt();
        if (purchaseLevel != null && purchaseExpireAt != null
                && purchaseExpireAt.isAfter(LocalDateTime.now())) {
            return maxLevel(level, purchaseLevel);
        }
        return level != null ? level : LEVEL_NONE;
    }

    public UserMembership getMembership(Long userId) {
        UserMembership membership = membershipService.lambdaQuery()
                .eq(UserMembership::getUserId, userId)
                .one();
        if (membership == null) {
            membership = createDefaultMembership(userId);
        }
        return membership;
    }

    public Map<String, Object> getMembershipInfo(Long userId) {
        UserMembership membership = getMembership(userId);
        String currentLevel = getCurrentLevel(userId);
        AppUser user = appUserService.getById(userId);
        int totalSpentCents = user != null && user.getTotalSpentCents() != null
                ? user.getTotalSpentCents() : 0;

        String levelSource = membership.getLevelSource();

        // badge
        Map<String, Object> badge = getBadgeInfo(currentLevel);

        // benefits
        Map<String, Object> benefits = MapUtil.map(
                "daily_free", getDailyFreeEpisodes(currentLevel),
                "multiplier", getPointMultiplier(currentLevel),
                "episode_discount", getEpisodeDiscount(currentLevel),
                "drama_discount", getDramaDiscount(currentLevel),
                "ad_free", getAdFreeLevel(currentLevel),
                "offline_cache", getMaxOfflineCache(currentLevel),
                "premium_support", hasPremiumSupport(currentLevel),
                "birthday_gift", getBirthdayGiftPoints(currentLevel),
                "danmu_style", getDanmuStyle(currentLevel)
        );

        // spend_progress
        String spendLevel = getSpendLevel(totalSpentCents);
        int currentThreshold = 0;
        int nextThreshold = SILVER_THRESHOLD;
        String nextLevel = LEVEL_SILVER;
        if (LEVEL_DIAMOND.equals(spendLevel)) {
            currentThreshold = DIAMOND_THRESHOLD;
            nextThreshold = DIAMOND_THRESHOLD;
            nextLevel = null;
        } else if (LEVEL_GOLD.equals(spendLevel)) {
            currentThreshold = GOLD_THRESHOLD;
            nextThreshold = DIAMOND_THRESHOLD;
            nextLevel = LEVEL_DIAMOND;
        } else if (LEVEL_SILVER.equals(spendLevel)) {
            currentThreshold = SILVER_THRESHOLD;
            nextThreshold = GOLD_THRESHOLD;
            nextLevel = LEVEL_GOLD;
        }
        int remainingCents = Math.max(0, nextThreshold - totalSpentCents);
        int progressPercent = 0;
        if (nextThreshold > currentThreshold) {
            progressPercent = (int) ((long) (totalSpentCents - currentThreshold) * 100
                    / (nextThreshold - currentThreshold));
            progressPercent = Math.max(0, Math.min(100, progressPercent));
        }

        Map<String, Object> spendProgress = MapUtil.map(
                "total_spent_cents", totalSpentCents,
                "current_threshold", currentThreshold,
                "next_threshold", nextThreshold,
                "next_level", nextLevel,
                "remaining_cents", remainingCents,
                "progress_percent", progressPercent
        );

        // purchase
        String purchaseLevel = membership.getPurchaseLevel();
        LocalDateTime purchaseExpireAt = membership.getPurchaseExpireAt();
        long daysRemaining = 0;
        if (purchaseLevel != null && purchaseExpireAt != null
                && purchaseExpireAt.isAfter(LocalDateTime.now())) {
            daysRemaining = Duration.between(LocalDateTime.now(), purchaseExpireAt).toDays();
        }
        Map<String, Object> purchase = MapUtil.map(
                "level", purchaseLevel,
                "expire_at", purchaseExpireAt,
                "days_remaining", daysRemaining
        );

        // daily_free
        Integer dailyFreeUsed = membership.getDailyFreeUsed();
        if (dailyFreeUsed == null) {
            dailyFreeUsed = 0;
        }
        int dailyFreeTotal = getDailyFreeEpisodes(currentLevel);
        int dailyFreeRemaining = Math.max(0, dailyFreeTotal - dailyFreeUsed);
        LocalDate dailyFreeResetAt = membership.getDailyFreeResetAt();
        Map<String, Object> dailyFree = MapUtil.map(
                "total", dailyFreeTotal,
                "used", dailyFreeUsed,
                "remaining", dailyFreeRemaining,
                "reset_at", dailyFreeResetAt
        );

        return MapUtil.map(
                "current_level", currentLevel,
                "level_source", levelSource,
                "badge", badge,
                "benefits", benefits,
                "spend_progress", spendProgress,
                "purchase", purchase,
                "daily_free", dailyFree
        );
    }

    // ==================== Payment / VIP / Refund ====================

    @Transactional
    public void onPaymentSuccess(Long userId, int amountCents, String productCategory) {
        appUserService.lambdaUpdate()
                .setSql("total_spent_cents = COALESCE(total_spent_cents, 0) + " + amountCents)
                .eq(AppUser::getId, userId)
                .update();
        AppUser user = appUserService.getById(userId);
        int totalSpent = user != null && user.getTotalSpentCents() != null
                ? user.getTotalSpentCents() : 0;

        UserMembership membership = getMembership(userId);
        String currentLevel = membership.getLevel();
        String spendLevel = getSpendLevel(totalSpent);

        if (!spendLevel.equals(currentLevel)) {
            // Only upgrade if spend level is higher
            if (compareLevel(spendLevel, currentLevel) > 0) {
                membership.setLevel(spendLevel);
                membership.setLevelSource("SPEND");
                membership.setSpentUpgradeAt(LocalDateTime.now());
                membershipService.updateById(membership);
            }
        }
    }

    @Transactional
    public void onVipActivated(Long userId, String level, LocalDateTime expireAt) {
        UserMembership membership = getMembership(userId);
        membership.setPurchaseLevel(level);
        membership.setPurchaseExpireAt(expireAt);
        membershipService.updateById(membership);
        recalculateLevel(userId);
    }

    @Transactional
    public void onRefund(Long userId, String orderNo, int amountCents,
                         int pointsEarned, String productCategory) {
        // R1: consumption amount only increases, never downgrades spend level
        // Points deduction is handled by OrderService via pointService.addPoints()
        appUserService.lambdaUpdate()
                .setSql("total_spent_cents = GREATEST(0, COALESCE(total_spent_cents, 0) - " + amountCents + ")")
                .eq(AppUser::getId, userId)
                .update();
    }

    // ==================== Daily Free ====================

    @Transactional
    public boolean consumeDailyFreeEpisode(Long userId) {
        UserMembership membership = getMembership(userId);
        String level = getCurrentLevel(userId);
        int maxFree = getDailyFreeEpisodes(level);

        LocalDate today = LocalDate.now();
        LocalDate resetAt = membership.getDailyFreeResetAt();
        int used = membership.getDailyFreeUsed() != null ? membership.getDailyFreeUsed() : 0;

        if (resetAt == null || !resetAt.equals(today)) {
            // New day, reset
            membership.setDailyFreeUsed(1);
            membership.setDailyFreeResetAt(today);
            membershipService.updateById(membership);
            return true;
        }

        if (used < maxFree) {
            membership.setDailyFreeUsed(used + 1);
            membershipService.updateById(membership);
            return true;
        }

        return false;
    }

    // ==================== Expire Purchase ====================

    @Transactional
    public void expirePurchaseMemberships() {
        LocalDateTime now = LocalDateTime.now();
        List<UserMembership> expiredList = membershipService.lambdaQuery()
                .le(UserMembership::getPurchaseExpireAt, now)
                .isNotNull(UserMembership::getPurchaseLevel)
                .list();
        for (UserMembership m : expiredList) {
            m.setPurchaseLevel(null);
            m.setPurchaseExpireAt(null);
            membershipService.updateById(m);
            recalculateLevel(m.getUserId());
        }
    }

    @Transactional
    public void recalculateLevel(Long userId) {
        UserMembership membership = getMembership(userId);
        AppUser user = appUserService.getById(userId);
        int totalSpent = user != null && user.getTotalSpentCents() != null
                ? user.getTotalSpentCents() : 0;

        String spendLevel = getSpendLevel(totalSpent);
        String purchaseLevel = membership.getPurchaseLevel();
        LocalDateTime purchaseExpireAt = membership.getPurchaseExpireAt();

        String effectivePurchaseLevel = null;
        if (purchaseLevel != null && purchaseExpireAt != null
                && purchaseExpireAt.isAfter(LocalDateTime.now())) {
            effectivePurchaseLevel = purchaseLevel;
        }

        String newLevel = maxLevel(spendLevel, effectivePurchaseLevel);
        String newSource = "SPEND";
        if (effectivePurchaseLevel != null
                && compareLevel(effectivePurchaseLevel, spendLevel) >= 0) {
            newSource = "PURCHASE";
        }

        membership.setLevel(newLevel);
        membership.setLevelSource(newSource);
        membershipService.updateById(membership);
    }

    // ==================== Static Benefit Methods ====================

    public static int getDailyFreeEpisodes(String level) {
        if (level == null) {
            return 3;
        }
        return switch (level) {
            case LEVEL_SILVER -> 8;
            case LEVEL_GOLD -> 15;
            case LEVEL_DIAMOND -> 30;
            default -> 3;
        };
    }

    public static double getPointMultiplier(String level) {
        if (level == null) {
            return 1.0;
        }
        return switch (level) {
            case LEVEL_SILVER -> 1.2;
            case LEVEL_GOLD -> 1.5;
            case LEVEL_DIAMOND -> 2.0;
            default -> 1.0;
        };
    }

    public static double getEpisodeDiscount(String level) {
        if (level == null) {
            return 1.0;
        }
        return switch (level) {
            case LEVEL_SILVER -> 0.95;
            case LEVEL_GOLD -> 0.90;
            case LEVEL_DIAMOND -> 0.85;
            default -> 1.0;
        };
    }

    public static double getDramaDiscount(String level) {
        if (level == null) {
            return 1.0;
        }
        return switch (level) {
            case LEVEL_SILVER -> 0.9;
            case LEVEL_GOLD -> 0.8;
            case LEVEL_DIAMOND -> 0.7;
            default -> 1.0;
        };
    }

    public static String getAdFreeLevel(String level) {
        if (level == null) {
            return "NONE";
        }
        return switch (level) {
            case LEVEL_GOLD -> "PARTIAL";
            case LEVEL_DIAMOND -> "FULL";
            default -> "NONE";
        };
    }

    public static int getMaxOfflineCache(String level) {
        if (level == null) {
            return 0;
        }
        return switch (level) {
            case LEVEL_SILVER -> 3;
            case LEVEL_GOLD -> 10;
            case LEVEL_DIAMOND -> -1;
            default -> 0;
        };
    }

    public static Map<String, Object> getDanmuStyle(String level) {
        if (LEVEL_DIAMOND.equals(level)) {
            return MapUtil.map("color", "#FFD700", "glow", true);
        }
        return MapUtil.map("color", "#FFFFFF", "glow", false);
    }

    public static boolean hasPremiumSupport(String level) {
        return LEVEL_GOLD.equals(level) || LEVEL_DIAMOND.equals(level);
    }

    public static int getBirthdayGiftPoints(String level) {
        if (level == null) {
            return 0;
        }
        return switch (level) {
            case LEVEL_SILVER -> 50;
            case LEVEL_GOLD -> 100;
            case LEVEL_DIAMOND -> 200;
            default -> 0;
        };
    }

    public static Map<String, Object> getBadgeInfo(String level) {
        if (level == null) {
            return MapUtil.map("level", LEVEL_NONE, "name", "普通用户",
                    "icon", "", "color", "#999999");
        }
        return switch (level) {
            case LEVEL_SILVER -> MapUtil.map("level", LEVEL_SILVER, "name", "银卡会员",
                    "icon", "silver_badge", "color", "#C0C0C0");
            case LEVEL_GOLD -> MapUtil.map("level", LEVEL_GOLD, "name", "金卡会员",
                    "icon", "gold_badge", "color", "#FFD700");
            case LEVEL_DIAMOND -> MapUtil.map("level", LEVEL_DIAMOND, "name", "钻石会员",
                    "icon", "diamond_badge", "color", "#B9F2FF");
            default -> MapUtil.map("level", LEVEL_NONE, "name", "普通用户",
                    "icon", "", "color", "#999999");
        };
    }

    // ==================== Private Helpers ====================

    private UserMembership createDefaultMembership(Long userId) {
        UserMembership membership = new UserMembership();
        membership.setUserId(userId);
        membership.setLevel(LEVEL_NONE);
        membership.setLevelSource("NONE");
        membership.setDailyFreeUsed(0);
        membership.setDailyFreeResetAt(LocalDate.now());
        membership.setStatus(1);
        membershipService.save(membership);
        return membership;
    }

    private static String getSpendLevel(int totalSpentCents) {
        if (totalSpentCents >= DIAMOND_THRESHOLD) {
            return LEVEL_DIAMOND;
        }
        if (totalSpentCents >= GOLD_THRESHOLD) {
            return LEVEL_GOLD;
        }
        if (totalSpentCents >= SILVER_THRESHOLD) {
            return LEVEL_SILVER;
        }
        return LEVEL_NONE;
    }

    private static String maxLevel(String a, String b) {
        if (a == null) {
            return b != null ? b : LEVEL_NONE;
        }
        if (b == null) {
            return a;
        }
        return compareLevel(a, b) >= 0 ? a : b;
    }

    private static int compareLevel(String a, String b) {
        return Integer.compare(levelRank(a), levelRank(b));
    }

    private static int levelRank(String level) {
        if (level == null) {
            return 0;
        }
        return switch (level) {
            case LEVEL_DIAMOND -> 4;
            case LEVEL_GOLD -> 3;
            case LEVEL_SILVER -> 2;
            case LEVEL_NONE -> 1;
            default -> 0;
        };
    }

    // ==================== Admin Operations ====================

    /**
     * 管理员手动调整用户会员等级。
     * 调整到 NONE 仅清空购买等级，不降级消费等级。
     */
    @Transactional
    public Map<String, Object> adjustMembership(Long userId, String level) {
        if (level == null || !isValidLevel(level)) {
            throw new IllegalArgumentException("invalid level: " + level);
        }
        UserMembership membership = getMembership(userId);
        String oldLevel = getCurrentLevel(userId);

        if (LEVEL_NONE.equals(level)) {
            membership.setPurchaseLevel(null);
            membership.setPurchaseExpireAt(null);
            membershipService.updateById(membership);
            recalculateLevel(userId);
        } else {
            membership.setPurchaseLevel(level);
            membership.setPurchaseExpireAt(null);
            membershipService.updateById(membership);
            recalculateLevel(userId);
        }

        String newLevel = getCurrentLevel(userId);
        return MapUtil.map(
                "user_id", userId,
                "old_level", oldLevel,
                "new_level", newLevel,
                "adjusted_to", level
        );
    }

    /** 会员统计：付费会员总数、各等级人数、各来源人数 */
    public Map<String, Object> getMembershipStats() {
        int total = membershipService.lambdaQuery()
                .eq(UserMembership::getStatus, 1)
                .ne(UserMembership::getLevel, LEVEL_NONE)
                .count().intValue();

        Map<String, Object> levels = new java.util.HashMap<>();
        Map<String, Object> sources = new java.util.HashMap<>();

        for (String lv : List.of(LEVEL_NONE, LEVEL_SILVER, LEVEL_GOLD, LEVEL_DIAMOND)) {
            int count = membershipService.lambdaQuery()
                    .eq(UserMembership::getLevel, lv)
                    .eq(UserMembership::getStatus, 1)
                    .count().intValue();
            levels.put(lv, count);
        }

        for (String src : List.of("NONE", "SPEND", "PURCHASE")) {
            int count = membershipService.lambdaQuery()
                    .eq(UserMembership::getLevelSource, src)
                    .eq(UserMembership::getStatus, 1)
                    .count().intValue();
            sources.put(src, count);
        }

        return MapUtil.map(
                "total", total,
                "levels", levels,
                "sources", sources
        );
    }

    private static boolean isValidLevel(String level) {
        return LEVEL_NONE.equals(level)
                || LEVEL_SILVER.equals(level)
                || LEVEL_GOLD.equals(level)
                || LEVEL_DIAMOND.equals(level);
    }
}