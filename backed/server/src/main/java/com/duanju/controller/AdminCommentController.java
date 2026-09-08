package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.entity.AppUser;
import com.duanju.entity.DramaComment;
import com.duanju.mapper.entity.AppUserMapper;
import com.duanju.mapper.entity.DramaCommentMapper;
import com.duanju.service.entity.DramaCommentService;
import com.duanju.util.MapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员评论控评：列出待审核/所有评论、强制删除、恢复
 * 路径 /api/admin/comments/** —— AdminAuthInterceptor 会校验管理员权限
 */
@RestController
@RequestMapping("/api/admin/comments")
public class AdminCommentController {

    private static final Logger log = LoggerFactory.getLogger(AdminCommentController.class);

    private final DramaCommentService commentService;
    private final DramaCommentMapper commentMapper;
    private final AppUserMapper appUserMapper;

    public AdminCommentController(DramaCommentService commentService,
                                  DramaCommentMapper commentMapper,
                                  AppUserMapper appUserMapper) {
        this.commentService = commentService;
        this.commentMapper = commentMapper;
        this.appUserMapper = appUserMapper;
    }

    /**
     * 管理员查看所有评论（不限于某剧）
     *
     * @param status  -1=用户删除, -2=管理员删除, 1=正常, null=全部
     */
    @GetMapping
    public R<Map<String, Object>> list(@RequestParam(required = false) Integer status,
                                       @RequestParam(required = false) Long dramaId,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 50;
        if (pageSize > 200) pageSize = 200;

        long total;
        if (dramaId != null) {
            total = commentMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DramaComment>()
                            .eq("drama_id", dramaId)
                            .eq(status != null, "status", status));
        } else {
            total = commentMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DramaComment>()
                            .eq(status != null, "status", status));
        }

        List<DramaComment> list = commentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DramaComment>()
                        .eq(dramaId != null, "drama_id", dramaId)
                        .eq(status != null, "status", status)
                        .orderByDesc("id")
                        .last("limit " + ((page - 1) * pageSize) + ", " + pageSize));

        List<Map<String, Object>> records = MapUtil.beansToMaps(list);
        if (!records.isEmpty()) {
            List<Long> userIds = records.stream()
                    .map(r -> MapUtil.lng(r, "user_id"))
                    .filter(id -> id != null)
                    .distinct()
                    .collect(Collectors.toList());
            if (!userIds.isEmpty()) {
                List<AppUser> users = appUserMapper.selectBatchIds(userIds);
                Map<Long, AppUser> userMap = users.stream()
                        .collect(Collectors.toMap(AppUser::getId, u -> u));
                for (Map<String, Object> record : records) {
                    Long userId = MapUtil.lng(record, "user_id");
                    AppUser user = userMap.get(userId);
                    if (user != null) {
                        record.put("nickname", user.getNickname());
                        record.put("avatar_url", user.getAvatarUrl());
                    }
                }
            }
        }

        return R.ok(MapUtil.map("total", total, "page", page, "page_size", pageSize, "records", records));
    }

    /** 管理员强制删除评论（status=-2），比用户删除优先级高 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        boolean updated = commentService.lambdaUpdate()
                .eq(DramaComment::getId, id)
                .set(DramaComment::getStatus, -2)
                .update();
        if (!updated) {
            return R.fail("comment not found");
        }
        log.info("Admin deleted comment id={}", id);
        return R.ok();
    }

    /** 管理员恢复被删评论（status→1） */
    @PostMapping("/{id}/restore")
    public R<Void> restore(@PathVariable Long id) {
        boolean updated = commentService.lambdaUpdate()
                .eq(DramaComment::getId, id)
                .ne(DramaComment::getStatus, 1)
                .set(DramaComment::getStatus, 1)
                .update();
        if (!updated) {
            return R.fail("comment not found or already active");
        }
        return R.ok();
    }

    /** 批量删除 */
    @PostMapping("/batch-delete")
    public R<Map<String, Object>> batchDelete(@RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.get("ids");
        if (ids == null || ids.isEmpty()) {
            return R.fail("ids required");
        }
        long before = commentService.count(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DramaComment>()
                        .in("id", ids));
        commentService.update(
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<DramaComment>()
                        .in("id", ids)
                        .set("status", -2));
        log.info("Admin batch deleted {} comments", before);
        return R.ok(MapUtil.map("deleted", before));
    }
}
