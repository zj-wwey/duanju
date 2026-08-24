package com.duanju.service.entity;

import com.baomidou.mybatisplus.extension.service.IService;
import com.duanju.entity.DramaCategoryFilter;

import java.util.List;
import java.util.Map;

public interface DramaCategoryFilterService extends IService<DramaCategoryFilter> {

    List<Map<String, Object>> categoryFilters();

    List<Map<String, Object>> adminCategoryFilters();

    void insertCategoryFilter(Map<String, Object> filter);

    int updateCategoryFilter(Map<String, Object> filter);
}
