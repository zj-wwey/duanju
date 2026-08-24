package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.UserInvite;
import com.duanju.mapper.entity.UserInviteMapper;
import com.duanju.service.entity.UserInviteService;
import org.springframework.stereotype.Service;

@Service
public class UserInviteServiceImpl extends ServiceImpl<UserInviteMapper, UserInvite> implements UserInviteService {
}