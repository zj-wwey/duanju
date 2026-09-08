package com.duanju.mapper.entity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duanju.entity.UserLike;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserLikeMapper extends BaseMapper<UserLike> {

    @Insert("insert ignore into user_like(user_id, drama_id) values(#{userId}, #{dramaId})")
    int insertIgnore(@Param("userId") Long userId, @Param("dramaId") Long dramaId);
}
