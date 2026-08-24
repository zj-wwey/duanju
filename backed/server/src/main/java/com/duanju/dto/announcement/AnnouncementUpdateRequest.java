package com.duanju.dto.announcement;

import java.time.LocalDateTime;

public record AnnouncementUpdateRequest(
        String title,
        String content,
        String type,
        Integer isTop,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
}
