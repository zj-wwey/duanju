package com.duanju.dto.feedback;

public record FeedbackUpdateRequest(
        String content,
        String screenshots
) {
}
