package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.UserMembership;
import com.duanju.mapper.entity.UserMembershipMapper;
import com.duanju.service.entity.UserMembershipService;
import org.springframework.stereotype.Service;

@Service
public class UserMembershipServiceImpl extends ServiceImpl<UserMembershipMapper, UserMembership> implements UserMembershipService {
}