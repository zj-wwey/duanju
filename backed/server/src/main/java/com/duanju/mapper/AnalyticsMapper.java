package com.duanju.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AnalyticsMapper {
    @Select("""
            select
              (select count(*) from app_user where status >= 0) totalUsers,
              (select count(*) from drama where status >= 0) totalDramas,
              (select count(*) from drama_episode where status >= 0) totalEpisodes,
              (select count(*) from user_watch_history) watchedDramas,
              (select count(distinct user_id) from user_watch_history) activeViewers,
              (select count(*) from episode_play_event) playEvents,
              (select ifnull(sum(points_cost), 0) from user_episode_unlock) unlockPoints,
              (select count(*) from user_episode_unlock) unlockCount,
              (select count(*) from user_order where status = 'PAID') paidOrders,
              (select ifnull(sum(amount_cents), 0) from user_order where status = 'PAID') paidAmountCents
            from (select 1) dummy
            """)
    List<Map<String, Object>> overview();

    @Select("""
            select d.id drama_id, d.title drama_title, d.cover_url,
                   count(e.id) play_events,
                   count(distinct e.user_id) viewers,
                   count(distinct h.user_id) watched_users,
                   ifnull(sum(e.progress_seconds), 0) progress_seconds,
                   count(distinct u.id) unlock_count,
                   ifnull(sum(u.points_cost), 0) unlock_points
            from drama d
            left join episode_play_event e on e.drama_id = d.id
              and (#{days} is null or timestampdiff(day, e.created_at, now()) <= #{days})
            left join user_watch_history h on h.drama_id = d.id
            left join user_episode_unlock u on u.drama_id = d.id
              and (#{days} is null or timestampdiff(day, u.created_at, now()) <= #{days})
            where d.status >= 0
            group by d.id, d.title, d.cover_url
            order by play_events desc, viewers desc, d.id desc
            limit #{limit}
            """)
    List<Map<String, Object>> dramaRanking(@Param("days") Integer days, @Param("limit") int limit);

    @Select("""
            select e.id episode_id, e.drama_id, d.title drama_title, e.episode_no, e.title episode_title,
                   count(pe.id) play_events,
                   count(distinct pe.user_id) viewers,
                   ifnull(avg(pe.progress_seconds), 0) avg_progress_seconds,
                   ifnull(max(pe.progress_seconds), 0) max_progress_seconds,
                   count(distinct u.user_id) unlock_users,
                   ifnull(sum(u.points_cost), 0) unlock_points
            from drama_episode e
            join drama d on d.id = e.drama_id
            left join episode_play_event pe on pe.episode_id = e.id
              and (#{days} is null or timestampdiff(day, pe.created_at, now()) <= #{days})
            left join user_episode_unlock u on u.episode_id = e.id
              and (#{days} is null or timestampdiff(day, u.created_at, now()) <= #{days})
            where e.status >= 0 and (#{dramaId} is null or e.drama_id = #{dramaId})
            group by e.id, e.drama_id, d.title, e.episode_no, e.title
            order by e.drama_id asc, e.episode_no asc
            """)
    List<Map<String, Object>> episodeStats(@Param("dramaId") Long dramaId, @Param("days") Integer days);

    @Select("""
            select date(created_at) stat_date,
                   count(*) play_events,
                   count(distinct user_id) viewers,
                   count(distinct drama_id) dramas,
                   ifnull(sum(progress_seconds), 0) progress_seconds
            from episode_play_event
            where timestampdiff(day, created_at, now()) <= #{days}
            group by date(created_at)
            order by stat_date asc
            """)
    List<Map<String, Object>> playTrend(@Param("days") int days);

    @Select("""
            select h.user_id, u.phone, u.nickname,
                   count(distinct h.drama_id) watched_dramas,
                   count(distinct h.episode_id) latest_episodes,
                   ifnull(sum(h.progress_seconds), 0) progress_seconds,
                   max(h.updated_at) last_watch_at
            from user_watch_history h
            join app_user u on u.id = h.user_id
            group by h.user_id, u.phone, u.nickname
            order by progress_seconds desc, watched_dramas desc
            limit #{limit}
            """)
    List<Map<String, Object>> viewerRanking(@Param("limit") int limit);

    @Select("""
            select d.id drama_id, d.title drama_title,
                   e.episode_no, e.title episode_title,
                   count(distinct h.user_id) reached_users
            from drama d
            join drama_episode e on e.drama_id = d.id
            left join user_watch_history h on h.drama_id = d.id and h.episode_id = e.id
            where d.status >= 0 and e.status >= 0
              and (#{dramaId} is null or d.id = #{dramaId})
            group by d.id, d.title, e.episode_no, e.title
            order by d.id asc, e.episode_no asc
            """)
    List<Map<String, Object>> episodeReach(@Param("dramaId") Long dramaId);
}
