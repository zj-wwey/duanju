package com.duanju.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("drama")
public class Drama {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    @TableField("author_name")
    private String authorName;

    @TableField("cover_url")
    private String coverUrl;

    @TableField("cover_object_key")
    private String coverObjectKey;

    @TableField("horizontal_cover_url")
    private String horizontalCoverUrl;

    @TableField("horizontal_cover_object_key")
    private String horizontalCoverObjectKey;

    @TableField("vertical_cover_url")
    private String verticalCoverUrl;

    @TableField("vertical_cover_object_key")
    private String verticalCoverObjectKey;

    private String tags;

    @TableField("free_episode_count")
    private Integer freeEpisodeCount;

    @TableField("total_episodes")
    private Integer totalEpisodes;

    @TableField("episode_price_points")
    private Integer episodePricePoints;

    @TableField("whole_price_points")
    private Integer wholePricePoints;

    @TableField("content_type")
    private String contentType;

    private String background;

    private String theme;

    @TableField("setting_key")
    private String settingKey;

    private String audience;

    @TableField("publish_date")
    private LocalDate publishDate;

    @TableField("online_time")
    private LocalDateTime onlineTime;

    @TableField("hot_score")
    private Integer hotScore;

    @TableField("like_count")
    private Integer likeCount;

    private Integer recommended;

    private Integer status;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
