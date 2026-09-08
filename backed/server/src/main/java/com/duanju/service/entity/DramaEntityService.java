package com.duanju.service.entity;

import com.baomidou.mybatisplus.extension.service.IService;
import com.duanju.entity.Drama;

import java.util.List;
import java.util.Map;

public interface DramaEntityService extends IService<Drama> {

    List<Map<String, Object>> dramas(String contentType, String keyword, String background,
                                     String theme, String setting, String audience,
                                     Integer timeDays, String sort);

    List<Map<String, Object>> adminDramas(String contentType, Integer status, String keyword);

    Map<String, Object> drama(Long id);

    void insertDrama(Map<String, Object> drama);

    int updateDrama(Map<String, Object> drama);

    int syncDramaEpisodeTotal(Long dramaId);

    List<Map<String, Object>> feedDramas(String contentType, Boolean recommended, int offset, int size);

    int countFeedDramas(String contentType, Boolean recommended);
}
