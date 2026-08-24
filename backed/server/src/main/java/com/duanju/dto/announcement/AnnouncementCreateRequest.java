package com.duanju.dto.announcement;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record AnnouncementCreateRequest(
        @NotBlank(message = "title is required") String title,
        @NotBlank(message = "content is required") String content,
        String type,
        Integer isTop,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
    public AnnouncementCreateRequest {
        if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
            throw new IllegalArgumentException("endAt must not be before startAt");
        }
    }
}
