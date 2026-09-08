package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.dto.user.CommentCreateRequest;
import com.duanju.dto.user.CommentReplyRequest;
import com.duanju.dto.user.HistoryRequest;
import com.duanju.dto.user.PasswordChangeRequest;
import com.duanju.dto.user.ProfileUpdateRequest;
import com.duanju.dto.user.SettingsUpdateRequest;
import com.duanju.dto.user.UnlockDramaRequest;
import com.duanju.dto.user.UnlockRequest;
import com.duanju.entity.AppUser;
import com.duanju.entity.UserInvite;
import com.duanju.security.PrincipalHolder;
import com.duanju.service.AutoRenewalService;
import com.duanju.service.DramaService;
import com.duanju.service.InviteService;
import com.duanju.service.MembershipService;
import com.duanju.service.NotificationService;
import com.duanju.service.PointService;
import com.duanju.service.UnlockService;
import com.duanju.service.UserActionService;
import com.duanju.service.UserService;
import com.duanju.service.VipService;
import com.duanju.service.entity.AppUserService;
import com.duanju.service.entity.UserInviteService;
import com.duanju.util.MapUtil;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserService userService;
    private final UserActionService userActionService;
    private final UnlockService unlockService;
    private final PointService pointService;
    private final VipService vipService;
    private final MembershipService membershipService;
    private final AutoRenewalService autoRenewalService;
    private final UserInviteService userInviteService;
    private final DramaService dramaService;
    private final AppUserService appUserService;
    private final InviteService inviteService;
    private final NotificationService notificationService;

    public UserController(UserService userService, UserActionService userActionService,
                          UnlockService unlockService, PointService pointService,
                          VipService vipService, MembershipService membershipService,
                          AutoRenewalService autoRenewalService,
                          UserInviteService userInviteService,
                          DramaService dramaService,
                          AppUserService appUserService,
                          InviteService inviteService,
                          NotificationService notificationService) {
        this.userService = userService;
        this.userActionService = userActionService;
        this.unlockService = unlockService;
        this.pointService = pointService;
        this.vipService = vipService;
        this.membershipService = membershipService;
        this.autoRenewalService = autoRenewalService;
        this.userInviteService = userInviteService;
        this.dramaService = dramaService;
        this.appUserService = appUserService;
        this.inviteService = inviteService;
        this.notificationService = notificationService;
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me() {
        Long userId = PrincipalHolder.userId();
        Map<String, Object> profile = userService.getProfile(userId);
        // 附加会员信息
        try {
            String level = membershipService.getCurrentLevel(userId);
            Map<String, Object> badge = membershipService.getBadgeInfo(level);
            Map<String, Object> membership = new HashMap<>(profile);
            membership.put("membership_level", level);
            membership.put("badge", badge);
            return R.ok(membership);
        } catch (Exception e) {
            return R.ok(profile);
        }
    }

    @PutMapping("/profile")
    public R<Map<String, Object>> updateProfile(@Validated @RequestBody ProfileUpdateRequest request) {
        return R.ok(userService.updateProfile(PrincipalHolder.userId(), request.nickname(), request.avatarUrl()));
    }

    /** 上传用户头像 (multipart),返回 {url, objectKey} */
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = PrincipalHolder.userId();
        return R.ok(userService.uploadAvatar(userId, file));
    }

    @PutMapping("/password")
    public R<Void> changePassword(@Validated @RequestBody PasswordChangeRequest request) {
        userService.changePassword(PrincipalHolder.userId(), request.oldPassword(), request.newPassword());
        return R.ok();
    }

    @GetMapping("/settings")
    public R<Map<String, Object>> settings() {
        return R.ok(userService.getSettings(PrincipalHolder.userId()));
    }

    @PutMapping("/settings")
    public R<Map<String, Object>> updateSettings(@Validated @RequestBody SettingsUpdateRequest request) {
        return R.ok(userService.updateSettings(PrincipalHolder.userId(), request.notice(), request.autoNext()));
    }

    @PostMapping("/favorites/{dramaId}/toggle")
    public R<Map<String, Object>> toggleFavorite(@PathVariable Long dramaId) {
        return R.ok(userActionService.toggleFavorite(PrincipalHolder.userId(), dramaId));
    }

    @GetMapping("/favorites")
    public R<List<Map<String, Object>>> favorites() {
        return R.ok(userActionService.getFavorites(PrincipalHolder.userId()));
    }

    /** 点赞/取消点赞，返回 {liked, likeCount} */
    @PostMapping("/likes/{dramaId}/toggle")
    public R<Map<String, Object>> toggleLike(@PathVariable Long dramaId) {
        return R.ok(userActionService.toggleLike(PrincipalHolder.userId(), dramaId));
    }

    /** 发表评论（路径避开 /api/dramas 白名单前缀，确保登录鉴权生效） */
    @PostMapping("/comments")
    public R<Map<String, Object>> addComment(@Validated @RequestBody CommentCreateRequest request) {
        return R.ok(userActionService.addComment(PrincipalHolder.userId(), request.dramaId(),
                request.episodeId(), request.content()));
    }

    /** 回复评论 */
    @PostMapping("/comments/reply")
    public R<Map<String, Object>> reply(@Validated @RequestBody CommentReplyRequest request) {
        return R.ok(userActionService.replyComment(PrincipalHolder.userId(), request.dramaId(),
                request.episodeId(), request.parentId(), request.replyToUserId(), request.content()));
    }

    /** 删除自己的评论 */
    @DeleteMapping("/comments/{commentId}")
    public R<Void> deleteComment(@PathVariable Long commentId) {
        userActionService.deleteComment(PrincipalHolder.userId(), commentId);
        return R.ok();
    }

    /** 当前用户通知列表 */
    @GetMapping("/notifications")
    public R<Map<String, Object>> notifications(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(notificationService.listForUser(PrincipalHolder.userId(), page, pageSize));
    }

    /** 未读数量 */
    @GetMapping("/notifications/unread-count")
    public R<Map<String, Object>> unreadCount() {
        return R.ok(Map.of("unread", notificationService.countUnread(PrincipalHolder.userId())));
    }

    /** 全部标记已读 */
    @PostMapping("/notifications/read-all")
    public R<Void> markAllRead() {
        notificationService.markAllRead(PrincipalHolder.userId());
        return R.ok();
    }

    /** 单条标记已读 */
    @PostMapping("/notifications/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(PrincipalHolder.userId(), id);
        return R.ok();
    }

    @PostMapping("/history")
    public R<Void> history(@Validated @RequestBody HistoryRequest request) {
        userActionService.saveHistory(PrincipalHolder.userId(), request.dramaId(),
                request.episodeId(), request.progressSeconds());
        return R.ok();
    }

    @GetMapping("/history")
    public R<List<Map<String, Object>>> histories() {
        return R.ok(userActionService.getHistories(PrincipalHolder.userId()));
    }

    @DeleteMapping("/history/{dramaId}")
    public R<Void> deleteHistory(@PathVariable Long dramaId) {
        userActionService.deleteHistory(PrincipalHolder.userId(), dramaId);
        return R.ok();
    }

    @DeleteMapping("/history")
    public R<Map<String, Object>> clearHistories() {
        return R.ok(userActionService.clearHistories(PrincipalHolder.userId()));
    }

    @PostMapping("/checkin")
    public R<Map<String, Object>> checkin() {
        return R.ok(pointService.checkin(PrincipalHolder.userId()));
    }

    @GetMapping("/points")
    public R<Map<String, Object>> points() {
        return R.ok(pointService.getPoints(PrincipalHolder.userId()));
    }

    @PostMapping("/unlock")
    public R<Void> unlock(@Validated @RequestBody UnlockRequest request) {
        unlockService.unlockEpisode(PrincipalHolder.userId(), request.episodeId());
        return R.ok();
    }

    @PostMapping("/unlock-drama")
    public R<Void> unlockDrama(@Validated @RequestBody UnlockDramaRequest request) {
        unlockService.unlockDrama(PrincipalHolder.userId(), request.dramaId());
        return R.ok();
    }

    @GetMapping("/vip/status")
    public R<Map<String, Object>> vipStatus() {
        return R.ok(vipService.getMyVipStatus());
    }

    @GetMapping("/vip/records")
    public R<List<Map<String, Object>>> vipRecords(@RequestParam(defaultValue = "50") int limit) {
        return R.ok(vipService.getMyVipRecords(limit));
    }

    // --- 新增积分获取接口 ---

    /** 看完一集奖励 */
    @PostMapping("/reward/episode/{episodeId}")
    public R<Map<String, Object>> rewardEpisode(@PathVariable Long episodeId) {
        return R.ok(pointService.rewardEpisodeWatch(PrincipalHolder.userId(), episodeId));
    }

    /** 分享奖励 */
    @PostMapping("/reward/share/{dramaId}")
    public R<Map<String, Object>> rewardShare(@PathVariable Long dramaId) {
        return R.ok(pointService.rewardShare(PrincipalHolder.userId(), dramaId));
    }

    /** 观看时长奖励 */
    @PostMapping("/reward/duration")
    public R<Map<String, Object>> rewardDuration(@RequestBody Map<String, Object> body) {
        int minutes = body.get("minutes") instanceof Integer ? (Integer) body.get("minutes") : 0;
        return R.ok(pointService.rewardWatchDuration(PrincipalHolder.userId(), minutes));
    }

    /** 评论奖励 */
    @PostMapping("/reward/comment/{commentId}")
    public R<Map<String, Object>> rewardComment(@PathVariable Long commentId,
                                                 @RequestBody Map<String, Object> body) {
        int quality = body.get("quality") instanceof Integer ? (Integer) body.get("quality") : 5;
        return R.ok(pointService.rewardComment(PrincipalHolder.userId(), commentId, quality));
    }

    // --- 解锁预览 ---

    /** 单集解锁预览 */
    @GetMapping("/unlock/preview/episode/{episodeId}")
    public R<Map<String, Object>> previewUnlockEpisode(@PathVariable Long episodeId) {
        return R.ok(unlockService.previewUnlockEpisode(PrincipalHolder.userId(), episodeId));
    }

    /** 整剧解锁预览 */
    @GetMapping("/unlock/preview/drama/{dramaId}")
    public R<Map<String, Object>> previewUnlockDrama(@PathVariable Long dramaId) {
        return R.ok(unlockService.previewUnlockDrama(PrincipalHolder.userId(), dramaId));
    }

    // --- 自动续费 ---

    /** 开通自动续费 */
    @PostMapping("/auto-renewal/subscribe")
    public R<Map<String, Object>> subscribeAutoRenewal(@RequestBody Map<String, Object> body) {
        Object rawProductId = body.get("productId");
        if (rawProductId == null) {
            throw new IllegalArgumentException("productId is required");
        }
        long productId = rawProductId instanceof Integer ? ((Integer) rawProductId).longValue()
                : (Long) rawProductId;
        String payChannel = (String) body.get("payChannel");
        String payMethodToken = (String) body.get("payMethodToken");
        return R.ok(autoRenewalService.subscribe(PrincipalHolder.userId(), productId, payChannel, payMethodToken));
    }

    /** 取消自动续费 */
    @PostMapping("/auto-renewal/cancel")
    public R<Void> cancelAutoRenewal() {
        autoRenewalService.cancel(PrincipalHolder.userId());
        return R.ok();
    }

    /** 自动续费状态 */
    @GetMapping("/auto-renewal/status")
    public R<Map<String, Object>> autoRenewalStatus() {
        return R.ok(autoRenewalService.getStatus(PrincipalHolder.userId()));
    }

    // --- 邀请与分享 ---

    /** 获取邀请码和邀请状态 */
    @GetMapping("/invite/code")
    public R<Map<String, Object>> inviteCode() {
        Long userId = PrincipalHolder.userId();

        long inviteCount = userInviteService.lambdaQuery()
                .eq(UserInvite::getInviterId, userId)
                .count();

        UserInvite myInvite = userInviteService.lambdaQuery()
                .eq(UserInvite::getInviteeId, userId)
                .one();

        boolean alreadyInvited = myInvite != null;

        return R.ok(MapUtil.map(
                "invite_code", String.valueOf(userId),
                "invite_count", inviteCount,
                "already_invited", alreadyInvited,
                "invite_status", alreadyInvited ? myInvite.getStatus() : null
        ));
    }

    /** 邀请记录列表 */
    @GetMapping("/invite/records")
    public R<Map<String, Object>> inviteRecords(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 20;
        if (pageSize > 100) pageSize = 100;
        Long userId = PrincipalHolder.userId();

        long total = userInviteService.lambdaQuery()
                .eq(UserInvite::getInviterId, userId)
                .count();

        List<UserInvite> invites = userInviteService.lambdaQuery()
                .eq(UserInvite::getInviterId, userId)
                .orderByDesc(UserInvite::getCreatedAt)
                .last("LIMIT " + (page - 1) * pageSize + ", " + pageSize)
                .list();

        List<Long> inviteeIds = invites.stream().map(UserInvite::getInviteeId).toList();
        Map<Long, AppUser> userMap = new HashMap<>();
        if (!inviteeIds.isEmpty()) {
            List<AppUser> users = appUserService.listByIds(inviteeIds);
            for (AppUser u : users) {
                userMap.put(u.getId(), u);
            }
        }

        List<Map<String, Object>> records = new ArrayList<>();
        for (UserInvite invite : invites) {
            AppUser invitee = userMap.get(invite.getInviteeId());
            records.add(MapUtil.map(
                    "id", invite.getId(),
                    "invitee_id", invite.getInviteeId(),
                    "invitee_nickname", invitee != null ? invitee.getNickname() : "未知用户",
                    "invitee_avatar", invitee != null ? invitee.getAvatarUrl() : null,
                    "status", invite.getStatus(),
                    "rewarded_at", invite.getRewardedAt(),
                    "created_at", invite.getCreatedAt()
            ));
        }

        return R.ok(MapUtil.map(
                "total", total,
                "page", page,
                "page_size", pageSize,
                "records", records
        ));
    }

    /** 分享链接访问：处理邀请奖励并返回短剧详情 */
    @GetMapping("/shared/{dramaId}/{sharerUserId}")
    public R<Map<String, Object>> sharedLink(@PathVariable Long dramaId,
                                              @PathVariable Long sharerUserId) {
        Long userId = PrincipalHolder.userId();

        Map<String, Object> drama = dramaService.getDramaDetail(dramaId);

        if (userId.equals(sharerUserId)) {
            return R.ok(MapUtil.map("drama", drama, "invited", false, "reason", "self"));
        }

        if (!inviteService.isSharerValid(sharerUserId)) {
            return R.ok(MapUtil.map("drama", drama, "invited", false, "reason", "sharer_invalid"));
        }

        UserInvite invite = inviteService.processInvite(sharerUserId, userId);

        return R.ok(MapUtil.map(
                "drama", drama,
                "invited", true,
                "status", invite.getStatus(),
                "rewarded", "REWARDED".equals(invite.getStatus())
        ));
    }
}
