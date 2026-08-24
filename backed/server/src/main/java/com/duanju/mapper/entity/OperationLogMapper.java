package com.duanju.mapper.entity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duanju.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {

    @Select("""
            select l.id, l.admin_id, a.username admin_username, l.method, l.path, l.status_code, l.ip, l.created_at
            from operation_log l left join admin_user a on a.id = l.admin_id
            order by l.id desc
            limit #{limit}
            """)
    List<Map<String, Object>> operationLogs(@Param("limit") int limit);
}
