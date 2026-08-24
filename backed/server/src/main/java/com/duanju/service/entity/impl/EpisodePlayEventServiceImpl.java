package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.EpisodePlayEvent;
import com.duanju.mapper.entity.EpisodePlayEventMapper;
import com.duanju.service.entity.EpisodePlayEventService;
import org.springframework.stereotype.Service;

@Service
public class EpisodePlayEventServiceImpl extends ServiceImpl<EpisodePlayEventMapper, EpisodePlayEvent> implements EpisodePlayEventService {
}
