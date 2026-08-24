package com.duanju.service;

import com.duanju.entity.UserFeedback;
import com.duanju.security.PrincipalHolder;
import com.duanju.service.entity.UserFeedbackService;
import com.duanju.util.MapUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class FeedbackService {

    private final UserFeedbackService feedbackService;
    private final UserService userService;

    public FeedbackService(UserFeedbackService feedbackService, UserService userService) {
        this.feedbackService = feedbackService;
        this.userService = userService;
    }

    @Transactional
    public Map<String, Object> submitFeedback(String type, String title, String content,
                                              String screenshots, String contact,
                                              String deviceInfo, String appVersion) {
        Long userId = PrincipalHolder.userId();
        Map<String, Object> profile = userService.getProfile(userId);
        UserFeedback fb = new UserFeedback();
        fb.setUserId(userId);
        fb.setUserNickname(MapUtil.str(profile, "nickname"));
        fb.setUserPhone(MapUtil.str(profile, "phone"));
        fb.setType(type == null ? "BUG" : type);
        fb.setTitle(title);
        fb.setContent(content);
        fb.setScreenshots(screenshots);
        fb.setContact(contact);
        fb.setDeviceInfo(deviceInfo);
        fb.setAppVersion(appVersion);
        fb.setStatus("PENDING");
        feedbackService.save(fb);
        return MapUtil.beanToMap(fb);
    }

    public List<Map<String, Object>> getMyFeedback(int limit) {
        Long userId = PrincipalHolder.userId();
        List<UserFeedback> list = feedbackService.lambdaQuery()
                .eq(UserFeedback::getUserId, userId)
                .orderByDesc(UserFeedback::getCreatedAt)
                .last("limit " + Math.max(1, Math.min(limit, 200)))
                .list();
        return MapUtil.beansToMaps(list);
    }

    public Map<String, Object> getFeedbackDetail(Long id) {
        Long userId = PrincipalHolder.userId();
        UserFeedback fb = feedbackService.getById(id);
        if (fb == null || !fb.getUserId().equals(userId)) {
            throw new IllegalArgumentException("feedback not found");
        }
        return MapUtil.beanToMap(fb);
    }

    @Transactional
    public Map<String, Object> updateFeedback(Long id, String content, String screenshots) {
        Long userId = PrincipalHolder.userId();
        UserFeedback fb = feedbackService.getById(id);
        if (fb == null || !fb.getUserId().equals(userId)) {
            throw new IllegalArgumentException("feedback not found");
        }
        feedbackService.lambdaUpdate()
                .set(UserFeedback::getContent, content)
                .set(UserFeedback::getScreenshots, screenshots)
                .eq(UserFeedback::getId, id)
                .update();
        return MapUtil.beanToMap(feedbackService.getById(id));
    }

    @Transactional
    public void deleteFeedback(Long id) {
        Long userId = PrincipalHolder.userId();
        UserFeedback fb = feedbackService.getById(id);
        if (fb == null || !fb.getUserId().equals(userId)) {
            throw new IllegalArgumentException("feedback not found");
        }
        feedbackService.removeById(id);
    }

    // --- Admin methods ---

    public List<Map<String, Object>> getAllFeedback(String keyword, String type, String status, int limit) {
        var query = feedbackService.lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            query.and(w -> w.like(UserFeedback::getTitle, keyword)
                    .or().like(UserFeedback::getContent, keyword)
                    .or().like(UserFeedback::getUserNickname, keyword));
        }
        if (type != null && !type.isBlank()) {
            query.eq(UserFeedback::getType, type);
        }
        if (status != null && !status.isBlank()) {
            query.eq(UserFeedback::getStatus, status);
        }
        List<UserFeedback> list = query
                .orderByDesc(UserFeedback::getCreatedAt)
                .last("limit " + Math.max(1, Math.min(limit, 500)))
                .list();
        return MapUtil.beansToMaps(list);
    }

    public Map<String, Object> getFeedbackById(Long id) {
        UserFeedback fb = feedbackService.getById(id);
        if (fb == null) {
            throw new IllegalArgumentException("feedback not found");
        }
        return MapUtil.beanToMap(fb);
    }

    @Transactional
    public Map<String, Object> replyFeedback(Long id, String handlerName, String reply) {
        UserFeedback fb = feedbackService.getById(id);
        if (fb == null) {
            throw new IllegalArgumentException("feedback not found");
        }
        feedbackService.lambdaUpdate()
                .set(UserFeedback::getStatus, "PROCESSED")
                .set(UserFeedback::getHandlerName, handlerName)
                .set(UserFeedback::getReply, reply)
                .set(UserFeedback::getRepliedAt, LocalDateTime.now())
                .eq(UserFeedback::getId, id)
                .update();
        return MapUtil.beanToMap(feedbackService.getById(id));
    }

    @Transactional
    public Map<String, Object> updateStatus(Long id, String status) {
        UserFeedback fb = feedbackService.getById(id);
        if (fb == null) {
            throw new IllegalArgumentException("feedback not found");
        }
        feedbackService.lambdaUpdate()
                .set(UserFeedback::getStatus, status)
                .eq(UserFeedback::getId, id)
                .update();
        return MapUtil.beanToMap(feedbackService.getById(id));
    }

    @Transactional
    public void deleteFeedbackAdmin(Long id) {
        feedbackService.removeById(id);
    }

    public Map<String, Object> getFeedbackStats() {
        long total = feedbackService.count();
        long pending = feedbackService.lambdaQuery()
                .eq(UserFeedback::getStatus, "PENDING")
                .count();
        long processed = feedbackService.lambdaQuery()
                .eq(UserFeedback::getStatus, "PROCESSED")
                .count();
        long closed = feedbackService.lambdaQuery()
                .eq(UserFeedback::getStatus, "CLOSED")
                .count();
        return MapUtil.map("total", total, "pending", pending,
                "processed", processed, "closed", closed);
    }
}
