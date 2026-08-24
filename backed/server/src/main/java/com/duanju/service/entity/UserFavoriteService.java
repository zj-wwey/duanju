package com.duanju.service.entity;

import com.baomidou.mybatisplus.extension.service.IService;
import com.duanju.entity.UserFavorite;

import java.util.List;
import java.util.Map;

public interface UserFavoriteService extends IService<UserFavorite> {

    int insertIgnore(Long userId, Long dramaId);

    List<Map<String, Object>> favorites(Long userId);
}
