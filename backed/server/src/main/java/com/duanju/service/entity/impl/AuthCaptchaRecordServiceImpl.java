package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.AuthCaptchaRecord;
import com.duanju.mapper.entity.AuthCaptchaRecordMapper;
import com.duanju.service.entity.AuthCaptchaRecordService;
import org.springframework.stereotype.Service;

@Service
public class AuthCaptchaRecordServiceImpl extends ServiceImpl<AuthCaptchaRecordMapper, AuthCaptchaRecord> implements AuthCaptchaRecordService {
}
