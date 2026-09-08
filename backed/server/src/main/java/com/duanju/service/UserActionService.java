package com.duanju.service;

import com.duanju.entity.AppUser;
import com.duanju.entity.Drama;
import com.duanju.entity.DramaComment;
import com.duanju.entity.EpisodePlayEvent;
import com.duanju.entity.UserFavorite;
import com.duanju.entity.UserLike;
import com.duanju.entity.UserNotification;
import com.duanju.mapper.entity.UserNotificationMapper;
import com.duanju.service.entity.AppUserService;
import com.duanju.service.entity.DramaCommentService;
import com.duanju.service.entity.DramaEntityService;
import com.duanju.service.entity.EpisodePlayEventService;
import com.duanju.service.entity.UserFavoriteService;
import com.duanju.service.entity.UserLikeService;
import com.duanju.service.entity.UserWatchHistoryService;
import com.duanju.util.MapUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class UserActionService {

    private final DramaEntityService dramaEntityService;
    private final UserFavoriteService userFavoriteService;
    private final UserLikeService userLikeService;
    private final DramaCommentService dramaCommentService;
    private final AppUserService appUserService;
    private final UserWatchHistoryService userWatchHistoryService;
    private final EpisodePlayEventService episodePlayEventService;
    private final UnlockService unlockService;
    private final UserNotificationMapper notificationMapper;

    public UserActionService(DramaEntityService dramaEntityService,
                             UserFavoriteService userFavoriteService,
                             UserLikeService userLikeService,
                             DramaCommentService dramaCommentService,
                             AppUserService appUserService,
                             UserWatchHistoryService userWatchHistoryService,
                             EpisodePlayEventService episodePlayEventService,
                             UnlockService unlockService,
                             UserNotificationMapper notificationMapper) {
        this.dramaEntityService = dramaEntityService;
        this.userFavoriteService = userFavoriteService;
        this.userLikeService = userLikeService;
        this.dramaCommentService = dramaCommentService;
        this.appUserService = appUserService;
        this.userWatchHistoryService = userWatchHistoryService;
        this.episodePlayEventService = episodePlayEventService;
        this.unlockService = unlockService;
        this.notificationMapper = notificationMapper;
    }

    public Map<String, Object> toggleFavorite(Long userId, Long dramaId) {
        Drama drama = dramaEntityService.getById(dramaId);
        if (drama == null || !Integer.valueOf(1).equals(drama.getStatus())) {
            throw new IllegalArgumentException("drama not found");
        }
        boolean exists = userFavoriteService.lambdaQuery()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getDramaId, dramaId)
                .exists();
        if (exists) {
            userFavoriteService.lambdaUpdate()
                    .eq(UserFavorite::getUserId, userId)
                    .eq(UserFavorite::getDramaId, dramaId)
                    .remove();
            return MapUtil.map("favorite", false);
        }
        userFavoriteService.insertIgnore(userId, dramaId);
        return MapUtil.map("favorite", true);
    }

    /** 点赞/取消点赞：user_like 表去重，drama.like_count 冗余计数同步增减 */
    public Map<String, Object> toggleLike(Long userId, Long dramaId) {
        Drama drama = dramaEntityService.getById(dramaId);
        if (drama == null || !Integer.valueOf(1).equals(drama.getStatus())) {
            throw new IllegalArgumentException("drama not found");
        }
        boolean exists = userLikeService.lambdaQuery()
                .eq(UserLike::getUserId, userId)
                .eq(UserLike::getDramaId, dramaId)
                .exists();
        if (exists) {
            userLikeService.lambdaUpdate()
                    .eq(UserLike::getUserId, userId)
                    .eq(UserLike::getDramaId, dramaId)
                    .remove();
            dramaEntityService.lambdaUpdate()
                    .eq(Drama::getId, dramaId)
                    .setSql("like_count = greatest(like_count - 1, 0)")
                    .update();
        } else {
            userLikeService.insertIgnore(userId, dramaId);
            dramaEntityService.lambdaUpdate()
                    .eq(Drama::getId, dramaId)
                    .setSql("like_count = like_count + 1")
                    .update();
        }
        Drama latest = dramaEntityService.getById(dramaId);
        int likeCount = latest == null || latest.getLikeCount() == null ? 0 : latest.getLikeCount();
        return MapUtil.map("liked", !exists, "likeCount", likeCount);
    }

    /** 发表根评论 */
    @Transactional
    public Map<String, Object> addComment(Long userId, Long dramaId, Long episodeId, String content) {
        return doCreateComment(userId, dramaId, episodeId, content, null, null);
    }

    /** 回复某评论 */
    @Transactional
    public Map<String, Object> replyComment(Long userId, Long dramaId, Long episodeId,
                                             Long parentId, Long replyToUserId, String content) {
        if (parentId == null) {
            throw new IllegalArgumentException("parentId is required for reply");
        }
        DramaComment parent = dramaCommentService.getById(parentId);
        if (parent == null || !Integer.valueOf(1).equals(parent.getStatus())) {
            throw new IllegalArgumentException("parent comment not found");
        }
        // 不允许回复自己（防刷屏），但允许回复别人
        if (parent.getUserId().equals(userId)) {
            throw new IllegalArgumentException("cannot reply to your own comment chain");
        }
        Long rootId = parent.getRootId() != null ? parent.getRootId() : parent.getId();
        Map<String, Object> result = doCreateComment(userId, dramaId, episodeId, content, parentId, replyToUserId);

        // 父评论 reply_count +1
        dramaCommentService.lambdaUpdate()
                .eq(DramaComment::getId, parentId)
                .setSql("reply_count = reply_count + 1")
                .update();

        // 根评论 reply_count +1（冗余）
        if (!rootId.equals(parentId)) {
            dramaCommentService.lambdaUpdate()
                    .eq(DramaComment::getId, rootId)
                    .setSql("reply_count = reply_count + 1")
                    .update();
        }
        return result;
    }

    private Map<String, Object> doCreateComment(Long userId, Long dramaId, Long episodeId,
                                                String content, Long parentId, Long replyToUserId) {
        Drama drama = dramaEntityService.getById(dramaId);
        if (drama == null || !Integer.valueOf(1).equals(drama.getStatus())) {
            throw new IllegalArgumentException("drama not found");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }
        String text = content.trim();
        if (text.length() > 500) {
            throw new IllegalArgumentException("content too long");
        }
        DramaComment comment = new DramaComment();
        comment.setUserId(userId);
        comment.setDramaId(dramaId);
        comment.setEpisodeId(episodeId);
        comment.setContent(text);
        comment.setParentId(parentId);
        comment.setReplyToUserId(replyToUserId);
        comment.setRootId(parentId != null ? findRootId(parentId) : null);
        comment.setReplyCount(0);
        comment.setStatus(1);
        dramaCommentService.save(comment);

        // 通知
        sendCommentNotifications(comment, userId, parentId, replyToUserId);

        AppUser user = appUserService.getById(userId);
        return MapUtil.map(
                "id", comment.getId(),
                "user_id", userId,
                "drama_id", dramaId,
                "content", text,
                "parent_id", parentId,
                "reply_to_user_id", replyToUserId,
                "created_at", comment.getCreatedAt(),
                "nickname", user != null ? user.getNickname() : null,
                "avatar_url", user != null ? user.getAvatarUrl() : null
        );
    }

    /** 向上递归找根评论ID */
    private Long findRootId(Long commentId) {
        DramaComment c = dramaCommentService.getById(commentId);
        if (c == null) return commentId;
        if (c.getRootId() != null) return c.getRootId();
        if (c.getParentId() == null) return c.getId();
        return findRootId(c.getParentId());
    }

    /** 评论/回复通知：回复通知被回复者（根评论暂无剧作者用户ID，跳过） */
    private void sendCommentNotifications(DramaComment comment, Long senderId,
                                          Long parentId, Long replyToUserId) {
        String snippet = truncate(comment.getContent(), 50);

        if (parentId == null) {
            // 根评论 —— 暂不发通知（剧作者可能不是 App 用户）
        } else {
            // 回复 → 通知被回复者
            if (replyToUserId != null && !replyToUserId.equals(senderId)) {
                saveNotification(replyToUserId, "reply", senderId,
                        comment.getDramaId(), comment.getId(),
                        "回复了你: " + snippet);
            }
            // 如果回复的不是根评论，也通知根评论作者
            if (comment.getRootId() != null && !comment.getRootId().equals(parentId)) {
                DramaComment root = dramaCommentService.getById(comment.getRootId());
                if (root != null && !root.getUserId().equals(senderId)
                        && (replyToUserId == null || !root.getUserId().equals(replyToUserId))) {
                    saveNotification(root.getUserId(), "reply", senderId,
                            comment.getDramaId(), comment.getId(),
                            "回复了你的评论: " + snippet);
                }
            }
        }
    }

    private void saveNotification(Long userId, String type, Long sourceUserId,
                                  Long dramaId, Long commentId, String content) {
        UserNotification n = new UserNotification();
        n.setUserId(userId);
        n.setType(type);
        n.setSourceUserId(sourceUserId);
        n.setDramaId(dramaId);
        n.setCommentId(commentId);
        n.setContent(content);
        n.setIsRead(false);
        notificationMapper.insert(n);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    /** 评论列表：根评论分页 + 每条根评论返回最新 3 条回复（内嵌 children） */
    public Map<String, Object> getComments(Long dramaId, int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 20;
        if (pageSize > 100) pageSize = 100;
        long total = dramaCommentService.countRoots(dramaId);
        List<Map<String, Object>> roots = dramaCommentService.roots(dramaId, (page - 1) * pageSize, pageSize);
        // 给每条根评论挂 children
        for (Map<String, Object> r : roots) {
            Long rootId = MapUtil.lng(r, "id");
            List<Map<String, Object>> children = dramaCommentService.replies(rootId, 0, 3);
            r.put("children", children);
            r.put("replies_total", dramaCommentService.countReplies(rootId));
        }
        return MapUtil.map(
                "total", total,
                "page", page,
                "page_size", pageSize,
                "records", roots
        );
    }

    /** 某根评论下的回复分页（"查看更多回复"） */
    public Map<String, Object> getReplies(Long rootId, int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 20;
        long total = dramaCommentService.countReplies(rootId);
        List<Map<String, Object>> records = dramaCommentService.replies(rootId, (page - 1) * pageSize, pageSize);
        return MapUtil.map("total", total, "page", page, "page_size", pageSize, "records", records);
    }

    /** 用户删除自己评论（软删 status=-1，同时清理 reply_count 冗余） */
    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        DramaComment target = dramaCommentService.getById(commentId);
        if (target == null || !Integer.valueOf(1).equals(target.getStatus())) {
            throw new IllegalArgumentException("comment not found");
        }
        if (!target.getUserId().equals(userId)) {
            throw new IllegalArgumentException("permission denied");
        }
        boolean updated = dramaCommentService.lambdaUpdate()
                .eq(DramaComment::getId, commentId)
                .eq(DramaComment::getStatus, 1)
                .set(DramaComment::getStatus, -1)
                .update();
        if (updated && target.getParentId() != null) {
            // 回复被删，父评论 reply_count -1
            dramaCommentService.lambdaUpdate()
                    .eq(DramaComment::getId, target.getParentId())
                    .setSql("reply_count = greatest(reply_count - 1, 0)")
                    .update();
            if (target.getRootId() != null && !target.getRootId().equals(target.getParentId())) {
                dramaCommentService.lambdaUpdate()
                        .eq(DramaComment::getId, target.getRootId())
                        .setSql("reply_count = greatest(reply_count - 1, 0)")
                        .update();
            }
        }
    }

    public List<Map<String, Object>> getFavorites(Long userId) {
        return userFavoriteService.favorites(userId);
    }

    public void saveHistory(Long userId, Long dramaId, Long episodeId, Integer progressSeconds) {
        Map<String, Object> episode = unlockService.requireAccessibleEpisode(userId, dramaId, episodeId);
        int safeProgress = Math.max(0, progressSeconds == null ? 0 : progressSeconds);
        Long episodePk = MapUtil.lng(episode, "id");
        userWatchHistoryService.saveHistory(userId, dramaId, episodePk, safeProgress);
        EpisodePlayEvent event = new EpisodePlayEvent();
        event.setUserId(userId);
        event.setDramaId(dramaId);
        event.setEpisodeId(episodePk);
        event.setProgressSeconds(safeProgress);
        event.setEventType("PROGRESS");
        episodePlayEventService.save(event);
    }

    public List<Map<String, Object>> getHistories(Long userId) {
        List<Map<String, Object>> histories = userWatchHistoryService.histories(userId);
        for (Map<String, Object> h : histories) {
            Integer progress = MapUtil.integer(h, "progress_seconds");
            h.put("progressText", DramaService.formatDurationSeconds(progress));
        }
        return histories;
    }

    public void deleteHistory(Long userId, Long dramaId) {
        if (userWatchHistoryService.deleteHistory(userId, dramaId) == 0) {
            throw new IllegalArgumentException("history not found");
        }
    }

    public Map<String, Object> clearHistories(Long userId) {
        int deleted = userWatchHistoryService.clearHistories(userId);
        return MapUtil.map("deleted", deleted);
    }
}