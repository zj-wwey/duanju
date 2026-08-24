package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.dto.announcement.AnnouncementCreateRequest;
import com.duanju.dto.announcement.AnnouncementUpdateRequest;
import com.duanju.entity.AdminUser;
import com.duanju.security.PrincipalHolder;
import com.duanju.security.RequiresPermission;
import com.duanju.service.AnnouncementService;
import com.duanju.service.entity.AdminUserService;
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
@RequiresPermission("system:manage")
@RequestMapping("/api/admin/announcements")
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;
    private final AdminUserService adminUserService;

    public AdminAnnouncementController(AnnouncementService announcementService,
                                       AdminUserService adminUserService) {
        this.announcementService = announcementService;
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public R<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Integer status,
                                             @RequestParam(defaultValue = "100") int limit) {
        return R.ok(announcementService.getAllAnnouncements(keyword, status, limit));
    }

    @PostMapping
    public R<Map<String, Object>> create(@Valid @RequestBody AnnouncementCreateRequest request) {
        Long adminId = PrincipalHolder.adminId();
        AdminUser admin = adminUserService.getById(adminId);
        String adminName = admin != null ? admin.getNickname() : "admin";
        return R.ok(announcementService.createAnnouncement(
                request.title(), request.content(), request.type(),
                request.isTop(), adminId, adminName,
                request.startAt(), request.endAt()));
    }

    @PutMapping("/{id}")
    public R<Map<String, Object>> update(@PathVariable Long id,
                                        @Valid @RequestBody AnnouncementUpdateRequest request) {
        return R.ok(announcementService.updateAnnouncement(id, request.title(), request.content(),
                request.type(), request.isTop(), request.startAt(), request.endAt()));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        announcementService.deleteAnnouncement(id);
        return R.ok();
    }

    @PostMapping("/{id}/publish")
    public R<Map<String, Object>> publish(@PathVariable Long id) {
        return R.ok(announcementService.publishAnnouncement(id));
    }

    @PutMapping("/{id}/top")
    public R<Map<String, Object>> toggleTop(@PathVariable Long id) {
        return R.ok(announcementService.toggleTop(id));
    }
}
