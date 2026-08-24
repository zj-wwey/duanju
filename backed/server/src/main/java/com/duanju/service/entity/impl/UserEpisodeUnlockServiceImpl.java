package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.UserEpisodeUnlock;
import com.duanju.mapper.entity.UserEpisodeUnlockMapper;
import com.duanju.service.entity.UserEpisodeUnlockService;
import org.springframework.stereotype.Service;

@Service
public class UserEpisodeUnlockServiceImpl extends ServiceImpl<UserEpisodeUnlockMapper, UserEpisodeUnlock> implements UserEpisodeUnlockService {

    @Override
    public int unlocked(Long userId, Long dramaId, Long episodeId) {
        return baseMapper.unlocked(userId, dramaId, episodeId);
    }
}
