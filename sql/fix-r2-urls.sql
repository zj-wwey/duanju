-- ============================================================
--  修复 R2 URL 前缀：从 cdn.marastel.com 迁移到 r2.marastel.com
-- ============================================================
--  原因：R2 存储桶已绑定自定义域名 r2.marastel.com
--  旧 URL: https://cdn.marastel.com/r2/images/...  (走 Nginx 代理，502)
--  新 URL: https://r2.marastel.com/images/...        (直连 R2，无需 Nginx)
-- ============================================================

SELECT '=== 修复前 URL 检查 ===' AS info;

-- 检查 drama 表的 cover_url
SELECT id, title, LEFT(cover_url, 80) AS cover_url_short FROM drama WHERE cover_url LIKE '%cdn.marastel.com%' LIMIT 10;

-- 检查 drama_episode 表的 video_url
SELECT id, drama_id, episode_no, LEFT(video_url, 80) AS video_url_short FROM drama_episode WHERE video_url LIKE '%cdn.marastel.com%' LIMIT 10;

-- ============================================================
-- 1. 修复 drama.cover_url
--    https://cdn.marastel.com/r2/images/... → https://r2.marastel.com/images/...
--    https://cdn.marastel.com/images/...    → https://r2.marastel.com/images/...
-- ============================================================
UPDATE drama
SET cover_url = REPLACE(cover_url, 'https://cdn.marastel.com/r2/', 'https://r2.marastel.com/')
WHERE cover_url LIKE 'https://cdn.marastel.com/r2/%';

UPDATE drama
SET cover_url = REPLACE(cover_url, 'https://cdn.marastel.com/images/', 'https://r2.marastel.com/images/')
WHERE cover_url LIKE 'https://cdn.marastel.com/images/%';

UPDATE drama
SET cover_url = REPLACE(cover_url, 'https://cdn.marastel.com/avatars/', 'https://r2.marastel.com/avatars/')
WHERE cover_url LIKE 'https://cdn.marastel.com/avatars/%';

UPDATE drama
SET cover_url = REPLACE(cover_url, 'https://cdn.marastel.com/videos/', 'https://r2.marastel.com/videos/')
WHERE cover_url LIKE 'https://cdn.marastel.com/videos/%';

-- ============================================================
-- 2. 修复 drama_episode.video_url
-- ============================================================
UPDATE drama_episode
SET video_url = REPLACE(video_url, 'https://cdn.marastel.com/r2/', 'https://r2.marastel.com/')
WHERE video_url LIKE 'https://cdn.marastel.com/r2/%';

UPDATE drama_episode
SET video_url = REPLACE(video_url, 'https://cdn.marastel.com/videos/', 'https://r2.marastel.com/videos/')
WHERE video_url LIKE 'https://cdn.marastel.com/videos/%';

UPDATE drama_episode
SET video_url = REPLACE(video_url, 'https://cdn.marastel.com/images/', 'https://r2.marastel.com/images/')
WHERE video_url LIKE 'https://cdn.marastel.com/images/%';

-- ============================================================
-- 3. 修复 user.avatar (如果有)
-- ============================================================
UPDATE user
SET avatar = REPLACE(avatar, 'https://cdn.marastel.com/r2/', 'https://r2.marastel.com/')
WHERE avatar LIKE 'https://cdn.marastel.com/r2/%';

UPDATE user
SET avatar = REPLACE(avatar, 'https://cdn.marastel.com/avatars/', 'https://r2.marastel.com/avatars/')
WHERE avatar LIKE 'https://cdn.marastel.com/avatars/%';

-- ============================================================
-- 4. 修复其他可能的 URL 字段 (drama backdrop_url 等)
-- ============================================================
UPDATE drama
SET backdrop_url = REPLACE(backdrop_url, 'https://cdn.marastel.com/r2/', 'https://r2.marastel.com/')
WHERE backdrop_url LIKE 'https://cdn.marastel.com/r2/%';

UPDATE drama
SET backdrop_url = REPLACE(backdrop_url, 'https://cdn.marastel.com/images/', 'https://r2.marastel.com/images/')
WHERE backdrop_url LIKE 'https://cdn.marastel.com/images/%';

-- ============================================================
-- 验证修复结果
-- ============================================================
SELECT '=== 修复后 URL 检查 ===' AS info;

SELECT 'drama.cover_url 残留检查' AS check_item, COUNT(*) AS cnt FROM drama WHERE cover_url LIKE '%cdn.marastel.com%';
SELECT 'drama_episode.video_url 残留检查' AS check_item, COUNT(*) AS cnt FROM drama_episode WHERE video_url LIKE '%cdn.marastel.com%';

SELECT '--- drama.cover_url 示例 ---' AS info;
SELECT id, title, LEFT(cover_url, 80) AS cover_url_short FROM drama WHERE cover_url IS NOT NULL LIMIT 5;

SELECT '--- drama_episode.video_url 示例 ---' AS info;
SELECT id, drama_id, episode_no, LEFT(video_url, 80) AS video_url_short FROM drama_episode WHERE video_url IS NOT NULL LIMIT 5;

SELECT '=== R2 URL 修复完成 ===' AS info;
