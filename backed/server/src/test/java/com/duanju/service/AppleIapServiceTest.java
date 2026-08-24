package com.duanju.service;

import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.duanju.entity.PointProduct;
import com.duanju.service.entity.PointProductService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AppleIapService 单元测试。
 *
 * <p>覆盖场景:</p>
 * <ol>
 *   <li>mapStoreProductToInternal: null/空白/查不到/状态非上架/正常命中</li>
 *   <li>handleSubscribed 指标上报:不同结果路径触发对应 result 标签的 Timer</li>
 * </ol>
 *
 * <p>设计说明:</p>
 * <ul>
 *   <li>mapStoreProductToInternal / handleSubscribed 是 package-private,测试类放在同包 com.duanju.service 下访问。</li>
 *   <li>使用 Mockito mock PointProductService + LambdaQueryChainWrapper,Mock 链式调用。</li>
 *   <li>用 SimpleMeterRegistry (Micrometer 内存实现) 验证指标上报正确性。</li>
 *   <li>不启动 Spring 容器,纯 JUnit5 + Mockito,执行快、隔离性好。</li>
 * </ul>
 */
class AppleIapServiceTest {

    private OrderService orderService;
    private PointProductService pointProductService;
    private SimpleMeterRegistry meterRegistry;
    private AppleIapService appleIapService;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        pointProductService = mock(PointProductService.class);
        meterRegistry = new SimpleMeterRegistry();
        appleIapService = new AppleIapService(orderService, pointProductService, meterRegistry);
    }

    @Test
    @DisplayName("输入 null 直接返回 null,不查库")
    void mapStoreProductToInternal_nullInput_returnsNullWithoutQuery() {
        assertNull(appleIapService.mapStoreProductToInternal(null));
        // 不应触发任何数据库查询
        verify(pointProductService, org.mockito.Mockito.never()).lambdaQuery();
    }

    @Test
    @DisplayName("输入空白字符串直接返回 null,不查库")
    void mapStoreProductToInternal_blankInput_returnsNullWithoutQuery() {
        assertNull(appleIapService.mapStoreProductToInternal("   "));
        verify(pointProductService, org.mockito.Mockito.never()).lambdaQuery();
    }

    @Test
    @DisplayName("商品完全不存在 (查询返回 null) → 返回 null")
    void mapStoreProductToInternal_productNotFound_returnsNull() {
        String storeProductId = "com.duanju.points100";
        stubChain(storeProductId, null);

        Long result = appleIapService.mapStoreProductToInternal(storeProductId);

        assertNull(result, "storeProductId 未绑定任何内部商品时应返回 null");
    }

    @Test
    @DisplayName("商品存在但 status=0 (下架) → 应用层兜底返回 null")
    void mapStoreProductToInternal_productOffline_returnsNull() {
        String storeProductId = "com.duanju.points500";
        PointProduct offlineProduct = new PointProduct();
        offlineProduct.setId(2L);
        offlineProduct.setStoreProductId(storeProductId);
        offlineProduct.setStatus(0); // 下架
        // 模拟 SQL where 被误改/绕过导致下架商品被查回 (应用层兜底应拦截)
        stubChain(storeProductId, offlineProduct);

        Long result = appleIapService.mapStoreProductToInternal(storeProductId);

        assertNull(result, "status != 1 的下架商品不应被映射为内部 productId");
    }

    @Test
    @DisplayName("商品存在但 status=-1 (删除) → 应用层兜底返回 null")
    void mapStoreProductToInternal_productDeleted_returnsNull() {
        String storeProductId = "com.duanju.points500";
        PointProduct deletedProduct = new PointProduct();
        deletedProduct.setId(2L);
        deletedProduct.setStoreProductId(storeProductId);
        deletedProduct.setStatus(-1); // 软删除
        stubChain(storeProductId, deletedProduct);

        Long result = appleIapService.mapStoreProductToInternal(storeProductId);

        assertNull(result, "status = -1 的软删除商品不应被映射为内部 productId");
    }

    @Test
    @DisplayName("商品上架且 storeProductId 匹配 → 返回内部 productId")
    void mapStoreProductToInternal_productOnline_returnsProductId() {
        String storeProductId = "com.duanju.points1200";
        PointProduct onlineProduct = new PointProduct();
        onlineProduct.setId(3L);
        onlineProduct.setStoreProductId(storeProductId);
        onlineProduct.setStatus(1); // 上架
        stubChain(storeProductId, onlineProduct);

        Long result = appleIapService.mapStoreProductToInternal(storeProductId);

        assertEquals(3L, result, "上架商品应返回其内部 productId");
    }

    @Test
    @DisplayName("handleSubscribed: storeProductId 无映射时上报 result=skip_no_product 指标")
    void handleSubscribed_noProductMapping_recordsSkipNoProductMetric() {
        // mock 反查返回 null (无商品映射)
        stubChain("com.duanju.unknown", null);
        JWSTransactionDecodedPayload tx = mock(JWSTransactionDecodedPayload.class);
        when(tx.getProductId()).thenReturn("com.duanju.unknown");
        when(tx.getTransactionId()).thenReturn("tx-001");

        appleIapService.handleSubscribed(tx);

        // 验证总耗时指标按 result=skip_no_product 标签上报,且计数=1
        Timer durationTimer = meterRegistry.find("apple.iap.subscribed.duration")
                .tag("result", "skip_no_product").timer();
        assertEquals(1L, durationTimer.count(), "skip_no_product 路径应上报 1 次");
        // 验证反查耗时指标也上报
        Timer mapTimer = meterRegistry.find("apple.iap.subscribed.map.duration").timer();
        assertEquals(1L, mapTimer.count(), "反查耗时指标应上报 1 次");
        // 验证 success 路径未触发
        Timer successTimer = meterRegistry.find("apple.iap.subscribed.duration")
                .tag("result", "success").timer();
        assertNull(successTimer, "不应上报 success 指标");
    }

    /**
     * Mock PointProductService.lambdaQuery() 链式调用,使 one() 返回指定 product。
     *
     * <p>链路:lambdaQuery() → eq(storeProductId) → eq(status,1) → last("limit 1") → one()。
     * 每个中间方法都返回同一个 chain mock,避免 NPE。</p>
     */
    @SuppressWarnings("unchecked")
    private void stubChain(String storeProductId, PointProduct returnedProduct) {
        LambdaQueryChainWrapper<PointProduct> chain = mock(LambdaQueryChainWrapper.class);
        when(pointProductService.lambdaQuery()).thenReturn(chain);
        when(chain.eq(any(), any())).thenReturn(chain);
        when(chain.last(anyString())).thenReturn(chain);
        when(chain.one()).thenReturn(returnedProduct);
    }
}
