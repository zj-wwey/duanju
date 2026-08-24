package com.duanju.service.entity;

import com.baomidou.mybatisplus.extension.service.IService;
import com.duanju.entity.UserEpisodeUnlock;

public interface UserEpisodeUnlockService extends IService<UserEpisodeUnlock> {

    int unlocked(Long userId, Long dramaId, Long episodeId);
}
