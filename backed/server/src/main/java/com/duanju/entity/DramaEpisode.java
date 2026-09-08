package com.duanju.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("drama_episode")
public class DramaEpisode {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("drama_id")
    private Long dramaId;

    @TableField("episode_no")
    private Integer episodeNo;

    private String title;

    private String description;

    @TableField("cover_url")
    private String coverUrl;

    @TableField("cover_object_key")
    private String coverObjectKey;

    @TableField("video_url")
    private String videoUrl;

    @TableField("price_points")
    private Integer pricePoints;

    @TableField("duration_seconds")
    private Integer durationSeconds;

    @TableField("is_free")
    private Integer isFree;

    @TableField("access_type")
    private String accessType;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("storage_provider")
    private String storageProvider;

    @TableField("cloudflare_uid")
    private String cloudflareUid;

    @TableField("hls_url")
    private String hlsUrl;

    @TableField("transcode_status")
    private Integer transcodeStatus;

    @TableField("video_duration")
    private Integer videoDurationSeconds;

    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
