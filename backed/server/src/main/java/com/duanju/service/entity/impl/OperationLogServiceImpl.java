package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.OperationLog;
import com.duanju.mapper.entity.OperationLogMapper;
import com.duanju.service.entity.OperationLogService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> implements OperationLogService {

    @Override
    public List<Map<String, Object>> operationLogs(int limit) {
        return baseMapper.operationLogs(limit);
    }
}
