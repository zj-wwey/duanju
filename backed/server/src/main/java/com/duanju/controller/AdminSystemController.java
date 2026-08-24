package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.dto.admin.AssignRolesRequest;
import com.duanju.dto.admin.CreateAdminRequest;
import com.duanju.dto.admin.RoleCreateRequest;
import com.duanju.dto.admin.UpdateAdminRequest;
import com.duanju.security.RequiresPermission;
import com.duanju.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/system")
public class AdminSystemController {

    private final AdminService adminService;

    public AdminSystemController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/admins")
    @RequiresPermission("role:manage")
    public R<List<Map<String, Object>>> admins() {
        return R.ok(adminService.getAdmins());
    }

    @PostMapping("/admins")
    @RequiresPermission("role:manage")
    public R<Map<String, Object>> createAdmin(@Valid @RequestBody CreateAdminRequest request) {
        return R.ok(adminService.createAdmin(request.username(), request.password(), request.nickname()));
    }

    @PutMapping("/admins/{id}")
    @RequiresPermission("role:manage")
    public R<Map<String, Object>> updateAdmin(@PathVariable Long id, @Valid @RequestBody UpdateAdminRequest request) {
        return R.ok(adminService.updateAdmin(id, request.nickname(), request.password()));
    }

    @DeleteMapping("/admins/{id}")
    @RequiresPermission("role:manage")
    public R<Void> deleteAdmin(@PathVariable Long id) {
        adminService.deleteAdmin(id);
        return R.ok();
    }

    @PutMapping("/admins/{id}/roles")
    @RequiresPermission("role:manage")
    public R<Void> assignRoles(@PathVariable Long id, @Valid @RequestBody AssignRolesRequest request) {
        adminService.assignRoles(id, request.roleIds());
        return R.ok();
    }

    @GetMapping("/roles")
    @RequiresPermission("role:manage")
    public R<List<Map<String, Object>>> roles() {
        return R.ok(adminService.getRoles());
    }

    @PostMapping("/roles")
    @RequiresPermission("role:manage")
    public R<Map<String, Object>> createRole(@Valid @RequestBody RoleCreateRequest request) {
        return R.ok(adminService.createRole(request.name(), request.code(), request.permissions(), request.status()));
    }

    @PutMapping("/roles/{id}")
    @RequiresPermission("role:manage")
    public R<Void> updateRole(@PathVariable Long id, @Valid @RequestBody RoleCreateRequest request) {
        adminService.updateRole(id, request.name(), request.code(), request.permissions(), request.status());
        return R.ok();
    }

    @DeleteMapping("/roles/{id}")
    @RequiresPermission("role:manage")
    public R<Void> deleteRole(@PathVariable Long id) {
        adminService.deleteRole(id);
        return R.ok();
    }

    @GetMapping("/operation-logs")
    @RequiresPermission("log:view")
    public R<List<Map<String, Object>>> operationLogs(@RequestParam(defaultValue = "200") int limit) {
        return R.ok(adminService.getOperationLogs(limit));
    }
}
