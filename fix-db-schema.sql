-- ============================================================
--  修复 drama_episode 表结构：补全缺失的列
-- ============================================================
--  问题：DramaEpisodeMapper.insertEpisode 插入了 video_duration 列，
--  但 backed/sql/init.sql 建表时没有这个列，导致插入报错。
--  用法：mysql -uroot -p duanju < fix-db-schema.sql
-- ============================================================

-- 1. 检查当前表结构（执行后看输出，确认哪些列缺失）
SELECT '=== drama_episode 表结构 ===' AS info;
SHOW COLUMNS FROM drama_episode;

-- 2. 补全 video_duration 列（如果不存在）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'drama_episode' AND column_name = 'video_duration') = 0,
    'ALTER TABLE drama_episode ADD COLUMN video_duration int COMMENT ''Cloudflare Stream 视频时长(秒)''',
    'SELECT ''video_duration 列已存在，跳过'' AS result'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 补全 hls_url 列（如果不存在，duanju-api 版本有但 backed 版本可能没有）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'drama_episode' AND column_name = 'hls_url') = 0,
    'ALTER TABLE drama_episode ADD COLUMN hls_url varchar(1000) COMMENT ''HLS 播放地址''',
    'SELECT ''hls_url 列已存在，跳过'' AS result'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. 验证修复结果
SELECT '=== 修复后表结构 ===' AS info;
SHOW COLUMNS FROM drama_episode;

-- 5. 检查是否还有其他可能缺失的列
SELECT '=== 列完整性检查 ===' AS info;
SELECT
  MAX(CASE WHEN column_name = 'id' THEN '✓' ELSE '✗' END) AS id,
  MAX(CASE WHEN column_name = 'drama_id' THEN '✓' ELSE '✗' END) AS drama_id,
  MAX(CASE WHEN column_name = 'episode_no' THEN '✓' ELSE '✗' END) AS episode_no,
  MAX(CASE WHEN column_name = 'title' THEN '✓' ELSE '✗' END) AS title,
  MAX(CASE WHEN column_name = 'video_url' THEN '✓' ELSE '✗' END) AS video_url,
  MAX(CASE WHEN column_name = 'cover_url' THEN '✓' ELSE '✗' END) AS cover_url,
  MAX(CASE WHEN column_name = 'cover_object_key' THEN '✓' ELSE '✗' END) AS cover_object_key,
  MAX(CASE WHEN column_name = 'cloudflare_uid' THEN '✓' ELSE '✗' END) AS cloudflare_uid,
  MAX(CASE WHEN column_name = 'hls_url' THEN '✓' ELSE '✗' END) AS hls_url,
  MAX(CASE WHEN column_name = 'price_points' THEN '✓' ELSE '✗' END) AS price_points,
  MAX(CASE WHEN column_name = 'duration_seconds' THEN '✓' ELSE '✗' END) AS duration_seconds,
  MAX(CASE WHEN column_name = 'video_duration' THEN '✓' ELSE '✗' END) AS video_duration,
  MAX(CASE WHEN column_name = 'is_free' THEN '✓' ELSE '✗' END) AS is_free,
  MAX(CASE WHEN column_name = 'access_type' THEN '✓' ELSE '✗' END) AS access_type,
  MAX(CASE WHEN column_name = 'sort_order' THEN '✓' ELSE '✗' END) AS sort_order,
  MAX(CASE WHEN column_name = 'storage_provider' THEN '✓' ELSE '✗' END) AS storage_provider,
  MAX(CASE WHEN column_name = 'status' THEN '✓' ELSE '✗' END) AS status
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'drama_episode';
