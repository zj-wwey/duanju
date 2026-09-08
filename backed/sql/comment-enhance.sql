-- ============================================================
-- 评论系统增强：回复 / 通知 / 管理员控评
-- ============================================================

-- 1) drama_comment 增加评论回复字段
set @col := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'drama_comment' and column_name = 'parent_id');
set @sql := if(@col = 0,
  'alter table drama_comment add column parent_id bigint null comment ''父评论ID（回复时填入）'' after content',
  'select ''drama_comment.parent_id exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @col := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'drama_comment' and column_name = 'reply_to_user_id');
set @sql := if(@col = 0,
  'alter table drama_comment add column reply_to_user_id bigint null comment ''回复目标用户ID'' after parent_id',
  'select ''drama_comment.reply_to_user_id exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @col := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'drama_comment' and column_name = 'root_id');
set @sql := if(@col = 0,
  'alter table drama_comment add column root_id bigint null comment ''根评论ID（同一根下的回复归组）'' after reply_to_user_id',
  'select ''drama_comment.root_id exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @col := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'drama_comment' and column_name = 'reply_count');
set @sql := if(@col = 0,
  'alter table drama_comment add column reply_count int default 0 comment ''回复数（冗余计数）'' after root_id',
  'select ''drama_comment.reply_count exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

-- 2) 用户通知表
set @tbl := (select count(*) from information_schema.tables
  where table_schema = database() and table_name = 'user_notification');
set @sql := if(@tbl = 0, '
  create table user_notification (
    id bigint auto_increment primary key,
    user_id bigint not null comment ''接收通知的用户'',
    type varchar(32) not null comment ''通知类型: comment/reply/like/system'',
    source_user_id bigint null comment ''触发人'',
    drama_id bigint null,
    comment_id bigint null,
    content varchar(500) null comment ''通知摘要'',
    is_read tinyint(1) default 0,
    created_at datetime default current_timestamp,
    key idx_user_read (user_id, is_read),
    key idx_user_created (user_id, created_at)
  ) engine=InnoDB default charset=utf8mb4 comment=''用户通知''',
  'select ''user_notification exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

-- 3) 管理员评论索引加速
set @idx := (select count(*) from information_schema.statistics
  where table_schema = database() and table_name = 'drama_comment' and index_name = 'idx_status_created');
set @sql := if(@idx = 0,
  'alter table drama_comment add index idx_status_created (status, created_at)',
  'select ''idx_status_created exists''');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;
