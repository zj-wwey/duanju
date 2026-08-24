package com.duanju.service;

import com.duanju.entity.AutoRenewalSubscription;
import com.duanju.entity.PointProduct;
import com.duanju.service.entity.AutoRenewalSubscriptionService;
import com.duanju.service.entity.PointProductService;
import com.duanju.util.MapUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 自动续费管理服务 (R15: 提前24小时扣款，失败3次取消)。
 */
@Service
public class AutoRenewalService {

    private static final int MAX_FAIL_COUNT = 3;
    private static final int RETRY_HOURS = 6;

    private final AutoRenewalSubscriptionService subscriptionService;
    private final PointProductService pointProductService;
    private final OrderService orderService;
    private final VipService vipService;

    public AutoRenewalService(AutoRenewalSubscriptionService subscriptionService,
                              PointProductService pointProductService,
                              OrderService orderService,
                              VipService vipService) {
        this.subscriptionService = subscriptionService;
        this.pointProductService = pointProductService;
        this.orderService = orderService;
        this.vipService = vipService;
    }

    /** 开通自动续费 */
    @Transactional
    public Map<String, Object> subscribe(Long userId, Long productId, String payChannel, String payMethodToken) {
        // 检查是否已有活跃订阅
        AutoRenewalSubscription existing = subscriptionService.lambdaQuery()
                .eq(AutoRenewalSubscription::getUserId, userId)
                .eq(AutoRenewalSubscription::getStatus, "ACTIVE")
                .one();
        if (existing != null) {
            throw new IllegalArgumentException("已有活跃的自动续费订阅");
        }

        PointProduct product = pointProductService.getById(productId);
        if (product == null) {
            throw new IllegalArgumentException("product not found");
        }

        // 下次扣款时间 = 当前时间 + 套餐天数 - 24小时
        int durationDays = product.getDurationDays() != null ? product.getDurationDays() : 30;
        LocalDateTime nextCharge = LocalDateTime.now().plusDays(durationDays).minusHours(24);

        AutoRenewalSubscription sub = new AutoRenewalSubscription();
        sub.setUserId(userId);
        sub.setProductId(productId);
        sub.setPayChannel(payChannel);
        sub.setPayMethodToken(payMethodToken);
        sub.setNextChargeAt(nextCharge);
        sub.setStatus("ACTIVE");
        sub.setFailCount(0);
        subscriptionService.save(sub);

        return MapUtil.map(
                "subscription_id", sub.getId(),
                "next_charge_at", nextCharge,
                "product_name", product.getName(),
                "status", "ACTIVE"
        );
    }

    /** 取消自动续费 */
    @Transactional
    public void cancel(Long userId) {
        subscriptionService.lambdaUpdate()
                .set(AutoRenewalSubscription::getStatus, "CANCELLED")
                .eq(AutoRenewalSubscription::getUserId, userId)
                .eq(AutoRenewalSubscription::getStatus, "ACTIVE")
                .update();
    }

    /** 查询状态 */
    public Map<String, Object> getStatus(Long userId) {
        AutoRenewalSubscription sub = subscriptionService.lambdaQuery()
                .eq(AutoRenewalSubscription::getUserId, userId)
                .eq(AutoRenewalSubscription::getStatus, "ACTIVE")
                .one();
        if (sub == null) {
            return MapUtil.map("active", false);
        }
        return MapUtil.map(
                "active", true,
                "product_id", sub.getProductId(),
                "pay_channel", sub.getPayChannel(),
                "next_charge_at", sub.getNextChargeAt(),
                "fail_count", sub.getFailCount()
        );
    }

    /** 定时任务：执行自动续费扣款 */
    @Transactional
    public void processAutoRenewals() {
        LocalDateTime now = LocalDateTime.now();
        List<AutoRenewalSubscription> activeList = subscriptionService.lambdaQuery()
                .eq(AutoRenewalSubscription::getStatus, "ACTIVE")
                .le(AutoRenewalSubscription::getNextChargeAt, now)
                .list();

        for (AutoRenewalSubscription sub : activeList) {
            try {
                PointProduct product = pointProductService.getById(sub.getProductId());
                if (product == null) {
                    continue;
                }

                // 创建订单 (定时任务无Security上下文,必须显式传userId)
                Map<String, Object> order = orderService.createOrder(sub.getUserId(), sub.getProductId(), sub.getPayChannel());
                String orderNo = (String) order.get("order_no");
                orderService.markPaid(orderNo);

                // VIP activation is handled by markPaid() -> onPaymentSuccess() -> activateVip()

                // 更新下次扣款时间
                int durationDays = product.getDurationDays() != null ? product.getDurationDays() : 30;
                sub.setNextChargeAt(now.plusDays(durationDays).minusHours(24));
                sub.setLastChargeOrderNo(orderNo);
                sub.setFailCount(0);
                subscriptionService.updateById(sub);

            } catch (Exception e) {
                sub.setFailCount((sub.getFailCount() != null ? sub.getFailCount() : 0) + 1);
                if (sub.getFailCount() >= MAX_FAIL_COUNT) {
                    sub.setStatus("FAILED");
                } else {
                    // 延后重试
                    sub.setNextChargeAt(now.plusHours(RETRY_HOURS));
                }
                subscriptionService.updateById(sub);
            }
        }
    }
}