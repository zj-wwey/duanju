package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.util.MapUtil;
import com.duanju.entity.AdminUser;
import com.duanju.security.PrincipalHolder;
import com.duanju.service.StorageService;
import com.duanju.service.entity.AdminUserService;
import com.duanju.util.PasswordUtil;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/profile")
public class AdminProfileController {

    private final AdminUserService adminUserService;
    private final StorageService storageService;

    public AdminProfileController(AdminUserService adminUserService, StorageService storageService) {
        this.adminUserService = adminUserService;
        this.storageService = storageService;
    }

    @GetMapping
    public R<Map<String, Object>> getProfile() {
        Long adminId = PrincipalHolder.adminId();
        if (adminId == null) {
            return R.fail("未登录");
        }
        AdminUser admin = adminUserService.getById(adminId);
        if (admin == null) {
            return R.fail("管理员不存在");
        }
        admin.setPasswordHash(null);
        Map<String, Object> result = MapUtil.beanToMap(admin);
        return R.ok(result);
    }

    @PutMapping
    public R<Map<String, Object>> updateProfile(@RequestBody Map<String, String> body) {
        Long adminId = PrincipalHolder.adminId();
        if (adminId == null) {
            return R.fail("未登录");
        }
        AdminUser admin = adminUserService.getById(adminId);
        if (admin == null) {
            return R.fail("管理员不存在");
        }

        String nickname = body.get("nickname");
        if (nickname != null) {
            admin.setNickname(nickname);
        }

        String avatar = body.get("avatar");
        if (avatar != null) {
            admin.setAvatar(storageService.saveAvatar(avatar));
        }

        adminUserService.updateById(admin);
        admin.setPasswordHash(null);
        return R.ok(MapUtil.beanToMap(admin));
    }

    @PutMapping("/password")
    public R<Void> changePassword(@RequestBody Map<String, String> body) {
        Long adminId = PrincipalHolder.adminId();
        if (adminId == null) {
            return R.fail("未登录");
        }
        AdminUser admin = adminUserService.getById(adminId);
        if (admin == null) {
            return R.fail("管理员不存在");
        }

        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        String confirmPassword = body.get("confirmPassword");

        if (oldPassword == null || newPassword == null) {
            return R.fail("请填写完整信息");
        }

        if (!PasswordUtil.verify(oldPassword, admin.getPasswordHash())) {
            return R.fail("原密码不正确");
        }

        if (confirmPassword != null && !newPassword.equals(confirmPassword)) {
            return R.fail("两次输入的新密码不一致");
        }

        if (!PasswordUtil.isValid(newPassword)) {
            return R.fail("密码需8-64位，包含大小写字母、数字和特殊符号，且不能包含空白字符");
        }

        admin.setPasswordHash(PasswordUtil.hash(newPassword));
        adminUserService.updateById(admin);
        return R.ok();
    }
}
