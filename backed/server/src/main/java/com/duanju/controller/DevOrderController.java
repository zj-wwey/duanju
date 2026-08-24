package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.service.OrderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 开发环境 mock-pay 端点。
 * 双重保护：必须同时满足 dev profile + duanju.dev.mock-pay=true 才会激活。
 * 生产环境不会注册此 Bean，避免暴露免费充值入口。
 */
@Profile("dev")
@ConditionalOnProperty(name = "duanju.dev.mock-pay", havingValue = "true")
@RestController
@RequestMapping("/api/user")
public class DevOrderController {

    private final OrderService orderService;

    public DevOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders/{orderNo}/mock-pay")
    public R<Map<String, Object>> mockPay(@PathVariable String orderNo) {
        return R.ok(orderService.markPaid(orderNo));
    }
}
