package com.duanju.mapper.entity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duanju.entity.UserEpisodeUnlock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserEpisodeUnlockMapper extends BaseMapper<UserEpisodeUnlock> {

    @Select("""
            select count(*) from user_episode_unlock
            where user_id = #{userId} and drama_id = #{dramaId}
              and (episode_id = #{episodeId} or unlock_type = 'DRAMA')
            """)
    int unlocked(@Param("userId") Long userId, @Param("dramaId") Long dramaId, @Param("episodeId") Long episodeId);
}
