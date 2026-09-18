package com.duanju.service;

import com.duanju.dto.payment.StorePaymentContext;
import com.duanju.entity.PointProduct;
import com.duanju.entity.UserOrder;
import com.duanju.service.entity.PointProductService;
import com.duanju.service.entity.UserOrderService;
import com.duanju.util.MapUtil;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Stripe Web 支付 (Checkout Sessions) 服务。
 *
 * <p>核心流程:</p>
 * <ol>
 *   <li>客户端 POST /api/user/orders 创建 STRIPE 渠道的 PENDING 订单,拿到 orderNo (UUID)。</li>
 *   <li>客户端再 POST /api/user/orders/stripe-checkout 创建 Stripe Checkout Session,
 *       服务端把 stripeSessionId 写回 PENDING 订单,并返回 session_url 让前端跳转。</li>
 *   <li>用户在 Stripe 页面完成支付后,Stripe 推送 checkout.session.completed +
 *       payment_intent.succeeded 到 /api/webhooks/stripe/notify。</li>
 *   <li>服务端验 Stripe-Signature header → 取 payment_intent + checkout_session_id →
 *       反查订单 → 调用 orderService.markPaidByStore 发放积分。</li>
 *   <li>退款:charge.refunded 事件或后台手动调退款接口,扣回积分并标记订单 REFUNDED。</li>
 * </ol>
 *
 * <p>幂等:user_order.stripe_payment_intent_id 有 uk_order_stripe_pi 唯一索引,
 * 同一 pi_ 绝不会被重复处理。</p>
 *
 * <p>密钥管理:secret-key / webhook-secret 全部走环境变量注入,application.yml 仅保留占位符。</p>
 */
@Service
public class StripePaymentService {
    private static final Logger log = LoggerFactory.getLogger(StripePaymentService.class);

    private final OrderService orderService;
    private final PointProductService pointProductService;
    private final UserOrderService userOrderService;
    private final PaymentEventLogService paymentEventLogService;

    @Autowired
    @Lazy
    private StripePaymentService self;

    @Value("${duanju.stripe.enabled:false}")
    private boolean enabled;

    @Value("${duanju.stripe.secret-key:}")
    private String secretKey;

    @Value("${duanju.stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${duanju.stripe.success-url:}")
    private String successUrl;

    @Value("${duanju.stripe.cancel-url:}")
    private String cancelUrl;

    public StripePaymentService(OrderService orderService, PointProductService pointProductService,
                                UserOrderService userOrderService,
                                PaymentEventLogService paymentEventLogService) {
        this.orderService = orderService;
        this.pointProductService = pointProductService;
        this.userOrderService = userOrderService;
        this.paymentEventLogService = paymentEventLogService;
    }

    @PostConstruct
    void init() {
        if (!enabled) {
            log.warn("Stripe disabled, stripe endpoints will reject requests");
            return;
        }
        if (secretKey == null || secretKey.isBlank()) {
            log.error("Stripe secret-key not configured (env STRIPE_SECRET_KEY), stripe disabled");
            enabled = false;
            return;
        }
        Stripe.apiKey = secretKey;
        log.info("Stripe initialized: successUrl={}, cancelUrl={}", successUrl, cancelUrl);
    }

    // --- Public APIs ---

    public boolean isEnabled() {
        return enabled;
    }

    /** 关闭 Stripe Checkout Session,防止用户继续支付。
     * 用 RestTemplate 直接调 Stripe REST API,绕开 Java SDK 版本兼容问题
     * (SDK 静态方法签名在 26.x → 33.x 频繁变更,但 REST API 永远稳定)。 */
    public void expireCheckoutSession(String sessionId) {
        if (!enabled || sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            RestTemplate rt = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth("", secretKey);  // Stripe REST API: Bearer token = Basic auth with empty username
            // 其实直接拼 Authorization header 更直观
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            String url = "https://api.stripe.com/v1/checkout/sessions/" + sessionId + "/expire";
            ResponseEntity<String> resp = rt.exchange(url, HttpMethod.POST, entity, String.class);

            if (resp.getStatusCode().is2xxSuccessful()) {
                log.info("Stripe expireCheckoutSession: ok, sessionId={}, status=expired", sessionId);
            } else {
                log.warn("Stripe expireCheckoutSession: non-2xx, sessionId={}, status={}",
                        sessionId, resp.getStatusCode());
            }
        } catch (Exception ex) {
            String msg = ex.getMessage();
            // 404 resource_missing: session 已不存在,不算错误
            if (msg != null && msg.contains("404")) {
                log.info("Stripe expireCheckoutSession: session already gone (404), sessionId={}", sessionId);
            } else {
                throw new IllegalStateException("failed to expire Stripe session: " + msg, ex);
            }
        }
    }

    /**
     * 为已创建的 PENDING 订单创建 Stripe Checkout Session,返回 session_url 让前端跳转。
     * 同时把 stripeSessionId 写回 PENDING 订单,用于 webhook 反查。
     *
     * @param orderNo 已创建的 PENDING 订单号 (STRIPE 渠道)
     * @return { "sessionId": "cs_xxx", "sessionUrl": "https://checkout.stripe.com/..." }
     */
    @Transactional
    public Map<String, Object> createCheckoutSession(String orderNo) {
        assertEnabled();
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("orderNo required");
        }
        UserOrder pending = userOrderService.lambdaQuery()
                .eq(UserOrder::getOrderNo, orderNo)
                .eq(UserOrder::getStatus, "PENDING")
                .last("limit 1")
                .one();
        if (pending == null) {
            throw new IllegalArgumentException("pending order not found: " + orderNo);
        }
        PointProduct product = pointProductService.getById(pending.getProductId());
        if (product == null) {
            throw new IllegalArgumentException("product not found for order " + orderNo);
        }

        String currency = (pending.getCurrency() != null ? pending.getCurrency() : "USD").toLowerCase();
        long amountCents = pending.getAmountCents() != null && pending.getAmountCents() > 0
                ? pending.getAmountCents() : (product.getPriceCents() != null ? product.getPriceCents() : 0);
        if (amountCents <= 0) {
            throw new IllegalArgumentException("order amount must be positive");
        }

        // 1. 使用 point_product.store_product_id 作为 Stripe Price ID (必须在 Stripe Dashboard 预创建)。
        //    安全要求:禁止动态创建 Price,避免被滥用创建非预期金额的商品。
        String stripePriceId = product.getStoreProductId();
        if (stripePriceId == null || stripePriceId.isBlank() || !stripePriceId.startsWith("price_")) {
            log.error("Stripe: product.storeProductId is not a valid Stripe Price ID, productId={}, storeProductId={}",
                    product.getId(), stripePriceId);
            throw new IllegalStateException(
                    "product storeProductId must be a pre-created Stripe Price ID (starting with 'price_'), "
                            + "productId=" + product.getId());
        }
        SessionCreateParams.LineItem lineItem = SessionCreateParams.LineItem.builder()
                .setPrice(stripePriceId)
                .setQuantity(1L)
                .build();
        log.info("Stripe: using pre-created Price={} for productId={}", stripePriceId, product.getId());

        // 2. 创建 Checkout Session
        //    关键:metadata 加在 Session 上不会自动继承到 PaymentIntent,
        //    必须显式用 PaymentIntentData 把 metadata 透传到 PI,
        //    否则 payment_intent.succeeded webhook 里 pi.getMetadata() 为 null,订单无法入账
        Map<String, String> sessionMeta = Map.of(
                "orderNo", orderNo,
                "productId", String.valueOf(product.getId()),
                "userId", String.valueOf(pending.getUserId())
        );
        SessionCreateParams.PaymentIntentData piData = SessionCreateParams.PaymentIntentData.builder()
                .setCaptureMethod(SessionCreateParams.PaymentIntentData.CaptureMethod.AUTOMATIC)
                .putAllMetadata(sessionMeta)
                .build();
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(buildRedirectUrl(successUrl, orderNo, true))
                .setCancelUrl(buildRedirectUrl(cancelUrl, orderNo, false))
                .setClientReferenceId(orderNo)
                .putAllMetadata(sessionMeta)
                .setPaymentIntentData(piData)
                .addLineItem(lineItem)
                .build();
        Session session;
        try {
            session = Session.create(params);
        } catch (StripeException ex) {
            log.error("Stripe create Session failed: orderNo={}", orderNo, ex);
            throw new IllegalStateException("stripe checkout session create failed: " + ex.getMessage());
        }

        // 3. 写 stripeSessionId 回 PENDING 订单,供 webhook 反查
        orderService.attachStripeSessionId(orderNo, session.getId());

        log.info("Stripe createCheckoutSession: ok, orderNo={}, sessionId={}, amountCents={}, currency={}",
                orderNo, session.getId(), amountCents, currency);
        Map<String, Object> result = new HashMap<>();
        result.put("session_id", session.getId());
        result.put("session_url", session.getUrl());
        result.put("order_no", orderNo);
        result.put("amount_cents", amountCents);
        result.put("currency", currency);
        return result;
    }

    /**
     * 处理 Stripe Webhook Event:签名校验 + 分发 + 幂等落单。
     *
     * <p>关键事件:</p>
     * <ul>
     *   <li>checkout.session.completed → 拿到 client_reference_id(orderNo) + payment_intent,准备落单</li>
     *   <li>payment_intent.succeeded → 实际确认支付成功,这里真正落单并发放积分 (选这个事件做主确认点更可靠)</li>
     *   <li>charge.refunded → 扣回积分 + 标记 REFUNDED</li>
     * </ul>
     *
     * <p>任何业务异常内部吞掉返回 200,避免 Stripe 无意义重试 (幂等保证不会重复发积分)。</p>
     */
    public void handleWebhook(String payload, String stripeSignatureHeader) {
        if (!enabled) {
            log.info("Stripe webhook received but stripe not enabled, ignoring (returning 200)");
            return;
        }
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("stripe webhook-secret not configured");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, stripeSignatureHeader, webhookSecret);
        } catch (SignatureVerificationException ex) {
            log.warn("Stripe webhook signature verification failed: {}", ex.getMessage());
            throw new IllegalArgumentException("invalid stripe signature");
        }
        String eventId = event.getId();
        String eventType = event.getType();
        log.info("Stripe webhook: eventId={}, type={}, apiVersion={}", eventId, eventType, event.getApiVersion());
        // 事件去重:同一 eventId 只处理一次 (uk_channel_event 唯一索引保证)
        if (!paymentEventLogService.tryLogEvent("STRIPE", eventId, eventType)) {
            log.info("Stripe webhook: event already processed, skipping eventId={}", eventId);
            return;
        }
        try {
            switch (eventType) {
                case "checkout.session.completed" -> onCheckoutSessionCompleted(event);
                case "payment_intent.succeeded" -> self.onPaymentIntentSucceeded(event);
                case "payment_intent.payment_failed" -> self.onPaymentIntentFailed(event);
                case "charge.refunded" -> self.onChargeRefunded(event);
                case "checkout.session.async_payment_succeeded" -> self.onAsyncPaymentSucceeded(event);
                case "checkout.session.async_payment_failed" -> self.onAsyncPaymentFailed(event);
                default -> log.info("Stripe webhook: ignoring event type {}", eventType);
            }
            paymentEventLogService.updateResult("STRIPE", eventId, "SUCCESS", null);
        } catch (RuntimeException ex) {
            // 永远 200,不让 Stripe 重试造成重复发放
            log.error("Stripe webhook handling failed: eventId={}, type={}", eventId, eventType, ex);
            paymentEventLogService.updateResult("STRIPE", eventId, "FAILED", null);
        }
    }

    // --- Event handlers ---

    /** checkout.session.completed:仅做信息记录,真正发积分在 payment_intent.succeeded
     *  (避免 checkout.session.completed 到达时 payment_intent 还没成功的竞态)。 */
    private void onCheckoutSessionCompleted(Event event) {
        Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
        if (session == null) {
            log.warn("Stripe checkout.session.completed: cannot deserialize Session, eventId={}", event.getId());
            return;
        }
        log.info("Stripe checkout.session.completed: sessionId={}, orderNo={}, paymentIntent={}",
                session.getId(), session.getClientReferenceId(), session.getPaymentIntent());
        // client_reference_id 就是 orderNo,此时订单仍是 PENDING,不做操作
    }

    /** payment_intent.succeeded:真正的支付成功确认点,落单 + 发积分。 */
    @Transactional
    public void onPaymentIntentSucceeded(Event event) {
        PaymentIntent pi = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
        if (pi == null) {
            log.warn("Stripe payment_intent.succeeded: cannot deserialize PaymentIntent, eventId={}", event.getId());
            return;
        }
        String paymentIntentId = pi.getId();
        String chargeId = pi.getLatestCharge() != null ? pi.getLatestCharge() : pi.getId();

        // 1. 幂等:pi_ 已处理则直接返回
        if (orderService.findOrderByStripePaymentIntent(paymentIntentId) != null) {
            log.info("Stripe payment_intent.succeeded: idempotent (already processed), pi={}", paymentIntentId);
            return;
        }

        // 2. 解析 orderNo / userId / productId
        String orderNo = pi.getMetadata() != null ? pi.getMetadata().get("orderNo") : null;
        String productIdStr = pi.getMetadata() != null ? pi.getMetadata().get("productId") : null;
        String userIdStr = pi.getMetadata() != null ? pi.getMetadata().get("userId") : null;
        Long userId = userIdStr != null ? Long.parseLong(userIdStr) : null;
        Long productId = productIdStr != null ? Long.parseLong(productIdStr) : null;

        // 3. 兜底:如果 metadata 里没带 (例如 Dashboard 手动创建的 PaymentIntent),
        //    尝试通过 stripe_session_id (uk_order_stripe_pi 还没写入时) 或 client_reference_id(orderNo) 反查
        if (orderNo == null || productId == null || userId == null) {
            // 用 PaymentIntent.charges.data[0].payment_intent 关联 session 的方式这里简化:
            // 如果 metadata 不全就拒绝处理,避免乱落积分 (线上必须走 Checkout Session,它会填全 metadata)
            log.warn("Stripe payment_intent.succeeded: metadata incomplete, pi={}, metadata={}",
                    paymentIntentId, pi.getMetadata());
            if (orderNo != null) {
                // 有 orderNo,通过订单拿缺的字段
                Map<String, Object> pending = orderService.findPendingByOrderNo(orderNo);
                if (pending != null) {
                    if (productId == null) productId = MapUtil.lng(pending, "product_id");
                    if (userId == null) userId = MapUtil.lng(pending, "user_id");
                }
            }
        }
        if (productId == null || userId == null) {
            log.error("Stripe payment_intent.succeeded: cannot resolve productId/userId, pi={}, skip",
                    paymentIntentId);
            return;
        }

        // 4. 币种/金额
        String currency = pi.getCurrency() != null ? pi.getCurrency().toUpperCase() : "USD";
        Integer amountCents = pi.getAmountReceived() != null ? pi.getAmountReceived().intValue()
                : (pi.getAmount() != null ? pi.getAmount().intValue() : null);

        // 4.1 金额校验:实际支付金额必须与订单预期金额一致,防止金额篡改
        if (orderNo != null && amountCents != null) {
            Map<String, Object> pending = orderService.findPendingByOrderNo(orderNo);
            if (pending != null) {
                Integer expectedCents = MapUtil.integer(pending, "amount_cents");
                if (expectedCents != null && expectedCents > 0 && !expectedCents.equals(amountCents)) {
                    log.error("Stripe payment_intent.succeeded: amount mismatch! pi={}, orderNo={}, expected={}cents, actual={}cents, REFUSING to process",
                            paymentIntentId, orderNo, expectedCents, amountCents);
                    return;
                }
            }
        }

        // 5. 调通用 markPaidByStore 落单 + 发积分
        StorePaymentContext ctx = new StorePaymentContext(
                StorePaymentContext.CHANNEL_STRIPE,
                orderNo,
                userId,
                productId,
                chargeId,                         // storeTransactionId = charge_id
                paymentIntentId,                  // storeOriginalTransactionId = payment_intent_id (跨 event 不变)
                productId != null ? productId.toString() : null,
                null,                             // Stripe 没有 bundleId
                pi.getLivemode() != null && pi.getLivemode() ? "LIVE" : "TEST",
                event.getId(),                    // storeReceiptHash = eventId (审计去重)
                0,
                amountCents,
                currency
        );
        orderService.markPaidByStore(ctx);
        log.info("Stripe payment_intent.succeeded: order paid, pi={}, chargeId={}, orderNo={}, userId={}, amountCents={}",
                paymentIntentId, chargeId, orderNo, userId, amountCents);
    }

    /** charge.refunded:统一走 OrderService.refundByStore 扣回积分 + 标记 REFUNDED。
     *  禁止直接操作 user_order 表或 PointService,确保退款逻辑与 Apple/Google 渠道一致。
     *  退款查找兜底:先用 chargeId(store_transaction_id),找不到再用 paymentIntentId(store_original_transaction_id)。 */
    @Transactional
    public void onChargeRefunded(Event event) {
        Charge charge = (Charge) event.getDataObjectDeserializer().getObject().orElse(null);
        if (charge == null) {
            log.warn("Stripe charge.refunded: cannot deserialize Charge, eventId={}", event.getId());
            return;
        }
        String chargeId = charge.getId();
        String paymentIntentId = charge.getPaymentIntent();
        log.info("Stripe charge.refunded: eventId={}, chargeId={}, paymentIntentId={}",
                event.getId(), chargeId, paymentIntentId);
        // 兜底:先按 chargeId 查 (store_transaction_id),找不到再按 paymentIntentId 查
        try {
            orderService.refundByStore(chargeId, "STRIPE_REFUND");
            log.info("Stripe charge.refunded: refund processed via chargeId, chargeId={}", chargeId);
        } catch (IllegalArgumentException ex) {
            if (paymentIntentId != null && !paymentIntentId.equals(chargeId)) {
                log.info("Stripe charge.refunded: chargeId lookup missed, retrying with paymentIntentId={}", paymentIntentId);
                orderService.refundByStore(paymentIntentId, "STRIPE_REFUND");
                log.info("Stripe charge.refunded: refund processed via paymentIntentId, pi={}", paymentIntentId);
            } else {
                throw ex;
            }
        }
    }

    /** payment_intent.payment_failed:支付失败,将关联 PENDING 订单标记为 CANCELLED,
     *  防止僵尸订单永远停在 PENDING 状态。 */
    @Transactional
    public void onPaymentIntentFailed(Event event) {
        PaymentIntent pi = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
        if (pi == null) {
            log.warn("Stripe payment_intent.payment_failed: cannot deserialize PaymentIntent, eventId={}", event.getId());
            return;
        }
        String paymentIntentId = pi.getId();
        String failureMsg = pi.getLastPaymentError() != null ? pi.getLastPaymentError().getMessage() : "unknown";
        log.warn("Stripe payment_intent.payment_failed: pi={}, reason={}", paymentIntentId, failureMsg);
        orderService.cancelPendingByStripePaymentIntent(paymentIntentId);
    }

    /** checkout.session.async_payment_succeeded:异步支付成功 (SEPA/ACH 等),
     *  重新解析 PaymentIntent 并复用落单流程 (幂等保护确保不重复发积分)。 */
    @Transactional
    public void onAsyncPaymentSucceeded(Event event) {
        Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
        if (session == null) {
            log.warn("Stripe async_payment_succeeded: cannot deserialize Session, eventId={}", event.getId());
            return;
        }
        String piId = session.getPaymentIntent();
        log.info("Stripe async_payment_succeeded: sessionId={}, pi={}", session.getId(), piId);
        if (piId == null || piId.isBlank()) {
            return;
        }
        // 幂等:已处理过的 pi_ 直接跳过
        if (orderService.findOrderByStripePaymentIntent(piId) != null) {
            log.info("Stripe async_payment_succeeded: order already processed for pi={}", piId);
            return;
        }
        // 重新解析 PaymentIntent 并直接走落单流程
        try {
            PaymentIntent pi = PaymentIntent.retrieve(piId);
            String paymentIntentId = pi.getId();
            String chargeId = pi.getLatestCharge() != null ? pi.getLatestCharge() : pi.getId();

            // 解析 orderNo / userId / productId
            String orderNo = pi.getMetadata() != null ? pi.getMetadata().get("orderNo") : null;
            String productIdStr = pi.getMetadata() != null ? pi.getMetadata().get("productId") : null;
            String userIdStr = pi.getMetadata() != null ? pi.getMetadata().get("userId") : null;
            Long userId = userIdStr != null ? Long.parseLong(userIdStr) : null;
            Long productId = productIdStr != null ? Long.parseLong(productIdStr) : null;

            if (orderNo == null || productId == null || userId == null) {
                Map<String, Object> pending = orderService.findPendingByOrderNo(orderNo);
                if (pending != null) {
                    if (productId == null) productId = MapUtil.lng(pending, "product_id");
                    if (userId == null) userId = MapUtil.lng(pending, "user_id");
                }
            }
            if (productId == null || userId == null) {
                log.error("Stripe async_payment_succeeded: cannot resolve productId/userId, pi={}, skip", paymentIntentId);
                return;
            }

            String currency = pi.getCurrency() != null ? pi.getCurrency().toUpperCase() : "USD";
            Integer amountCents = pi.getAmountReceived() != null ? pi.getAmountReceived().intValue()
                    : (pi.getAmount() != null ? pi.getAmount().intValue() : null);

            StorePaymentContext ctx = new StorePaymentContext(
                    StorePaymentContext.CHANNEL_STRIPE,
                    orderNo,
                    userId,
                    productId,
                    chargeId,
                    paymentIntentId,
                    productId.toString(),
                    null,
                    pi.getLivemode() != null && pi.getLivemode() ? "LIVE" : "TEST",
                    "evt-async-" + event.getId(),
                    0,
                    amountCents,
                    currency
            );
            orderService.markPaidByStore(ctx);
            log.info("Stripe async_payment_succeeded: order paid, pi={}, chargeId={}, orderNo={}",
                    paymentIntentId, chargeId, orderNo);
        } catch (StripeException ex) {
            log.error("Stripe async_payment_succeeded: failed to retrieve pi={}", piId, ex);
        }
    }

    /** checkout.session.async_payment_failed:异步支付失败 (SEPA/ACH 等),
     *  将关联 PENDING 订单标记为 CANCELLED。 */
    @Transactional
    public void onAsyncPaymentFailed(Event event) {
        Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
        if (session == null) {
            log.warn("Stripe async_payment_failed: cannot deserialize Session, eventId={}", event.getId());
            return;
        }
        String piId = session.getPaymentIntent();
        String orderNo = session.getClientReferenceId();
        log.warn("Stripe async_payment_failed: sessionId={}, pi={}, orderNo={}", session.getId(), piId, orderNo);
        if (piId != null && !piId.isBlank()) {
            orderService.cancelPendingByStripePaymentIntent(piId);
        }
    }

    /** 管理员/后台主动触发退款:先调 Stripe Refund API,成功后统一走 OrderService.refundByStore 更新本地状态。 */
    @Transactional
    public Map<String, Object> refundOrder(String orderNo) {
        assertEnabled();
        Map<String, Object> order = userOrderService.orderByNo(orderNo);
        if (order == null) {
            throw new IllegalArgumentException("order not found: " + orderNo);
        }
        if (!"PAID".equals(MapUtil.str(order, "status"))) {
            throw new IllegalArgumentException("only PAID orders can be refunded");
        }
        String paymentIntentId = MapUtil.str(order, "stripe_payment_intent_id");
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new IllegalArgumentException("order has no stripe_payment_intent_id, cannot refund via stripe");
        }
        // 1. 调 Stripe Refund API (失败则抛异常,不更新本地状态)
        try {
            Refund.create(RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .putMetadata("orderNo", orderNo)
                    .build());
        } catch (StripeException ex) {
            log.error("Stripe refund API failed: orderNo={}, pi={}", orderNo, paymentIntentId, ex);
            throw new IllegalStateException("stripe refund failed: " + ex.getMessage());
        }
        // 2. 统一走 OrderService.refundByStore:扣回积分 + 标记 REFUNDED + 写退款原因
        //    refundByStore 通过 store_original_transaction_id 匹配 paymentIntentId
        try {
            Map<String, Object> result = orderService.refundByStore(paymentIntentId, "STRIPE_MANUAL_REFUND");
            log.info("Stripe manual refund: done, orderNo={}, pi={}", orderNo, paymentIntentId);
            return result;
        } catch (RuntimeException ex) {
            log.error("CRITICAL: Stripe refund succeeded externally but local update failed! "
                    + "Manual reconciliation required. orderNo={}, paymentIntentId={}, error={}",
                    orderNo, paymentIntentId, ex.getMessage(), ex);
            throw ex;
        }
    }

    // --- Helpers ---

    private void assertEnabled() {
        if (!enabled) {
            throw new IllegalStateException("支付渠道 Stripe 暂未开通，请联系管理员或切换其他支付方式");
        }
    }

    private String buildRedirectUrl(String base, String orderNo, boolean success) {
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("stripe " + (success ? "success" : "cancel") + "-url not configured");
        }
        String sep = base.contains("?") ? "&" : "?";
        return base + sep + "orderNo=" + orderNo + "&status=" + (success ? "success" : "cancelled");
    }
}
