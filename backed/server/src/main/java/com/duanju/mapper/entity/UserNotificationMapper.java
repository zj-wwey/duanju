package com.duanju.mapper.entity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duanju.entity.UserNotification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserNotificationMapper extends BaseMapper<UserNotification> {

    @Select("""
            select n.id, n.type, n.source_user_id, n.drama_id, n.comment_id, n.content, n.is_read, n.created_at,
                   u.nickname as source_nickname, u.avatar_url as source_avatar,
                   d.title as drama_title
            from user_notification n
            left join app_user u on u.id = n.source_user_id
            left join drama d on d.id = n.drama_id
            where n.user_id = #{userId}
            order by n.id desc
            limit #{offset}, #{limit}
            """)
    List<Map<String, Object>> listWithDetail(@Param("userId") Long userId,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    @Select("select count(*) from user_notification where user_id = #{userId} and is_read = 0")
    long countUnread(@Param("userId") Long userId);
}
