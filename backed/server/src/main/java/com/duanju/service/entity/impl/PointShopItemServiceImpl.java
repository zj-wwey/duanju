package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.PointShopItem;
import com.duanju.mapper.entity.PointShopItemMapper;
import com.duanju.service.entity.PointShopItemService;
import org.springframework.stereotype.Service;

@Service
public class PointShopItemServiceImpl extends ServiceImpl<PointShopItemMapper, PointShopItem> implements PointShopItemService {
}