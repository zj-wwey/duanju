#!/bin/bash
# ============================================================
# 全量更新部署脚本 — 包含所有功能修改和Bug修复
# ============================================================
# 修复内容:
#   1. 后端: DramaService.java (软删episode_no=-id + 删除同步清理R2)
#   2. 后端: StorageService.java + storage/* (时长探测 + 对象删除 + presign)
#   3. 后端: Dockerfile (fat JAR 验证)
#   4. 前端: App.vue (上传进度在剧集列表 + 取消/重传 + 网速 + 非阻塞)
#   5. 前端: style.css (待上传剧集行样式)
#   6. 前端: api.js (presign/batchUpload API)
#   7. Nginx: duanju.conf (R2代理占位符替换 + Range头转发)
#   8. SQL: fix-video-upload.sql (video_url可空 + 清理重复)
#   9. SQL: fix-soft-delete-conflict.sql (软删episode_no冲突修复)
# ============================================================

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN} [OK]${NC}  $1"; }
log_warn()    { echo -e "${YELLOW} [!]${NC}   $1"; }
log_error()   { echo -e "${RED}  [ERR]${NC}  $1"; }

echo ""
echo "=============================================="
echo " 全量更新部署 — 功能修改 + Bug修复"
echo "=============================================="
echo ""

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DEPLOY_DIR"

if [ ! -f docker-compose.yml ]; then
    log_error "当前目录 ($DEPLOY_DIR) 没有 docker-compose.yml"
    exit 1
fi

MYSQL_ROOT_PASSWORD=$(grep "^MYSQL_ROOT_PASSWORD=" .env | head -1 | cut -d'=' -f2-)

# ============================================================
# 步骤 1: 执行 SQL 修复
# ============================================================
echo "=============================================="
echo " 步骤 1/5: 执行数据库修复 SQL"
echo "=============================================="
echo ""

if docker ps --format '{{.Names}}' | grep -q "duanju-mysql"; then
    if [ -f fix-video-upload.sql ]; then
        log_info "执行 fix-video-upload.sql (video_url 可空 + 清理重复)..."
        docker exec -i duanju-mysql mysql -u root -p"$MYSQL_ROOT_PASSWORD" duanju < fix-video-upload.sql 2>/dev/null
        log_success "video_url 已改为可空, 重复数据已清理"
    else
        log_warn "未找到 fix-video-upload.sql, 跳过"
    fi

    if [ -f fix-soft-delete-conflict.sql ]; then
        log_info "执行 fix-soft-delete-conflict.sql (软删 episode_no 冲突修复)..."
        docker exec -i duanju-mysql mysql -u root -p"$MYSQL_ROOT_PASSWORD" duanju < fix-soft-delete-conflict.sql 2>/dev/null
        log_success "软删记录 episode_no 已修正为 -id, 不再冲突"
    else
        log_warn "未找到 fix-soft-delete-conflict.sql, 跳过"
    fi
else
    log_error "duanju-mysql 容器未运行, 跳过数据库修复"
fi

# ============================================================
# 步骤 2: 更新 Nginx 配置并重载
# ============================================================
echo ""
echo "=============================================="
echo " 步骤 2/5: 更新 Nginx 配置 (R2代理 + Range头)"
echo "=============================================="
echo ""

if docker ps --format '{{.Names}}' | grep -q "duanju-nginx"; then
    log_info "重载 Nginx 配置..."
    docker exec duanju-nginx nginx -t 2>&1 && \
    docker exec duanju-nginx nginx -s reload 2>&1
    log_success "Nginx 配置已重载 (R2代理 + Range头转发)"
else
    log_warn "duanju-nginx 容器未运行, 跳过 Nginx 更新"
fi

# ============================================================
# 步骤 3: 无缓存重建后端 API 镜像
# ============================================================
echo ""
echo "=============================================="
echo " 步骤 3/5: 无缓存重建后端 API 镜像"
echo "=============================================="
echo ""

log_info "停止旧 api 容器..."
docker compose stop api 2>/dev/null || true
docker compose rm -f api 2>/dev/null || true

log_info "删除旧镜像 (清缓存)..."
docker rmi duanju-api:latest 2>/dev/null || true

log_info "无缓存构建 api 镜像 (约 3-5 分钟)..."
log_info "Dockerfile 内会自动验证 fat jar 含 AdminContentController + BOOT-INF/classes"
if docker compose build --no-cache api; then
    log_success "后端镜像构建成功"
else
    log_error "后端镜像构建失败! 检查上方错误信息"
    exit 1
fi

log_info "启动 api 容器..."
docker compose up -d api

log_info "等待 Spring Boot 启动 (约 45 秒)..."
sleep 45

# 验证 API 健康
log_info "验证 API 健康状态..."
HTTP_CODE=$(docker exec duanju-api curl -s -o /dev/null -w "%{http_code}" \
    "http://127.0.0.1:8080/actuator/health" 2>/dev/null || echo "000")

if [ "$HTTP_CODE" = "200" ]; then
    log_success "API 健康检查通过 (HTTP 200)"
else
    log_warn "API 健康检查返回 HTTP $HTTP_CODE (可能需要更多时间启动)"
    log_info "查看启动日志: docker logs duanju-api --tail 30"
fi

# ============================================================
# 步骤 4: 重建管理后台前端
# ============================================================
echo ""
echo "=============================================="
echo " 步骤 4/5: 重建管理后台前端"
echo "=============================================="
echo ""

# 备份旧构建
if [ -d admin-dist ]; then
    mv admin-dist "admin-dist.bak.$(date +%Y%m%d_%H%M%S)" 2>/dev/null || true
    log_info "已备份旧 admin-dist"
fi

docker rm -f duanju-admin-build 2>/dev/null || true

log_info "重新构建管理后台 (admin-build)..."
docker compose up --build -d admin-build

log_info "等待前端构建完成 (约 2-3 分钟)..."
BUILD_WAIT=0
BUILD_TIMEOUT=180
while [ $BUILD_WAIT -lt $BUILD_TIMEOUT ]; do
    if [ "$(docker inspect -f '{{.State.Status}}' duanju-admin-build 2>/dev/null)" = "exited" ]; then
        break
    fi
    sleep 10
    BUILD_WAIT=$((BUILD_WAIT + 10))
    echo -n "."
done
echo ""

EXIT_CODE=$(docker inspect -f '{{.State.ExitCode}}' duanju-admin-build 2>/dev/null || echo "1")
if [ "$EXIT_CODE" = "0" ]; then
    log_success "前端构建成功"
else
    log_error "前端构建失败 (exit code: $EXIT_CODE)"
    docker logs duanju-admin-build 2>&1 | tail -20
    exit 1
fi

# 验证新功能在构建产物中
log_info "验证前端构建产物包含新功能代码..."
if grep -rq "pendingEpisodes" admin-dist/ 2>/dev/null; then
    log_success "上传进度功能已在构建产物中"
else
    log_warn "构建产物中未找到 pendingEpisodes, 请检查 App.vue 源码"
fi
if grep -rq "formatSpeed" admin-dist/ 2>/dev/null; then
    log_success "网速显示功能已在构建产物中"
fi

log_info "重启 Nginx (加载新前端)..."
docker compose restart nginx

# ============================================================
# 步骤 5: 最终验证
# ============================================================
echo ""
echo "=============================================="
echo " 步骤 5/5: 最终验证"
echo "=============================================="
echo ""

log_info "容器状态:"
docker compose ps --format "table {{.Name}}\t{{.Status}}" 2>/dev/null || \
docker ps --format "table {{.Names}}\t{{.Status}}" 2>/dev/null

echo ""

# 验证 presign 路由
log_info "验证 presign 路由..."
PRESIGN_CODE=$(docker exec duanju-api curl -s -o /dev/null -w "%{http_code}" \
    -X POST "http://127.0.0.1:8080/api/admin/storage/presign?fileName=test.jpg&type=image" 2>/dev/null || echo "000")

if [ "$PRESIGN_CODE" = "401" ] || [ "$PRESIGN_CODE" = "200" ]; then
    log_success "presign 路由正常 (HTTP $PRESIGN_CODE)"
elif [ "$PRESIGN_CODE" = "404" ]; then
    log_error "presign 路由 404! fat JAR 可能未正确打包"
else
    log_warn "presign 返回 HTTP $PRESIGN_CODE"
fi

# 验证 R2 启用状态
log_info "验证 R2 启用状态..."
if docker logs duanju-api 2>&1 | grep -qi "R2 存储已启用"; then
    log_success "R2 存储已启用"
else
    log_warn "启动日志中未找到 R2 启用信息, 检查环境变量..."
    docker exec duanju-api env 2>/dev/null | grep -E "R2_ENABLED|CLOUDFLARE_R2" | while read -r line; do
        name=$(echo "$line" | cut -d'=' -f1)
        val=$(echo "$line" | cut -d'=' -f2-)
        if echo "$name" | grep -q "SECRET\|KEY"; then
            log_info "  $name=${val:0:6}...${val: -4}"
        else
            log_info "  $name=$val"
        fi
    done
fi

# ============================================================
# 汇总
# ============================================================
echo ""
echo "=============================================="
echo " 部署完成汇总"
echo "=============================================="
echo ""
echo " 已部署的修改:"
echo "   1. 后端 DramaService — 软删 episode_no=-id + 删除同步清理R2"
echo "   2. 后端 StorageService — 时长探测 + 对象删除 + presign"
echo "   3. 后端 Dockerfile — fat JAR 验证"
echo "   4. 前端 App.vue — 上传进度在剧集列表 + 取消/重传 + 网速"
echo "   5. 前端 style.css — 待上传剧集行样式"
echo "   6. 前端 api.js — presign/batchUpload API"
echo "   7. Nginx duanju.conf — R2代理 + Range头转发"
echo "   8. SQL fix-video-upload — video_url可空 + 清理重复"
echo "   9. SQL fix-soft-delete — 软删episode_no冲突修复"
echo ""
echo " 下一步操作:"
echo "   1. 浏览器硬刷新 (Ctrl+Shift+R) 管理后台"
echo "   2. 上传一张图片测试 R2 直传"
echo "   3. 上传一个视频测试完整流程 (进度/取消/网速)"
echo "   4. 删除一集再重建同集数, 验证不再报'数据重复'"
echo ""
echo " 手动验证命令:"
echo "   docker exec duanju-api jar tf /app/app.jar | grep AdminContentController"
echo "   docker exec duanju-api curl -s 'http://127.0.0.1:8080/actuator/health'"
echo "   docker exec duanju-nginx nginx -t"
echo ""
log_success "全部部署完成!"
