#!/bin/bash
# ============================================================
# 星幕短剧 Marastel · 一键部署脚本
# 目标：Ubuntu / Debian（带 Docker & Nginx）
# 用法：  bash deploy.sh            # Docker 方案（推荐）
#        bash deploy.sh pm2       # PM2 直接跑 Node
# ============================================================
set -e

APP_NAME="marastel-kyc"
APP_DIR="/var/www/$APP_NAME"
DOMAIN="star.marastel.com"
MODE="${1:-docker}"

echo "======================================"
echo " 星幕短剧 Marastel · 生产部署"
echo " 模式: $MODE"
echo "======================================"

# ---- 0. 前置检查 ----
command -v nginx >/dev/null 2>&1 || { echo "[ERR] 未检测到 nginx"; exit 1; }

if [ "$MODE" = "docker" ]; then
    command -v docker >/dev/null 2>&1 || { echo "[ERR] 未检测到 docker"; exit 1; }
    command -v docker compose >/dev/null 2>&1 || { echo "[ERR] 未检测到 docker compose"; exit 1; }
fi

# ---- 1. 上传代码 ----
echo ""
echo "[1/5] 同步代码到 $APP_DIR"
sudo mkdir -p "$APP_DIR"
sudo chown -R $USER:$USER "$APP_DIR"
rsync -avz --delete \
  --exclude='node_modules' \
  --exclude='dist' \
  --exclude='.git' \
  --exclude='logs' \
  ./ "$APP_DIR"/

# ---- 2. 安装依赖 + 构建前端 ----
echo ""
echo "[2/5] 安装依赖并构建前端"
cd "$APP_DIR"
npm install --no-audit --no-fund --loglevel=error
npm run build

# ---- 3. 启动服务 ----
echo ""
if [ "$MODE" = "docker" ]; then
    echo "[3/5] Docker Compose 构建并启动"
    cd "$APP_DIR"
    docker compose up -d --build --remove-orphans
    docker compose ps
else
    echo "[3/5] PM2 启动 Node 服务"
    command -v pm2 >/dev/null 2>&1 || { npm install -g pm2; }
    mkdir -p "$APP_DIR/logs"
    cd "$APP_DIR"
    pm2 start ecosystem.config.cjs --env production
    pm2 save
    pm2 startup || true
    echo "   PM2 监听 $PORT，Nginx 代理 :3000"
fi

# ---- 4. Nginx 配置 ----
echo ""
echo "[4/5] 安装 Nginx 配置 → /etc/nginx/sites-available/star.marastel.com.conf"
if [ "$MODE" = "docker" ]; then
    # Docker: proxy_pass http://app:3000（compose 内部网络）
    sed 's|proxy_pass http://localhost:3000|proxy_pass http://app:3000|g' \
        "$APP_DIR/nginx/star.marastel.com.conf" \
        | sudo tee /etc/nginx/sites-available/star.marastel.com.conf >/dev/null
else
    # PM2: proxy_pass http://localhost:3000
    cp "$APP_DIR/nginx/star.marastel.com.conf" \
        /tmp/star.conf
    sed -i 's|proxy_pass http://app:3000|proxy_pass http://localhost:3000|g' \
        /tmp/star.conf
    sudo cp /tmp/star.conf /etc/nginx/sites-available/star.marastel.com.conf
fi

sudo ln -sf /etc/nginx/sites-available/star.marastel.com.conf \
            /etc/nginx/sites-enabled/star.marastel.com.conf
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx

# ---- 5. SSL ----
echo ""
echo "[5/5] Let's Encrypt SSL"
if [ ! -d "/etc/letsencrypt/live/$DOMAIN" ]; then
    sudo certbot --nginx \
        -d "$DOMAIN" -d "www.$DOMAIN" \
        --non-interactive --agree-tos \
        --email "trendameasor@mail.com" \
        --redirect
else
    echo "SSL 证书已存在，跳过"
    sudo certbot renew --dry-run 2>/dev/null || true
fi

# ---- Done ----
echo ""
echo "======================================"
echo " 部署完成！"
echo " 站点：https://$DOMAIN"
if [ "$MODE" = "docker" ]; then
    echo " 状态：docker compose ps"
    echo " 日志：docker compose logs -f app"
else
    echo " 状态：pm2 status"
    echo " 日志：pm2 logs marastel-kyc"
fi
echo " 重启：bash deploy.sh"
echo "======================================"
