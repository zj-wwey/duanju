-- ============================================================
--  L4 修复：清理数据库中旧的 /r2/ URL 前缀
-- ============================================================
--  执行前请务必先备份数据库！
--  用法：mysql -uroot -p duanju < fix-r2-url-prefix.sql
-- ============================================================

-- drama 表：三列封面 URL
UPDATE drama
SET
  cover_url            = REPLACE(cover_url, '/r2/', '/'),
  horizontal_cover_url = REPLACE(horizontal_cover_url, '/r2/', '/'),
  vertical_cover_url   = REPLACE(vertical_cover_url, '/r2/', '/')
WHERE cover_url            LIKE '%/r2/%'
   OR horizontal_cover_url LIKE '%/r2/%'
   OR vertical_cover_url   LIKE '%/r2/%';

-- drama_episode 表：封面 + 视频 URL
UPDATE drama_episode
SET
  cover_url = REPLACE(cover_url, '/r2/', '/'),
  video_url = REPLACE(video_url, '/r2/', '/')
WHERE cover_url LIKE '%/r2/%'
   OR video_url LIKE '%/r2/%';

-- app_user 表：用户头像 URL
UPDATE app_user
SET avatar_url = REPLACE(avatar_url, '/r2/', '/')
WHERE avatar_url LIKE '%/r2/%';

-- admin_user 表：管理员头像 URL
UPDATE admin_user
SET avatar = REPLACE(avatar, '/r2/', '/')
WHERE avatar LIKE '%/r2/%';

-- ============================================================
--  验证：查询是否还有残留的 /r2/ 前缀
-- ============================================================
SELECT
  (SELECT COUNT(*) FROM drama WHERE cover_url LIKE '%/r2/%') AS drama_cover_r2,
  (SELECT COUNT(*) FROM drama WHERE horizontal_cover_url LIKE '%/r2/%') AS drama_horizontal_r2,
  (SELECT COUNT(*) FROM drama WHERE vertical_cover_url LIKE '%/r2/%') AS drama_vertical_r2,
  (SELECT COUNT(*) FROM drama_episode WHERE cover_url LIKE '%/r2/%') AS ep_cover_r2,
  (SELECT COUNT(*) FROM drama_episode WHERE video_url LIKE '%/r2/%') AS ep_video_r2,
  (SELECT COUNT(*) FROM app_user WHERE avatar_url LIKE '%/r2/%') AS user_avatar_r2,
  (SELECT COUNT(*) FROM admin_user WHERE avatar LIKE '%/r2/%') AS admin_avatar_r2;
