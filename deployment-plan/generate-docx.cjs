const fs = require('fs');
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  Header, Footer, AlignmentType, LevelFormat, HeadingLevel,
  BorderStyle, WidthType, ShadingType, PageNumber, PageBreak
} = require('docx');

const border = { style: BorderStyle.SINGLE, size: 1, color: 'CCCCCC' };
const borders = { top: border, bottom: border, left: border, right: border };
const cellMargins = { top: 60, bottom: 60, left: 100, right: 100 };
const headerShading = { fill: 'F1F5F9', type: ShadingType.CLEAR };

function txt(text, opts = {}) {
  return new TextRun({
    text,
    font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' },
    size: opts.size || 22,
    bold: opts.bold || false,
    color: opts.color || '0F172A',
  });
}

function p(text, opts = {}) {
  return new Paragraph({
    spacing: { after: opts.spacingAfter !== undefined ? opts.spacingAfter : 120, before: opts.spacingBefore || 0 },
    children: typeof text === 'string' ? [txt(text, opts)] : text,
  });
}

function h1(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    spacing: { before: 360, after: 160 },
    children: [new TextRun({
      text, bold: true, size: 30,
      font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' },
      color: '0F172A',
    })],
  });
}

function h2(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2,
    spacing: { before: 280, after: 140 },
    children: [new TextRun({
      text, bold: true, size: 26,
      font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' },
      color: '1E293B',
    })],
  });
}

function h3(text) {
  return new Paragraph({
    spacing: { before: 200, after: 100 },
    children: [new TextRun({
      text, bold: true, size: 23,
      font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' },
      color: '2563EB',
    })],
  });
}

function bullet(text, opts = {}) {
  return new Paragraph({
    numbering: { reference: 'bullets', level: 0 },
    spacing: { after: 60 },
    children: typeof text === 'string' ? [txt(text, opts)] : text,
  });
}

function makeTable(headers, rows, colWidths) {
  const totalWidth = 9360;
  const widths = colWidths || headers.map(() => Math.floor(totalWidth / headers.length));

  const headerRow = new TableRow({
    cantSplit: true,
    children: headers.map((h, i) => new TableCell({
      borders,
      width: { size: widths[i], type: WidthType.DXA },
      shading: headerShading,
      margins: cellMargins,
      children: [new Paragraph({
        children: [new TextRun({
          text: h, bold: true, size: 20,
          font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' },
          color: '0F172A',
        })],
      })],
    })),
  });

  const dataRows = rows.map(row => new TableRow({
    cantSplit: true,
    children: row.map((cell, i) => {
      const cellText = typeof cell === 'string' ? cell : (cell.text || '');
      const cellColor = typeof cell === 'object' ? cell.color : null;
      const cellBold = typeof cell === 'object' ? cell.bold : false;
      return new TableCell({
        borders,
        width: { size: widths[i], type: WidthType.DXA },
        margins: cellMargins,
        children: [new Paragraph({
          children: [new TextRun({
            text: cellText, size: 20,
            font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' },
            color: cellColor || '334155',
            bold: cellBold,
          })],
        })],
      });
    }),
  }));

  return new Table({
    width: { size: totalWidth, type: WidthType.DXA },
    columnWidths: widths,
    rows: [headerRow, ...dataRows],
  });
}

function spacer(size = 120) {
  return new Paragraph({ spacing: { after: size }, children: [] });
}

function calloutPara(title, lines, color) {
  const children = [];
  children.push(new Paragraph({
    spacing: { after: 60 },
    children: [new TextRun({
      text: title, bold: true, size: 22,
      font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' },
      color: color || '2563EB',
    })],
  }));
  lines.forEach(line => {
    children.push(new Paragraph({
      spacing: { after: 40 },
      indent: { left: 360 },
      children: [new TextRun({
        text: line, size: 21,
        font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' },
        color: '475569',
      })],
    }));
  });
  return children;
}

const doc = new Document({
  styles: {
    default: {
      document: {
        run: {
          font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' },
          size: 22,
        },
      },
    },
    paragraphStyles: [
      { id: 'Heading1', name: 'Heading 1', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { size: 30, bold: true, font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' } },
        paragraph: { spacing: { before: 360, after: 160 }, outlineLevel: 0, keepNext: false, keepLines: false } },
      { id: 'Heading2', name: 'Heading 2', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { size: 26, bold: true, font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' } },
        paragraph: { spacing: { before: 280, after: 140 }, outlineLevel: 1, keepNext: false, keepLines: false } },
    ],
  },
  numbering: {
    config: [
      { reference: 'bullets',
        levels: [{ level: 0, format: LevelFormat.BULLET, text: '\u2022', alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 720, hanging: 360 } } } }] },
    ],
  },
  sections: [{
    properties: {
      page: {
        size: { width: 11906, height: 16838 },
        margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 },
      },
    },
    headers: {
      default: new Header({
        children: [new Paragraph({
          alignment: AlignmentType.RIGHT,
          children: [new TextRun({
            text: '短剧平台海外上线计划书', size: 18, color: '94A3B8',
            font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' },
          })],
        })],
      }),
    },
    footers: {
      default: new Footer({
        children: [new Paragraph({
          alignment: AlignmentType.CENTER,
          children: [
            new TextRun({ text: '第 ', size: 18, color: '94A3B8', font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' } }),
            new TextRun({ children: [PageNumber.CURRENT], size: 18, color: '94A3B8' }),
            new TextRun({ text: ' 页', size: 18, color: '94A3B8', font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' } }),
          ],
        })],
      }),
    },
    children: [
      // ===== COVER =====
      new Paragraph({ spacing: { before: 2400 }, children: [] }),
      new Paragraph({
        alignment: AlignmentType.CENTER,
        spacing: { after: 200 },
        children: [new TextRun({
          text: '短剧平台海外上线计划书', bold: true, size: 44,
          font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' },
          color: '0F172A',
        })],
      }),
      new Paragraph({
        alignment: AlignmentType.CENTER,
        spacing: { after: 600 },
        children: [new TextRun({
          text: '功能级排期 · 资源清单 · 费用预算', size: 24,
          font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' },
          color: '64748B',
        })],
      }),
      // Summary table
      makeTable(
        ['周期', '每月费用', '待修复功能', '开发人员'],
        [['12 周', '$60-78', '8 项', '1 人']],
        [2340, 2340, 2340, 2340]
      ),
      spacer(600),
      new Paragraph({
        alignment: AlignmentType.CENTER,
        children: [new TextRun({
          text: '生成日期：2026-08-15', size: 20, color: '94A3B8',
          font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' },
        })],
      }),
      new Paragraph({ children: [new PageBreak()] }),

      // ===== 1. 项目现状 =====
      h1('一、项目现状'),
      p('短剧平台的核心功能已经开发完成，包括以下已实现的功能：'),
      bullet('用户注册、登录、观看短剧'),
      bullet('会员购买、积分系统、自动续费'),
      bullet('支付接入（Stripe / PayPal / Apple / Google）'),
      bullet('管理后台（内容管理、用户管理、数据统计）'),
      bullet('17 种语言支持（中英日韩泰等）'),
      bullet('视频上传和播放（基于 Cloudflare）'),
      spacer(80),
      p('但排查发现 8 个功能存在问题：1 个接口缺失会导致报错、1 处价格硬编码、1 处直接调外部网站、1 处测试接口未关闭，以及 6 块后端功能做好了但前端还没接入。', { bold: true }),
      spacer(160),

      // ===== 2. 功能清单与状态 =====
      h1('二、功能清单与完成状态'),

      h2('（一）必须修复（上线前）'),
      makeTable(
        ['功能', '所在页面', '问题描述', '修复方案', '工期'],
        [
          ['个人中心快速充值', '个人中心页', '调用的"模拟支付"接口后端不存在，点击会报 404 错误', '后端补上接口，或前端改用正式支付流程', '4 小时'],
          ['个人中心购买会员', '个人中心页', '同上，也调用了不存在的"模拟支付"接口', '同上，一并修复', '—'],
          ['积分兑换会员价格', '会员页', '兑换价格写死在前端代码里，后台改了价格前端不更新', '改为从后端接口获取价格', '3 小时'],
          ['汇率查询', '汇率管理页', '前端直接调用外部网站获取汇率，不经过自己的服务器', '改为通过后端代理获取', '2 小时'],
        ],
        [1600, 1200, 2800, 2560, 1200]
      ),
      spacer(80),
      ...calloutPara('最严重的问题', [
        '个人中心页的"快速充值"和"购买会员"按钮，点击后会直接报错（404），因为前端调用的支付接口在后端根本不存在。这个必须在上线前修好。',
      ], 'DC2626'),
      spacer(160),

      h2('（二）后端已做好、前端未接入（上线后补充）'),
      p('以下功能后端接口已经开发完成，但前端页面还没有调用。不影响上线，但会影响用户体验和收入。'),
      makeTable(
        ['功能模块', '包含功能', '商业价值', '建议接入时间'],
        [
          ['观看奖励系统', '看完一集送积分、观看时长奖励、评论奖励', '提高用户留存和活跃度', '上线后第 1-2 周'],
          ['分享奖励系统', '分享剧集得积分、分享链接追踪', '免费拉新渠道', '上线后第 1-2 周'],
          ['广告奖励系统', '看广告领积分、广告奖励记录', '非付费用户的收入来源', '上线后第 2-3 周'],
          ['邀请系统', '邀请码、邀请记录、分享链接', '用户裂变增长', '上线后第 3-4 周'],
          ['解锁预览', '单集/整剧解锁前显示价格预览', '提升付费转化率', '上线后第 1 周'],
          ['Apple/Google 内购校验', 'App 内购买的订单验证', 'App 上架后必需', 'App 发布前'],
        ],
        [1800, 2800, 2200, 2560]
      ),
      spacer(160),

      h2('（三）已完成功能一览'),
      makeTable(
        ['页面', '功能', '状态'],
        [
          ['首页', '短剧列表展示、分类筛选', '完整'],
          ['详情页', '剧集详情、收藏、解锁', '完整'],
          ['播放页', '视频播放、解锁单集/整剧、观看历史', '完整'],
          ['充值页', '积分充值、Stripe/PayPal 支付', '完整'],
          ['会员页', '会员购买、等级展示、权益说明', '完整'],
          ['积分商城', '积分兑换商品、兑换记录', '完整'],
          ['分类页', '按分类筛选短剧', '完整'],
          ['管理-仪表盘', '数据概览、关键指标', '完整'],
          ['管理-数据分析', '播放量趋势、剧集排行、用户分析', '完整'],
          ['管理-用户管理', '用户列表、创建/编辑/删除、积分调整', '完整'],
          ['管理-订单管理', '订单列表、标记已付、退款', '完整'],
          ['管理-角色权限', '角色管理、管理员管理、权限分配', '完整'],
          ['管理-短剧管理', '剧集增删改查、批量创建', '完整'],
          ['管理-公告管理', '公告发布、置顶、删除', '完整'],
          ['管理-反馈管理', '用户反馈查看、回复、处理', '完整'],
          ['管理-会员管理', '会员记录、积分记录、积分发放', '完整'],
          ['管理-商品管理', '积分商品增删改查', '完整'],
          ['管理-自动续费', '续费列表、取消续费', '完整'],
          ['管理-汇率管理', '汇率增删改查、缓存刷新', '需修复'],
          ['管理-个人资料', '管理员资料编辑、密码修改', '完整'],
        ],
        [2400, 5360, 1600]
      ),
      spacer(160),

      // ===== 3. 上线计划 =====
      h1('三、上线计划（功能级排期）'),

      h2('第一阶段：功能修复 + 工程整理'),
      p('时间：第 1-2 周（10 个工作日）', { color: '64748B', size: 21 }),
      makeTable(
        ['天数', '任务', '具体内容'],
        [
          ['第 1 天', '修复模拟支付接口', '后端补上 mock-pay 接口，或前端改用正式支付流程'],
          ['第 1 天', '修复会员兑换价格', '会员页改为从后端获取兑换价格，不写死在前端'],
          ['第 2 天', '修复汇率查询', '汇率管理页改为通过后端代理获取外部汇率'],
          ['第 2 天', '关闭测试支付接口', '模拟支付接口添加环境限制，生产环境不可访问'],
          ['第 3 天', '前端环境变量', '创建生产环境配置文件，替换硬编码的接口地址'],
          ['第 3 天', '错误提示优化', '系统报错时不显示技术细节，只显示友好提示'],
          ['第 4 天', '项目文档', '编写项目说明、开发指南、.gitignore 配置'],
          ['第 5-7 天', '解锁预览接入', '用户解锁前显示价格预览，提升付费转化'],
          ['第 8-10 天', '缓冲与验证', '手动测试修复后的功能，确保无回归问题'],
        ],
        [1200, 2200, 5960]
      ),
      spacer(160),

      h2('第二阶段：安全加固'),
      p('时间：第 3-4 周（10 个工作日）', { color: '64748B', size: 21 }),
      makeTable(
        ['天数', '任务', '具体内容'],
        [
          ['第 1-2 天', '生产配置文件', '创建生产环境配置，移除所有默认密码，强制环境变量注入'],
          ['第 3 天', 'CORS 白名单', '配置只允许生产域名访问接口'],
          ['第 4 天', 'JWT 密钥', '生成 256 位随机密钥，替换开发用的弱密钥'],
          ['第 5 天', 'Redis 密码', '云 Redis 设置密码保护'],
          ['第 6-7 天', '支付回调验证', '验证 Stripe/PayPal 回调签名，确保支付安全'],
          ['第 8 天', 'HTTPS 配置', '申请 Let\'s Encrypt 证书，配置 TLS 1.2/1.3'],
          ['第 9-10 天', '安全自查', '逐项检查安全清单，修复发现的问题'],
        ],
        [1200, 2200, 5960]
      ),
      spacer(160),

      h2('第三阶段：核心功能测试'),
      p('时间：第 5-7 周（15 个工作日）', { color: '64748B', size: 21 }),
      makeTable(
        ['天数', '任务', '具体内容'],
        [
          ['第 1-3 天', '支付流程测试', '测试：充值 → 积分到账 → 购买会员 → 会员生效 → 退款 → 积分扣除'],
          ['第 4-5 天', '限购逻辑测试', '测试：每日/每月/全局限购是否正确拦截'],
          ['第 6-7 天', '认证授权测试', '测试：登录、JWT 过期、权限拦截、令牌吊销'],
          ['第 8-9 天', '手动全流程测试', '走完：注册 → 登录 → 充值 → 买会员 → 看剧 → 签到 → 积分商城兑换'],
          ['第 10-11 天', '管理后台测试', '走完：内容发布 → 用户管理 → 订单处理 → 数据统计 → 公告发布'],
          ['第 12 天', '多语言测试', '切换 17 种语言，检查页面显示是否正常'],
          ['第 13-15 天', 'Bug 修复', '修复测试中发现的问题'],
        ],
        [1200, 2200, 5960]
      ),
      spacer(160),

      h2('第四阶段：打包部署上线'),
      p('时间：第 8-12 周（25 个工作日）', { color: '64748B', size: 21 }),
      makeTable(
        ['天数', '任务', '具体内容'],
        [
          ['第 1-3 天', '后端打包', '编写 Dockerfile，将后端程序打包成可部署的镜像'],
          ['第 4-5 天', 'Nginx 配置', '配置反向代理、HTTPS、静态资源服务、安全头'],
          ['第 6-7 天', '前端打包', '编写构建脚本，前端编译后部署到 Nginx'],
          ['第 8-9 天', '编排文件', '编写 docker-compose，一键启动所有服务'],
          ['第 10-12 天', '自动构建', '配置 GitHub Actions，代码推送后自动构建测试'],
          ['第 13-14 天', '部署脚本', '编写手动部署脚本，5 分钟完成更新发布'],
          ['第 15-17 天', '预发布环境', '在预发布环境部署，验证完整流程'],
          ['第 18-19 天', '服务器准备', '购买云服务器、云数据库、云 Redis，配置网络'],
          ['第 20 天', '数据库初始化', '创建数据库，执行建表脚本，迁移数据'],
          ['第 21 天', '后端部署', '部署后端容器，验证健康检查通过'],
          ['第 22 天', '前端部署 + Nginx', '部署前端静态文件，启动 Nginx'],
          ['第 23 天', '域名 + SSL', '域名解析到服务器，申请 SSL 证书'],
          ['第 24 天', '支付配置', '更新 Stripe/PayPal 回调地址为生产域名'],
          ['第 25 天', '上线冒烟测试', '正式上线，走完整流程验证'],
        ],
        [1200, 2200, 5960]
      ),
      spacer(160),

      // ===== 4. 资源清单 =====
      h1('四、需要准备什么'),

      h2('（一）服务器'),
      makeTable(
        ['名称', '用途', '推荐方案', '为什么选它'],
        [
          ['云服务器', '运行程序', 'DigitalOcean / Vultr / AWS  2核4G 80G硬盘', '放网站程序的地方，相当于"租一台电脑"'],
          ['云数据库', '存储用户和订单数据', 'DigitalOcean Managed MySQL 或 AWS RDS', '自动备份、自动维护，不用自己管，数据不丢'],
          ['云缓存', '加速访问', 'Upstash Redis 或 DigitalOcean Redis', '让网站打开更快，自动维护'],
        ],
        [1400, 2000, 3200, 2760]
      ),
      spacer(80),
      ...calloutPara('海外上线的优势', [
        '海外服务器不需要备案，域名注册后立即可以使用。',
        'Cloudflare CDN 免费提供全球加速，让各国用户都能快速访问。',
        'Stripe 和 PayPal 原生支持海外支付，无需额外申请。',
      ], '059669'),
      spacer(160),

      h2('（二）域名'),
      makeTable(
        ['项目', '说明', '价格', '去哪买'],
        [
          ['域名注册', '网站的地址，比如 duanju.com', '.com 约 $10/年', 'Namecheap / Cloudflare / GoDaddy'],
          ['SSL 证书', '让网站显示锁标志（https），用户信任', '免费', 'Let\'s Encrypt（自动申请）'],
          ['CDN 加速', '全球加速，让各国用户访问都快', '免费起步', 'Cloudflare（已有账号）'],
        ],
        [1400, 3000, 1800, 3160]
      ),
      spacer(160),

      h2('（三）第三方服务'),
      makeTable(
        ['服务', '用途', '费用', '备注'],
        [
          ['Stripe', '海外信用卡支付', '交易额 2.9% + $0.30/笔', '无月费，按交易抽成'],
          ['PayPal', '海外 PayPal 支付', '交易额约 4%', '无月费'],
          ['Cloudflare Stream', '视频存储和播放', '$5/月起', '包含 1000 分钟存储 + 流量'],
          ['Apple/Google 开发者', 'App 内购', 'Apple $99/年，Google $25 一次性', 'App 上架应用商店必需'],
          ['对象存储', '存用户头像、封面图', '$1-5/月', 'AWS S3 或 Cloudflare R2'],
        ],
        [1600, 2000, 2600, 3160]
      ),
      spacer(160),

      // ===== 5. 费用预算 =====
      h1('五、总共要花多少钱'),

      h2('（一）一次性费用'),
      makeTable(
        ['项目', '费用', '说明'],
        [
          ['域名注册（.com）', '$10/年', '每年续费，注册后立即可用'],
          ['SSL 证书', '$0', '免费申请'],
          ['Google 开发者账号', '$25', '一次性，永久有效'],
          ['Apple 开发者账号', '$99/年', '每年续费'],
          [{ text: '一次性合计', bold: true }, { text: '约 $134（首年）', bold: true, color: '2563EB' }, ''],
        ],
        [3000, 3000, 3360]
      ),
      spacer(160),

      h2('（二）每月持续费用'),
      makeTable(
        ['项目', '月费用', '说明'],
        [
          ['云服务器（2核4G）', '$20-24', 'DigitalOcean / Vultr，运行网站程序'],
          ['云数据库 MySQL', '$15-20', '存数据，自动备份'],
          ['云 Redis', '$10-15', '加速访问'],
          ['对象存储', '$1-5', '存图片等文件'],
          ['Cloudflare Stream', '$5', '视频存储和播放'],
          ['域名（年均摊）', '$1', '$10 / 12'],
          ['Apple 开发者（年均摊）', '$8', '$99 / 12'],
          [{ text: '每月合计', bold: true }, { text: '$60-78/月', bold: true, color: '059669' }, ''],
        ],
        [3000, 3000, 3360]
      ),
      spacer(80),
      ...calloutPara('省钱建议', [
        '初期用户少时，服务器买最低配即可，后续随时升级',
        'Stripe 和 PayPal 没有月费，只有用户支付时才扣手续费',
        'SSL 证书用 Let\'s Encrypt 完全免费，不需要花钱买',
        'Cloudflare CDN 免费版已足够初期使用',
        '数据库和 Redis 初期可以装在同一台服务器上省掉 $25-35/月',
        'Cloudflare R2 存储比 AWS S3 便宜（无出口流量费）',
      ], '059669'),
      spacer(160),

      // ===== 6. 时间安排 =====
      h1('六、时间安排总览'),
      makeTable(
        ['周', '阶段', '主要任务', '产出'],
        [
          ['第 1-2 周', '功能修复', '修复 mock-pay 接口、兑换价格硬编码、汇率代理、环境变量配置、解锁预览接入', '所有功能可正常使用'],
          ['第 3-4 周', '安全加固', '生产配置、密钥清理、CORS 白名单、支付回调验证、HTTPS', '安全检查全部通过'],
          ['第 5-7 周', '功能测试', '支付流程测试、限购测试、认证测试、全流程手动验证、多语言测试', '核心功能无 Bug'],
          ['第 8-9 周', '打包部署', 'Dockerfile、Nginx 配置、docker-compose、构建脚本', '可一键部署'],
          ['第 10-11 周', '发布流程', 'GitHub Actions 自动构建、部署脚本、预发布环境验证', '预发布环境可用'],
          ['第 12 周', '正式上线', '服务器购买、数据库初始化、部署、域名解析、SSL、冒烟测试', '用户可正常使用'],
        ],
        [1200, 1400, 4800, 1960]
      ),
      spacer(80),
      ...calloutPara('注意事项', [
        '域名注册建议第 1 周完成，海外域名注册后立即可用',
        '服务器购买建议第 11 周左右，太早买了空跑浪费钱',
        'Stripe 账号审核 2-5 天，建议第 1 周就注册',
        'Apple 开发者账号审核 1-2 天，建议第 1 周就申请',
        '服务器地区：目标用户在东南亚选新加坡，欧美选美国东部',
      ], 'D97706'),
      spacer(160),

      // ===== 7. 上线后计划 =====
      h1('七、上线后计划'),
      p('上线不是终点，还有 6 块功能等后续接入：'),
      makeTable(
        ['时间', '功能', '商业价值'],
        [
          ['上线后第 1 周', '观看奖励（看完送积分）', '提高用户留存'],
          ['上线后第 1 周', '解锁预览（显示价格）', '提升付费转化'],
          ['上线后第 2 周', '分享奖励（分享得积分）', '免费拉新'],
          ['上线后第 3 周', '广告奖励（看广告领积分）', '非付费用户收入'],
          ['上线后第 4 周', '邀请系统（邀请码）', '用户裂变增长'],
          ['App 发布前', 'Apple/Google 内购校验', 'App 上架必需'],
        ],
        [2000, 4000, 3360]
      ),
      spacer(160),

      h2('日常维护'),
      bullet('每周看一次服务器监控面板'),
      bullet('关注磁盘空间是否快满了'),
      bullet('查看是否有异常错误日志'),
      bullet('确认数据库备份正常'),
      spacer(80),

      h2('更新发布'),
      bullet('代码提交后自动构建'),
      bullet('手动执行部署脚本（5 分钟）'),
      bullet('选择低峰期更新'),
      bullet('出问题可 3 分钟内回退旧版本'),
      spacer(80),

      h2('出了问题怎么办'),
      bullet('网站打不开：云监控自动发短信通知，3 分钟内切换旧版本'),
      bullet('数据库出问题：云数据库有自动备份，可恢复到任意时间点'),
      bullet('支付出问题：支付平台有交易记录，不会丢钱'),
      bullet('用户数据丢失：每天自动备份，最多丢 1 天数据'),
      spacer(160),

      // ===== 8. 总结 =====
      h1('八、一句话总结'),
      new Paragraph({
        spacing: { after: 200, before: 100 },
        shading: { fill: 'EFF6FF', type: ShadingType.CLEAR },
        border: {
          left: { style: BorderStyle.SINGLE, size: 12, color: '2563EB' },
        },
        indent: { left: 200 },
        children: [
          new TextRun({ text: '项目功能已完成 ', size: 24, font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' } }),
          new TextRun({ text: '70%', size: 24, bold: true, color: '2563EB', font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' } }),
          new TextRun({ text: '，有 ', size: 24, font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' } }),
          new TextRun({ text: '4 个必须修复的问题', size: 24, bold: true, color: 'DC2626', font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' } }),
          new TextRun({ text: '（1 个会导致报错、1 处价格写死、1 处直接调外部网站、1 处测试接口未关闭），', size: 24, font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' } }),
          new TextRun({ text: '6 块后端做好了前端没接的功能', size: 24, bold: true, color: 'D97706', font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' } }),
          new TextRun({ text: '可上线后逐步接入。还需 ', size: 24, font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' } }),
          new TextRun({ text: '12 周', size: 24, bold: true, color: '2563EB', font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' } }),
          new TextRun({ text: '完成修复、安全加固、测试和部署。海外上线无需备案，每月运营成本约 ', size: 24, font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' } }),
          new TextRun({ text: '$60-78', size: 24, bold: true, color: '059669', font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' } }),
          new TextRun({ text: '，一个人即可维护。', size: 24, font: { ascii: 'Arial', hAnsi: 'Arial', eastAsia: 'Microsoft YaHei' } }),
        ],
      }),
    ],
  }],
});

Packer.toBuffer(doc).then(buffer => {
  const outPath = 'c:\\Users\\Administrator\\Desktop\\AICode\\duanju-master\\deployment-plan\\短剧平台海外上线计划书.docx';
  fs.writeFileSync(outPath, buffer);
  console.log('Word document generated: ' + outPath);
});
