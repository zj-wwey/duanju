-- ============================================================
--  修复 R2 URL 前缀：给被误删 /r2/ 的 URL 加回来
--  Nginx CDN 路由: cdn.marastel.com/r2/ → R2 bucket
--  没有带 /r2/ 前缀的 URL 匹配不到 Nginx location /r2/，会 404
-- ============================================================

-- dramas 表
UPDATE dramas
  SET cover_url = REPLACE(cover_url, 'https://cdn.marastel.com/', 'https://cdn.marastel.com/r2/')
  WHERE cover_url LIKE 'https://cdn.marastel.com/%'
    AND cover_url NOT LIKE 'https://cdn.marastel.com/r2/%'
    AND cover_url IS NOT NULL;

UPDATE dramas
  SET horizontal_cover_url = REPLACE(horizontal_cover_url, 'https://cdn.marastel.com/', 'https://cdn.marastel.com/r2/')
  WHERE horizontal_cover_url LIKE 'https://cdn.marastel.com/%'
    AND horizontal_cover_url NOT LIKE 'https://cdn.marastel.com/r2/%'
    AND horizontal_cover_url IS NOT NULL;

UPDATE dramas
  SET vertical_cover_url = REPLACE(vertical_cover_url, 'https://cdn.marastel.com/', 'https://cdn.marastel.com/r2/')
  WHERE vertical_cover_url LIKE 'https://cdn.marastel.com/%'
    AND vertical_cover_url NOT LIKE 'https://cdn.marastel.com/r2/%'
    AND vertical_cover_url IS NOT NULL;

-- drama_episodes 表
UPDATE drama_episodes
  SET video_url = REPLACE(video_url, 'https://cdn.marastel.com/', 'https://cdn.marastel.com/r2/')
  WHERE video_url LIKE 'https://cdn.marastel.com/%'
    AND video_url NOT LIKE 'https://cdn.marastel.com/r2/%'
    AND video_url IS NOT NULL;

UPDATE drama_episodes
  SET cover_url = REPLACE(cover_url, 'https://cdn.marastel.com/', 'https://cdn.marastel.com/r2/')
  WHERE cover_url LIKE 'https://cdn.marastel.com/%'
    AND cover_url NOT LIKE 'https://cdn.marastel.com/r2/%'
    AND cover_url IS NOT NULL;

-- users 表（头像）
UPDATE users
  SET avatar_url = REPLACE(avatar_url, 'https://cdn.marastel.com/', 'https://cdn.marastel.com/r2/')
  WHERE avatar_url LIKE 'https://cdn.marastel.com/%'
    AND avatar_url NOT LIKE 'https://cdn.marastel.com/r2/%'
    AND avatar_url IS NOT NULL;

-- 验证结果
SELECT 'dramas.cover_url' AS tbl, COUNT(*) AS fixed FROM dramas WHERE cover_url LIKE 'https://cdn.marastel.com/r2/%'
UNION ALL
SELECT 'dramas.horizontal_cover_url', COUNT(*) FROM dramas WHERE horizontal_cover_url LIKE 'https://cdn.marastel.com/r2/%'
UNION ALL
SELECT 'dramas.vertical_cover_url', COUNT(*) FROM dramas WHERE vertical_cover_url LIKE 'https://cdn.marastel.com/r2/%'
UNION ALL
SELECT 'drama_episodes.video_url', COUNT(*) FROM drama_episodes WHERE video_url LIKE 'https://cdn.marastel.com/r2/%'
UNION ALL
SELECT 'drama_episodes.cover_url', COUNT(*) FROM drama_episodes WHERE cover_url LIKE 'https://cdn.marastel.com/r2/%'
UNION ALL
SELECT 'users.avatar_url', COUNT(*) FROM users WHERE avatar_url LIKE 'https://cdn.marastel.com/r2/%';
