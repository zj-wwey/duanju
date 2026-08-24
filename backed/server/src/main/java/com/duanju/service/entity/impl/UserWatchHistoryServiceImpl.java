package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.UserWatchHistory;
import com.duanju.mapper.entity.UserWatchHistoryMapper;
import com.duanju.service.entity.UserWatchHistoryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class UserWatchHistoryServiceImpl extends ServiceImpl<UserWatchHistoryMapper, UserWatchHistory> implements UserWatchHistoryService {

    @Override
    public int saveHistory(Long userId, Long dramaId, Long episodeId, Integer progressSeconds) {
        return baseMapper.saveHistory(userId, dramaId, episodeId, progressSeconds);
    }

    @Override
    public List<Map<String, Object>> histories(Long userId) {
        return baseMapper.histories(userId);
    }

    @Override
    public int deleteHistory(Long userId, Long dramaId) {
        return baseMapper.delete(new LambdaQueryWrapper<UserWatchHistory>()
                .eq(UserWatchHistory::getUserId, userId)
                .eq(UserWatchHistory::getDramaId, dramaId));
    }

    @Override
    public int clearHistories(Long userId) {
        return baseMapper.delete(new LambdaQueryWrapper<UserWatchHistory>()
                .eq(UserWatchHistory::getUserId, userId));
    }
}
