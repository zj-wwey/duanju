package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.DramaComment;
import com.duanju.mapper.entity.DramaCommentMapper;
import com.duanju.service.entity.DramaCommentService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DramaCommentServiceImpl extends ServiceImpl<DramaCommentMapper, DramaComment> implements DramaCommentService {

    @Override
    public List<Map<String, Object>> roots(Long dramaId, int offset, int limit) {
        return baseMapper.roots(dramaId, offset, limit);
    }

    @Override
    public long countRoots(Long dramaId) {
        return baseMapper.countRoots(dramaId);
    }

    @Override
    public List<Map<String, Object>> replies(Long rootId, int offset, int limit) {
        return baseMapper.replies(rootId, offset, limit);
    }

    @Override
    public long countReplies(Long rootId) {
        return baseMapper.countReplies(rootId);
    }
}
