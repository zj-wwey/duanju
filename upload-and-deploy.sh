#!/bin/bash
# ============================================================
#  视频上传修复 - 手动逐条执行版（适合复制粘贴）
#  服务器: root@167.179.72.109
#  部署目录: /opt/duanju-api
# ============================================================

SERVER="root@167.179.72.109"
REMOTE="/opt/duanju-api"
LOCAL="c:/Users/Administrator/Desktop/AICode/duanju-master"

echo ""
echo "=============================================="
echo "  视频上传修复 - 上传文件到服务器"
echo "=============================================="
echo ""

# ============================================================
#  第 1 步：上传修复脚本和 SQL 文件
# ============================================================
echo "[1/4] 上传 deploy-fix.sh 和 fix-r2-url-prefix.sql..."

scp "$LOCAL/deploy-fix.sh"          "$SERVER:$REMOTE/"
scp "$LOCAL/fix-r2-url-prefix.sql"  "$SERVER:$REMOTE/"
scp "$LOCAL/fix-db-schema.sql"      "$SERVER:$REMOTE/"
scp "$LOCAL/fix-video-upload.sql"   "$SERVER:$REMOTE/"

echo "  -> done"

# ============================================================
#  第 2 步：同步后端关键 Java 文件（确保 presign 代码在服务器上）
# ============================================================
echo ""
echo "[2/4] 同步后端关键 Java 文件..."

scp "$LOCAL/backed/server/src/main/java/com/duanju/controller/AdminContentController.java" \
    "$SERVER:$REMOTE/backed/server/src/main/java/com/duanju/controller/"

scp "$LOCAL/backed/server/src/main/java/com/duanju/service/StorageService.java" \
    "$SERVER:$REMOTE/backed/server/src/main/java/com/duanju/service/"

scp "$LOCAL/backed/server/src/main/java/com/duanju/service/storage/R2StorageProvider.java" \
    "$SERVER:$REMOTE/backed/server/src/main/java/com/duanju/service/storage/"

echo "  -> done"

# ============================================================
#  第 3 步：同步前端 api.js（确保 ORB 修复在服务器上）
# ============================================================
echo ""
echo "[3/4] 同步前端 api.js（ORB 修复）..."

# 上传到 duanju-api/front/admin/src/
scp "$LOCAL/front/admin/src/api.js" "$SERVER:$REMOTE/front/admin/src/"

# 也上传到 duanju-web/admin/src/
scp "$LOCAL/front/admin/src/api.js" "$SERVER:/opt/duanju-web/admin/src/"

echo "  -> done"

# ============================================================
#  第 4 步：SSH 执行修复脚本
# ============================================================
echo ""
echo "[4/4] 在服务器上执行修复脚本（约 5-10 分钟）..."
echo ""

ssh -t "$SERVER" "cd $REMOTE && chmod +x deploy-fix.sh && ./deploy-fix.sh"

echo ""
echo "=============================================="
echo "  全部完成！"
echo "=============================================="
echo ""
echo "下一步：浏览器硬刷新 (Ctrl+Shift+R) 管理后台，测试上传"
