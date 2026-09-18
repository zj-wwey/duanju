# 短剧 App 迁移 TikTok 海外小程序（TikTok Minis · Mini Drama）落地方案

> 文档版本：v2.0（海外版，替代原中国区抖音方案）　编制日期：2026-09-12
> 适用项目：duanju-master（移动端工程 `front/uniapp`，后端 `backed/server`）
> 说明：本方案只做规划，不包含任何代码改动。TikTok 海外小程序仍处于定向合作+快速迭代期，平台规则、SDK 与分成政策可能调整，标注「需对接确认」的事项以签约时 TikTok 对接人/官方文档为准。

---

## 一、目标与结论

### 1.1 目标
把现有移动端 App（uni-app 工程）的核心体验（短剧 Feed 流、选集播放、免费试看、付费解锁、积分、VIP、激励广告、收藏评论等）以 **TikTok 海外小程序（TikTok Minis 中的 Mini Drama 小程序）** 形态上线，在 TikTok 端内完成「刷到视频 → 点锚点 → 看剧 → 付费/看广告解锁 → 复访」的闭环，首批面向东南亚/北美等已开放市场。

### 1.2 核心结论（先看这 7 条）

1. **技术形态和国内抖音小程序完全不同，对你反而更有利**。TikTok Minis 是**运行在 TikTok 客户端 WebView 里的 H5 网页应用框架**（官方原话：H5-based product framework），不是 TTML 原生小程序。**现有 uni-app 工程直接出 H5 包**，再注入 TikTok Minis JS SDK 即可，**不用走 mp-toutiao 编译、不用重写页面**，业务代码复用率预计 90%+。
2. **变现走 TikTok 虚拟币 Beans（IAP）+ 激励广告（IAA）**，不能用 Stripe/PayPal 外链支付（容器内禁止外链收银台）。用户在 App Store/Google Play 充值 Beans，看剧时用 Beans 下单解锁；服务端调 TikTok OpenAPI 下单、接收支付成功 Webhook 后发货。**iOS/安卓都能付费**，不存在国内抖音「iOS 禁止虚拟支付」的限制。
3. **两条合作路径要先想清楚**：① **Minis 小程序**（本方案，自营产品、用户和数据归自己、IAP+IAA 变现，类似 NetShort/ShortMax 模式）；② **Drama Center/Drama Series 内容分账**（把片单交给 TikTok 发行、平台免费+广告分账，类似红果/PineDrama 模式，开发量极小但用户不归你）。建议**以①为主，②作为部分片库的并行发行渠道**。
4. **准入主体是门槛**：开发者平台只接受**组织（企业）账号**；需要海外主体（业内普遍推荐**新加坡公司**，中国香港主体目前不能上架美国区）、企业认证、**短剧发行方行业资质审核（Industry Qualification Review）**；上架美国还要过 **USDS TPRM 审查（约 15~30 个工作日）**；需提供已上线代表作品证明发行资质。官方口径注册到上线约 1 个月。
5. **已开放市场**：美国、日本、印尼、泰国、菲律宾、越南、马来西亚、巴西、沙特、土耳其（韩国也在灰度，需对接确认）。美国收入最高但审查最严；东南亚上线快、免费+广告模式接受度高。建议**东南亚先行、美国并行过审**。
6. **后端新增约 5 个接口/模块**：TikTok 登录换 token、服务端 client_token 管理、Beans 下单、支付 Webhook 验签发货、广告奖励回调。订单/积分/解锁/会员/多语言表结构全部可复用。
7. **Cloudflare 媒资在海外是优势不是风险**（与国内版相反）：R2+Stream 本就是全球网络，重点变成 **WebView 内的播放兼容性**——安卓 TikTok WebView 是 Chromium 内核，原生 `<video>` 不支持 HLS(m3u8)，大概率需要引入 hls.js 或由后端对小程序端下发 mp4，这是前端必须最早验证的技术点。

### 1.3 预计周期

| 阶段 | 内容 | 周期 |
|---|---|---|
| 阶段 0 | 企业主体/认证、行业资质、USDS 审查、开通 IAP/IAA、拿 client_key | 3~6 周（与开发并行，美国审查最久） |
| 阶段 1 | H5 构建 + Minis CLI/SDK 跑通、真机调试环境 | 2~3 天 |
| 阶段 2 | TikTok 登录 + 后端鉴权 | 2~3 天 |
| 阶段 3 | Feed/播放（含 HLS 兼容）与全站页面适配 | 4~6 天 |
| 阶段 4 | Beans 支付下单/Webhook/发货/对账 | 4~5 天 |
| 阶段 5 | 激励广告 IAA、分享/锚点、站内复访 | 2~3 天 |
| 阶段 6 | 提审、灰度、Growth Max 投流准备 | 1~2 周 |

净开发约 **2~3 周**；总周期主要取决于主体资质与美国审查。

---

## 二、平台背景：TikTok 海外短剧生态与两条路径

### 2.1 生态现状（2026 年）

- 2025 年底 TikTok 在主 App 上线 **TikTok Minis** 专区（搜索 "Minis" 或个人主页菜单进入），2026 年升级为端内小程序专区，短剧是其中最核心品类，已有 NetShort、ShortMax、Starshort、SnackShort、TopShort 等十余个短剧小程序入驻。
- 官方数据：2026 年 Q1 TikTok 短剧为合作方分账超 2400 万美元、流量环比增长 400%；并推出最高 20 倍收益激励系数招募本土剧/首发/独家/AI 漫剧内容。
- 商业化闭环全部留在 TikTok 内：用户从信息流短视频/搜索/Minis Center/侧边栏进入，免费试看 8~10 集后，按集/整剧付费或看广告解锁；投流用 **TikTok Growth Max**，支持按 D0 IAA/IAP tROAS（当天广告支出回报率）出价。
- TikTok 同时有独立免费短剧 App **PineDrama**（红果模式），说明平台在推「免费+广告分账」，未来付费习惯可能被分流，**小程序需要 IAP+IAA 混合变现对冲**。

### 2.2 两条合作路径对比

| 维度 | 路径 A：TikTok Minis 短剧小程序（本方案） | 路径 B：Drama Center 内容分账（Drama Series） |
|---|---|---|
| 产品归属 | 自有品牌小程序，UI/付费点/会员体系自己定 | TikTok Business Account 官号发布，平台播放器 |
| 用户与数据 | **用户 open_id、观看/付费数据落自有后端** | 用户归平台，只能看到平台统计面板 |
| 变现 | Beans IAP（单集/整剧/会员）+ 激励广告 IAA | 平台广告分账/激励系数，免费模式为主 |
| 技术工作量 | 中（本方案，2~3 周） | 极小（片单上传/API 对接，1~2 周） |
| 入口 | 短视频锚点、搜索、Minis Center、分享、侧边栏 | For You 信息流、Drama Center |
| 适合内容 | 全量片库，付费剧 | 适合拿来做免费引流/独家置换激励的剧集 |
| 风险 | 平台规则与抽成变化 | 议价权弱、无用户沉淀 |

**建议**：先按路径 A 把小程序做成（复用现有 App 全部能力）；运营侧同步在 [drama.tiktok.com](https://drama.tiktok.com/) 注册 Drama Center，挑部分剧集走路径 B 拿流量激励和分账，两条腿走路。

### 2.3 已开放市场与上架策略

- 已开放：美国、日本、印尼、泰国、菲律宾、越南、马来西亚、巴西、沙特、土耳其（韩国等市场灰度中，需对接确认）。
- 主体经验：海外主体必备；**新加坡主体综合最优**（可上美国区）；中国香港主体目前不能上架美国区。
- 越南对内容类有额外许可要求（需对接确认最新政策）；中东市场关注内容与宗教审查；各市场版权授权区域必须与上架区域一致。
- 建议第一批：**印尼/菲律宾/泰国（上线快、IAA 接受度高）+ 美国（IAP 收入最高，同步过审）**；日语/葡语/阿语市场第二批。

---

## 三、准入、资质与账号（阶段 0，第一天就启动）

### 3.1 注册与认证清单

1. 访问 [developers.tiktok.com](https://developers.tiktok.com/) 注册开发者账号 → **My Organizations → Create Organization**（仅组织账号；组织名须为主体全称且创建后不可改，会展示给用户）。
2. 在组织下 **Create App**，类型选择 Mini Drama / Minis 类应用。
3. **Verify Your Business**：提交海外公司注册文件完成企业认证。
4. **Industry Qualification Review**：短剧发行方行业资质审核，需证明：
   - 合法的短剧发行/经营资质；
   - **片库版权授权链**（每部剧的发行权、且授权区域覆盖上架国家）；代理发行需上传版权方授权书；
   - 提交**已上线代表作品链接**（Google Play / App Store 链接、微信/抖音小程序等均可，证明行业发行资质；代表作与本次上架内容不必一致）。
5. **Launch Your App to U.S. Users（USDS TPRM）**：仅上架美国需要，数据与安全审查，平台处理约 **15~30 个工作日**，务必最早提交。
6. 开通变现：开发者后台 **Monetization → Enable IAA / IAP**（至少开一个），在线签合同；填写公司信息、**税务信息（W-8/W-9 等）**、收款账户（收款主体须与认证主体一致）。创建广告位（Ad Placement）与 Beans 商品档位。
7. 向 TikTok 对接人申请开通 Minis 能力，获取 **client_key / client_secret**（SDK 初始化与服务端调用凭证）。当前部分能力仍需定向邀请，需主动对接 BD。
8. 控制台基础配置：应用名称/图标/简介（与商店一致、无敏感内容）、多语言信息、隐私政策与服务条款 URL、开发配置（域名白名单、回调地址）、测试白名单 TikTok 账号。

### 3.2 合规要点（提审硬性项）

- **版权区域匹配**：仅在授权地区上架对应剧集，后台按国家做内容可见性配置。
- **付费透明**：试看集数、单集/整剧/会员价格（Beans 标价）、扣币规则、订单查询路径、充值与消耗明细、退款规则、未成年人不可购买提示，均需在支付前明示。
- **隐私合规**：Privacy Policy 覆盖 GDPR（欧盟虽暂不在首批也建议覆盖）、CCPA（美国）、东南亚各国个人数据法；明确收集 open_id、观看记录、设备信息；提供数据删除/联系渠道。
- **用户举报 SLA**：用户可就体验/支付/内容举报，开发者须在 **72 小时内**在后台 User Reports 处理。
- 内容分级、成人向题材边界、广告内容规范遵循各市场法律与 TikTok 社区规则。

---

## 四、技术方案总览

### 4.1 架构

```
                         TikTok App（iOS/Android WebView 容器）
 ┌──────────────────────────────────────────────────────────────┐
 │  现有 uni-app 工程发行 H5（unpackage/dist/build/web）           │
 │  + 构建后注入 TTMinis SDK（connect.tiktok-minis.com/drama/sdk.js）│
 │  页面：Feed/剧场/详情/播放器/商店/个人中心（全部复用现有 .vue）     │
 │  平台能力桥：登录 TTMinis.login · 支付 TTMinis.pay · 激励广告     │
 └───────────────┬───────────────────────────────┬───────────────┘
                 │ HTTPS（trustedDomains 白名单）    │ 无（不存在外链支付）
                 ▼                                 ▼
        自有 Spring Boot 后端              TikTok Open API
  （新增 TikTok 渠道 Service/Webhook）     open.tiktokapis.com
   订单/积分/解锁/会员/内容全部复用          OAuth2 · trade_order · 用户信息
                 │                                 ▲
                 └──────── 支付成功 Webhook 验签 ───┘
                              媒资：Cloudflare R2 + Stream（全球加速）
```

### 4.2 为什么是 H5 方案而不是编译原生小程序

- 官方定义：Mini Dramas Program 是 **H5-based product framework**；开发用任意前端框架（React/Vue 均可），官方 CLI 只负责生成配置、本地调试桥、构建校验和打包。
- TikTok Minis JS SDK（`TTMinis`）提供：登录授权、支付、激励广告、UI 触发器、生命周期钩子、网络能力、功能检测等——全部运行在普通网页 JS 环境里。
- 因此本工程的正确产物是 **H5 发行包**（HBuilderX「发行 → 网站-PC 或手机 H5」），`manifest.json` 中的 `mp-toutiao` 配置**用不上、不需要维护**。
- 约束：代码包 ZIP ≤ **50MB**；禁止 `eval()`、`iframe` 等（打包校验会拦截）；所有网络请求域名须进白名单且为 HTTPS。

### 4.3 开发工具链

| 工具 | 说明 |
|---|---|
| Node.js ≥ 18（推荐 20.x） | CLI 运行环境 |
| `@tiktok-minis/cli`（`npm i -g @tiktok-minis/cli`，注意用官方 npm 源） | `ttdx minis init` 生成 `minis.config.json`；`ttdx minis debug` 真机联调；`ttdx minis build:after` 构建校验出 ZIP |
| TikTok 内测客户端 | 安卓用官方内测 APK（按地区分 M 包=US 等、T 包=JP 等）；iOS 需上传代码包后扫码预览 |
| HBuilderX | 继续作为 uni-app H5 构建工具，不变 |

`minis.config.json` 关键项（放在 `front/uniapp` 工程根目录）：

- `dev.port / dev.host`：本地调试端口与主机（手机与电脑同一局域网，host 需写电脑局域网 IP）；
- `build.outputDir`：指向 H5 产物目录，如 `unpackage/dist/build/web`；
- `build.htmlEntry`：`index.html`；
- `domain.trustedDomains / allowList`：API 与媒资 HTTPS 域名白名单（如 `https://api.your-domain.com/`、`https://cdn.marastel.com/`、Cloudflare Stream 域名）。

### 4.4 SDK 接入方式（适配 uni-app 的关键点）

uni-app 发行 H5 时 `index.html` 由构建过程重新生成，直接在源文件加 SDK 会被覆盖。标准做法（实施时做，本次不写代码）：

1. 在工程根目录新增 `scripts/inject-minis-sdk.js`，H5 构建完成后把以下片段自动注入产物 `unpackage/dist/build/web/index.html` 的 `<head>`，并做幂等避免重复注入：

   ```html
   <script src="https://connect.tiktok-minis.com/drama/sdk.js"></script>
   <script>TTMinis.init({ clientKey: '你的_client_key' });</script>
   ```

2. 在工程 `package.json` 增加脚本：H5 构建 → 注入 SDK → `ttdx minis build:after` 打包；调试走「起本地静态服务（带 CORS）→ `ttdx minis debug` → 手机 TikTok 扫码」。
3. 业务代码通过全局 `window.TTMinis` 调能力，并统一封装到一个 `utils/tiktok-minis.js`（登录/支付/广告/分享/环境检测），**页面只调封装层**，与 App、浏览器 H5 用条件分支隔离。
4. 真机调试注意：电脑手机同一 WiFi、均能访问外网；测试账号需在后台加白；安卓客户端前台保持常亮，息屏断连；**不支持热更新**，改代码要重新构建 H5 再 debug。

---

## 五、前端改造方案（基于现有工程的具体差异）

### 5.1 构建与路由配置

| 项 | 现状 | Minis 处置 |
|---|---|---|
| 发行产物 | App 为主，H5 已配置（history 路由、`jiaoyu.xianmxkj.com`） | 新增 Minis 专用 H5 构建：**路由建议改 hash 模式**（容器内本地加载资源，避免 history 刷新 404）；通过环境变量区分普通 H5 与 Minis 构建 |
| API 基地址 | H5 生产用相对路径 `/api` | Minis 内网页与 API 不同源，**必须配绝对 HTTPS 域名**（环境变量注入），不能用 `/api` |
| `utils/config.js` | 按 APP-PLUS/H5 区分 | 增加 Minis 构建分支；检测 `window.TTMinis` 存在时视为小程序环境 |
| `utils/request.js` | `X-Client-Type` 仅 APP/H5/WEB | 新增 `TIKTOK_MINIS` 类型；401 刷新、URL 重写逻辑原样复用 |
| CORS | 后端按白名单放行 | 在 `DUANJU_CORS_ALLOWED_ORIGINS` 增加 TikTok 容器来源（对接时确认实际 Origin；不带 Cookie 时可按规则放行其来源，仍保持白名单制，不用 `*`） |
| 禁用项 | web-view 支付中转页、`window.location` 跳外链 | Minis 构建中**排除 `pages/webview` 及任何 iframe/外链收银台**；`store.vue` 的 H5 外链支付分支仅在普通浏览器保留 |
| 包体 | — | 静态图走 CDN 或精简，产物 ZIP ≤ 50MB（现有静态资源很小，无忧） |

### 5.2 页面处置一览

| 页面/能力 | 处置 |
|---|---|
| 首页 Feed（`pages/index`） | 直接复用。现有 H5 分支的信息层/进度条（非 nvue 版）即为 Minis 生效版本；真机校准 WebView 手势与层级 |
| 剧场/分类/详情/选集 | 直接复用；解锁弹层接 Beans 支付 |
| 播放器（`pages/player`） | 复用，重点做 WebView 全屏与 HLS 兼容（见第八章） |
| 商店/充值（`store`/`recharge`） | **支付逻辑改造**：Minis 环境走 Beans；保留浏览器 H5 的 Stripe/PayPal 分支 |
| 登录（`login`） | 默认改为 TikTok 一键静默登录；账号密码登录收为次要入口 |
| 个人中心及全部子页（积分/订单/收藏/历史/会员/VIP/商城/反馈/公告/设置） | 全部复用；订单新增 Beans 渠道展示；多语言 18 语种原样保留（首批按市场开放） |
| webview 支付中转页 | Minis 构建移除 |
| 视频本地下载（`utils/videoDownloader.js`） | 不接入（仅 APP-PLUS 分支存在，天然隔离） |
| 推送（unipush） | 无此能力；用 TikTok 端内订阅/通知能力（若已开放）或运营触达替代 |

---

## 六、登录与用户体系

### 6.1 登录链路

```
小程序启动（容器内 H5）
 → TTMinis.init({ clientKey })
 → TTMinis.login() 静默拿临时 code（无需用户点按钮）
 → 前端 POST /api/auth/tiktok/session { code }
 → 后端用 client_key + client_secret 调
   POST https://open.tiktokapis.com/v2/oauth/token/
   换 open_id（以及 scope 允许时的用户访问令牌）
 → 后端按 open_id 查第三方绑定表：
      已绑定 → 直接签发本系统 JWT（复用现有 token/refreshToken 机制）
      未绑定 → 自动创建受限账号（默认头像昵称），需要时再 TTMinis.authorize
               显式授权，通过 /v2/user/info/ 拿昵称/头像补资料
 → 前端存 token，之后所有请求与 App 完全一致
```

- 需要用户头像昵称时用 **TTMinis.authorize 显式授权**（用户已产生使用行为后再弹，授权率更高），不要在启动时强弹。
- 老用户合并：提供「绑定已有账号」入口（账号密码校验后把 open_id 绑到原 user_id），积分/订单/解锁记录不丢失。
- client_secret 只在服务端使用；服务端调用 OpenAPI 的 **client_token 做缓存**（Redis，项目已具备），不要每次请求换 token。

### 6.2 后端新增

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/tiktok/session` | code 换 open_id 并登录，返回 `{token, refreshToken, user, bound}`，加入鉴权白名单 |
| POST | `/api/auth/tiktok/bind` | 已登录用户绑定 open_id |
| POST | `/api/user/profile/tiktok` | 显式授权后同步昵称/头像（服务端调 `/v2/user/info/`） |

新增第三方绑定表（Flyway 版本化脚本）：`user_third_account(id, user_id, platform['TIKTOK'], openid, unionid, nickname, avatar, created_at, updated_at)`，`(platform, openid)` 唯一索引。

环境变量：`TIKTOK_MINIS_CLIENT_KEY`、`TIKTOK_MINIS_CLIENT_SECRET`、`TIKTOK_MINIS_WEBHOOK_SECRET`，全部走环境变量注入（遵循项目密钥管理约束）。

---

## 七、支付方案：TikTok Beans（IAP）

### 7.1 Beans 体系与定价

- 用户在 TikTok 内通过 App Store/Google Play 购买 Beans 充值包；商品以 Beans 标价（如「解锁第 6 集 15 Beans」「整部剧 420 Beans」）。
- 平台按开发者合同结算（Beans 渠道内含苹果/谷歌通道成本，具体分成比例以合同为准；推广期平台曾有减免/激励政策，需对接确认）。业内反馈有 **T+1 提现**政策，签约时确认。
- 两种扣款模式：
  - **充值+支付合并（官方推荐，短剧必选）**：余额不足时原生弹窗引导充值并一步完成支付，转化最高；
  - 充值/支付分离：先 `CheckBalance`，不足跳充值页再回来支付——步骤多，不建议短剧使用。
- 与现有商品体系映射：在 `point_product`/会员套餐上新增 **Beans 价格字段或渠道映射表**（按市场配置不同 Beans 价，对应现有 `products-by-locale` 多币种定价）。积分仍然是站内资产：**用户付 Beans → 后端发货为积分/VIP/解锁权**，解锁/扣费链路继续走 `UnlockService`、`PointService.addPoints()`。

### 7.2 交易链路

```
用户点「用 15 Beans 解锁本集」
 → 前端调自有后端 POST /api/user/orders（复用！payChannel=TIKTOK_BEANS，带商品/剧集信息）
 → 后端建本地订单(PENDING)，再调 TikTok 服务端 OpenAPI：
   POST https://open.tiktokapis.com/v2/minis/trade_order/create/
   参数：access_token(服务端 client_token)、open_id、price_amount(Beans 数)、
        order_detail(JSON：product_id/product_name 等)、回调相关配置
 ← 返回 trade_order_id
 → 前端拿到 trade_order_id 调 TTMinis.pay({ tradeOrderId })
 → TikTok 原生收银台（合并充值）→ 用户 FaceID/指纹确认
 → 【发货只认 Webhook】TikTok 服务端推送事件：
   minis.trade_order.redeem.success
 → 后端验签 → 校验订单与金额 → 幂等发货（积分/VIP/解锁）→ 更新订单 PAID
 → 前端 success 回调只做 UI 轮询/提示，以查单结果为准；掉单走主动查单补偿
```

前端调用形态（封装在支付桥内）：

```js
TTMinis.pay({
  tradeOrderId: '服务端下单返回的 trade_order_id',
  success(res) { /* is_success 仅表示支付流程完成，提示处理中并轮询订单 */ },
  fail(err)  { /* 取消/失败提示与重试 */ }
})
```

### 7.3 必须落实的后端规则

- **下单只在服务端**：trade_order 绝不能在前端构造。
- **Webhook 验签 + 幂等**：以 trade_order_id/本地 orderNo 去重，重复回调只发货一次；原始回调写入支付事件日志（复用 `PaymentEventLogService`）。
- **主动查单兜底**：新增 PENDING 订单定时对账（复用 `ScheduledTaskConfig`），回调延迟/丢失时主动调 TikTok 查单接口。
- **退款**：对接 TikTok 退款 OpenAPI 与退款回调，复用 `RefundService`。
- `payChannel` 新增枚举 `TIKTOK_BEANS`；订单/积分明细页可展示 Beans 金额。
- 严格遵循项目分层：支付逻辑在新 `TikTokMinisPaymentService`，`@Transactional` 在 Service 层，发货只走 PointService，不产生服务循环依赖。

### 7.4 与现有支付渠道的关系

| 渠道 | 独立 App | 普通手机浏览器 H5 | TikTok Minis |
|---|---|---|---|
| Apple IAP / Google Play | ✅ 保留 | — | ❌（Beans 已含平台通道） |
| Stripe / PayPal | — | ✅ 保留（外链收银台） | ❌ 禁止外链支付 |
| TikTok Beans | — | — | ✅ 唯一付费方式 |

前端在 `store.vue` 按运行环境三分支处理，互不影响现有 App/H5 上线版本。

---

## 八、播放与媒体方案（重点兼容性验证）

### 8.1 播放链路

- 继续使用现有鉴权接口 `GET /api/user/video/play/{episodeId}`（免费/已解锁校验后返回地址，优先级 `hls_url` → `signed_url` → mp4）与 `/api/user/video/renew/{episodeId}`（签名 URL 1 小时续期）。
- Minis 内就是标准 H5 `<video>`：iOS TikTok WebView（WKWebView/Safari 内核）**原生支持 HLS**；**安卓 Chromium 内核原生不支持 m3u8**，必须二选一：
  1. 前端引入 **hls.js**（MSE 播放），在 Minis/H5 构建中挂到 video 元素（首选，Cloudflare Stream 已是 HLS）；
  2. 后端对 Minis 渠道优先下发 **mp4** 地址（`playback.js` 的回退逻辑已有雏形，扩展渠道判断即可）。
- **这是阶段 1 必须最先做真机验证的点**（美区/东南亚各取一台安卓+iOS），验证起播、拖拽、连播、全屏。

### 8.2 WebView 播放体验注意

- **自动播放策略**：浏览器内核通常禁止带声音自动播放。现有 Feed 依赖 autoplay，需真机确认 TikTok 容器是否放行；不放行则改为「滑到当前集 → 首次轻点播放」的交互兜底，并保留播放/暂停 PNG 图标状态逻辑。
- 全屏：用 `playsinline` + webkit 全屏 API；锁定为页内全屏方案，避免容器导航栏冲突。
- Feed 性能：保持现有窗口化渲染（仅当前集 ±1 挂载 video，其余封面），切集释放实例；快速滑动竞态、后台暂停、音轨叠加重点回归。
- 自定义进度条：H5 下现有 cover-view 分支即普通 DOM，直接按既定抖音样式（白轨道 4rpx/填充 8rpx/24rpx 滑块、左右时间、120rpx 缩进）校准；边界 Toast（"已是第一部"/"已经到底了"，>50rpx 触发）保留。
- 签名 URL 续期：长剧连看在 error/定时触发 renew，无感刷新。

### 8.3 媒资与域名

- **Cloudflare R2/Stream 在海外是合适选择**（与国内版相反），无需 ICP 备案；仍需：HTTPS、完整证书链、域名加入 Minis `trustedDomains` 与控制台白名单。
- 按上架区域评估 Stream 边缘延迟（东南亚/中东重点实测首帧时间），必要时对热点剧集预热或加区域 CDN。
- 鉴权依旧是「先过 `/play` 权限校验 → 再下发签名地址」，Referer 在容器内不可作为鉴权依据；继续用签名 TTL + 播放频控防盗刷。

---

## 九、广告（IAA）、分享与拉新

### 9.1 激励视频广告

- 后台开通 IAA 并创建 Ad Placement（区分场景：单集解锁、签到翻倍、积分任务）。
- 前端用 TTMinis SDK 的激励广告 API 预加载、播放，`onClose` 判断是否完整观看；**发奖以 TikTok 服务端回调/校验为准**，前端展示"发放中"并轮询。
- 后端新增广告奖励回调端点：验签 → 按 `(userId, traceId)` 唯一约束去重 → 调 PointService/UnlockService 发放；频控沿用现有配置（每日上限、冷却 30 秒）；广告加载/播放失败**不发奖**。
- 混合变现建议：付费剧前 8~10 集免费 → 付费集可「付 Beans 或看广告解锁」；东南亚市场 IAA 权重调高，美区 IAP 权重调高。

### 9.2 分享、锚点与复访

- 视频锚点是核心入口：发布带短剧挂载的 TikTok 短视频（自有账号/达人合作/投流素材），用户点锚点直达对应剧目，落地参数形如 `?dramaId=123&inviter=xxx`，App 启动（H5 即页面加载）时解析 query：`dramaId` 定位剧目，`inviter` 调 `/api/user/invite/bind`（复用现有 `InviteService`）。
- 小程序内分享用 TTMinis 分享能力（具体 API 以 SDK 文档为准），卡片标题用剧名、封面用 cover；无法拿到"分享成功"回执时，**分享奖励以被邀请人回流登录/观看为准**，防刷。
- 复访位：Minis Center 最近使用、个人主页入口、搜索、侧边栏；订阅/通知能力按平台开放情况接入更新提醒（替代 unipush）。

### 9.3 投流

- 接入 **TikTok Growth Max（Minis 原生增长目标）**：短剧支持 D0 IAA/IAP tROAS 出价；需要在小程序内回传站内事件（注册、观看里程碑、解锁、下单），按文档做事件埋点与 postback。
- 投放初期可用 Smart+ 出价保护政策降低试错成本（IAP 每日≥1 转化、IAA≥300 展示起算，细节以广告后台为准）。

---

## 十、后端改造清单汇总

| 模块 | 内容 | 复用/新增 |
|---|---|---|
| 绑定表 | `user_third_account`（platform=TIKTOK，openid 唯一） | 新增 Flyway 脚本 |
| 鉴权 | `/auth/tiktok/session`、`/bind`、`/profile/tiktok`；client_token Redis 缓存 | 新增 Service + Controller 方法 |
| 支付 | `TikTokMinisPaymentService`：trade_order 下单、查单、退款；Webhook 验签幂等发货 | 新增；订单表加渠道枚举/Beans 金额字段 |
| 商品 | 积分包/会员套餐 Beans 定价映射（按市场） | 加字段/映射表，后台维护 |
| 广告 | IAA 奖励回调验签、traceId 去重 | 新增端点，复用 PointService |
| 邀请 | 小程序来源邀请绑定 | 复用 InviteService，扩来源 |
| 事件回传 | Growth Max 埋点 postback（注册/观看/付费） | 新增埋点服务（可异步） |
| 配置 | TIKTOK_* 环境变量、CORS 白名单、Webhook 白名单路径 | 配置变更，无硬编码密钥 |
| 管理后台（可选，不阻塞） | 订单按 TikTok 渠道筛选、Beans 价格配置 | 小改，可上线后补 |

所有改造遵守项目既有约束：业务逻辑在 Service、事务在 Service、文件/密钥走环境变量、DDL 走 Flyway、积分变动统一走 PointService、解锁校验走 UnlockService。

---

## 十一、分阶段实施 Checklist

### 阶段 0：资质与账号（第 1 天启动）
- [ ] 注册/确定海外企业主体（推荐新加坡），开发者平台创建组织与应用
- [ ] 企业认证（Verify Your Business）
- [ ] 行业资质审核（版权授权链 + 代表作证明）
- [ ] 美国区 USDS TPRM 审查提交
- [ ] 开通 IAP/IAA、签合同、税务与收款信息
- [ ] 申请 Minis 能力，拿 client_key/client_secret，配置测试白名单
- [ ] 确定首批上架国家与对应片单（版权区域核对）
- [ ] 准备 Privacy Policy / ToS 英文（及多语言）页面

### 阶段 1：工程跑通（2~3 天）
- [ ] Node 18+/20、`@tiktok-minis/cli` 安装；`ttdx minis init` 生成 minis.config.json
- [ ] HBuilderX 出 H5 包；写 SDK 注入脚本与 minis 构建脚本
- [ ] 配置绝对 API 域名、域名白名单；`ttdx minis debug` + 安卓内测包扫码跑通首页
- [ ] **优先验证：安卓/iOS WebView 内 HLS 播放（hls.js 或 mp4 下发）、带声自动播放策略**
- [ ] 校验包体（<50MB）、无 iframe/eval

### 阶段 2：登录（2~3 天）
- [ ] 绑定表 + session/bind 接口 + client_token 缓存
- [ ] 前端静默登录、显式授权补资料、老账号绑定、401 刷新回归

### 阶段 3：页面与播放适配（4~6 天）
- [ ] Feed 窗口化渲染、播放/暂停图标、进度条、边界 Toast、全屏
- [ ] 详情/选集/解锁弹层、剧场/分类、mine 全部子页回归
- [ ] 多语言/多币种按市场开关、安全区适配
- [ ] 移除 webview 中转页与 Minis 构建中的外链支付分支

### 阶段 4：Beans 支付（4~5 天）
- [ ] 商品 Beans 定价配置；后端下单/支付桥/查单/退款
- [ ] Webhook 验签、幂等发货、PENDING 对账定时任务
- [ ] 沙箱/测试档全链路：单集、整剧、会员、余额不足合并充值、取消、掉单补单
- [ ] 订单/明细展示回归

### 阶段 5：广告/分享/复访（2~3 天）
- [ ] 激励视频多广告位 + 回调验签去重发奖 + 频控
- [ ] 分享卡片、锚点落地参数、邀请绑定与回流奖励
- [ ] Growth Max 站内事件埋点（注册/观看/解锁/付费）

### 阶段 6：提审上线（1~2 周）
- [ ] 付费透明文案、退款/未成年人提示、订单与充值消耗明细自查
- [ ] 隐私合规（GDPR/CCPA/当地数据法）、举报处理流程（72h SLA）
- [ ] 分国家上传代码包/灰度（Partial Rollout 可小流量 A/B）
- [ ] 提审（常规审核约 1~3 天，行业类可能更久）→ 灰度 → 全量
- [ ] 素材与锚点视频投放准备，Growth Max 开量

---

## 十二、测试要点

| 类别 | 必测项 |
|---|---|
| 环境 | 安卓 TikTok 内测包（M/T 包按地区）、iOS 扫码预览；美区与东南亚各至少一台真机 |
| 登录 | 静默建号、显式授权、老账号绑定、token 刷新、换设备、隐私弹窗同意/拒绝 |
| 播放 | iOS HLS 原生、安卓 hls.js/mp4、弱网、首帧时间、连播签名续期、快速滑动、来电/后台中断、全屏、带声自动播放 |
| 支付 | 余额充足/不足合并充值、取消、超时、重复回调幂等、伪造回调拒绝、掉单查单、退款、Beans 与积分/VIP 到账一致性 |
| 广告 | 加载失败、未看完关闭、看完回调延迟、traceId 重放、频控上限、iOS/安卓差异 |
| 分享拉新 | 锚点直达剧目、邀请绑定、回流奖励、自邀刷量风控 |
| 合规 | 各国可见剧集与版权区域一致、支付前明示信息、隐私政策可访问、举报入口可用 |
| 兼容性能 | 高低端机 WebView、页面加载时长、Feed 滑动帧率、包体大小 |

---

## 十三、风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| **海外主体/行业资质/USDS 审查周期长** | 美国区无法按期上线 | 阶段 0 最先启动；东南亚先行上线赚钱，美国并行过审；提前整理版权链与代表作 |
| **平台仍在快速迭代、政策/抽成变化** | 方案返工、毛利波动 | 只依赖官方 SDK/OpenAPI，封装隔离层；合同确认分成与 T+1 结算；IAP+IAA 双轨对冲，同时布局 Drama Center 分账 |
| **安卓 WebView HLS 不兼容/自动播放受限** | 核心看剧体验受损 | 阶段 1 第一件事验证；hls.js 与 mp4 下发双预案；必要时改交互轻点播放 |
| **Beans 支付掉单/回调伪造** | 资损、投诉 | 服务端下单、Webhook 验签、幂等事件表、主动查单、定时对账 |
| **免费短剧（PineDrama/红果模式）冲击付费习惯** | IAP 转化下降 | 混合变现、广告解锁兜底；本土剧/独家内容提高壁垒；用 Growth Max tROAS 精细买量 |
| **版权区域错配** | 下架/法律风险 | 剧库按国家配置可见性，授权文件与上架区域逐一核对 |
| **外链支付/iframe 被打包校验拦截** | 无法提审 | Minis 构建彻底排除 Stripe/PayPal/webview 页，支付只保留 Beans |
| **单一渠道依赖（TikTok 既是流量又是平台）** | 账号封禁/规则调整风险 | 用户邮箱授权沉淀、自有 App/H5 继续维护，多平台分发（后续可扩快手等） |
| **Cloudflare 区域延迟** | 边缘市场起播慢 | 分区域实测，热点剧预热，必要时加区域 CDN/多副本 |

---

## 十四、官方入口与参考

- TikTok for Developers（开发者平台、Minis 文档）：`https://developers.tiktok.com/`
  - Mini Dramas Integration Workflow：`https://developers.tiktok.com/doc/tiktok-minis-integration-workflow`
  - Minis SDK（Get Started）：`https://developers.tiktok.com/doc/minis-sdk-get-started`
  - Minis Server APIs：`https://developers.tiktok.com/doc/minis-server-apis-overview`
  - 资质：Verify Your Business / Industry Qualification Review / Launch Your App to U.S. Users
  - 变现：Monetization Overview / Enable Monetization Features
- 短剧内容合作/分账（路径 B）：TikTok Drama Center `https://drama.tiktok.com/`
- 广告投放：TikTok Ads Help → Growth Max（Mini Drama，D0 IAA/IAP tROAS）
- Minis JS SDK 地址（短剧）：`https://connect.tiktok-minis.com/drama/sdk.js`
- 服务端关键端点（以官方文档为准）：
  - 登录换 token：`POST https://open.tiktokapis.com/v2/oauth/token/`
  - 用户信息：`GET https://open.tiktokapis.com/v2/user/info/`
  - 创建 Beans 订单：`POST https://open.tiktokapis.com/v2/minis/trade_order/create/`
  - 支付成功事件：`minis.trade_order.redeem.success`（Webhook）
- CLI：`npm i -g @tiktok-minis/cli`（官方 npm 源，Node ≥18）

---

### 附：对现有代码的改动量预估（供排期，本次不执行）

- **前端**：新增 `utils/tiktok-minis.js` 能力封装、SDK 注入与 minis 构建脚本、Minis 专用 H5 构建配置（hash 路由+绝对 API 域名）；改 store/recharge 支付分支、登录页默认登录方式、请求 clientType；HLS 兼容（hls.js 或下发 mp4）；广告/分享/落地参数处理。**无新增页面、无重写**，App 与浏览器 H5 不受影响。
- **后端**：1 张绑定表、1 个 TikTok 渠道 Service（登录+支付+广告回调）、约 5~7 个端点、商品 Beans 映射、Growth Max 埋点；全部遵循现有分层与密钥/DDL 约束。
- **运维/商务**：海外主体与资质、税务收款、域名白名单与回调地址、Beans 商品与广告位配置、分国家片单可见性。
