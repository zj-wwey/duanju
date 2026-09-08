package com.duanju.mapper.entity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duanju.entity.DramaEpisode;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface DramaEpisodeMapper extends BaseMapper<DramaEpisode> {

    @Select("""
            select e.id, e.drama_id, e.episode_no, e.title, e.description, e.cover_url, e.video_url,
                   e.price_points, e.duration_seconds, e.video_duration, e.is_free, e.access_type,
                   e.cloudflare_uid, e.hls_url, e.transcode_status, e.status, e.storage_provider
            from drama_episode e join drama d on d.id = e.drama_id
            where e.id = #{episodeId} and e.status = 1 and d.status = 1
            """)
    Map<String, Object> episode(@Param("episodeId") Long episodeId);

    @Select("""
            select id, drama_id, episode_no, title, description, cover_url, video_url, price_points,
                   duration_seconds, video_duration, is_free, access_type, sort_order, storage_provider,
                   cloudflare_uid, hls_url, transcode_status, status, created_at
            from drama_episode
            where drama_id = #{dramaId} and status = 1
            order by sort_order asc, episode_no asc
            """)
    List<Map<String, Object>> episodes(@Param("dramaId") Long dramaId);

    @Select("""
            select id, drama_id, episode_no, title, description, cover_url, video_url, price_points,
                   duration_seconds, is_free, access_type, sort_order, storage_provider, status, created_at,
                   transcode_status, hls_url,
                   (select count(*) from episode_play_event pe where pe.episode_id = drama_episode.id) play_count,
                   (select count(distinct pe.user_id) from episode_play_event pe where pe.episode_id = drama_episode.id) play_users,
                   (select ifnull(round(avg(pe.progress_seconds), 0), 0) from episode_play_event pe where pe.episode_id = drama_episode.id) avg_watch_seconds,
                   (select case when drama_episode.duration_seconds > 0 then ifnull(round(
                     count(distinct case when pe.progress_seconds >= drama_episode.duration_seconds * 0.9 then pe.user_id end)
                     / nullif(count(distinct pe.user_id), 0) * 100, 2), 0) else 0 end
                    from episode_play_event pe where pe.episode_id = drama_episode.id) completion_rate,
                   (select case when drama_episode.duration_seconds > 0 then ifnull(round(
                     count(distinct case when pe.progress_seconds < drama_episode.duration_seconds * 0.3 then pe.user_id end)
                     / nullif(count(distinct pe.user_id), 0) * 100, 2), 0) else 0 end
                    from episode_play_event pe where pe.episode_id = drama_episode.id) churn_rate,
                   (select count(distinct u.user_id) from user_episode_unlock u where u.episode_id = drama_episode.id) unlock_users,
                   (select ifnull(sum(u.points_cost), 0) from user_episode_unlock u where u.episode_id = drama_episode.id) revenue_points
            from drama_episode
            where drama_id = #{dramaId} and status >= 0
              and (#{keyword} is null or #{keyword} = ''
                or title like concat('%', #{keyword}, '%')
                or description like concat('%', #{keyword}, '%')
                or cast(episode_no as char) = #{keyword})
              and (#{accessType} is null or access_type = #{accessType})
              and (#{status} is null or status = #{status})
            order by sort_order asc, episode_no asc
            """)
    List<Map<String, Object>> adminEpisodes(@Param("dramaId") Long dramaId,
                                            @Param("keyword") String keyword,
                                            @Param("accessType") String accessType,
                                            @Param("status") Integer status);

    @Insert("""
            insert into drama_episode(drama_id, episode_no, title, description, cover_url, video_url, cloudflare_uid, price_points,
              duration_seconds, video_duration, is_free, access_type, sort_order, storage_provider, status, cover_object_key)
            values(#{dramaId}, #{episodeNo}, #{title}, #{description}, #{coverUrl}, #{videoUrl}, #{cloudflareUid}, #{pricePoints},
              #{durationSeconds}, #{videoDurationSeconds}, #{isFree}, #{accessType}, #{sortOrder}, #{storageProvider}, #{status}, #{coverObjectKey})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertEpisode(Map<String, Object> episode);

    @Update("""
            update drama_episode set drama_id=#{dramaId}, episode_no=#{episodeNo}, title=#{title}, description=#{description},
              cover_url=#{coverUrl}, video_url=#{videoUrl}, cloudflare_uid=#{cloudflareUid}, price_points=#{pricePoints},
              duration_seconds=#{durationSeconds}, video_duration=#{videoDurationSeconds},
              is_free=#{isFree}, access_type=#{accessType}, sort_order=#{sortOrder}, storage_provider=#{storageProvider}, status=#{status},
              cover_object_key=#{coverObjectKey}
            where id=#{id}
            """)
    int updateEpisode(Map<String, Object> episode);

    @Update("""
            update drama_episode
            set is_free = case when episode_no <= #{freeCount} then 1 else 0 end,
                access_type = case when episode_no <= #{freeCount} then 'FREE' else 'POINTS' end,
                price_points = case when episode_no <= #{freeCount} then 0 else #{pricePoints} end
            where drama_id = #{dramaId} and status >= 0
            """)
    int applyFreePreview(@Param("dramaId") Long dramaId,
                         @Param("freeCount") int freeCount,
                         @Param("pricePoints") int pricePoints);
}
