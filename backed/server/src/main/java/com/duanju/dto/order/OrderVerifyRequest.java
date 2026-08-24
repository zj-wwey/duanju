package com.duanju.dto.order;

import jakarta.validation.constraints.NotBlank;

/**
 * 客户端 IAP 交易主动校验请求 (Apple / Google Play 通用)。
 *
 * <p>Apple 场景:store=APPLE, transactionId 必填 (StoreKit 2 Transaction.id)。</p>
 * <p>Google Play 场景 (本地 RSA 验签):store=GOOGLE, purchaseData(INAPP_PURCHASE_DATA JSON)
 * 和 purchaseSignature 必填写;orderNo 为客户端预创建的订单号 (可选,便于落到对应 PENDING 单)。</p>
 *
 * @param store            应用商店枚举:APPLE / GOOGLE (必填)
 * @param transactionId    Apple StoreKit 2 返回的 Transaction.id (Apple 场景必填)
 * @param purchaseToken    Google Play Billing purchase.getPurchaseToken() (用于幂等/审计;
 *                         本地验签场景可与 purchaseData 同时传,purchaseToken 作为 store_transaction_id 落库)
 * @param purchaseData     Google Play INAPP_PURCHASE_DATA JSON 字符串 (即 Purchase.getOriginalJson(),
 *                         本地 RSA 验签的"原文"部分,Google 场景必填)
 * @param purchaseSignature Google Play 对 purchaseData 的 RSA/SHA1 签名 (即 Purchase.getSignature(),
 *                         本地 RSA 验签的"签名"部分,Google 场景必填)
 * @param orderNo          客户端预创建订单号 (可选;传了则落到对应 PENDING 单,便于关联)
 */
public record OrderVerifyRequest(
        @NotBlank Store store,
        String transactionId,
        String purchaseToken,
        String purchaseData,
        String purchaseSignature,
        String orderNo
) {
    /** 支持的应用商店枚举。避免客户端传入任意字符串。 */
    public enum Store { APPLE, GOOGLE }
}
