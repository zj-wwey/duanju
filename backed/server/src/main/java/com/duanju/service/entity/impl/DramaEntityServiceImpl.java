package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.Drama;
import com.duanju.mapper.entity.DramaMapper;
import com.duanju.service.entity.DramaEntityService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DramaEntityServiceImpl extends ServiceImpl<DramaMapper, Drama> implements DramaEntityService {

    @Override
    public List<Map<String, Object>> dramas(String contentType, String keyword, String background,
                                            String theme, String setting, String audience,
                                            Integer timeDays, String sort) {
        return baseMapper.dramas(contentType, keyword, background, theme, setting, audience, timeDays, sort);
    }

    @Override
    public List<Map<String, Object>> adminDramas(String contentType, Integer status, String keyword) {
        return baseMapper.adminDramas(contentType, status, keyword);
    }

    @Override
    public Map<String, Object> drama(Long id) {
        return baseMapper.drama(id);
    }

    @Override
    public void insertDrama(Map<String, Object> drama) {
        baseMapper.insertDrama(drama);
    }

    @Override
    public int updateDrama(Map<String, Object> drama) {
        return baseMapper.updateDrama(drama);
    }

    @Override
    public int syncDramaEpisodeTotal(Long dramaId) {
        return baseMapper.syncDramaEpisodeTotal(dramaId);
    }

    @Override
    public List<Map<String, Object>> feedDramas(String contentType, Boolean recommended, int offset, int size) {
        return baseMapper.feedDramas(contentType, recommended, offset, size);
    }

    @Override
    public int countFeedDramas(String contentType, Boolean recommended) {
        return baseMapper.countFeedDramas(contentType, recommended);
    }
}
