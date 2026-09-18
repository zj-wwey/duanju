# ============================================================
#  视频上传 4 层修复 - 上传文件到服务器并执行
# ============================================================
#  服务器: root@167.179.72.109
#  部署目录: /opt/duanju-api
#  用法: 在 PowerShell 中运行本脚本
# ============================================================

# 服务器信息
$SERVER = "root@167.179.72.109"
$REMOTE_DIR = "/opt/duanju-api"
$LOCAL_DIR = "c:\Users\Administrator\Desktop\AICode\duanju-master"

Write-Host ""
Write-Host "==============================================" -ForegroundColor Cyan
Write-Host "  视频上传 4 层修复 - 上传并执行" -ForegroundColor Cyan
Write-Host "==============================================" -ForegroundColor Cyan
Write-Host ""

# ============================================================
#  第 1 步：上传核心配置文件 (Dockerfile, docker-compose.yml, .env)
# ============================================================
Write-Host "[1/5] 上传核心配置文件..." -ForegroundColor Yellow

# L1: Dockerfile (去掉 dependency:go-offline + jar 验证)
scp "$LOCAL_DIR\backed\server\Dockerfile" "$SERVER`:$REMOTE_DIR/backed/server/Dockerfile"
Write-Host "  Dockerfile -> backed/server/" -ForegroundColor Gray

# L2: docker-compose.yml (显式传递 R2 环境变量)
scp "$LOCAL_DIR\docker-compose.yml" "$SERVER`:$REMOTE_DIR/docker-compose.yml"
Write-Host "  docker-compose.yml -> /" -ForegroundColor Gray

# L2: .env (新 R2 密钥: 32 字符 Access Key ID)
scp "$LOCAL_DIR\.env" "$SERVER`:$REMOTE_DIR/.env"
Write-Host "  .env -> / (R2 密钥已更新)" -ForegroundColor Gray

Write-Host "  -> 完成" -ForegroundColor Green

# ============================================================
#  第 2 步：上传修复脚本和 SQL 文件
# ============================================================
Write-Host ""
Write-Host "[2/5] 上传修复脚本和 SQL..." -ForegroundColor Yellow

scp "$LOCAL_DIR\fix-all-layers.sh" "$SERVER`:$REMOTE_DIR/"
Write-Host "  fix-all-layers.sh" -ForegroundColor Gray

scp "$LOCAL_DIR\fix-r2-url-prefix.sql" "$SERVER`:$REMOTE_DIR/"
scp "$LOCAL_DIR\fix-db-schema.sql" "$SERVER`:$REMOTE_DIR/"
scp "$LOCAL_DIR\fix-video-upload.sql" "$SERVER`:$REMOTE_DIR/"
Write-Host "  fix-*.sql (3 个)" -ForegroundColor Gray

Write-Host "  -> 完成" -ForegroundColor Green

# ============================================================
#  第 3 步：同步后端 Java 源码 (确保 presign 代码在服务器上)
# ============================================================
Write-Host ""
Write-Host "[3/5] 同步后端 Java 源码..." -ForegroundColor Yellow

scp "$LOCAL_DIR\backed\server\src\main\java\com\duanju\controller\AdminContentController.java" `
    "$SERVER`:$REMOTE_DIR/backed/server/src/main/java/com/duanju/controller/"

scp "$LOCAL_DIR\backed\server\src\main\java\com\duanju\service\StorageService.java" `
    "$SERVER`:$REMOTE_DIR/backed/server/src/main/java/com/duanju/service/"

scp "$LOCAL_DIR\backed\server\src\main\java\com\duanju\service\storage\R2StorageProvider.java" `
    "$SERVER`:$REMOTE_DIR/backed/server/src/main/java/com/duanju/service/storage/"

scp "$LOCAL_DIR\backed\server\src\main\java\com\duanju\service\storage\StorageProvider.java" `
    "$SERVER`:$REMOTE_DIR/backed/server/src/main/java/com/duanju/service/storage/"

scp "$LOCAL_DIR\backed\server\src\main\java\com\duanju\service\storage\LocalStorageProvider.java" `
    "$SERVER`:$REMOTE_DIR/backed/server/src/main/java/com/duanju/service/storage/"

Write-Host "  -> 完成" -ForegroundColor Green

# ============================================================
#  第 4 步：同步前端 api.js (ORB 修复)
# ============================================================
Write-Host ""
Write-Host "[4/5] 同步前端 api.js (ORB 修复)..." -ForegroundColor Yellow

scp "$LOCAL_DIR\front\admin\src\api.js" `
    "$SERVER`:$REMOTE_DIR/front/admin/src/"

scp "$LOCAL_DIR\front\admin\src\api.js" `
    "$SERVER`:/opt/duanju-web/admin/src/"

Write-Host "  -> 完成" -ForegroundColor Green

# ============================================================
#  第 5 步：SSH 执行一键修复脚本
# ============================================================
Write-Host ""
Write-Host "[5/5] 在服务器上执行 fix-all-layers.sh..." -ForegroundColor Yellow
Write-Host "  (约 5-10 分钟: 无缓存重建 API + 重建前端 + SQL 修复)" -ForegroundColor Gray
Write-Host ""

ssh -t $SERVER "cd $REMOTE_DIR && chmod +x fix-all-layers.sh && ./fix-all-layers.sh"

Write-Host ""
Write-Host "==============================================" -ForegroundColor Green
Write-Host "  全部完成！" -ForegroundColor Green
Write-Host "==============================================" -ForegroundColor Green
Write-Host ""
Write-Host "下一步:" -ForegroundColor Cyan
Write-Host "  1. 浏览器硬刷新 (Ctrl+Shift+R) 管理后台"
Write-Host "  2. 上传一张小图片测试 R2 直传"
Write-Host "  3. 上传一个视频测试完整流程"
Write-Host ""
