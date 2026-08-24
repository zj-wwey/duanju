package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.UserFavorite;
import com.duanju.mapper.entity.UserFavoriteMapper;
import com.duanju.service.entity.UserFavoriteService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class UserFavoriteServiceImpl extends ServiceImpl<UserFavoriteMapper, UserFavorite> implements UserFavoriteService {

    @Override
    public int insertIgnore(Long userId, Long dramaId) {
        return baseMapper.insertIgnore(userId, dramaId);
    }

    @Override
    public List<Map<String, Object>> favorites(Long userId) {
        return baseMapper.favorites(userId);
    }
}
