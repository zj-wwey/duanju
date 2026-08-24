package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.PointRecord;
import com.duanju.mapper.entity.PointRecordMapper;
import com.duanju.service.entity.PointRecordService;
import org.springframework.stereotype.Service;

@Service
public class PointRecordServiceImpl extends ServiceImpl<PointRecordMapper, PointRecord> implements PointRecordService {
}
