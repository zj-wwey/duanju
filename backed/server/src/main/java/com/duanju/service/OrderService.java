package com.duanju.service;

import com.duanju.dto.payment.StorePaymentContext;
import com.duanju.entity.PointProduct;
import com.duanju.entity.UserEpisodeUnlock;
import com.duanju.entity.UserOrder;
import com.duanju.security.PrincipalHolder;
import com.duanju.service.entity.PointProductService;
import com.duanju.service.entity.UserEpisodeUnlockService;
import com.duanju.service.entity.UserOrderService;
import com.duanju.util.MapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final int GLOBAL_MONTHLY_CAP_CENTS = 500_000;  // $5000
    private static final int MINOR_MONTHLY_CAP_CENTS = 20_000;   // $200
    private static final int NEW_USER_HOURS_LIMIT = 24;
    private static final int NEW_USER_DAILY_LIMIT = 1;
    private static final int NEW_USER_MAX_PRICE_CENTS = 499;      // $4.99
    private static final int REFUND_MAX_DAYS = 7;
    private static final int REFUND_MAX_UNLOCKS = 10;

    // 消费升级阈值 (分)
    private static final int SILVER_THRESHOLD = 9900;
    private static final int GOLD_THRESHOLD = 29900;
    private static final int DIAMOND_THRESHOLD = 99900;

    private static final String LEVEL_NONE = "NONE";
    private static final String LEVEL_SILVER = "SILVER";
    private static final String LEVEL_GOLD = "GOLD";
    private static final String LEVEL_DIAMOND = "DIAMOND";

    private final UserOrderService userOrderService;
    private final PointProductService pointProductService;
    private final PointService pointService;
    private final VipService vipService;
    private final MembershipService membershipService;
    private final UserEpisodeUnlockService userEpisodeUnlockService;
    private final StringRedisTemplate redisTemplate;
    private final StripePaymentService stripePaymentService;
    private final PayPalPaymentService payPalPaymentService;
    private final AppleIapService appleIapService;
    private final GooglePlayIapService googlePlayIapService;
    private final PaymentEventLogService paymentEventLogService;

    public OrderService(UserOrderService userOrderService, PointProductService pointProductService,
                        PointService pointService, VipService vipService,
                        MembershipService membershipService,
                        UserEpisodeUnlockService userEpisodeUnlockService,
                        StringRedisTemplate redisTemplate,
                        @Lazy StripePaymentService stripePaymentService,
                        @Lazy PayPalPaymentService payPalPaymentService,
                        @Lazy AppleIapService appleIapService,
                        @Lazy GooglePlayIapService googlePlayIapService,
                        PaymentEventLogService paymentEventLogService) {
        this.userOrderService = userOrderService;
        this.pointProductService = pointProductService;
        this.pointService = pointService;
        this.vipService = vipService;
        this.membershipService = membershipService;
        this.userEpisodeUnlockService = userEpisodeUnlockService;
        this.redisTemplate = redisTemplate;
        this.stripePaymentService = stripePaymentService;
        this.payPalPaymentService = payPalPaymentService;
        this.appleIapService = appleIapService;
        this.googlePlayIapService = googlePlayIapService;
        this.paymentEventLogService = paymentEventLogService;
    }

    // --- Products ---

    public List<Map<String, Object>> getProducts() {
        return MapUtil.beansToMaps(pointProductService.lambdaQuery()
                .eq(PointProduct::getStatus, 1)
                .orderByAsc(PointProduct::getSortOrder)
                .orderByAsc(PointProduct::getId)
                .list());
    }

    // --- User orders ---

    public List<Map<String, Object>> getUserOrders(int limit) {
        return userOrderService.userOrders(PrincipalHolder.userId(), Math.max(1, Math.min(limit, 200)));
    }

    public Map<String, Object> createOrder(Long productId, String payChannel) {
        return createOrder(PrincipalHolder.userId(), productId, payChannel);
    }

    public Map<String, Object> createOrder(Long userId, Long productId, String payChannel) {
        PointProduct product = pointProductService.lambdaQuery()
                .eq(PointProduct::getId, productId)
                .eq(PointProduct::getStatus, 1)
                .one();
        if (product == null) {
            throw new IllegalArgumentException("product not found");
        }

        // 购买限制校验
        validatePurchaseLimit(userId, product);

        int productPoints = product.getPoints() == null ? 0 : product.getPoints();
        int bonusPoints = product.getBonusPoints() == null ? 0 : product.getBonusPoints();
        int points = productPoints + bonusPoints;

        // 首充奖励
        int firstPurchaseBonus = calculateFirstPurchaseBonus(userId, product);
        int totalPoints = points + firstPurchaseBonus;

        if (payChannel == null || payChannel.isBlank()) {
            throw new IllegalArgumentException("payChannel required");
        }
        String pc = payChannel.toUpperCase();
        if (!StorePaymentContext.CHANNEL_APPLE_IAP.equals(pc)
                && !StorePaymentContext.CHANNEL_GOOGLE_PLAY.equals(pc)
                && !StorePaymentContext.CHANNEL_STRIPE.equals(pc)
                && !StorePaymentContext.CHANNEL_PAYPAL.equals(pc)) {
            throw new IllegalArgumentException("unsupported payChannel: " + pc);
        }
        UserOrder order = new UserOrder();
        // Apple/Google IAP 的 appAccountToken 需要 UUID 格式;Stripe 也推荐 UUID 作为商户订单号避免重复
        order.setOrderNo(requiresUuidOrderNo(pc) ? orderNoUuid() : orderNo());
        order.setUserId(userId);
        order.setProductId(productId);
        order.setOrderType("RECHARGE");
        order.setPayChannel(pc);
        order.setPoints(totalPoints);
        order.setAmountCents(product.getPriceCents());
        order.setCurrency(product.getCurrency());
        order.setStatus("PENDING");
        userOrderService.save(order);

        Map<String, Object> result = userOrderService.orderByNo(order.getOrderNo());
        // 等级变化预告
        Map<String, Object> levelPreview = calculateLevelPreview(userId, product.getPriceCents());
        result.put("level_preview", levelPreview);
        result.put("first_purchase_bonus", firstPurchaseBonus);
        result.put("base_points", points);
        result.put("total_points", totalPoints);
        return result;
    }

    /** Apple IAP / Google Play / Stripe 需要 UUID 格式的 orderNo,便于渠道侧字段承载 */
    private static boolean requiresUuidOrderNo(String payChannel) {
        return StorePaymentContext.CHANNEL_APPLE_IAP.equals(payChannel)
                || StorePaymentContext.CHANNEL_GOOGLE_PLAY.equals(payChannel)
                || StorePaymentContext.CHANNEL_STRIPE.equals(payChannel);
    }

    // --- Store IAP / Stripe integration ---

    /**
     * 通用"渠道确认支付成功"入口,被 AppleIapService / GooglePlayIapService / StripePaymentService 复用。
     *
     * <p>执行顺序:</p>
     * <ol>
     *   <li>幂等:先按 store_transaction_id 查已处理订单,有则直接返回 (uk_order_store_tx 唯一索引)</li>
     *   <li>若有 orderNo:尝试走"更新 PENDING 订单为 PAID"路径,找不到 PENDING 则降级到"直接新建 PAID 订单" (不能抛异常让渠道重试)</li>
     *   <li>若无 orderNo:直接新建 PAID 订单 (Webhook/通知先到的路径)</li>
     *   <li>发积分必须走 PointService.addPoints</li>
     * </ol>
     *
     * <p>注意:该方法使用条件更新 (UpdateWrapper where status=PENDING),确保并发安全。</p>
     */
    @Transactional
    public Map<String, Object> markPaidByStore(StorePaymentContext ctx) {
        // 1. 幂等:store_transaction_id 已存在则直接返回
        UserOrder existing = userOrderService.lambdaQuery()
                .eq(UserOrder::getStoreTransactionId, ctx.storeTransactionId())
                .last("limit 1")
                .one();
        if (existing != null) {
            return userOrderService.orderByNo(existing.getOrderNo());
        }

        PointProduct product = pointProductService.getById(ctx.productId());
        if (product == null) {
            throw new IllegalArgumentException("product not found for storeProductId: " + ctx.storeProductId());
        }
        int productPoints = product.getPoints() == null ? 0 : product.getPoints();
        int bonusPoints = product.getBonusPoints() == null ? 0 : product.getBonusPoints();
        int points = productPoints + bonusPoints;

        String channel = ctx.channel() == null ? "UNKNOWN" : ctx.channel();
        String remark = channel.toLowerCase() + " recharge";

        if (ctx.orderNo() != null && !ctx.orderNo().isBlank()) {
            // 客户端预创建订单路径:更新 PENDING 订单
            UserOrder pending = userOrderService.lambdaQuery()
                    .eq(UserOrder::getOrderNo, ctx.orderNo())
                    .eq(UserOrder::getStatus, "PENDING")
                    .last("limit 1")
                    .one();
            if (pending != null) {
                com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper<UserOrder> uw =
                        userOrderService.lambdaUpdate()
                                .set(UserOrder::getStatus, "PAID")
                                .set(UserOrder::getPaidAt, LocalDateTime.now())
                                .set(UserOrder::getPayChannel, channel)
                                .set(UserOrder::getStoreTransactionId, ctx.storeTransactionId())
                                .set(UserOrder::getStoreOriginalTransactionId, ctx.storeOriginalTransactionId())
                                .set(UserOrder::getStoreProductId, ctx.storeProductId())
                                .set(UserOrder::getStoreBundleId, ctx.storeBundleId())
                                .set(UserOrder::getStoreEnvironment, ctx.storeEnvironment())
                                .set(UserOrder::getStoreReceiptHash, ctx.storeReceiptHash())
                                .set(UserOrder::getTaxAmountCents, ctx.taxAmountCents() == null ? 0 : ctx.taxAmountCents());
                // 使用订单创建时锁定的积分,避免产品配置变更导致发放积分与承诺不一致
                points = pending.getPoints() == null ? points : pending.getPoints();
                // Stripe 额外列:仅 STRIPE 渠道更新,避免把其他渠道的列置空
                if (StorePaymentContext.CHANNEL_STRIPE.equals(channel)) {
                    uw.set(UserOrder::getStripePaymentIntentId, extractStripePaymentIntentId(ctx));
                    // stripe_session_id 已在创建 Checkout Session 时写入,这里保留原值,不要覆盖
                    if (pending.getStripeSessionId() != null) {
                        uw.set(UserOrder::getStripeSessionId, pending.getStripeSessionId());
                    }
                }
                // Google Play 额外列:仅 GOOGLE_PLAY 渠道更新
                if (StorePaymentContext.CHANNEL_GOOGLE_PLAY.equals(channel)) {
                    uw.set(UserOrder::getGooglePurchaseToken, ctx.storeTransactionId());
                }
                boolean updated = uw
                        .eq(UserOrder::getOrderNo, ctx.orderNo())
                        .eq(UserOrder::getStatus, "PENDING")
                        .update();
                if (!updated) {
                    // 并发竞争,订单已被其他线程处理
                    return userOrderService.orderByNo(ctx.orderNo());
                }
                pointService.addPoints(pending.getUserId(), points, "RECHARGE", pending.getOrderNo(), remark);
                // VIP类商品：激活会员 + 更新累计消费
                onPaymentSuccess(pending.getUserId(), product, pending.getOrderNo());
                return userOrderService.orderByNo(pending.getOrderNo());
            }
            // 未找到 PENDING 订单:不抛异常 (抛异常会让 Apple/Stripe 重试,消耗重试配额),
            // 降级为"无 orderNo 路径"新建 PAID 订单,只要 userId 可用就继续
            if (ctx.userId() == null) {
                throw new IllegalArgumentException("pending order not found and no userId to create new order, orderNo=" + ctx.orderNo());
            }
        }

        if (ctx.userId() == null) {
            throw new IllegalArgumentException("cannot resolve userId for store tx " + ctx.storeTransactionId());
        }

        // 渠道通知先到的路径:订单未预创建,直接新建 PAID 订单
        UserOrder order = new UserOrder();
        order.setOrderNo(orderNoUuid());
        order.setUserId(ctx.userId());
        order.setProductId(ctx.productId());
        order.setOrderType("RECHARGE");
        order.setPayChannel(channel);
        order.setPoints(points);
        order.setAmountCents(ctx.priceCents() == null ? product.getPriceCents() : ctx.priceCents());
        order.setCurrency(ctx.currency() == null ? product.getCurrency() : ctx.currency());
        order.setStatus("PAID");
        order.setPaidAt(LocalDateTime.now());
        order.setStoreTransactionId(ctx.storeTransactionId());
        order.setStoreOriginalTransactionId(ctx.storeOriginalTransactionId());
        order.setStoreProductId(ctx.storeProductId());
        order.setStoreBundleId(ctx.storeBundleId());
        order.setStoreEnvironment(ctx.storeEnvironment());
        order.setStoreReceiptHash(ctx.storeReceiptHash());
        order.setTaxAmountCents(ctx.taxAmountCents() == null ? 0 : ctx.taxAmountCents());
        // Stripe 额外列
        if (StorePaymentContext.CHANNEL_STRIPE.equals(channel)) {
            order.setStripePaymentIntentId(extractStripePaymentIntentId(ctx));
        }
        // Google Play 额外列
        if (StorePaymentContext.CHANNEL_GOOGLE_PLAY.equals(channel)) {
            order.setGooglePurchaseToken(ctx.storeTransactionId());
        }
        userOrderService.save(order);
        pointService.addPoints(ctx.userId(), points, "RECHARGE", order.getOrderNo(), remark);
        // VIP类商品：激活会员 + 更新累计消费
        onPaymentSuccess(ctx.userId(), product, order.getOrderNo());
        return userOrderService.orderByNo(order.getOrderNo());
    }

    /** 从 storeReceiptHash 位置复用:Stripe 把 stripe_payment_intent 放到 storeReceiptHash 不够干净,
     *  约定 Stripe 场景下 storeOriginalTransactionId 存 payment_intent_id (跨 Event 不变) */
    private static String extractStripePaymentIntentId(StorePaymentContext ctx) {
        if (!StorePaymentContext.CHANNEL_STRIPE.equals(ctx.channel())) {
            return null;
        }
        return ctx.storeOriginalTransactionId();
    }

    /**
     * 供 AppleIapService.resolveUserId 使用:按 orderNo 查询 PENDING 订单取 userId。
     * 只查 PENDING,避免把已 PAID 的 order 再用一次。
     */
    public Long findPendingOrderUserId(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return null;
        }
        UserOrder o = userOrderService.lambdaQuery()
                .eq(UserOrder::getOrderNo, orderNo)
                .eq(UserOrder::getStatus, "PENDING")
                .last("limit 1")
                .one();
        return o == null ? null : o.getUserId();
    }

    /**
     * 供 StripePaymentService 兜底:按 orderNo 取 PENDING 订单的 Map (含 product_id/user_id)。
     * Stripe PaymentIntent.metadata 不全时 (如 Dashboard 手工发起),通过此方法补全。
     */
    public Map<String, Object> findPendingByOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return null;
        }
        UserOrder o = userOrderService.lambdaQuery()
                .eq(UserOrder::getOrderNo, orderNo)
                .eq(UserOrder::getStatus, "PENDING")
                .last("limit 1")
                .one();
        return o == null ? null : userOrderService.orderByNo(o.getOrderNo());
    }

    /**
     * Apple 退款通知 (REFUND / REVOKE) 回调,扣回积分并标记 REFUNDED。
     * 幂等:已 REFUNDED 直接返回。
     */
    @Transactional
    public Map<String, Object> refundByStore(String storeTransactionId, String refundReason) {
        UserOrder order = userOrderService.lambdaQuery()
                .and(w -> w.eq(UserOrder::getStoreTransactionId, storeTransactionId)
                        .or().eq(UserOrder::getStoreOriginalTransactionId, storeTransactionId))
                .last("limit 1")
                .one();
        if (order == null) {
            throw new IllegalArgumentException("order not found for storeTransactionId: " + storeTransactionId);
        }
        if ("REFUNDED".equals(order.getStatus())) {
            return userOrderService.orderByNo(order.getOrderNo());
        }
        if (!"PAID".equals(order.getStatus())) {
            throw new IllegalArgumentException("only paid orders can be refunded, current: " + order.getStatus());
        }

        // 退款资格校验 (R12: 7天内+观看≤10集)
        Map<String, Object> orderMap = userOrderService.orderByNo(order.getOrderNo());
        validateRefundEligibility(orderMap);

        int points = order.getPoints() == null ? 0 : order.getPoints();
        int amountCents = order.getAmountCents() == null ? 0 : order.getAmountCents();

        // 先条件更新订单状态，确保并发安全：只有 PAID → REFUNDED 成功才继续扣积分
        boolean updated = userOrderService.lambdaUpdate()
                .set(UserOrder::getStatus, "REFUNDED")
                .set(UserOrder::getRefundReason, refundReason)
                .eq(UserOrder::getOrderNo, order.getOrderNo())
                .eq(UserOrder::getStatus, "PAID")
                .update();
        if (!updated) {
            // 并发竞争：订单已被其他线程退款，直接返回当前状态
            return userOrderService.orderByNo(order.getOrderNo());
        }
        pointService.addPoints(order.getUserId(), -points, "ORDER_REFUND", order.getOrderNo(), refundReason);
        // 会员退款：撤销权益 + 减消费额
        membershipService.onRefund(order.getUserId(), order.getOrderNo(), amountCents, points, "RECHARGE");
        return userOrderService.orderByNo(order.getOrderNo());
    }

    public Map<String, Object> findOrderByStoreTransactionId(String storeTransactionId) {
        UserOrder order = userOrderService.lambdaQuery()
                .and(w -> w.eq(UserOrder::getStoreTransactionId, storeTransactionId)
                        .or().eq(UserOrder::getStoreOriginalTransactionId, storeTransactionId))
                .last("limit 1")
                .one();
        return order == null ? null : userOrderService.orderByNo(order.getOrderNo());
    }

    // --- Stripe 辅助方法 ---

    /** 创建 Stripe Checkout Session 后,把 sessionId 写入 PENDING 订单,供后续 webhook 反查 */
    @Transactional
    public void attachStripeSessionId(String orderNo, String stripeSessionId) {
        if (orderNo == null || stripeSessionId == null) {
            return;
        }
        userOrderService.lambdaUpdate()
                .set(UserOrder::getStripeSessionId, stripeSessionId)
                .eq(UserOrder::getOrderNo, orderNo)
                .eq(UserOrder::getStatus, "PENDING")
                .update();
    }

    /** checkout.session.completed / payment_intent.succeeded 按 stripe_session_id 反查订单 */
    public Map<String, Object> findOrderByStripeSessionId(String stripeSessionId) {
        if (stripeSessionId == null || stripeSessionId.isBlank()) {
            return null;
        }
        UserOrder order = userOrderService.lambdaQuery()
                .eq(UserOrder::getStripeSessionId, stripeSessionId)
                .last("limit 1")
                .one();
        return order == null ? null : userOrderService.orderByNo(order.getOrderNo());
    }

    /** 幂等:按 stripe_payment_intent_id 查已落单订单 (uk_order_stripe_pi 唯一索引保证唯一) */
    public Map<String, Object> findOrderByStripePaymentIntent(String paymentIntentId) {
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            return null;
        }
        UserOrder order = userOrderService.lambdaQuery()
                .eq(UserOrder::getStripePaymentIntentId, paymentIntentId)
                .last("limit 1")
                .one();
        return order == null ? null : userOrderService.orderByNo(order.getOrderNo());
    }

    /** payment_intent.payment_failed / checkout.session.async_payment_failed:
     *  查找对应 PENDING 订单,标记为 CANCELLED,防止僵尸订单。 */
    @Transactional
    public void cancelPendingByStripePaymentIntent(String paymentIntentId) {
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            return;
        }
        // 先按 stripe_payment_intent_id 查
        UserOrder order = userOrderService.lambdaQuery()
                .eq(UserOrder::getStripePaymentIntentId, paymentIntentId)
                .last("limit 1")
                .one();
        if (order == null) {
            // 兜底:按 store_original_transaction_id 查 (payment_intent 作为 original_tx)
            order = userOrderService.lambdaQuery()
                    .eq(UserOrder::getStoreOriginalTransactionId, paymentIntentId)
                    .last("limit 1")
                    .one();
        }
        if (order == null) {
            return;
        }
        if (!"PENDING".equals(order.getStatus())) {
            return;
        }
        userOrderService.lambdaUpdate()
                .set(UserOrder::getStatus, "CANCELLED")
                .eq(UserOrder::getId, order.getId())
                .eq(UserOrder::getStatus, "PENDING")
                .update();
    }

    /** 创建 PayPal 订单后,把 paypalOrderId 写入 PENDING 订单,供后续 webhook 反查 */
    @Transactional
    public void attachPayPalOrderId(String orderNo, String paypalOrderId) {
        if (orderNo == null || paypalOrderId == null) {
            return;
        }
        userOrderService.lambdaUpdate()
                .set(UserOrder::getPaypalOrderId, paypalOrderId)
                .eq(UserOrder::getOrderNo, orderNo)
                .eq(UserOrder::getStatus, "PENDING")
                .update();
    }

    /** 幂等:按 paypal_payment_id 查已落单订单 (uk_order_paypal_payment_id 唯一索引保证唯一) */
    public Map<String, Object> findOrderByPayPalPaymentId(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            return null;
        }
        UserOrder order = userOrderService.lambdaQuery()
                .eq(UserOrder::getPaypalPaymentId, paymentId)
                .last("limit 1")
                .one();
        return order == null ? null : userOrderService.orderByNo(order.getOrderNo());
    }

    /** PAYMENT.CAPTURE.DECLINED / DENIED:
     *  查找对应 PENDING 订单,标记为 CANCELLED,防止僵尸订单。 */
    @Transactional
    public void cancelPendingByPayPalPaymentId(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            return;
        }
        // 先按 paypal_payment_id 查
        UserOrder order = userOrderService.lambdaQuery()
                .eq(UserOrder::getPaypalPaymentId, paymentId)
                .last("limit 1")
                .one();
        if (order == null) {
            // 兜底:按 store_transaction_id / store_original_transaction_id 查
            order = userOrderService.lambdaQuery()
                    .and(w -> w.eq(UserOrder::getStoreTransactionId, paymentId)
                            .or().eq(UserOrder::getStoreOriginalTransactionId, paymentId))
                    .last("limit 1")
                    .one();
        }
        if (order == null) {
            return;
        }
        if (!"PENDING".equals(order.getStatus())) {
            return;
        }
        userOrderService.lambdaUpdate()
                .set(UserOrder::getStatus, "CANCELLED")
                .eq(UserOrder::getId, order.getId())
                .eq(UserOrder::getStatus, "PENDING")
                .update();
    }

    // --- Admin orders ---

    public List<Map<String, Object>> getAdminOrders(String keyword, String status, int limit) {
        return userOrderService.adminOrders(keyword, status, Math.max(1, Math.min(limit, 500)));
    }

    /**
     * 关闭超时未支付的 PENDING 订单,防止僵尸订单堆积。
     * 由定时任务每 5 分钟调用,关闭创建超过 30 分钟仍未支付的订单。
     * 条件更新 (where status=PENDING) 确保并发安全,不会误关正在被 webhook 处理的订单。
     *
     * @param expireMinutes 订单过期阈值(分钟)
     * @return 关闭的订单数量
     */
    @Transactional
    public int cancelExpiredPendingOrders(int expireMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(expireMinutes);
        // 先查出待关闭的订单号用于日志记录 (lambdaUpdate 不直接返回受影响行数)
        List<UserOrder> expired = userOrderService.lambdaQuery()
                .eq(UserOrder::getStatus, "PENDING")
                .lt(UserOrder::getCreatedAt, cutoff)
                .list();
        if (expired.isEmpty()) {
            return 0;
        }
        for (UserOrder order : expired) {
            userOrderService.lambdaUpdate()
                    .set(UserOrder::getStatus, "CANCELLED")
                    .eq(UserOrder::getId, order.getId())
                    .eq(UserOrder::getStatus, "PENDING")
                    .update();
        }
        log.info("cancelExpiredPendingOrders: closed {} orders (older than {} minutes)", expired.size(), expireMinutes);
        return expired.size();
    }

    @Transactional
    public Map<String, Object> markPaid(String orderNo) {
        Map<String, Object> order = mustOrder(orderNo);
        if ("PAID".equals(MapUtil.str(order, "status"))) {
            return order;
        }
        if (!"PENDING".equals(MapUtil.str(order, "status"))) {
            throw new IllegalArgumentException("only pending orders can be paid");
        }
        boolean updated = userOrderService.lambdaUpdate()
                .set(UserOrder::getStatus, "PAID")
                .set(UserOrder::getPaidAt, LocalDateTime.now())
                .eq(UserOrder::getOrderNo, orderNo)
                .eq(UserOrder::getStatus, "PENDING")
                .update();
        if (!updated) {
            return userOrderService.orderByNo(orderNo);
        }
        Long userId = MapUtil.lng(order, "user_id");
        int points = MapUtil.integer(order, "points");
        pointService.addPoints(userId, points, "ADMIN_ORDER_PAY", orderNo, "admin marked order paid");
        // VIP类商品：激活会员 + 更新累计消费
        Long productId = MapUtil.lng(order, "product_id");
        PointProduct product = pointProductService.getById(productId);
        if (product != null) {
            onPaymentSuccess(userId, product, orderNo);
        }
        return userOrderService.orderByNo(orderNo);
    }

    @Transactional
    public Map<String, Object> refund(String orderNo) {
        Map<String, Object> order = mustOrder(orderNo);
        if ("REFUNDED".equals(MapUtil.str(order, "status"))) {
            return order;
        }
        if (!"PAID".equals(MapUtil.str(order, "status"))) {
            throw new IllegalArgumentException("only paid orders can be refunded");
        }
        Long userId = MapUtil.lng(order, "user_id");
        int points = MapUtil.integer(order, "points");
        int amountCents = MapUtil.integer(order, "amount_cents");

        // 退款资格校验 (R12: 7天内+观看≤10集)
        validateRefundEligibility(order);

        // 先条件更新订单状态，确保并发安全：只有 PAID → REFUNDED 成功才继续扣积分
        boolean updated = userOrderService.lambdaUpdate()
                .set(UserOrder::getStatus, "REFUNDED")
                .eq(UserOrder::getOrderNo, orderNo)
                .eq(UserOrder::getStatus, "PAID")
                .update();
        if (!updated) {
            // 并发竞争：订单已被其他线程退款，直接返回当前状态
            return userOrderService.orderByNo(orderNo);
        }
        pointService.addPoints(userId, -points, "ORDER_REFUND", orderNo, "order refunded");
        // 会员退款：撤销权益 + 减消费额
        membershipService.onRefund(userId, orderNo, amountCents, points, "RECHARGE");
        return userOrderService.orderByNo(orderNo);
    }


    /**
     * 用户软删除自己的终态订单 (CANCELLED / CLOSED / REFUNDED)。
     * 涉及支付的订单 (PENDING / PAID) 不允许删除,避免影响财务对账。
     * 已删除订单对用户侧查询不可见,但管理员仍可查看。
     */
    public void deleteOrder(String orderNo) {
        UserOrder order = userOrderService.lambdaQuery()
                .eq(UserOrder::getOrderNo, orderNo)
                .last("limit 1")
                .one();
        if (order == null) {
            throw new IllegalArgumentException("order not found");
        }
        if (!PrincipalHolder.userId().equals(order.getUserId())) {
            throw new IllegalArgumentException("order not found");
        }
        if (order.getDeletedAt() != null) {
            return;
        }
        String s = order.getStatus();
        if ("PENDING".equals(s)) {
            throw new IllegalArgumentException("待支付订单不支持删除,请先完成支付或等待自动取消");
        }
        if ("PAID".equals(s)) {
            throw new IllegalArgumentException("已支付订单不支持删除");
        }
        // 终态订单 (CANCELLED / CLOSED / REFUNDED) 允许软删除
        userOrderService.lambdaUpdate()
                .set(UserOrder::getDeletedAt, LocalDateTime.now())
                .eq(UserOrder::getId, order.getId())
                .update();
    }
    public Map<String, Object> updateStatus(String orderNo, String status) {
        if (!List.of("PENDING", "CANCELLED", "CLOSED").contains(status)) {
            throw new IllegalArgumentException("invalid status");
        }
        userOrderService.lambdaUpdate()
                .set(UserOrder::getStatus, status)
                .eq(UserOrder::getOrderNo, orderNo)
                .ne(UserOrder::getStatus, "PAID")
                .update();
        return userOrderService.orderByNo(orderNo);
    }

    // --- Payment Success Helpers ---

    /** 支付成功后统一处理：VIP激活 + 累计消费更新 + 购买限制计数器 */
    private void onPaymentSuccess(Long userId, PointProduct product, String orderNo) {
        String productCategory = product.getProductCategory();
        // VIP类商品：激活会员
        if ("VIP".equals(productCategory)) {
            vipService.activateVip(userId, product.getId(), orderNo);
        }
        // 更新累计消费
        membershipService.onPaymentSuccess(userId, product.getPriceCents(), productCategory);
        // 记录购买限制计数器
        recordPurchaseLimitCounters(userId, product);
    }

    // --- Purchase Limit Validation ---

    /** 购买限制校验 (R13, R14, R23) */
    private void validatePurchaseLimit(Long userId, PointProduct product) {
        int priceCents = product.getPriceCents() == null ? 0 : product.getPriceCents();
        Long productId = product.getId();
        LocalDate today = LocalDate.now();
        YearMonth thisMonth = YearMonth.from(today);

        // 1. 商品每日限购
        Integer dailyLimit = product.getDailyLimit();
        if (dailyLimit != null && dailyLimit > 0) {
            String dailyKey = "purchase:daily:" + userId + ":" + productId + ":" + today;
            String countStr = redisTemplate.opsForValue().get(dailyKey);
            int dailyCount = countStr == null ? 0 : Integer.parseInt(countStr);
            if (dailyCount >= dailyLimit) {
                throw new IllegalArgumentException("该商品今日购买次数已达上限 (" + dailyLimit + " 次)");
            }
        }

        // 2. 商品每月限购
        Integer monthlyLimit = product.getMonthlyLimit();
        if (monthlyLimit != null && monthlyLimit > 0) {
            String monthlyKey = "purchase:monthly:" + userId + ":" + productId + ":" + thisMonth;
            String countStr = redisTemplate.opsForValue().get(monthlyKey);
            int monthlyCount = countStr == null ? 0 : Integer.parseInt(countStr);
            if (monthlyCount >= monthlyLimit) {
                throw new IllegalArgumentException("该商品本月购买次数已达上限 (" + monthlyLimit + " 次)");
            }
        }

        // 3. 全局月度上限 $5000
        String globalKey = "purchase:global:" + userId + ":" + thisMonth;
        String globalStr = redisTemplate.opsForValue().get(globalKey);
        int globalSpent = globalStr == null ? 0 : Integer.parseInt(globalStr);
        if (globalSpent + priceCents > GLOBAL_MONTHLY_CAP_CENTS) {
            throw new IllegalArgumentException("月度消费已达上限 $5000");
        }

        // 4. 新用户限制：从未购买过的用户限制价格 <= $4.99，每日限购1次
        boolean hasPaidOrders = userOrderService.lambdaQuery()
                .eq(UserOrder::getUserId, userId)
                .eq(UserOrder::getStatus, "PAID")
                .exists();
        if (!hasPaidOrders) {
            if (priceCents > NEW_USER_MAX_PRICE_CENTS) {
                throw new IllegalArgumentException("新用户仅可购买 $4.99 及以下套餐");
            }
            String newUserDailyKey = "purchase:new_user_daily:" + userId + ":" + today;
            String newUserCountStr = redisTemplate.opsForValue().get(newUserDailyKey);
            int newUserDailyCount = newUserCountStr == null ? 0 : Integer.parseInt(newUserCountStr);
            if (newUserDailyCount >= NEW_USER_DAILY_LIMIT) {
                throw new IllegalArgumentException("新用户每日限购 " + NEW_USER_DAILY_LIMIT + " 次");
            }
        }
    }

    /** 记录购买成功后更新限购计数器 */
    private void recordPurchaseLimitCounters(Long userId, PointProduct product) {
        Long productId = product.getId();
        LocalDate today = LocalDate.now();
        YearMonth thisMonth = YearMonth.from(today);
        int priceCents = product.getPriceCents() == null ? 0 : product.getPriceCents();

        // 每日计数
        String dailyKey = "purchase:daily:" + userId + ":" + productId + ":" + today;
        Long newDaily = redisTemplate.opsForValue().increment(dailyKey);
        if (newDaily != null && newDaily == 1L) {
            redisTemplate.expire(dailyKey, Duration.ofDays(1));
        }

        // 每月计数
        String monthlyKey = "purchase:monthly:" + userId + ":" + productId + ":" + thisMonth;
        Long newMonthly = redisTemplate.opsForValue().increment(monthlyKey);
        if (newMonthly != null && newMonthly == 1L) {
            redisTemplate.expire(monthlyKey, Duration.ofDays(32));
        }

        // 全局月度金额
        String globalKey = "purchase:global:" + userId + ":" + thisMonth;
        Long newGlobal = redisTemplate.opsForValue().increment(globalKey, priceCents);
        if (newGlobal != null && newGlobal == (long) priceCents) {
            redisTemplate.expire(globalKey, Duration.ofDays(32));
        }

        // 新用户每日计数
        String newUserDailyKey = "purchase:new_user_daily:" + userId + ":" + today;
        redisTemplate.opsForValue().increment(newUserDailyKey);
        if (redisTemplate.getExpire(newUserDailyKey) < 0) {
            redisTemplate.expire(newUserDailyKey, Duration.ofDays(1));
        }
    }

    // --- First Purchase Bonus ---

    /** 首充奖励计算 (R7) */
    private int calculateFirstPurchaseBonus(Long userId, PointProduct product) {
        boolean hasPaidRecharge = userOrderService.lambdaQuery()
                .eq(UserOrder::getUserId, userId)
                .eq(UserOrder::getStatus, "PAID")
                .eq(UserOrder::getOrderType, "RECHARGE")
                .exists();
        if (hasPaidRecharge) {
            return 0;
        }
        Integer bonus = product.getFirstPurchaseBonus();
        return bonus != null ? bonus : 0;
    }

    // --- Level Preview ---

    /** 等级变化预告 */
    private Map<String, Object> calculateLevelPreview(Long userId, int priceCents) {
        String currentLevel = membershipService.getCurrentLevel(userId);
        int totalSpent = 0;
        try {
            Map<String, Object> info = membershipService.getMembershipInfo(userId);
            @SuppressWarnings("unchecked")
            Map<String, Object> spendProgress = (Map<String, Object>) info.get("spend_progress");
            if (spendProgress != null) {
                Object totalObj = spendProgress.get("total_spent_cents");
                totalSpent = totalObj instanceof Integer ? (Integer) totalObj : 0;
            }
        } catch (Exception e) {
            totalSpent = 0;
        }

        int newTotal = totalSpent + priceCents;
        String newLevel = getSpendLevel(newTotal);
        boolean willUpgrade = !newLevel.equals(currentLevel)
                && compareLevel(newLevel, currentLevel) > 0;

        int nextThreshold;
        String nextLevelName;
        int remaining;

        if (LEVEL_DIAMOND.equals(newLevel)) {
            nextThreshold = DIAMOND_THRESHOLD;
            nextLevelName = null;
            remaining = 0;
        } else if (LEVEL_GOLD.equals(newLevel)) {
            nextThreshold = DIAMOND_THRESHOLD;
            nextLevelName = "DIAMOND";
            remaining = Math.max(0, nextThreshold - newTotal);
        } else if (LEVEL_SILVER.equals(newLevel)) {
            nextThreshold = GOLD_THRESHOLD;
            nextLevelName = "GOLD";
            remaining = Math.max(0, nextThreshold - newTotal);
        } else {
            nextThreshold = SILVER_THRESHOLD;
            nextLevelName = "SILVER";
            remaining = Math.max(0, nextThreshold - newTotal);
        }

        return MapUtil.map(
                "current_level", currentLevel,
                "current_total_spent", totalSpent,
                "new_total_spent", newTotal,
                "new_level", newLevel,
                "will_upgrade", willUpgrade,
                "next_level", nextLevelName,
                "remaining_cents", remaining
        );
    }

    private static String getSpendLevel(int totalSpentCents) {
        if (totalSpentCents >= DIAMOND_THRESHOLD) return "DIAMOND";
        if (totalSpentCents >= GOLD_THRESHOLD) return "GOLD";
        if (totalSpentCents >= SILVER_THRESHOLD) return "SILVER";
        return "NONE";
    }

    private static int compareLevel(String a, String b) {
        return Integer.compare(levelRank(a), levelRank(b));
    }

    private static int levelRank(String level) {
        if (level == null) return 0;
        return switch (level) {
            case "DIAMOND" -> 4;
            case "GOLD" -> 3;
            case "SILVER" -> 2;
            case "NONE" -> 1;
            default -> 0;
        };
    }

    // --- Refund Validation ---

    /** 退款资格校验 (R12: 7天内+观看≤10集) */
    private void validateRefundEligibility(Map<String, Object> order) {
        String createdAtStr = MapUtil.str(order, "created_at");
        if (createdAtStr == null) {
            return; // 无法判断创建时间，放行
        }
        LocalDateTime createdAt = LocalDateTime.parse(createdAtStr.replace("T", " ").substring(0, 19),
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        long daysSinceCreate = Duration.between(createdAt, LocalDateTime.now()).toDays();

        if (daysSinceCreate > REFUND_MAX_DAYS) {
            throw new IllegalArgumentException("购买已超过 " + REFUND_MAX_DAYS + " 天，不支持退款");
        }

        Long userId = MapUtil.lng(order, "user_id");
        if (userId != null) {
            long unlockCount = userEpisodeUnlockService.lambdaQuery()
                    .eq(UserEpisodeUnlock::getUserId, userId)
                    .gt(UserEpisodeUnlock::getCreatedAt, createdAt)
                    .count();
            if (unlockCount >= REFUND_MAX_UNLOCKS) {
                throw new IllegalArgumentException("已观看解锁超过 " + REFUND_MAX_UNLOCKS + " 集，不支持退款");
            }
        }
    }

    // --- Private helpers ---

    private Map<String, Object> ownOrder(String orderNo) {
        Map<String, Object> order = userOrderService.orderByNo(orderNo);
        if (order == null || !PrincipalHolder.userId().equals(MapUtil.lng(order, "user_id"))) {
            throw new IllegalArgumentException("order not found");
        }
        return order;
    }

    public Map<String, Object> mustOrder(String orderNo) {
        Map<String, Object> order = userOrderService.orderByNo(orderNo);
        if (order == null) {
            throw new IllegalArgumentException("order not found");
        }
        return order;
    }

    /** 传统可读订单号格式 DJO+yyyyMMddHHmmss+6位随机,用于 mock/后台人工下单 */
    private String orderNo() {
        String time = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000, 1000000);
        return "DJO" + time + random;
    }

    /** UUID 订单号:Apple appAccountToken、Google obfuscatedAccountId、Stripe client_reference_id 均要求 UUID 承载 */
    private String orderNoUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 管理员支付渠道状态监控。
     * 返回各渠道启用状态、最近 24 小时 Webhook 事件处理统计 (成功/失败)。
     *
     * <p>返回结构:</p>
     * <pre>
     * {
     *   "channels": [
     *     { "channel": "STRIPE", "enabled": true, lastSuccessAt: "...", stats24h: { success: 12, failed: 0 } },
     *     ...
     *   ]
     * }
     * </pre>
     */
    public Map<String, Object> getPaymentChannelsStatus() {
        List<Map<String, Object>> channels = List.of(
                channelStatus(StorePaymentContext.CHANNEL_STRIPE, stripePaymentService.isEnabled()),
                channelStatus(StorePaymentContext.CHANNEL_PAYPAL, payPalPaymentService.isEnabled()),
                channelStatus(StorePaymentContext.CHANNEL_APPLE_IAP, appleIapService.isEnabled()),
                channelStatus(StorePaymentContext.CHANNEL_GOOGLE_PLAY, googlePlayIapService.isEnabled())
        );
        return MapUtil.map("channels", channels);
    }

    private Map<String, Object> channelStatus(String channel, boolean enabled) {
        Map<String, Object> m = MapUtil.map(
                "channel", channel,
                "enabled", enabled
        );
        try {
            LocalDateTime since = LocalDateTime.now().minusHours(24);
            List<Map<String, Object>> rows = userOrderService.listMapsByChannelAndCreatedAt(channel, since);
            int paidCount = 0;
            int pendingCount = 0;
            int refundedCount = 0;
            for (Map<String, Object> row : rows) {
                String s = MapUtil.str(row, "status");
                if ("PAID".equals(s)) paidCount++;
                else if ("PENDING".equals(s)) pendingCount++;
                else if ("REFUNDED".equals(s)) refundedCount++;
            }
            m.put("orders24h", MapUtil.map(
                    "total", rows.size(),
                    "paid", paidCount,
                    "pending", pendingCount,
                    "refunded", refundedCount
            ));
        } catch (Exception ignore) {
        }
        return m;
    }
}
