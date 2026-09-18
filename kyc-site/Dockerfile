# ===============================
# 星幕短剧 Marastel — Dockerfile
# 多阶段构建：先构建前端，再合并到生产镜像
# ===============================

# ---- 阶段 1：前端构建 ----
FROM node:20-alpine AS builder
WORKDIR /app

# 先复制 package.json 利用 Docker 缓存
COPY package.json ./
RUN npm install --no-audit --no-fund

# 复制源码并构建
COPY . .
RUN npm run build

# ---- 阶段 2：生产镜像 ----
FROM node:20-alpine AS runner

ENV NODE_ENV=production
ENV PORT=3000

# 创建非 root 用户
RUN addgroup --system --gid 1001 nodejs && \
    adduser --system --uid 1001 --ingroup nodejs app

WORKDIR /app

# 只安装运行时依赖（不含 devDependencies）
COPY package.json ./
RUN npm install --omit=dev --no-audit --no-fund

# 复制后端代码和构建产物
COPY --from=builder /app/dist ./dist
COPY server ./server
COPY .env.example ./.env.example

# 创建数据目录并赋权（运行时表单数据写入 JSON）
RUN mkdir -p /app/server/data && \
    chown -R app:nodejs /app

USER app

EXPOSE 3000

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD node -e "fetch('http://localhost:3000/api/health').then(r=>r.ok?process.exit(0):process.exit(1)).catch(()=>process.exit(1))" || \
  wget -q --spider http://localhost:3000/api/health || exit 1

CMD ["node", "server/index.js"]
