package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.UserVipRecord;
import com.duanju.mapper.entity.UserVipRecordMapper;
import com.duanju.service.entity.UserVipRecordService;
import org.springframework.stereotype.Service;

@Service
public class UserVipRecordServiceImpl extends ServiceImpl<UserVipRecordMapper, UserVipRecord> implements UserVipRecordService {
}
