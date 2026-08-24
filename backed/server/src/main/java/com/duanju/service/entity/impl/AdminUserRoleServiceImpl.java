package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.AdminUserRole;
import com.duanju.mapper.entity.AdminUserRoleMapper;
import com.duanju.service.entity.AdminUserRoleService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AdminUserRoleServiceImpl extends ServiceImpl<AdminUserRoleMapper, AdminUserRole> implements AdminUserRoleService {

    @Override
    public List<String> permissions(Long adminId) {
        return baseMapper.permissions(adminId);
    }

    @Override
    public List<Map<String, Object>> adminRoles(Long adminId) {
        return baseMapper.adminRoles(adminId);
    }
}
