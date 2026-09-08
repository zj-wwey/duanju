package com.duanju.service.entity;

import com.baomidou.mybatisplus.extension.service.IService;
import com.duanju.entity.UserLike;

public interface UserLikeService extends IService<UserLike> {

    int insertIgnore(Long userId, Long dramaId);
}
