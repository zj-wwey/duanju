package com.duanju.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_order")
public class UserOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_no")
    private String orderNo;

    @TableField("user_id")
    private Long userId;

    @TableField("product_id")
    private Long productId;

    @TableField("order_type")
    private String orderType;

    @TableField("pay_channel")
    private String payChannel;

    private Integer points;

    @TableField("amount_cents")
    private Integer amountCents;

    private String currency;

    private String status;

    @TableField("paid_at")
    private LocalDateTime paidAt;

    /** 应用商店交易ID (Apple originalTransactionId / Google purchaseToken),用于幂等与跨周期追溯 */
    @TableField("store_transaction_id")
    private String storeTransactionId;

    /** Apple 订阅原始交易ID (订阅生命周期内不变) */
    @TableField("store_original_transaction_id")
    private String storeOriginalTransactionId;

    /** 应用商店侧商品ID (Apple productId / Google sku) */
    @TableField("store_product_id")
    private String storeProductId;

    /** Apple bundleId / Google packageName */
    @TableField("store_bundle_id")
    private String storeBundleId;

    /** SANDBOX / PRODUCTION */
    @TableField("store_environment")
    private String storeEnvironment;

    /** 已验证的 receipt/transaction payload 的 SHA-256 摘要,用于审计去重 */
    @TableField("store_receipt_hash")
    private String storeReceiptHash;

    /** 退款原因 (Apple refundReason enum,如 REFUND_ISSUE / REVOKE) */
    @TableField("refund_reason")
    private String refundReason;

    /** 税额 (单位:分),Apple/Google IAP 已代扣税费时也用于对账 */
    @TableField("tax_amount_cents")
    private Integer taxAmountCents;

    /** Stripe Checkout Session ID (cs_test_xxx),创建 Stripe Session 时写入,用于从 webhook event 反查订单 */
    @TableField("stripe_session_id")
    private String stripeSessionId;

    /** Stripe PaymentIntent ID (pi_xxx),支付成功后由 webhook 写入,用于幂等与对账 */
    @TableField("stripe_payment_intent_id")
    private String stripePaymentIntentId;

    /** Google Play Billing purchaseToken,用于 verifyPurchase / RTDN / 退款查询唯一索引 */
    @TableField("google_purchase_token")
    private String googlePurchaseToken;

    /** PayPal Order ID,创建 PayPal 订单时写入,用于 webhook 反查订单 */
    @TableField("paypal_order_id")
    private String paypalOrderId;

    /** PayPal Payment ID,支付确认后产生,用于幂等去重 */
    @TableField("paypal_payment_id")
    private String paypalPaymentId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
