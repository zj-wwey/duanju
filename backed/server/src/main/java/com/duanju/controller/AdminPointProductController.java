package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.dto.product.CreateProductRequest;
import com.duanju.dto.product.UpdateProductRequest;
import com.duanju.entity.PointProduct;
import com.duanju.security.RequiresPermission;
import com.duanju.service.OrderService;
import com.duanju.service.entity.PointProductService;
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
@RequiresPermission("order:manage")
@RequestMapping("/api/admin/point-products")
public class AdminPointProductController {

    private final PointProductService pointProductService;
    private final OrderService orderService;

    public AdminPointProductController(PointProductService pointProductService,
                                       OrderService orderService) {
        this.pointProductService = pointProductService;
        this.orderService = orderService;
    }

    @GetMapping
    public R<List<Map<String, Object>>> list(@RequestParam(required = false) String packageType,
                                             @RequestParam(required = false) Integer status) {
        var query = pointProductService.lambdaQuery();
        if (packageType != null && !packageType.isBlank()) {
            query.eq(PointProduct::getPackageType, packageType);
        }
        if (status != null) {
            query.eq(PointProduct::getStatus, status);
        }
        List<PointProduct> list = query
                .orderByAsc(PointProduct::getSortOrder)
                .orderByAsc(PointProduct::getId)
                .list();
        return R.ok(MapUtil.beansToMaps(list));
    }

    @PostMapping
    public R<Map<String, Object>> create(@Valid @RequestBody CreateProductRequest request) {
        PointProduct product = new PointProduct();
        product.setName(request.name());
        product.setPoints(request.points());
        product.setBonusPoints(request.bonusPoints());
        product.setPriceCents(request.priceCents());
        product.setCurrency(request.currency() != null ? request.currency() : "USD");
        product.setStoreProductId(request.storeProductId());
        product.setPackageType(request.packageType() != null ? request.packageType() : "RECHARGE");
        product.setDurationDays(request.durationDays());
        product.setOriginalPriceCents(request.originalPriceCents());
        product.setTagText(request.tagText());
        product.setCoverUrl(request.coverUrl());
        product.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        product.setProductCategory(request.productCategory() != null ? request.productCategory() : "RECHARGE");
        product.setMembershipLevel(request.membershipLevel());
        product.setDailyLimit(request.dailyLimit());
        product.setMonthlyLimit(request.monthlyLimit());
        product.setFirstPurchaseBonus(request.firstPurchaseBonus() != null ? request.firstPurchaseBonus() : 0);
        product.setStatus(1);
        pointProductService.save(product);
        return R.ok(MapUtil.beanToMap(product));
    }

    @PutMapping("/{id}")
    public R<Map<String, Object>> update(@PathVariable Long id,
                                          @Valid @RequestBody UpdateProductRequest request) {
        PointProduct product = pointProductService.getById(id);
        if (product == null) {
            return R.fail("product not found");
        }
        pointProductService.lambdaUpdate()
                .set(PointProduct::getName, request.name())
                .set(PointProduct::getPoints, request.points())
                .set(PointProduct::getBonusPoints, request.bonusPoints())
                .set(PointProduct::getPriceCents, request.priceCents())
                .set(PointProduct::getCurrency, request.currency())
                .set(PointProduct::getStoreProductId, request.storeProductId())
                .set(PointProduct::getPackageType, request.packageType())
                .set(PointProduct::getDurationDays, request.durationDays())
                .set(PointProduct::getOriginalPriceCents, request.originalPriceCents())
                .set(PointProduct::getTagText, request.tagText())
                .set(PointProduct::getCoverUrl, request.coverUrl())
                .set(PointProduct::getSortOrder, request.sortOrder())
                .set(PointProduct::getStatus, request.status())
                .set(PointProduct::getProductCategory, request.productCategory())
                .set(PointProduct::getMembershipLevel, request.membershipLevel())
                .set(PointProduct::getDailyLimit, request.dailyLimit())
                .set(PointProduct::getMonthlyLimit, request.monthlyLimit())
                .set(PointProduct::getFirstPurchaseBonus, request.firstPurchaseBonus())
                .eq(PointProduct::getId, id)
                .update();
        product = pointProductService.getById(id);
        return R.ok(MapUtil.beanToMap(product));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        pointProductService.lambdaUpdate()
                .set(PointProduct::getStatus, -1)
                .eq(PointProduct::getId, id)
                .update();
        return R.ok();
    }

    @PutMapping("/{id}/status")
    public R<Map<String, Object>> updateStatus(@PathVariable Long id,
                                                @RequestBody Map<String, Integer> body) {
        PointProduct product = pointProductService.getById(id);
        if (product == null) {
            return R.fail("product not found");
        }
        Integer status = body.get("status");
        if (status != null) {
            pointProductService.lambdaUpdate()
                    .set(PointProduct::getStatus, status)
                    .eq(PointProduct::getId, id)
                    .update();
            product.setStatus(status);
        }
        return R.ok(MapUtil.beanToMap(product));
    }
}
