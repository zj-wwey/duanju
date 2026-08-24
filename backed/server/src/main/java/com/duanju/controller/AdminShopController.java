package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.dto.shop.DeliveryStatusRequest;
import com.duanju.dto.shop.ShopItemCreateRequest;
import com.duanju.dto.shop.ShopItemStatusRequest;
import com.duanju.dto.shop.ShopItemUpdateRequest;
import com.duanju.entity.PointShopItem;
import com.duanju.entity.PointShopOrder;
import com.duanju.security.RequiresPermission;
import com.duanju.service.entity.PointShopItemService;
import com.duanju.service.entity.PointShopOrderService;
import com.duanju.util.MapUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiresPermission("shop:manage")
@RequestMapping("/api/admin/shop")
public class AdminShopController {

    private final PointShopItemService itemService;
    private final PointShopOrderService orderService;

    public AdminShopController(PointShopItemService itemService,
                               PointShopOrderService orderService) {
        this.itemService = itemService;
        this.orderService = orderService;
    }

    @GetMapping("/items")
    public R<List<Map<String, Object>>> listItems(@RequestParam(required = false) Integer status) {
        var query = itemService.lambdaQuery();
        if (status != null) {
            query.eq(PointShopItem::getStatus, status);
        }
        query.orderByAsc(PointShopItem::getSortOrder).orderByAsc(PointShopItem::getId);
        return R.ok(MapUtil.beansToMaps(query.list()));
    }

    @PostMapping("/items")
    public R<Map<String, Object>> createItem(@Valid @RequestBody ShopItemCreateRequest request) {
        PointShopItem item = new PointShopItem();
        item.setName(request.name());
        item.setDescription(request.description());
        item.setItemType(request.itemType() != null ? request.itemType() : "VIRTUAL");
        item.setPointsCost(request.pointsCost());
        item.setVipPointsCost(request.vipPointsCost());
        item.setImageUrl(request.imageUrl());
        item.setStock(request.stock() != null ? request.stock() : -1);
        item.setStatus(1);
        item.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        itemService.save(item);
        return R.ok(MapUtil.beanToMap(item));
    }

    @PutMapping("/items/{id}")
    public R<Map<String, Object>> updateItem(@PathVariable Long id,
                                              @Valid @RequestBody ShopItemUpdateRequest request) {
        PointShopItem item = itemService.getById(id);
        if (item == null) {
            return R.fail("item not found");
        }
        var update = itemService.lambdaUpdate().eq(PointShopItem::getId, id);
        if (request.name() != null) update.set(PointShopItem::getName, request.name());
        if (request.description() != null) update.set(PointShopItem::getDescription, request.description());
        if (request.itemType() != null) update.set(PointShopItem::getItemType, request.itemType());
        if (request.pointsCost() != null) update.set(PointShopItem::getPointsCost, request.pointsCost());
        if (request.vipPointsCost() != null) update.set(PointShopItem::getVipPointsCost, request.vipPointsCost());
        if (request.imageUrl() != null) update.set(PointShopItem::getImageUrl, request.imageUrl());
        if (request.stock() != null) update.set(PointShopItem::getStock, request.stock());
        if (request.status() != null) update.set(PointShopItem::getStatus, request.status());
        if (request.sortOrder() != null) update.set(PointShopItem::getSortOrder, request.sortOrder());
        update.update();
        return R.ok(MapUtil.beanToMap(itemService.getById(id)));
    }

    @DeleteMapping("/items/{id}")
    public R<Void> deleteItem(@PathVariable Long id) {
        itemService.lambdaUpdate().set(PointShopItem::getStatus, -1).eq(PointShopItem::getId, id).update();
        return R.ok();
    }

    @PutMapping("/items/{id}/status")
    public R<Map<String, Object>> updateItemStatus(@PathVariable Long id,
                                                    @Valid @RequestBody ShopItemStatusRequest request) {
        PointShopItem item = itemService.getById(id);
        if (item == null) {
            return R.fail("item not found");
        }
        itemService.lambdaUpdate()
                .set(PointShopItem::getStatus, request.status())
                .eq(PointShopItem::getId, id)
                .update();
        return R.ok(MapUtil.beanToMap(itemService.getById(id)));
    }

    @GetMapping("/orders")
    public R<List<Map<String, Object>>> listOrders(@RequestParam(required = false) Long userId,
                                                    @RequestParam(required = false) String deliveryStatus,
                                                    @RequestParam(defaultValue = "20") int limit) {
        if (limit < 1) limit = 20;
        if (limit > 100) limit = 100;
        var query = orderService.lambdaQuery();
        if (userId != null) {
            query.eq(PointShopOrder::getUserId, userId);
        }
        if (deliveryStatus != null && !deliveryStatus.isBlank()) {
            query.eq(PointShopOrder::getDeliveryStatus, deliveryStatus);
        }
        query.orderByDesc(PointShopOrder::getCreatedAt).last("LIMIT " + limit);
        return R.ok(MapUtil.beansToMaps(query.list()));
    }

    @PutMapping("/orders/{id}/delivery")
    public R<Map<String, Object>> updateDelivery(@PathVariable Long id,
                                                  @Valid @RequestBody DeliveryStatusRequest request) {
        PointShopOrder order = orderService.getById(id);
        if (order == null) {
            return R.fail("order not found");
        }
        orderService.lambdaUpdate()
                .set(PointShopOrder::getDeliveryStatus, request.deliveryStatus())
                .eq(PointShopOrder::getId, id)
                .update();
        return R.ok(MapUtil.beanToMap(orderService.getById(id)));
    }
}
