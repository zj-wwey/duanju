package com.duanju.dto.payment;

/**
 * 通用应用商店/Web 支付上下文。
 *
 * <p>由各渠道 Service (AppleIapService / GooglePlayIapService / StripePaymentService)
 * 在验签 + 解码交易信息之后构造,交给 OrderService.markPaidByStore 完成订单创建/更新与积分发放。</p>
 *
 * <p>渠道枚举:</p>
 * <ul>
 *   <li>APPLE_IAP  - Apple App Store In-App Purchase (StoreKit 2)</li>
 *   <li>GOOGLE_PLAY - Google Play In-App Billing</li>
 *   <li>STRIPE     - Stripe Checkout Web 支付 (Credit Card / Apple Pay / Google Pay)</li>
 *   <li>PAYPAL     - PayPal Web 支付</li>
 * </ul>
 *
 * @param channel                    支付渠道枚举值 (APPLE_IAP / GOOGLE_PLAY / STRIPE / PAYPAL)
 * @param orderNo                    客户端预创建订单号;Webhook/通知先到时可为空,此时走"先建 PAID 订单"分支
 * @param userId                     用户ID,orderNo 不为空时从预创建订单解析;否则需通过上下文反查
 * @param productId                  内部 point_product.id,通过 storeProductId 映射得到
 * @param storeTransactionId         渠道侧唯一交易ID,用于幂等去重 (Apple transactionId / Google purchaseToken / Stripe charge_id / PayPal capture_id)
 * @param storeOriginalTransactionId 渠道侧原始交易ID (订阅生命周期不变);非订阅场景与 storeTransactionId 相同
 * @param storeProductId             渠道侧商品ID (Apple productId / Google sku / Stripe price_id)
 * @param storeBundleId              渠道侧应用标识 (Apple bundleId / Google packageName / Stripe account_id)
 * @param storeEnvironment           渠道侧环境标识 (SANDBOX/PRODUCTION/TEST/LIVE)
 * @param storeReceiptHash           已验证 payload 的 SHA-256 摘要,用于审计去重;Stripe 存 Event.id
 * @param taxAmountCents             税额,单位分 (渠道已代扣时记录)
 * @param priceCents                 渠道实际售价,单位分
 * @param currency                   ISO 4217 货币码 (USD/EUR/CNY...)
 */
public record StorePaymentContext(
        String channel,
        String orderNo,
        Long userId,
        Long productId,
        String storeTransactionId,
        String storeOriginalTransactionId,
        String storeProductId,
        String storeBundleId,
        String storeEnvironment,
        String storeReceiptHash,
        Integer taxAmountCents,
        Integer priceCents,
        String currency
) {
    /** 渠道枚举常量,避免字符串魔法值散落在各处 */
    public static final String CHANNEL_APPLE_IAP = "APPLE_IAP";
    public static final String CHANNEL_GOOGLE_PLAY = "GOOGLE_PLAY";
    public static final String CHANNEL_STRIPE = "STRIPE";
    public static final String CHANNEL_PAYPAL = "PAYPAL";
}
