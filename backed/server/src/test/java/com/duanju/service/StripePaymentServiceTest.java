package com.duanju.service;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.duanju.entity.UserOrder;
import com.duanju.service.entity.PointProductService;
import com.duanju.service.entity.UserOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * StripePaymentService 单元测试。
 */
class StripePaymentServiceTest {

    private OrderService orderService;
    private UserOrderService userOrderService;
    private PointProductService pointProductService;
    private PaymentEventLogService paymentEventLogService;
    private StripePaymentService stripeService;

    @BeforeEach
    void setUp() throws Exception {
        orderService = mock(OrderService.class);
        userOrderService = mock(UserOrderService.class);
        pointProductService = mock(PointProductService.class);
        paymentEventLogService = mock(PaymentEventLogService.class);
        stripeService = new StripePaymentService(orderService, pointProductService, userOrderService, paymentEventLogService);
        setField(stripeService, "secretKey", "sk_test_fake");
        setField(stripeService, "webhookSecret", "whsec_fake");
        setField(stripeService, "successUrl", "http://localhost/success");
        setField(stripeService, "cancelUrl", "http://localhost/cancel");
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryChainWrapper<UserOrder> stubOrderChain(UserOrder returnedOrder) {
        LambdaQueryChainWrapper<UserOrder> chain = mock(LambdaQueryChainWrapper.class);
        when(userOrderService.lambdaQuery()).thenReturn(chain);
        when(chain.eq(any(), any())).thenReturn(chain);
        when(chain.last(anyString())).thenReturn(chain);
        when(chain.one()).thenReturn(returnedOrder);
        return chain;
    }

    // --- handleWebhook ---

    @Test
    @DisplayName("handleWebhook: Stripe未启用静默忽略不抛异常")
    void handleWebhook_notEnabled_noException() throws Exception {
        setField(stripeService, "enabled", false);
        assertDoesNotThrow(() ->
                stripeService.handleWebhook("{}", "t=12345,v1=abc"));
    }

    @Test
    @DisplayName("handleWebhook: webhookSecret未配置抛异常")
    void handleWebhook_noWebhookSecret_throwsException() throws Exception {
        setField(stripeService, "enabled", true);
        setField(stripeService, "webhookSecret", null);

        assertThrows(IllegalStateException.class, () ->
                stripeService.handleWebhook("{}", "t=12345,v1=abc"));
    }

    @Test
    @DisplayName("handleWebhook: 签名校验失败抛异常")
    void handleWebhook_invalidSignature_throwsException() throws Exception {
        setField(stripeService, "enabled", true);
        setField(stripeService, "webhookSecret", "whsec_real");

        assertThrows(IllegalArgumentException.class, () ->
                stripeService.handleWebhook("{}", "invalid-signature-header"));
    }

    // --- createCheckoutSession ---

    @Test
    @DisplayName("createCheckoutSession: 未启用抛异常")
    void createCheckoutSession_notEnabled_throwsException() throws Exception {
        setField(stripeService, "enabled", false);

        assertThrows(IllegalStateException.class, () ->
                stripeService.createCheckoutSession("order-1"));
    }

    @Test
    @DisplayName("createCheckoutSession: orderNo为null抛异常")
    void createCheckoutSession_nullOrderNo_throwsException() throws Exception {
        setField(stripeService, "enabled", true);

        assertThrows(IllegalArgumentException.class, () ->
                stripeService.createCheckoutSession(null));
    }

    @Test
    @DisplayName("createCheckoutSession: PENDING订单不存在抛异常")
    void createCheckoutSession_pendingOrderNotFound_throwsException() throws Exception {
        setField(stripeService, "enabled", true);
        stubOrderChain(null);

        assertThrows(IllegalArgumentException.class, () ->
                stripeService.createCheckoutSession("order-not-exist"));
    }

    @Test
    @DisplayName("createCheckoutSession: 订单商品不存在抛异常")
    void createCheckoutSession_productNotFound_throwsException() throws Exception {
        setField(stripeService, "enabled", true);
        UserOrder order = new UserOrder();
        order.setOrderNo("order-no-product");
        order.setStatus("PENDING");
        order.setProductId(999L);
        stubOrderChain(order);
        when(pointProductService.getById(999L)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () ->
                stripeService.createCheckoutSession("order-no-product"));
    }

    // --- refundOrder ---

    @Test
    @DisplayName("refundOrder: 订单不存在抛异常")
    void refundOrder_orderNotFound_throwsException() throws Exception {
        setField(stripeService, "enabled", true);
        when(userOrderService.orderByNo("order-not-exist")).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () ->
                stripeService.refundOrder("order-not-exist"));
    }

    @Test
    @DisplayName("refundOrder: 非PAID状态抛异常")
    void refundOrder_notPaidStatus_throwsException() throws Exception {
        setField(stripeService, "enabled", true);
        Map<String, Object> order = new HashMap<>();
        order.put("order_no", "order-cancelled");
        order.put("status", "CANCELLED");
        when(userOrderService.orderByNo("order-cancelled")).thenReturn(order);

        assertThrows(IllegalArgumentException.class, () ->
                stripeService.refundOrder("order-cancelled"));
    }

    @Test
    @DisplayName("refundOrder: 无stripe_payment_intent_id抛异常")
    void refundOrder_noPiId_throwsException() throws Exception {
        setField(stripeService, "enabled", true);
        Map<String, Object> order = new HashMap<>();
        order.put("order_no", "order-no-pi");
        order.put("status", "PAID");
        order.put("stripe_payment_intent_id", null);
        when(userOrderService.orderByNo("order-no-pi")).thenReturn(order);

        assertThrows(IllegalArgumentException.class, () ->
                stripeService.refundOrder("order-no-pi"));
    }
}