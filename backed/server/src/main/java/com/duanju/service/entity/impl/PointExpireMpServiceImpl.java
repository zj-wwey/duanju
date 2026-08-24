package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.PointExpire;
import com.duanju.mapper.entity.PointExpireMapper;
import com.duanju.service.entity.PointExpireMpService;
import org.springframework.stereotype.Service;

@Service
public class PointExpireMpServiceImpl extends ServiceImpl<PointExpireMapper, PointExpire> implements PointExpireMpService {
}