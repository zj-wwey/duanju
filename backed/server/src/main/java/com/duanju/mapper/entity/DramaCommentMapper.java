package com.duanju.mapper.entity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duanju.entity.DramaComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DramaCommentMapper extends BaseMapper<DramaComment> {

    /** 根评论列表（parent_id 为空），按时间倒序 */
    @Select("""
            select c.id, c.user_id, c.drama_id, c.episode_id, c.content, c.created_at, c.reply_count,
                   u.nickname, u.avatar_url
            from drama_comment c join app_user u on u.id = c.user_id
            where c.drama_id = #{dramaId} and c.status = 1 and c.parent_id is null
            order by c.id desc
            limit #{offset}, #{limit}
            """)
    List<Map<String, Object>> roots(@Param("dramaId") Long dramaId,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    @Select("select count(*) from drama_comment where drama_id = #{dramaId} and status = 1 and parent_id is null")
    long countRoots(@Param("dramaId") Long dramaId);

    /** 某根评论下的回复 */
    @Select("""
            select c.id, c.user_id, c.content, c.parent_id, c.reply_to_user_id, c.created_at,
                   u.nickname, u.avatar_url,
                   ru.nickname as reply_to_nickname
            from drama_comment c
            join app_user u on u.id = c.user_id
            left join app_user ru on ru.id = c.reply_to_user_id
            where c.root_id = #{rootId} and c.status = 1
            order by c.id asc
            limit #{offset}, #{limit}
            """)
    List<Map<String, Object>> replies(@Param("rootId") Long rootId,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    @Select("select count(*) from drama_comment where root_id = #{rootId} and status = 1")
    long countReplies(@Param("rootId") Long rootId);
}
