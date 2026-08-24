package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.SystemAnnouncement;
import com.duanju.mapper.entity.SystemAnnouncementMapper;
import com.duanju.service.entity.SystemAnnouncementService;
import org.springframework.stereotype.Service;

@Service
public class SystemAnnouncementServiceImpl extends ServiceImpl<SystemAnnouncementMapper, SystemAnnouncement> implements SystemAnnouncementService {
}
