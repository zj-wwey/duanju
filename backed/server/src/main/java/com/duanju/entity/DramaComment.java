package com.duanju.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("drama_comment")
public class DramaComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("drama_id")
    private Long dramaId;

    @TableField("episode_id")
    private Long episodeId;

    private String content;

    @TableField("parent_id")
    private Long parentId;

    @TableField("reply_to_user_id")
    private Long replyToUserId;

    @TableField("root_id")
    private Long rootId;

    @TableField("reply_count")
    private Integer replyCount;

    /** 1=正常, -1=用户删除, -2=管理员删除 */
    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
