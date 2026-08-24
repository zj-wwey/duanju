package com.duanju.service;

import com.duanju.entity.AppUser;
import com.duanju.service.entity.AppUserService;
import com.duanju.util.MapUtil;
import com.duanju.util.PasswordUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private final AppUserService appUserService;
    private final StorageService storageService;

    public UserService(AppUserService appUserService, StorageService storageService) {
        this.appUserService = appUserService;
        this.storageService = storageService;
    }

    public Map<String, Object> getProfile(Long userId) {
        return toSafeMap(appUserService.getById(userId));
    }

    public Map<String, Object> updateProfile(Long userId, String nickname, String avatarUrl) {
        String trimmedNickname = nickname == null ? "" : nickname.trim();
        if (trimmedNickname.length() < 2 || trimmedNickname.length() > 24
                || trimmedNickname.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("nickname must be 2-24 chars without whitespace");
        }
        String savedAvatarUrl = storageService.saveAvatar(avatarUrl);
        appUserService.lambdaUpdate()
                .set(AppUser::getNickname, trimmedNickname)
                .set(AppUser::getAvatarUrl, savedAvatarUrl)
                .eq(AppUser::getId, userId)
                .update();
        return toSafeMap(appUserService.getById(userId));
    }

    public void changePassword(Long userId, String oldPassword, String newPassword) {
        AppUser user = appUserService.getById(userId);
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }
        if (!PasswordUtil.verify(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("old password incorrect");
        }
        if (newPassword.equals(oldPassword)) {
            throw new IllegalArgumentException("new password must differ from old password");
        }
        PasswordUtil.validate(newPassword);
        appUserService.lambdaUpdate()
                .set(AppUser::getPasswordHash, PasswordUtil.hash(newPassword))
                .eq(AppUser::getId, userId)
                .update();
    }

    public Map<String, Object> getSettings(Long userId) {
        return settingsPayload(appUserService.getById(userId));
    }

    public Map<String, Object> updateSettings(Long userId, Boolean notice, Boolean autoNext) {
        appUserService.lambdaUpdate()
                .set(AppUser::getNoticeEnabled, boolToFlag(notice))
                .set(AppUser::getAutoNextEnabled, boolToFlag(autoNext))
                .eq(AppUser::getId, userId)
                .update();
        return settingsPayload(appUserService.getById(userId));
    }

    // --- Methods used by other Services ---

    public AppUser findById(Long userId) {
        return appUserService.getById(userId);
    }

    public AppUser findByUsername(String username) {
        return appUserService.lambdaQuery()
                .eq(AppUser::getUsername, username)
                .last("limit 1")
                .one();
    }

    public boolean changePoints(Long userId, int delta) {
        return appUserService.lambdaUpdate()
                .setSql("points = points + " + delta)
                .eq(AppUser::getId, userId)
                .apply("points + {0} >= 0", delta)
                .update();
    }

    public void updateLoginState(Long userId, String accessJti, String refreshJti, String ip) {
        appUserService.lambdaUpdate()
                .set(AppUser::getLastTokenJti, accessJti)
                .set(AppUser::getRefreshTokenJti, refreshJti)
                .set(AppUser::getLastLoginIp, ip)
                .eq(AppUser::getId, userId)
                .update();
    }

    public List<Map<String, Object>> searchUsers(String keyword, Integer status, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        List<AppUser> users = appUserService.lambdaQuery()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(AppUser::getUsername, keyword)
                        .or().like(AppUser::getPhone, keyword)
                        .or().like(AppUser::getNickname, keyword))
                .eq(status != null, AppUser::getStatus, status)
                .orderByDesc(AppUser::getId)
                .last("limit " + safeLimit)
                .list();
        return MapUtil.beansToMaps(users);
    }

    public void updateStatus(Long userId, int status) {
        appUserService.lambdaUpdate()
                .set(AppUser::getStatus, status)
                .eq(AppUser::getId, userId)
                .update();
    }

    public Map<String, Object> createUser(String username, String phone, String password,
                                          String nickname, String avatarUrl, Integer points) {
        AppUser existing = findByUsername(username);
        if (existing != null) {
            throw new IllegalArgumentException("username already exists");
        }
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPhone(phone);
        user.setPhoneVerified(0);
        user.setAuthProvider("manual");
        if (password != null && !password.isBlank()) {
            user.setPasswordHash(PasswordUtil.hash(password));
        } else {
            user.setPasswordHash(PasswordUtil.hash(generateRandomPassword()));
        }
        user.setNickname(nickname != null ? nickname : username);
        String savedAvatarUrl = storageService.saveAvatar(avatarUrl);
        user.setAvatarUrl(savedAvatarUrl);
        user.setPoints(points != null ? points : 0);
        user.setNoticeEnabled(1);
        user.setAutoNextEnabled(1);
        user.setStatus(1);
        appUserService.save(user);
        return toSafeMap(appUserService.getById(user.getId()));
    }

    public Map<String, Object> updateUser(Long userId, String username, String phone,
                                          String nickname, String avatarUrl, Integer status) {
        AppUser user = appUserService.getById(userId);
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }
        if (username != null && !username.equals(user.getUsername())) {
            AppUser existing = findByUsername(username);
            if (existing != null) {
                throw new IllegalArgumentException("username already exists");
            }
            user.setUsername(username);
        }
        if (phone != null) {
            user.setPhone(phone);
        }
        if (nickname != null) {
            String trimmedNickname = nickname.trim();
            if (trimmedNickname.length() < 2 || trimmedNickname.length() > 24) {
                throw new IllegalArgumentException("nickname must be 2-24 chars");
            }
            user.setNickname(trimmedNickname);
        }
        if (avatarUrl != null) {
            user.setAvatarUrl(storageService.saveAvatar(avatarUrl));
        }
        if (status != null) {
            user.setStatus(status);
        }
        appUserService.updateById(user);
        return toSafeMap(appUserService.getById(userId));
    }

    public void deleteUser(Long userId) {
        AppUser user = appUserService.getById(userId);
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }
        appUserService.removeById(userId);
    }

    // --- Private helpers ---

    private String generateRandomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return sb.toString() + "!";
    }

    private Map<String, Object> toSafeMap(AppUser user) {
        if (user == null) {
            return null;
        }
        user.setPasswordHash(null); // 不暴露密码哈希
        return MapUtil.beanToMap(user);
    }

    private Map<String, Object> settingsPayload(AppUser user) {
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }
        return MapUtil.map(
                "notice", flagToBool(user.getNoticeEnabled()),
                "autoNext", flagToBool(user.getAutoNextEnabled()),
                "user", toSafeMap(user)
        );
    }

    private static int boolToFlag(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    private static boolean flagToBool(Integer value) {
        return value == null || value != 0;
    }
}
