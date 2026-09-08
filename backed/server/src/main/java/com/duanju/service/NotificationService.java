package com.duanju.service;

import com.duanju.entity.UserNotification;
import com.duanju.mapper.entity.UserNotificationMapper;
import com.duanju.util.MapUtil;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private final UserNotificationMapper mapper;

    public NotificationService(UserNotificationMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> listForUser(Long userId, int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 20;
        long total = mapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserNotification>()
                        .eq("user_id", userId));
        List<Map<String, Object>> records = mapper.listWithDetail(
                userId, (page - 1) * pageSize, pageSize);
        return MapUtil.map("total", total, "page", page, "page_size", pageSize, "records", records);
    }

    public long countUnread(Long userId) {
        return mapper.countUnread(userId);
    }

    public void markAllRead(Long userId) {
        mapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<UserNotification>()
                        .eq("user_id", userId)
                        .eq("is_read", 0)
                        .set("is_read", 1));
    }

    public void markRead(Long userId, Long id) {
        mapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<UserNotification>()
                        .eq("id", id)
                        .eq("user_id", userId)
                        .set("is_read", 1));
    }
}
