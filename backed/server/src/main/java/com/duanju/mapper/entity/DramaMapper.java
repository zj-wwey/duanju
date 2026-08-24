package com.duanju.mapper.entity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duanju.entity.Drama;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface DramaMapper extends BaseMapper<Drama> {

    @Select("""
            select d.id, d.title, d.description, d.cover_url,
                   d.horizontal_cover_url, d.vertical_cover_url, d.tags,
                   d.free_episode_count, d.total_episodes, d.episode_price_points, d.whole_price_points,
                   d.content_type, d.background, d.theme, d.setting_key,
                   d.audience, d.publish_date, d.online_time, d.hot_score, d.recommended, d.status, d.sort_order, d.created_at
            from drama d
            where d.status = 1
              and (#{contentType} is null or d.content_type = #{contentType})
              and (#{keyword} is null or #{keyword} = ''
                or d.title like concat('%', #{keyword}, '%')
                or d.description like concat('%', #{keyword}, '%')
                or d.tags like concat('%', #{keyword}, '%'))
              and (#{background} is null or d.background = #{background})
              and (#{theme} is null or d.theme = #{theme})
              and (#{setting} is null or d.setting_key = #{setting})
              and (#{audience} is null or d.audience = #{audience})
              and (#{timeDays} is null or coalesce(d.publish_date, date(d.created_at)) >=
                case #{timeDays}
                  when 7 then date_sub(current_date, interval 7 day)
                  when 14 then date_sub(current_date, interval 14 day)
                  when 30 then date_sub(current_date, interval 30 day)
                  when 90 then date_sub(current_date, interval 90 day)
                  else date('1000-01-01')
                end)
            order by
              d.recommended desc,
              case when #{sort} = 'newest' then coalesce(d.publish_date, date(d.created_at)) end desc,
              case when #{sort} = 'newest' then d.id end desc,
              case when #{sort} = 'hottest' then d.hot_score end desc,
              case when #{sort} = 'hottest' then d.id end desc,
              d.sort_order asc,
              d.id desc
            """)
    List<Map<String, Object>> dramas(@Param("contentType") String contentType,
                                     @Param("keyword") String keyword,
                                     @Param("background") String background,
                                     @Param("theme") String theme,
                                     @Param("setting") String setting,
                                     @Param("audience") String audience,
                                     @Param("timeDays") Integer timeDays,
                                     @Param("sort") String sort);

    @Select("""
            select d.id, d.title, d.description, d.cover_url,
                   d.horizontal_cover_url, d.vertical_cover_url, d.tags,
                   d.free_episode_count, d.total_episodes, d.episode_price_points, d.whole_price_points,
                   d.content_type, d.background, d.theme, d.setting_key,
                   d.audience, d.publish_date, d.online_time, d.hot_score, d.recommended, d.status, d.sort_order, d.created_at,
                   (select count(*) from drama_episode e where e.drama_id = d.id and e.status >= 0) episode_count,
                   (select count(*) from episode_play_event pe where pe.drama_id = d.id) play_count,
                   (select ifnull(sum(u.points_cost), 0) from user_episode_unlock u where u.drama_id = d.id) revenue_points
            from drama d
            where d.status >= 0
              and (#{contentType} is null or d.content_type = #{contentType})
              and (#{status} is null or d.status = #{status})
              and (#{keyword} is null or #{keyword} = ''
                or d.title like concat('%', #{keyword}, '%')
                or d.description like concat('%', #{keyword}, '%')
                or d.tags like concat('%', #{keyword}, '%'))
            order by d.recommended desc, d.sort_order asc, d.id desc
            """)
    List<Map<String, Object>> adminDramas(@Param("contentType") String contentType,
                                          @Param("status") Integer status,
                                          @Param("keyword") String keyword);

    @Select("""
            select d.id, d.title, d.description, d.cover_url,
                   d.horizontal_cover_url, d.vertical_cover_url, d.tags,
                   d.free_episode_count, d.total_episodes, d.episode_price_points, d.whole_price_points,
                   d.content_type, d.background, d.theme, d.setting_key,
                   d.audience, d.publish_date, d.online_time, d.hot_score, d.recommended, d.status, d.sort_order, d.created_at
            from drama d
            where d.id = #{id} and d.status = 1
            """)
    Map<String, Object> drama(@Param("id") Long id);

    @Insert("""
            insert into drama(title, description, cover_url, free_episode_count, total_episodes,
              horizontal_cover_url, vertical_cover_url, tags, episode_price_points, whole_price_points,
              content_type, background, theme, setting_key, audience, publish_date, online_time, hot_score, recommended, status, sort_order)
            values(#{title}, #{description}, #{coverUrl}, #{freeEpisodeCount}, #{totalEpisodes},
              #{horizontalCoverUrl}, #{verticalCoverUrl}, #{tags}, #{episodePricePoints}, #{wholePricePoints},
              #{contentType}, #{background}, #{theme}, #{setting}, #{audience}, #{publishDate}, #{onlineTime}, #{hotScore}, #{recommended}, #{status}, #{sortOrder})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertDrama(Map<String, Object> drama);

    @Update("""
            update drama set title=#{title}, description=#{description}, cover_url=#{coverUrl},
              horizontal_cover_url=#{horizontalCoverUrl}, vertical_cover_url=#{verticalCoverUrl}, tags=#{tags},
              free_episode_count=#{freeEpisodeCount}, total_episodes=#{totalEpisodes},
              episode_price_points=#{episodePricePoints}, whole_price_points=#{wholePricePoints}, content_type=#{contentType},
              background=#{background}, theme=#{theme}, setting_key=#{setting}, audience=#{audience},
              publish_date=#{publishDate}, online_time=#{onlineTime}, hot_score=#{hotScore}, recommended=#{recommended},
              status=#{status}, sort_order=#{sortOrder}
            where id=#{id}
            """)
    int updateDrama(Map<String, Object> drama);

    @Update("""
            update drama
            set total_episodes = (
                select count(*) from drama_episode
                where drama_id = #{dramaId} and status >= 0
            )
            where id = #{dramaId}
            """)
    int syncDramaEpisodeTotal(@Param("dramaId") Long dramaId);
}
