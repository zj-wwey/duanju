-- 订单表软删除字段
-- 用于"我的订单"页面删除终态订单 (CANCELLED / CLOSED / REFUNDED),用户侧查询自动过滤,管理员侧保留可见。
-- 执行前请确认: 对已有数据无影响 (新增列允许 NULL,默认 NULL)

ALTER TABLE user_order
  ADD COLUMN deleted_at datetime NULL COMMENT '用户软删除时间,非空表示用户已删除该订单' AFTER updated_at;