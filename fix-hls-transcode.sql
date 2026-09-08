-- HLS转码功能: 添加 transcode_status 列
-- hls_url 列已存在,无需重复添加

-- 检查 transcode_status 列是否存在,不存在则添加
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'drama_episode'
    AND COLUMN_NAME = 'transcode_status');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE drama_episode ADD COLUMN transcode_status INT DEFAULT 0 COMMENT ''转码状态: 0=未转码 1=转码中 2=完成 -1=失败''',
    'SELECT ''transcode_status column already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查 hls_url 列是否存在,不存在则添加(安全兜底)
SET @hls_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'drama_episode'
    AND COLUMN_NAME = 'hls_url');

SET @sql2 = IF(@hls_exists = 0,
    'ALTER TABLE drama_episode ADD COLUMN hls_url VARCHAR(512) DEFAULT NULL COMMENT ''HLS播放列表URL''',
    'SELECT ''hls_url column already exists''');
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- 验证
SELECT
    COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'duanju'
    AND TABLE_NAME = 'drama_episode'
    AND COLUMN_NAME IN ('hls_url', 'transcode_status');
