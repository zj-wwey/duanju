package com.duanju.mapper.entity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duanju.entity.AdminUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminUserRoleMapper extends BaseMapper<AdminUserRole> {

    @Select("""
            select distinct r.permissions
            from admin_user_role ur join admin_role r on r.id = ur.role_id
            where ur.admin_id = #{adminId} and r.status = 1
            """)
    List<String> permissions(@Param("adminId") Long adminId);

    @Select("""
            select r.id, r.name, r.code, r.permissions
            from admin_user_role ur join admin_role r on r.id = ur.role_id
            where ur.admin_id = #{adminId} and r.status = 1
            order by r.id asc
            """)
    List<Map<String, Object>> adminRoles(@Param("adminId") Long adminId);
}
