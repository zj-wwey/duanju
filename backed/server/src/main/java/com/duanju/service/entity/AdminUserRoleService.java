package com.duanju.service.entity;

import com.baomidou.mybatisplus.extension.service.IService;
import com.duanju.entity.AdminUserRole;

import java.util.List;
import java.util.Map;

public interface AdminUserRoleService extends IService<AdminUserRole> {

    List<String> permissions(Long adminId);

    List<Map<String, Object>> adminRoles(Long adminId);
}
