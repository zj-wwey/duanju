package com.duanju.service;

import com.duanju.entity.AppUser;
import com.duanju.entity.PointExpire;
import com.duanju.service.entity.AppUserService;
import com.duanju.service.entity.PointExpireMpService;
import com.duanju.util.MapUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class PointExpireService {

    private static final LocalDate NEVER_EXPIRE = LocalDate.of(9999, 12, 31);
    private static final int DEFAULT_PROMOTION_DAYS = 90;
    private static final int DEFAULT_EARNED_DAYS = 365;

    private final PointExpireMpService pointExpireMpService;
    private final AppUserService appUserService;

    public PointExpireService(PointExpireMpService pointExpireMpService,
                              AppUserService appUserService) {
        this.pointExpireMpService = pointExpireMpService;
        this.appUserService = appUserService;
    }

    // ==================== Create Batch ====================

    public PointExpire createBatch(Long userId, int points, String pointType) {
        PointExpire batch = new PointExpire();
        batch.setUserId(userId);
        batch.setPointType(pointType);
        batch.setTotalPoints(points);
        batch.setRemaining(points);
        batch.setExpireAt(calcExpireAt(pointType, 0));
        pointExpireMpService.save(batch);
        return batch;
    }

    public PointExpire createBatch(Long userId, int points, String pointType, int expireDays) {
        PointExpire batch = new PointExpire();
        batch.setUserId(userId);
        batch.setPointType(pointType);
        batch.setTotalPoints(points);
        batch.setRemaining(points);
        batch.setExpireAt(calcExpireAt(pointType, expireDays));
        pointExpireMpService.save(batch);
        return batch;
    }

    // ==================== FIFO Consumption ====================

    @Transactional
    public void consumePoints(Long userId, int points) {
        if (points <= 0) {
            return;
        }
        List<PointExpire> batches = pointExpireMpService.lambdaQuery()
                .eq(PointExpire::getUserId, userId)
                .gt(PointExpire::getRemaining, 0)
                .last("ORDER BY CASE point_type WHEN 'PROMOTION' THEN 1 WHEN 'EARNED' THEN 2 WHEN 'PURCHASED' THEN 3 END, expire_at ASC FOR UPDATE")
                .list();

        int remaining = points;
        for (PointExpire batch : batches) {
            if (remaining <= 0) {
                break;
            }
            int batchRemaining = batch.getRemaining();
            int deduct = Math.min(batchRemaining, remaining);
            batch.setRemaining(batchRemaining - deduct);
            pointExpireMpService.updateById(batch);
            remaining -= deduct;
        }
    }

    // ==================== Cleanup Expired ====================

    @Transactional
    public void cleanupExpiredPoints() {
        LocalDate today = LocalDate.now();
        List<PointExpire> expiredBatches = pointExpireMpService.lambdaQuery()
                .le(PointExpire::getExpireAt, today)
                .ne(PointExpire::getExpireAt, NEVER_EXPIRE)
                .gt(PointExpire::getRemaining, 0)
                .list();

        for (PointExpire batch : expiredBatches) {
            int remaining = batch.getRemaining();
            if (remaining > 0) {
                appUserService.lambdaUpdate()
                        .setSql("points = GREATEST(0, points - " + remaining + ")")
                        .eq(AppUser::getId, batch.getUserId())
                        .update();
                batch.setRemaining(0);
                pointExpireMpService.updateById(batch);
            }
        }
    }

    // ==================== Query ====================

    public List<Map<String, Object>> getExpireBatches(Long userId) {
        List<PointExpire> batches = pointExpireMpService.lambdaQuery()
                .eq(PointExpire::getUserId, userId)
                .orderByAsc(PointExpire::getExpireAt)
                .list();
        return MapUtil.beansToMaps(batches);
    }

    // ==================== Private Helpers ====================

    private LocalDate calcExpireAt(String pointType, int customDays) {
        if (customDays > 0) {
            return LocalDate.now().plusDays(customDays);
        }
        if ("PURCHASED".equals(pointType)) {
            return NEVER_EXPIRE;
        }
        if ("PROMOTION".equals(pointType)) {
            return LocalDate.now().plusDays(DEFAULT_PROMOTION_DAYS);
        }
        // EARNED or other
        return LocalDate.now().plusDays(DEFAULT_EARNED_DAYS);
    }
}