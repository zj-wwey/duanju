package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.PointShopOrder;
import com.duanju.mapper.entity.PointShopOrderMapper;
import com.duanju.service.entity.PointShopOrderService;
import org.springframework.stereotype.Service;

@Service
public class PointShopOrderServiceImpl extends ServiceImpl<PointShopOrderMapper, PointShopOrder> implements PointShopOrderService {
}