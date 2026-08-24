package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.UserOrder;
import com.duanju.mapper.entity.UserOrderMapper;
import com.duanju.service.entity.UserOrderService;
import com.duanju.util.MapUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class UserOrderServiceImpl extends ServiceImpl<UserOrderMapper, UserOrder> implements UserOrderService {

    @Override
    public List<Map<String, Object>> userOrders(Long userId, int limit) {
        return baseMapper.userOrders(userId, limit);
    }

    @Override
    public Map<String, Object> orderByNo(String orderNo) {
        return baseMapper.orderByNo(orderNo);
    }

    @Override
    public List<Map<String, Object>> adminOrders(String keyword, String status, int limit) {
        return baseMapper.adminOrders(keyword, status, limit);
    }

    @Override
    public List<Map<String, Object>> listMapsByChannelAndCreatedAt(String payChannel, LocalDateTime createdAtFrom) {
        List<UserOrder> list = lambdaQuery()
                .eq(UserOrder::getPayChannel, payChannel)
                .ge(UserOrder::getCreatedAt, createdAtFrom)
                .orderByDesc(UserOrder::getCreatedAt)
                .last("limit 500")
                .list();
        return MapUtil.beansToMaps(list);
    }
}
