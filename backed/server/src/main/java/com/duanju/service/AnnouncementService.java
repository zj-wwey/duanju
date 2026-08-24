package com.duanju.service;

import com.duanju.entity.AnnouncementReadRecord;
import com.duanju.entity.SystemAnnouncement;
import com.duanju.security.PrincipalHolder;
import com.duanju.service.entity.AnnouncementReadRecordService;
import com.duanju.service.entity.SystemAnnouncementService;
import com.duanju.util.MapUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AnnouncementService {

    private final SystemAnnouncementService announcementService;
    private final AnnouncementReadRecordService readRecordService;

    public AnnouncementService(SystemAnnouncementService announcementService,
                               AnnouncementReadRecordService readRecordService) {
        this.announcementService = announcementService;
        this.readRecordService = readRecordService;
    }

    public List<Map<String, Object>> getActiveAnnouncements() {
        LocalDateTime now = LocalDateTime.now();
        List<SystemAnnouncement> list = announcementService.lambdaQuery()
                .eq(SystemAnnouncement::getStatus, 1)
                .and(w -> w.isNull(SystemAnnouncement::getStartAt)
                        .or().le(SystemAnnouncement::getStartAt, now))
                .and(w -> w.isNull(SystemAnnouncement::getEndAt)
                        .or().ge(SystemAnnouncement::getEndAt, now))
                .orderByDesc(SystemAnnouncement::getIsTop)
                .orderByDesc(SystemAnnouncement::getPublishedAt)
                .list();
        List<Map<String, Object>> result = MapUtil.beansToMaps(list);
        Long userId = PrincipalHolder.userId();
        for (Map<String, Object> item : result) {
            Object annId = item.get("id");
            boolean read = readRecordService.lambdaQuery()
                    .eq(AnnouncementReadRecord::getUserId, userId)
                    .eq(AnnouncementReadRecord::getAnnouncementId, annId)
                    .exists();
            item.put("is_read", read);
        }
        return result;
    }

    public Map<String, Object> getAnnouncementDetail(Long id) {
        SystemAnnouncement ann = announcementService.getById(id);
        if (ann == null || ann.getStatus() != 1) {
            throw new IllegalArgumentException("announcement not found");
        }
        announcementService.lambdaUpdate()
                .set(SystemAnnouncement::getViewCount, ann.getViewCount() == null ? 1 : ann.getViewCount() + 1)
                .eq(SystemAnnouncement::getId, id)
                .update();
        markAsRead(id);
        Map<String, Object> result = MapUtil.beanToMap(ann);
        result.put("is_read", true);
        return result;
    }

    public long getUnreadCount() {
        Long userId = PrincipalHolder.userId();
        LocalDateTime now = LocalDateTime.now();
        List<SystemAnnouncement> activeList = announcementService.lambdaQuery()
                .eq(SystemAnnouncement::getStatus, 1)
                .and(w -> w.isNull(SystemAnnouncement::getStartAt)
                        .or().le(SystemAnnouncement::getStartAt, now))
                .and(w -> w.isNull(SystemAnnouncement::getEndAt)
                        .or().ge(SystemAnnouncement::getEndAt, now))
                .list();
        long count = 0;
        for (SystemAnnouncement ann : activeList) {
            boolean read = readRecordService.lambdaQuery()
                    .eq(AnnouncementReadRecord::getUserId, userId)
                    .eq(AnnouncementReadRecord::getAnnouncementId, ann.getId())
                    .exists();
            if (!read) {
                count++;
            }
        }
        return count;
    }

    @Transactional
    public void markAsRead(Long announcementId) {
        Long userId = PrincipalHolder.userId();
        boolean exists = readRecordService.lambdaQuery()
                .eq(AnnouncementReadRecord::getUserId, userId)
                .eq(AnnouncementReadRecord::getAnnouncementId, announcementId)
                .exists();
        if (!exists) {
            AnnouncementReadRecord record = new AnnouncementReadRecord();
            record.setUserId(userId);
            record.setAnnouncementId(announcementId);
            record.setReadAt(LocalDateTime.now());
            readRecordService.save(record);
        }
    }

    // --- Admin methods ---

    public List<Map<String, Object>> getAllAnnouncements(String keyword, Integer status, int limit) {
        var query = announcementService.lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            query.and(w -> w.like(SystemAnnouncement::getTitle, keyword)
                    .or().like(SystemAnnouncement::getContent, keyword));
        }
        if (status != null) {
            query.eq(SystemAnnouncement::getStatus, status);
        }
        List<SystemAnnouncement> list = query
                .orderByDesc(SystemAnnouncement::getIsTop)
                .orderByDesc(SystemAnnouncement::getCreatedAt)
                .last("limit " + Math.max(1, Math.min(limit, 500)))
                .list();
        return MapUtil.beansToMaps(list);
    }

    @Transactional
    public Map<String, Object> createAnnouncement(String title, String content, String type,
                                                  Integer isTop, Long publisherId, String publisherName,
                                                  LocalDateTime startAt, LocalDateTime endAt) {
        SystemAnnouncement ann = new SystemAnnouncement();
        ann.setTitle(title);
        ann.setContent(content);
        ann.setType(type == null ? "SYSTEM" : type);
        ann.setIsTop(isTop == null ? 0 : isTop);
        ann.setStatus(0);
        ann.setPublisherId(publisherId);
        ann.setPublisherName(publisherName);
        ann.setStartAt(startAt);
        ann.setEndAt(endAt);
        ann.setViewCount(0);
        announcementService.save(ann);
        return MapUtil.beanToMap(ann);
    }

    @Transactional
    public Map<String, Object> updateAnnouncement(Long id, String title, String content, String type,
                                                  Integer isTop, LocalDateTime startAt, LocalDateTime endAt) {
        SystemAnnouncement ann = announcementService.getById(id);
        if (ann == null) {
            throw new IllegalArgumentException("announcement not found");
        }
        announcementService.lambdaUpdate()
                .set(SystemAnnouncement::getTitle, title)
                .set(SystemAnnouncement::getContent, content)
                .set(SystemAnnouncement::getType, type)
                .set(SystemAnnouncement::getIsTop, isTop)
                .set(SystemAnnouncement::getStartAt, startAt)
                .set(SystemAnnouncement::getEndAt, endAt)
                .eq(SystemAnnouncement::getId, id)
                .update();
        return MapUtil.beanToMap(announcementService.getById(id));
    }

    @Transactional
    public void deleteAnnouncement(Long id) {
        announcementService.lambdaUpdate()
                .set(SystemAnnouncement::getStatus, -1)
                .eq(SystemAnnouncement::getId, id)
                .update();
    }

    @Transactional
    public Map<String, Object> publishAnnouncement(Long id) {
        SystemAnnouncement ann = announcementService.getById(id);
        if (ann == null) {
            throw new IllegalArgumentException("announcement not found");
        }
        announcementService.lambdaUpdate()
                .set(SystemAnnouncement::getStatus, 1)
                .set(SystemAnnouncement::getPublishedAt, LocalDateTime.now())
                .eq(SystemAnnouncement::getId, id)
                .update();
        return MapUtil.beanToMap(announcementService.getById(id));
    }

    @Transactional
    public Map<String, Object> toggleTop(Long id) {
        SystemAnnouncement ann = announcementService.getById(id);
        if (ann == null) {
            throw new IllegalArgumentException("announcement not found");
        }
        int newTop = ann.getIsTop() != null && ann.getIsTop() == 1 ? 0 : 1;
        announcementService.lambdaUpdate()
                .set(SystemAnnouncement::getIsTop, newTop)
                .eq(SystemAnnouncement::getId, id)
                .update();
        return MapUtil.beanToMap(announcementService.getById(id));
    }
}
