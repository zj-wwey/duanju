package com.duanju.service;

import com.duanju.entity.AdRewardRecord;
import com.duanju.entity.PointRecord;
import com.duanju.entity.UserCheckin;
import com.duanju.service.entity.AdRewardRecordService;
import com.duanju.service.entity.PointRecordService;
import com.duanju.service.entity.UserCheckinService;
import com.duanju.util.MapUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PointService {
    private static final int CHECKIN_POINTS = 5;
    private static final int CHECKIN_STREAK_BONUS = 10;
    private static final int AD_REWARD_POINTS = 2;
    private static final int EPISODE_WATCH_POINTS = 1;
    private static final int SHARE_POINTS = 3;
    private static final int INVITE_POINTS = 50;
    private static final int WATCH_DURATION_POINTS_PER_10MIN = 1;

    private final UserService userService;
    private final UserCheckinService userCheckinService;
    private final PointRecordService pointRecordService;
    private final AdRewardRecordService adRewardRecordService;
    private final StringRedisTemplate redisTemplate;
    private final MembershipService membershipService;
    private final PointExpireService pointExpireService;

    @Value("${duanju.ad-reward.daily-limit:10}")
    private int dailyLimit;

    @Value("${duanju.ad-reward.cooldown-seconds:30}")
    private int cooldownSeconds;

    public PointService(UserService userService, UserCheckinService userCheckinService,
                        PointRecordService pointRecordService, AdRewardRecordService adRewardRecordService,
                        StringRedisTemplate redisTemplate, MembershipService membershipService,
                        PointExpireService pointExpireService) {
        this.userService = userService;
        this.userCheckinService = userCheckinService;
        this.pointRecordService = pointRecordService;
        this.adRewardRecordService = adRewardRecordService;
        this.redisTemplate = redisTemplate;
        this.membershipService = membershipService;
        this.pointExpireService = pointExpireService;
    }

    @Transactional
    public Map<String, Object> checkin(Long userId) {
        LocalDate today = LocalDate.now();
        boolean already = userCheckinService.lambdaQuery()
                .eq(UserCheckin::getUserId, userId)
                .eq(UserCheckin::getCheckinDate, today)
                .exists();
        if (already) {
            throw new IllegalArgumentException("already checked in today");
        }

        // 查昨日记录计算连续天数
        UserCheckin yesterday = userCheckinService.lambdaQuery()
                .eq(UserCheckin::getUserId, userId)
                .eq(UserCheckin::getCheckinDate, today.minusDays(1))
                .one();
        int consecutiveDays = yesterday != null ? yesterday.getConsecutiveDays() + 1 : 1;

        // 基础积分 + 连续7天奖励
        int basePoints = CHECKIN_POINTS;
        int bonusPoints = 0;
        if (consecutiveDays % 7 == 0) {
            bonusPoints = CHECKIN_STREAK_BONUS;
        }
        int totalPoints = basePoints + bonusPoints;

        UserCheckin checkin = new UserCheckin();
        checkin.setUserId(userId);
        checkin.setCheckinDate(today);
        checkin.setPointDelta(totalPoints);
        checkin.setConsecutiveDays(consecutiveDays);
        userCheckinService.save(checkin);

        // 统一走 addPoints，应用会员倍率
        Map<String, Object> pointResult = addPoints(userId, totalPoints, "CHECKIN", today.toString(), "daily checkin");

        return MapUtil.map(
                "user", pointResult.get("user"),
                "consecutive_days", consecutiveDays,
                "base_points", basePoints,
                "bonus_points", bonusPoints,
                "membership_multiplier", pointResult.get("multiplier"),
                "membership_bonus", pointResult.get("bonus_delta"),
                "total_earned", pointResult.get("actual_delta")
        );
    }

    public Map<String, Object> getPoints(Long userId) {
        List<PointRecord> records = pointRecordService.lambdaQuery()
                .eq(PointRecord::getUserId, userId)
                .orderByDesc(PointRecord::getId)
                .last("limit 100")
                .list();
        Map<String, Object> result = MapUtil.map("user", userService.getProfile(userId), "records", MapUtil.beansToMaps(records));
        // 最早的积分过期日期
        List<Map<String, Object>> batches = pointExpireService.getExpireBatches(userId);
        for (Map<String, Object> batch : batches) {
            if (batch.get("remaining") instanceof Number && ((Number) batch.get("remaining")).intValue() > 0) {
                result.put("pointsExpireAt", batch.get("expireAt"));
                break;
            }
        }
        return result;
    }

    @Transactional
    public Map<String, Object> adReward(Long userId, String adSlot, String traceId) {
        if (adSlot == null || adSlot.isBlank()) {
            throw new IllegalArgumentException("adSlot is required");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId is required");
        }

        String cooldownKey = "ad_reward:cooldown:" + userId;
        Boolean cooldownSet = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, "1", Duration.ofSeconds(cooldownSeconds));
        if (Boolean.FALSE.equals(cooldownSet)) {
            throw new IllegalArgumentException("Too frequent, please try again in " + cooldownSeconds + " seconds");
        }

        String dailyCountKey = "ad_reward:count:" + userId + ":" + LocalDate.now();
        String countStr = redisTemplate.opsForValue().get(dailyCountKey);
        int count = countStr == null ? 0 : Integer.parseInt(countStr);
        if (count >= dailyLimit) {
            redisTemplate.delete(cooldownKey);
            throw new IllegalArgumentException("Daily ad reward limit reached (" + dailyLimit + ")");
        }

        AdRewardRecord reward = new AdRewardRecord();
        reward.setUserId(userId);
        reward.setAdSlot(adSlot);
        reward.setTraceId(traceId);
        reward.setPointDelta(AD_REWARD_POINTS);
        try {
            adRewardRecordService.save(reward);
        } catch (DuplicateKeyException ex) {
            redisTemplate.delete(cooldownKey);
            return userService.getProfile(userId);
        }

        // 统一走 addPoints，应用会员倍率
        addPoints(userId, AD_REWARD_POINTS, "AD_REWARD", traceId, "ad reward");

        Long newCount = redisTemplate.opsForValue().increment(dailyCountKey);
        if (newCount != null && newCount == 1L) {
            redisTemplate.expire(dailyCountKey, Duration.ofDays(1));
        }

        return userService.getProfile(userId);
    }

    public List<Map<String, Object>> getAdRewards(Long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<AdRewardRecord> rewards = adRewardRecordService.lambdaQuery()
                .eq(AdRewardRecord::getUserId, userId)
                .orderByDesc(AdRewardRecord::getId)
                .last("limit " + safeLimit)
                .list();
        return MapUtil.beansToMaps(rewards);
    }

    @Transactional
    public Map<String, Object> adjustPoints(Long userId, int delta, String remark) {
        if (delta == 0) {
            throw new IllegalArgumentException("delta must not be zero");
        }
        if (delta < 0) {
            pointExpireService.consumePoints(userId, -delta);
        }
        if (!userService.changePoints(userId, delta)) {
            throw new IllegalArgumentException("insufficient points or user not found");
        }
        recordPoint(userId, delta, "ADMIN_ADJUST", String.valueOf(userId),
                remark == null || remark.isBlank() ? "admin adjustment" : remark);
        return userService.getProfile(userId);
    }

    /**
     * 统一积分增减入口，自动应用会员倍率 (R10)。
     */
    @Transactional
    public Map<String, Object> addPoints(Long userId, int delta, String bizType, String bizId, String remark) {
        int originalDelta = delta;
        int actualDelta = delta;
        double multiplier = 1.0;
        String level = "NONE";

        // 正向积分：应用会员倍率
        if (delta > 0 && shouldApplyMultiplier(bizType)) {
            level = membershipService.getCurrentLevel(userId);
            multiplier = membershipService.getPointMultiplier(level);
            actualDelta = (int) Math.ceil(delta * multiplier);
        }

        // 扣减积分：FIFO消耗
        if (actualDelta < 0) {
            pointExpireService.consumePoints(userId, -actualDelta);
        }

        // 更新余额
        if (!userService.changePoints(userId, actualDelta)) {
            throw new IllegalArgumentException("insufficient points or user not found");
        }

        // 正向积分：写入过期批次
        if (actualDelta > 0 && shouldApplyMultiplier(bizType)) {
            boolean isPurchased = "RECHARGE".equals(bizType) || "ADMIN_ORDER_PAY".equals(bizType);
            pointExpireService.createBatch(userId, actualDelta, isPurchased ? "PURCHASED" : "EARNED");
        }

        // 记录流水
        recordPoint(userId, actualDelta, bizType, bizId, remark);

        return MapUtil.map(
                "original_delta", originalDelta,
                "actual_delta", actualDelta,
                "multiplier", multiplier,
                "bonus_delta", actualDelta - originalDelta,
                "membership_level", level,
                "has_bonus", actualDelta > originalDelta,
                "user", userService.getProfile(userId)
        );
    }

    /** 不应用倍率的业务类型 */
    private boolean shouldApplyMultiplier(String bizType) {
        return !Set.of("ORDER_REFUND", "ADMIN_ADJUST", "ADMIN_ORDER_PAY", "PROMOTION", "BIRTHDAY").contains(bizType);
    }

    // --- 新增积分获取方法 ---

    @Transactional
    public Map<String, Object> rewardEpisodeWatch(Long userId, Long episodeId) {
        String key = "episode_watch:" + userId + ":" + LocalDate.now();
        String countStr = redisTemplate.opsForValue().get(key);
        int dailyCount = countStr == null ? 0 : Integer.parseInt(countStr);
        if (dailyCount >= 20) {
            throw new IllegalArgumentException("Daily watch reward limit reached");
        }
        String episodeKey = "episode_watched:" + userId + ":" + episodeId;
        Boolean set = redisTemplate.opsForValue().setIfAbsent(episodeKey, "1", Duration.ofDays(1));
        if (Boolean.FALSE.equals(set)) {
            return userService.getProfile(userId); // 本集已奖励过
        }
        addPoints(userId, EPISODE_WATCH_POINTS, "EPISODE_WATCH", String.valueOf(episodeId), "watch episode reward");
        Long newCount = redisTemplate.opsForValue().increment(key);
        if (newCount != null && newCount == 1L) {
            redisTemplate.expire(key, Duration.ofDays(1));
        }
        return userService.getProfile(userId);
    }

    @Transactional
    public Map<String, Object> rewardShare(Long userId, Long dramaId) {
        String cooldownKey = "share:cooldown:" + userId;
        Boolean set = redisTemplate.opsForValue().setIfAbsent(cooldownKey, "1", Duration.ofSeconds(60));
        if (Boolean.FALSE.equals(set)) {
            throw new IllegalArgumentException("Share too frequent, please try again later");
        }
        String dailyKey = "share:count:" + userId + ":" + LocalDate.now();
        String countStr = redisTemplate.opsForValue().get(dailyKey);
        int dailyCount = countStr == null ? 0 : Integer.parseInt(countStr);
        if (dailyCount >= 5) {
            throw new IllegalArgumentException("Daily share reward limit reached");
        }
        addPoints(userId, SHARE_POINTS, "SHARE", String.valueOf(dramaId), "share drama reward");
        Long newCount = redisTemplate.opsForValue().increment(dailyKey);
        if (newCount != null && newCount == 1L) {
            redisTemplate.expire(dailyKey, Duration.ofDays(1));
        }
        return userService.getProfile(userId);
    }

    @Transactional
    public Map<String, Object> rewardWatchDuration(Long userId, int minutes) {
        int points = minutes / 10;
        if (points <= 0) {
            return userService.getProfile(userId);
        }
        String dailyKey = "watch_duration:" + userId + ":" + LocalDate.now();
        String countStr = redisTemplate.opsForValue().get(dailyKey);
        int dailyPoints = countStr == null ? 0 : Integer.parseInt(countStr);
        if (dailyPoints >= 30) {
            return userService.getProfile(userId); // 已达上限，不抛异常
        }
        int actualPoints = Math.min(points, 30 - dailyPoints);
        addPoints(userId, actualPoints, "WATCH_DURATION", LocalDate.now().toString(), "watch duration reward");
        redisTemplate.opsForValue().increment(dailyKey, actualPoints);
        if (redisTemplate.getExpire(dailyKey) < 0) {
            redisTemplate.expire(dailyKey, Duration.ofDays(1));
        }
        return userService.getProfile(userId);
    }

    @Transactional
    public Map<String, Object> rewardComment(Long userId, Long commentId, int quality) {
        int points = Math.max(5, Math.min(quality, 20));
        String dailyKey = "comment:" + userId + ":" + LocalDate.now();
        String countStr = redisTemplate.opsForValue().get(dailyKey);
        int dailyCount = countStr == null ? 0 : Integer.parseInt(countStr);
        if (dailyCount >= 3) {
            throw new IllegalArgumentException("Daily comment reward limit reached");
        }
        addPoints(userId, points, "COMMENT", String.valueOf(commentId), "quality comment reward");
        Long newCount = redisTemplate.opsForValue().increment(dailyKey);
        if (newCount != null && newCount == 1L) {
            redisTemplate.expire(dailyKey, Duration.ofDays(1));
        }
        return userService.getProfile(userId);
    }

    @Transactional
    public Map<String, Object> sendBirthdayGift(Long userId) {
        String level = membershipService.getCurrentLevel(userId);
        int points = membershipService.getBirthdayGiftPoints(level);
        if (points <= 0) {
            return userService.getProfile(userId);
        }
        String key = "birthday_gift:" + userId + ":" + LocalDate.now().getYear();
        Boolean set = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofDays(366));
        if (Boolean.FALSE.equals(set)) {
            return userService.getProfile(userId); // 今年已发过
        }
        addPoints(userId, points, "BIRTHDAY", String.valueOf(LocalDate.now().getYear()), "birthday gift");
        return userService.getProfile(userId);
    }

    @Transactional
    public Map<String, Object> grantPromotionPoints(Long userId, int points, String activityId, int expireDays) {
        if (points <= 0) {
            throw new IllegalArgumentException("points must be positive");
        }
        if (!userService.changePoints(userId, points)) {
            throw new IllegalArgumentException("user not found");
        }
        pointExpireService.createBatch(userId, points, "PROMOTION", expireDays);
        recordPoint(userId, points, "PROMOTION", activityId, "promotion grant");
        return userService.getProfile(userId);
    }

    private void recordPoint(Long userId, int delta, String bizType, String bizId, String remark) {
        PointRecord record = new PointRecord();
        record.setUserId(userId);
        record.setDelta(delta);
        record.setBizType(bizType);
        record.setBizId(bizId);
        record.setRemark(remark);
        pointRecordService.save(record);
    }
}
