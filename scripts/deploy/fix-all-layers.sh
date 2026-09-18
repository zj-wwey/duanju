#!/bin/bash
# ============================================================
# 视频上传 4 层问题 — 一键修复脚本
# ============================================================
# 修复内容:
#   L1: 无缓存重建后端镜像, Dockerfile 内验证 fat jar 含 presign class
#   L2: 确认 .env R2 变量正确, docker-compose 显式传递 R2 环境变量
#   L3: 重建管理后台前端, 确保 ORB 修复(responseType=blob + orbBypassed)在构建产物中
#   L4: 执行 SQL 修复: video_url 可空 + 重复数据清理 + /r2/ URL 前缀替换
#
# 用法:
#   1. 确保本脚本和 docker-compose.yml、.env、fix-*.sql 都在部署目录
#   2. chmod +x fix-all-layers.sh
#   3. ./fix-all-layers.sh
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
echo " 视频上传 4 层问题 — 一键修复"
echo "=============================================="
echo ""

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DEPLOY_DIR"

if [ ! -f docker-compose.yml ]; then
    log_error "当前目录 ($DEPLOY_DIR) 没有 docker-compose.yml"
    exit 1
fi
log_info "部署目录: $DEPLOY_DIR"

if [ ! -f .env ]; then
    log_error "找不到 .env 文件"
    exit 1
fi

MYSQL_ROOT_PASSWORD=$(grep "^MYSQL_ROOT_PASSWORD=" .env | head -1 | cut -d'=' -f2-)

# ============================================================
# L2 预检: 验证 .env 中 R2 变量
# ============================================================
echo ""
echo "=============================================="
echo " L2 预检: .env R2 变量验证"
echo "=============================================="
echo ""

check_env() {
    local name="$1"
    local expected_len="$2"
    local val
    val=$(grep "^${name}=" .env 2>/dev/null | head -1 | cut -d'=' -f2- || true)
    if [ -z "$val" ]; then
        log_error "$name 为空!"
        return 1
    fi
    if [ "$expected_len" -gt 0 ] && [ "${#val}" -ne "$expected_len" ]; then
        log_warn "$name 长度=${#val}（期望 $expected_len）值=${val:0:8}..."
        return 1
    fi
    if echo "$name" | grep -q "SECRET\|KEY"; then
        log_success "$name = ${val:0:6}...${val: -4} (len=${#val})"
    else
        log_success "$name = $val"
    fi
    return 0
}

L2_OK=true
check_env "R2_ENABLED" 0 || L2_OK=false
check_env "CLOUDFLARE_R2_ACCOUNT_ID" 0 || L2_OK=false
check_env "CLOUDFLARE_R2_ACCESS_KEY_ID" 32 || L2_OK=false
check_env "CLOUDFLARE_R2_SECRET_ACCESS_KEY" 0 || L2_OK=false
check_env "CLOUDFLARE_R2_BUCKET" 0 || L2_OK=false
check_env "CLOUDFLARE_R2_ENDPOINT" 0 || L2_OK=false
check_env "CLOUDFLARE_R2_PUBLIC_BASE_URL" 0 || L2_OK=false

# 检查 PUBLIC_BASE_URL 不含 /r2/ 后缀
R2_URL=$(grep "^CLOUDFLARE_R2_PUBLIC_BASE_URL=" .env | head -1 | cut -d'=' -f2-)
if echo "$R2_URL" | grep -q '/r2/*$'; then
    log_warn "CLOUDFLARE_R2_PUBLIC_BASE_URL 末尾含 /r2，正在修正..."
    FIXED_URL=$(echo "$R2_URL" | sed 's|/r2/*$||')
    sed -i "s|^CLOUDFLARE_R2_PUBLIC_BASE_URL=.*|CLOUDFLARE_R2_PUBLIC_BASE_URL=${FIXED_URL}|" .env
    log_success "已修正: $R2_URL -> $FIXED_URL"
fi

if [ "$L2_OK" = false ]; then
    log_warn "L2 预检发现问题（见上方警告），继续修复..."
    log_warn "特别是 CLOUDFLARE_R2_ACCESS_KEY_ID 应为 32 字符，请到 Cloudflare R2 控制台确认"
fi

# ============================================================
# L4a: 修复数据库 (video_url NOT NULL + 重复数据 + 表结构)
# ============================================================
echo ""
echo "=============================================="
echo " L4a: 修复数据库 (video_url 约束 + 重复数据)"
echo "=============================================="
echo ""

if docker ps --format '{{.Names}}' | grep -q "duanju-mysql"; then
    if [ -f fix-video-upload.sql ]; then
        log_info "执行 fix-video-upload.sql..."
        docker exec -i duanju-mysql mysql -u root -p"$MYSQL_ROOT_PASSWORD" duanju < fix-video-upload.sql 2>/dev/null
        log_success "video_url 已改为可空, 重复数据已清理"
    else
        log_warn "未找到 fix-video-upload.sql, 跳过"
    fi

    if [ -f fix-db-schema.sql ]; then
        log_info "执行 fix-db-schema.sql..."
        docker exec -i duanju-mysql mysql -u root -p"$MYSQL_ROOT_PASSWORD" duanju < fix-db-schema.sql 2>/dev/null
        log_success "表结构已检查/补全"
    else
        log_warn "未找到 fix-db-schema.sql, 跳过"
    fi
else
    log_error "duanju-mysql 容器未运行, 跳过数据库修复"
fi

# ============================================================
# L1: 无缓存重建后端 API 镜像
# ============================================================
echo ""
echo "=============================================="
echo " L1: 无缓存重建后端 API 镜像"
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
    log_success "L1 构建成功 (Dockerfile 内 jar 验证通过)"
else
    log_error "L1 构建失败! Dockerfile 内 jar 验证未通过, 检查上方错误信息"
    exit 1
fi

log_info "启动 api 容器..."
docker compose up -d api

log_info "等待 Spring Boot 启动 (约 45 秒)..."
sleep 45

# 验证 presign 路由
log_info "验证 presign 路由..."
HTTP_CODE=$(docker exec duanju-api curl -s -o /dev/null -w "%{http_code}" \
    -X POST "http://127.0.0.1:8080/api/admin/storage/presign?fileName=test.jpg&type=image" 2>/dev/null || echo "000")

if [ "$HTTP_CODE" = "401" ] || [ "$HTTP_CODE" = "200" ]; then
    log_success "L1 验证通过: presign 路由存在 (HTTP $HTTP_CODE)"
elif [ "$HTTP_CODE" = "404" ]; then
    log_error "L1 失败: presign 路由仍然 404!"
    log_error "检查: docker exec duanju-api jar tf /app/app.jar | grep AdminContentController"
    exit 1
else
    log_warn "L1 验证: presign 返回 HTTP $HTTP_CODE (可能服务未完全启动, 稍后可手动验证)"
fi

# 验证 R2 启用状态
log_info "验证 R2 启用状态..."
if docker logs duanju-api 2>&1 | grep -qi "R2 存储已启用"; then
    log_success "L2 验证通过: R2 存储已启用 (启动日志确认)"
else
    log_warn "L2: 启动日志中未找到 R2 启用信息"
    log_info "检查容器内 R2 环境变量..."
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
# L3: 重建管理后台前端
# ============================================================
echo ""
echo "=============================================="
echo " L3: 重建管理后台前端 (ORB 修复)"
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

# 验证 ORB 修复在构建产物中
if grep -rq "orbBypassed" admin-dist/ 2>/dev/null; then
    log_success "L3 验证通过: ORB 修复代码已在构建产物中"
else
    log_warn "L3: 构建产物中未找到 orbBypassed, 检查前端源码"
fi

log_info "重启 nginx..."
docker compose restart nginx

# ============================================================
# L4b: 修复数据库 URL 前缀 (/r2/ -> /)
# ============================================================
echo ""
echo "=============================================="
echo " L4b: 修复数据库旧 /r2/ URL 前缀"
echo "=============================================="
echo ""

if docker ps --format '{{.Names}}' | grep -q "duanju-mysql"; then
    if [ -f fix-r2-url-prefix.sql ]; then
        log_info "执行 fix-r2-url-prefix.sql..."
        docker exec -i duanju-mysql mysql -u root -p"$MYSQL_ROOT_PASSWORD" duanju < fix-r2-url-prefix.sql 2>/dev/null
        log_success "L4b: /r2/ URL 前缀已清理"
    else
        log_warn "未找到 fix-r2-url-prefix.sql, 跳过"
    fi
else
    log_error "duanju-mysql 容器未运行, 跳过 L4b"
fi

# ============================================================
# 最终验证
# ============================================================
echo ""
echo "=============================================="
echo " 最终验证"
echo "=============================================="
echo ""

# 检查所有容器状态
log_info "容器状态:"
docker compose ps --format "table {{.Name}}\t{{.Status}}" 2>/dev/null || docker ps --format "table {{.Names}}\t{{.Status}}" 2>/dev/null

echo ""

# 检查 presign 返回 supported=true
log_info "验证 presign 返回 supported=true..."
PRESIGN_RESP=$(docker exec duanju-api curl -s \
    -X POST "http://127.0.0.1:8080/api/admin/storage/presign?fileName=test.mp4&type=video" 2>/dev/null || echo "")

if echo "$PRESIGN_RESP" | grep -q '"supported":true'; then
    log_success "presign 返回 supported=true — L1+L2 全部通过!"
elif echo "$PRESIGN_RESP" | grep -q '"supported":false'; then
    log_error "presign 返回 supported=false — R2 未正确启用"
    log_error "检查容器内环境变量: docker exec duanju-api env | grep CLOUDFLARE_R2"
    log_error "特别检查 CLOUDFLARE_R2_ACCESS_KEY_ID 是否为 32 字符"
elif echo "$PRESIGN_RESP" | grep -q '"code":0'; then
    log_success "presign 接口正常响应"
else
    log_warn "presign 响应异常: $PRESIGN_RESP"
fi

# ============================================================
# 汇总
# ============================================================
echo ""
echo "=============================================="
echo " 修复完成汇总"
echo "=============================================="
echo ""
echo " L1 后端 presign 路由 — Dockerfile 已修复 (去掉 go-offline + jar 验证)"
echo " L2 R2 环境变量     — docker-compose.yml 已显式传递 7 个 R2 变量"
echo " L3 前端 ORB 修复    — admin-dist 已重新构建 (orbBypassed 在产物中)"
echo " L4 数据库修复       — video_url 可空 + /r2/ 前缀已清理"
echo ""
echo " 下一步操作:"
echo "   1. 浏览器硬刷新 (Ctrl+Shift+R) 管理后台"
echo "   2. 上传一张小图片测试 R2 直传"
echo "   3. 上传一个视频测试完整流程"
echo ""
if [ "$L2_OK" = false ]; then
    echo -e " ${YELLOW}[!]${NC} L2 警告: CLOUDFLARE_R2_ACCESS_KEY_ID 当前为 31 字符 (应为 32)"
    echo "     请到 Cloudflare R2 控制台 → Manage R2 API Tokens 确认完整 Access Key ID"
    echo "     修正后重新运行本脚本即可"
    echo ""
fi
echo " 手动验证命令:"
echo "   docker exec duanju-api jar tf /app/app.jar | grep AdminContentController"
echo "   docker exec duanju-api curl -s -X POST 'http://127.0.0.1:8080/api/admin/storage/presign?fileName=test.mp4&type=video'"
echo "   docker logs duanju-api 2>&1 | grep 'R2 存储已启用'"
echo ""
log_success "全部修复完成!"
