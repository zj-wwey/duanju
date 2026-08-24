package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.AutoRenewalSubscription;
import com.duanju.mapper.entity.AutoRenewalSubscriptionMapper;
import com.duanju.service.entity.AutoRenewalSubscriptionService;
import org.springframework.stereotype.Service;

@Service
public class AutoRenewalSubscriptionServiceImpl extends ServiceImpl<AutoRenewalSubscriptionMapper, AutoRenewalSubscription> implements AutoRenewalSubscriptionService {
}