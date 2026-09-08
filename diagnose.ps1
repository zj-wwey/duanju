# ============================================================
#  数据库诊断命令 - 检查视频上传问题根因
# ============================================================
#  在 PowerShell 中直接运行本脚本
# ============================================================

$DBPASS = 'Kp$7sQ9!vB2@zR5#mG8^dN4&'
$SERVER = 'root@167.179.72.109'

Write-Host ""
Write-Host "==============================================" -ForegroundColor Cyan
Write-Host "  数据库诊断" -ForegroundColor Cyan
Write-Host "==============================================" -ForegroundColor Cyan
Write-Host ""

# ============================================================
#  1. 检查 drama_episode 表里有没有数据
# ============================================================
Write-Host "[1/3] 检查 drama_episode 表里的剧集记录..." -ForegroundColor Yellow
Write-Host ""
ssh $SERVER "docker exec duanju-mysql mysql -u root -p'$DBPASS' duanju -e 'SELECT id, drama_id, episode_no, title, video_url, storage_provider, status FROM drama_episode;'"
Write-Host ""

# ============================================================
#  2. 检查表结构（看有没有 video_duration 列）
# ============================================================
Write-Host "[2/3] 检查 drama_episode 表结构..." -ForegroundColor Yellow
Write-Host ""
ssh $SERVER "docker exec duanju-mysql mysql -u root -p'$DBPASS' duanju -e 'SHOW COLUMNS FROM drama_episode;'"
Write-Host ""

# ============================================================
#  3. 查看后端错误日志
# ============================================================
Write-Host "[3/3] 查看后端最近错误日志..." -ForegroundColor Yellow
Write-Host ""
ssh $SERVER "docker logs duanju-api --tail 50 2>&1 | grep -i 'error\|exception\|fail'"
Write-Host ""

Write-Host "==============================================" -ForegroundColor Green
Write-Host "  诊断完成，请把以上输出截图发给我" -ForegroundColor Green
Write-Host "==============================================" -ForegroundColor Green
