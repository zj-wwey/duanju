package com.duanju.service;

import com.apple.itunes.storekit.client.APIException;
import com.apple.itunes.storekit.client.AppStoreServerAPIClient;
import com.apple.itunes.storekit.model.Data;
import com.apple.itunes.storekit.model.Environment;
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.NotificationTypeV2;
import com.apple.itunes.storekit.model.ResponseBodyV2DecodedPayload;
import com.apple.itunes.storekit.model.TransactionInfoResponse;
import com.apple.itunes.storekit.verification.SignedDataVerifier;
import com.apple.itunes.storekit.verification.VerificationException;
import com.duanju.dto.payment.StorePaymentContext;
import com.duanju.entity.PointProduct;
import com.duanju.service.entity.PointProductService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Apple In-App Purchase (StoreKit 2) 服务端校验与 Server Notification V2 处理。
 *
 * <p>核心流程:</p>
 * <ol>
 *   <li>客户端发起 IAP 前调 POST /api/user/orders 创建 PENDING 订单,得到 orderNo。</li>
 *   <li>客户端在 StoreKit 的 Payment.applicationUsername 中传入 orderNo,Apple 会在
 *       transaction.appAccountToken 字段以 UUID 形式回传 (要求 orderNo 为 UUID 格式,或额外维护映射)。</li>
 *   <li>Apple 完成支付后推送 Server Notification V2 (signedPayload) 到 POST /api/webhooks/apple/notify。</li>
 *   <li>本服务验签 + 解码 + 根据 notificationType 更新订单状态 + 发放积分。</li>
 *   <li>客户端也可主动调 verifyTransaction(transactionId) 触发服务端主动查询 Apple API 兜底。</li>
 * </ol>
 *
 * <p>幂等:user_order.store_transaction_id 上有唯一索引 uk_order_store_tx,
 * 同一 Apple transactionId 不会被重复处理。</p>
 *
 * <p>部署前置:从 https://www.apple.com/certificateauthority/ 下载 AppleRootCA-G3.cer,
 * 放到 src/main/resources/apple/AppleRootCA-G3.cer。</p>
 */
@Service
public class AppleIapService {
    private static final Logger log = LoggerFactory.getLogger(AppleIapService.class);

    private final OrderService orderService;
    private final PointProductService pointProductService;
    private final MeterRegistry meterRegistry;

    @Autowired
    @Lazy
    private AppleIapService self;

    @Value("${duanju.apple-iap.enabled:false}")
    private boolean enabled;

    @Value("${duanju.apple-iap.bundle-id:}")
    private String bundleId;

    @Value("${duanju.apple-iap.app-apple-id:0}")
    private long appAppleId;

    @Value("${duanju.apple-iap.issuer-id:}")
    private String issuerId;

    @Value("${duanju.apple-iap.key-id:}")
    private String keyId;

    @Value("${duanju.apple-iap.private-key:}")
    private String privateKey;

    @Value("${duanju.apple-iap.environment:SANDBOX}")
    private String environment;

    private SignedDataVerifier verifier;
    private AppStoreServerAPIClient apiClient;

    public AppleIapService(OrderService orderService, PointProductService pointProductService,
                          MeterRegistry meterRegistry) {
        this.orderService = orderService;
        this.pointProductService = pointProductService;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void init() {
        if (!enabled) {
            log.warn("Apple IAP disabled, webhook endpoints will reject notifications");
            return;
        }
        try (InputStream is = getClass().getResourceAsStream("/apple/AppleRootCA-G3.cer")) {
            if (is == null) {
                log.error("Apple Root CA not found at classpath:apple/AppleRootCA-G3.cer; verifier disabled. "
                        + "Download from https://www.apple.com/certificateauthority/");
                return;
            }
            Environment env = Environment.valueOf(environment.toUpperCase());
            // enableOnlineChecks=true 让 SDK 在线校验 Apple OCSP,生产环境推荐
            verifier = new SignedDataVerifier(Set.of(is), bundleId, appAppleId, env, true);
            log.info("Apple IAP verifier initialized: bundleId={}, env={}", bundleId, env);
        } catch (Exception ex) {
            log.error("Apple IAP verifier init failed", ex);
        }
        initApiClient();
    }

    private void initApiClient() {
        if (issuerId == null || issuerId.isBlank()
                || keyId == null || keyId.isBlank()
                || privateKey == null || privateKey.isBlank()) {
            log.warn("Apple IAP apiClient not configured (issuerId/keyId/privateKey missing), verifyTransaction disabled");
            return;
        }
        try {
            Environment env = Environment.valueOf(environment.toUpperCase());
            apiClient = new AppStoreServerAPIClient(issuerId, keyId, privateKey, bundleId, env);
            log.info("Apple IAP apiClient initialized: issuerId={}, env={}", issuerId, env);
        } catch (Exception ex) {
            log.warn("Apple IAP apiClient init failed, verifyTransaction will be unavailable: {}", ex.getMessage());
        }
    }

    /**
     * 处理 Apple Server Notification V2。
     *
     * @param signedPayload Apple 推送的 JWS 字符串 (请求体 {"signedPayload":"xxx.yyy.zzz"} 中的值)
     */
    public void handleNotification(String signedPayload) {
        if (!enabled || verifier == null) {
            log.warn("Apple IAP not enabled/verifier not initialized, ignoring webhook (returning 200 to stop retries)");
            return;
        }
        ResponseBodyV2DecodedPayload payload;
        try {
            payload = verifier.verifyAndDecodeNotification(signedPayload);
        } catch (VerificationException ex) {
            log.warn("Apple IAP notification signature verification failed: {}", ex.getMessage());
            throw new IllegalArgumentException("invalid apple notification signature");
        }
        NotificationTypeV2 type = payload.getNotificationType();
        Data data = payload.getData();
        if (data == null || data.getSignedTransactionInfo() == null) {
            log.info("Apple IAP notification {} has no transaction info, skip", type);
            return;
        }
        JWSTransactionDecodedPayload tx;
        try {
            tx = verifier.verifyAndDecodeTransaction(data.getSignedTransactionInfo());
        } catch (VerificationException ex) {
            log.warn("Apple IAP transaction signature verification failed: {}", ex.getMessage());
            throw new IllegalArgumentException("invalid apple transaction signature");
        }
        log.info("Apple IAP notification: type={}, subtype={}, txId={}, originalTxId={}, productId={}",
                type, payload.getSubtype(), tx.getTransactionId(),
                tx.getOriginalTransactionId(), tx.getProductId());
        try {
            switch (type) {
                // 购买/续费/兑换成功 → 发放积分
                case SUBSCRIBED, DID_RENEW, OFFER_REDEEMED -> self.handleSubscribed(tx);
                // 退款/撤销 → 扣回积分
                case REFUND, REVOKE -> self.handleRefund(tx, type.name());
                // 订阅过期 → 仅记录,不扣积分 (用户已享受的积分不动)
                case EXPIRED, GRACE_PERIOD_EXPIRED -> log.info("Apple IAP subscription expired: originalTxId={}",
                        tx.getOriginalTransactionId());
                // 续费失败 → 仅记录,等待 Apple 重试或用户处理
                case DID_FAIL_TO_RENEW -> log.warn("Apple IAP renewal failed: originalTxId={}",
                        tx.getOriginalTransactionId());
                default -> log.info("Apple IAP notification type {} not handled", type);
            }
        } catch (RuntimeException ex) {
            // 业务异常不应让 Apple 收到 5xx (Apple 会重试,可能导致重复处理)
            // 已记录日志,返回 200 让 Apple 停止重试;幂等逻辑会保证不重复发积分
            log.error("Apple IAP notification handling failed: type={}, txId={}",
                    type, tx.getTransactionId(), ex);
        }
    }

    /**
     * 客户端主动校验交易 (兜底流程)。
     *
     * <p>当客户端完成 IAP 但 webhook 未及时到达时,客户端可上传 transactionId 触发服务端主动查询 Apple API,
     * 验证交易真实性后发放积分。比纯 webhook 模式更可靠,推荐客户端在 IAP 完成后立即调用。</p>
     *
     * @param transactionId Apple StoreKit 2 返回的 transactionId (Transaction.id)
     * @return 处理后的订单信息
     */
    public Map<String, Object> verifyTransaction(String transactionId) {
        long start = System.nanoTime();
        log.info("Apple IAP verifyTransaction: start, transactionId={}", transactionId);

        // 1. 幂等:先查本地,已处理过直接返回
        long t1 = System.nanoTime();
        Map<String, Object> existing = orderService.findOrderByStoreTransactionId(transactionId);
        long localQueryMs = (System.nanoTime() - t1) / 1_000_000;
        if (existing != null) {
            long totalMs = (System.nanoTime() - start) / 1_000_000;
            log.info("Apple IAP verifyTransaction: idempotent hit (already processed), transactionId={}, localQueryMs={}, totalMs={}",
                    transactionId, localQueryMs, totalMs);
            return existing;
        }
        if (apiClient == null || verifier == null) {
            long totalMs = (System.nanoTime() - start) / 1_000_000;
            log.error("Apple IAP verifyTransaction: api client not initialized, transactionId={}, localQueryMs={}, totalMs={}",
                    transactionId, localQueryMs, totalMs);
            throw new IllegalArgumentException("支付渠道 Apple 应用内购买暂未开通，请切换其他支付方式");
        }

        // 2. 调 Apple App Store Server API 主动查询交易
        long t2 = System.nanoTime();
        TransactionInfoResponse info;
        JWSTransactionDecodedPayload tx;
        try {
            info = apiClient.getTransactionInfo(transactionId);
            tx = verifier.verifyAndDecodeTransaction(info.getSignedTransactionInfo());
        } catch (VerificationException ex) {
            long appleMs = (System.nanoTime() - t2) / 1_000_000;
            long totalMs = (System.nanoTime() - start) / 1_000_000;
            log.error("Apple IAP verifyTransaction: signature verification failed, transactionId={}, appleMs={}, totalMs={}",
                    transactionId, appleMs, totalMs);
            throw new IllegalArgumentException("apple transaction verification failed: " + ex.getMessage());
        } catch (APIException | java.io.IOException ex) {
            long appleMs = (System.nanoTime() - t2) / 1_000_000;
            long totalMs = (System.nanoTime() - start) / 1_000_000;
            log.error("Apple IAP verifyTransaction: apple api call failed, transactionId={}, appleMs={}, totalMs={}",
                    transactionId, appleMs, totalMs, ex);
            throw new IllegalStateException("apple api call failed: " + ex.getMessage(), ex);
        }
        long appleMs = (System.nanoTime() - t2) / 1_000_000;
        log.info("Apple IAP verifyTransaction: apple api ok, transactionId={}, txId={}, originalTxId={}, storeProductId={}, bundleId={}, appleMs={}",
                transactionId, tx.getTransactionId(), tx.getOriginalTransactionId(),
                tx.getProductId(), tx.getBundleId(), appleMs);

        // 3. 处理交易 (发放积分),内部有幂等保护
        long t3 = System.nanoTime();
        self.handleSubscribed(tx);
        long handleMs = (System.nanoTime() - t3) / 1_000_000;

        // 4. 返回处理后的订单
        long t4 = System.nanoTime();
        Map<String, Object> order = orderService.findOrderByStoreTransactionId(transactionId);
        long finalQueryMs = (System.nanoTime() - t4) / 1_000_000;
        if (order == null) {
            long totalMs = (System.nanoTime() - start) / 1_000_000;
            log.error("Apple IAP verifyTransaction: order not created after handle (likely mapping/userId issue), transactionId={}, handleMs={}, totalMs={}",
                    transactionId, handleMs, totalMs);
            throw new IllegalStateException("transaction processed but order not created, check logs for mapping issues");
        }
        long totalMs = (System.nanoTime() - start) / 1_000_000;
        log.info("Apple IAP verifyTransaction: success, transactionId={}, localQueryMs={}, appleMs={}, handleMs={}, finalQueryMs={}, totalMs={}",
                transactionId, localQueryMs, appleMs, handleMs, finalQueryMs, totalMs);
        return order;
    }

    // --- Internal handlers ---

    @Transactional
    public void handleSubscribed(JWSTransactionDecodedPayload tx) {
        long start = System.nanoTime();
        // 总耗时指标样本:在所有 return 路径用不同 result 标签 stop,得到按结果分组的调用次数和耗时分布
        Timer.Sample sample = Timer.start(meterRegistry);
        // env 标签兜底:防御 environment 字段未注入 (如单元测试),Micrometer Tag 不允许 null
        String env = environment != null ? environment : "unknown";
        String storeProductId = tx.getProductId();
        String txId = tx.getTransactionId();
        String originalTxId = tx.getOriginalTransactionId();
        log.info("Apple IAP handleSubscribed: start, txId={}, originalTxId={}, storeProductId={}, bundleId={}, env={}, price={}, currency={}",
                txId, originalTxId, storeProductId, tx.getBundleId(), env,
                tx.getPrice(), tx.getCurrency());

        // 阶段1: storeProductId → 内部 productId 反查
        long t1 = System.nanoTime();
        Long productId = mapStoreProductToInternal(storeProductId);
        long mapMs = (System.nanoTime() - t1) / 1_000_000;
        // 反查耗时指标:低基数标签 env,便于按环境分桶观察 DB 反查性能
        meterRegistry.timer("apple.iap.subscribed.map.duration", "env", env)
                .record(mapMs, TimeUnit.MILLISECONDS);
        if (productId == null) {
            long totalMs = (System.nanoTime() - start) / 1_000_000;
            log.warn("Apple IAP handleSubscribed: skip (no product mapping), txId={}, storeProductId={}, mapMs={}, totalMs={}",
                    txId, storeProductId, mapMs, totalMs);
            sample.stop(meterRegistry.timer("apple.iap.subscribed.duration",
                    "result", "skip_no_product", "env", env));
            return;
        }

        // 阶段2: 解析 orderNo + userId
        String orderNo = parseOrderNoFromAppAccountToken(tx.getAppAccountToken());
        Long userId = resolveUserId(orderNo, tx);
        if (userId == null) {
            long totalMs = (System.nanoTime() - start) / 1_000_000;
            log.warn("Apple IAP handleSubscribed: skip (cannot resolve userId), txId={}, storeProductId={}, productId={}, orderNo={}, mapMs={}, totalMs={}. "
                    + "Ensure client sets Payment.applicationUsername = orderNo (UUID format)",
                    txId, storeProductId, productId, orderNo, mapMs, totalMs);
            sample.stop(meterRegistry.timer("apple.iap.subscribed.duration",
                    "result", "skip_no_user", "env", env));
            return;
        }

        // 阶段3: 发放积分 (走 OrderService.markPaidByStore → PointService.addPoints)
        StorePaymentContext ctx = new StorePaymentContext(
                StorePaymentContext.CHANNEL_APPLE_IAP,
                orderNo,
                userId,
                productId,
                txId,
                originalTxId,
                storeProductId,
                tx.getBundleId(),
                environment,
                receiptHash(tx),
                0,
                tx.getPrice() != null ? tx.getPrice() : 0,
                tx.getCurrency()
        );
        long t2 = System.nanoTime();
        orderService.markPaidByStore(ctx);
        long markPaidMs = (System.nanoTime() - t2) / 1_000_000;
        // 发积分耗时指标:markPaidByStore 内部含 DB 写入 + PointService.addPoints,是性能热点
        meterRegistry.timer("apple.iap.subscribed.mark_paid.duration", "env", env)
                .record(markPaidMs, TimeUnit.MILLISECONDS);
        long totalMs = (System.nanoTime() - start) / 1_000_000;
        log.info("Apple IAP handleSubscribed: order paid, txId={}, originalTxId={}, productId={}, userId={}, orderNo={}, mapMs={}, markPaidMs={}, totalMs={}",
                txId, originalTxId, productId, userId, orderNo, mapMs, markPaidMs, totalMs);
        sample.stop(meterRegistry.timer("apple.iap.subscribed.duration",
                "result", "success", "env", env));
    }

    @Transactional
    public void handleRefund(JWSTransactionDecodedPayload tx, String reason) {
        long start = System.nanoTime();
        String storeTransactionId = tx.getOriginalTransactionId();
        if (storeTransactionId == null) {
            storeTransactionId = tx.getTransactionId();
        }
        log.info("Apple IAP handleRefund: start, txId={}, originalTxId={}, storeTxId={}, reason={}",
                tx.getTransactionId(), tx.getOriginalTransactionId(), storeTransactionId, reason);
        orderService.refundByStore(storeTransactionId, reason);
        long totalMs = (System.nanoTime() - start) / 1_000_000;
        log.info("Apple IAP handleRefund: done, storeTxId={}, reason={}, totalMs={}",
                storeTransactionId, reason, totalMs);
    }

    // --- Helpers ---

    /**
     * 将 Apple storeProductId 映射到内部 point_product.id。
     *
     * <p>数据驱动:按 point_product.store_product_id 反查,要求商品上架(status=1)。
     * store_product_id 由运营在 App Store Connect / Google Play Console 中配置后,
     * 写入 point_product 表 (有唯一索引 uk_point_product_store_id 防止重复绑定)。</p>
     */
    Long mapStoreProductToInternal(String storeProductId) {
        long start = System.nanoTime();
        if (storeProductId == null || storeProductId.isBlank()) {
            log.warn("Apple IAP mapStoreProduct: blank storeProductId, skip query");
            return null;
        }
        PointProduct product = pointProductService.lambdaQuery()
                .eq(PointProduct::getStoreProductId, storeProductId)
                .eq(PointProduct::getStatus, 1)
                .last("limit 1")
                .one();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        // 应用层兜底:即便 SQL where 被误改/绕过,也拒绝映射下架商品 (支付反查关键路径)
        if (product == null) {
            log.warn("Apple IAP mapStoreProduct: no product found, storeProductId={}, elapsedMs={}",
                    storeProductId, elapsedMs);
            return null;
        }
        if (!Integer.valueOf(1).equals(product.getStatus())) {
            log.warn("Apple IAP mapStoreProduct: product offline (status!=1 filtered by app layer), storeProductId={}, productId={}, status={}, elapsedMs={}",
                    storeProductId, product.getId(), product.getStatus(), elapsedMs);
            return null;
        }
        log.info("Apple IAP mapStoreProduct: mapped ok, storeProductId={}, productId={}, name={}, elapsedMs={}",
                storeProductId, product.getId(), product.getName(), elapsedMs);
        return product.getId();
    }

    /**
     * 从 Apple appAccountToken 解析 orderNo。
     * Apple 要求 appAccountToken 为 UUID 格式,所以客户端创建订单时应使用 UUID 格式的 orderNo,
     * 或额外维护 orderNo → UUID 映射 (TODO)。
     */
    String parseOrderNoFromAppAccountToken(UUID appAccountToken) {
        return appAccountToken == null ? null : appAccountToken.toString();
    }

    /**
     * 解析 userId:优先从 appAccountToken 关联的预创建 PENDING 订单中获取。
     *
     * <p>正确链路:客户端 POST /api/user/orders 创建 PENDING 订单 (IAP 渠道 orderNo 为 UUID 格式),
     * 再把 orderNo 通过 StoreKit Payment.applicationUsername 传给 Apple,Apple 在
     * JWSTransaction.appAccountToken 中以 UUID 回传;我们 parse 出 orderNo 后按
     * order_no + PENDING 条件反查拿到 userId。</p>
     *
     * <p>找不到 PENDING 时返回 null,handleSubscribed 会记录告警并跳过,
     * 但只要 Apple 不抛 5xx 就不会无限重试。</p>
     */
    Long resolveUserId(String orderNo, JWSTransactionDecodedPayload tx) {
        if (orderNo == null) {
            return null;
        }
        Long uid = orderService.findPendingOrderUserId(orderNo);
        if (uid != null) {
            return uid;
        }
        // 兜底:部分客户端可能没传 appAccountToken,此时如果本地已经用同一 storeTransactionId
        // 创建过 PAID 订单 (例如 verifyTransaction 兜底路径先跑过),也可以从已落库订单取 userId。
        Map<String, Object> existing = orderService.findOrderByStoreTransactionId(tx.getTransactionId());
        if (existing != null) {
            Object oUid = existing.get("user_id");
            if (oUid instanceof Number n) {
                log.info("Apple IAP resolveUserId: fallback to existing PAID order, txId={}, orderNo={}",
                        tx.getTransactionId(), orderNo);
                return n.longValue();
            }
        }
        return null;
    }

    String receiptHash(JWSTransactionDecodedPayload tx) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String input = tx.getTransactionId() + ":" + tx.getOriginalTransactionId() + ":" + tx.getProductId();
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return null;
        }
    }

    public boolean isEnabled() {
        return enabled && verifier != null;
    }
}
