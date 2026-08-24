package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.dto.feedback.FeedbackCreateRequest;
import com.duanju.dto.feedback.FeedbackUpdateRequest;
import com.duanju.service.FeedbackService;
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
@RequestMapping("/api/feedback")
public class UserFeedbackController {

    private final FeedbackService feedbackService;

    public UserFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public R<Map<String, Object>> submit(@Valid @RequestBody FeedbackCreateRequest request) {
        return R.ok(feedbackService.submitFeedback(
                request.type(), request.title(), request.content(),
                request.screenshots(), request.contact(),
                request.deviceInfo(), request.appVersion()));
    }

    @GetMapping
    public R<List<Map<String, Object>>> myList(@RequestParam(defaultValue = "50") int limit) {
        return R.ok(feedbackService.getMyFeedback(limit));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(feedbackService.getFeedbackDetail(id));
    }

    @PutMapping("/{id}")
    public R<Map<String, Object>> update(@PathVariable Long id,
                                         @RequestBody FeedbackUpdateRequest request) {
        return R.ok(feedbackService.updateFeedback(id, request.content(), request.screenshots()));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        feedbackService.deleteFeedback(id);
        return R.ok();
    }
}
