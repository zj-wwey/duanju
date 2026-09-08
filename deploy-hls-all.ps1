# HLS 转码功能完整部署脚本
# 一次性上传所有修改过的文件 + 重建 Docker 镜像
# 用法: powershell -ExecutionPolicy Bypass -File deploy-hls-all.ps1

$SERVER = "root@167.179.72.109"
$LOCAL_BASE = "c:\Users\Administrator\Desktop\AICode\duanju-master\backed\server\src\main\java\com\duanju"
$REMOTE_BASE = "/opt/duanju-api/backed/server/src/main/java/com/duanju"
$LOCAL_DOCKERFILE = "c:\Users\Administrator\Desktop\AICode\duanju-master\backed\server\Dockerfile"
$REMOTE_DOCKERFILE = "/opt/duanju-api/backed/server/Dockerfile"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  HLS 转码功能 - 完整部署" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 需要上传的文件列表 (相对路径)
$files = @(
    "entity/DramaEpisode.java",
    "service/VideoTranscodeService.java",
    "config/AsyncConfig.java",
    "service/DramaService.java",
    "controller/AdminContentController.java",
    "service/storage/StorageProvider.java",
    "service/storage/R2StorageProvider.java",
    "service/storage/LocalStorageProvider.java",
    "service/CloudflareStreamService.java",
    "mapper/entity/DramaEpisodeMapper.java"
)

Write-Host "[1/3] 上传 Java 源文件 ($($files.Count) 个)..." -ForegroundColor Yellow
foreach ($f in $files) {
    $local = "$LOCAL_BASE\$($f -replace '/', '\')"
    $remote = "${SERVER}:$REMOTE_BASE/$f"
    Write-Host "  -> $f" -ForegroundColor Gray
    scp -o StrictHostKeyChecking=no "$local" "$remote"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  上传失败: $f" -ForegroundColor Red
        Write-Host "  请检查路径是否正确: $local" -ForegroundColor Red
        exit 1
    }
}
Write-Host "  Java 文件上传完成" -ForegroundColor Green

Write-Host ""
Write-Host "[2/3] 上传 Dockerfile..." -ForegroundColor Yellow
scp -o StrictHostKeyChecking=no "$LOCAL_DOCKERFILE" "${SERVER}:$REMOTE_DOCKERFILE"
if ($LASTEXITCODE -ne 0) {
    Write-Host "  Dockerfile 上传失败" -ForegroundColor Red
    exit 1
}
Write-Host "  Dockerfile 上传完成" -ForegroundColor Green

Write-Host ""
Write-Host "[3/3] 重建 Docker 镜像并重启..." -ForegroundColor Yellow
Write-Host "  正在构建镜像 (可能需要 3-5 分钟)..." -ForegroundColor Gray
ssh -o StrictHostKeyChecking=no $SERVER "cd /opt/duanju-api && docker compose build --no-cache api 2>&1"
if ($LASTEXITCODE -ne 0) {
    Write-Host "  Docker 构建失败!" -ForegroundColor Red
    Write-Host "  请检查上方输出中的错误信息" -ForegroundColor Red
    exit 1
}

Write-Host "  正在重启容器..." -ForegroundColor Gray
ssh -o StrictHostKeyChecking=no $SERVER "cd /opt/duanju-api && docker compose up -d api 2>&1"
if ($LASTEXITCODE -ne 0) {
    Write-Host "  容器启动失败!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  部署完成!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "验证命令:" -ForegroundColor Cyan
Write-Host "  ssh $SERVER `"docker logs --tail 20 \$(docker ps -q -f name=api)`""
Write-Host "  ssh $SERVER `"curl -s http://localhost:8080/actuator/health`""
Write-Host ""
