package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.DramaEpisode;
import com.duanju.mapper.entity.DramaEpisodeMapper;
import com.duanju.service.entity.DramaEpisodeService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DramaEpisodeServiceImpl extends ServiceImpl<DramaEpisodeMapper, DramaEpisode> implements DramaEpisodeService {

    @Override
    public Map<String, Object> episode(Long episodeId) {
        return baseMapper.episode(episodeId);
    }

    @Override
    public List<Map<String, Object>> episodes(Long dramaId) {
        return baseMapper.episodes(dramaId);
    }

    @Override
    public List<Map<String, Object>> adminEpisodes(Long dramaId, String keyword, String accessType, Integer status) {
        return baseMapper.adminEpisodes(dramaId, keyword, accessType, status);
    }

    @Override
    public void insertEpisode(Map<String, Object> episode) {
        baseMapper.insertEpisode(episode);
    }

    @Override
    public int updateEpisode(Map<String, Object> episode) {
        return baseMapper.updateEpisode(episode);
    }

    @Override
    public int applyFreePreview(Long dramaId, int freeCount, int pricePoints) {
        return baseMapper.applyFreePreview(dramaId, freeCount, pricePoints);
    }
}
