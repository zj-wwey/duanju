package com.duanju.service;

import com.duanju.entity.AppUser;
import com.duanju.entity.UserInvite;
import com.duanju.service.entity.AppUserService;
import com.duanju.service.entity.UserInviteService;
import com.duanju.util.MapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class InviteService {

    private static final Logger log = LoggerFactory.getLogger(InviteService.class);
    private static final int INVITE_REWARD_POINTS = 50;

    private final UserInviteService userInviteService;
    private final AppUserService appUserService;
    private final PointService pointService;

    public InviteService(UserInviteService userInviteService,
                         AppUserService appUserService,
                         PointService pointService) {
        this.userInviteService = userInviteService;
        this.appUserService = appUserService;
        this.pointService = pointService;
    }

    /**
     * 处理邀请奖励：创建邀请记录 + 发放积分，在同一事务中保证一致性。
     *
     * @return invite 创建的邀请记录
     */
    @Transactional
    public UserInvite processInvite(Long sharerUserId, Long inviteeUserId) {
        UserInvite existing = userInviteService.lambdaQuery()
                .eq(UserInvite::getInviteeId, inviteeUserId)
                .one();
        if (existing != null) {
            return existing;
        }

        UserInvite invite = new UserInvite();
        invite.setInviterId(sharerUserId);
        invite.setInviteeId(inviteeUserId);
        invite.setStatus("PENDING");
        invite.setCreatedAt(LocalDateTime.now());
        userInviteService.save(invite);

        try {
            pointService.addPoints(sharerUserId, INVITE_REWARD_POINTS, "INVITE_REWARD",
                    String.valueOf(invite.getId()), "invite user reward");
            invite.setStatus("REWARDED");
            invite.setRewardedAt(LocalDateTime.now());
            userInviteService.updateById(invite);
        } catch (Exception e) {
            log.warn("invite reward failed sharerUserId={} inviteeUserId={} inviteId={}",
                    sharerUserId, inviteeUserId, invite.getId(), e);
            throw e;
        }

        return invite;
    }

    /** 校验分享者是否存在 */
    public boolean isSharerValid(Long sharerUserId) {
        AppUser sharer = appUserService.getById(sharerUserId);
        return sharer != null && sharer.getStatus() != null && sharer.getStatus() == 1;
    }
}