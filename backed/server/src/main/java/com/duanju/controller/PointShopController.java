package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.security.PrincipalHolder;
import com.duanju.service.PointShopService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shop")
public class PointShopController {

    private final PointShopService pointShopService;

    public PointShopController(PointShopService pointShopService) {
        this.pointShopService = pointShopService;
    }

    /** 商城商品列表 */
    @GetMapping("/items")
    public R<List<Map<String, Object>>> items() {
        return R.ok(pointShopService.listItems());
    }

    /** 兑换商品 */
    @PostMapping("/exchange/{id}")
    public R<Map<String, Object>> exchange(@PathVariable Long id) {
        return R.ok(pointShopService.exchange(PrincipalHolder.userId(), id));
    }

    /** 兑换记录 */
    @GetMapping("/records")
    public R<List<Map<String, Object>>> records() {
        return R.ok(pointShopService.getRecords(PrincipalHolder.userId()));
    }
}