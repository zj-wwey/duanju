package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.dto.admin.CreateUserRequest;
import com.duanju.dto.admin.PointAdjustRequest;
import com.duanju.dto.admin.StatusUpdateRequest;
import com.duanju.dto.admin.UpdateUserRequest;
import com.duanju.security.RequiresPermission;
import com.duanju.service.AdminService;
import com.duanju.service.PointService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiresPermission("user:manage")
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminService adminService;
    private final PointService pointService;

    public AdminUserController(AdminService adminService, PointService pointService) {
        this.adminService = adminService;
        this.pointService = pointService;
    }

    @GetMapping
    public R<List<Map<String, Object>>> users(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(defaultValue = "100") int limit) {
        return R.ok(adminService.getUsers(keyword, status, limit));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(adminService.getUserDetail(id));
    }

    @PostMapping
    public R<Map<String, Object>> create(@Valid @RequestBody CreateUserRequest request) {
        return R.ok(adminService.createUser(
                request.username(), request.phone(), request.password(),
                request.nickname(), request.avatarUrl(), request.points()));
    }

    @PutMapping("/{id}")
    public R<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return R.ok(adminService.updateUser(id,
                request.username(), request.phone(), request.nickname(),
                request.avatarUrl(), request.status()));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        adminService.deleteUser(id);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        adminService.updateUserStatus(id, request.status());
        return R.ok();
    }

    @PostMapping("/{id}/points")
    @RequiresPermission("point:manage")
    public R<Map<String, Object>> changePoints(@PathVariable Long id, @Valid @RequestBody PointAdjustRequest request) {
        return R.ok(pointService.adjustPoints(id, request.delta(), request.remark()));
    }
}
