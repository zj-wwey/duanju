package com.duanju.service;

import com.duanju.entity.AdminRole;
import com.duanju.entity.AdminUser;
import com.duanju.entity.AdminUserRole;
import com.duanju.entity.OperationLog;
import com.duanju.service.entity.AdminRoleService;
import com.duanju.service.entity.AdminUserRoleService;
import com.duanju.service.entity.AdminUserService;
import com.duanju.service.entity.OperationLogService;
import com.duanju.util.MapUtil;
import com.duanju.util.PasswordUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class AdminService {

    private final AdminUserService adminUserService;
    private final AdminRoleService adminRoleService;
    private final AdminUserRoleService adminUserRoleService;
    private final OperationLogService operationLogService;
    private final UserService userService;

    public AdminService(AdminUserService adminUserService, AdminRoleService adminRoleService,
                        AdminUserRoleService adminUserRoleService, OperationLogService operationLogService,
                        UserService userService) {
        this.adminUserService = adminUserService;
        this.adminRoleService = adminRoleService;
        this.adminUserRoleService = adminUserRoleService;
        this.operationLogService = operationLogService;
        this.userService = userService;
    }

    // --- Admin accounts ---

    public List<Map<String, Object>> getAdmins() {
        List<com.duanju.entity.AdminUser> admins = adminUserService.lambdaQuery()
                .select(com.duanju.entity.AdminUser::getId, com.duanju.entity.AdminUser::getUsername,
                        com.duanju.entity.AdminUser::getNickname, com.duanju.entity.AdminUser::getStatus,
                        com.duanju.entity.AdminUser::getCreatedAt)
                .orderByAsc(com.duanju.entity.AdminUser::getId)
                .list();
        List<Map<String, Object>> result = MapUtil.beansToMaps(admins);
        for (Map<String, Object> admin : result) {
            admin.put("roles", adminUserRoleService.adminRoles(MapUtil.lng(admin, "id")));
        }
        return result;
    }

    @Transactional
    public Map<String, Object> createAdmin(String username, String password, String nickname) {
        PasswordUtil.validateUsername(username);
        long exists = adminUserService.lambdaQuery()
                .eq(AdminUser::getUsername, username)
                .count();
        if (exists > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }
        PasswordUtil.validate(password);
        AdminUser admin = new AdminUser();
        admin.setUsername(username);
        admin.setPasswordHash(PasswordUtil.hash(password));
        admin.setNickname(nickname != null ? nickname : username);
        admin.setStatus(1);
        adminUserService.save(admin);
        return MapUtil.beanToMap(admin);
    }

    @Transactional
    public Map<String, Object> updateAdmin(Long id, String nickname, String password) {
        AdminUser existing = adminUserService.getById(id);
        if (existing == null) {
            throw new IllegalArgumentException("管理员不存在");
        }
        if (nickname != null) {
            existing.setNickname(nickname);
        }
        if (password != null && !password.isBlank()) {
            PasswordUtil.validate(password);
            existing.setPasswordHash(PasswordUtil.hash(password));
        }
        adminUserService.updateById(existing);
        return MapUtil.beanToMap(existing);
    }

    @Transactional
    public void deleteAdmin(Long id) {
        if (id == 1) {
            throw new IllegalArgumentException("不能删除超级管理员");
        }
        adminUserRoleService.lambdaUpdate()
                .eq(AdminUserRole::getAdminId, id)
                .remove();
        adminUserService.removeById(id);
    }

    // --- Role management ---

    public List<Map<String, Object>> getRoles() {
        List<AdminRole> roles = adminRoleService.lambdaQuery()
                .ge(AdminRole::getStatus, 0)
                .orderByAsc(AdminRole::getId)
                .list();
        return MapUtil.beansToMaps(roles);
    }

    @Transactional
    public Map<String, Object> createRole(String name, String code, String permissions, Integer status) {
        AdminRole role = new AdminRole();
        role.setName(name);
        role.setCode(code);
        role.setPermissions(permissions);
        role.setStatus(status == null ? 1 : status);
        adminRoleService.save(role);
        return MapUtil.beanToMap(role);
    }

    @Transactional
    public void updateRole(Long id, String name, String code, String permissions, Integer status) {
        AdminRole role = new AdminRole();
        role.setId(id);
        role.setName(name);
        role.setCode(code);
        role.setPermissions(permissions);
        role.setStatus(status == null ? 1 : status);
        adminRoleService.updateById(role);
    }

    public void deleteRole(Long id) {
        adminRoleService.lambdaUpdate()
                .set(AdminRole::getStatus, -1)
                .eq(AdminRole::getId, id)
                .update();
    }

    @Transactional
    public void assignRoles(Long adminId, List<Long> roleIds) {
        adminUserRoleService.lambdaUpdate()
                .eq(AdminUserRole::getAdminId, adminId)
                .remove();
        for (Long roleId : roleIds) {
            AdminUserRole aur = new AdminUserRole();
            aur.setAdminId(adminId);
            aur.setRoleId(roleId);
            adminUserRoleService.save(aur);
        }
    }

    // --- Operation logs ---

    public List<Map<String, Object>> getOperationLogs(int limit) {
        return operationLogService.operationLogs(Math.max(1, Math.min(limit, 1000)));
    }

    public void logOperation(Long adminId, String method, String path, int statusCode, String ip) {
        OperationLog log = new OperationLog();
        log.setAdminId(adminId);
        log.setMethod(method);
        log.setPath(path);
        log.setStatusCode(statusCode);
        log.setIp(ip);
        operationLogService.save(log);
    }

    // --- Permission check (for AuthInterceptor) ---

    public List<String> getPermissions(Long adminId) {
        return adminUserRoleService.permissions(adminId);
    }

    // --- User management ---

    public List<Map<String, Object>> getUsers(String keyword, Integer status, int limit) {
        return userService.searchUsers(keyword, status, limit);
    }

    public Map<String, Object> getUserDetail(Long id) {
        return MapUtil.beanToMap(userService.findById(id));
    }

    public void updateUserStatus(Long id, Integer status) {
        userService.updateStatus(id, status);
    }

    public Map<String, Object> createUser(String username, String phone, String password,
                                          String nickname, String avatarUrl, Integer points) {
        return userService.createUser(username, phone, password, nickname, avatarUrl, points);
    }

    public Map<String, Object> updateUser(Long id, String username, String phone,
                                          String nickname, String avatarUrl, Integer status) {
        return userService.updateUser(id, username, phone, nickname, avatarUrl, status);
    }

    public void deleteUser(Long id) {
        userService.deleteUser(id);
    }
}
