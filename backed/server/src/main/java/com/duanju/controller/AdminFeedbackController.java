package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.dto.feedback.FeedbackReplyRequest;
import com.duanju.entity.AdminUser;
import com.duanju.security.PrincipalHolder;
import com.duanju.security.RequiresPermission;
import com.duanju.service.FeedbackService;
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
@RequiresPermission("user:manage")
@RequestMapping("/api/admin/feedback")
public class AdminFeedbackController {

    private final FeedbackService feedbackService;
    private final AdminUserService adminUserService;

    public AdminFeedbackController(FeedbackService feedbackService,
                                   AdminUserService adminUserService) {
        this.feedbackService = feedbackService;
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public R<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String type,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "100") int limit) {
        return R.ok(feedbackService.getAllFeedback(keyword, type, status, limit));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(feedbackService.getFeedbackById(id));
    }

    @PostMapping("/{id}/reply")
    public R<Map<String, Object>> reply(@PathVariable Long id,
                                        @Valid @RequestBody FeedbackReplyRequest request) {
        Long adminId = PrincipalHolder.adminId();
        AdminUser admin = adminUserService.getById(adminId);
        String adminName = admin != null ? admin.getNickname() : "admin";
        return R.ok(feedbackService.replyFeedback(id, adminName, request.reply()));
    }

    @PutMapping("/{id}/status")
    public R<Map<String, Object>> updateStatus(@PathVariable Long id,
                                               @RequestBody Map<String, String> body) {
        return R.ok(feedbackService.updateStatus(id, body.get("status")));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        feedbackService.deleteFeedbackAdmin(id);
        return R.ok();
    }

    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        return R.ok(feedbackService.getFeedbackStats());
    }
}
