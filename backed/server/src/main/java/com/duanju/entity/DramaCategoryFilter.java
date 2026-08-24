package com.duanju.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("drama_category_filter")
public class DramaCategoryFilter {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("group_key")
    private String groupKey;

    @TableField("group_label_key")
    private String groupLabelKey;

    @TableField("group_sort_order")
    private Integer groupSortOrder;

    @TableField("option_key")
    private String optionKey;

    @TableField("option_label_key")
    private String optionLabelKey;

    @TableField("option_sort_order")
    private Integer optionSortOrder;

    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
