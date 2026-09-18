-- ============================================================
--  修复软删除导致的 episode_no 唯一约束冲突
--  问题：deleteEpisode 软删时只设 status=-1，episode_no 不变
--        再新建同集数时 uk_episode_no 唯一约束报错"数据重复"
--  修复：把所有 status=-1 的记录的 episode_no 改为 -id（负值，不再冲突）
-- ============================================================

-- 查看当前有多少软删记录在冲突
SELECT id, drama_id, episode_no, status, title
FROM drama_episode
WHERE status = -1
ORDER BY drama_id, episode_no;

-- 修复：软删记录的 episode_no 改为 -id
UPDATE drama_episode
SET episode_no = -id
WHERE status = -1
  AND episode_no > 0;

-- 验证修复结果
SELECT id, drama_id, episode_no, status, title
FROM drama_episode
WHERE status = -1
ORDER BY drama_id, episode_no;

-- 同时检查 dramas 表是否有类似问题
SELECT id, title, status FROM dramas WHERE status = -1;
