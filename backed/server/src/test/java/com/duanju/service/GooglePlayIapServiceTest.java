package com.duanju.service;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.duanju.dto.payment.StorePaymentContext;
import com.duanju.entity.PointProduct;
import com.duanju.service.entity.PointProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GooglePlayIapService 单元测试。
 */
class GooglePlayIapServiceTest {

    private OrderService orderService;
    private PointProductService pointProductService;
    private GooglePlayIapService googlePlayIapService;
    private PublicKey testPublicKey;

    @BeforeEach
    void setUp() throws Exception {
        orderService = mock(OrderService.class);
        pointProductService = mock(PointProductService.class);
        googlePlayIapService = new GooglePlayIapService(orderService, pointProductService);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);
        KeyPair kp = kpg.generateKeyPair();
        testPublicKey = kp.getPublic();

        setField(googlePlayIapService, "enabled", true);
        setField(googlePlayIapService, "packageName", "com.example.app");
        setField(googlePlayIapService, "rsaPublicKey", testPublicKey);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    // --- mapStoreProductToInternal ---

    @Test
    @DisplayName("mapStoreProductToInternal: null输入直接返回null,不查库")
    void mapStoreProductToInternal_nullInput_returnsNullWithoutQuery() {
        assertNull(googlePlayIapService.mapStoreProductToInternal(null));
        verify(pointProductService, never()).lambdaQuery();
    }

    @Test
    @DisplayName("mapStoreProductToInternal: 空白字符串直接返回null,不查库")
    void mapStoreProductToInternal_blankInput_returnsNullWithoutQuery() {
        assertNull(googlePlayIapService.mapStoreProductToInternal("   "));
        verify(pointProductService, never()).lambdaQuery();
    }

    @Test
    @DisplayName("mapStoreProductToInternal: 商品不存在返回null")
    void mapStoreProductToInternal_productNotFound_returnsNull() {
        String sku = "com.example.sku100";
        stubChain(sku, null);
        assertNull(googlePlayIapService.mapStoreProductToInternal(sku));
    }

    @Test
    @DisplayName("mapStoreProductToInternal: 商品下架(status=0)返回null")
    void mapStoreProductToInternal_productOffline_returnsNull() {
        String sku = "com.example.sku500";
        PointProduct offline = new PointProduct();
        offline.setId(2L);
        offline.setStoreProductId(sku);
        offline.setStatus(0);
        stubChain(sku, offline);
        assertNull(googlePlayIapService.mapStoreProductToInternal(sku));
    }

    @Test
    @DisplayName("mapStoreProductToInternal: 商品上架返回productId")
    void mapStoreProductToInternal_productOnline_returnsProductId() {
        String sku = "com.example.sku1200";
        PointProduct online = new PointProduct();
        online.setId(3L);
        online.setStoreProductId(sku);
        online.setStatus(1);
        stubChain(sku, online);
        assertEquals(3L, googlePlayIapService.mapStoreProductToInternal(sku));
    }

    // --- receiptHash ---

    @Test
    @DisplayName("receiptHash: 正常输入返回SHA-256十六进制摘要")
    void receiptHash_normalInput_returnsHexHash() {
        String hash = googlePlayIapService.receiptHash("test-data");
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }

    // --- verifyPurchase ---

    @Test
    @DisplayName("verifyPurchase: 公钥为null时isEnabled返回false")
    void verifyPurchase_noPublicKey_isEnabledFalse() throws Exception {
        setField(googlePlayIapService, "rsaPublicKey", null);
        setField(googlePlayIapService, "enabled", true);
        assertEquals(false, googlePlayIapService.isEnabled());
    }

    @Test
    @DisplayName("verifyPurchase: 幂等命中直接返回已有订单")
    void verifyPurchase_idempotentHit_returnsExistingOrder() {
        String purchaseToken = "token-existing";
        Map<String, Object> existingOrder = new HashMap<>();
        existingOrder.put("order_no", "existing-order");
        when(orderService.findOrderByStoreTransactionId(purchaseToken)).thenReturn(existingOrder);

        Map<String, Object> result = googlePlayIapService.verifyPurchase(
                "{\"packageName\":\"com.example.app\",\"productId\":\"sku100\",\"purchaseToken\":\"token-existing\",\"purchaseState\":0}",
                "sig", purchaseToken, "order-123");

        assertEquals("existing-order", result.get("order_no"));
        verify(orderService, never()).markPaidByStore(any(StorePaymentContext.class));
    }

    @Test
    @DisplayName("verifyPurchase: purchaseData为null抛异常")
    void verifyPurchase_nullPurchaseData_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                googlePlayIapService.verifyPurchase(null, "sig", "token", null));
    }

    @Test
    @DisplayName("verifyPurchase: purchaseSignature为null抛异常")
    void verifyPurchase_nullSignature_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                googlePlayIapService.verifyPurchase("data", null, "token", null));
    }

    @Test
    @DisplayName("verifyPurchase: purchaseToken为null抛异常")
    void verifyPurchase_nullToken_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                googlePlayIapService.verifyPurchase("data", "sig", null, null));
    }

    // --- handleRtdnEvent ---

    @Test
    @DisplayName("handleRtdnEvent: 缺少message抛异常")
    void handleRtdnEvent_missingMessage_throwsException() {
        Map<String, Object> payload = new HashMap<>();
        assertThrows(IllegalArgumentException.class, () ->
                googlePlayIapService.handleRtdnEvent(payload));
    }

    @Test
    @DisplayName("handleRtdnEvent: 缺少data安全返回不抛异常")
    void handleRtdnEvent_missingData_logsAndReturns() {
        Map<String, Object> payload = buildRtdnPayload("ONE_TIME_PRODUCT_PURCHASED", "evt-1", null);
        googlePlayIapService.handleRtdnEvent(payload);
    }

    @Test
    @DisplayName("handleRtdnEvent: 缺少eventType安全返回")
    void handleRtdnEvent_missingEventType_logsAndReturns() {
        Map<String, Object> payload = buildRtdnPayload(null, "evt-1", "base64data");
        googlePlayIapService.handleRtdnEvent(payload);
    }

    @Test
    @DisplayName("handleRtdnEvent: 购买事件无映射商品时安全跳过")
    void handleRtdnEvent_purchaseNoMapping_skipsGracefully() {
        String purchaseToken = "token-no-mapping";
        String sku = "com.example.unknown";
        Map<String, Object> payload = buildRtdnPayloadWithData("ONE_TIME_PRODUCT_PURCHASED", "evt-2",
                purchaseToken, sku, "order-123", null, 1990000, "USD");

        stubChain(sku, null);
        when(orderService.findOrderByStoreTransactionId(purchaseToken)).thenReturn(null);

        googlePlayIapService.handleRtdnEvent(payload);

        verify(orderService, never()).markPaidByStore(any(StorePaymentContext.class));
    }

    @Test
    @DisplayName("handleRtdnEvent: 取消事件无订单时安全跳过")
    void handleRtdnEvent_cancelNoOrder_skipsGracefully() {
        String purchaseToken = "token-cancel-no-order";
        Map<String, Object> payload = buildRtdnPayloadWithData("ONE_TIME_PRODUCT_CANCELED", "evt-3",
                purchaseToken, "sku100", null, null, null, null);

        when(orderService.findOrderByStoreTransactionId(purchaseToken)).thenReturn(null);

        googlePlayIapService.handleRtdnEvent(payload);

        verify(orderService, never()).refundByStore(anyString(), anyString());
    }

    @Test
    @DisplayName("handleRtdnEvent: 购买事件成功标记已支付")
    void handleRtdnEvent_purchaseSuccess_marksPaid() {
        String purchaseToken = "token-success";
        String sku = "com.example.sku100";
        String orderNo = "order-abc";
        Long userId = 100L;

        Map<String, Object> payload = buildRtdnPayloadWithData("ONE_TIME_PRODUCT_PURCHASED", "evt-4",
                purchaseToken, sku, orderNo, userId, 1990000, "USD");

        PointProduct product = new PointProduct();
        product.setId(1L);
        product.setStoreProductId(sku);
        product.setStatus(1);
        stubChain(sku, product);

        when(orderService.findOrderByStoreTransactionId(purchaseToken)).thenReturn(null);
        when(orderService.findPendingOrderUserId(orderNo)).thenReturn(userId);

        googlePlayIapService.handleRtdnEvent(payload);

        verify(orderService).markPaidByStore(any(StorePaymentContext.class));
    }

    @Test
    @DisplayName("handleRtdnEvent: 取消事件成功退款")
    void handleRtdnEvent_cancelSuccess_refundsOrder() {
        String purchaseToken = "token-cancel-success";
        Map<String, Object> existingOrder = new HashMap<>();
        existingOrder.put("order_no", "order-refund");
        existingOrder.put("status", "PAID");

        Map<String, Object> payload = buildRtdnPayloadWithData("ONE_TIME_PRODUCT_CANCELED", "evt-5",
                purchaseToken, "sku100", null, null, null, null);

        when(orderService.findOrderByStoreTransactionId(purchaseToken)).thenReturn(existingOrder);

        googlePlayIapService.handleRtdnEvent(payload);

        verify(orderService).refundByStore(purchaseToken, "GOOGLE_PLAY_ONE_TIME_PRODUCT_CANCELED");
    }

    @Test
    @DisplayName("handleRtdnEvent: 幂等跳过已处理购买事件")
    void handleRtdnEvent_purchaseIdempotent_skipsAlreadyPaid() {
        String purchaseToken = "token-idempotent";
        Map<String, Object> existingOrder = new HashMap<>();
        existingOrder.put("order_no", "order-existing");
        existingOrder.put("status", "PAID");

        Map<String, Object> payload = buildRtdnPayloadWithData("ONE_TIME_PRODUCT_PURCHASED", "evt-6",
                purchaseToken, "sku100", "order-1", null, null, null);

        when(orderService.findOrderByStoreTransactionId(purchaseToken)).thenReturn(existingOrder);

        googlePlayIapService.handleRtdnEvent(payload);

        verify(orderService, never()).markPaidByStore(any(StorePaymentContext.class));
    }

    @Test
    @DisplayName("handleRtdnEvent: 未知事件类型安全跳过")
    void handleRtdnEvent_unknownEventType_skipsGracefully() {
        Map<String, Object> orderData = new HashMap<>();
        orderData.put("purchaseToken", "token-unknown");
        orderData.put("productId", "sku100");
        String dataB64 = Base64.getEncoder().encodeToString(toJson(orderData));
        Map<String, Object> payload = buildRtdnPayload("SOME_UNKNOWN_EVENT", "evt-7", dataB64);
        googlePlayIapService.handleRtdnEvent(payload);
    }

    // --- helpers ---

    @SuppressWarnings("unchecked")
    private void stubChain(String storeProductId, PointProduct returnedProduct) {
        LambdaQueryChainWrapper<PointProduct> chain = mock(LambdaQueryChainWrapper.class);
        when(pointProductService.lambdaQuery()).thenReturn(chain);
        when(chain.eq(any(), any())).thenReturn(chain);
        when(chain.last(anyString())).thenReturn(chain);
        when(chain.one()).thenReturn(returnedProduct);
    }

    private Map<String, Object> buildRtdnPayload(String eventType, String eventId, String dataB64) {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        Map<String, String> attributes = new HashMap<>();
        attributes.put("packageName", "com.example.app");
        if (eventType != null) {
            attributes.put("eventType", eventType);
        }
        if (eventId != null) {
            attributes.put("eventId", eventId);
        }
        message.put("attributes", attributes);
        if (dataB64 != null) {
            message.put("data", dataB64);
        }
        message.put("messageId", "msg-" + eventId);
        message.put("publishTime", "2024-01-01T00:00:00Z");
        payload.put("message", message);
        payload.put("subscription", "projects/test/subscriptions/test");
        return payload;
    }

    private Map<String, Object> buildRtdnPayloadWithData(String eventType, String eventId,
                                                         String purchaseToken, String sku,
                                                         String developerPayload, Long userId,
                                                         Integer priceMicros, String currency) {
        Map<String, Object> purchaseOrder = new HashMap<>();
        purchaseOrder.put("purchaseToken", purchaseToken);
        purchaseOrder.put("productId", sku);
        purchaseOrder.put("orderId", "google-order-" + eventId);
        purchaseOrder.put("purchaseTimeMillis", "1700000000000");
        purchaseOrder.put("purchaseState", 0);
        if (developerPayload != null) {
            purchaseOrder.put("developerPayload", developerPayload);
        }
        if (currency != null) {
            purchaseOrder.put("currencyCode", currency);
        }
        if (priceMicros != null) {
            purchaseOrder.put("priceAmountMicros", priceMicros);
        }
        String dataB64 = Base64.getEncoder().encodeToString(toJson(purchaseOrder));
        return buildRtdnPayload(eventType, eventId, dataB64);
    }

    private byte[] toJson(Map<String, Object> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(map);
        } catch (Exception e) {
            return map.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}