-- ============================================================================
-- 修复 point_product 表中旧数据的 NULL 字段
-- 早期 init.sql 缺少 product_category / package_type / membership_level 等列,
-- 后来通过 ALTER TABLE 加列后,旧数据的这些字段为 NULL,导致前端 filter 过滤掉。
-- 执行后 Web 端充值/商城/VIP 页面能正确展示所有套餐。
-- ============================================================================

-- 1. 回填 product_category (默认 RECHARGE 即积分充值)
UPDATE point_product
SET product_category = 'RECHARGE'
WHERE product_category IS NULL OR product_category = '';

-- 2. 回填 package_type
UPDATE point_product
SET package_type = 'RECHARGE'
WHERE package_type IS NULL OR package_type = '';

-- 3. 回填其他可能为 NULL 的字段,给合理默认值
UPDATE point_product SET first_purchase_bonus = 0 WHERE first_purchase_bonus IS NULL;
UPDATE point_product SET daily_limit = 0 WHERE daily_limit IS NULL;
UPDATE point_product SET monthly_limit = 0 WHERE monthly_limit IS NULL;
UPDATE point_product SET sort_order = 0 WHERE sort_order IS NULL;
