package com.duanju.service.entity;

import com.baomidou.mybatisplus.extension.service.IService;
import com.duanju.entity.UserWatchHistory;

import java.util.List;
import java.util.Map;

public interface UserWatchHistoryService extends IService<UserWatchHistory> {

    int saveHistory(Long userId, Long dramaId, Long episodeId, Integer progressSeconds);

    List<Map<String, Object>> histories(Long userId);

    int deleteHistory(Long userId, Long dramaId);

    int clearHistories(Long userId);
}
