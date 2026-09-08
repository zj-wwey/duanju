-- ============================================================
-- 完整修复 R2 URL：cdn.marastel.com/r2/ → r2.marastel.com/
-- 覆盖所有存储 CDN URL 的表和字段
-- ============================================================

SELECT '=== 修复前：残留旧 URL 统计 ===' AS info;

SELECT 'drama.cover_url' AS field, COUNT(*) AS cnt FROM drama WHERE cover_url LIKE '%cdn.marastel.com%';
SELECT 'drama.horizontal_cover_url' AS field, COUNT(*) AS cnt FROM drama WHERE horizontal_cover_url LIKE '%cdn.marastel.com%';
SELECT 'drama.vertical_cover_url' AS field, COUNT(*) AS cnt FROM drama WHERE vertical_cover_url LIKE '%cdn.marastel.com%';
SELECT 'drama_episode.cover_url' AS field, COUNT(*) AS cnt FROM drama_episode WHERE cover_url LIKE '%cdn.marastel.com%';
SELECT 'drama_episode.video_url' AS field, COUNT(*) AS cnt FROM drama_episode WHERE video_url LIKE '%cdn.marastel.com%';
SELECT 'drama_episode.hls_url' AS field, COUNT(*) AS cnt FROM drama_episode WHERE hls_url LIKE '%cdn.marastel.com%';
SELECT 'app_user.avatar_url' AS field, COUNT(*) AS cnt FROM app_user WHERE avatar_url LIKE '%cdn.marastel.com%';
SELECT 'admin_user.avatar' AS field, COUNT(*) AS cnt FROM admin_user WHERE avatar LIKE '%cdn.marastel.com%';
SELECT 'point_product.cover_url' AS field, COUNT(*) AS cnt FROM point_product WHERE cover_url LIKE '%cdn.marastel.com%';
SELECT 'point_shop_item.image_url' AS field, COUNT(*) AS cnt FROM point_shop_item WHERE image_url LIKE '%cdn.marastel.com%';

-- ============================================================
-- 1. drama 表
-- ============================================================

-- cover_url
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

-- horizontal_cover_url
UPDATE drama
SET horizontal_cover_url = REPLACE(horizontal_cover_url, 'https://cdn.marastel.com/r2/', 'https://r2.marastel.com/')
WHERE horizontal_cover_url LIKE 'https://cdn.marastel.com/r2/%';

UPDATE drama
SET horizontal_cover_url = REPLACE(horizontal_cover_url, 'https://cdn.marastel.com/images/', 'https://r2.marastel.com/images/')
WHERE horizontal_cover_url LIKE 'https://cdn.marastel.com/images/%';

UPDATE drama
SET horizontal_cover_url = REPLACE(horizontal_cover_url, 'https://cdn.marastel.com/avatars/', 'https://r2.marastel.com/avatars/')
WHERE horizontal_cover_url LIKE 'https://cdn.marastel.com/avatars/%';

-- vertical_cover_url
UPDATE drama
SET vertical_cover_url = REPLACE(vertical_cover_url, 'https://cdn.marastel.com/r2/', 'https://r2.marastel.com/')
WHERE vertical_cover_url LIKE 'https://cdn.marastel.com/r2/%';

UPDATE drama
SET vertical_cover_url = REPLACE(vertical_cover_url, 'https://cdn.marastel.com/images/', 'https://r2.marastel.com/images/')
WHERE vertical_cover_url LIKE 'https://cdn.marastel.com/images/%';

UPDATE drama
SET vertical_cover_url = REPLACE(vertical_cover_url, 'https://cdn.marastel.com/avatars/', 'https://r2.marastel.com/avatars/')
WHERE vertical_cover_url LIKE 'https://cdn.marastel.com/avatars/%';

-- ============================================================
-- 2. drama_episode 表
-- ============================================================

-- cover_url
UPDATE drama_episode
SET cover_url = REPLACE(cover_url, 'https://cdn.marastel.com/r2/', 'https://r2.marastel.com/')
WHERE cover_url LIKE 'https://cdn.marastel.com/r2/%';

UPDATE drama_episode
SET cover_url = REPLACE(cover_url, 'https://cdn.marastel.com/images/', 'https://r2.marastel.com/images/')
WHERE cover_url LIKE 'https://cdn.marastel.com/images/%';

UPDATE drama_episode
SET cover_url = REPLACE(cover_url, 'https://cdn.marastel.com/avatars/', 'https://r2.marastel.com/avatars/')
WHERE cover_url LIKE 'https://cdn.marastel.com/avatars/%';

-- video_url
UPDATE drama_episode
SET video_url = REPLACE(video_url, 'https://cdn.marastel.com/r2/', 'https://r2.marastel.com/')
WHERE video_url LIKE 'https://cdn.marastel.com/r2/%';

UPDATE drama_episode
SET video_url = REPLACE(video_url, 'https://cdn.marastel.com/videos/', 'https://r2.marastel.com/videos/')
WHERE video_url LIKE 'https://cdn.marastel.com/videos/%';

UPDATE drama_episode
SET video_url = REPLACE(video_url, 'https://cdn.marastel.com/images/', 'https://r2.marastel.com/images/')
WHERE video_url LIKE 'https://cdn.marastel.com/images/%';

-- hls_url
UPDATE drama_episode
SET hls_url = REPLACE(hls_url, 'https://cdn.marastel.com/r2/', 'https://r2.marastel.com/')
WHERE hls_url LIKE 'https://cdn.marastel.com/r2/%';

UPDATE drama_episode
SET hls_url = REPLACE(hls_url, 'https://cdn.marastel.com/videos/', 'https://r2.marastel.com/videos/')
WHERE hls_url LIKE 'https://cdn.marastel.com/videos/%';

UPDATE drama_episode
SET hls_url = REPLACE(hls_url, 'https://cdn.marastel.com/hls/', 'https://r2.marastel.com/hls/')
WHERE hls_url LIKE 'https://cdn.marastel.com/hls/%';

-- ============================================================
-- 3. app_user 表（用户头像）
-- ============================================================

UPDATE app_user
SET avatar_url = REPLACE(avatar_url, 'https://cdn.marastel.com/r2/', 'https://r2.marastel.com/')
WHERE avatar_url LIKE 'https://cdn.marastel.com/r2/%';

UPDATE app_user
SET avatar_url = REPLACE(avatar_url, 'https://cdn.marastel.com/avatars/', 'https://r2.marastel.com/avatars/')
WHERE avatar_url LIKE 'https://cdn.marastel.com/avatars/%';

UPDATE app_user
SET avatar_url = REPLACE(avatar_url, 'https://cdn.marastel.com/images/', 'https://r2.marastel.com/images/')
WHERE avatar_url LIKE 'https://cdn.marastel.com/images/%';

-- ============================================================
-- 4. admin_user 表（管理员头像）
-- ============================================================

UPDATE admin_user
SET avatar = REPLACE(avatar, 'https://cdn.marastel.com/r2/', 'https://r2.marastel.com/')
WHERE avatar LIKE 'https://cdn.marastel.com/r2/%';

UPDATE admin_user
SET avatar = REPLACE(avatar, 'https://cdn.marastel.com/avatars/', 'https://r2.marastel.com/avatars/')
WHERE avatar LIKE 'https://cdn.marastel.com/avatars/%';

UPDATE admin_user
SET avatar = REPLACE(avatar, 'https://cdn.marastel.com/images/', 'https://r2.marastel.com/images/')
WHERE avatar LIKE 'https://cdn.marastel.com/images/%';

-- ============================================================
-- 5. point_product 表（积分商品封面）
-- ============================================================

UPDATE point_product
SET cover_url = REPLACE(cover_url, 'https://cdn.marastel.com/r2/', 'https://r2.marastel.com/')
WHERE cover_url LIKE 'https://cdn.marastel.com/r2/%';

UPDATE point_product
SET cover_url = REPLACE(cover_url, 'https://cdn.marastel.com/images/', 'https://r2.marastel.com/images/')
WHERE cover_url LIKE 'https://cdn.marastel.com/images/%';

-- ============================================================
-- 6. point_shop_item 表（商城商品图片）
-- ============================================================

UPDATE point_shop_item
SET image_url = REPLACE(image_url, 'https://cdn.marastel.com/r2/', 'https://r2.marastel.com/')
WHERE image_url LIKE 'https://cdn.marastel.com/r2/%';

UPDATE point_shop_item
SET image_url = REPLACE(image_url, 'https://cdn.marastel.com/images/', 'https://r2.marastel.com/images/')
WHERE image_url LIKE 'https://cdn.marastel.com/images/%';

-- ============================================================
-- 验证修复结果
-- ============================================================

SELECT '=== 修复后：残留旧 URL 统计（应该全为 0）===' AS info;

SELECT 'drama.cover_url' AS field, COUNT(*) AS cnt FROM drama WHERE cover_url LIKE '%cdn.marastel.com%';
SELECT 'drama.horizontal_cover_url' AS field, COUNT(*) AS cnt FROM drama WHERE horizontal_cover_url LIKE '%cdn.marastel.com%';
SELECT 'drama.vertical_cover_url' AS field, COUNT(*) AS cnt FROM drama WHERE vertical_cover_url LIKE '%cdn.marastel.com%';
SELECT 'drama_episode.cover_url' AS field, COUNT(*) AS cnt FROM drama_episode WHERE cover_url LIKE '%cdn.marastel.com%';
SELECT 'drama_episode.video_url' AS field, COUNT(*) AS cnt FROM drama_episode WHERE video_url LIKE '%cdn.marastel.com%';
SELECT 'drama_episode.hls_url' AS field, COUNT(*) AS cnt FROM drama_episode WHERE hls_url LIKE '%cdn.marastel.com%';
SELECT 'app_user.avatar_url' AS field, COUNT(*) AS cnt FROM app_user WHERE avatar_url LIKE '%cdn.marastel.com%';
SELECT 'admin_user.avatar' AS field, COUNT(*) AS cnt FROM admin_user WHERE avatar LIKE '%cdn.marastel.com%';
SELECT 'point_product.cover_url' AS field, COUNT(*) AS cnt FROM point_product WHERE cover_url LIKE '%cdn.marastel.com%';
SELECT 'point_shop_item.image_url' AS field, COUNT(*) AS cnt FROM point_shop_item WHERE image_url LIKE '%cdn.marastel.com%';

SELECT '=== 修复完成 ===' AS info;
