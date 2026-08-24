package com.duanju.service;

import com.duanju.entity.PointShopItem;
import com.duanju.entity.PointShopOrder;
import com.duanju.service.entity.PointShopItemService;
import com.duanju.service.entity.PointShopOrderService;
import com.duanju.util.MapUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class PointShopService {

    private final PointShopItemService itemService;
    private final PointShopOrderService orderService;
    private final PointService pointService;
    private final MembershipService membershipService;

    public PointShopService(PointShopItemService itemService,
                            PointShopOrderService orderService,
                            PointService pointService,
                            MembershipService membershipService) {
        this.itemService = itemService;
        this.orderService = orderService;
        this.pointService = pointService;
        this.membershipService = membershipService;
    }

    /** 商品列表 */
    public List<Map<String, Object>> listItems() {
        List<PointShopItem> items = itemService.lambdaQuery()
                .eq(PointShopItem::getStatus, 1)
                .orderByAsc(PointShopItem::getSortOrder)
                .list();
        return MapUtil.beansToMaps(items);
    }

    /** 兑换商品 (R18: 钻石会员享专属优惠价) */
    @Transactional
    public Map<String, Object> exchange(Long userId, Long itemId) {
        PointShopItem item = itemService.getById(itemId);
        if (item == null || item.getStatus() == null || item.getStatus() != 1) {
            throw new IllegalArgumentException("item not found or off shelf");
        }

        // 库存检查
        if (item.getStock() != null && item.getStock() >= 0) {
            if (item.getStock() <= 0) {
                throw new IllegalArgumentException("item out of stock");
            }
        }

        // 计算价格：钻石会员享专属优惠价
        String level = membershipService.getCurrentLevel(userId);
        int cost = item.getPointsCost();
        if (MembershipService.LEVEL_DIAMOND.equals(level)
                && item.getVipPointsCost() != null && item.getVipPointsCost() > 0) {
            cost = item.getVipPointsCost();
        }

        // 扣减积分
        pointService.addPoints(userId, -cost, "SHOP_EXCHANGE", String.valueOf(itemId),
                "exchange: " + item.getName());

        // 扣减库存
        if (item.getStock() != null && item.getStock() >= 0) {
            boolean stockUpdated = itemService.lambdaUpdate()
                    .setSql("stock = stock - 1")
                    .eq(PointShopItem::getId, itemId)
                    .gt(PointShopItem::getStock, 0)
                    .update();
            if (!stockUpdated) {
                throw new IllegalStateException("item out of stock");
            }
        }

        // 创建兑换记录
        PointShopOrder order = new PointShopOrder();
        order.setUserId(userId);
        order.setItemId(itemId);
        order.setItemName(item.getName());
        order.setPointsCost(cost);
        order.setDeliveryStatus("PENDING");
        orderService.save(order);

        return MapUtil.map(
                "order_id", order.getId(),
                "item_name", item.getName(),
                "points_cost", cost,
                "vip_discount", cost < item.getPointsCost(),
                "delivery_status", "PENDING"
        );
    }

    /** 兑换记录 */
    public List<Map<String, Object>> getRecords(Long userId) {
        List<PointShopOrder> orders = orderService.lambdaQuery()
                .eq(PointShopOrder::getUserId, userId)
                .orderByDesc(PointShopOrder::getCreatedAt)
                .list();
        return MapUtil.beansToMaps(orders);
    }
}