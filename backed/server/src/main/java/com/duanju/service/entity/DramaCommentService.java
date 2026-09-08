package com.duanju.service.entity;

import com.baomidou.mybatisplus.extension.service.IService;
import com.duanju.entity.DramaComment;

import java.util.List;
import java.util.Map;

public interface DramaCommentService extends IService<DramaComment> {

    /** 根评论（parent_id is null） */
    List<Map<String, Object>> roots(Long dramaId, int offset, int limit);

    long countRoots(Long dramaId);

    /** 某根评论下的回复 */
    List<Map<String, Object>> replies(Long rootId, int offset, int limit);

    long countReplies(Long rootId);
}
