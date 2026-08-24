package com.duanju.mapper.entity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duanju.entity.UserWatchHistory;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserWatchHistoryMapper extends BaseMapper<UserWatchHistory> {

    @Insert("""
            insert into user_watch_history(user_id, drama_id, episode_id, progress_seconds)
            values(#{userId}, #{dramaId}, #{episodeId}, #{progressSeconds})
            on duplicate key update episode_id=values(episode_id), progress_seconds=values(progress_seconds), updated_at=now()
            """)
    int saveHistory(@Param("userId") Long userId, @Param("dramaId") Long dramaId, @Param("episodeId") Long episodeId,
                    @Param("progressSeconds") Integer progressSeconds);

    @Select("""
            select h.id, h.drama_id, h.episode_id, h.progress_seconds, h.updated_at, d.title drama_title,
                   d.cover_url, e.episode_no, e.title episode_title
            from user_watch_history h
            join drama d on d.id = h.drama_id
            join drama_episode e on e.id = h.episode_id
            where h.user_id = #{userId}
              and d.status = 1
              and e.status = 1
            order by h.updated_at desc
            """)
    List<Map<String, Object>> histories(@Param("userId") Long userId);
}
