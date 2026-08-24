package com.duanju.service.entity;

import com.baomidou.mybatisplus.extension.service.IService;
import com.duanju.entity.UserOrder;

import java.util.List;
import java.util.Map;

public interface UserOrderService extends IService<UserOrder> {

    List<Map<String, Object>> userOrders(Long userId, int limit);

    Map<String, Object> orderByNo(String orderNo);

    List<Map<String, Object>> adminOrders(String keyword, String status, int limit);

    List<Map<String, Object>> listMapsByChannelAndCreatedAt(String payChannel, java.time.LocalDateTime createdAtFrom);
}
