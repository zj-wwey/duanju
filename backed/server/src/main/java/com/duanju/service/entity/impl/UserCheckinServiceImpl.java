package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.UserCheckin;
import com.duanju.mapper.entity.UserCheckinMapper;
import com.duanju.service.entity.UserCheckinService;
import org.springframework.stereotype.Service;

@Service
public class UserCheckinServiceImpl extends ServiceImpl<UserCheckinMapper, UserCheckin> implements UserCheckinService {
}
