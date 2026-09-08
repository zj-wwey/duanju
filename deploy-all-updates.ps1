# ============================================================
# 全量更新部署 — 上传所有修改文件到服务器并执行
# ============================================================
# 服务器: root@167.179.72.109
# 部署目录: /opt/duanju-api
#
# 包含所有修改:
#   后端: DramaService.java, StorageService.java, storage/*, Dockerfile, docker-compose.yml
#   前端: App.vue, style.css, api.js
#   Nginx: duanju.conf
#   SQL: fix-video-upload.sql, fix-soft-delete-conflict.sql
#   脚本: deploy-updates.sh
#
# 用法: 在 PowerShell 中运行本脚本
# ============================================================

$SERVER = "root@167.179.72.109"
$REMOTE_DIR = "/opt/duanju-api"
$LOCAL_DIR = "c:\Users\Administrator\Desktop\AICode\duanju-master"

Write-Host ""
Write-Host "==============================================" -ForegroundColor Cyan
Write-Host "  全量更新部署 — 上传所有修改文件" -ForegroundColor Cyan
Write-Host "==============================================" -ForegroundColor Cyan
Write-Host ""

# ============================================================
# 第 1 步: 上传后端 Java 源码
# ============================================================
Write-Host "[1/6] 上传后端 Java 源码..." -ForegroundColor Yellow

# DramaService.java (软删 episode_no=-id + 删除同步清理R2)
scp "$LOCAL_DIR\backed\server\src\main\java\com\duanju\service\DramaService.java" `
    "$SERVER`:$REMOTE_DIR/backed/server/src/main/java/com/duanju/service/"
Write-Host "  DramaService.java" -ForegroundColor Gray

# StorageService.java (时长探测 + 对象删除 + presign)
scp "$LOCAL_DIR\backed\server\src\main\java\com\duanju\service\StorageService.java" `
    "$SERVER`:$REMOTE_DIR/backed/server/src/main/java/com/duanju/service/"
Write-Host "  StorageService.java" -ForegroundColor Gray

# storage providers
scp "$LOCAL_DIR\backed\server\src\main\java\com\duanju\service\storage\R2StorageProvider.java" `
    "$SERVER`:$REMOTE_DIR/backed/server/src/main/java/com/duanju/service/storage/"
scp "$LOCAL_DIR\backed\server\src\main\java\com\duanju\service\storage\StorageProvider.java" `
    "$SERVER`:$REMOTE_DIR/backed/server/src/main/java/com/duanju/service/storage/"
scp "$LOCAL_DIR\backed\server\src\main\java\com\duanju\service\storage\LocalStorageProvider.java" `
    "$SERVER`:$REMOTE_DIR/backed/server/src/main/java/com/duanju/service/storage/"
Write-Host "  storage/* (3 files)" -ForegroundColor Gray

# AdminContentController.java
scp "$LOCAL_DIR\backed\server\src\main\java\com\duanju\controller\AdminContentController.java" `
    "$SERVER`:$REMOTE_DIR/backed/server/src/main/java/com/duanju/controller/"
Write-Host "  AdminContentController.java" -ForegroundColor Gray

Write-Host "  -> 完成" -ForegroundColor Green

# ============================================================
# 第 2 步: 上传后端配置文件
# ============================================================
Write-Host ""
Write-Host "[2/6] 上传后端配置..." -ForegroundColor Yellow

# Dockerfile (fat JAR 验证)
scp "$LOCAL_DIR\backed\server\Dockerfile" "$SERVER`:$REMOTE_DIR/backed/server/Dockerfile"
Write-Host "  Dockerfile" -ForegroundColor Gray

# docker-compose.yml (R2环境变量 + Redis命令列表格式)
scp "$LOCAL_DIR\docker-compose.yml" "$SERVER`:$REMOTE_DIR/docker-compose.yml"
Write-Host "  docker-compose.yml" -ForegroundColor Gray

# .env (R2 密钥配置)
scp "$LOCAL_DIR\.env" "$SERVER`:$REMOTE_DIR/.env"
Write-Host "  .env" -ForegroundColor Gray

Write-Host "  -> 完成" -ForegroundColor Green

# ============================================================
# 第 3 步: 上传前端文件
# ============================================================
Write-Host ""
Write-Host "[3/6] 上传前端文件..." -ForegroundColor Yellow

# App.vue (上传进度在剧集列表 + 取消/重传 + 网速 + 非阻塞)
scp "$LOCAL_DIR\front\admin\src\App.vue" `
    "$SERVER`:$REMOTE_DIR/front/admin/src/"
Write-Host "  App.vue" -ForegroundColor Gray

# style.css (待上传剧集行样式)
scp "$LOCAL_DIR\front\admin\src\style.css" `
    "$SERVER`:$REMOTE_DIR/front/admin/src/"
Write-Host "  style.css" -ForegroundColor Gray

# api.js (presign/batchUpload API)
scp "$LOCAL_DIR\front\admin\src\api.js" `
    "$SERVER`:$REMOTE_DIR/front/admin/src/"
Write-Host "  api.js" -ForegroundColor Gray

Write-Host "  -> 完成" -ForegroundColor Green

# ============================================================
# 第 4 步: 上传 Nginx 配置
# ============================================================
Write-Host ""
Write-Host "[4/6] 上传 Nginx 配置..." -ForegroundColor Yellow

# duanju.conf (R2代理占位符替换 + Range头转发)
scp "$LOCAL_DIR\nginx\conf.d\duanju.conf" `
    "$SERVER`:$REMOTE_DIR/nginx/conf.d/duanju.conf"
Write-Host "  duanju.conf" -ForegroundColor Gray

Write-Host "  -> 完成" -ForegroundColor Green

# ============================================================
# 第 5 步: 上传 SQL 脚本和部署脚本
# ============================================================
Write-Host ""
Write-Host "[5/6] 上传 SQL 和部署脚本..." -ForegroundColor Yellow

scp "$LOCAL_DIR\fix-video-upload.sql" "$SERVER`:$REMOTE_DIR/"
Write-Host "  fix-video-upload.sql" -ForegroundColor Gray

scp "$LOCAL_DIR\fix-soft-delete-conflict.sql" "$SERVER`:$REMOTE_DIR/"
Write-Host "  fix-soft-delete-conflict.sql" -ForegroundColor Gray

scp "$LOCAL_DIR\deploy-updates.sh" "$SERVER`:$REMOTE_DIR/"
Write-Host "  deploy-updates.sh" -ForegroundColor Gray

Write-Host "  -> 完成" -ForegroundColor Green

# ============================================================
# 第 6 步: SSH 执行部署脚本
# ============================================================
Write-Host ""
Write-Host "[6/6] 在服务器上执行部署..." -ForegroundColor Yellow
Write-Host "  (约 8-12 分钟: SQL修复 + 无缓存重建后端 + 重建前端 + Nginx重载)" -ForegroundColor Gray
Write-Host ""

ssh -t $SERVER "cd $REMOTE_DIR && chmod +x deploy-updates.sh && ./deploy-updates.sh"

Write-Host ""
Write-Host "==============================================" -ForegroundColor Green
Write-Host "  全部完成！" -ForegroundColor Green
Write-Host "==============================================" -ForegroundColor Green
Write-Host ""
Write-Host "验证步骤:" -ForegroundColor Cyan
Write-Host "  1. 浏览器硬刷新 (Ctrl+Shift+R) 管理后台"
Write-Host "  2. 上传图片测试 R2 直传"
Write-Host "  3. 上传视频测试完整流程 (进度/取消/网速)"
Write-Host "  4. 删除一集再重建同集数, 验证不再报'数据重复'"
Write-Host "  5. 删除剧集后检查 R2 存储是否同步删除"
Write-Host ""
