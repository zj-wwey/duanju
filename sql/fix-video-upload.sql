-- ============================================================
--  修复视频上传：video_url NOT NULL 约束 + 清理重复数据
-- ============================================================
--  问题根因：
--    drama_episode 表的 video_url 列是 NOT NULL，
--    但前端保存时 video_url 可能为空（presign 未返回 URL 或单集编辑未上传视频）
--    导致 INSERT 报 SQLIntegrityConstraintViolationException
--
--  修复内容：
--    1. 将 video_url 改为可空（允许先创建剧集再上传视频）
--    2. 清理可能的重复/残留记录
--    3. 验证表结构
-- ============================================================

-- 1. 先查看当前有没有记录（诊断用）
SELECT '=== 现有剧集记录 ===' AS info;
SELECT id, drama_id, episode_no, title,
       CASE WHEN video_url IS NULL THEN 'NULL!' ELSE LEFT(video_url, 60) END AS video_url_short,
       storage_provider, status
FROM drama_episode
ORDER BY drama_id, episode_no;

-- 2. 将 video_url 列改为可空（允许先保存剧集再上传视频）
ALTER TABLE drama_episode MODIFY COLUMN video_url varchar(1000) NULL;

-- 3. 清理重复的剧集记录（同一 drama_id + episode_no 只保留最新一条）
--    先查看有哪些重复
SELECT '=== 重复记录检查 ===' AS info;
SELECT drama_id, episode_no, COUNT(*) AS cnt, GROUP_CONCAT(id) AS dup_ids
FROM drama_episode
GROUP BY drama_id, episode_no
HAVING cnt > 1;

-- 4. 删除重复记录（保留 id 最大的那条）
DELETE e1 FROM drama_episode e1
INNER JOIN drama_episode e2
  ON e1.drama_id = e2.drama_id
 AND e1.episode_no = e2.episode_no
 AND e1.id < e2.id;

-- 5. 验证修复结果
SELECT '=== 修复后验证 ===' AS info;
SHOW COLUMNS FROM drama_episode WHERE field = 'video_url';

SELECT '=== 修复后记录 ===' AS info;
SELECT id, drama_id, episode_no, title,
       CASE WHEN video_url IS NULL THEN 'NULL' ELSE LEFT(video_url, 60) END AS video_url_short,
       storage_provider, status
FROM drama_episode
ORDER BY drama_id, episode_no;
