package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.DramaCategoryFilter;
import com.duanju.mapper.entity.DramaCategoryFilterMapper;
import com.duanju.service.entity.DramaCategoryFilterService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DramaCategoryFilterServiceImpl extends ServiceImpl<DramaCategoryFilterMapper, DramaCategoryFilter> implements DramaCategoryFilterService {

    @Override
    public List<Map<String, Object>> categoryFilters() {
        return baseMapper.categoryFilters();
    }

    @Override
    public List<Map<String, Object>> adminCategoryFilters() {
        return baseMapper.adminCategoryFilters();
    }

    @Override
    public void insertCategoryFilter(Map<String, Object> filter) {
        baseMapper.insertCategoryFilter(filter);
    }

    @Override
    public int updateCategoryFilter(Map<String, Object> filter) {
        return baseMapper.updateCategoryFilter(filter);
    }
}
