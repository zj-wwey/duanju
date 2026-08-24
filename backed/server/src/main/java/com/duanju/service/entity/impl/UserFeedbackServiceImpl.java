package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.UserFeedback;
import com.duanju.mapper.entity.UserFeedbackMapper;
import com.duanju.service.entity.UserFeedbackService;
import org.springframework.stereotype.Service;

@Service
public class UserFeedbackServiceImpl extends ServiceImpl<UserFeedbackMapper, UserFeedback> implements UserFeedbackService {
}
