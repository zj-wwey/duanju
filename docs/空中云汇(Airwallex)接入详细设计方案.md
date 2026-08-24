# 空中云汇（Airwallex）接入详细设计方案

> **版本**: v1.0  
> **日期**: 2026-08-13  
> **状态**: 待评审 / 待开发  

---

## 目录

- [1. 概述](#1-概述)
- [2. 现有支付架构分析](#2-现有支付架构分析)
- [3. 空中云汇 API 对接设计](#3-空中云汇-api-对接设计)
- [4. 后端代码设计](#4-后端代码设计)
- [5. 数据库设计](#5-数据库设计)
- [6. 前端改造](#6-前端改造)
- [7. 部署配置](#7-部署配置)
- [8. 测试方案](#8-测试方案)
- [9. 文件清单](#9-文件清单)

---

## 1. 概述

### 1.1 目标

在现有 Stripe / PayPal / Apple IAP / Google Play 四渠道基础上，新增空中云汇（Airwallex）作为第五个支付渠道，支持信用卡、借记卡及本地支付方式（支付宝、微信支付等），复用现有 `OrderService.markPaidByStore()` 统一落单入口。

### 1.2 空中云汇简介

空中云汇是一家全球支付平台，提供：
- **收单（Acquiring）**：在线支付收单，支持 Visa/Mastercard/银联 等
- **支付方式（Payment Methods）**：卡支付 + 本地支付方式（支付宝、微信支付、AlipayHK、GrabPay 等）
- **Checkout**：托管支付页面，重定向模式（类似 Stripe Checkout）
- **Webhook 通知**：支付确认、退款、争议等事件推送

### 1.3 接入模式选择

选择 **Airwallex Payment Links / Hosted Checkout 模式**（重定向模式），与现有 Stripe Checkout 保持一致：

```
客户端创建订单 → 服务端创建 Airwallex PaymentIntent
  → 返回 checkout_url → 前端跳转到 Airwallex 托管支付页
  → 用户完成支付 → Airwallex 回调 redirect_url
  → 同时推送 webhook 到服务端 → 验签 → markPaidByStore 落单 + 发积分
```

---

## 2. 现有支付架构分析

### 2.1 统一支付上下文

所有支付渠道通过 `StorePaymentContext` 统一上下文记录，经 `OrderService.markPaidByStore()` 完成订单创建/更新与积分发放：

```java
public record StorePaymentContext(
    String channel,                    // 渠道: APPLE_IAP / GOOGLE_PLAY / STRIPE / PAYPAL / AIRWALLEX
    String orderNo,                    // 订单号
    Long userId,                       // 用户ID
    Long productId,                    // 商品ID
    String storeTransactionId,         // 渠道交易ID（幂等去重）
    String storeOriginalTransactionId, // 渠道原始交易ID
    String storeProductId,             // 渠道商品ID
    String storeBundleId,              // 渠道应用标识
    String storeEnvironment,           // 环境: SANDBOX / PRODUCTION
    String storeReceiptHash,           // 审计去重hash
    Integer taxAmountCents,            // 税额
    Integer priceCents,                // 实际售价
    String currency                    // 币种
) {}
```

### 2.2 现有渠道对比

| 维度 | Stripe | PayPal | Apple IAP | Google Play | Airwallex（新增） |
|------|:------:|:------:|:------:|:------:|:------:|
| 支付模式 | Checkout Session | Checkout Orders | StoreKit 2 | IAB v5 | PaymentIntent + Checkout |
| 前端跳转 | session_url | approve_url | 内购弹窗 | 内购弹窗 | checkout_url |
| Webhook | ✅ | ✅ | ✅ | ✅ | ✅ |
| 服务端回调 | ❌ | ✅(capture) | ✅(verify) | ✅(verify) | ✅(redirect) |
| 渠道常量 | STRIPE | PAYPAL | APPLE_IAP | GOOGLE_PLAY | **AIRWALLEX** |
| 幂等字段 | stripe_payment_intent_id | paypal_payment_id | store_transaction_id | google_purchase_token | **awx_payment_intent_id** |

### 2.3 现有代码结构

```
Controller 层:
  UserOrderController  → createOrder / stripe-checkout / paypal-checkout / paypal-capture / verify
  WebhookController    → stripe/notify / paypal/notify / apple/notify / google/notify

Service 层:
  StripePaymentService  → createCheckoutSession / handleWebhook / refundOrder
  PayPalPaymentService  → createPayPalOrder / capturePayment / handleWebhook / refundOrder
  AppleIapService       → verifyTransaction / handleNotification
  GooglePlayIapService  → verifyPurchase / handleRtdnEvent
  
  统一入口:
  OrderService          → markPaidByStore / refundByStore / createOrder

Entity 层:
  UserOrder  → stripe_session_id / stripe_payment_intent_id / 
               paypal_order_id / paypal_payment_id / store_transaction_id / ...
```

---

## 3. 空中云汇 API 对接设计

### 3.1 核心 API 端点

| 用途 | 方法 | 端点 | 说明 |
|------|:---:|------|------|
| 获取 Access Token | POST | `/api/v1/authentication/login` | 使用 Client ID + API Key 登录 |
| 创建 PaymentIntent | POST | `/api/v1/pa/payment_intents/create` | 创建支付意图，返回 checkout_url |
| 查询 PaymentIntent | GET | `/api/v1/pa/payment_intents/{id}` | 查询支付状态 |
| 退款 | POST | `/api/v1/pa/refunds/create` | 创建退款 |
| Webhook 接收 | POST | 服务端端点 | 接收支付确认通知 |

### 3.2 获取 Access Token（认证）

```http
POST https://api-demo.airwallex.com/api/v1/authentication/login
Content-Type: application/json
x-client-id: {CLIENT_ID}
x-api-key: {API_KEY}

Response:
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "expires_at": "2026-08-14T00:00:00Z"
}
```

**Token 管理策略：**
- 使用 `@PostConstruct` 初始化时获取，存入内存缓存
- 过期前 5 分钟自动刷新（通过定时任务或懒加载检查）
- 注意：Stripe 使用 `Stripe.apiKey` 全局静态设置，PayPal 使用 OAuth2 token。Airwallex 与 PayPal 类似，需要管理 token 生命周期。

### 3.3 创建 PaymentIntent（支付下单）

```http
POST https://api-demo.airwallex.com/api/v1/pa/payment_intents/create
Authorization: Bearer {TOKEN}
Content-Type: application/json

Request Body:
{
  "request_id": "uuid-abc123",           // 唯一请求ID（幂等键）
  "amount": 9.99,
  "currency": "USD",
  "order": {
    "reference": "DJO20260813001",       // 商户订单号
    "description": "Points Pack 500"
  },
  "merchant_order_id": "DJO20260813001", // 商户订单号（冗余）
  "return_url": "https://app.example.com/payment/result?orderNo=DJO20260813001",
  "payment_method_options": ["card"],    // 支付方式: card, alipay, wechatpay 等
  "descriptor": "DuanjuShorts",
  "metadata": {
    "orderNo": "DJO20260813001",
    "productId": "1",
    "userId": "1001"
  }
}

Response:
{
  "id": "pi_26ABCDEF1234567890",        // PaymentIntent ID
  "status": "REQUIRES_PAYMENT_METHOD",
  "amount": 9.99,
  "currency": "USD",
  "client_secret": "pi_26ABCDEF...",
  "url": "https://checkout.airwallex.com/pi_26ABCDEF1234567890", // 托管支付页URL
  "created_at": "2026-08-13T10:00:00Z"
}
```

**关键字段映射：**

| Airwallex 字段 | 对应系统字段 | 说明 |
|---------------|------------|------|
| `request_id` | UUID（幂等键） | 防止重复创建 PaymentIntent |
| `order.reference` | `orderNo` | 订单号 |
| `return_url` | 支付完成跳转地址 | 追加 orderNo 参数 |
| `metadata.orderNo` | 订单号 | Webhook 反查用 |
| `metadata.productId` | 商品ID | Webhook 反查用 |
| `metadata.userId` | 用户ID | Webhook 反查用 |
| `response.id` | `awx_payment_intent_id` | 存入数据库，幂等去重 |
| `response.url` | `checkout_url` | 前端跳转地址 |

### 3.4 查询 PaymentIntent

```http
GET https://api-demo.airwallex.com/api/v1/pa/payment_intents/{id}
Authorization: Bearer {TOKEN}

Response:
{
  "id": "pi_26ABCDEF1234567890",
  "status": "SUCCEEDED",               // REQUIRES_PAYMENT_METHOD / SUCCEEDED / CANCELLED
  "amount": 9.99,
  "currency": "USD",
  "latest_payment_attempt": {
    "id": "pa_26XYZ...",
    "status": "SUCCEEDED",
    "amount": 9.99,
    "currency": "USD"
  }
}
```

### 3.5 退款

```http
POST https://api-demo.airwallex.com/api/v1/pa/refunds/create
Authorization: Bearer {TOKEN}
Content-Type: application/json

Request Body:
{
  "request_id": "refund-uuid-123",
  "payment_intent_id": "pi_26ABCDEF1234567890",
  "amount": 9.99,
  "currency": "USD",
  "reason": "customer_request",
  "metadata": {
    "orderNo": "DJO20260813001"
  }
}
```

### 3.6 Webhook 事件

| 事件类型 | 说明 | 处理方式 |
|---------|------|---------|
| `payment_intent.succeeded` | 支付成功 | 落单 + 发积分 |
| `payment_intent.cancelled` | 支付取消 | 取消订单 |
| `payment_intent.capture_required` | 需要捕获（两阶段支付） | 执行 capture |
| `payment_attempt.failed` | 支付失败 | 日志记录 |
| `refund.succeeded` | 退款成功 | 扣回积分 |
| `dispute.created` | 争议创建 | 通知管理员 |

**Webhook 签名验证：**

Airwallex 使用 HMAC-SHA256 签名，在 `x-signature` header 中传递：

```
x-signature: t=1691913600,v1=abc123def456...
```

验证逻辑：
```
1. 从 header 解析 timestamp 和 signature
2. 构造 payload = timestamp + "." + raw_body
3. 用 webhook_secret 做 HMAC-SHA256
4. 对比 signature
```

### 3.7 环境配置

| 环境 | Base URL | 说明 |
|------|---------|------|
| Demo（沙盒） | `https://api-demo.airwallex.com` | 开发测试用 |
| Production（生产） | `https://api.airwallex.com` | 生产环境 |

---

## 4. 后端代码设计

### 4.1 新增文件

```
service/AirwallexPaymentService.java        -- 核心支付服务
controller/WebhookController.java           -- 新增 webhook 端点（修改）
controller/UserOrderController.java         -- 新增 checkout 端点（修改）
dto/payment/StorePaymentContext.java        -- 新增 AIRWALLEX 渠道常量（修改）
```

### 4.2 AirwallexPaymentService（新增）

**完整代码设计：**

```java
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/**
 * 空中云汇 (Airwallex) Web 支付服务。
 *
 * <p>核心流程:</p>
 * <ol>
 *   <li>客户端 POST /api/user/orders 创建 AIRWALLEX 渠道的 PENDING 订单,拿到 orderNo (UUID)。</li>
 *   <li>客户端 POST /api/user/orders/airwallex-checkout 创建 Airwallex PaymentIntent,
 *       服务端把 awxPaymentIntentId 写回 PENDING 订单,并返回 checkout_url 让前端跳转。</li>
 *   <li>用户在 Airwallex 托管页面完成支付后,跳回 return_url。</li>
 *   <li>Airwallex 同时推送 payment_intent.succeeded 到 /api/webhooks/airwallex/notify。</li>
 *   <li>服务端验签 → 取 payment_intent_id → 反查订单 → 调用 orderService.markPaidByStore 发放积分。</li>
 *   <li>退款:refund.succeeded 事件或后台手动调退款接口,扣回积分并标记订单 REFUNDED。</li>
 * </ol>
 *
 * <p>幂等:user_order.awx_payment_intent_id 有唯一索引,同一 pi_ 绝不会被重复处理。</p>
 *
 * <p>密钥管理:client-id / api-key / webhook-secret 全部走环境变量注入,application.yml 仅保留占位符。</p>
 */
@Service
public class AirwallexPaymentService {

    private static final Logger log = LoggerFactory.getLogger(AirwallexPaymentService.class);

    private final OrderService orderService;
    private final PointProductService pointProductService;
    private final UserOrderService userOrderService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    // ============ 配置项 ============

    @Value("${duanju.airwallex.enabled:false}")
    private boolean enabled;

    @Value("${duanju.airwallex.client-id:}")
    private String clientId;

    @Value("${duanju.airwallex.api-key:}")
    private String apiKey;

    @Value("${duanju.airwallex.webhook-secret:}")
    private String webhookSecret;

    @Value("${duanju.airwallex.base-url:https://api-demo.airwallex.com}")
    private String baseUrl;

    @Value("${duanju.airwallex.return-url:}")
    private String returnUrl;

    @Value("${duanju.airwallex.payment-methods:card}")
    private String paymentMethods;

    // ============ 运行时状态 ============

    private String accessToken;
    private Instant tokenExpiresAt;

    public AirwallexPaymentService(OrderService orderService,
                                   PointProductService pointProductService,
                                   UserOrderService userOrderService,
                                   ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.pointProductService = pointProductService;
        this.userOrderService = userOrderService;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    // ============ 初始化 ============

    @PostConstruct
    void init() {
        if (!enabled) {
            log.warn("Airwallex disabled, endpoints will reject requests");
            return;
        }
        if (clientId == null || clientId.isBlank() || apiKey == null || apiKey.isBlank()) {
            log.error("Airwallex client-id/api-key not configured, airwallex disabled");
            enabled = false;
            return;
        }
        log.info("Airwallex initialized: baseUrl={}, returnUrl={}", baseUrl, returnUrl);
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ============ Token 管理 ============

    /**
     * 获取 Airwallex Access Token。
     * 使用 Client ID + API Key 通过 /api/v1/authentication/login 获取。
     */
    private synchronized String getAccessToken() {
        // 如果 token 未过期，直接返回
        if (accessToken != null && tokenExpiresAt != null
                && tokenExpiresAt.isAfter(Instant.now().plusSeconds(300))) {
            return accessToken;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-client-id", clientId);
        headers.set("x-api-key", apiKey);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/api/v1/authentication/login", request, String.class);
            JsonNode json = objectMapper.readTree(response.getBody());
            accessToken = json.get("token").asText();
            // 解析过期时间（Airwallex 返回 ISO 8601 格式）
            String expiresAt = json.get("expires_at").asText();
            tokenExpiresAt = Instant.parse(expiresAt);
            log.info("Airwallex: token refreshed, expiresAt={}", tokenExpiresAt);
            return accessToken;
        } catch (Exception ex) {
            log.error("Airwallex: get access token failed", ex);
            throw new IllegalStateException("airwallex auth failed: " + ex.getMessage());
        }
    }

    // ============ 创建 PaymentIntent ============

    /**
     * 为已创建的 PENDING 订单创建 Airwallex PaymentIntent,
     * 返回 checkout_url 让前端跳转。
     *
     * @param orderNo 已创建的 PENDING 订单号 (AIRWALLEX 渠道)
     * @return { "paymentIntentId": "pi_xxx", "checkoutUrl": "https://checkout.airwallex.com/...",
     *           "orderNo": "xxx", "amountCents": xxx, "currency": "USD" }
     */
    @Transactional
    public Map<String, Object> createPaymentIntent(String orderNo) {
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
        int amountCents = pending.getAmountCents() != null && pending.getAmountCents() > 0
                ? pending.getAmountCents()
                : (product.getPriceCents() != null ? product.getPriceCents() : 0);
        if (amountCents <= 0) {
            throw new IllegalArgumentException("order amount must be positive");
        }
        double amountValue = amountCents / 100.0;

        // 1. 获取 Access Token
        String token = getAccessToken();

        // 2. 构造请求体
        String requestId = UUID.randomUUID().toString();
        String fullReturnUrl = buildReturnUrl(returnUrl, orderNo);

        // 解析支付方式（逗号分隔，如 "card,alipay,wechatpay"）
        List<String> methods = paymentMethods != null && !paymentMethods.isBlank()
                ? Arrays.asList(paymentMethods.split("\\s*,\\s*"))
                : List.of("card");

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("request_id", requestId);
        requestBody.put("amount", amountValue);
        requestBody.put("currency", currency);

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("reference", orderNo);
        order.put("description", product.getName() != null
                ? product.getName()
                : ("Points Pack " + product.getId()));
        requestBody.put("order", order);

        requestBody.put("merchant_order_id", orderNo);
        requestBody.put("return_url", fullReturnUrl);
        requestBody.put("payment_method_options", methods);
        requestBody.put("descriptor", "DuanjuShorts");

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("orderNo", orderNo);
        metadata.put("productId", String.valueOf(product.getId()));
        metadata.put("userId", String.valueOf(pending.getUserId()));
        requestBody.put("metadata", metadata);

        // 3. 调用 API
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        String paymentIntentId;
        String checkoutUrl;
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/api/v1/pa/payment_intents/create", request, String.class);
            JsonNode json = objectMapper.readTree(response.getBody());
            paymentIntentId = json.get("id").asText();
            checkoutUrl = json.get("url").asText();
        } catch (Exception ex) {
            log.error("Airwallex: create PaymentIntent failed, orderNo={}", orderNo, ex);
            throw new IllegalStateException("airwallex payment intent create failed: " + ex.getMessage());
        }

        // 4. 写 awxPaymentIntentId 回 PENDING 订单
        attachAwxPaymentIntentId(orderNo, paymentIntentId);

        log.info("Airwallex: createPaymentIntent ok, orderNo={}, pi={}, amountCents={}, currency={}",
                orderNo, paymentIntentId, amountCents, currency);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("payment_intent_id", paymentIntentId);
        result.put("checkout_url", checkoutUrl);
        result.put("order_no", orderNo);
        result.put("amount_cents", amountCents);
        result.put("currency", currency);
        return result;
    }

    // ============ Webhook 处理 ============

    /**
     * 处理 Airwallex Webhook Event:签名校验 + 分发 + 幂等落单。
     *
     * <p>关键事件:</p>
     * <ul>
     *   <li>payment_intent.succeeded → 落单 + 发积分</li>
     *   <li>payment_intent.cancelled → 取消订单</li>
     *   <li>refund.succeeded → 扣回积分 + 标记 REFUNDED</li>
     * </ul>
     *
     * <p>任何业务异常内部吞掉返回 200,避免 Airwallex 无意义重试。</p>
     */
    public void handleWebhook(String payload, String signatureHeader) {
        if (!enabled) {
            log.info("Airwallex webhook received but airwallex not enabled, ignoring (returning 200)");
            return;
        }

        // 1. 验证签名
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            try {
                verifyWebhookSignature(payload, signatureHeader);
            } catch (Exception ex) {
                log.warn("Airwallex webhook signature verification failed: {}", ex.getMessage());
                throw new IllegalArgumentException("invalid airwallex webhook signature");
            }
        }

        // 2. 解析事件
        String eventType;
        String eventId;
        try {
            JsonNode event = objectMapper.readTree(payload);
            eventType = event.get("name").asText();
            eventId = event.get("id").asText();
        } catch (Exception ex) {
            log.error("Airwallex webhook: cannot parse payload", ex);
            return;
        }

        log.info("Airwallex webhook: eventId={}, type={}", eventId, eventType);

        try {
            switch (eventType) {
                case "payment_intent.succeeded" -> onPaymentIntentSucceeded(payload);
                case "payment_intent.cancelled" -> onPaymentIntentCancelled(payload);
                case "refund.succeeded" -> onRefundSucceeded(payload);
                default -> log.info("Airwallex webhook: ignoring event type {}", eventType);
            }
        } catch (RuntimeException ex) {
            log.error("Airwallex webhook handling failed: eventId={}, type={}", eventId, eventType, ex);
        }
    }

    /** payment_intent.succeeded:支付成功,落单 + 发积分。 */
    @Transactional
    protected void onPaymentIntentSucceeded(String payload) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            JsonNode data = event.get("data");
            if (data == null) {
                log.warn("Airwallex onPaymentIntentSucceeded: no data field");
                return;
            }
            JsonNode pi = data.get("object");
            if (pi == null) {
                log.warn("Airwallex onPaymentIntentSucceeded: no object field");
                return;
            }

            String paymentIntentId = pi.get("id").asText();
            String status = pi.get("status").asText();

            if (!"SUCCEEDED".equals(status)) {
                log.info("Airwallex onPaymentIntentSucceeded: status is {}, skip", status);
                return;
            }

            // 幂等:已处理则直接返回
            Map<String, Object> existing = orderService.findOrderByAwxPaymentIntent(paymentIntentId);
            if (existing != null) {
                log.info("Airwallex onPaymentIntentSucceeded: idempotent, pi={}", paymentIntentId);
                return;
            }

            // 解析 metadata
            JsonNode metadata = pi.get("metadata");
            String orderNo = metadata != null ? metadata.get("orderNo").asText() : null;
            Long productId = metadata != null && metadata.get("productId") != null
                    ? Long.parseLong(metadata.get("productId").asText()) : null;
            Long userId = metadata != null && metadata.get("userId") != null
                    ? Long.parseLong(metadata.get("userId").asText()) : null;

            // 兜底：从 DB 反查
            if (orderNo == null || productId == null || userId == null) {
                UserOrder order = userOrderService.lambdaQuery()
                        .eq(UserOrder::getAwxPaymentIntentId, paymentIntentId)
                        .last("limit 1")
                        .one();
                if (order != null) {
                    orderNo = order.getOrderNo();
                    userId = order.getUserId();
                    productId = order.getProductId();
                }
            }

            if (orderNo == null || userId == null || productId == null) {
                log.error("Airwallex onPaymentIntentSucceeded: cannot resolve orderNo/userId/productId, pi={}",
                        paymentIntentId);
                return;
            }

            // 币种/金额
            String currency = pi.get("currency") != null ? pi.get("currency").asText() : "USD";
            double amount = pi.get("amount").asDouble();
            int amountCents = (int) Math.round(amount * 100);

            // 调 markPaidByStore 落单 + 发积分
            StorePaymentContext ctx = new StorePaymentContext(
                    StorePaymentContext.CHANNEL_AIRWALLEX,
                    orderNo,
                    userId,
                    productId,
                    paymentIntentId,                     // storeTransactionId = pi id
                    paymentIntentId,                     // storeOriginalTransactionId = pi id
                    productId.toString(),                // storeProductId
                    null,                                // storeBundleId
                    "LIVE",
                    paymentIntentId,                     // storeReceiptHash
                    0,
                    amountCents,
                    currency
            );
            orderService.markPaidByStore(ctx);

            // 写 awx_payment_intent_id
            userOrderService.lambdaUpdate()
                    .set(UserOrder::getAwxPaymentIntentId, paymentIntentId)
                    .eq(UserOrder::getOrderNo, orderNo)
                    .eq(UserOrder::getStatus, "PAID")
                    .update();

            log.info("Airwallex onPaymentIntentSucceeded: order paid, pi={}, orderNo={}, userId={}, amountCents={}",
                    paymentIntentId, orderNo, userId, amountCents);
        } catch (Exception ex) {
            log.error("Airwallex onPaymentIntentSucceeded: failed", ex);
        }
    }

    /** payment_intent.cancelled:支付取消,标记订单为 CANCELLED。 */
    @Transactional
    protected void onPaymentIntentCancelled(String payload) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            JsonNode pi = event.get("data").get("object");
            if (pi == null) return;

            String paymentIntentId = pi.get("id").asText();
            log.info("Airwallex onPaymentIntentCancelled: pi={}", paymentIntentId);

            UserOrder order = userOrderService.lambdaQuery()
                    .eq(UserOrder::getAwxPaymentIntentId, paymentIntentId)
                    .eq(UserOrder::getStatus, "PENDING")
                    .last("limit 1")
                    .one();
            if (order != null) {
                userOrderService.lambdaUpdate()
                        .set(UserOrder::getStatus, "CANCELLED")
                        .eq(UserOrder::getId, order.getId())
                        .eq(UserOrder::getStatus, "PENDING")
                        .update();
                log.info("Airwallex onPaymentIntentCancelled: order cancelled, orderNo={}", order.getOrderNo());
            }
        } catch (Exception ex) {
            log.error("Airwallex onPaymentIntentCancelled: failed", ex);
        }
    }

    /** refund.succeeded:退款成功,扣回积分 + 标记 REFUNDED。 */
    @Transactional
    protected void onRefundSucceeded(String payload) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            JsonNode refund = event.get("data").get("object");
            if (refund == null) return;

            String paymentIntentId = refund.get("payment_intent_id") != null
                    ? refund.get("payment_intent_id").asText() : null;
            if (paymentIntentId == null || paymentIntentId.isBlank()) return;

            log.info("Airwallex onRefundSucceeded: pi={}", paymentIntentId);

            try {
                orderService.refundByStore(paymentIntentId, "AIRWALLEX_REFUND");
                log.info("Airwallex onRefundSucceeded: refund processed, pi={}", paymentIntentId);
            } catch (IllegalArgumentException ex) {
                log.warn("Airwallex onRefundSucceeded: order not found for pi={}", paymentIntentId);
            }
        } catch (Exception ex) {
            log.error("Airwallex onRefundSucceeded: failed", ex);
        }
    }

    // ============ 退款 ============

    /**
     * 管理员/后台主动触发退款。
     */
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
        String paymentIntentId = MapUtil.str(order, "awx_payment_intent_id");
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new IllegalArgumentException("order has no awx_payment_intent_id");
        }

        // 1. 调 Airwallex Refund API
        try {
            String token = getAccessToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            Map<String, Object> refundBody = new LinkedHashMap<>();
            refundBody.put("request_id", UUID.randomUUID().toString());
            refundBody.put("payment_intent_id", paymentIntentId);
            refundBody.put("amount", (MapUtil.integer(order, "amount_cents") != null
                    ? MapUtil.integer(order, "amount_cents") : 0) / 100.0);
            refundBody.put("currency", MapUtil.str(order, "currency") != null
                    ? MapUtil.str(order, "currency") : "USD");
            refundBody.put("reason", "customer_request");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(refundBody, headers);
            restTemplate.postForEntity(
                    baseUrl + "/api/v1/pa/refunds/create", request, String.class);
        } catch (Exception ex) {
            log.error("Airwallex refund API failed: orderNo={}, pi={}", orderNo, paymentIntentId, ex);
            throw new IllegalStateException("airwallex refund failed: " + ex.getMessage());
        }

        // 2. 统一走 OrderService.refundByStore
        Map<String, Object> result = orderService.refundByStore(paymentIntentId, "AIRWALLEX_MANUAL_REFUND");
        log.info("Airwallex manual refund: done, orderNo={}, pi={}", orderNo, paymentIntentId);
        return result;
    }

    // ============ 辅助方法 ============

    private void assertEnabled() {
        if (!enabled) {
            throw new IllegalStateException("airwallex not enabled");
        }
    }

    /**
     * 将 awxPaymentIntentId 写回 PENDING 订单。
     */
    @Transactional
    public void attachAwxPaymentIntentId(String orderNo, String paymentIntentId) {
        if (orderNo == null || paymentIntentId == null) return;
        userOrderService.lambdaUpdate()
                .set(UserOrder::getAwxPaymentIntentId, paymentIntentId)
                .eq(UserOrder::getOrderNo, orderNo)
                .eq(UserOrder::getStatus, "PENDING")
                .update();
    }

    /**
     * 构造 return_url（支付完成后 Airwallex 跳转回前端）。
     */
    private String buildReturnUrl(String base, String orderNo) {
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("airwallex return-url not configured");
        }
        String sep = base.contains("?") ? "&" : "?";
        return base + sep + "orderNo=" + orderNo + "&status=success";
    }

    /**
     * 验证 Airwallex Webhook 签名 (HMAC-SHA256)。
     *
     * <p>Airwallex 在 x-signature header 中传递:
     * <pre>t=1691913600,v1=abc123def456...</pre>
     * 验证逻辑:构造 payload = timestamp + "." + raw_body,用 webhookSecret 做 HMAC-SHA256。
     */
    private void verifyWebhookSignature(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new IllegalArgumentException("missing x-signature header");
        }

        // 解析 timestamp 和 signature
        String timestamp = null;
        String signature = null;
        for (String part : signatureHeader.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                if ("t".equals(kv[0])) timestamp = kv[1];
                if ("v1".equals(kv[0])) signature = kv[1];
            }
        }

        if (timestamp == null || signature == null) {
            throw new IllegalArgumentException("invalid x-signature format");
        }

        // 构造 payload
        String signedPayload = timestamp + "." + payload;

        // HMAC-SHA256
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            String computed = Base64.getEncoder().encodeToString(hash);

            if (!MessageDigest.isEqual(computed.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("signature mismatch");
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalArgumentException("signature verification failed", ex);
        }
    }
}
```

### 4.3 WebhookController 修改（新增端点）

在 `WebhookController.java` 中新增：

```java
private final AirwallexPaymentService airwallexPaymentService;

// 构造函数注入
public WebhookController(..., AirwallexPaymentService airwallexPaymentService) {
    // ...
    this.airwallexPaymentService = airwallexPaymentService;
}

/**
 * Airwallex Webhook 接收端点。
 *
 * <p>配置方法:登录 Airwallex Dashboard → Developers → Webhooks → Add endpoint →
 * URL = https://yourdomain.com/api/webhooks/airwallex/notify →
 * 订阅事件: payment_intent.succeeded、payment_intent.cancelled、refund.succeeded →
 * 拿到 Webhook Secret,配置到 AIRWALLEX_WEBHOOK_SECRET 环境变量。</p>
 *
 * <p>签名验证:Airwallex 使用 HMAC-SHA256,x-signature header 格式为 t=timestamp,v1=signature。</p>
 */
@PostMapping("/airwallex/notify")
public R<Void> airwallexNotify(HttpServletRequest request,
                               @RequestHeader(value = "x-signature", required = false) String signature) {
    if (!airwallexPaymentService.isEnabled()) {
        return R.ok();
    }
    // 1. 读取 RAW payload
    String payload;
    try (ServletInputStream is = request.getInputStream();
         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) {
            baos.write(buf, 0, n);
        }
        payload = baos.toString(StandardCharsets.UTF_8);
    } catch (Exception ex) {
        throw new IllegalArgumentException("cannot read airwallex request payload: " + ex.getMessage());
    }
    // 2. 签名校验 + 分发
    airwallexPaymentService.handleWebhook(payload, signature);
    return R.ok();
}
```

### 4.4 UserOrderController 修改（新增端点）

```java
private final AirwallexPaymentService airwallexPaymentService;

// 构造函数注入
public UserOrderController(..., AirwallexPaymentService airwallexPaymentService) {
    // ...
    this.airwallexPaymentService = airwallexPaymentService;
}

/**
 * 为已创建的 PENDING(AIRWALLEX 渠道) 订单创建 Airwallex PaymentIntent,
 * 返回 checkout_url 让前端跳转完成支付。
 *
 * @param request 只包含 orderNo 字段;必须是已创建的 PENDING 订单,且 payChannel=AIRWALLEX
 * @return { paymentIntentId, checkoutUrl, orderNo, amountCents, currency }
 */
@PostMapping("/orders/airwallex-checkout")
public R<Map<String, Object>> airwallexCheckout(@Valid @RequestBody AirwallexCheckoutRequest request) {
    return R.ok(airwallexPaymentService.createPaymentIntent(request.orderNo()));
}

/** Airwallex Checkout 请求体 */
public record AirwallexCheckoutRequest(@NotBlank String orderNo) {}
```

### 4.5 StorePaymentContext 修改

```java
// 新增渠道常量
public static final String CHANNEL_AIRWALLEX = "AIRWALLEX";
```

### 4.6 OrderService 修改（新增幂等查询方法）

```java
/**
 * 按 awx_payment_intent_id 查已落单订单 (幂等检查)。
 */
public Map<String, Object> findOrderByAwxPaymentIntent(String paymentIntentId) {
    if (paymentIntentId == null || paymentIntentId.isBlank()) {
        return null;
    }
    UserOrder order = userOrderService.lambdaQuery()
            .eq(UserOrder::getAwxPaymentIntentId, paymentIntentId)
            .last("limit 1")
            .one();
    return order == null ? null : userOrderService.orderByNo(order.getOrderNo());
}
```

---

## 5. 数据库设计

### 5.1 user_order 新增字段

```sql
ALTER TABLE user_order ADD COLUMN awx_payment_intent_id VARCHAR(128) COMMENT 'Airwallex PaymentIntent ID,用于幂等与对账';
```

### 5.2 新增唯一索引

```sql
-- 唯一索引:防止同一 Airwallex PaymentIntent 被重复处理
CREATE UNIQUE INDEX uk_order_awx_pi ON user_order(awx_payment_intent_id);
```

### 5.3 SchemaMigrationRunner 新增

```java
private void ensureAirwallexColumns() {
    if (!tableExists("user_order")) return;
    addColumnIfMissing("user_order", "awx_payment_intent_id",
            "varchar(128) comment 'Airwallex PaymentIntent ID,用于幂等与对账'");
    if (!indexExists("user_order", "uk_order_awx_pi")) {
        jdbcTemplate.execute("create unique index uk_order_awx_pi on user_order(awx_payment_intent_id)");
    }
}
```

在 `run()` 方法中调用 `ensureAirwallexColumns()`。

### 5.4 UserOrder Entity 新增字段

```java
/** Airwallex PaymentIntent ID,用于幂等与对账 */
@TableField("awx_payment_intent_id")
private String awxPaymentIntentId;
```

---

## 6. 前端改造

### 6.1 Web 端（front\admin）

#### 6.1.1 支付方式选择增加 Airwallex 选项

在 `VipPage.vue` 和 `RechargePage.vue` 的支付方式选择区域增加：

```html
<div class="payment-methods">
  <!-- 现有 Stripe / PayPal -->
  <button @click="selectPayChannel('STRIPE')">Stripe (信用卡)</button>
  <button @click="selectPayChannel('PAYPAL')">PayPal</button>
  
  <!-- 新增 Airwallex -->
  <button @click="selectPayChannel('AIRWALLEX')">
    <img src="/assets/airwallex-logo.svg" alt="Airwallex" />
    空中云汇 (信用卡/支付宝/微信支付)
  </button>
</div>
```

#### 6.1.2 支付流程修改

```javascript
async buyPlan(plan) {
  // 1. 创建订单
  const order = await api.userCreateOrder({
    productId: plan.id,
    payChannel: this.selectedPayChannel
  });
  
  if (this.selectedPayChannel === 'AIRWALLEX') {
    // 2. Airwallex 流程：创建 PaymentIntent → 跳转
    const result = await api.userAirwallexCheckout({
      orderNo: order.order_no || order.orderNo
    });
    // 3. 跳转到 Airwallex 托管支付页
    window.location.href = result.checkout_url;
  } else if (this.selectedPayChannel === 'STRIPE') {
    // 现有 Stripe 流程...
  }
  // ...
}
```

#### 6.1.3 支付结果回调页

前端需要一个回调页面接收 `return_url` 参数：

```javascript
// pages/PaymentResultPage.vue 或路由回调
onMounted(async () => {
  const urlParams = new URLSearchParams(window.location.search);
  const orderNo = urlParams.get('orderNo');
  const status = urlParams.get('status');
  
  if (status === 'success') {
    // 轮询或通过 webhook 确认支付后刷新用户状态
    await this.checkPaymentStatus(orderNo);
    this.$router.push('/recharge?result=success');
  }
});
```

### 6.2 小程序端（front\uniapp）

在 `pages/mine/vip.vue` 的支付方式中增加 Airwallex 选项。

### 6.3 API 封装

```javascript
// api.js 新增
export function userAirwallexCheckout(data) {
  return request({
    url: '/api/user/orders/airwallex-checkout',
    method: 'POST',
    data
  });
}
```

---

## 7. 部署配置

### 7.1 application.yml 新增

```yaml
duanju:
  airwallex:
    # 是否启用 Airwallex Web 支付
    enabled: ${AIRWALLEX_ENABLED:false}
    # Airwallex Client ID (从 Airwallex Dashboard → Developers → API Keys 获取)
    client-id: ${AIRWALLEX_CLIENT_ID:}
    # Airwallex API Key (从 Airwallex Dashboard → Developers → API Keys 获取)
    api-key: ${AIRWALLEX_API_KEY:}
    # Airwallex Webhook 签名密钥 (从 Airwallex Dashboard → Developers → Webhooks 获取)
    webhook-secret: ${AIRWALLEX_WEBHOOK_SECRET:}
    # Airwallex API 基础路径 (沙盒: https://api-demo.airwallex.com, 生产: https://api.airwallex.com)
    base-url: ${AIRWALLEX_BASE_URL:https://api-demo.airwallex.com}
    # 支付完成后跳转地址 (前端回调页面)
    return-url: ${AIRWALLEX_RETURN_URL:}
    # 支持的支付方式,逗号分隔 (card, alipay, wechatpay, alipayhk, grabpay, etc.)
    payment-methods: ${AIRWALLEX_PAYMENT_METHODS:card}
```

### 7.2 环境变量清单

| 环境变量 | 说明 | 示例 |
|---------|------|------|
| `AIRWALLEX_ENABLED` | 是否启用 | true |
| `AIRWALLEX_CLIENT_ID` | Client ID | `abc123...` |
| `AIRWALLEX_API_KEY` | API Key | `sk_abc...` |
| `AIRWALLEX_WEBHOOK_SECRET` | Webhook 签名密钥 | `whsec_abc...` |
| `AIRWALLEX_BASE_URL` | API 基础路径 | `https://api-demo.airwallex.com` |
| `AIRWALLEX_RETURN_URL` | 支付完成跳转地址 | `https://app.example.com/payment/result` |
| `AIRWALLEX_PAYMENT_METHODS` | 支付方式 | `card,alipay,wechatpay` |

### 7.3 Airwallex Dashboard 配置

1. 注册 Airwallex 账号，完成 KYC 认证
2. 进入 Dashboard → Developers → API Keys，创建 Client ID 和 API Key
3. 进入 Developers → Webhooks，添加 Webhook Endpoint：
   - URL: `https://yourdomain.com/api/webhooks/airwallex/notify`
   - 订阅事件：
     - `payment_intent.succeeded`
     - `payment_intent.cancelled`
     - `refund.succeeded`
     - `payment_attempt.failed`
   - 获取 Webhook Secret
4. 配置 Return URL（支付完成后跳转的前端页面地址）
5. 在 Payment Methods 中启用需要的支付方式（card、alipay、wechatpay 等）

### 7.4 沙盒测试

- 沙盒环境：`https://api-demo.airwallex.com`
- 测试卡号：
  - 成功：`4111111111111111`（Visa）
  - 失败：`4000000000000002`（拒绝）
  - 3DS：`4000002500001001`（需要3D验证）
- 沙盒支付宝/微信支付通过 Airwallex Dashboard 配置测试参数

---

## 8. 测试方案

### 8.1 单元测试

| 测试项 | 测试内容 |
|--------|---------|
| Token 管理 | 获取 token、过期刷新、并发获取 |
| PaymentIntent 创建 | 正常创建、无效订单号、金额为0 |
| Webhook 签名验证 | 正确签名、错误签名、缺失签名 |
| Webhook 事件处理 | succeeded/cancelled/refunded/重复事件 |
| 退款 | 正常退款、退款金额不匹配、重复退款 |

### 8.2 集成测试

| 场景 | 步骤 | 预期结果 |
|------|------|---------|
| 正常支付 | 创建订单 → airwallex-checkout → 跳转支付 → webhook 通知 | 订单 PAID，积分到账 |
| 支付取消 | 创建订单 → airwallex-checkout → 用户取消 | 订单 CANCELLED |
| 支付失败 | 创建订单 → 支付失败 | 订单状态不变 |
| 重复 webhook | 同一 payment_intent 多次推送 | 幂等，不重复发积分 |
| 退款 | 后台退款 → webhook 通知 | 订单 REFUNDED，积分扣回 |
| 前端轮询 | 支付后回到回调页 → 轮询订单状态 | 正确显示支付结果 |

### 8.3 沙盒测试流程

```
1. 配置 AIRWALLEX_BASE_URL=https://api-demo.airwallex.com
2. 配置 AIRWALLEX_ENABLED=true
3. 配置沙盒 Client ID / API Key / Webhook Secret
4. 启动后端服务
5. 前端选择 Airwallex 支付
6. 在沙盒页面使用测试卡号 4111111111111111 完成支付
7. 验证 webhook 接收和处理
8. 验证积分到账
```

### 8.4 上线前检查清单

- [ ] 环境变量全部配置为生产值
- [ ] `base-url` 改为 `https://api.airwallex.com`
- [ ] `return-url` 改为生产前端地址
- [ ] Webhook URL 配置为生产域名
- [ ] 支付方式配置确认（card、alipay、wechatpay 等）
- [ ] 生产环境完成一笔真实支付测试
- [ ] 退款流程验证
- [ ] 日志监控配置

---

## 9. 文件清单

### 9.1 新增文件

| 文件路径 | 说明 |
|---------|------|
| `service/AirwallexPaymentService.java` | 空中云汇支付核心服务 |

### 9.2 修改文件

| 文件路径 | 修改内容 |
|---------|---------|
| `controller/WebhookController.java` | 新增 airwallexNotify 端点 |
| `controller/UserOrderController.java` | 新增 airwallexCheckout 端点 + AirwallexCheckoutRequest DTO |
| `dto/payment/StorePaymentContext.java` | 新增 CHANNEL_AIRWALLEX 常量 |
| `service/OrderService.java` | 新增 findOrderByAwxPaymentIntent 方法 |
| `entity/UserOrder.java` | 新增 awxPaymentIntentId 字段 |
| `config/SchemaMigrationRunner.java` | 新增 ensureAirwallexColumns 方法 |
| `resources/application.yml` | 新增 duanju.airwallex 配置段 |
| `front/admin/src/pages/VipPage.vue` | 新增 Airwallex 支付方式选择 |
| `front/admin/src/pages/RechargePage.vue` | 新增 Airwallex 支付方式选择 |
| `front/admin/src/api/api.js` | 新增 userAirwallexCheckout 方法 |
| `front/uniapp/pages/mine/vip.vue` | 新增 Airwallex 支付方式选择 |

---

## 10. 支付渠道对比总结

| 维度 | Stripe | PayPal | Airwallex（新增） |
|------|:------:|:------:|:------:|
| 认证方式 | API Key (静态) | OAuth2 Client Credentials | Client ID + API Key → JWT Token |
| 支付模式 | Checkout Session | Orders API | PaymentIntent + Checkout |
| 支付方式 | 信用卡 + 本地支付 | PayPal + 信用卡 | 信用卡 + 支付宝/微信/本地支付 |
| Webhook 签名 | Stripe-Signature | 简化验证 | HMAC-SHA256 (x-signature) |
| 退款 | Refund API | Refund API | Refund API |
| 前端跳转 | session_url | approve_url | checkout_url |
| 数据库字段 | stripe_session_id / stripe_payment_intent_id | paypal_order_id / paypal_payment_id | awx_payment_intent_id |
| 幂等键 | stripe_payment_intent_id (uk_order_stripe_pi) | paypal_payment_id (uk_order_paypal_payment_id) | awx_payment_intent_id (uk_order_awx_pi) |

**接入工作量评估：**

| 模块 | 工作量 | 说明 |
|------|:------:|------|
| AirwallexPaymentService | 2-3天 | 主要业务逻辑，参考 StripePaymentService 实现 |
| Webhook / Controller | 0.5天 | 新增端点和路由 |
| 数据库迁移 | 0.5天 | 新增字段和索引 |
| application.yml 配置 | 0.5天 | 新增配置段 |
| 前端改造 | 1天 | 支付方式选择 + 回调页面 |
| 沙盒联调测试 | 1-2天 | 端到端测试 |
| **总计** | **6-8天** | |

---

> **文档结束**  
> 本文档覆盖空中云汇接入的完整设计方案，包括 API 对接、后端代码、数据库变更、前端改造、部署配置和测试方案，可作为开发的完整需求依据。