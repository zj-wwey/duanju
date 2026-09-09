package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.dto.order.AdRewardRequest;
import com.duanju.dto.order.CreateOrderRequest;
import com.duanju.dto.order.OrderVerifyRequest;
import com.duanju.dto.order.StripeCheckoutRequest;
import com.duanju.dto.order.PaypalCheckoutRequest;
import com.duanju.dto.order.PaypalCaptureRequest;
import com.duanju.security.PrincipalHolder;
import com.duanju.service.AppleIapService;
import com.duanju.service.GooglePlayIapService;
import com.duanju.service.OrderService;
import com.duanju.service.PayPalPaymentService;
import com.duanju.service.entity.PointProductService;
import com.duanju.service.PointService;
import com.duanju.service.StripePaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/user")
public class UserOrderController {

    private final OrderService orderService;
    private final PointService pointService;
    private final PointProductService pointProductService;
    private final AppleIapService appleIapService;
    private final GooglePlayIapService googlePlayIapService;
    private final StripePaymentService stripePaymentService;
    private final PayPalPaymentService payPalPaymentService;

    public UserOrderController(OrderService orderService, PointService pointService,
                               PointProductService pointProductService,
                               AppleIapService appleIapService,
                               GooglePlayIapService googlePlayIapService,
                               StripePaymentService stripePaymentService,
                               PayPalPaymentService payPalPaymentService) {
        this.orderService = orderService;
        this.pointService = pointService;
        this.pointProductService = pointProductService;
        this.appleIapService = appleIapService;
        this.googlePlayIapService = googlePlayIapService;
        this.stripePaymentService = stripePaymentService;
        this.payPalPaymentService = payPalPaymentService;
    }

    @GetMapping("/orders")
    public R<List<Map<String, Object>>> orders(@RequestParam(defaultValue = "100") @Min(1) @Max(100) int limit) {
        return R.ok(orderService.getUserOrders(limit));
    }

    /**
     * 用户软删除自己的订单。仅 CANCELLED / CLOSED / REFUNDED 终态允许删除。
     * PENDING / PAID 订单禁止删除 (前者引导完成支付或等待自动取消,后者涉及真实支付记录不可删)。
     */
    @DeleteMapping("/orders/{orderNo}")
    public R<Map<String, Object>> deleteOrder(@PathVariable String orderNo) {
        orderService.deleteOrder(orderNo);
        return R.ok(Map.of("orderNo", orderNo));
    }

    @PostMapping("/orders")
    public R<Map<String, Object>> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return R.ok(orderService.createOrder(request.productId(), request.payChannel()));
    }

    /**
     * 为已创建的 PENDING(STRIPE 渠道) 订单创建 Stripe Checkout Session,
     * 返回 session_url 让前端跳转完成支付。
     *
     * @param request 只包含 orderNo 字段;必须是已创建的 PENDING 订单,且 payChannel=STRIPE
     * @return { sessionId, sessionUrl, orderNo, amountCents, currency }
     */
    @PostMapping("/orders/stripe-checkout")
    public R<Map<String, Object>> stripeCheckout(@Valid @RequestBody StripeCheckoutRequest request) {
        return R.ok(stripePaymentService.createCheckoutSession(request.orderNo()));
    }

    /**
     * 为已创建的 PENDING(PAYPAL 渠道) 订单创建 PayPal 订单,
     * 返回 approve_url 让前端跳转完成支付。
     *
     * @param request 只包含 orderNo 字段;必须是已创建的 PENDING 订单,且 payChannel=PAYPAL
     * @return { paypalOrderId, approveUrl, orderNo, amountCents, currency }
     */
    @PostMapping("/orders/paypal-checkout")
    public R<Map<String, Object>> paypalCheckout(@Valid @RequestBody PaypalCheckoutRequest request) {
        return R.ok(payPalPaymentService.createPayPalOrder(request.orderNo()));
    }

    /**
     * 用户在 PayPal 页面完成支付后,前端调用此接口捕获支付 (兜底 webhook 未到达的场景)。
     *
     * @param request 包含 orderNo 和 paypalOrderId
     * @return 已支付订单信息
     */
    @PostMapping("/orders/paypal-capture")
    public R<Map<String, Object>> paypalCapture(@Valid @RequestBody PaypalCaptureRequest request) {
        return R.ok(payPalPaymentService.capturePayment(request.orderNo(), request.paypalOrderId()));
    }

    /**
     * 客户端 IAP 完成后主动校验交易 (兜底 webhook 未到达的场景)。
     *
     * <p>按 request.store 分发到对应渠道:</p>
     * <ul>
     *   <li>APPLE  → 调 AppleIapService.verifyTransaction (transactionId 必须传),
     *              服务端再查 Apple App Store Server API 验真。</li>
     *   <li>GOOGLE → 调 GooglePlayIapService.verifyPurchase (purchaseData + purchaseSignature +
     *              purchaseToken 必须传),服务端本地用 Google RSA 公钥验签。</li>
     * </ul>
     *
     * <p>两种场景都通过 OrderService.markPaidByStore 统一入口发放积分 (内部幂等保护)。</p>
     */
    @PostMapping("/orders/verify")
    public R<Map<String, Object>> verifyOrder(@Valid @RequestBody OrderVerifyRequest request) {
        return switch (request.store()) {
            case APPLE -> {
                if (request.transactionId() == null || request.transactionId().isBlank()) {
                    throw new IllegalArgumentException("transactionId required for APPLE store verify");
                }
                yield R.ok(appleIapService.verifyTransaction(request.transactionId()));
            }
            case GOOGLE -> {
                if (request.purchaseData() == null || request.purchaseData().isBlank()) {
                    throw new IllegalArgumentException("purchaseData required for GOOGLE store verify");
                }
                if (request.purchaseSignature() == null || request.purchaseSignature().isBlank()) {
                    throw new IllegalArgumentException("purchaseSignature required for GOOGLE store verify");
                }
                yield R.ok(googlePlayIapService.verifyPurchase(
                        request.purchaseData(),
                        request.purchaseSignature(),
                        request.purchaseToken(),
                        request.orderNo()
                ));
            }
        };
    }

    @PostMapping("/ad-reward")
    public R<Map<String, Object>> adReward(@Valid @RequestBody AdRewardRequest request) {
        return R.ok(pointService.adReward(PrincipalHolder.userId(), request.adSlot(), request.traceId()));
    }

    @GetMapping("/ad-rewards")
    public R<List<Map<String, Object>>> adRewards(@RequestParam(defaultValue = "100") int limit) {
        return R.ok(pointService.getAdRewards(PrincipalHolder.userId(), limit));
    }

    @GetMapping("/products-by-locale")
    public R<List<Map<String, Object>>> getProductsByLocale(
            @RequestHeader(value = "X-Locale", required = false) String locale) {
        return R.ok(pointProductService.listWithLocale(locale));
    }
}
