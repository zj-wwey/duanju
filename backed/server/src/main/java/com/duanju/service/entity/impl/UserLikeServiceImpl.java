package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.UserLike;
import com.duanju.mapper.entity.UserLikeMapper;
import com.duanju.service.entity.UserLikeService;
import org.springframework.stereotype.Service;

@Service
public class UserLikeServiceImpl extends ServiceImpl<UserLikeMapper, UserLike> implements UserLikeService {

    @Override
    public int insertIgnore(Long userId, Long dramaId) {
        return baseMapper.insertIgnore(userId, dramaId);
    }
}
