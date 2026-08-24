package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.dto.order.OrderStatusUpdateRequest;
import com.duanju.security.RequiresPermission;
import com.duanju.service.OrderService;
import com.duanju.service.PayPalPaymentService;
import com.duanju.service.StripePaymentService;
import com.duanju.util.MapUtil;
import jakarta.validation.Valid;
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
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;
    private final StripePaymentService stripePaymentService;
    private final PayPalPaymentService payPalPaymentService;

    public AdminOrderController(OrderService orderService,
                                StripePaymentService stripePaymentService,
                                PayPalPaymentService payPalPaymentService) {
        this.orderService = orderService;
        this.stripePaymentService = stripePaymentService;
        this.payPalPaymentService = payPalPaymentService;
    }

    /** 支付渠道状态:启用情况 + 最近24小时订单统计 */
    @GetMapping("/payment/channels/status")
    public R<Map<String, Object>> paymentChannelsStatus() {
        return R.ok(orderService.getPaymentChannelsStatus());
    }

    @GetMapping
    public R<List<Map<String, Object>>> orders(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(defaultValue = "100") int limit) {
        return R.ok(orderService.getAdminOrders(keyword, status, limit));
    }

    @PostMapping("/{orderNo}/pay")
    public R<Map<String, Object>> markPaid(@PathVariable String orderNo) {
        return R.ok(orderService.markPaid(orderNo));
    }

    @PostMapping("/{orderNo}/refund")
    public R<Map<String, Object>> refund(@PathVariable String orderNo) {
        Map<String, Object> order = orderService.mustOrder(orderNo);
        String payChannel = MapUtil.str(order, "pay_channel");
        if ("STRIPE".equals(payChannel)) {
            return R.ok(stripePaymentService.refundOrder(orderNo));
        } else if ("PAYPAL".equals(payChannel)) {
            return R.ok(payPalPaymentService.refundOrder(orderNo));
        }
        return R.ok(orderService.refund(orderNo));
    }

    @PutMapping("/{orderNo}/status")
    public R<Map<String, Object>> updateStatus(@PathVariable String orderNo, @Valid @RequestBody OrderStatusUpdateRequest request) {
        return R.ok(orderService.updateStatus(orderNo, request.status()));
    }
}
