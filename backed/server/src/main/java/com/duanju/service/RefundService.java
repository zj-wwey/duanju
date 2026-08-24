package com.duanju.service;

import com.duanju.entity.UserEpisodeUnlock;
import com.duanju.service.entity.UserEpisodeUnlockService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 退款资格校验服务 (R12: 7天内+观看≤10集方可退款)。
 *
 * <p>供 OrderService.refund() / refundByStore() 调用。</p>
 */
@Service
public class RefundService {

    private static final int REFUND_MAX_DAYS = 7;
    private static final int REFUND_MAX_UNLOCKS = 10;

    private final UserEpisodeUnlockService userEpisodeUnlockService;

    public RefundService(UserEpisodeUnlockService userEpisodeUnlockService) {
        this.userEpisodeUnlockService = userEpisodeUnlockService;
    }

    /**
     * 校验退款资格。
     *
     * @param userId    用户ID
     * @param createdAt 订单创建时间
     * @throws IllegalArgumentException 不满足退款条件时抛出
     */
    public void validateRefundEligibility(Long userId, LocalDateTime createdAt) {
        // 1. 7天内
        long daysSinceCreate = Duration.between(createdAt, LocalDateTime.now()).toDays();
        if (daysSinceCreate > REFUND_MAX_DAYS) {
            throw new IllegalArgumentException("购买已超过 " + REFUND_MAX_DAYS + " 天，不支持退款");
        }

        // 2. 观看解锁 ≤ 10集
        long unlockCount = userEpisodeUnlockService.lambdaQuery()
                .eq(UserEpisodeUnlock::getUserId, userId)
                .gt(UserEpisodeUnlock::getCreatedAt, createdAt)
                .count();
        if (unlockCount >= REFUND_MAX_UNLOCKS) {
            throw new IllegalArgumentException("已观看解锁超过 " + REFUND_MAX_UNLOCKS + " 集，不支持退款");
        }
    }
}