package com.duanju.mapper.entity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duanju.entity.UserFavorite;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {

    @Insert("insert ignore into user_favorite(user_id, drama_id) values(#{userId}, #{dramaId})")
    int insertIgnore(@Param("userId") Long userId, @Param("dramaId") Long dramaId);

    @Select("""
            select d.id, d.title, d.cover_url, d.description, f.created_at
            from user_favorite f join drama d on d.id = f.drama_id
            where f.user_id = #{userId}
              and d.status = 1
            order by f.created_at desc
            """)
    List<Map<String, Object>> favorites(@Param("userId") Long userId);
}
