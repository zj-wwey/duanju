package com.duanju.mapper.entity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duanju.entity.DramaCategoryFilter;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface DramaCategoryFilterMapper extends BaseMapper<DramaCategoryFilter> {

    @Select("""
            select group_key, group_label_key, group_sort_order,
                   option_key, option_label_key, option_sort_order
            from drama_category_filter
            where status = 1
            order by group_sort_order asc, option_sort_order asc, id asc
            """)
    List<Map<String, Object>> categoryFilters();

    @Select("""
            select id, group_key, group_label_key, group_sort_order,
                   option_key, option_label_key, option_sort_order, status
            from drama_category_filter
            where status >= 0
            order by group_sort_order asc, option_sort_order asc, id asc
            """)
    List<Map<String, Object>> adminCategoryFilters();

    @Insert("""
            insert into drama_category_filter(group_key, group_label_key, group_sort_order,
              option_key, option_label_key, option_sort_order, status)
            values(#{groupKey}, #{groupLabelKey}, #{groupSortOrder},
              #{optionKey}, #{optionLabelKey}, #{optionSortOrder}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertCategoryFilter(Map<String, Object> filter);

    @Update("""
            update drama_category_filter
            set group_key=#{groupKey}, group_label_key=#{groupLabelKey}, group_sort_order=#{groupSortOrder},
                option_key=#{optionKey}, option_label_key=#{optionLabelKey}, option_sort_order=#{optionSortOrder},
                status=#{status}
            where id=#{id}
            """)
    int updateCategoryFilter(Map<String, Object> filter);
}
