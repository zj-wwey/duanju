package com.duanju.config;

import com.duanju.service.AutoRenewalService;
import com.duanju.service.MembershipService;
import com.duanju.service.OrderService;
import com.duanju.service.PointExpireService;
import com.duanju.service.VipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 定时任务配置。
 *
 * <pre>
 * expirePurchaseMemberships  - 每小时  - 降级过期购买会员
 * cleanupExpiredPoints       - 每日00:30 - 清理过期积分
 * expireOldVipRecords        - 每小时  - VIP记录标记过期
 * processAutoRenewals        - 每小时  - 执行自动续费扣款
 * cancelExpiredPendingOrders - 每5分钟 - 关闭超时未支付PENDING订单(30分钟)
 * </pre>
 */
@Configuration
@EnableScheduling
public class ScheduledTaskConfig {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskConfig.class);
    private static final int PENDING_ORDER_EXPIRE_MINUTES = 30;

    private final MembershipService membershipService;
    private final PointExpireService pointExpireService;
    private final VipService vipService;
    private final AutoRenewalService autoRenewalService;
    private final OrderService orderService;

    public ScheduledTaskConfig(MembershipService membershipService,
                               PointExpireService pointExpireService,
                               VipService vipService,
                               AutoRenewalService autoRenewalService,
                               OrderService orderService) {
        this.membershipService = membershipService;
        this.pointExpireService = pointExpireService;
        this.vipService = vipService;
        this.autoRenewalService = autoRenewalService;
        this.orderService = orderService;
    }

    /** 每小时执行：降级过期购买会员 + 过期VIP记录 + 自动续费扣款 */
    @Scheduled(cron = "0 0 * * * ?")
    public void hourlyTasks() {
        try {
            log.info("running hourly tasks: expirePurchaseMemberships");
            membershipService.expirePurchaseMemberships();
            log.info("expirePurchaseMemberships completed");
        } catch (Exception e) {
            log.error("expirePurchaseMemberships failed", e);
        }

        try {
            log.info("running hourly tasks: expireOldVipRecords");
            vipService.expireOldRecords();
            log.info("expireOldVipRecords completed");
        } catch (Exception e) {
            log.error("expireOldVipRecords failed", e);
        }

        try {
            log.info("running hourly tasks: processAutoRenewals");
            autoRenewalService.processAutoRenewals();
            log.info("processAutoRenewals completed");
        } catch (Exception e) {
            log.error("processAutoRenewals failed", e);
        }
    }

    /** 每日 00:30 执行：清理过期积分 */
    @Scheduled(cron = "0 30 0 * * ?")
    public void cleanupExpiredPoints() {
        try {
            log.info("running cleanupExpiredPoints");
            pointExpireService.cleanupExpiredPoints();
            log.info("cleanupExpiredPoints completed");
        } catch (Exception e) {
            log.error("cleanupExpiredPoints failed", e);
        }
    }

    /** 每 5 分钟执行：关闭超时未支付的 PENDING 订单 (创建超过 30 分钟) */
    @Scheduled(cron = "0 */5 * * * ?")
    public void cancelExpiredPendingOrders() {
        try {
            int closed = orderService.cancelExpiredPendingOrders(PENDING_ORDER_EXPIRE_MINUTES);
            if (closed > 0) {
                log.info("cancelExpiredPendingOrders completed: {} orders cancelled", closed);
            }
        } catch (Exception e) {
            log.error("cancelExpiredPendingOrders failed", e);
        }
    }
}