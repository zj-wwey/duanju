package com.duanju.service;

import com.duanju.entity.PointProduct;
import com.duanju.entity.UserVipRecord;
import com.duanju.security.PrincipalHolder;
import com.duanju.service.entity.PointProductService;
import com.duanju.service.entity.UserVipRecordService;
import com.duanju.util.MapUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class VipService {

    private final UserVipRecordService vipRecordService;
    private final PointProductService pointProductService;
    private final MembershipService membershipService;
    private final PointService pointService;

    /** 积分兑换会员价格表: 等级 -> (天数 -> 积分) */
    private static final Map<String, Map<Integer, Integer>> EXCHANGE_PRICE_TABLE = Map.of(
            "SILVER", Map.of(1, 200, 7, 1200),
            "GOLD", Map.of(1, 350, 7, 2100),
            "DIAMOND", Map.of(1, 600, 7, 3600)
    );
    private static final double EXCHANGE_RENEWAL_DISCOUNT = 0.9;

    /** 跨等级升级折算比例: 旧等级 -> (新等级 -> 比例) */
    private static final Map<String, Map<String, Double>> UPGRADE_CONVERT_RATIO = Map.of(
            "SILVER", Map.of("GOLD", 0.6, "DIAMOND", 0.4),
            "GOLD", Map.of("DIAMOND", 0.65)
    );

    public VipService(UserVipRecordService vipRecordService,
                      PointProductService pointProductService,
                      MembershipService membershipService,
                      PointService pointService) {
        this.vipRecordService = vipRecordService;
        this.pointProductService = pointProductService;
        this.membershipService = membershipService;
        this.pointService = pointService;
    }

    public Map<String, Object> getMyVipStatus() {
        Long userId = PrincipalHolder.userId();
        LocalDateTime now = LocalDateTime.now();
        UserVipRecord activeRecord = vipRecordService.lambdaQuery()
                .eq(UserVipRecord::getUserId, userId)
                .eq(UserVipRecord::getStatus, 1)
                .gt(UserVipRecord::getExpireAt, now)
                .orderByDesc(UserVipRecord::getExpireAt)
                .last("limit 1")
                .one();
        if (activeRecord == null) {
            return MapUtil.map("is_vip", false, "vip_type", null,
                    "start_at", null, "expire_at", null);
        }
        return MapUtil.map("is_vip", true,
                "vip_type", activeRecord.getVipType(),
                "start_at", activeRecord.getStartAt(),
                "expire_at", activeRecord.getExpireAt(),
                "product_id", activeRecord.getProductId());
    }

    public List<Map<String, Object>> getMyVipRecords(int limit) {
        Long userId = PrincipalHolder.userId();
        List<UserVipRecord> list = vipRecordService.lambdaQuery()
                .eq(UserVipRecord::getUserId, userId)
                .orderByDesc(UserVipRecord::getCreatedAt)
                .last("limit " + Math.max(1, Math.min(limit, 200)))
                .list();
        return MapUtil.beansToMaps(list);
    }

    /**
     * 激活会员 (R11: 跨等级升级折算)。
     */
    @Transactional
    public void activateVip(Long userId, Long productId, String orderNo) {
        PointProduct product = pointProductService.getById(productId);
        if (product == null) {
            throw new IllegalArgumentException("product not found");
        }
        // 非 VIP 商品跳过
        String productCategory = product.getProductCategory();
        if (productCategory == null || !"VIP".equals(productCategory)) {
            return;
        }
        String newLevel = product.getMembershipLevel();
        if (newLevel == null) {
            newLevel = extractLevelFromVipType(product.getPackageType());
        }

        int durationDays = product.getDurationDays() != null ? product.getDurationDays() : 30;
        LocalDateTime now = LocalDateTime.now();

        // 查当前生效VIP记录
        UserVipRecord activeRecord = vipRecordService.lambdaQuery()
                .eq(UserVipRecord::getUserId, userId)
                .eq(UserVipRecord::getStatus, 1)
                .gt(UserVipRecord::getExpireAt, now)
                .orderByDesc(UserVipRecord::getExpireAt)
                .last("limit 1")
                .one();

        LocalDateTime startAt = now;
        int totalDays = durationDays;

        if (activeRecord != null) {
            String oldLevel = extractLevelFromVipType(activeRecord.getVipType());
            if (oldLevel != null && oldLevel.equals(newLevel)) {
                // 同等级续费：从expireAt叠加
                startAt = activeRecord.getExpireAt();
            } else if (oldLevel != null && !oldLevel.equals(newLevel)) {
                // 跨等级升级：旧剩余天数按价格比折算 + 新天数
                long remainingDays = java.time.Duration.between(now, activeRecord.getExpireAt()).toDays();
                if (remainingDays > 0) {
                    double ratio = getUpgradeConvertRatio(oldLevel, newLevel);
                    int convertedDays = (int) Math.ceil(remainingDays * ratio);
                    totalDays = convertedDays + durationDays;
                }
                // 旧记录标记失效
                vipRecordService.lambdaUpdate()
                        .set(UserVipRecord::getStatus, 0)
                        .eq(UserVipRecord::getId, activeRecord.getId())
                        .update();
                startAt = now;
            }
        }

        LocalDateTime expireAt = startAt.plusDays(totalDays);

        UserVipRecord record = new UserVipRecord();
        record.setUserId(userId);
        record.setProductId(productId);
        record.setOrderNo(orderNo);
        record.setVipType(product.getPackageType() != null ? product.getPackageType() : "VIP_" + newLevel);
        record.setStartAt(startAt);
        record.setExpireAt(expireAt);
        record.setStatus(1);
        vipRecordService.save(record);

        // 通知 MembershipService 更新购买等级
        membershipService.onVipActivated(userId, newLevel, expireAt);
    }

    /**
     * 积分兑换会员 (R6: 不计入累计消费, R24: 同等级续费享9折)。
     */
    @Transactional
    public Map<String, Object> exchangeVipByPoints(Long userId, String level, int days) {
        Map<Integer, Integer> priceMap = EXCHANGE_PRICE_TABLE.get(level);
        if (priceMap == null || !priceMap.containsKey(days)) {
            throw new IllegalArgumentException("Unsupported exchange: " + level + " " + days + " days");
        }
        int basePrice = priceMap.get(days);

        // 同等级续费享9折
        String currentPurchaseLevel = membershipService.getCurrentLevel(userId);
        boolean sameLevel = currentPurchaseLevel.equals(level);
        int finalPrice = sameLevel ? (int) Math.ceil(basePrice * EXCHANGE_RENEWAL_DISCOUNT) : basePrice;

        // 扣积分（不走倍率，因为这不是赚取行为）
        pointService.addPoints(userId, -finalPrice, "VIP_EXCHANGE", level + "_" + days, "exchange vip by points");

        // 创建VIP记录（不计入累计消费）
        String packageType = "VIP_EXCHANGE_" + level;
        LocalDateTime now = LocalDateTime.now();

        UserVipRecord activeRecord = vipRecordService.lambdaQuery()
                .eq(UserVipRecord::getUserId, userId)
                .eq(UserVipRecord::getStatus, 1)
                .gt(UserVipRecord::getExpireAt, now)
                .orderByDesc(UserVipRecord::getExpireAt)
                .last("limit 1")
                .one();

        LocalDateTime startAt = now;
        if (activeRecord != null && sameLevel) {
            startAt = activeRecord.getExpireAt();
        } else if (activeRecord != null) {
            vipRecordService.lambdaUpdate()
                    .set(UserVipRecord::getStatus, 0)
                    .eq(UserVipRecord::getId, activeRecord.getId())
                    .update();
        }

        UserVipRecord record = new UserVipRecord();
        record.setUserId(userId);
        record.setProductId(null);
        record.setOrderNo(null);
        record.setVipType(packageType);
        record.setStartAt(startAt);
        record.setExpireAt(startAt.plusDays(days));
        record.setStatus(1);
        vipRecordService.save(record);

        membershipService.onVipActivated(userId, level, record.getExpireAt());

        return MapUtil.map(
                "level", level,
                "days", days,
                "points_cost", finalPrice,
                "original_price", basePrice,
                "same_level_discount", sameLevel,
                "expire_at", record.getExpireAt()
        );
    }

    @Transactional
    public void expireOldRecords() {
        LocalDateTime now = LocalDateTime.now();
        vipRecordService.lambdaUpdate()
                .set(UserVipRecord::getStatus, 0)
                .lt(UserVipRecord::getExpireAt, now)
                .eq(UserVipRecord::getStatus, 1)
                .update();
    }

    public List<Map<String, Object>> getAllVipRecords(String keyword, String status, int limit) {
        var query = vipRecordService.lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            query.and(w -> w.like(UserVipRecord::getOrderNo, keyword)
                    .or().like(UserVipRecord::getVipType, keyword));
        }
        if (status != null && !status.isBlank()) {
            query.eq(UserVipRecord::getStatus, Integer.parseInt(status));
        }
        List<UserVipRecord> list = query
                .orderByDesc(UserVipRecord::getCreatedAt)
                .last("limit " + Math.max(1, Math.min(limit, 500)))
                .list();
        return MapUtil.beansToMaps(list);
    }

    /** 从 VIP 类型字符串提取等级 */
    private String extractLevelFromVipType(String vipType) {
        if (vipType == null) return null;
        String upper = vipType.toUpperCase();
        if (upper.contains("VIP_EXCHANGE_")) {
            return upper.replace("VIP_EXCHANGE_", "");
        }
        if (upper.contains("VIP_")) {
            return upper.replace("VIP_", "");
        }
        for (String level : new String[]{"DIAMOND", "GOLD", "SILVER"}) {
            if (upper.contains(level)) return level;
        }
        return null;
    }

    /** 获取跨等级折算比例 */
    private double getUpgradeConvertRatio(String fromLevel, String toLevel) {
        if (fromLevel == null || toLevel == null || fromLevel.equals(toLevel)) return 1.0;
        Map<String, Double> subMap = UPGRADE_CONVERT_RATIO.get(fromLevel);
        if (subMap == null) return 1.0;
        return subMap.getOrDefault(toLevel, 1.0);
    }
}
