package com.duanju.service;

import com.duanju.dto.payment.StorePaymentContext;
import com.duanju.entity.PointProduct;
import com.duanju.service.entity.PointProductService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Google Play In-App Billing (IAB) 服务。
 *
 * <p>支持两种支付确认模式:</p>
 * <ol>
 *   <li><b>客户端主动校验 (verifyPurchase)</b>:客户端上传 INAPP_PURCHASE_DATA + SIGNATURE + purchaseToken,
 *       服务端本地 RSA/SHA1 验签后发放积分。适合快速闭环,但客户端可被绕过。</li>
 *   <li><b>RTDN 被动接收 (handleRtdnEvent)</b>:Google Real-time Developer Notifications 通过 Pub/Sub
 *       推送 ONE_TIME_PRODUCT_PURCHASED / ONE_TIME_PRODUCT_CANCELED 等事件到 /api/webhooks/google/notify,
 *       服务端被动接收后发放/扣回积分,是更可靠的服务端确认方式。</li>
 * </ol>
 *
 * <h3>RTDN 与客户端校验的关系:</h3>
 * <p>两种模式互为补充:客户端校验保证即时到账,RTDN 保证最终一致性 (RTDN 可能延迟数秒到达)。
 * 幂等列 store_transaction_id=purchaseToken 确保两条路径不会重复发积分。</p>
 *
 * <h3>核心流程 (两种模式通用):</h3>
 * <ol>
 *   <li>客户端调 POST /api/user/orders, payChannel=GOOGLE_PLAY, 创建 PENDING 订单 (orderNo=UUID)。</li>
 *   <li>客户端调 Google Play Billing Library launchBillingFlow(),把 orderNo 写入
 *       BillingFlowParams.obfuscatedAccountId 或 developerPayload (UUID 格式),便于服务端关联。</li>
 *   <li>Google Play 完成支付后:
 *       <ul>
 *         <li>客户端主动模式:客户端拿到 Purchase 对象,调 POST /api/user/orders/verify 主动校验。</li>
 *         <li>RTDN 模式:Google 通过 Pub/Sub 推送事件到 /api/webhooks/google/notify,服务端被动处理。</li>
 *       </ul>
 *   </li>
 *   <li>服务端通过 sku → 内部 productId 反查,按 orderNo + PENDING 拿 userId,
 *       调 orderService.markPaidByStore(CHANNEL_GOOGLE_PLAY) 落单 + 发积分。</li>
 * </ol>
 *
 * <h3>幂等:</h3>
 * <ul>
 *   <li>通用列 store_transaction_id=purchaseToken (uk_order_store_tx 唯一索引)。</li>
 *   <li>渠道列 google_purchase_token=purchaseToken (uk_order_google_purchase_token 唯一索引)。</li>
 *   <li>双重唯一索引确保同一 purchaseToken 无论走哪条路径都绝不重复发积分。</li>
 * </ul>
 *
 * <h3>公钥来源:</h3>
 * <p>登录 Google Play Console → Monetization setup → Licensing →
 * "Base64-encoded RSA public key",复制出来通过 GOOGLE_PLAY_PUBLIC_KEY_BASE64 环境变量注入。
 * 注意:这是应用级别的公钥 (每个应用不同),不要和 Upload Key / App Signing Key 混淆。</p>
 *
 * <h3>安全性说明:</h3>
 * <ul>
 *   <li>本地验签保证 INAPP_PURCHASE_DATA JSON 确实由 Google 私钥签名,防止客户端伪造。</li>
 *   <li>Developer API 二次验签 (可选,生产强烈建议开启):在本地 RSA 验签通过后,
 *       调 androidpublisher.purchases.products.get 主动查询 Google 侧真实状态,
 *       可防"伪造签名 + 已退款/已取消 purchaseToken 被重新提交"等攻击。
 *       开关:duanju.google-play.developer-api-verify=true +
 *       duanju.google-play.service-account-json=完整 JSON。</li>
 *   <li>RTDN 模式本身依赖 Pub/Sub push endpoint 鉴权 token (X-Duanju-Webhook-Token) 保证来源,
 *       默认不再做二次 API 验签 (避免高频调用 Google API),如需开启可在 RTDN 流程加调 verifyWithDeveloperApi。</li>
 * </ul>
 */
@Service
public class GooglePlayIapService {
    private static final Logger log = LoggerFactory.getLogger(GooglePlayIapService.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    private final OrderService orderService;
    private final PointProductService pointProductService;

    @Value("${duanju.google-play.enabled:false}")
    private boolean enabled;

    @Value("${duanju.google-play.package-name:}")
    private String packageName;

    @Value("${duanju.google-play.public-key-base64:}")
    private String publicKeyBase64;

    @Value("${duanju.google-play.webhook-auth-token:}")
    private String webhookAuthToken;

    /** PRODUCTION 或 SANDBOX,用于标记订单来源环境。沙盒测试时需设置为 SANDBOX。 */
    @Value("${duanju.google-play.environment:PRODUCTION}")
    private String environment;

    /** 是否启用 Developer API 二次验签 (androidpublisher.purchases.products.get)。
     *  生产环境强烈建议开启,通过 Google 官方 API 验证 purchaseToken 真实性。 */
    @Value("${duanju.google-play.developer-api-verify:false}")
    private boolean developerApiVerify;

    /** Google Service Account JSON 文本 (从 Google Cloud Console 下载的完整 JSON)。
     *  该 Service Account 必须在 Google Play Console → Users and permissions 添加并授予权限,
     *  否则 purchases.products.get 会返回 401/403。 */
    @Value("${duanju.google-play.service-account-json:}")
    private String serviceAccountJson;

    /** RTDN 已处理事件 ID 去重 (内存 LRU,容量 10000,单实例内有效)。
     *  多实例部署时各实例缓存独立,同一事件可能被不同实例重复处理,
     *  最终由 DB 唯一索引 (uk_order_store_tx / uk_order_google_purchase_token) 兜底防重。
     *  此处仅作为减少 DB 查询的 best-effort 优化。 */
    private final ConcurrentHashMap<String, Long> processedEventIds = new ConcurrentHashMap<>();
    private final AtomicInteger eventCounter = new AtomicInteger(0);
    private static final int EVENT_CACHE_MAX = 10000;

    private PublicKey rsaPublicKey;

    /** Google OAuth2 credentials,用于调用 Google Play Developer API (purchases.products.get 二次验签)。
     *  仅在 developerApiVerify=true 且 serviceAccountJson 已配置时初始化成功。 */
    private GoogleCredentials googleCredentials;

    /** RestTemplate 用于直接调用 Google Play Developer REST API,避免引入重型 google-api-services-androidpublisher SDK。
     *  与 PayPalPaymentService 一致:本地 OAuth2 取 token + RestTemplate 直调 REST。 */
    private final RestTemplate restTemplate = new RestTemplate();

    public GooglePlayIapService(OrderService orderService, PointProductService pointProductService) {
        this.orderService = orderService;
        this.pointProductService = pointProductService;
    }

    @PostConstruct
    void init() {
        if (!enabled) {
            log.warn("Google Play IAP disabled, verify endpoints will reject requests");
            return;
        }
        if (publicKeyBase64 == null || publicKeyBase64.isBlank()) {
            log.error("Google Play public-key-base64 not configured, google play disabled");
            enabled = false;
            return;
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64.trim());
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            rsaPublicKey = kf.generatePublic(spec);
            log.info("Google Play IAP initialized: packageName={}, rsaKeyBits={}, environment={}, developerApiVerify={}",
                    packageName, rsaPublicKey.getAlgorithm() != null ? "RSA" : "N/A", environment, developerApiVerify);
        } catch (Exception ex) {
            log.error("Google Play RSA public key parse failed: {}", ex.getMessage(), ex);
            enabled = false;
            rsaPublicKey = null;
            return;
        }
        initDeveloperApiCredentials();
    }

    /**
     * 初始化 GoogleCredentials (Service Account JWT 鉴权)。
     *
     * <p>前置条件:</p>
     * <ul>
     *   <li>developerApiVerify=true 才初始化,否则跳过 (verifyWithDeveloperApi 也直接放行)</li>
     *   <li>serviceAccountJson 必须为 Google Cloud Console 下载的完整 JSON 文本</li>
     *   <li>scope 固定为 https://www.googleapis.com/auth/androidpublisher (Google Play Developer API 唯一可用 scope)</li>
     * </ul>
     *
     * <p>初始化失败不会禁用 google play 整体功能,但 verifyWithDeveloperApi 会抛 IllegalStateException 拒绝发积分,
     *  避免在 developerApiVerify=true 的部署场景下静默降级绕过二次验签。</p>
     */
    private void initDeveloperApiCredentials() {
        if (!developerApiVerify) {
            log.info("Google Play developerApiVerify=false, skip credentials init (only local RSA verification)");
            return;
        }
        if (serviceAccountJson == null || serviceAccountJson.isBlank()) {
            log.error("Google Play developerApiVerify=true but service-account-json not configured, "
                    + "verifyPurchase will reject all requests until configured");
            return;
        }
        try {
            byte[] jsonBytes = serviceAccountJson.trim().getBytes(StandardCharsets.UTF_8);
            googleCredentials = GoogleCredentials.fromStream(new ByteArrayInputStream(jsonBytes))
                    .createScoped("https://www.googleapis.com/auth/androidpublisher");
            log.info("Google Play Developer API credentials initialized (developerApiVerify enabled)");
        } catch (Exception ex) {
            log.error("Google Play Developer API credentials init failed: {}", ex.getMessage(), ex);
        }
    }

    // --- Public API ---

    public boolean isEnabled() {
        return enabled && rsaPublicKey != null;
    }

    /**
     * 客户端上传 INAPP_PURCHASE_DATA + SIGNATURE + purchaseToken,
     * 本地 RSA 验签通过后落单 + 发放积分。
     *
     * @param purchaseData     Google Play Billing Library Purchase.getOriginalJson()
     *                         (INAPP_PURCHASE_DATA JSON 字符串,签名原文)
     * @param purchaseSignature Purchase.getSignature() (RSA/SHA1 with PKCS#1 v1.5 签名,Base64 编码)
     * @param purchaseToken    Purchase.getPurchaseToken() (唯一标识,用于幂等去重)
     * @param orderNo          客户端预创建 PENDING 订单号 (可选;传了则落到对应订单,便于关联)
     * @return 处理后的订单 Map
     */
    @Transactional
    public Map<String, Object> verifyPurchase(String purchaseData, String purchaseSignature,
                                               String purchaseToken, String orderNo) {
        assertEnabled();
        if (purchaseData == null || purchaseData.isBlank()) {
            throw new IllegalArgumentException("purchaseData (INAPP_PURCHASE_DATA) required");
        }
        if (purchaseSignature == null || purchaseSignature.isBlank()) {
            throw new IllegalArgumentException("purchaseSignature (SIGNATURE) required");
        }
        if (purchaseToken == null || purchaseToken.isBlank()) {
            throw new IllegalArgumentException("purchaseToken required");
        }

        // 1. 幂等:本地已处理过的直接返回 (按 purchaseToken,因为 uk_order_google_purchase_token
        //    建在该列上;同时 findOrderByStoreTransactionId 也会命中 store_transaction_id=purchaseToken)
        Map<String, Object> existing = orderService.findOrderByStoreTransactionId(purchaseToken);
        if (existing != null) {
            log.info("Google Play verifyPurchase: idempotent hit, purchaseToken={}", purchaseToken);
            return existing;
        }

        // 2. RSA/SHA1 本地验签 (PKCS#1 v1.5 填充 → "SHA1withRSA")
        if (!verifySignature(purchaseData, purchaseSignature)) {
            log.warn("Google Play verifyPurchase: signature verification failed, purchaseToken={}", purchaseToken);
            throw new IllegalArgumentException("google play signature verification failed");
        }

        // 3. 解析 INAPP_PURCHASE_DATA JSON
        JsonNode root;
        try {
            root = JSON.readTree(purchaseData);
        } catch (Exception ex) {
            log.warn("Google Play verifyPurchase: cannot parse INAPP_PURCHASE_DATA JSON, purchaseToken={}",
                    purchaseToken, ex);
            throw new IllegalArgumentException("invalid INAPP_PURCHASE_DATA JSON: " + ex.getMessage());
        }

        // 4. 校验关键 JSON 字段 (防御性校验,避免签名绕过/JSON 篡改等边缘漏洞)
        String jsonPackageName = text(root, "packageName");
        String jsonSku = text(root, "productId");
        if (jsonSku == null || jsonSku.isBlank()) {
            // 老版本 Billing Library 可能字段名是 "productId" 而非 "sku",两者都兜底
            jsonSku = text(root, "sku");
        }
        String jsonOrderId = text(root, "orderId");
        String jsonPurchaseToken = text(root, "purchaseToken");
        int purchaseState = intValue(root, "purchaseState", -1);
        String jsonCurrency = text(root, "currency");
        Long priceCentsMicros = longValue(root, "priceAmountMicros");
        Long priceCents = priceCentsMicros != null ? priceCentsMicros / 10000 : null;

        if (packageName != null && !packageName.isBlank() && !packageName.equals(jsonPackageName)) {
            log.warn("Google Play verifyPurchase: packageName mismatch, config={}, json={}, purchaseToken={}",
                    packageName, jsonPackageName, purchaseToken);
            throw new IllegalArgumentException("packageName mismatch");
        }
        if (purchaseState != 0) {
            log.warn("Google Play verifyPurchase: purchaseState != 0 (not purchased), state={}, purchaseToken={}",
                    purchaseState, purchaseToken);
            throw new IllegalArgumentException("purchase is not in purchased state (state=" + purchaseState + ")");
        }
        if (jsonSku == null || jsonSku.isBlank()) {
            throw new IllegalArgumentException("productId/sku missing in INAPP_PURCHASE_DATA");
        }
        // purchaseToken 也兜底校验:JSON 里的 token 要等于客户端上传的 token (防止用 A 的签名配 B 的 token)
        if (jsonPurchaseToken != null && !jsonPurchaseToken.isBlank() && !purchaseToken.equals(jsonPurchaseToken)) {
            log.warn("Google Play verifyPurchase: purchaseToken mismatch between param and JSON, param={}, json={}",
                    purchaseToken, jsonPurchaseToken);
            throw new IllegalArgumentException("purchaseToken mismatch between param and INAPP_PURCHASE_DATA");
        }

        // 4.5 (可选) Developer API 二次验签:调用 androidpublisher.purchases.products.get 验证 purchaseToken 真实性
        //     本地 RSA 只能验证签名未被篡改,无法验证 purchaseToken 是否真的在 Google 侧存在且仍有效;
        //     二次验签可防伪造签名 + 已退款/已取消的 purchaseToken 被重新提交
        //     行为:developerApiVerify=false → 内部 return 放行;=true 但 credentials 缺失 → 抛异常拒绝发积分
        verifyWithDeveloperApi(jsonSku, purchaseToken);

        // 5. 反查内部 productId (point_product.store_product_id = Google sku)
        Long productId = mapStoreProductToInternal(jsonSku);
        if (productId == null) {
            log.warn("Google Play verifyPurchase: no product mapping for sku={}, purchaseToken={}",
                    jsonSku, purchaseToken);
            throw new IllegalArgumentException("no product mapping for google sku: " + jsonSku);
        }

        // 6. 解析 userId:优先通过 orderNo 找 PENDING 订单 (客户端已预创建订单的规范路径)
        Long userId = null;
        if (orderNo != null && !orderNo.isBlank()) {
            userId = orderService.findPendingOrderUserId(orderNo);
        }
        if (userId == null) {
            // 兜底:如果 obfuscatedAccountId 没写入 orderNo,客户端也没传 orderNo,
            // 但未来可以通过 developerPayload 或"账号绑定 token"再反查;
            // 本骨架保守抛出,避免积分落到错误的用户上
            log.warn("Google Play verifyPurchase: cannot resolve userId, orderNo={}, purchaseToken={}",
                    orderNo, purchaseToken);
            throw new IllegalArgumentException("cannot resolve userId: orderNo (PENDING) not found or missing. "
                    + "Ensure client sets obfuscatedAccountId=orderNo and passes orderNo.");
        }

        // 7. 调通用 markPaidByStore 落单 + 发积分 (内部已做幂等,再次购买同一 token 会直接返回)
        StorePaymentContext ctx = new StorePaymentContext(
                StorePaymentContext.CHANNEL_GOOGLE_PLAY,
                orderNo,
                userId,
                productId,
                purchaseToken,                 // storeTransactionId = purchaseToken (幂等主键)
                purchaseToken,                 // storeOriginalTransactionId = purchaseToken (非订阅场景相同)
                jsonSku,                       // storeProductId = Google sku
                jsonPackageName,               // storeBundleId = Google packageName
                environment,                      // PRODUCTION / SANDBOX (由配置或 JSON 检测决定)
                receiptHash(purchaseData),     // SHA-256(purchaseData) 摘要,审计去重
                0,
                priceCents != null ? priceCents.intValue() : null,
                jsonCurrency != null ? jsonCurrency.toUpperCase() : null
        );
        orderService.markPaidByStore(ctx);
        log.info("Google Play verifyPurchase: order paid, sku={}, purchaseToken={}, orderId={}, userId={}, productId={}",
                jsonSku, purchaseToken, jsonOrderId, userId, productId);

        return orderService.findOrderByStoreTransactionId(purchaseToken);
    }

    // --- Developer API 二次验签 ---

    /**
     * 调用 Google Play Developer API (purchases.products.get) 二次验证 purchaseToken 真实性。
     *
     * <p>触发条件:仅在 {@code developerApiVerify=true} 且 GoogleCredentials 初始化成功时生效。</p>
     *
     * <p>响应字段含义 (Google 官方文档):</p>
     * <ul>
     *   <li>purchaseState: 0=Purchased (已购买), 1=Canceled (已取消)</li>
     *   <li>consumptionState: 0=Yet to be consumed, 1=Consumed (积分商品无所谓消费状态)</li>
     *   <li>orderId: Google 侧生成的订单 ID (审计用)</li>
     *   <li>purchaseTimeMillis: 购买时间戳</li>
     * </ul>
     *
     * <p>失败处理策略 (保守优先,防止绕过验签直接发积分):</p>
     * <ul>
     *   <li>404 (Not Found):purchaseToken 在 Google 侧不存在 → 抛 IAE (拒绝发积分)</li>
     *   <li>401/403 (Auth Failed):Service Account 未在 Play Console 添加/未授权 → 抛 ISE (拒绝发积分并告警)</li>
     *   <li>purchaseState≠0:已退款/已取消 → 抛 IAE (拒绝发积分)</li>
     *   <li>其他 RestClientException:网络/Google 侧故障 → 抛 ISE (拒绝,客户端可重试,幂等保证不会重复发积分)</li>
     * </ul>
     *
     * @param sku 商品 SKU (productId,与 point_product.store_product_id 对应)
     * @param purchaseToken Google Play Billing 返回的 purchaseToken (幂等主键)
     */
    private void verifyWithDeveloperApi(String sku, String purchaseToken) {
        if (!developerApiVerify) {
            return;
        }
        if (googleCredentials == null) {
            log.error("Google Play developerApiVerify=true but credentials not initialized, rejecting to prevent bypass, purchaseToken={}",
                    purchaseToken);
            throw new IllegalStateException("google play developer api verify enabled but credentials not configured");
        }
        if (packageName == null || packageName.isBlank()) {
            log.error("Google Play developerApiVerify: package-name not configured, cannot call purchases.products.get, purchaseToken={}",
                    purchaseToken);
            throw new IllegalStateException("google play package-name not configured");
        }

        long t1 = System.nanoTime();
        // 1. 取 OAuth2 access token (GoogleCredentials 内部自动 refresh,带本地缓存)
        String accessToken;
        try {
            googleCredentials.refreshIfExpired();
            AccessToken token = googleCredentials.getAccessToken();
            if (token == null || token.getTokenValue() == null || token.getTokenValue().isBlank()) {
                throw new IllegalStateException("google play access token unavailable");
            }
            accessToken = token.getTokenValue();
        } catch (Exception ex) {
            log.error("Google Play developer api: get access token failed, purchaseToken={}", purchaseToken, ex);
            throw new IllegalStateException("google play access token failed: " + ex.getMessage());
        }

        // 2. GET https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{packageName}/purchases/products/{sku}/tokens/{token}
        String url = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
                + URLEncoder.encode(packageName, StandardCharsets.UTF_8)
                + "/purchases/products/"
                + URLEncoder.encode(sku, StandardCharsets.UTF_8)
                + "/tokens/"
                + URLEncoder.encode(purchaseToken, StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> resp;
        try {
            resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("Google Play developer api: purchase not found (404), sku={}, purchaseToken={}", sku, purchaseToken);
            throw new IllegalArgumentException("google play purchase not found: " + purchaseToken);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
            log.error("Google Play developer api: auth failed ({}), service account not granted in Play Console? url={}, body={}",
                    ex.getStatusCode(), url, ex.getResponseBodyAsString());
            throw new IllegalStateException("google play developer api auth failed: " + ex.getStatusCode()
                    + ", check if service account is added in Play Console Users and permissions");
        } catch (RestClientException ex) {
            log.error("Google Play developer api: call failed, purchaseToken={}, url={}", purchaseToken, url, ex);
            throw new IllegalStateException("google play developer api call failed: " + ex.getMessage());
        }

        // 3. 解析响应,校验 purchaseState
        try {
            JsonNode body = JSON.readTree(resp.getBody());
            int purchaseState = body.path("purchaseState").asInt(-1);
            int consumptionState = body.path("consumptionState").asInt(-1);
            String orderId = body.path("orderId").asText(null);
            String purchaseTimeMillis = body.path("purchaseTimeMillis").asText(null);
            String regionCode = body.path("regionCode").asText(null);

            if (purchaseState != 0) {
                log.warn("Google Play developer api: purchaseState={}, expected 0 (Purchased), sku={}, purchaseToken={}, orderId={}",
                        purchaseState, sku, purchaseToken, orderId);
                throw new IllegalArgumentException("google play purchase not in purchased state (state="
                        + purchaseState + ", expected 0). Token may be canceled or refunded.");
            }
            long apiMs = (System.nanoTime() - t1) / 1_000_000;
            log.info("Google Play developer api verify: ok, sku={}, purchaseToken={}, orderId={}, purchaseTime={}, consumptionState={}, region={}, apiMs={}",
                    sku, purchaseToken, orderId, purchaseTimeMillis, consumptionState, regionCode, apiMs);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Google Play developer api: parse response failed, purchaseToken={}, body={}",
                    purchaseToken, resp.getBody(), ex);
            throw new IllegalStateException("google play developer api parse failed: " + ex.getMessage());
        }
    }

    // --- RTDN (Real-time Developer Notifications) ---

    /**
     * 处理 Google Play RTDN Pub/Sub 推送事件。
     *
     * <p>RTDN 消息格式 (Pub/Sub envelope):</p>
     * <pre>
     * {
     *   "message": {
     *     "attributes": { "packageName": "...", "eventId": "...", "eventType": "ONE_TIME_PRODUCT_PURCHASED" },
     *     "data": "&lt;base64 PurchaseOrder JSON&gt;",
     *     "messageId": "...",
     *     "publishTime": "..."
     *   },
     *   "subscription": "..."
     * }
     * </pre>
     *
     * <p>PurchaseOrder data 格式 (base64 解码后):</p>
     * <pre>
     * { "purchaseToken": "...", "productId": "...", "orderId": "...",
     *   "purchaseTimeMillis": "...", "purchaseState": 0,
     *   "developerPayload": "...", "currencyCode": "USD", "priceAmountMicros": 1990000 }
     * </pre>
     *
     * <p>支持的事件类型:</p>
     * <ul>
     *   <li>ONE_TIME_PRODUCT_PURCHASED → 发积分 (markPaidByStore)</li>
     *   <li>ONE_TIME_PRODUCT_CANCELED → 扣回积分 (refundByStore)</li>
     *   <li>SUBSCRIPTION_PURCHASED / SUBSCRIPTION_RENEWED → 发积分</li>
     *   <li>SUBSCRIPTION_CANCELED / SUBSCRIPTION_REVOKED → 扣回积分</li>
     * </ul>
     */
    @Transactional
    public void handleRtdnEvent(Map<String, Object> payload) {
        if (!enabled) {
            log.warn("Google Play IAP not enabled, ignoring RTDN event (returning 200 to stop retries)");
            return;
        }
        // 1. 解析 Pub/Sub envelope
        Map<String, Object> message = castMap(payload.get("message"));
        if (message == null) {
            throw new IllegalArgumentException("RTDN: missing 'message' in payload");
        }
        Map<String, String> attributes = castStringMap(message.get("attributes"));
        String eventType = attributes != null ? attributes.get("eventType") : null;
        String eventId = attributes != null ? attributes.get("eventId") : null;
        String attrPackageName = attributes != null ? attributes.get("packageName") : null;
        String dataB64 = message.get("data") instanceof String s ? s : null;

        log.info("Google Play RTDN: eventId={}, eventType={}, packageName={}", eventId, eventType, attrPackageName);

        if (eventType == null || eventType.isBlank()) {
            log.warn("Google Play RTDN: missing eventType, eventId={}", eventId);
            return;
        }
        if (dataB64 == null || dataB64.isBlank()) {
            log.warn("Google Play RTDN: missing data, eventId={}", eventId);
            return;
        }

        // 2. Base64 解码 → PurchaseOrder JSON
        String decodedData;
        try {
            decodedData = new String(Base64.getDecoder().decode(dataB64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            log.warn("Google Play RTDN: base64 decode failed, eventId={}, error={}", eventId, ex.getMessage());
            throw new IllegalArgumentException("RTDN data base64 decode failed: " + ex.getMessage());
        }

        JsonNode orderData;
        try {
            orderData = JSON.readTree(decodedData);
        } catch (Exception ex) {
            log.warn("Google Play RTDN: cannot parse PurchaseOrder JSON, eventId={}", eventId, ex);
            throw new IllegalArgumentException("RTDN PurchaseOrder JSON parse failed: " + ex.getMessage());
        }

        String purchaseToken = text(orderData, "purchaseToken");
        String sku = text(orderData, "productId");
        String developerPayload = text(orderData, "developerPayload");
        String jsonPackageName = text(orderData, "packageName");
        String currency = text(orderData, "currencyCode");
        Long priceMicros = longValue(orderData, "priceAmountMicros");
        Long priceCentsRTDN = priceMicros != null ? priceMicros / 10000 : null;

        // 3. eventId 级别去重 (防止 Pub/Sub 重试重复处理)
        if (eventId != null && !eventId.isBlank()) {
            if (processedEventIds.putIfAbsent(eventId, System.currentTimeMillis()) != null) {
                log.info("Google Play RTDN: eventId already processed, skipping, eventId={}", eventId);
                return;
            }
            // LRU 清理:超过容量时清空一半
            if (eventCounter.incrementAndGet() > EVENT_CACHE_MAX) {
                log.info("Google Play RTDN: eventId cache full, clearing half ({} entries)", EVENT_CACHE_MAX);
                int count = 0;
                for (String key : processedEventIds.keySet()) {
                    if (count++ > EVENT_CACHE_MAX / 2) break;
                    processedEventIds.remove(key);
                }
                eventCounter.set(processedEventIds.size());
            }
        }

        if (purchaseToken == null || purchaseToken.isBlank()) {
            log.warn("Google Play RTDN: missing purchaseToken, eventId={}", eventId);
            return;
        }
        if (sku == null || sku.isBlank()) {
            log.warn("Google Play RTDN: missing productId, eventId={}, purchaseToken={}", eventId, purchaseToken);
            return;
        }

        // 4. 幂等:已处理过的 purchaseToken 直接跳过
        Map<String, Object> existing = orderService.findOrderByStoreTransactionId(purchaseToken);
        boolean isPaidEvent = isPurchaseEvent(eventType);
        boolean isCancelEvent = isCancelEvent(eventType);

        if (isPaidEvent && existing != null) {
            log.info("Google Play RTDN: idempotent (already PAID), purchaseToken={}, eventType={}", purchaseToken, eventType);
            return;
        }
        if (isCancelEvent && existing == null) {
            log.warn("Google Play RTDN: cancel event but no order found, purchaseToken={}, eventType={}", purchaseToken, eventType);
            return;
        }

        // 4. 事件分发
        switch (eventType) {
            case "ONE_TIME_PRODUCT_PURCHASED", "SUBSCRIPTION_PURCHASED", "SUBSCRIPTION_RENEWED" -> {
                if (existing != null) {
                    log.info("Google Play RTDN: idempotent hit for eventType={}, purchaseToken={}", eventType, purchaseToken);
                    return;
                }
                handleRtdnPurchase(purchaseToken, sku, developerPayload, jsonPackageName,
                        currency, priceCentsRTDN, eventId, eventType);
            }
            case "ONE_TIME_PRODUCT_CANCELED", "SUBSCRIPTION_CANCELED", "SUBSCRIPTION_REVOKED" -> {
                handleRtdnCancel(purchaseToken, eventType);
            }
            default -> log.info("Google Play RTDN: ignoring eventType={}, eventId={}", eventType, eventId);
        }
    }

    /** RTDN 购买事件:映射商品 + 解析 userId + 发积分。 */
    private void handleRtdnPurchase(String purchaseToken, String sku, String developerPayload,
                                     String jsonPackageName, String currency, Long priceCents,
                                     String eventId, String eventType) {
        Long productId = mapStoreProductToInternal(sku);
        if (productId == null) {
            log.warn("Google Play RTDN: no product mapping for sku={}, purchaseToken={}, eventType={}",
                    sku, purchaseToken, eventType);
            return;
        }
        // 优先通过 developerPayload (客户端设置的 orderNo) 找 PENDING 订单拿 userId
        Long userId = null;
        String orderNo = developerPayload;
        if (developerPayload != null && !developerPayload.isBlank()) {
            userId = orderService.findPendingOrderUserId(developerPayload);
        }
        if (userId == null) {
            // 兜底:直接用 orderNo 作为标识创建 PAID 订单 (需要 userId,失败则跳过)
            log.warn("Google Play RTDN: cannot resolve userId for purchaseToken={}, sku={}, developerPayload={}, eventType={}. "
                    + "Ensure client sets developerPayload=orderNo in BillingFlowParams.",
                    purchaseToken, sku, developerPayload, eventType);
            return;
        }

        StorePaymentContext ctx = new StorePaymentContext(
                StorePaymentContext.CHANNEL_GOOGLE_PLAY,
                orderNo,
                userId,
                productId,
                purchaseToken,
                purchaseToken,
                sku,
                jsonPackageName != null ? jsonPackageName : packageName,
                environment,
                eventId,
                0,
                priceCents != null ? priceCents.intValue() : null,
                currency != null ? currency.toUpperCase() : null
        );
        orderService.markPaidByStore(ctx);
        log.info("Google Play RTDN: purchase processed, purchaseToken={}, sku={}, productId={}, userId={}, eventType={}",
                purchaseToken, sku, productId, userId, eventType);
    }

    /** RTDN 取消/退款事件:通过 refundByStore 扣回积分。 */
    private void handleRtdnCancel(String purchaseToken, String eventType) {
        log.info("Google Play RTDN: cancel event, purchaseToken={}, eventType={}", purchaseToken, eventType);
        try {
            orderService.refundByStore(purchaseToken, "GOOGLE_PLAY_" + eventType);
            log.info("Google Play RTDN: cancel processed, purchaseToken={}, eventType={}", purchaseToken, eventType);
        } catch (IllegalArgumentException ex) {
            log.warn("Google Play RTDN: cancel skip (order not found or not PAID), purchaseToken={}, eventType={}, error={}",
                    purchaseToken, eventType, ex.getMessage());
        }
    }

    private boolean isPurchaseEvent(String eventType) {
        return "ONE_TIME_PRODUCT_PURCHASED".equals(eventType)
                || "SUBSCRIPTION_PURCHASED".equals(eventType)
                || "SUBSCRIPTION_RENEWED".equals(eventType);
    }

    private boolean isCancelEvent(String eventType) {
        return "ONE_TIME_PRODUCT_CANCELED".equals(eventType)
                || "SUBSCRIPTION_CANCELED".equals(eventType)
                || "SUBSCRIPTION_REVOKED".equals(eventType);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        if (o instanceof Map m) {
            return (Map<String, Object>) m;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> castStringMap(Object o) {
        if (o instanceof Map m) {
            Map<String, String> result = new java.util.HashMap<>();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) m).entrySet()) {
                if (e.getValue() != null) {
                    result.put(e.getKey(), e.getValue().toString());
                }
            }
            return result;
        }
        return null;
    }

    public String getWebhookAuthToken() {
        return webhookAuthToken;
    }

    // --- Internal helpers ---

    /**
     * RSA/SHA1 with PKCS#1 v1.5 本地验签。
     *
     * <p>Google Play Console 导出的公钥是 X.509 SubjectPublicKeyInfo (SPKI) 格式的 Base64,
     * init 阶段已解析为 java.security.PublicKey。签名算法固定为 "SHA1withRSA"。</p>
     */
    private boolean verifySignature(String purchaseData, String signatureBase64) {
        if (rsaPublicKey == null) {
            return false;
        }
        try {
            byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64.trim());
            Signature sig = Signature.getInstance("SHA1withRSA");
            sig.initVerify(rsaPublicKey);
            sig.update(purchaseData.getBytes(StandardCharsets.UTF_8));
            return sig.verify(signatureBytes);
        } catch (IllegalArgumentException ex) {
            // Base64 解码失败 → 签名非法
            log.warn("Google Play verifySignature: signature base64 decode failed: {}", ex.getMessage());
            return false;
        } catch (Exception ex) {
            // JCA 异常 (NoSuchAlgorithmException / InvalidKeyException / SignatureException)
            // 都视为签名校验失败,不抛出
            log.warn("Google Play verifySignature: JCA exception: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Google sku → 内部 point_product.id 反查。
     * 与 AppleIapService.mapStoreProductToInternal 逻辑保持一致:
     * 只映射 status=1 (上架) 的商品,应用层兜底拒绝下架/删除商品。
     */
    Long mapStoreProductToInternal(String storeProductId) {
        if (storeProductId == null || storeProductId.isBlank()) {
            return null;
        }
        PointProduct product = pointProductService.lambdaQuery()
                .eq(PointProduct::getStoreProductId, storeProductId)
                .eq(PointProduct::getStatus, 1)
                .last("limit 1")
                .one();
        if (product == null) {
            log.warn("Google Play mapStoreProduct: no product found, storeProductId={}", storeProductId);
            return null;
        }
        if (!Integer.valueOf(1).equals(product.getStatus())) {
            log.warn("Google Play mapStoreProduct: product offline (app-layer guard), storeProductId={}, productId={}",
                    storeProductId, product.getId());
            return null;
        }
        log.info("Google Play mapStoreProduct: mapped ok, storeProductId={} → productId={}",
                storeProductId, product.getId());
        return product.getId();
    }

    /** INAPP_PURCHASE_DATA JSON 的 SHA-256 摘要,用于审计/去重。 */
    String receiptHash(String purchaseData) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(purchaseData.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return null;
        }
    }

    private void assertEnabled() {
        if (!isEnabled()) {
            throw new IllegalArgumentException("支付渠道 Google Play 暂未开通或公钥未配置，请切换其他支付方式");
        }
    }

    // --- JSON helper (避免额外依赖 Guava / Apache Commons,保持轻量) ---

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        return v.asText();
    }

    private static int intValue(JsonNode node, String field, int defaultValue) {
        if (node == null) return defaultValue;
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.canConvertToInt()) return defaultValue;
        return v.asInt();
    }

    private static Integer integer(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.canConvertToLong()) return null;
        try {
            return v.asInt();
        } catch (Exception ex) {
            return null;
        }
    }

    private static Long longValue(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.canConvertToLong()) return null;
        try {
            return v.asLong();
        } catch (Exception ex) {
            return null;
        }
    }
}
