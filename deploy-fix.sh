#!/bin/bash
# ============================================================
#  视频上传 4 层问题一键修复脚本
# ============================================================
#  用法：
#    1. 将本脚本和 fix-r2-url-prefix.sql 上传到服务器部署目录
#       (通常是 /opt/duanju-api/ 或 duanju-api/ 目录)
#    2. chmod +x deploy-fix.sh
#    3. ./deploy-fix.sh
#
# 修复内容：
#    L0: 修复数据库表结构（补全 drama_episode 缺失的 video_duration 列）
#    L1: 无缓存重建后端镜像（确保 presign 路由存在）
#    L2: 修正 .env 中 R2 变量名和 /r2 后缀
#    L3: 重建管理后台前端（确保 ORB 修复生效）
#    L4: 清理数据库中旧的 /r2/ URL 前缀
# ============================================================

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[✓]${NC} $1"; }
log_warn()    { echo -e "${YELLOW}[!]${NC} $1"; }
log_error()   { echo -e "${RED}[✗]${NC} $1"; }

# ============================================================
#  0. 环境检测
# ============================================================
echo ""
echo "=============================================="
echo "  视频上传 4 层问题一键修复"
echo "=============================================="
echo ""

# 检测部署目录：优先使用当前目录，其次尝试常见路径
DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DEPLOY_DIR"

if [ ! -f docker-compose.yml ]; then
    log_error "当前目录 ($DEPLOY_DIR) 没有 docker-compose.yml"
    log_info "请将本脚本放到部署目录（包含 docker-compose.yml 的目录）后再运行"
    exit 1
fi

log_info "部署目录: $DEPLOY_DIR"

# 检测 .env 文件
if [ ! -f .env ]; then
    log_warn "未找到 .env 文件，尝试从 .env.deploy 复制..."
    if [ -f .env.deploy ]; then
        cp .env.deploy .env
        log_success "已从 .env.deploy 复制生成 .env"
    else
        log_error "找不到 .env 也找不到 .env.deploy，无法继续"
        exit 1
    fi
fi

# 检测 SQL 修复文件
SQL_FILE="$DEPLOY_DIR/fix-r2-url-prefix.sql"
if [ ! -f "$SQL_FILE" ]; then
    log_warn "未找到 fix-r2-url-prefix.sql，L4 步骤将跳过"
    SKIP_L4=1
else
    SKIP_L4=0
fi

# 检测 SQL 修复文件
SCHEMA_SQL="$DEPLOY_DIR/fix-db-schema.sql"
VIDEO_FIX_SQL="$DEPLOY_DIR/fix-video-upload.sql"

# ============================================================
#  L0: 修复数据库（video_url NOT NULL + 清理重复数据）
# ============================================================
echo ""
echo "=============================================="
echo "  L0: 修复数据库（video_url 约束 + 重复数据）"
echo "=============================================="
echo ""

MYSQL_ROOT_PASSWORD=$(grep "^MYSQL_ROOT_PASSWORD=" .env | cut -d'=' -f2-)

# L0a: 修复 video_url NOT NULL 约束 + 清理重复
if [ -f "$VIDEO_FIX_SQL" ]; then
    log_info "执行 video_url 约束修复 + 重复数据清理..."
    docker exec -i duanju-mysql mysql -u root -p"$MYSQL_ROOT_PASSWORD" duanju < "$VIDEO_FIX_SQL" 2>/dev/null
    log_success "L0a 完成：video_url 已改为可空，重复数据已清理"
else
    log_warn "未找到 fix-video-upload.sql，跳过 L0a"
fi

# L0b: 补全可能缺失的列
if [ -f "$SCHEMA_SQL" ]; then
    log_info "检查并补全表结构..."
    docker exec -i duanju-mysql mysql -u root -p"$MYSQL_ROOT_PASSWORD" duanju < "$SCHEMA_SQL" 2>/dev/null
    log_success "L0b 完成：表结构已检查/补全"
else
    log_warn "未找到 fix-db-schema.sql，跳过 L0b"
fi

# ============================================================
#  L2: 修正 .env 中 R2 变量（最优先，因为重建需要正确的变量）
# ============================================================
echo ""
echo "=============================================="
echo "  L2: 修正 .env 中 R2 环境变量"
echo "=============================================="
echo ""

fix_env_var() {
    local old_name="$1"
    local new_name="$2"
    local value="$3"

    if grep -q "^${new_name}=" .env; then
        # 新变量名已存在，检查值是否正确
        local current_val
        current_val=$(grep "^${new_name}=" .env | head -1 | cut -d'=' -f2-)
        if [ "$current_val" = "$value" ]; then
            log_success "$new_name 已正确配置"
            return 0
        fi
    fi

    # 处理旧变量名
    if grep -q "^${old_name}=" .env; then
        local old_val
        old_val=$(grep "^${old_name}=" .env | head -1 | cut -d'=' -f2-)
        # 删除旧变量行
        sed -i "/^${old_name}=/d" .env
        log_warn "删除旧变量名 $old_name (值: $old_val)"
    fi

    # 添加/更新新变量
    if grep -q "^${new_name}=" .env; then
        sed -i "s|^${new_name}=.*|${new_name}=${value}|" .env
    else
        # 在 R2 区域末尾添加
        echo "${new_name}=${value}" >> .env
    fi
    log_success "设置 $new_name=$value"
}

# 获取当前 .env 中的 R2 相关值（兼容新旧变量名）
get_env_val() {
    local val=""
    # 先试新变量名
    val=$(grep "^$1=" .env 2>/dev/null | head -1 | cut -d'=' -f2- || true)
    if [ -z "$val" ] && [ -n "${2:-}" ]; then
        # 再试旧变量名
        val=$(grep "^$2=" .env 2>/dev/null | head -1 | cut -d'=' -f2- || true)
    fi
    echo "$val"
}

# 读取当前值（兼容新旧变量名）
R2_ENABLED_VAL=$(get_env_val "R2_ENABLED" "CLOUDFLARE_R2_ENABLED")
R2_ACCOUNT_ID_VAL=$(get_env_val "CLOUDFLARE_R2_ACCOUNT_ID")
R2_ACCESS_KEY_VAL=$(get_env_val "CLOUDFLARE_R2_ACCESS_KEY_ID" "CLOUDFLARE_R2_ACCESS_KEY")
R2_SECRET_KEY_VAL=$(get_env_val "CLOUDFLARE_R2_SECRET_ACCESS_KEY" "CLOUDFLARE_R2_SECRET_KEY")
R2_BUCKET_VAL=$(get_env_val "CLOUDFLARE_R2_BUCKET")
R2_ENDPOINT_VAL=$(get_env_val "CLOUDFLARE_R2_ENDPOINT")
R2_PUBLIC_URL_VAL=$(get_env_val "CLOUDFLARE_R2_PUBLIC_BASE_URL")

# 如果 account-id 缺失，尝试从 endpoint 中提取
if [ -z "$R2_ACCOUNT_ID_VAL" ] && [ -n "$R2_ENDPOINT_VAL" ]; then
    # 从 https://xxx.r2.cloudflarestorage.com 中提取 xxx
    R2_ACCOUNT_ID_VAL=$(echo "$R2_ENDPOINT_VAL" | sed -n 's|https://\([^.]*\)\.r2\.cloudflarestorage\.com|\1|p')
    if [ -n "$R2_ACCOUNT_ID_VAL" ]; then
        log_info "从 endpoint 中提取到 account-id: $R2_ACCOUNT_ID_VAL"
    fi
fi

# 如果 CLOUDFLARE_ACCOUNT_ID 存在但 R2 的没有，复用之
if [ -z "$R2_ACCOUNT_ID_VAL" ]; then
    CF_ACCOUNT_ID=$(get_env_val "CLOUDFLARE_ACCOUNT_ID")
    if [ -n "$CF_ACCOUNT_ID" ]; then
        R2_ACCOUNT_ID_VAL="$CF_ACCOUNT_ID"
        log_info "复用 CLOUDFLARE_ACCOUNT_ID 作为 R2 account-id"
    fi
fi

# 修复 PUBLIC_BASE_URL：去掉末尾的 /r2
R2_PUBLIC_URL_FIXED="$R2_PUBLIC_URL_VAL"
if echo "$R2_PUBLIC_URL_VAL" | grep -q '/r2/*$'; then
    # 去掉末尾的 /r2 或 /r2/
    R2_PUBLIC_URL_FIXED=$(echo "$R2_PUBLIC_URL_VAL" | sed 's|/r2/*$||')
    log_warn "PUBLIC_BASE_URL 带 /r2 后缀，已修正: $R2_PUBLIC_URL_VAL → $R2_PUBLIC_URL_FIXED"
fi

# 备份 .env
cp .env .env.bak.$(date +%Y%m%d_%H%M%S)
log_info "已备份 .env"

# 先删除所有旧格式的 R2 变量行（防止重复/混乱）
sed -i '/^CLOUDFLARE_R2_ENABLED=/d' .env
sed -i '/^CLOUDFLARE_R2_ACCESS_KEY=/d' .env
sed -i '/^CLOUDFLARE_R2_SECRET_KEY=/d' .env

# 现在用正确变量名重写 R2 配置
# 先找到 R2 区域的位置（在 R2 注释附近），如果找不到就追加到文件末尾

# 检查是否已有 R2 注释标记
if grep -q "Cloudflare R2" .env; then
    # 删除旧的 R2 配置块（从 R2 注释行开始，到下一个 ====== 或空行+# 结束）
    # 简单处理：删除所有 R2 相关变量行，然后在 R2 注释后插入正确的
    sed -i '/^R2_ENABLED=/d' .env
    sed -i '/^CLOUDFLARE_R2_ACCOUNT_ID=/d' .env
    sed -i '/^CLOUDFLARE_R2_ACCESS_KEY_ID=/d' .env
    sed -i '/^CLOUDFLARE_R2_SECRET_ACCESS_KEY=/d' .env
    sed -i '/^CLOUDFLARE_R2_BUCKET=/d' .env
    sed -i '/^CLOUDFLARE_R2_ENDPOINT=/d' .env
    sed -i '/^CLOUDFLARE_R2_PUBLIC_BASE_URL=/d' .env

    # 在 "Cloudflare R2" 注释行后插入正确配置
    R2_CONFIG=$(cat <<EOF
R2_ENABLED=${R2_ENABLED_VAL:-true}
CLOUDFLARE_R2_ACCOUNT_ID=${R2_ACCOUNT_ID_VAL:-}
CLOUDFLARE_R2_ACCESS_KEY_ID=${R2_ACCESS_KEY_VAL:-}
CLOUDFLARE_R2_SECRET_ACCESS_KEY=${R2_SECRET_KEY_VAL:-}
CLOUDFLARE_R2_BUCKET=${R2_BUCKET_VAL:-duanju}
CLOUDFLARE_R2_ENDPOINT=${R2_ENDPOINT_VAL:-}
CLOUDFLARE_R2_PUBLIC_BASE_URL=${R2_PUBLIC_URL_FIXED:-}
EOF
)

    # 使用 awk 在 "Cloudflare R2" 注释行后插入
    awk -v config="$R2_CONFIG" '
        /Cloudflare R2/ { print; print config; next }
        { print }
    ' .env > .env.tmp && mv .env.tmp .env

    log_success "已在 R2 注释块后写入正确的变量"
else
    # 没有 R2 区域，追加到末尾
    cat >> .env <<EOF

# ====== Cloudflare R2 对象存储 ======
R2_ENABLED=${R2_ENABLED_VAL:-true}
CLOUDFLARE_R2_ACCOUNT_ID=${R2_ACCOUNT_ID_VAL:-}
CLOUDFLARE_R2_ACCESS_KEY_ID=${R2_ACCESS_KEY_VAL:-}
CLOUDFLARE_R2_SECRET_ACCESS_KEY=${R2_SECRET_KEY_VAL:-}
CLOUDFLARE_R2_BUCKET=${R2_BUCKET_VAL:-duanju}
CLOUDFLARE_R2_ENDPOINT=${R2_ENDPOINT_VAL:-}
CLOUDFLARE_R2_PUBLIC_BASE_URL=${R2_PUBLIC_URL_FIXED:-}
EOF
    log_success "已追加 R2 配置到 .env 末尾"
fi

# 验证修复结果
echo ""
log_info "验证 .env 中的 R2 变量："
grep -E "^(R2_ENABLED|CLOUDFLARE_R2_)" .env | sort | while read -r line; do
    name=$(echo "$line" | cut -d'=' -f1)
    val=$(echo "$line" | cut -d'=' -f2-)
    # 敏感信息打码
    if echo "$name" | grep -q "SECRET\|ACCESS_KEY"; then
        masked_val="${val:0:6}...${val: -4}"
        log_info "  $name=$masked_val"
    else
        log_info "  $line"
    fi
done

# 关键变量非空检查
MISSING_VARS=0
for var in R2_ENABLED CLOUDFLARE_R2_ACCOUNT_ID CLOUDFLARE_R2_ACCESS_KEY_ID CLOUDFLARE_R2_SECRET_ACCESS_KEY CLOUDFLARE_R2_BUCKET CLOUDFLARE_R2_ENDPOINT CLOUDFLARE_R2_PUBLIC_BASE_URL; do
    val=$(grep "^${var}=" .env | cut -d'=' -f2-)
    if [ -z "$val" ]; then
        log_warn "$var 为空，R2 可能无法启用"
        MISSING_VARS=$((MISSING_VARS + 1))
    fi
done

if [ "$MISSING_VARS" -gt 0 ]; then
    log_warn "有 $MISSING_VARS 个 R2 变量为空，请检查 .env 配置"
else
    log_success "L2 修复完成：R2 变量名和值均已修正"
fi

# ============================================================
#  L1: 无缓存重建后端 API 镜像
# ============================================================
echo ""
echo "=============================================="
echo "  L1: 无缓存重建后端 API 镜像"
echo "=============================================="
echo ""

log_info "停止旧的 api 容器..."
docker compose stop api 2>/dev/null || true
docker compose rm -f api 2>/dev/null || true

log_info "删除旧镜像（清缓存）..."
docker rmi duanju-api:latest 2>/dev/null || true

log_info "无缓存构建 api 镜像（这可能需要 3-5 分钟）..."
docker compose build --no-cache api

log_info "启动 api 容器..."
docker compose up -d api

log_info "等待 Spring Boot 启动（约 45 秒）..."
sleep 45

# 验证 presign 路由是否存在
log_info "验证 presign 路由..."
HTTP_CODE=$(docker exec duanju-api curl -s -o /dev/null -w "%{http_code}" \
    -X POST "http://127.0.0.1:8080/api/admin/storage/presign?fileName=test.jpg&type=image" 2>/dev/null || echo "000")

if [ "$HTTP_CODE" = "401" ] || [ "$HTTP_CODE" = "200" ]; then
    log_success "L1 修复完成：presign 路由存在（HTTP $HTTP_CODE，401=未授权属正常）"
elif [ "$HTTP_CODE" = "404" ]; then
    log_error "L1 失败：presign 路由仍然 404，新代码未打进 jar"
    log_info "请检查：1. 源码是否含 presignStorage 方法  2. Dockerfile 是否正确 COPY src"
    exit 1
else
    log_warn "L1 验证：presign 返回 HTTP $HTTP_CODE（可能服务未完全启动，稍后可手动验证）"
fi

# 查看 R2 启动日志
if docker logs duanju-api 2>&1 | grep -qi "R2.*启用\|R2.*enabled\|presigner-enabled"; then
    log_success "R2 存储已在后端启用（启动日志确认）"
else
    log_warn "启动日志中未找到 R2 启用信息，请检查 R2 配置是否完整"
fi

# ============================================================
#  L3: 重建管理后台前端
# ============================================================
echo ""
echo "=============================================="
echo "  L3: 重建管理后台前端（ORB 修复）"
echo "=============================================="
echo ""

# 备份旧构建
if [ -d admin-dist ]; then
    mv admin-dist "admin-dist.bak.$(date +%Y%m%d_%H%M%S)"
    log_info "已备份旧 admin-dist"
fi

# 删除旧的 admin-build 容器
docker rm -f duanju-admin-build 2>/dev/null || true

log_info "重新构建管理后台（admin-build）..."
docker compose up --build -d admin-build

# 等待构建完成
log_info "等待前端构建完成（约 2-3 分钟）..."
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

# 检查构建结果
EXIT_CODE=$(docker inspect -f '{{.State.ExitCode}}' duanju-admin-build 2>/dev/null || echo "1")
if [ "$EXIT_CODE" = "0" ]; then
    log_success "前端构建成功"
else
    log_error "前端构建失败（exit code: $EXIT_CODE）"
    docker logs duanju-admin-build 2>&1 | tail -20
    exit 1
fi

# 验证 ORB 修复是否在构建产物中
if grep -rq "orbBypassed" admin-dist/ 2>/dev/null; then
    log_success "L3 修复完成：ORB 修复代码已在构建产物中"
else
    log_warn "构建产物中未找到 orbBypassed，请检查前端源码是否包含 L3 修复"
fi

# 重启 nginx 使新构建生效
log_info "重启 nginx..."
docker compose restart nginx

# ============================================================
#  L4: 数据库 URL 前缀修复
# ============================================================
echo ""
echo "=============================================="
echo "  L4: 修复数据库旧 /r2/ URL 前缀"
echo "=============================================="
echo ""

if [ "$SKIP_L4" = "1" ]; then
    log_warn "跳过 L4：未找到 fix-r2-url-prefix.sql"
else
    # 先备份数据库
    log_info "备份数据库..."
    MYSQL_ROOT_PASSWORD=$(grep "^MYSQL_ROOT_PASSWORD=" .env | cut -d'=' -f2-)
    BACKUP_FILE="duanju_backup_pre_l4_$(date +%Y%m%d_%H%M%S).sql"
    docker exec duanju-mysql mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" \
        --databases duanju --default-character-set=utf8mb4 > "$BACKUP_FILE" 2>/dev/null
    log_success "数据库已备份到 $BACKUP_FILE"

    # 统计修复前的数量
    log_info "修复前统计（含 /r2/ 前缀的记录数）..."
    docker exec duanju-mysql mysql -u root -p"$MYSQL_ROOT_PASSWORD" duanju -e "
SELECT
  (SELECT COUNT(*) FROM drama WHERE cover_url LIKE '%/r2/%') AS drama_cover,
  (SELECT COUNT(*) FROM drama WHERE horizontal_cover_url LIKE '%/r2/%') AS drama_horizontal,
  (SELECT COUNT(*) FROM drama WHERE vertical_cover_url LIKE '%/r2/%') AS drama_vertical,
  (SELECT COUNT(*) FROM drama_episode WHERE cover_url LIKE '%/r2/%') AS ep_cover,
  (SELECT COUNT(*) FROM drama_episode WHERE video_url LIKE '%/r2/%') AS ep_video;
" 2>/dev/null

    # 执行修复 SQL
    log_info "执行 URL 前缀修复 SQL..."
    docker exec -i duanju-mysql mysql -u root -p"$MYSQL_ROOT_PASSWORD" duanju < "$SQL_FILE" 2>/dev/null

    # 验证修复结果
    log_info "修复后验证..."
    REMAINING=$(docker exec duanju-mysql mysql -u root -p"$MYSQL_ROOT_PASSWORD" duanju -N -e "
SELECT
  (SELECT COUNT(*) FROM drama WHERE cover_url LIKE '%/r2/%') +
  (SELECT COUNT(*) FROM drama WHERE horizontal_cover_url LIKE '%/r2/%') +
  (SELECT COUNT(*) FROM drama WHERE vertical_cover_url LIKE '%/r2/%') +
  (SELECT COUNT(*) FROM drama_episode WHERE cover_url LIKE '%/r2/%') +
  (SELECT COUNT(*) FROM drama_episode WHERE video_url LIKE '%/r2/%') +
  (SELECT COUNT(*) FROM app_user WHERE avatar_url LIKE '%/r2/%') +
  (SELECT COUNT(*) FROM admin_user WHERE avatar LIKE '%/r2/%');
" 2>/dev/null)

    if [ "$REMAINING" = "0" ]; then
        log_success "L4 修复完成：所有 /r2/ 前缀已清理（剩余 $REMAINING 条）"
    else
        log_warn "L4 修复后仍有 $REMAINING 条记录含 /r2/ 前缀（可能是未影响到的表）"
    fi
fi

# ============================================================
#  最终汇总
# ============================================================
echo ""
echo "=============================================="
echo "  修复完成汇总"
echo "=============================================="
echo ""
echo "部署目录: $DEPLOY_DIR"
echo ""
echo -e "  ${GREEN}L1${NC} 后端 presign 路由  - 已无缓存重建并验证"
echo -e "  ${GREEN}L2${NC} R2 环境变量        - 已修正变量名 + 去 /r2 后缀"
echo -e "  ${GREEN}L3${NC} 前端 ORB 拦截修复   - 已重新构建管理后台"
echo -e "  ${GREEN}L4${NC} 数据库 URL 前缀     - 已执行 SQL 修复（如适用）"
echo ""
echo "下一步操作："
echo "  1. 浏览器硬刷新（Ctrl+Shift+R）管理后台"
echo "  2. 上传一张小图片测试 R2 直传是否成功"
echo "  3. 如仍有问题，查看各容器日志："
echo "     docker logs duanju-api --tail 50"
echo "     docker logs duanju-admin-build"
echo ""
echo "备份文件："
echo "  .env 备份: $(ls -1t .env.bak.* 2>/dev/null | head -1 || echo '无')"
echo "  旧前端备份: $(ls -1dt admin-dist.bak.* 2>/dev/null | head -1 || echo '无')"
echo "  数据库备份: ${BACKUP_FILE:-无}"
echo ""
log_success "全部修复完成！"
