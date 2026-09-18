-- ============================================================
-- user_order 支付渠道列 + 软删除列 幂等补齐
-- ------------------------------------------------------------
-- 现象: H5/App 调 /user/orders/stripe-checkout、/user/orders/paypal-checkout
--       返回 {"code":400,"errorCode":"DATA_ACCESS_FAILED"}。
-- 根因: 线上 user_order 表结构落后,缺少 Stripe/PayPal/Google 支付列。
--       StripePaymentService / PayPalPaymentService 用 MyBatis-Plus lambdaQuery
--       查询订单实体会 SELECT 全部映射列,缺列即抛 "Unknown column" →
--       DataAccessException → DATA_ACCESS_FAILED (创建订单的手写 SQL 不查
--       这些列,所以下单正常、发起支付才失败)。
-- 本脚本: 与代码 SchemaMigrationRunner.ensureOrderColumns() 的定义保持一致,
--         缺列才加、缺索引才建,可重复执行。执行后无需重启后端。
-- ============================================================

-- ---------- 列 ----------
set @col := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'user_order' and column_name = 'store_transaction_id');
set @sql := if(@col = 0,
  'alter table user_order add column store_transaction_id varchar(128) null comment ''应用商店交易ID (Apple originalTransactionId / Google purchaseToken / Stripe charge_id)''',
  'select ''user_order.store_transaction_id exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @col := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'user_order' and column_name = 'store_original_transaction_id');
set @sql := if(@col = 0,
  'alter table user_order add column store_original_transaction_id varchar(128) null comment ''Apple 订阅原始交易ID / Stripe payment_intent_id,订阅生命周期不变''',
  'select ''user_order.store_original_transaction_id exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @col := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'user_order' and column_name = 'store_product_id');
set @sql := if(@col = 0,
  'alter table user_order add column store_product_id varchar(128) null comment ''应用商店侧商品ID (Apple productId / Google sku / Stripe price_id)''',
  'select ''user_order.store_product_id exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @col := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'user_order' and column_name = 'store_bundle_id');
set @sql := if(@col = 0,
  'alter table user_order add column store_bundle_id varchar(128) null comment ''Apple bundleId / Google packageName / Stripe account_id''',
  'select ''user_order.store_bundle_id exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @col := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'user_order' and column_name = 'store_environment');
set @sql := if(@col = 0,
  'alter table user_order add column store_environment varchar(16) null comment ''渠道侧环境: SANDBOX/PRODUCTION/TEST/LIVE''',
  'select ''user_order.store_environment exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @col := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'user_order' and column_name = 'store_receipt_hash');
set @sql := if(@col = 0,
  'alter table user_order add column store_receipt_hash varchar(128) null comment ''已验证 payload 的 SHA-256 摘要,用于审计去重''',
  'select ''user_order.store_receipt_hash exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @col := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'user_order' and column_name = 'refund_reason');
set @sql := if(@col = 0,
  'alter table user_order add column refund_reason varchar(64) null comment ''退款原因 (Apple refundReason enum / Stripe refund reason)''',
  'select ''user_order.refund_reason exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @col := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'user_order' and column_name = 'tax_amount_cents');
set @sql := if(@col = 0,
  'alter table user_order add column tax_amount_cents int not null default 0 comment ''税额,单位分''',
  'select ''user_order.tax_amount_cents exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @col := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'user_order' and column_name = 'stripe_session_id');
set @sql := if(@col = 0,
  'alter table user_order add column stripe_session_id varchar(128) null comment ''Stripe Checkout Session ID (cs_test_xxx),创建 Session 时写入''',
  'select ''user_order.stripe_session_id exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @col := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'user_order' and column_name = 'stripe_payment_intent_id');
set @sql := if(@col = 0,
  'alter table user_order add column stripe_payment_intent_id varchar(128) null comment ''Stripe PaymentIntent ID (pi_xxx),支付成功 webhook 写入,幂等''',
  'select ''user_order.stripe_payment_intent_id exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @col := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'user_order' and column_name = 'google_purchase_token');
set @sql := if(@col = 0,
  'alter table user_order add column google_purchase_token varchar(512) null comment ''Google Play Billing purchaseToken (verifyPurchase/RTDN/退款查询)''',
  'select ''user_order.google_purchase_token exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @col := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'user_order' and column_name = 'paypal_order_id');
set @sql := if(@col = 0,
  'alter table user_order add column paypal_order_id varchar(128) null comment ''PayPal Order ID (创建订单时写入)''',
  'select ''user_order.paypal_order_id exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @col := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'user_order' and column_name = 'paypal_payment_id');
set @sql := if(@col = 0,
  'alter table user_order add column paypal_payment_id varchar(128) null comment ''PayPal Capture ID / Payment ID (捕获支付时写入,幂等)''',
  'select ''user_order.paypal_payment_id exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @col := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'user_order' and column_name = 'deleted_at');
set @sql := if(@col = 0,
  'alter table user_order add column deleted_at datetime null comment ''用户软删除时间,非空表示用户已删除该订单''',
  'select ''user_order.deleted_at exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

-- ---------- 索引 (新列均为 NULL,唯一索引允许重复 NULL,可安全创建) ----------
set @idx := (select count(*) from information_schema.statistics
  where table_schema = database() and table_name = 'user_order' and index_name = 'uk_order_store_tx');
set @sql := if(@idx = 0,
  'create unique index uk_order_store_tx on user_order(store_transaction_id)',
  'select ''index uk_order_store_tx exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @idx := (select count(*) from information_schema.statistics
  where table_schema = database() and table_name = 'user_order' and index_name = 'uk_order_stripe_pi');
set @sql := if(@idx = 0,
  'create unique index uk_order_stripe_pi on user_order(stripe_payment_intent_id)',
  'select ''index uk_order_stripe_pi exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @idx := (select count(*) from information_schema.statistics
  where table_schema = database() and table_name = 'user_order' and index_name = 'idx_order_stripe_session');
set @sql := if(@idx = 0,
  'create index idx_order_stripe_session on user_order(stripe_session_id)',
  'select ''index idx_order_stripe_session exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @idx := (select count(*) from information_schema.statistics
  where table_schema = database() and table_name = 'user_order' and index_name = 'uk_order_google_purchase_token');
set @sql := if(@idx = 0,
  'create unique index uk_order_google_purchase_token on user_order(google_purchase_token)',
  'select ''index uk_order_google_purchase_token exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @idx := (select count(*) from information_schema.statistics
  where table_schema = database() and table_name = 'user_order' and index_name = 'uk_order_paypal_payment_id');
set @sql := if(@idx = 0,
  'create unique index uk_order_paypal_payment_id on user_order(paypal_payment_id)',
  'select ''index uk_order_paypal_payment_id exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;
