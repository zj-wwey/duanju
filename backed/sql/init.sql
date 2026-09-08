-- ============================================================================
-- 短剧运营后台 - 数据库初始化脚本
-- ============================================================================
-- 说明：本文件包含当前后端需要的全部表结构、索引与基础示例数据。
--       已整合历史增量脚本（auth_upgrade、category_system_upgrade），
--       历史脚本已归档至 migrations/archive/ 目录，仅保留用于追溯。
-- 使用方式：mysql -uroot -p < backed/sql/init.sql
-- 适用版本：MySQL 8.0+
-- 注意事项：导入后会重建 duanju 库内业务表，请勿直接用于保留生产数据的库。
-- ============================================================================

create database if not exists duanju default character set utf8mb4 collate utf8mb4_0900_ai_ci;
use duanju;

-- ============================================================================
-- 清理旧表（按外键依赖逆序删除，子表先于父表）
-- ============================================================================

-- 第1层：所有含外键的子表（引用其他表）
drop table if exists user_episode_unlock;
drop table if exists episode_play_event;
drop table if exists user_watch_history;
drop table if exists user_favorite;
drop table if exists ad_reward_record;
drop table if exists user_order;
drop table if exists point_record;
drop table if exists user_checkin;
drop table if exists drama_episode;
drop table if exists admin_user_role;
drop table if exists operation_log;

-- 第2层：被引用的父表
drop table if exists drama;
drop table if exists point_product;
drop table if exists admin_role;
drop table if exists admin_user;
drop table if exists app_user;

-- 第3层：无外键的独立表
drop table if exists drama_category_filter;
drop table if exists auth_captcha_record;

-- ============================================================================
-- 模块一：认证与权限 (Auth & RBAC)
-- 包含：管理员账号、角色权限、前台用户、验证码记录
-- ============================================================================

-- 后台管理员表
create table admin_user (
  id bigint primary key auto_increment comment '管理员ID',
  username varchar(64) not null comment '管理员登录账号',
  password_hash varchar(128) not null comment '密码哈希，格式为salt:hash',
  nickname varchar(64) not null comment '管理员昵称',
  avatar varchar(1000) comment '管理员头像URL',
  avatar_object_key varchar(255) comment '管理员头像 R2 对象 key (用于级联删除)',
  status tinyint not null default 1 comment '状态：1启用，0禁用，-1删除',
  created_at datetime not null default current_timestamp comment '创建时间',
  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  unique key uk_admin_username (username)
) engine=InnoDB default charset=utf8mb4 comment='后台管理员表';

-- 后台角色权限表
create table admin_role (
  id bigint primary key auto_increment comment '角色ID',
  name varchar(64) not null comment '角色名称',
  code varchar(64) not null comment '角色编码，系统内唯一',
  permissions varchar(1000) not null comment '权限标识集合，多个权限用英文逗号分隔，*表示全部权限',
  status tinyint not null default 1 comment '状态：1启用，0禁用，-1删除',
  created_at datetime not null default current_timestamp comment '创建时间',
  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  unique key uk_role_code (code)
) engine=InnoDB default charset=utf8mb4 comment='后台角色权限表';

-- 管理员-角色关联表（多对多）
create table admin_user_role (
  id bigint primary key auto_increment comment '关联ID',
  admin_id bigint not null comment '管理员ID',
  role_id bigint not null comment '角色ID',
  created_at datetime not null default current_timestamp comment '创建时间',
  unique key uk_admin_role (admin_id, role_id),
  constraint fk_admin_role_admin foreign key (admin_id) references admin_user(id),
  constraint fk_admin_role_role foreign key (role_id) references admin_role(id)
) engine=InnoDB default charset=utf8mb4 comment='管理员角色关联表';

-- 标准化前台用户信息表
create table app_user (
  id bigint primary key auto_increment comment '用户ID',
  username varchar(64) not null comment '登录用户名',
  phone varchar(32) comment '手机号，预留手机号认证能力',
  phone_verified tinyint not null default 0 comment '手机号认证状态：1已认证，0未认证',
  auth_provider varchar(16) not null default 'PASSWORD' comment '当前认证方式：PASSWORD用户名密码，PHONE预留手机号认证',
  password_hash varchar(128) not null comment '密码哈希，格式为salt:hash',
  nickname varchar(64) not null comment '用户昵称',
  avatar_url varchar(1000) comment '头像URL',
  avatar_object_key varchar(255) comment '用户头像 R2 对象 key (用于级联删除)',
  points int not null default 0 comment '当前可用积分余额',
  notice_enabled tinyint not null default 1 comment '消息通知开关：1开，0关',
  auto_next_enabled tinyint not null default 1 comment '自动播放下一集：1开，0关',
  last_token_jti varchar(64) comment '最近一次JWT访问令牌ID',
  refresh_token_jti varchar(64) comment '最近一次JWT刷新令牌ID',
  last_login_at datetime comment '最近登录时间',
  last_login_ip varchar(64) comment '最近登录IP',
  status tinyint not null default 1 comment '状态：1正常，0禁用，-1注销',
  created_at datetime not null default current_timestamp comment '注册时间',
  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  unique key uk_user_username (username),
  unique key uk_user_phone (phone),
  key idx_user_status (status, id),
  key idx_user_last_login (last_login_at)
) engine=InnoDB default charset=utf8mb4 comment='标准化前台用户信息表';

-- 认证验证码记录表
create table auth_captcha_record (
  id bigint primary key auto_increment comment '验证码记录ID',
  captcha_id varchar(64) not null comment '验证码请求ID',
  scene varchar(32) not null comment '验证码场景：LOGIN、REGISTER、ADMIN_LOGIN等',
  receiver varchar(128) comment '验证码接收目标，图形验证码为空，短信验证码预留手机号',
  captcha_hash varchar(128) not null comment '验证码哈希值',
  fail_count int not null default 0 comment '错误次数',
  expires_at datetime not null comment '过期时间',
  verified_at datetime comment '验证通过时间',
  request_ip varchar(64) comment '请求IP',
  status tinyint not null default 0 comment '状态：0待验证，1已验证，-1作废',
  created_at datetime not null default current_timestamp comment '创建时间',
  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  unique key uk_captcha_id (captcha_id),
  key idx_captcha_scene (scene, status, expires_at),
  key idx_captcha_receiver (receiver, scene, created_at)
) engine=InnoDB default charset=utf8mb4 comment='认证验证码记录表';

-- ============================================================================
-- 模块二：内容管理 (Content)
-- 包含：分类筛选配置、短剧主表、短剧分集
-- ============================================================================

-- 前端分类页筛选配置表
create table drama_category_filter (
  id bigint primary key auto_increment comment '分类筛选项ID',
  group_key varchar(32) not null comment '筛选组标识，对应前端筛选参数',
  group_label_key varchar(64) not null comment '筛选组前端多语言key',
  group_sort_order int not null default 0 comment '筛选组排序值，越小越靠前',
  option_key varchar(32) not null comment '筛选项标识，对应前端筛选值',
  option_label_key varchar(64) not null comment '筛选项前端多语言key',
  option_sort_order int not null default 0 comment '筛选项排序值，越小越靠前',
  status tinyint not null default 1 comment '状态：1启用，0禁用，-1删除',
  created_at datetime not null default current_timestamp comment '创建时间',
  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  unique key uk_category_filter_option (group_key, option_key),
  key idx_category_filter_sort (status, group_sort_order, option_sort_order)
) engine=InnoDB default charset=utf8mb4 comment='前端分类页筛选配置表';

-- 短剧主表
create table drama (
  id bigint primary key auto_increment comment '短剧ID',
  title varchar(128) not null comment '短剧标题',
  description varchar(1000) comment '短剧简介',
  author_name varchar(64) comment '作者/出品方名称',
  cover_url varchar(1000) comment '短剧封面图片URL',
  cover_object_key varchar(255) comment '短剧封面 R2 对象 key (用于级联删除)',
  horizontal_cover_url varchar(1000) comment '横版封面图片URL',
  horizontal_cover_object_key varchar(255) comment '横版封面 R2 对象 key (用于级联删除)',
  vertical_cover_url varchar(1000) comment '竖版封面图片URL',
  vertical_cover_object_key varchar(255) comment '竖版封面 R2 对象 key (用于级联删除)',
  tags varchar(255) comment '短剧标签，英文逗号分隔',
  free_episode_count int not null default 0 comment '免费观看的前N集数量',
  total_episodes int not null default 0 comment '总集数',
  episode_price_points int not null default 10 comment '默认单集解锁积分',
  whole_price_points int not null default 0 comment '整剧购买积分，0表示不启用',
  content_type varchar(16) not null default 'real' comment '内容类型：ai AI剧，real真人剧',
  background varchar(32) not null default 'modern' comment '分类筛选：背景',
  theme varchar(32) not null default 'romance' comment '分类筛选：主题',
  setting_key varchar(32) not null default 'ordinary' comment '分类筛选：设定',
  audience varchar(16) not null default 'female' comment '分类筛选：受众',
  publish_date date comment '分类筛选：上新日期',
  online_time datetime comment '上线时间',
  hot_score int not null default 0 comment '推荐排序：热度值',
  like_count int not null default 0 comment '点赞总数',
  recommended tinyint not null default 0 comment '是否推荐：1推荐，0否',
  status tinyint not null default 1 comment '状态：1上架，0下架，-1删除',
  sort_order int not null default 0 comment '排序值，越小越靠前',
  created_at datetime not null default current_timestamp comment '创建时间',
  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  key idx_drama_content_type (status, content_type, sort_order),
  key idx_drama_filters (status, background, theme, setting_key, audience, publish_date),
  key idx_drama_hot (status, hot_score, id),
  key idx_drama_recommend (status, recommended, sort_order),
  key idx_drama_online (status, online_time)
) engine=InnoDB default charset=utf8mb4 comment='短剧主表';

-- 短剧分集表
create table drama_episode (
  id bigint primary key auto_increment comment '分集ID',
  drama_id bigint not null comment '所属短剧ID',
  episode_no int not null comment '第几集，从1开始',
  title varchar(128) not null comment '分集标题',
  description varchar(1000) comment '分集简介',
  cover_url varchar(1000) comment '分集封面图片URL',
  cover_object_key varchar(255) comment '分集封面 R2 对象 key (用于级联删除)',
  video_url varchar(1000) not null comment '可播放视频地址，支持真实m3u8或mp4链接',
  cloudflare_uid varchar(64) comment 'Cloudflare Stream 视频 UID (cloudflare 存储模式)',
  hls_url varchar(1000) comment 'Cloudflare Stream HLS 播放列表 URL (webhook 异步回写)',
  transcode_status tinyint not null default 0 comment '转码状态：0待处理，1转码中，2完成，-1失败',
  price_points int not null default 1 comment '解锁本集需要消耗的积分，0表示免费',
  duration_seconds int not null default 0 comment '视频时长，单位秒',
  is_free tinyint not null default 0 comment '是否免费试看：1免费，0付费',
  access_type varchar(16) not null default 'POINTS' comment '观看权限：FREE免费，POINTS积分解锁，VIP会员专享',
  sort_order int not null default 0 comment '排序值，越小越靠前',
  storage_provider varchar(16) not null default 'oss' comment '视频存储来源，例如oss、cos或url',
  status tinyint not null default 1 comment '状态：1上架，0下架，-1删除',
  created_at datetime not null default current_timestamp comment '创建时间',
  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  unique key uk_episode_no (drama_id, episode_no),
  key idx_episode_drama (drama_id, status, episode_no),
  key idx_episode_sort (drama_id, status, sort_order),
  constraint fk_episode_drama foreign key (drama_id) references drama(id)
) engine=InnoDB default charset=utf8mb4 comment='短剧分集表';

-- ============================================================================
-- 模块三：用户行为 (User Activity)
-- 包含：收藏、观看历史、播放事件、分集解锁
-- ============================================================================

-- 用户收藏短剧表
create table user_favorite (
  id bigint primary key auto_increment comment '收藏ID',
  user_id bigint not null comment '用户ID',
  drama_id bigint not null comment '短剧ID',
  created_at datetime not null default current_timestamp comment '收藏时间',
  unique key uk_favorite (user_id, drama_id),
  key idx_favorite_user (user_id, created_at),
  constraint fk_favorite_user foreign key (user_id) references app_user(id),
  constraint fk_favorite_drama foreign key (drama_id) references drama(id)
) engine=InnoDB default charset=utf8mb4 comment='用户收藏短剧表';

-- 用户观看历史表
create table user_watch_history (
  id bigint primary key auto_increment comment '观看历史ID',
  user_id bigint not null comment '用户ID',
  drama_id bigint not null comment '短剧ID',
  episode_id bigint not null comment '最近观看分集ID',
  progress_seconds int not null default 0 comment '最近观看进度，单位秒',
  created_at datetime not null default current_timestamp comment '首次观看时间',
  updated_at datetime not null default current_timestamp on update current_timestamp comment '最近观看时间',
  unique key uk_history (user_id, drama_id),
  key idx_history_user (user_id, updated_at),
  constraint fk_history_user foreign key (user_id) references app_user(id),
  constraint fk_history_drama foreign key (drama_id) references drama(id),
  constraint fk_history_episode foreign key (episode_id) references drama_episode(id)
) engine=InnoDB default charset=utf8mb4 comment='用户观看历史表';

-- 分集播放事件明细表
create table episode_play_event (
  id bigint primary key auto_increment comment '播放事件ID',
  user_id bigint not null comment '用户ID',
  drama_id bigint not null comment '短剧ID',
  episode_id bigint not null comment '分集ID',
  progress_seconds int not null default 0 comment '播放进度，单位秒',
  event_type varchar(16) not null default 'PROGRESS' comment '事件类型：START开始、PROGRESS进度、COMPLETE完成',
  created_at datetime not null default current_timestamp comment '事件发生时间',
  key idx_play_event_drama (drama_id, created_at),
  key idx_play_event_episode (episode_id, created_at),
  key idx_play_event_user (user_id, created_at),
  constraint fk_play_event_user foreign key (user_id) references app_user(id),
  constraint fk_play_event_drama foreign key (drama_id) references drama(id),
  constraint fk_play_event_episode foreign key (episode_id) references drama_episode(id)
) engine=InnoDB default charset=utf8mb4 comment='分集播放事件明细表';

-- 用户分集解锁记录表
create table user_episode_unlock (
  id bigint primary key auto_increment comment '解锁记录ID',
  user_id bigint not null comment '用户ID',
  drama_id bigint not null comment '短剧ID',
  episode_id bigint comment '分集ID，为空时表示整剧或特殊权益解锁',
  unlock_type varchar(16) not null comment '解锁类型：FREE免费、POINT积分、VIP会员等',
  points_cost int not null default 0 comment '本次解锁消耗积分',
  created_at datetime not null default current_timestamp comment '解锁时间',
  unique key uk_unlock_episode (user_id, episode_id),
  key idx_unlock_drama (user_id, drama_id),
  constraint fk_unlock_user foreign key (user_id) references app_user(id),
  constraint fk_unlock_drama foreign key (drama_id) references drama(id),
  constraint fk_unlock_episode foreign key (episode_id) references drama_episode(id)
) engine=InnoDB default charset=utf8mb4 comment='用户分集解锁记录表';

-- ============================================================================
-- 模块四：积分与交易 (Points & Trade)
-- 包含：积分流水、签到记录、积分商品、用户订单、广告激励
-- ============================================================================

-- 用户每日签到记录表
create table user_checkin (
  id bigint primary key auto_increment comment '签到ID',
  user_id bigint not null comment '用户ID',
  checkin_date date not null comment '签到日期',
  point_delta int not null comment '签到获得积分',
  created_at datetime not null default current_timestamp comment '签到时间',
  unique key uk_checkin (user_id, checkin_date),
  constraint fk_checkin_user foreign key (user_id) references app_user(id)
) engine=InnoDB default charset=utf8mb4 comment='用户每日签到记录表';

-- 用户积分流水表
create table point_record (
  id bigint primary key auto_increment comment '积分流水ID',
  user_id bigint not null comment '用户ID',
  delta int not null comment '积分变动值，正数增加，负数扣减',
  biz_type varchar(32) not null comment '业务类型，例如CHECKIN、UNLOCK、ORDER_PAY、ADMIN_ADJUST、AD_REWARD',
  biz_id varchar(64) comment '业务单据ID或外部流水号',
  remark varchar(255) comment '流水备注',
  created_at datetime not null default current_timestamp comment '发生时间',
  key idx_point_user (user_id, id),
  constraint fk_point_user foreign key (user_id) references app_user(id)
) engine=InnoDB default charset=utf8mb4 comment='用户积分流水表';

-- 积分充值商品表
create table point_product (
  id bigint primary key auto_increment comment '积分商品ID',
  name varchar(64) not null comment '商品名称',
  points int not null comment '购买获得基础积分',
  bonus_points int not null default 0 comment '赠送积分',
  price_cents int not null comment '售价，单位为分',
  currency varchar(8) not null default 'USD' comment '币种，例如USD、EUR、CNY',
  store_product_id varchar(128) comment '应用商店商品ID (Apple productId / Google sku)，用于 IAP/webhook 反查内部商品',
  status tinyint not null default 1 comment '状态：1上架，0下架，-1删除',
  sort_order int not null default 0 comment '排序值，越小越靠前',
  created_at datetime not null default current_timestamp comment '创建时间',
  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  unique key uk_point_product_store_id (store_product_id),
  key idx_point_product_sort (status, sort_order)
) engine=InnoDB default charset=utf8mb4 comment='积分充值商品表';

-- 用户订单表
create table user_order (
  id bigint primary key auto_increment comment '订单ID',
  order_no varchar(64) not null comment '订单号，业务唯一',
  user_id bigint not null comment '下单用户ID',
  product_id bigint comment '积分商品ID，非商品订单可为空',
  order_type varchar(32) not null comment '订单类型，例如POINT_RECHARGE积分充值',
  pay_channel varchar(32) comment '支付渠道，例如APPLE_IAP/GOOGLE_PLAY/STRIPE/mock',
  points int not null default 0 comment '订单到账积分，含赠送积分',
  amount_cents int not null default 0 comment '订单金额，单位为分',
  currency varchar(8) not null default 'USD' comment '币种',
  status varchar(16) not null default 'PENDING' comment '订单状态：PENDING待支付、PAID已支付、REFUNDED已退款、CANCELLED已取消、CLOSED已关闭',
  paid_at datetime comment '支付完成时间',
  store_transaction_id varchar(128) comment '应用商店交易ID (Apple transactionId / Stripe charge_id),幂等去重',
  store_original_transaction_id varchar(128) comment 'Apple originalTransactionId / Stripe payment_intent_id,订阅生命周期不变',
  store_product_id varchar(128) comment '应用商店侧商品ID (Apple productId / Google sku / Stripe price_id)',
  store_bundle_id varchar(128) comment 'Apple bundleId / Google packageName / Stripe account_id',
  store_environment varchar(16) comment '渠道侧环境: SANDBOX/PRODUCTION/TEST/LIVE',
  store_receipt_hash varchar(128) comment '已验证 payload 的 SHA-256 摘要,用于审计去重',
  refund_reason varchar(64) comment '退款原因 (Apple refundReason enum / Stripe refund reason)',
  tax_amount_cents int not null default 0 comment '税额,单位分 (渠道已代扣时记录)',
  stripe_session_id varchar(128) comment 'Stripe Checkout Session ID (cs_test_xxx),创建 Session 时写入',
  stripe_payment_intent_id varchar(128) comment 'Stripe PaymentIntent ID (pi_xxx),支付成功 webhook 写入,幂等',
  google_purchase_token varchar(512) comment 'Google Play Billing purchaseToken (verifyPurchase/RTDN/退款查询)',
  paypal_order_id varchar(128) comment 'PayPal Order ID (已创建的 PayPal 订单号)',
  paypal_payment_id varchar(128) comment 'PayPal Payment ID (支付确认后产生,幂等去重)',
  created_at datetime not null default current_timestamp comment '创建时间',
  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  unique key uk_order_no (order_no),
  unique key uk_order_store_tx (store_transaction_id),
  unique key uk_order_stripe_pi (stripe_payment_intent_id),
  unique key uk_order_google_purchase_token (google_purchase_token),
  unique key uk_order_paypal_payment_id (paypal_payment_id),
  key idx_order_stripe_session (stripe_session_id),
  key idx_order_user (user_id, id),
  key idx_order_status (status, id),
  constraint fk_order_user foreign key (user_id) references app_user(id),
  constraint fk_order_product foreign key (product_id) references point_product(id)
) engine=InnoDB default charset=utf8mb4 comment='用户订单表';

-- 广告激励积分记录表
create table ad_reward_record (
  id bigint primary key auto_increment comment '广告激励记录ID',
  user_id bigint not null comment '用户ID',
  ad_slot varchar(64) not null comment '广告位标识',
  trace_id varchar(128) not null comment '广告平台回调或客户端上报唯一追踪ID',
  point_delta int not null comment '本次激励发放积分',
  created_at datetime not null default current_timestamp comment '发放时间',
  unique key uk_ad_user_trace (user_id, trace_id),
  key idx_ad_user (user_id, id),
  constraint fk_ad_user foreign key (user_id) references app_user(id)
) engine=InnoDB default charset=utf8mb4 comment='广告激励积分记录表';

-- ============================================================================
-- 模块五：系统 (System)
-- 包含：操作日志
-- ============================================================================

-- 后台操作日志表
create table operation_log (
  id bigint primary key auto_increment comment '日志ID',
  admin_id bigint comment '操作管理员ID，未识别时为空',
  method varchar(16) not null comment 'HTTP请求方法',
  path varchar(255) not null comment '请求路径',
  status_code int not null default 0 comment '响应状态码',
  ip varchar(64) comment '客户端IP地址',
  created_at datetime not null default current_timestamp comment '操作时间',
  key idx_operation_admin (admin_id, id),
  key idx_operation_path (path, id),
  constraint fk_operation_admin foreign key (admin_id) references admin_user(id)
) engine=InnoDB default charset=utf8mb4 comment='后台操作日志表';

-- ============================================================================
-- 基础示例数据 (Seed Data)
-- ============================================================================

-- 管理员账号 (password_hash 留空,首次启动由 AdminBootstrapRunner 生成随机密码并打印到日志)
-- 运维从启动日志获取初始密码后,务必立即登录后台修改
insert into admin_user(username, password_hash, nickname)
values ('admin', '', '管理员');

-- 系统角色
insert into admin_role(id, name, code, permissions) values
(1, '超级管理员', 'SUPER_ADMIN', '*'),
(2, '内容运营', 'CONTENT_OPERATOR', 'content:manage'),
(3, '用户运营', 'USER_OPERATOR', 'user:manage,order:manage,point:manage,log:view,analytics:view');

-- 管理员-角色绑定
insert into admin_user_role(admin_id, role_id) values (1, 1);

-- 积分充值商品 (store_product_id 需与 App Store Connect / Google Play Console 中配置的 productId/sku 一致)
insert into point_product(id, name, points, bonus_points, price_cents, currency, store_product_id, sort_order) values
(1, '100积分包', 100, 0, 199, 'USD', 'com.duanju.points100', 1),
(2, '500积分包', 500, 60, 799, 'USD', 'com.duanju.points500', 2),
(3, '1200积分包', 1200, 180, 1499, 'USD', 'com.duanju.points1200', 3);

-- 分类筛选项配置
insert into drama_category_filter(group_key, group_label_key, group_sort_order, option_key, option_label_key, option_sort_order) values
('contentType', 'category.contentType', 0, 'ai', 'category.aiDrama', 1),
('contentType', 'category.contentType', 0, 'real', 'category.realDrama', 2),
('background', 'category.background', 1, 'all', 'category.options.all', 0),
('background', 'category.background', 1, 'modern', 'category.options.modern', 1),
('background', 'category.background', 1, 'urban', 'category.options.urban', 2),
('background', 'category.background', 1, 'ancient', 'category.options.ancient', 3),
('background', 'category.background', 1, 'rural', 'category.options.rural', 4),
('background', 'category.background', 1, 'period', 'category.options.period', 5),
('background', 'category.background', 1, 'fantasySpace', 'category.options.fantasySpace', 6),
('background', 'category.background', 1, 'workplace', 'category.options.workplace', 7),
('background', 'category.background', 1, 'republican', 'category.options.republican', 8),
('background', 'category.background', 1, 'campus', 'category.options.campus', 9),
('background', 'category.background', 1, 'palace', 'category.options.palace', 10),
('background', 'category.background', 1, 'island', 'category.options.island', 11),
('theme', 'category.theme', 2, 'all', 'category.options.all', 0),
('theme', 'category.theme', 2, 'romance', 'category.options.romance', 1),
('theme', 'category.theme', 2, 'femaleGrowth', 'category.options.femaleGrowth', 2),
('theme', 'category.theme', 2, 'brainHole', 'category.options.brainHole', 3),
('theme', 'category.theme', 2, 'fantasy', 'category.options.fantasy', 4),
('theme', 'category.theme', 2, 'xuanhuan', 'category.options.xuanhuan', 5),
('theme', 'category.theme', 2, 'ancientRomance', 'category.options.ancientRomance', 6),
('theme', 'category.theme', 2, 'warGod', 'category.options.warGod', 7),
('theme', 'category.theme', 2, 'palaceFight', 'category.options.palaceFight', 8),
('theme', 'category.theme', 2, 'xianxia', 'category.options.xianxia', 9),
('theme', 'category.theme', 2, 'power', 'category.options.power', 10),
('theme', 'category.theme', 2, 'farming', 'category.options.farming', 11),
('theme', 'category.theme', 2, 'ageLove', 'category.options.ageLove', 12),
('theme', 'category.theme', 2, 'suspense', 'category.options.suspense', 13),
('theme', 'category.theme', 2, 'comedy', 'category.options.comedy', 14),
('theme', 'category.theme', 2, 'youth', 'category.options.youth', 15),
('theme', 'category.theme', 2, 'republicanLove', 'category.options.republicanLove', 16),
('setting', 'category.setting', 3, 'all', 'category.options.all', 0),
('setting', 'category.setting', 3, 'revenge', 'category.options.revenge', 1),
('setting', 'category.setting', 3, 'maleLead', 'category.options.maleLead', 2),
('setting', 'category.setting', 3, 'femaleLead', 'category.options.femaleLead', 3),
('setting', 'category.setting', 3, 'hiddenIdentity', 'category.options.hiddenIdentity', 4),
('setting', 'category.setting', 3, 'rebirth', 'category.options.rebirth', 5),
('setting', 'category.setting', 3, 'timeTravel', 'category.options.timeTravel', 6),
('setting', 'category.setting', 3, 'system', 'category.options.system', 7),
('setting', 'category.setting', 3, 'marriageFirst', 'category.options.marriageFirst', 8),
('setting', 'category.setting', 3, 'family', 'category.options.family', 9),
('setting', 'category.setting', 3, 'ordinary', 'category.options.ordinary', 10),
('setting', 'category.setting', 3, 'reunion', 'category.options.reunion', 11),
('setting', 'category.setting', 3, 'tycoon', 'category.options.tycoon', 12),
('setting', 'category.setting', 3, 'wealthy', 'category.options.wealthy', 13),
('setting', 'category.setting', 3, 'comeback', 'category.options.comeback', 14),
('audience', 'category.audience', 4, 'all', 'category.options.all', 0),
('audience', 'category.audience', 4, 'male', 'category.options.male', 1),
('audience', 'category.audience', 4, 'female', 'category.options.female', 2),
('time', 'category.time', 5, 'all', 'category.options.all', 0),
('time', 'category.time', 5, 'd7', 'category.options.d7', 1),
('time', 'category.time', 5, 'd14', 'category.options.d14', 2),
('time', 'category.time', 5, 'd30', 'category.options.d30', 3),
('time', 'category.time', 5, 'd90', 'category.options.d90', 4),
('sort', 'category.sort', 6, 'all', 'category.options.all', 0),
('sort', 'category.sort', 6, 'newest', 'category.options.newest', 1),
('sort', 'category.sort', 6, 'hottest', 'category.options.hottest', 2);

-- 示例短剧
insert into drama(id, title, description, cover_url, horizontal_cover_url, vertical_cover_url, tags,
  free_episode_count, total_episodes, episode_price_points, whole_price_points,
  content_type, background, theme, setting_key, audience, publish_date, online_time, hot_score, recommended, sort_order)
values
(1, '回到古代当太子', '竖屏短剧示例数据，前2集免费，后续分集使用积分解锁。', 'https://sdp003.wanshibao.com:49/m3u8/pic/1a77B6xVZnZRDq-p5qMzR6lGtqvEAqQArAihNz9Ixp-w5lB2qhr1HE6UbwTozBi_8LOhsoevKxw.jpg',
  'https://sdp003.wanshibao.com:49/m3u8/pic/1a77B6xVZnZRDq-p5qMzR6lGtqvEAqQArAihNz9Ixp-w5lB2qhr1HE6UbwTozBi_8LOhsoevKxw.jpg',
  'https://sdp003.wanshibao.com:49/m3u8/pic/1a77B6xVZnZRDq-p5qMzR6lGtqvEAqQArAihNz9Ixp-w5lB2qhr1HE6UbwTozBi_8LOhsoevKxw.jpg',
  '古装,穿越,逆袭', 2, 4, 10, 30,
  'real', 'ancient', 'ancientRomance', 'timeTravel', 'female', current_date, current_timestamp, 1470, 1, 1);

-- 示例分集
insert into drama_episode(drama_id, episode_no, title, description, cover_url, video_url, price_points, duration_seconds, is_free, access_type, sort_order, storage_provider)
values
(1, 1, '第1集', '初入风云', 'https://sdp003.wanshibao.com:49/m3u8/pic/1a77B6xVZnZRDq-p5qMzR6lGtqvEAqQArAihNz9Ixp-w5lB2qhr1HE6UbwTozBi_8LOhsoevKxw.jpg', 'https://sdp003.wanshibao.com:49/m3u8/5cfewiWOsb9LUzdXoF8CY_nJ_nYB-TxjUPg8FEnaGwXdEHWmLOzdcwfxYHH9cMAtsiYJ_eP4pBudI9Q.m3u8', 0, 60, 1, 'FREE', 1, 'oss'),
(1, 2, '第2集', '暗潮涌动', 'https://sdp003.wanshibao.com:49/m3u8/pic/bed1wbZXz-Gjf4qwoKwmJ0y5L8UkWz65whyMJfJaW0XPwapPPr-z-teVOs4cwjJ2rbqlxo2mUWE.jpg', 'https://sdp003.wanshibao.com:49/m3u8/e3132AbBDNItEc6T23Zo_RveoW5xpWSLYANwcPoJuJ3eFKMwUn9srZ5iW6g_Xw1_c3JcOvIHjD4S2rI.m3u8', 0, 60, 1, 'FREE', 2, 'oss'),
(1, 3, '第3集', '初露锋芒', 'https://sdp003.wanshibao.com:49/m3u8/pic/15a5xE-c8eR_k76QA86VgjeHYUCZF_18TvT7QJzLH5HnHvWNlHdmBzIOdWxUj54uY9prCZdXU1o.jpg', 'https://sdp003.wanshibao.com:49/m3u8/82afKKYJ2yza-iPefiofrJ0A6hcJ8UnJcWLvv9RVGoW3FcNqvG3JmrQZy5l00w6VYcIroikyCxD3lII.m3u8', 10, 60, 0, 'POINTS', 3, 'oss'),
(1, 4, '第4集', '危局反转', 'https://sdp003.wanshibao.com:49/m3u8/pic/ec4epSaxrB3IxB3oe6k4F2THW4nbSyKMZHmACkwvMlNmQQYwp7awyhJt-bB8Xey-2uJl8eDz9Sg.jpg', 'https://sdp003.wanshibao.com:49/m3u8/4333NUZUz_Asi9Bq19Fszc_yUKUWSG2Fj04N7-PDWng3xsm3fk4cCg7dqOTY4D4VBoiP3SC6_ZpJiIo.m3u8', 10, 60, 0, 'POINTS', 4, 'oss');

-- ============================================================================
-- 模块六：多币种 (Multi-Currency)
-- 包含：汇率配置表
-- ============================================================================

-- 汇率配置表（基准：USD）
create table currency_rate (
  id bigint primary key auto_increment comment 'ID',
  currency_code varchar(10) not null comment '币种代码，如 CNY, JPY, EUR',
  currency_name varchar(50) not null comment '币种名称',
  locale varchar(20) comment '关联语言代码',
  rate_to_usd decimal(12,6) not null comment '1 USD = X 目标货币',
  symbol varchar(10) not null comment '币种符号，如 ¥, $, €',
  decimals int not null default 2 comment '小数位数',
  enabled tinyint not null default 1 comment '是否启用：1启用，0禁用',
  sort_order int not null default 0 comment '排序',
  created_at datetime not null default current_timestamp comment '创建时间',
  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  unique key uk_currency_code_locale (currency_code, locale)
) engine=InnoDB default charset=utf8mb4 comment='汇率配置表';

-- 初始化数据
insert into currency_rate (currency_code, currency_name, locale, rate_to_usd, symbol, decimals, enabled, sort_order) values
('USD', '美元', 'en', 1.000000, '$', 2, 1, 0),
('CNY', '人民币', NULL, 7.250000, '¥', 2, 1, 1),
('TWD', '新台币', 'zh-Hant', 32.500000, 'NT$', 0, 1, 2),
('JPY', '日元', 'ja', 150.500000, '¥', 0, 1, 3),
('KRW', '韩元', 'ko', 1350.000000, '₩', 0, 1, 4),
('THB', '泰铢', 'th', 35.500000, '฿', 2, 1, 5),
('VND', '越南盾', 'vi', 25400.000000, '₫', 0, 1, 6),
('IDR', '印尼盾', 'id', 16800.000000, 'Rp', 0, 1, 7),
('MYR', '马币', 'ms', 4.700000, 'RM', 2, 1, 8),
('EUR', '欧元', 'es', 0.920000, '€', 2, 1, 9),
('EUR', '欧元', 'fr', 0.920000, '€', 2, 1, 10),
('EUR', '欧元', 'de', 0.920000, '€', 2, 1, 11),
('EUR', '欧元', 'it', 0.920000, '€', 2, 1, 12),
('BRL', '雷亚尔', 'pt', 5.100000, 'R$', 2, 1, 13),
('RUB', '卢布', 'ru', 92.000000, '₽', 2, 1, 14),
('TRY', '土耳其里拉', 'tr', 32.500000, '₺', 2, 1, 15),
('AED', '阿联酋迪拉姆', 'ar', 3.670000, 'د.إ', 2, 1, 16);

-- ============================================================================
-- 模块七：点赞与评论
-- ============================================================================

-- 用户点赞短剧表
create table user_like (
  id bigint primary key auto_increment comment '点赞ID',
  user_id bigint not null comment '用户ID',
  drama_id bigint not null comment '短剧ID',
  created_at datetime not null default current_timestamp comment '点赞时间',
  unique key uk_user_like (user_id, drama_id),
  key idx_like_drama (drama_id),
  constraint fk_like_user foreign key (user_id) references app_user(id),
  constraint fk_like_drama foreign key (drama_id) references drama(id)
) engine=InnoDB default charset=utf8mb4 comment='用户点赞短剧表';

-- 短剧评论表
create table drama_comment (
  id bigint primary key auto_increment comment '评论ID',
  user_id bigint not null comment '评论用户ID',
  drama_id bigint not null comment '短剧ID',
  episode_id bigint null comment '关联分集ID，可空表示整剧',
  content varchar(500) not null comment '评论内容',
  status tinyint not null default 1 comment '状态：1正常，-1删除',
  created_at datetime not null default current_timestamp comment '评论时间',
  key idx_comment_drama (drama_id, status, id),
  key idx_comment_user (user_id),
  constraint fk_comment_user foreign key (user_id) references app_user(id),
  constraint fk_comment_drama foreign key (drama_id) references drama(id)
) engine=InnoDB default charset=utf8mb4 comment='短剧评论表';
