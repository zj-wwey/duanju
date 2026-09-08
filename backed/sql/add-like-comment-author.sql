-- =====================================================================
-- 点赞 + 评论 + 作者字段 迁移脚本
-- 在 MySQL 容器内对 duanju 库执行一次；幂等性：like_count/author_name 用
-- 条件判断，表存在则跳过。重复执行安全。
-- =====================================================================

-- 1) drama 表新增作者与点赞数字段
set @col := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'drama' and column_name = 'author_name');
set @sql := if(@col = 0,
  'alter table drama add column author_name varchar(64) null comment ''作者/出品方名称'' after description',
  'select ''drama.author_name exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @col := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'drama' and column_name = 'like_count');
set @sql := if(@col = 0,
  'alter table drama add column like_count int not null default 0 comment ''点赞总数'' after hot_score',
  'select ''drama.like_count exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

-- 2) 用户点赞表
set @tbl := (select count(*) from information_schema.tables
  where table_schema = database() and table_name = 'user_like');
set @sql := if(@tbl = 0,
  'create table user_like (
    id bigint primary key auto_increment comment ''点赞ID'',
    user_id bigint not null comment ''用户ID'',
    drama_id bigint not null comment ''短剧ID'',
    created_at datetime not null default current_timestamp comment ''点赞时间'',
    unique key uk_user_like (user_id, drama_id),
    key idx_like_drama (drama_id),
    constraint fk_like_user foreign key (user_id) references app_user(id),
    constraint fk_like_drama foreign key (drama_id) references drama(id)
  ) engine=InnoDB default charset=utf8mb4 comment=''用户点赞短剧表''',
  'select ''user_like exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

-- 3) 短剧评论表
set @tbl := (select count(*) from information_schema.tables
  where table_schema = database() and table_name = 'drama_comment');
set @sql := if(@tbl = 0,
  'create table drama_comment (
    id bigint primary key auto_increment comment ''评论ID'',
    user_id bigint not null comment ''评论用户ID'',
    drama_id bigint not null comment ''短剧ID'',
    episode_id bigint null comment ''关联分集ID，可空表示整剧'',
    content varchar(500) not null comment ''评论内容'',
    status tinyint not null default 1 comment ''状态：1正常，-1删除'',
    created_at datetime not null default current_timestamp comment ''评论时间'',
    key idx_comment_drama (drama_id, status, id),
    key idx_comment_user (user_id),
    constraint fk_comment_user foreign key (user_id) references app_user(id),
    constraint fk_comment_drama foreign key (drama_id) references drama(id)
  ) engine=InnoDB default charset=utf8mb4 comment=''短剧评论表''',
  'select ''drama_comment exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

-- 4) 用已有收藏数据回填一个初始点赞量（可选，让列表不至于全 0）
update drama d
set d.like_count = (
  select count(*) from user_favorite f where f.drama_id = d.id
);
