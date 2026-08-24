package com.duanju.service;

import com.duanju.entity.Drama;
import com.duanju.entity.DramaEpisode;
import com.duanju.entity.UserEpisodeUnlock;
import com.duanju.service.entity.DramaEntityService;
import com.duanju.service.entity.DramaEpisodeService;
import com.duanju.service.entity.PointRecordService;
import com.duanju.service.entity.UserEpisodeUnlockService;
import com.duanju.util.MapUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class UnlockService {

    private final DramaEntityService dramaEntityService;
    private final DramaEpisodeService dramaEpisodeService;
    private final UserEpisodeUnlockService userEpisodeUnlockService;
    private final UserService userService;
    private final PointRecordService pointRecordService;
    private final MembershipService membershipService;
    private final PointService pointService;

    public UnlockService(DramaEntityService dramaEntityService, DramaEpisodeService dramaEpisodeService,
                         UserEpisodeUnlockService userEpisodeUnlockService, UserService userService,
                         PointRecordService pointRecordService,
                         MembershipService membershipService, PointService pointService) {
        this.dramaEntityService = dramaEntityService;
        this.dramaEpisodeService = dramaEpisodeService;
        this.userEpisodeUnlockService = userEpisodeUnlockService;
        this.userService = userService;
        this.pointRecordService = pointRecordService;
        this.membershipService = membershipService;
        this.pointService = pointService;
    }

    /**
     * 校验分集是否可观看，返回分集信息。不可观看时抛出异常。
     * 供 UserActionService.saveHistory 和 DramaService.getDramaDetail 复用。
     */
    public Map<String, Object> requireAccessibleEpisode(Long userId, Long dramaId, Long episodeId) {
        Map<String, Object> episode = dramaEpisodeService.episode(episodeId);
        if (episode == null || !dramaId.equals(MapUtil.lng(episode, "drama_id"))) {
            throw new IllegalArgumentException("episode does not belong to drama");
        }
        if (isEpisodeFree(episode, dramaId)) {
            return episode;
        }
        if (userEpisodeUnlockService.unlocked(userId, dramaId, episodeId) > 0) {
            return episode;
        }
        if (membershipService.consumeDailyFreeEpisode(userId)) {
            return episode;
        }
        throw new IllegalArgumentException("episode is locked");
    }

    /**
     * 判断分集是否免费可看（is_free=1 或集数 <= 免费集数）。
     */
    public boolean isEpisodeFree(Map<String, Object> episode, Long dramaId) {
        int episodeNo = MapUtil.integer(episode, "episode_no");
        Drama drama = dramaEntityService.getById(dramaId);
        Integer freeCountValue = drama == null ? null : drama.getFreeEpisodeCount();
        int freeCount = freeCountValue == null ? 0 : freeCountValue;
        return Integer.valueOf(1).equals(MapUtil.integer(episode, "is_free")) || episodeNo <= freeCount;
    }

    @Transactional
    public void unlockEpisode(Long userId, Long episodeId) {
        Map<String, Object> episode = dramaEpisodeService.episode(episodeId);
        if (episode == null) {
            throw new IllegalArgumentException("episode not found");
        }
        Long dramaId = MapUtil.lng(episode, "drama_id");
        int episodeNo = MapUtil.integer(episode, "episode_no");
        Drama drama = dramaEntityService.getById(dramaId);
        int freeCount = drama == null || drama.getFreeEpisodeCount() == null ? 0 : drama.getFreeEpisodeCount();
        boolean episodeUnlocked = userEpisodeUnlockService.lambdaQuery()
                .eq(UserEpisodeUnlock::getUserId, userId)
                .eq(UserEpisodeUnlock::getEpisodeId, episodeId)
                .exists();
        boolean dramaUnlocked = userEpisodeUnlockService.lambdaQuery()
                .eq(UserEpisodeUnlock::getUserId, userId)
                .eq(UserEpisodeUnlock::getDramaId, dramaId)
                .eq(UserEpisodeUnlock::getUnlockType, "DRAMA")
                .exists();
        if (episodeNo <= freeCount || episodeUnlocked || dramaUnlocked) {
            return;
        }

        // 基础价格：优先取集级别价格，否则取剧级别默认价格
        int baseCost = MapUtil.integer(episode, "price_points");
        if (baseCost <= 0 && drama != null && drama.getEpisodePricePoints() != null) {
            baseCost = drama.getEpisodePricePoints();
        }

        // 会员折扣
        String level = membershipService.getCurrentLevel(userId);
        double discount = membershipService.getEpisodeDiscount(level);
        int cost = (int) Math.ceil(baseCost * discount);

        // 统一走 PointService 扣减 (R10)
        pointService.addPoints(userId, -cost, "UNLOCK_EPISODE", String.valueOf(episodeId), "unlock episode");

        saveUnlock(userId, dramaId, episodeId, "EPISODE", cost);
    }

    @Transactional
    public void unlockDrama(Long userId, Long dramaId) {
        Drama drama = dramaEntityService.getById(dramaId);
        if (drama == null || !Integer.valueOf(1).equals(drama.getStatus())) {
            throw new IllegalArgumentException("drama not found");
        }
        boolean dramaUnlocked = userEpisodeUnlockService.lambdaQuery()
                .eq(UserEpisodeUnlock::getUserId, userId)
                .eq(UserEpisodeUnlock::getDramaId, dramaId)
                .eq(UserEpisodeUnlock::getUnlockType, "DRAMA")
                .exists();
        if (dramaUnlocked) {
            return;
        }
        int wholePrice = drama.getWholePricePoints() == null ? 0 : drama.getWholePricePoints();
        if (wholePrice <= 0) {
            throw new IllegalArgumentException("whole drama purchase is not enabled");
        }

        // 已解锁集数抵扣 (R21): 已花费 >= 整剧价50%时，最低按50%计算
        int alreadySpent = 0;
        List<UserEpisodeUnlock> episodeUnlocks = userEpisodeUnlockService.lambdaQuery()
                .eq(UserEpisodeUnlock::getUserId, userId)
                .eq(UserEpisodeUnlock::getDramaId, dramaId)
                .eq(UserEpisodeUnlock::getUnlockType, "EPISODE")
                .list();
        for (UserEpisodeUnlock unlock : episodeUnlocks) {
            alreadySpent += unlock.getPointsCost() != null ? unlock.getPointsCost() : 0;
        }

        int afterDeduction = Math.max(wholePrice - alreadySpent, 0);
        int minPrice = (int) Math.ceil(wholePrice * 0.5);
        if (alreadySpent >= minPrice) {
            afterDeduction = Math.max(afterDeduction, minPrice);
        }
        if (alreadySpent >= wholePrice) {
            // 已花费 >= 整剧价，直接标记解锁
            saveUnlock(userId, dramaId, null, "DRAMA", 0);
            return;
        }

        // 会员折扣
        String level = membershipService.getCurrentLevel(userId);
        double discount = membershipService.getDramaDiscount(level);
        int cost = (int) Math.ceil(afterDeduction * discount);

        // 统一走 PointService 扣减 (R10)
        pointService.addPoints(userId, -cost, "UNLOCK_DRAMA", String.valueOf(dramaId), "unlock drama");

        saveUnlock(userId, dramaId, null, "DRAMA", cost);
    }

    private void saveUnlock(Long userId, Long dramaId, Long episodeId, String unlockType, int pointsCost) {
        UserEpisodeUnlock unlock = new UserEpisodeUnlock();
        unlock.setUserId(userId);
        unlock.setDramaId(dramaId);
        unlock.setEpisodeId(episodeId);
        unlock.setUnlockType(unlockType);
        unlock.setPointsCost(pointsCost);
        userEpisodeUnlockService.save(unlock);
    }

    // --- Preview Methods ---

    /** 单集解锁预览（原价/折扣价/免费额度） */
    public Map<String, Object> previewUnlockEpisode(Long userId, Long episodeId) {
        Map<String, Object> episode = dramaEpisodeService.episode(episodeId);
        if (episode == null) {
            throw new IllegalArgumentException("episode not found");
        }
        Long dramaId = MapUtil.lng(episode, "drama_id");
        int episodeNo = MapUtil.integer(episode, "episode_no");
        Drama drama = dramaEntityService.getById(dramaId);
        int freeCount = drama == null || drama.getFreeEpisodeCount() == null ? 0 : drama.getFreeEpisodeCount();

        boolean isFree = episodeNo <= freeCount
                || Integer.valueOf(1).equals(MapUtil.integer(episode, "is_free"));
        if (isFree) {
            return MapUtil.map("is_free", true, "base_cost", 0, "discounted_cost", 0,
                    "discount", 1.0, "free_remaining", 0);
        }

        String level = membershipService.getCurrentLevel(userId);
        double discount = membershipService.getEpisodeDiscount(level);
        int baseCost = MapUtil.integer(episode, "price_points");
        if (baseCost <= 0 && drama != null && drama.getEpisodePricePoints() != null) {
            baseCost = drama.getEpisodePricePoints();
        }
        int discountedCost = (int) Math.ceil(baseCost * discount);

        // VIP免费额度剩余
        var membership = membershipService.getMembership(userId);
        int freeTotal = membershipService.getDailyFreeEpisodes(level);
        int freeUsed = membership.getDailyFreeUsed() != null ? membership.getDailyFreeUsed() : 0;
        int freeRemaining = Math.max(0, freeTotal - freeUsed);

        return MapUtil.map(
                "is_free", false,
                "base_cost", baseCost,
                "discounted_cost", discountedCost,
                "discount", discount,
                "membership_level", level,
                "free_remaining", freeRemaining,
                "free_total", freeTotal
        );
    }

    /** 整剧解锁预览（原价/抵扣/折扣/最终价） */
    public Map<String, Object> previewUnlockDrama(Long userId, Long dramaId) {
        Drama drama = dramaEntityService.getById(dramaId);
        if (drama == null) {
            throw new IllegalArgumentException("drama not found");
        }
        int wholePrice = drama.getWholePricePoints() == null ? 0 : drama.getWholePricePoints();

        boolean dramaUnlocked = userEpisodeUnlockService.lambdaQuery()
                .eq(UserEpisodeUnlock::getUserId, userId)
                .eq(UserEpisodeUnlock::getDramaId, dramaId)
                .eq(UserEpisodeUnlock::getUnlockType, "DRAMA")
                .exists();

        if (dramaUnlocked) {
            return MapUtil.map("is_unlocked", true, "whole_price", wholePrice,
                    "final_cost", 0, "already_spent", wholePrice);
        }

        int alreadySpent = 0;
        List<UserEpisodeUnlock> episodeUnlocks = userEpisodeUnlockService.lambdaQuery()
                .eq(UserEpisodeUnlock::getUserId, userId)
                .eq(UserEpisodeUnlock::getDramaId, dramaId)
                .eq(UserEpisodeUnlock::getUnlockType, "EPISODE")
                .list();
        for (UserEpisodeUnlock unlock : episodeUnlocks) {
            alreadySpent += unlock.getPointsCost() != null ? unlock.getPointsCost() : 0;
        }

        int afterDeduction = Math.max(wholePrice - alreadySpent, 0);
        int minPrice = (int) Math.ceil(wholePrice * 0.5);
        if (alreadySpent >= minPrice) {
            afterDeduction = Math.max(afterDeduction, minPrice);
        }

        String level = membershipService.getCurrentLevel(userId);
        double discount = membershipService.getDramaDiscount(level);
        int finalCost = (int) Math.ceil(afterDeduction * discount);

        return MapUtil.map(
                "is_unlocked", false,
                "whole_price", wholePrice,
                "already_spent", alreadySpent,
                "after_deduction", afterDeduction,
                "discount", discount,
                "membership_level", level,
                "final_cost", finalCost,
                "min_price", minPrice
        );
    }
}
