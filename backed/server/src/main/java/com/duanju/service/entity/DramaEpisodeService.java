package com.duanju.service.entity;

import com.baomidou.mybatisplus.extension.service.IService;
import com.duanju.entity.DramaEpisode;

import java.util.List;
import java.util.Map;

public interface DramaEpisodeService extends IService<DramaEpisode> {

    Map<String, Object> episode(Long episodeId);

    List<Map<String, Object>> episodes(Long dramaId);

    List<Map<String, Object>> adminEpisodes(Long dramaId, String keyword, String accessType, Integer status);

    void insertEpisode(Map<String, Object> episode);

    int updateEpisode(Map<String, Object> episode);

    int applyFreePreview(Long dramaId, int freeCount, int pricePoints);
}
