package com.duanju.dto.user;

import jakarta.validation.constraints.Size;

public record CommentReplyRequest(
        Long dramaId,
        Long episodeId,
        /** 被回复的评论ID */
        Long parentId,
        /** 真正被 @ 的用户ID（可以为 null） */
        Long replyToUserId,
        @Size(max = 500) String content
) {
}
