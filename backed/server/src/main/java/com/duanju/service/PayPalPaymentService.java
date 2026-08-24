package com.duanju.service;

import com.duanju.dto.payment.StorePaymentContext;
import com.duanju.entity.PointProduct;
import com.duanju.entity.UserOrder;
import com.duanju.service.entity.PointProductService;
import com.duanju.service.entity.UserOrderService;
import com.duanju.util.MapUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * PayPal Web 支付服务。
 *
 * <p>核心流程:</p>
 * <ol>
 *   <li>客户端 POST /api/user/orders 创建 PAYPAL 渠道的 PENDING 订单,拿到 orderNo (UUID)。</li>
 *   <li>客户端再 POST /api/user/orders/paypal-checkout 创建 PayPal 订单,
 *       服务端把 paypalOrderId 写回 PENDING 订单,并返回 approve_url 让前端跳转。</li>
 *   <li>用户在 PayPal 页面完成支付后,PayPal 推送 PAYMENT.CAPTURE.COMPLETED 到 /api/webhooks/paypal/notify。</li>
 *   <li>服务端验签 → 取 resource → 反查订单 → 调用 orderService.markPaidByStore 发放积分。</li>
 *   <li>退款:PAYMENT.CAPTURE.REFUNDED 事件或后台手动调退款接口,扣回积分并标记订单 REFUNDED。</li>
 * </ol>
 *
 * <p>幂等:user_order.paypal_payment_id 有 uk_order_paypal_payment_id 唯一索引,
 * 同一 capture_id 绝不会被重复处理。</p>
 *
 * <p>密钥管理:client-id / client-secret / webhook-id 全部走环境变量注入,application.yml 仅保留占位符。</p>
 */
@Service
public class PayPalPaymentService {
    private static final Logger log = LoggerFactory.getLogger(PayPalPaymentService.class);
    /** 前端 capture 兜底路径调用时间窗 (秒),超过此时间窗拒绝,强制等待 Webhook 或管理员介入 */
    private static final int PAYPAL_CAPTURE_WINDOW_SECONDS = 10 * 60;

    private final OrderService orderService;
    private final PointProductService pointProductService;
    private final UserOrderService userOrderService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final PaymentEventLogService paymentEventLogService;

    @Autowired
    @Lazy
    private PayPalPaymentService self;

    @Value("${duanju.paypal.enabled:false}")
    private boolean enabled;

    @Value("${duanju.paypal.client-id:}")
    private String clientId;

    @Value("${duanju.paypal.client-secret:}")
    private String clientSecret;

    @Value("${duanju.paypal.webhook-id:}")
    private String webhookId;

    @Value("${duanju.paypal.base-url:https://api-m.sandbox.paypal.com}")
    private String baseUrl;

    @Value("${duanju.paypal.success-url:}")
    private String successUrl;

    @Value("${duanju.paypal.cancel-url:}")
    private String cancelUrl;

    private volatile String accessToken;
    private volatile long tokenExpiresAt;

    public PayPalPaymentService(OrderService orderService, PointProductService pointProductService,
                                UserOrderService userOrderService, ObjectMapper objectMapper,
                                PaymentEventLogService paymentEventLogService) {
        this.orderService = orderService;
        this.pointProductService = pointProductService;
        this.userOrderService = userOrderService;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
        this.paymentEventLogService = paymentEventLogService;
    }

    @PostConstruct
    void init() {
        if (!enabled) {
            log.warn("PayPal disabled, paypal endpoints will reject requests");
            return;
        }
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            log.error("PayPal client-id/client-secret not configured (env PAYPAL_CLIENT_ID/SECRET), paypal disabled");
            enabled = false;
            return;
        }
        log.info("PayPal initialized: baseUrl={}, successUrl={}, cancelUrl={}", baseUrl, successUrl, cancelUrl);
    }

    // --- Public APIs ---

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 获取 PayPal OAuth2 access token。
     */
    private String getAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiresAt) {
            return accessToken;
        }
        synchronized (this) {
            if (accessToken != null && System.currentTimeMillis() < tokenExpiresAt) {
                return accessToken;
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(clientId, clientSecret);

            Map<String, String> body = new HashMap<>();
            body.put("grant_type", "client_credentials");

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/v1/oauth2/token", request, String.class);

            try {
                JsonNode json = objectMapper.readTree(response.getBody());
                accessToken = json.get("access_token").asText();
                int expiresIn = json.has("expires_in") ? json.get("expires_in").asInt() : 32400;
                tokenExpiresAt = System.currentTimeMillis() + (long)(expiresIn - 300) * 1000;
                return accessToken;
            } catch (Exception ex) {
                log.error("PayPal get access token failed", ex);
                throw new IllegalStateException("paypal auth failed: " + ex.getMessage());
            }
        }
    }

    /**
     * 为已创建的 PENDING 订单创建 PayPal 订单,返回 approve_url 让前端跳转。
     * 同时把 paypalOrderId 写回 PENDING 订单,用于 webhook 反查。
     *
     * @param orderNo 已创建的 PENDING 订单号 (PAYPAL 渠道)
     * @return { "paypalOrderId": "xxx", "approveUrl": "https://www.paypal.com/checkoutnow?token=xxx", "orderNo": "xxx" }
     */
    @Transactional
    public Map<String, Object> createPayPalOrder(String orderNo) {
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

        String currency = (pending.getCurrency() != null ? pending.getCurrency() : "USD");
        long amountCents = pending.getAmountCents() != null && pending.getAmountCents() > 0
                ? pending.getAmountCents() : (product.getPriceCents() != null ? product.getPriceCents() : 0);
        if (amountCents <= 0) {
            throw new IllegalArgumentException("order amount must be positive");
        }

        double amountValue = amountCents / 100.0;

        // 1. 获取 access token
        String token = getAccessToken();

        // 2. 创建 PayPal Order
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        Map<String, Object> orderBody = new HashMap<>();
        orderBody.put("intent", "CAPTURE");

        Map<String, Object> purchaseUnit = new HashMap<>();
        purchaseUnit.put("reference_id", orderNo);
        purchaseUnit.put("description", product.getName() != null ? product.getName() : ("Points Pack " + product.getId()));

        Map<String, Object> amount = new HashMap<>();
        amount.put("currency_code", currency);
        amount.put("value", String.format("%.2f", amountValue));
        purchaseUnit.put("amount", amount);

        Map<String, Object> payee = new HashMap<>();
        if (product.getStoreProductId() != null && !product.getStoreProductId().isBlank()) {
            payee.put("merchant_id", product.getStoreProductId());
        }
        purchaseUnit.put("payee", payee);

        orderBody.put("purchase_units", new Map[]{purchaseUnit});

        Map<String, Object> appContext = new HashMap<>();
        appContext.put("brand_name", "Duanju Shorts");
        appContext.put("shipping_preference", "NO_SHIPPING");
        if (successUrl != null && !successUrl.isBlank()) {
            appContext.put("return_url", successUrl);
        }
        if (cancelUrl != null && !cancelUrl.isBlank()) {
            appContext.put("cancel_url", cancelUrl);
        }
        orderBody.put("application_context", appContext);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(orderBody, headers);

        String createOrderUrl = baseUrl + "/v2/checkout/orders";
        ResponseEntity<String> response = restTemplate.postForEntity(createOrderUrl, request, String.class);

        String paypalOrderId;
        String approveUrl;
        try {
            JsonNode json = objectMapper.readTree(response.getBody());
            paypalOrderId = json.get("id").asText();

            // 查找 approve_url
            approveUrl = null;
            JsonNode links = json.get("links");
            if (links != null && links.isArray()) {
                for (JsonNode link : links) {
                    if ("approve".equals(link.get("rel").asText())) {
                        approveUrl = link.get("href").asText();
                        break;
                    }
                }
            }

            if (approveUrl == null) {
                throw new IllegalStateException("paypal order create failed: no approve_url found");
            }
        } catch (Exception ex) {
            log.error("PayPal create order failed: orderNo={}", orderNo, ex);
            throw new IllegalStateException("paypal order create failed: " + ex.getMessage());
        }

        // 3. 写 paypalOrderId 回 PENDING 订单
        orderService.attachPayPalOrderId(orderNo, paypalOrderId);

        log.info("PayPal createPayPalOrder: ok, orderNo={}, paypalOrderId={}, amountCents={}, currency={}",
                orderNo, paypalOrderId, amountCents, currency);

        Map<String, Object> result = new HashMap<>();
        result.put("paypal_order_id", paypalOrderId);
        result.put("approve_url", approveUrl);
        result.put("order_no", orderNo);
        result.put("amount_cents", amountCents);
        result.put("currency", currency);
        return result;
    }

    /**
     * 捕获 PayPal 支付 (用户在 PayPal 页面完成支付后,前端调用此接口)。
     *
     * @param orderNo 订单号
     * @param paypalOrderId PayPal Order ID
     * @return 订单信息
     */
    @Transactional
    public Map<String, Object> capturePayment(String orderNo, String paypalOrderId) {
        assertEnabled();
        if (orderNo == null || orderNo.isBlank() || paypalOrderId == null || paypalOrderId.isBlank()) {
            throw new IllegalArgumentException("orderNo and paypalOrderId required");
        }

        UserOrder pending = userOrderService.lambdaQuery()
                .eq(UserOrder::getOrderNo, orderNo)
                .eq(UserOrder::getStatus, "PENDING")
                .last("limit 1")
                .one();
        if (pending == null) {
            throw new IllegalArgumentException("pending order not found: " + orderNo);
        }

        // 安全限制:前端 capture 兜底路径只允许在订单创建后 10 分钟内调用
        // (Webhook 是权威确认点,此接口仅用来兜底 webhook 未及时到达的短时间窗场景)
        // 超过时间窗后,由 Webhook 或管理员手动操作。这样防止前端被劫持伪造 capture 请求。
        if (pending.getCreatedAt() != null) {
            long ageSeconds = java.time.Duration.between(pending.getCreatedAt(),
                    java.time.LocalDateTime.now()).getSeconds();
            if (ageSeconds > PAYPAL_CAPTURE_WINDOW_SECONDS) {
                log.warn("PayPal capture: window expired for orderNo={}, ageSeconds={}, window={}s",
                        orderNo, ageSeconds, PAYPAL_CAPTURE_WINDOW_SECONDS);
                throw new IllegalStateException("paypal capture window expired, please wait for webhook or refresh order status");
            }
        }

        // 必须订单 paypal_order_id 与请求一致,防止用户拿 A 订单的 orderNo + B 订单的 paypalOrderId 乱抓
        if (pending.getPaypalOrderId() != null && !pending.getPaypalOrderId().equals(paypalOrderId)) {
            log.error("PayPal capture: paypalOrderId mismatch! orderNo={}, expected={}, got={}",
                    orderNo, pending.getPaypalOrderId(), paypalOrderId);
            throw new IllegalArgumentException("paypalOrderId mismatch");
        }

        // 1. 获取 access token
        String token = getAccessToken();

        // 2. 捕获支付
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        String captureUrl = baseUrl + "/v2/checkout/orders/" + paypalOrderId + "/capture";
        Map<String, Object> captureBody = new HashMap<>();
        captureBody.put("id", paypalOrderId);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(captureBody, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(captureUrl, request, String.class);

        String captureId;
        String paymentId = paypalOrderId;
        String captureStatus = null;
        try {
            JsonNode json = objectMapper.readTree(response.getBody());
            captureId = json.get("id").asText();
            String orderStatus = json.has("status") ? json.get("status").asText() : null;

            // 查找 payment_id 和 capture status (capture 后的 id)
            JsonNode purchaseUnits = json.get("purchase_units");
            if (purchaseUnits != null && purchaseUnits.isArray()) {
                for (JsonNode unit : purchaseUnits) {
                    JsonNode payments = unit.get("payments");
                    if (payments != null && payments.isArray()) {
                        for (JsonNode payment : payments) {
                            JsonNode captures = payment.get("captures");
                            if (captures != null && captures.isArray()) {
                                for (JsonNode capture : captures) {
                                    paymentId = capture.get("id").asText();
                                    captureStatus = capture.has("status") ? capture.get("status").asText() : null;
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            if (orderStatus != null && !"COMPLETED".equals(orderStatus)) {
                log.error("PayPal capture: order status is {}, expected COMPLETED, orderNo={}", orderStatus, orderNo);
                throw new IllegalStateException("paypal capture not completed: status=" + orderStatus);
            }
            if (captureStatus != null && !"COMPLETED".equals(captureStatus)) {
                log.error("PayPal capture: capture status is {}, expected COMPLETED, orderNo={}", captureStatus, orderNo);
                throw new IllegalStateException("paypal capture not completed: captureStatus=" + captureStatus);
            }
        } catch (Exception ex) {
            log.error("PayPal capture payment failed: orderNo={}, paypalOrderId={}", orderNo, paypalOrderId, ex);
            throw new IllegalStateException("paypal capture failed: " + ex.getMessage());
        }

        // 3. 调用 markPaidByStore 落单 + 发积分
        String currency = pending.getCurrency() != null ? pending.getCurrency() : "USD";
        Integer amountCents = pending.getAmountCents();

        StorePaymentContext ctx = new StorePaymentContext(
                StorePaymentContext.CHANNEL_PAYPAL,
                orderNo,
                pending.getUserId(),
                pending.getProductId(),
                captureId,
                paymentId,
                pending.getProductId() != null ? pending.getProductId().toString() : null,
                null,
                "LIVE",
                captureId,
                0,
                amountCents,
                currency
        );
        Map<String, Object> result = orderService.markPaidByStore(ctx);

        // 4. 写 paypalPaymentId
        userOrderService.lambdaUpdate()
                .set(UserOrder::getPaypalPaymentId, paymentId)
                .eq(UserOrder::getOrderNo, orderNo)
                .eq(UserOrder::getStatus, "PAID")
                .update();

        log.info("PayPal capturePayment: ok, orderNo={}, captureId={}, paymentId={}",
                orderNo, captureId, paymentId);
        return result;
    }

    /**
     * 处理 PayPal Webhook Event:签名校验 + 分发 + 幂等落单。
     *
     * <p>关键事件:</p>
     * <ul>
     *   <li>PAYMENT.CAPTURE.COMPLETED → 捕获支付成功,这里真正落单并发放积分</li>
     *   <li>PAYMENT.CAPTURE.REFUNDED → 扣回积分 + 标记 REFUNDED</li>
     *   <li>PAYMENT.CAPTURE.DECLINED / PAYMENT.CAPTURE.DENIED → 标记订单为 CANCELLED</li>
     * </ul>
     *
     * <p>任何业务异常内部吞掉返回 200,避免 PayPal 无意义重试 (幂等保证不会重复发积分)。</p>
     */
    public void handleWebhook(String payload, String transmissionId, String transmissionTime,
                              String certUrl, String authAlgo, String transmissionSig) {
        if (!enabled) {
            log.info("PayPal webhook received but paypal not enabled, ignoring (returning 200)");
            return;
        }
        if (webhookId == null || webhookId.isBlank()) {
            throw new IllegalStateException("paypal webhook-id not configured");
        }

        // 1. 验证 webhook 签名
        try {
            verifyWebhook(payload, transmissionId, transmissionTime, certUrl, authAlgo, transmissionSig);
        } catch (Exception ex) {
            log.warn("PayPal webhook signature verification failed: {}", ex.getMessage());
            throw new IllegalArgumentException("invalid paypal webhook signature");
        }

        // 2. 解析事件
        String eventType;
        String resourceId;
        String resourceType;
        String orderId;
        try {
            JsonNode event = objectMapper.readTree(payload);
            eventType = event.get("event_type").asText();
            JsonNode resource = event.get("resource");
            if (resource != null) {
                resourceId = resource.get("id") != null ? resource.get("id").asText() : null;
                resourceType = resource.get("capture_status") != null ? "capture" : "order";
                orderId = resource.get("supplementary_data") != null && resource.get("supplementary_data").get("related_ids") != null
                        ? resource.get("supplementary_data").get("related_ids").get("order_id").asText()
                        : null;
            } else {
                resourceId = null;
                resourceType = null;
                orderId = null;
            }
        } catch (Exception ex) {
            log.error("PayPal webhook: cannot parse payload", ex);
            return;
        }

        log.info("PayPal webhook: eventType={}, resourceId={}, resourceType={}, orderId={}",
                eventType, resourceId, resourceType, orderId);

        // 事件去重:同一 transmissionId 只处理一次 (uk_channel_event 唯一索引保证)
        if (!paymentEventLogService.tryLogEvent("PAYPAL", transmissionId, eventType)) {
            log.info("PayPal webhook: event already processed, skipping transmissionId={}", transmissionId);
            return;
        }

        try {
            switch (eventType) {
                case "PAYMENT.CAPTURE.COMPLETED" -> self.onCaptureCompleted(payload);
                case "PAYMENT.CAPTURE.REFUNDED" -> self.onCaptureRefunded(payload);
                case "PAYMENT.CAPTURE.DECLINED", "PAYMENT.CAPTURE.DENIED" -> self.onCaptureDeclined(resourceId);
                case "PAYMENT.ORDER.CANCELED" -> self.onOrderCanceled(orderId);
                default -> log.info("PayPal webhook: ignoring event type {}", eventType);
            }
            paymentEventLogService.updateResult("PAYPAL", transmissionId, "SUCCESS", orderId);
        } catch (RuntimeException ex) {
            log.error("PayPal webhook handling failed: eventType={}, resourceId={}", eventType, resourceId, ex);
            paymentEventLogService.updateResult("PAYPAL", transmissionId, "FAILED", orderId);
        }
    }

    // --- Event handlers ---

    /** PAYMENT.CAPTURE.COMPLETED:真正的支付成功确认点,落单 + 发积分。 */
    @Transactional
    public void onCaptureCompleted(String payload) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            JsonNode resource = event.get("resource");
            if (resource == null) {
                log.warn("PayPal onCaptureCompleted: no resource, skip");
                return;
            }
            String captureId = resource.get("id").asText();
            String captureStatus = resource.get("status").asText();
            if (!"COMPLETED".equals(captureStatus)) {
                log.info("PayPal onCaptureCompleted: capture status is {}, skip", captureStatus);
                return;
            }

            // 幂等:payment_id 已处理则直接返回
            Map<String, Object> existing = orderService.findOrderByPayPalPaymentId(captureId);
            if (existing != null) {
                log.info("PayPal onCaptureCompleted: idempotent (already processed), captureId={}", captureId);
                return;
            }

            // 解析 paypalOrderId (PayPal Order ID, 非内部 orderNo) / userId / productId
            String orderNo = null;
            Long userId = null;
            Long productId = null;
            String currency = null;
            Integer amountCents = null;

            String paypalOrderId = null;
            JsonNode supplementaryData = resource.get("supplementary_data");
            if (supplementaryData != null && supplementaryData.get("related_ids") != null) {
                paypalOrderId = supplementaryData.get("related_ids").get("order_id") != null
                        ? supplementaryData.get("related_ids").get("order_id").asText() : null;
            }

            // 1. 通过 paypal_order_id 反查订单 (PayPal Order ID 在创建订单时写入)
            UserOrder order = null;
            if (paypalOrderId != null && !paypalOrderId.isBlank()) {
                order = userOrderService.lambdaQuery()
                        .eq(UserOrder::getPaypalOrderId, paypalOrderId)
                        .last("limit 1")
                        .one();
            }
            // 2. 兜底:通过 captureId 查 storeTransactionId / storeOriginalTransactionId
            if (order == null) {
                order = userOrderService.lambdaQuery()
                        .and(w -> w.eq(UserOrder::getStoreTransactionId, captureId)
                                .or().eq(UserOrder::getStoreOriginalTransactionId, captureId))
                        .last("limit 1")
                        .one();
            }
            if (order != null) {
                orderNo = order.getOrderNo();
                userId = order.getUserId();
                productId = order.getProductId();
                currency = order.getCurrency();
                amountCents = order.getAmountCents();
            }

            if (orderNo == null || userId == null || productId == null) {
                log.error("PayPal onCaptureCompleted: cannot resolve orderNo/userId/productId, captureId={}, skip", captureId);
                return;
            }

            if (currency == null) currency = "USD";
            if (amountCents == null) amountCents = 0;

            // 校验金额
            JsonNode amountNode = resource.get("amount");
            if (amountNode != null) {
                currency = amountNode.get("currency_code") != null ? amountNode.get("currency_code").asText() : currency;
                double payAmount = Double.parseDouble(amountNode.get("value").asText());
                int payCents = (int) Math.round(payAmount * 100);
                if (amountCents > 0 && payCents != amountCents) {
                    log.error("PayPal onCaptureCompleted: amount mismatch, rejecting! expected={}, actual={}, orderNo={}",
                            amountCents, payCents, orderNo);
                    throw new IllegalArgumentException("PayPal amount mismatch: expected " + amountCents
                            + " cents but got " + payCents);
                }
                amountCents = payCents;
            }

            // 调 markPaidByStore 落单 + 发积分
            StorePaymentContext ctx = new StorePaymentContext(
                    StorePaymentContext.CHANNEL_PAYPAL,
                    orderNo,
                    userId,
                    productId,
                    captureId,
                    captureId,
                    productId.toString(),
                    null,
                    "LIVE",
                    captureId,
                    0,
                    amountCents,
                    currency
            );
            orderService.markPaidByStore(ctx);

            // 写 paypalPaymentId
            userOrderService.lambdaUpdate()
                    .set(UserOrder::getPaypalPaymentId, captureId)
                    .eq(UserOrder::getOrderNo, orderNo)
                    .eq(UserOrder::getStatus, "PAID")
                    .update();

            log.info("PayPal onCaptureCompleted: order paid, captureId={}, orderNo={}, userId={}",
                    captureId, orderNo, userId);
        } catch (Exception ex) {
            log.error("PayPal onCaptureCompleted: failed", ex);
        }
    }

    /** PAYMENT.CAPTURE.REFUNDED:统一走 OrderService.refundByStore 扣回积分 + 标记 REFUNDED。 */
    @Transactional
    public void onCaptureRefunded(String payload) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            JsonNode resource = event.get("resource");
            if (resource == null) {
                log.warn("PayPal onCaptureRefunded: no resource, skip");
                return;
            }
            String captureId = resource.get("id").asText();
            String refundStatus = resource.get("status").asText();

            log.info("PayPal onCaptureRefunded: captureId={}, refundStatus={}", captureId, refundStatus);

            if (!"COMPLETED".equals(refundStatus)) {
                log.info("PayPal onCaptureRefunded: refund status is {}, skip", refundStatus);
                return;
            }

            Map<String, Object> result = orderService.refundByStore(captureId, "PAYPAL_REFUND");
            log.info("PayPal onCaptureRefunded: refund processed, captureId={}", captureId);
        } catch (IllegalArgumentException ex) {
            log.warn("PayPal onCaptureRefunded: order not found: {}", ex.getMessage());
        } catch (Exception ex) {
            log.error("PayPal onCaptureRefunded: failed", ex);
        }
    }

    /** PAYMENT.CAPTURE.DECLINED / DENIED:标记订单为 CANCELLED。 */
    @Transactional
    public void onCaptureDeclined(String captureId) {
        if (captureId == null || captureId.isBlank()) {
            return;
        }
        log.info("PayPal onCaptureDeclined: captureId={}", captureId);
        orderService.cancelPendingByPayPalPaymentId(captureId);
    }

    /** PAYMENT.ORDER.CANCELED:订单被取消,标记为 CANCELLED。 */
    @Transactional
    public void onOrderCanceled(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return;
        }
        log.info("PayPal onOrderCanceled: orderId={}", orderId);
        // 用 paypalOrderId 反查订单并取消
        UserOrder order = userOrderService.lambdaQuery()
                .eq(UserOrder::getPaypalOrderId, orderId)
                .eq(UserOrder::getStatus, "PENDING")
                .last("limit 1")
                .one();
        if (order != null) {
            userOrderService.lambdaUpdate()
                    .set(UserOrder::getStatus, "CANCELLED")
                    .eq(UserOrder::getId, order.getId())
                    .eq(UserOrder::getStatus, "PENDING")
                    .update();
            log.info("PayPal onOrderCanceled: order cancelled, orderNo={}", order.getOrderNo());
        }
    }

    /** 管理员/后台主动触发退款:先调 PayPal Refund API,成功后统一走 OrderService.refundByStore 更新本地状态。 */
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
        String captureId = MapUtil.str(order, "paypal_payment_id");
        if (captureId == null || captureId.isBlank()) {
            throw new IllegalArgumentException("order has no paypal_payment_id, cannot refund via paypal");
        }

        // 1. 调 PayPal Refund API
        try {
            String token = getAccessToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            Map<String, Object> refundBody = new HashMap<>();
            Map<String, Object> amount = new HashMap<>();
            Integer amountCents = MapUtil.integer(order, "amount_cents");
            double amountValue = (amountCents != null ? amountCents : 0) / 100.0;
            amount.put("value", String.format("%.2f", amountValue));
            amount.put("currency_code", MapUtil.str(order, "currency") != null ? MapUtil.str(order, "currency") : "USD");
            refundBody.put("amount", amount);
            refundBody.put("note_to_payer", "Refund for order " + orderNo);

            String refundUrl = baseUrl + "/v2/payments/captures/" + captureId + "/refund";
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(refundBody, headers);
            restTemplate.postForEntity(refundUrl, request, String.class);
        } catch (Exception ex) {
            log.error("PayPal refund API failed: orderNo={}, captureId={}", orderNo, captureId, ex);
            throw new IllegalStateException("paypal refund failed: " + ex.getMessage());
        }

        // 2. 统一走 OrderService.refundByStore
        try {
            Map<String, Object> result = orderService.refundByStore(captureId, "PAYPAL_MANUAL_REFUND");
            log.info("PayPal manual refund: done, orderNo={}, captureId={}", orderNo, captureId);
            return result;
        } catch (RuntimeException ex) {
            log.error("CRITICAL: PayPal refund succeeded externally but local update failed! "
                    + "Manual reconciliation required. orderNo={}, captureId={}, error={}",
                    orderNo, captureId, ex.getMessage(), ex);
            throw ex;
        }
    }

    /** 按 paypal_payment_id 查找订单 (幂等检查) */
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

    // --- Helpers ---

    private void assertEnabled() {
        if (!enabled) {
            throw new IllegalArgumentException("支付渠道 PayPal 暂未开通，请联系管理员或切换其他支付方式");
        }
    }

    /**
     * 验证 PayPal Webhook 签名。
     * 调用 PayPal verify-webhook-signature API 进行服务端验签。
     * https://developer.paypal.com/docs/api/webhooks/v1/#verify-webhook-signature
     */
    private void verifyWebhook(String payload, String transmissionId, String transmissionTime,
                               String certUrl, String authAlgo, String transmissionSig) {
        if (transmissionId == null || transmissionSig == null || certUrl == null || authAlgo == null) {
            throw new IllegalArgumentException("missing paypal webhook transmission headers");
        }

        String token = getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        Map<String, Object> verifyBody = new HashMap<>();
        verifyBody.put("auth_algo", authAlgo);
        verifyBody.put("cert_url", certUrl);
        verifyBody.put("transmission_id", transmissionId);
        verifyBody.put("transmission_sig", transmissionSig);
        verifyBody.put("transmission_time", transmissionTime);
        verifyBody.put("webhook_id", webhookId);
        try {
            JsonNode event = objectMapper.readTree(payload);
            verifyBody.put("webhook_event", objectMapper.treeToValue(event, Object.class));
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid paypal webhook payload: " + ex.getMessage());
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(verifyBody, headers);
        String verifyUrl = baseUrl + "/v1/notifications/verify-webhook-signature";
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(verifyUrl, request, String.class);
            JsonNode result = objectMapper.readTree(response.getBody());
            String status = result.has("verification_status") ? result.get("verification_status").asText() : null;
            if (!"SUCCESS".equals(status)) {
                throw new IllegalArgumentException("paypal webhook verification failed: status=" + status);
            }
            log.info("PayPal webhook signature verified: transmissionId={}", transmissionId);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("paypal webhook verification error: " + ex.getMessage());
        }
    }
}