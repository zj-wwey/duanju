package com.duanju.dto.feedback;

import jakarta.validation.constraints.NotBlank;

public record FeedbackCreateRequest(
        @NotBlank(message = "反馈类型不能为空") String type,
        @NotBlank(message = "标题不能为空") String title,
        @NotBlank(message = "内容不能为空") String content,
        String screenshots,
        String contact,
        String deviceInfo,
        String appVersion
) {
}
