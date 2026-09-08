# ============================================================
# 修复服务器 HLS 转码编译错误
# 从本地 backed/ 上传到服务器 /opt/duanju-api/
# ============================================================

$REMOTE = "root@167.179.72.109"
$REMOTE_BASE = "/opt/duanju-api"

Write-Host "`n===== 修复服务器 HLS 编译错误 =====`n" -ForegroundColor Cyan

# ----------------------------------------------------------
# 1. 上传所有缺失/过时的 Java 文件
# ----------------------------------------------------------
Write-Host "[1/3] 上传 Java 文件到服务器..." -ForegroundColor Yellow

$files = @(
    @{ local = "backed\server\src\main\java\com\duanju\entity\DramaEpisode.java";
       remote = "backed/server/src/main/java/com/duanju/entity/DramaEpisode.java" },
    @{ local = "backed\server\src\main\java\com\duanju\service\VideoTranscodeService.java";
       remote = "backed/server/src/main/java/com/duanju/service/VideoTranscodeService.java" },
    @{ local = "backed\server\src\main\java\com\duanju\service\DramaService.java";
       remote = "backed/server/src/main/java/com/duanju/service/DramaService.java" },
    @{ local = "backed\server\src\main\java\com\duanju\service\CloudflareStreamService.java";
       remote = "backed/server/src/main/java/com/duanju/service/CloudflareStreamService.java" },
    @{ local = "backed\server\src\main\java\com\duanju\config\AsyncConfig.java";
       remote = "backed/server/src/main/java/com/duanju/config/AsyncConfig.java" },
    @{ local = "backed\server\src\main\java\com\duanju\DuanjuApplication.java";
       remote = "backed/server/src/main/java/com/duanju/DuanjuApplication.java" },
    @{ local = "backed\server\src\main\java\com\duanju\controller\AdminContentController.java";
       remote = "backed/server/src/main/java/com/duanju/controller/AdminContentController.java" },
    @{ local = "backed\server\src\main\java\com\duanju\service\storage\StorageProvider.java";
       remote = "backed/server/src/main/java/com/duanju/service/storage/StorageProvider.java" },
    @{ local = "backed\server\src\main\java\com\duanju\service\storage\R2StorageProvider.java";
       remote = "backed/server/src/main/java/com/duanju/service/storage/R2StorageProvider.java" },
    @{ local = "backed\server\src\main\java\com\duanju\service\storage\LocalStorageProvider.java";
       remote = "backed/server/src/main/java/com/duanju/service/storage/LocalStorageProvider.java" },
    @{ local = "backed\server\src\main\java\com\duanju\mapper\entity\DramaEpisodeMapper.java";
       remote = "backed/server/src/main/java/com/duanju/mapper/entity/DramaEpisodeMapper.java" }
)

foreach ($f in $files) {
    $remotePath = "$REMOTE_BASE/$($f.remote)"
    $remoteDir = Split-Path $remotePath -Parent
    ssh $REMOTE "mkdir -p '$remoteDir'"
    scp $f.local "${REMOTE}:$remotePath"
    Write-Host "  uploaded: $($f.local)" -ForegroundColor Green
}

# ----------------------------------------------------------
# 2. 上传 Dockerfile（含 FFmpeg 安装）
# ----------------------------------------------------------
Write-Host "`n[2/3] 上传 Dockerfile..." -ForegroundColor Yellow
scp "backed\server\Dockerfile" "${REMOTE}:$REMOTE_BASE/backed/server/Dockerfile"
Write-Host "  Dockerfile uploaded" -ForegroundColor Green

# ----------------------------------------------------------
# 3. 重建并重启后端容器
# ----------------------------------------------------------
Write-Host "`n[3/3] 重建并重启后端容器..." -ForegroundColor Yellow

Write-Host "  正在构建（可能需要几分钟）..." -ForegroundColor DarkGray
ssh $REMOTE "cd $REMOTE_BASE && docker compose build --no-cache api" 2>&1 | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }

Write-Host "  启动容器..." -ForegroundColor DarkGray
ssh $REMOTE "cd $REMOTE_BASE && docker compose up -d api"

Write-Host "  等待容器启动（30秒）..." -ForegroundColor DarkGray
Start-Sleep -Seconds 30

# 验证
Write-Host "`n===== 验证 =====`n" -ForegroundColor Cyan

Write-Host "1. 检查容器状态:" -ForegroundColor Yellow
ssh $REMOTE "docker ps --filter name=duanju-api --format 'table {{.Names}}\t{{.Status}}'"

Write-Host "`n2. 检查 FFmpeg:" -ForegroundColor Yellow
ssh $REMOTE "docker exec duanju-api ffmpeg -version 2>&1 | head -1"

Write-Host "`n3. 检查健康状态:" -ForegroundColor Yellow
ssh $REMOTE "curl -s http://127.0.0.1:8080/actuator/health"

Write-Host "`n4. 检查编译日志（最后20行）:" -ForegroundColor Yellow
ssh $REMOTE "docker logs duanju-api --tail 20 2>&1"

Write-Host "`n===== 完成 =====`n" -ForegroundColor Cyan
Write-Host "如果容器启动成功，说明编译错误已修复。" -ForegroundColor Green
Write-Host "接下来可以测试 HLS 转码功能。" -ForegroundColor Green
