# 星幕短剧（Duanju）— 短剧流媒体平台

> 一个面向移动端的全栈短剧流媒体平台，集内容管理、视频托管、多渠道支付、会员体系、积分商城于一体。

## 目录

- [项目概览](#项目概览)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [目录结构](#目录结构)
- [核心功能模块](#核心功能模块)
- [后端架构详解](#后端架构详解)
- [前端架构详解](#前端架构详解)
- [数据库设计](#数据库设计)
- [基础设施与部署](#基础设施与部署)
- [环境变量配置](#环境变量配置)
- [本地开发指南](#本地开发指南)
- [API 概览](#api-概览)
- [安全约束](#安全约束)

---

## 项目概览

星幕短剧是一个完整的短剧流媒体运营平台，覆盖从内容生产到用户消费的全链路：

| 维度 | 说明 |
|------|------|
| **内容侧** | 管理后台创建短剧 → 上传分集视频（Cloudflare Stream）→ 配置封面/分类/定价 |
| **用户侧** | 移动端 App / H5 浏览短剧 → 免费试看 → 积分解锁/会员订阅 → 观看全剧 |
| **商业化** | VIP 会员订阅（Stripe / PayPal / Apple IAP / Google Play）+ 积分体系（签到 / 广告激励 / 充值）+ 积分商城兑换 |
| **多端** | uni-app 跨平台客户端（H5 / Android / iOS）+ Vue 3 管理后台 + KYC 官网 |

---

## 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 运行时 |
| Spring Boot | 3.3.5 | Web 框架 |
| MyBatis-Plus | 3.5.8 | ORM / 数据访问 |
| MySQL | 8.0 | 主数据库 |
| Redis | 7 | 缓存 / 验证码 / 限流 |
| Lombok | — | 样板代码消除 |
| AWS SDK for Java v2 | 2.28.28 | S3 兼容接口（Cloudflare R2） |
| Stripe Java SDK | 33.4.2 | Stripe 支付 |
| Apple StoreKit Library | 1.1.0 | Apple IAP 验签 |
| Google Auth Library | 1.23.0 | Google Play Developer API |
| Micrometer + Prometheus | — | 监控指标 |

### 前端

| 端 | 技术 | 说明 |
|----|------|------|
| 移动端 App / H5 | uni-app + Vue 2 + uview-ui | 跨平台，编译到 H5 / Android / iOS |
| 管理后台 | Vue 3 + Vite | SPA，部署于 Nginx |
| KYC 官网 | Vue 3 + Express | Stripe KYC 审核所需的官方网站 |
| 国际化 | i18n（前后端各自实现） | 多语言支持 |

### 基础设施

| 组件 | 用途 |
|------|------|
| Docker Compose | 容器编排，一键部署全部服务 |
| Nginx | 反向代理 / 静态资源托管 / CDN |
| Cloudflare Stream | 视频托管 + HLS 转码 + 签名 URL |
| Cloudflare R2 | 图片/头像对象存储（S3 兼容） |
| ffmpeg | HLS 转码（容器内集成） |

---

## 系统架构

```
                          ┌─────────────────────────────────────────────┐
                          │              Nginx (80/443)                 │
                          │  srv.marastel.com  → API 反向代理           │
                          │  dash.marastel.com → 管理后台 SPA           │
                          │  web.marastel.com  → H5 移动端              │
                          │  cdn.marastel.com  → 静态资源 / R2 代理      │
                          └───────────┬─────────────────────────────────┘
                                      │
              ┌───────────────────────┼───────────────────────┐
              │                       │                       │
              ▼                       ▼                       ▼
     ┌────────────────┐     ┌──────────────┐        ┌──────────────────┐
     │  Spring Boot   │     │   admin-dist │        │   h5-dist        │
     │   API (:8080)  │     │  (Vue 3 SPA) │        │  (uni-app H5)    │
     │                │     └──────────────┘        └──────────────────┘
     │  - JWT Auth    │
     │  - RBAC        │
     │  - 内容管理     │
     │  - 支付集成     │
     │  - 视频托管     │
     └───┬──────┬─────┘
         │      │
         ▼      ▼
  ┌──────────┐ ┌──────────┐
  │  MySQL   │ │  Redis   │
  │  8.0     │ │  7       │
  └──────────┘ └──────────┘
         │
         │  外部服务
         ▼
  ┌──────────────────────────────────────────────┐
  │  Cloudflare Stream  → 视频托管 / HLS / 签名URL │
  │  Cloudflare R2      → 图片 / 头像对象存储       │
  │  Stripe / PayPal    → Web 支付                 │
  │  Apple / Google     → IAP 内购验签             │
  └──────────────────────────────────────────────┘
```

---

## 目录结构

```
duanju-master/
├── backed/                        # 后端工程
│   └── server/                    # Spring Boot 服务
│       ├── pom.xml                # Maven 依赖
│       ├── Dockerfile             # 多阶段构建（Maven → JRE）
│       └── src/main/
│           ├── java/com/duanju/
│           │   ├── DuanjuApplication.java   # 启动类
│           │   ├── common/      # 通用类（R 统一响应、异常）
│           │   ├── config/      # 配置类（Web、异步、定时任务、i18n）
│           │   ├── controller/  # 控制器（Admin* + 业务）
│           │   ├── dto/         # 数据传输对象
│           │   ├── entity/      # 数据库实体
│           │   ├── interceptor/  # 拦截器（认证、国际化）
│           │   ├── mapper/      # MyBatis-Plus Mapper
│           │   ├── security/    # 安全（Principal、权限注解）
│           │   ├── service/     # 业务逻辑层
│           │   │   └── storage/ # 存储提供者（Local / R2）
│           │   └── util/        # 工具类
│           └── resources/
│               ├── application.yml          # 主配置
│               └── i18n/                   # 国际化资源
│       └── src/test/           # 测试
│   ├── sql/
│   │   ├── init.sql            # 数据库初始化脚本
│   │   └── migrations/         # 增量迁移脚本
│   └── uploads/                # 本地上传目录
├── front/                       # 前端工程
│   ├── uniapp/                 # uni-app 移动端
│   │   ├── pages/              # 页面（login, index, player, mine...）
│   │   ├── components/         # 公共组件
│   │   ├── static/            # 静态资源
│   │   ├── utils/             # 工具函数
│   │   ├── i18n/              # 国际化
│   │   ├── uview-ui/          # UI 组件库
│   │   ├── App.vue            # 根组件
│   │   ├── pages.json         # 页面路由配置
│   │   └── manifest.json      # App 配置（Android/iOS）
│   ├── admin/                  # Vue 3 管理后台
│   │   ├── src/
│   │   │   ├── pages/          # 管理页面
│   │   │   ├── components/     # 公共组件
│   │   │   ├── router/        # 路由
│   │   │   ├── i18n/          # 国际化
│   │   │   └── api.js         # API 封装
│   │   └── vite.config.js
│   └── shared/                 # 前端共享代码
├── kyc-site/                    # KYC 官网（Stripe 审核）
│   ├── src/                    # Vue 3 前端
│   ├── server/                 # Express 后端（邮件发送）
│   └── Dockerfile
├── nginx/                      # Nginx 配置
│   └── conf.d/
├── sql/                         # 运维 SQL 修复脚本
├── scripts/                     # 构建辅助脚本（i18n 同步等）
├── platform-tools/              # Android ADB 工具
├── tools/                       # cloudflared 等工具
├── docker-compose.yml           # 容器编排
├── .env.example                 # 环境变量模板
├── nginx-duanju.conf            # Nginx 基础配置
├── duanju-https.conf            # Nginx HTTPS 配置
└── duanju-https-dashapi.conf    # Nginx HTTPS + Dashboard API 配置
```

---

## 核心功能模块

### 1. 认证与权限（Auth & RBAC）

- **JWT 认证**：Access Token + Refresh Token 双令牌机制
- **验证码**：图形验证码（Captcha），支持 LOGIN / REGISTER / ADMIN_LOGIN 场景
- **安全防护**：IP 级别 + 账户级别的登录失败锁定
- **后台 RBAC**：管理员 → 角色 → 权限三级模型，基于 `@RequiresPermission` 注解的方法级权限控制

### 2. 内容管理（Content）

- **短剧管理**：创建/编辑短剧，配置封面（竖版 + 横版）、标签、分类筛选（背景/主题/设定/受众）、免费集数、单集定价、整剧定价
- **分集管理**：逐集或批量添加分集，关联 Cloudflare Stream 视频 UID
- **分类筛选**：可配置的前端分类页筛选维度，支持多语言
- **视频上传**：支持单集直传 + VPS 中转批量上传（TUS 断点续传）
- **HLS 转码**：容器内集成 ffmpeg，支持本地视频转 HLS

### 3. 会员与支付（Membership & Payment）

- **VIP 会员**：按月/季/年订阅，支持续费与自动续订
- **多渠道支付**：
  - **Stripe**：Checkout Sessions + Webhook 签名验签
  - **PayPal**：REST API + Webhook 验证
  - **Apple IAP**：StoreKit 服务端验签 + Server Notification V2
  - **Google Play**：本地签名验证 + Developer API 二次验签 + RTDN
- **退款**：统一的退款处理流程
- **货币汇率**：可配置的多币种汇率展示

### 4. 积分体系（Points）

- **积分获取**：每日签到、广告激励（含防刷限制）、充值赠送
- **积分消费**：单集解锁、整剧解锁
- **积分商城**：商品上架/兑换、订单管理、发货状态跟踪
- **积分过期**：可配置的过期策略与定时清理
- **统一入口**：所有积分变动通过 `PointService.addPoints()` 统一处理

### 5. 用户互动（User Engagement）

- **点赞**：短剧点赞，展示点赞数
- **评论**：分集评论 + 管理员回复
- **收藏**：短剧收藏夹
- **观看历史**：自动记录播放进度
- **通知**：系统公告 + 已读/未读管理
- **反馈**：用户意见反馈 + 管理员回复
- **邀请**：邀请奖励机制

### 6. 数据分析（Analytics）

- 播放事件追踪（`episode_play_event`）
- 管理后台仪表盘：用户增长、订单统计、内容热度
- Prometheus 指标暴露（`/actuator/prometheus`）

### 7. 国际化（i18n）

- 后端：Spring `MessageSource` + `LocaleInterceptor`（按 `Accept-Language` 路由）
- 前端：独立的 i18n 资源文件，支持多语言切换
- 管理后台：固定使用中文

---

## 后端架构详解

### 分层架构

```
Controller（参数校验 + 路由）
    ↓
Service（业务逻辑 + 事务边界）
    ↓
Mapper（MyBatis-Plus 数据访问）
    ↓
Entity（数据库映射）
```

**核心约束**：
- Controller 层只做参数校验和路由委托，不含业务逻辑
- `@Transactional` 注解放在 Service 层方法上
- Service 层依赖关系为 DAG（有向无环图），无循环依赖
- DTO 统一放在 `dto/` 包，不散落在 Controller 内

### Service 层核心服务

| Service | 职责 |
|---------|------|
| `AuthService` | 登录/注册/JWT 签发/验证码 |
| `DramaService` | 短剧 CRUD、分集管理、分类筛选 |
| `CloudflareStreamService` | 视频上传/TUS 断点续传/签名 URL/HLS |
| `StorageService` | 统一存储抽象（Local / R2 提供者切换） |
| `OrderService` | 订单创建/状态流转 |
| `StripePaymentService` | Stripe 支付集成 |
| `PayPalPaymentService` | PayPal 支付集成 |
| `AppleIapService` | Apple IAP 验签 |
| `GooglePlayIapService` | Google Play 验签 |
| `PointService` | 积分统一入口 |
| `UnlockService` | 分集解锁状态判断 |
| `MembershipService` | VIP 会员管理 |
| `BatchUploadService` | VPS 中转批量上传 |
| `VideoTranscodeService` | HLS 转码 |
| `I18nService` | 内容翻译 |
| `AnalyticsService` | 数据统计 |

### 存储抽象

```
StorageService（统一接口）
    ├── LocalStorageProvider   # 本地文件存储
    └── R2StorageProvider      # Cloudflare R2（S3 兼容）
```

通过 `DUANJU_STORAGE_PROVIDER` 环境变量切换，R2 启用后自动路由。

### 安全机制

- `SecurityStartupValidator`：prod 环境启动时检查密钥/密码是否为默认值，阻止不安全启动
- `AuthInterceptor`：JWT 令牌校验 + 权限拦截
- `ApiExceptionHandler`：全局异常兜底，不返回异常原始消息
- `SchemaMigrationRunner`：数据库 Schema 版本化管理

---

## 前端架构详解

### 移动端（uni-app）

**页面结构**（pages.json）：

| 页面 | 路径 | 功能 |
|------|------|------|
| 登录 | `pages/login/login` | 用户名/密码登录 |
| 首页（Feed） | `pages/index/index` | 竖屏短视频流，上下滑动切换 |
| 影院 | `pages/theater/theater` | 短剧列表浏览 |
| 详情 | `pages/detail/detail` | 短剧详情 + 分集列表 |
| 播放器 | `pages/player/player` | 视频播放页 |
| 商城 | `pages/store/store` | 积分商城 |
| 个人中心 | `pages/mine/mine` | 用户信息汇总 |
| 充值 | `pages/recharge/recharge` | 积分充值 |
| VIP | `pages/mine/vip` | 会员订阅 |
| 分类 | `pages/category/category` | 分类筛选 |

**原生能力**（manifest.json）：
- 支付：Apple IAP / Google Play Billing / Apple Pay / Google Pay（通过 PaymentBridge 插件）
- 视频：Exo 引擎播放
- 推送：Push 通知
- 摄像头/录音

### 管理后台（Vue 3 + Vite）

| 页面 | 功能 |
|------|------|
| Dashboard | 运营数据概览 |
| 内容管理 | 短剧/分集 CRUD + 视频上传 |
| 用户管理 | 前台用户列表/积分调整/状态管理 |
| 管理员管理 | 后台管理员/角色/权限 |
| 订单管理 | 订单列表/退款 |
| 会员管理 | VIP 订阅/自动续订 |
| 积分管理 | 积分产品/积分记录 |
| 商城管理 | 商品上下架/订单/发货 |
| 评论管理 | 评论审核/回复 |
| 公告管理 | 系统公告 |
| 反馈管理 | 用户反馈处理 |
| 汇率管理 | 币种汇率配置 |
| 系统设置 | 全局参数 |

---

## 数据库设计

数据库 `duanju`，MySQL 8.0，`utf8mb4` 字符集。核心表分组如下：

### 认证与权限

| 表 | 说明 |
|----|------|
| `admin_user` | 后台管理员 |
| `admin_role` | 角色权限 |
| `admin_user_role` | 管理员-角色关联（多对多） |
| `app_user` | 前台用户（含积分余额、JWT 令牌 ID） |
| `auth_captcha_record` | 验证码记录 |

### 内容管理

| 表 | 说明 |
|----|------|
| `drama` | 短剧主表（标题、封面、定价、分类筛选、热度值） |
| `drama_episode` | 短剧分集（视频 URL、Cloudflare UID、免费标记） |
| `drama_category_filter` | 前端分类筛选配置 |
| `content_translation` | 内容多语言翻译 |

### 用户行为

| 表 | 说明 |
|----|------|
| `user_like` | 点赞记录 |
| `drama_comment` | 评论及回复 |
| `user_favorite` | 收藏记录 |
| `user_watch_history` | 观看历史 |
| `episode_play_event` | 播放事件追踪 |
| `user_episode_unlock` | 分集解锁记录 |
| `user_feedback` | 用户反馈 |

### 支付与积分

| 表 | 说明 |
|----|------|
| `user_order` | 用户订单 |
| `user_membership` | 会员订阅记录 |
| `user_vip_record` | VIP 变更记录 |
| `auto_renewal_subscription` | 自动续订订阅 |
| `point_record` | 积分变动记录 |
| `point_expire` | 积分过期记录 |
| `point_product` | 积分充值产品 |
| `point_shop_item` | 商城商品 |
| `point_shop_order` | 商城兑换订单 |
| `user_checkin` | 签到记录 |
| `ad_reward_record` | 广告激励记录 |
| `user_invite` | 邀请记录 |

### 运营

| 表 | 说明 |
|----|------|
| `system_announcement` | 系统公告 |
| `announcement_read_record` | 公告已读记录 |
| `user_notification` | 用户通知 |
| `operation_log` | 操作日志 |
| `currency_rate` | 货币汇率 |

---

## 基础设施与部署

### Docker Compose 服务

| 服务 | 镜像 | 端口 | 依赖 |
|------|------|------|------|
| `mysql` | mysql:8.0 | 127.0.0.1:3306 | — |
| `redis` | redis:7-alpine | 127.0.0.1:6379 | — |
| `admin-build` | node:20-alpine | — | —（一次性构建） |
| `api` | duanju-api:latest | 127.0.0.1:8080 | mysql(healthy) + redis(healthy) |
| `nginx` | nginx:alpine | 80/443 | admin-build(completed) + api(started) |

### 域名规划

| 域名 | 用途 |
|------|------|
| `srv.marastel.com` | API 后端接口 |
| `dash.marastel.com` | Web 管理后台 |
| `web.marastel.com` | H5 移动端 |
| `cdn.marastel.com` | 静态资源 / R2 代理 |

### Nginx 配置

- **HTTP**（`nginx-duanju.conf`）：基础反代 + SPA 回退 + CDN 静态资源
- **HTTPS**（`duanju-https.conf`）：SSL 证书 + HTTP/2 + 安全头
- **CORS**：白名单域名模式，按 `$http_origin` 匹配放行

### 后端 Dockerfile（多阶段构建）

```
阶段 1：Maven 构建
  maven:3.9-eclipse-temurin-17
  → mvn clean package -DskipTests
  → 验证 fat jar 包含 BOOT-INF/classes（防止 repackage 被跳过）

阶段 2：运行时
  eclipse-temurin:17-jre-alpine
  → 安装 tzdata + curl + ffmpeg
  → JVM: -Xmx512m -XX:+UseG1GC
```

---

## 环境变量配置

复制 `.env.example` 为 `.env` 并填写实际值。关键变量：

| 变量 | 说明 | 必填 |
|------|------|------|
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 | ✅ |
| `REDIS_PASSWORD` | Redis 密码 | ✅ |
| `DUANJU_AUTH_SECRET` | JWT 签名密钥（≥32 字符） | ✅ |
| `DUANJU_CORS_ALLOWED_ORIGINS` | CORS 白名单（逗号分隔） | ✅ |
| `DUANJU_STORAGE_PROVIDER` | 存储方式（`local` / `r2`） | — |
| `R2_ENABLED` | 是否启用 Cloudflare R2 | — |
| `CLOUDFLARE_R2_*` | R2 连接配置 | R2 启用时 ✅ |
| `CLOUDFLARE_ENABLED` | 是否启用 Cloudflare Stream | — |
| `CLOUDFLARE_API_TOKEN` | Stream API Token | Stream 启用时 ✅ |
| `CLOUDFLARE_SIGNING_KEY` | 签名 URL 密钥 | Stream 启用时 ✅ |
| `STRIPE_ENABLED` | 是否启用 Stripe | — |
| `STRIPE_SECRET_KEY` | Stripe Secret Key | Stripe 启用时 ✅ |
| `PAYPAL_ENABLED` | 是否启用 PayPal | — |
| `APPLE_IAP_ENABLED` | 是否启用 Apple IAP | — |
| `GOOGLE_PLAY_ENABLED` | 是否启用 Google Play | — |
| `SPRING_PROFILES_ACTIVE` | Spring Profile（`prod`） | ✅ |
| `TZ` | 时区（`Asia/Singapore`） | — |

---

## 本地开发指南

### 前置条件

- Java 17+
- Maven 3.9+
- Node.js 20+
- MySQL 8.0+
- Redis 7+
- Docker & Docker Compose（推荐）

### 1. 使用 Docker Compose 一键启动

```bash
# 复制环境变量模板并填写
cp .env.example .env

# 启动全部服务
docker compose up -d --build

# 查看服务状态
docker compose ps
```

### 2. 本地开发（不用 Docker）

**后端**：

```bash
cd backed/server
mvn spring-boot:run
```

**管理后台**：

```bash
cd front/admin
npm install
npm run dev
```

**移动端（H5 模式）**：

```bash
cd front/uniapp
npm install
# 使用 HBuilderX 或 CLI 编译到 H5/Android/iOS
```

### 3. 数据库初始化

```bash
mysql -uroot -p < backed/sql/init.sql
```

---

## API 概览

### 公开接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/login` | 用户登录 |
| POST | `/api/auth/refresh` | 刷新令牌 |
| GET | `/api/dramas` | 短剧列表 |
| GET | `/api/dramas/{id}` | 短剧详情 |
| GET | `/api/videos/{id}/play` | 获取播放地址 |

### 用户接口（需认证）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/user/profile` | 个人信息 |
| POST | `/api/user/unlock` | 解锁分集 |
| POST | `/api/orders/create` | 创建订单 |
| POST | `/api/ad/reward` | 广告激励领取 |
| POST | `/api/user/checkin` | 每日签到 |
| GET | `/api/comments` | 评论列表 |

### 管理后台接口（需管理员认证）

| 前缀 | 说明 |
|------|------|
| `/api/admin/dashboard` | 运营数据 |
| `/api/admin/content` | 内容管理 |
| `/api/admin/users` | 用户管理 |
| `/api/admin/orders` | 订单管理 |
| `/api/admin/membership` | 会员管理 |
| `/api/admin/points` | 积分管理 |
| `/api/admin/shop` | 商城管理 |
| `/api/admin/comments` | 评论管理 |
| `/api/admin/announcements` | 公告管理 |
| `/api/admin/feedback` | 反馈管理 |

### Webhook 回调

| 路径 | 说明 |
|------|------|
| `/api/webhooks/stripe` | Stripe 支付回调 |
| `/api/webhooks/paypal` | PayPal 支付回调 |
| `/api/webhooks/cloudflare-stream` | Cloudflare Stream 状态回调 |
| `/api/webhooks/apple` | Apple Server Notification V2 |
| `/api/webhooks/google-play` | Google Play RTDN |

---

## 安全约束

以下约束在整个代码库中强制执行：

1. **密码/密钥**：禁止硬编码，必须通过环境变量注入（`application.yml` 仅保留开发默认值，`SecurityStartupValidator` 在 prod 下阻止使用默认值启动）
2. **CORS**：必须使用白名单域名，禁止 `*` 通配符
3. **异常处理**：兜底分支不返回 `Exception` 原始消息，仅返回通用提示并记录详细日志
4. **数据库迁移**：必须使用版本化管理（`SchemaMigrationRunner`），禁止启动时执行破坏性 DDL
5. **积分变动**：必须通过 `PointService.addPoints()` 统一处理，禁止直接调用 Mapper
6. **解锁状态**：必须使用 `UnlockService.isEpisodeAccessible()` 判断分集可访问性
7. **文件 I/O**：集中由 `StorageService` 处理，避免重复逻辑
8. **生产环境**：mock-pay 接口必须用 `@Profile("dev")` 隔离
9. **广告激励**：必须接入广告平台服务端回调签名校验，按 `(userId, traceId)` 唯一约束去重
10. **Cloudflare R2**：S3 client 必须使用 `Region.of("auto")`，Access Key ID 必须为 32 字符
11. **DROP TABLE**：必须按外键依赖逆序执行，避免约束错误
