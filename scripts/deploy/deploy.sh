#!/bin/bash
# ============================================================
# 短剧平台 Web 端 - 一键部署脚本
# 适用环境：Vultr Ubuntu 22.04 (1GB RAM / 40GB SSD)
# 用法：将项目上传到服务器 /opt/duanju 后执行： bash deploy.sh
# ============================================================
# 安全子域名方案 A：
#   srv.marastel.com  = API 后端接口
#   dash.marastel.com = Web 管理后台
#   web.marastel.com  = H5 移动端
# ============================================================

set -e

PROJECT_DIR="/opt/duanju"
cd "$PROJECT_DIR"

echo "================================================"
echo "  短剧平台 Web 端部署脚本"
echo "================================================"

# ---------- 1. 检查前置条件 ----------
echo ""
echo "[1/6] 检查前置条件..."

if ! command -v docker &> /dev/null; then
    echo "❌ Docker 未安装，正在安装..."
    curl -fsSL https://get.docker.com | sh
    usermod -aG docker root
fi

if ! docker compose version &> /dev/null; then
    echo "❌ Docker Compose 未安装"
    exit 1
fi

echo "✅ Docker: $(docker --version)"
echo "✅ Docker Compose: $(docker compose version)"

# ---------- 2. 创建 .env 文件 ----------
echo ""
echo "[2/6] 检查 .env 配置..."

if [ ! -f ".env" ]; then
    if [ -f ".env.example" ]; then
        cp .env.example .env
        echo "⚠️  已从 .env.example 复制，请编辑 .env 填入实际密码！"
        echo "   执行：nano .env"
        echo "   然后重新运行本脚本"
        exit 1
    else
        echo "❌ .env 和 .env.example 都不存在"
        exit 1
    fi
else
    # 检查是否还有占位符
    if grep -q "CHANGE_ME" .env; then
        echo "❌ .env 中还有 <CHANGE_ME> 占位符，请编辑填入实际值"
        echo "   nano .env"
        exit 1
    fi
    echo "✅ .env 已配置"
fi

# ---------- 3. 创建目录 ----------
echo ""
echo "[3/6] 创建部署目录..."
mkdir -p mysql-data redis-data admin-dist h5-dist logs uploads nginx/ssl
echo "✅ 目录就绪"

# ---------- 4. 清理旧容器（如果存在）----------
echo ""
echo "[4/6] 清理旧容器..."
docker compose down --remove-orphans 2>/dev/null || true
echo "✅ 清理完成"

# ---------- 5. 构建并启动 ----------
echo ""
echo "[5/6] 构建并启动服务（首次约 5-10 分钟）..."
echo "   - Maven 下载依赖 + 打包"
echo "   - npm install + npm run build"
echo "   - MySQL 初始化数据库"
echo ""

docker compose up -d --build

# ---------- 6. 等待并验证 ----------
echo ""
echo "[6/6] 等待服务启动..."
echo ""

# 等待 MySQL 就绪
echo "  等待 MySQL 就绪..."
for i in $(seq 1 30); do
    if docker compose exec -T mysql mysqladmin ping -h localhost -u root -p"${MYSQL_ROOT_PASSWORD}" --silent 2>/dev/null; then
        echo "  ✅ MySQL 已就绪"
        break
    fi
    echo "  ...等待 ($i/30)"
    sleep 2
done

# 等待 API 就绪
echo "  等待后端 API 就绪..."
for i in $(seq 1 30); do
    if curl -sf http://127.0.0.1:8080/actuator/health > /dev/null 2>&1; then
        echo "  ✅ API 已就绪"
        break
    fi
    echo "  ...等待 ($i/30)"
    sleep 3
done

# 等待 Nginx 就绪
echo "  等待 Nginx 就绪..."
sleep 3

# ---------- 打印结果 ----------
echo ""
echo "================================================"
echo "  部署完成！"
echo "================================================"
echo ""
echo "📊 容器状态："
docker compose ps
echo ""
echo "🔗 访问地址："
echo "   管理后台:  https://dash.marastel.com"
echo "   API:       https://srv.marastel.com"
echo "   健康检查:  https://srv.marastel.com/actuator/health"
echo ""
echo "⚠️  重要："
echo "   1. 首次启动请查看后端日志获取 admin 随机密码："
echo "      docker compose logs api | grep '密码'"
echo ""
echo "   2. 首次部署为 HTTP 模式，SSL 证书申请后启用 HTTPS："
echo "      sudo apt install -y certbot"
echo "      sudo certbot certonly --standalone -d srv.marastel.com -d dash.marastel.com -d web.marastel.com --email your@email.com --agree-tos"
echo ""
echo "   3. Cloudflare DNS 记录将 Proxy 改为橙云朵（Proxied）开启 CDN 加速"
echo "================================================"
