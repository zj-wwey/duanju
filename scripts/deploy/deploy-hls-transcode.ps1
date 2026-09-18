# ============================================================
# HLS流式转码功能部署脚本
# 功能: 视频转为HLS分片(.m3u8 + .ts),支持流式播放
# ============================================================

$REMOTE = "root@dash.marastel.com"
$REMOTE_PATH = "/opt/duanju"

Write-Host "`n===== HLS流式转码部署 =====`n" -ForegroundColor Cyan

# ----------------------------------------------------------
# 1. 上传后端 Java 文件
# ----------------------------------------------------------
Write-Host "[1/5] 上传后端 Java 文件..." -ForegroundColor Yellow

$backendFiles = @(
    "backed\server\Dockerfile",
    "backed\server\src\main\java\com\duanju\DuanjuApplication.java",
    "backed\server\src\main\java\com\duanju\entity\DramaEpisode.java",
    "backed\server\src\main\java\com\duanju\service\DramaService.java",
    "backed\server\src\main\java\com\duanju\service\VideoTranscodeService.java",
    "backed\server\src\main\java\com\duanju\service\storage\StorageProvider.java",
    "backed\server\src\main\java\com\duanju\service\storage\R2StorageProvider.java",
    "backed\server\src\main\java\com\duanju\controller\AdminContentController.java",
    "backed\server\src\main\java\com\duanju\mapper\entity\DramaEpisodeMapper.java"
)

foreach ($file in $backendFiles) {
    $localPath = $file
    $remotePath = "$REMOTE_PATH/$file"
    $remoteDir = Split-Path $remotePath -Parent
    ssh $REMOTE "mkdir -p '$remoteDir'"
    scp $localPath "${REMOTE}:$remotePath"
    Write-Host "  uploaded: $file" -ForegroundColor Green
}

# ----------------------------------------------------------
# 2. 上传前端文件
# ----------------------------------------------------------
Write-Host "`n[2/5] 上传前端文件..." -ForegroundColor Yellow

$frontendFiles = @(
    "front\admin\package.json",
    "front\admin\src\App.vue",
    "front\admin\src\api.js",
    "front\admin\src\style.css"
)

foreach ($file in $frontendFiles) {
    $remotePath = "$REMOTE_PATH/$file"
    $remoteDir = Split-Path $remotePath -Parent
    ssh $REMOTE "mkdir -p '$remoteDir'"
    scp $file "${REMOTE}:$remotePath"
    Write-Host "  uploaded: $file" -ForegroundColor Green
}

# ----------------------------------------------------------
# 3. 上传并执行 SQL 迁移
# ----------------------------------------------------------
Write-Host "`n[3/5] 执行数据库迁移..." -ForegroundColor Yellow

scp fix-hls-transcode.sql "${REMOTE}:$REMOTE_PATH/fix-hls-transcode.sql"
ssh $REMOTE "docker exec -i duanju-mysql mysql -u root -p'Duanju@2024!' duanju < $REMOTE_PATH/fix-hls-transcode.sql"
Write-Host "  SQL migration completed" -ForegroundColor Green

# ----------------------------------------------------------
# 4. 重建并重启后端容器
# ----------------------------------------------------------
Write-Host "`n[4/5] 重建并重启后端容器..." -ForegroundColor Yellow

ssh $REMOTE "cd $REMOTE_PATH && docker compose build --no-cache api"
ssh $REMOTE "cd $REMOTE_PATH && docker compose up -d api"
Write-Host "  Backend container rebuilt and restarted" -ForegroundColor Green
Write-Host "  等待容器启动 (30秒)..." -ForegroundColor DarkGray
Start-Sleep -Seconds 30

# 验证后端健康
Write-Host "  验证后端健康状态..." -ForegroundColor DarkGray
ssh $REMOTE "curl -s http://127.0.0.1:8080/actuator/health"
Write-Host ""

# ----------------------------------------------------------
# 5. 重建前端并重启 Nginx
# ----------------------------------------------------------
Write-Host "`n[5/5] 重建前端并重启 Nginx..." -ForegroundColor Yellow

ssh $REMOTE "cd $REMOTE_PATH && docker compose build --no-cache admin"
ssh $REMOTE "cd $REMOTE_PATH && docker compose up -d admin"
ssh $REMOTE "docker exec duanju-nginx nginx -s reload"
Write-Host "  Frontend rebuilt and Nginx reloaded" -ForegroundColor Green

# ----------------------------------------------------------
# 验证
# ----------------------------------------------------------
Write-Host "`n===== 部署验证 =====`n" -ForegroundColor Cyan

Write-Host "1. 检查 FFmpeg 是否可用:" -ForegroundColor Yellow
ssh $REMOTE "docker exec duanju-api ffmpeg -version | head -1"

Write-Host "`n2. 检查转码状态接口:" -ForegroundColor Yellow
ssh $REMOTE "curl -s http://127.0.0.1:8080/api/admin/episodes/1/transcode-status -H 'Authorization: Bearer dummy' | head -100"

Write-Host "`n3. 检查 R2 公开访问:" -ForegroundColor Yellow
ssh $REMOTE "curl -sI https://r2.marastel.com/ | head -5"

Write-Host "`n===== 部署完成 =====`n" -ForegroundColor Cyan
Write-Host "HLS流式转码功能已部署。" -ForegroundColor Green
Write-Host "功能说明:" -ForegroundColor White
Write-Host "  - 上传视频后自动触发HLS转码(异步)" -ForegroundColor White
Write-Host "  - 转码完成后自动生成 .m3u8 + .ts 分片" -ForegroundColor White
Write-Host "  - 剧集列表显示转码状态标签(未转码/转码中/HLS/失败)" -ForegroundColor White
Write-Host "  - 点击「预览」按钮可预览视频(优先HLS流)" -ForegroundColor White
Write-Host "  - 转码失败可点击「重转码」重新触发" -ForegroundColor White
Write-Host "  - C端播放器自动使用HLS流(原生支持)" -ForegroundColor White
