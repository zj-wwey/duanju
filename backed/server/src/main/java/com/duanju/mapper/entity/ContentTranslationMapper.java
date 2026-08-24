package com.duanju.mapper.entity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duanju.entity.ContentTranslation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ContentTranslationMapper extends BaseMapper<ContentTranslation> {

    @Select("""
            SELECT id, entity_type, entity_id, field_name, locale, content
            FROM content_translation
            WHERE entity_type = #{entityType}
              AND entity_id = #{entityId}
            """)
    List<ContentTranslation> selectByEntity(@Param("entityType") String entityType,
                                            @Param("entityId") String entityId);

    @Select("""
            SELECT id, entity_type, entity_id, field_name, locale, content
            FROM content_translation
            WHERE entity_type = #{entityType}
              AND entity_id = #{entityId}
              AND locale = #{locale}
            """)
    List<ContentTranslation> selectByEntityAndLocale(@Param("entityType") String entityType,
                                                     @Param("entityId") String entityId,
                                                     @Param("locale") String locale);
}
