// 中文（简体）语言包
export default {
  brand: {
    name: '星幕短剧',
    fullName: '星幕短剧 Marastel',
    kicker: 'Marastel · AI 漫剧平台',
    companyEn: '香港饮冰文化传媒有限公司',
    companyEnName: '香港饮冰文化传媒有限公司',
    copyright: '保留所有权利。',
    paymentNote: '安全支付由 Stripe 处理。支持 Visa、Mastercard、American Express、Apple Pay & Google Pay。'
  },

  nav: {
    home: '首页',
    pricing: '定价',
    vip: 'VIP会员',
    credits: '积分充值',
    contact: '联系我们',
    startNow: '立即开始',
    menuAria: '菜单',
    closeAria: '关闭'
  },

  footer: {
    desc: '专注 AI 漫剧的流媒体平台。全部内容由 AI 生成，画风独特，故事新颖。VIP会员无限观看，积分解锁单集。',
    platform: '平台',
    legal: '法律',
    company: '公司',
    privacy: '隐私政策',
    refund: '退款政策',
    terms: '服务条款'
  },

  home: {
    heroTitle1: 'AI 漫剧，看见不一样的故事',
    heroTitle2: '星幕短剧 Marastel',
    heroSubtitle: '专注 AI 漫剧的流媒体平台。全部内容由 AI 生成，画风独特、故事新颖。VIP会员无限畅享，积分灵活解锁单集，支持全球 Stripe 安全支付。',
    viewPlans: '查看套餐',
    browseNow: '立即浏览',
    stats: {
      works: 'AI 漫剧作品',
      countries: '覆盖国家',
      audience: '全球观众'
    },

    // 观看方式
    modelsKicker: '观看方式',
    modelsTitle: '两种模式，任你选择',
    modelsSubtitle: '灵活适配你的观看习惯，订阅省更多，按需花得少。',

    vipBadge: 'VIP会员',
    vipTitle: 'VIP 无限畅享',
    vipDesc: '全库 AI 漫剧无限观看，无广告无等待。每日上新 AI 生成新作，覆盖科幻、国风、赛博朋克等多元题材。',
    vipFeatures: ['全库 AI 漫剧无限制畅看', '每日上新 AI 生成新作', '4K 高清 & AI 降噪画质', '随时可取消，无签约'],
    vipCta: '了解 VIP 套餐 →',

    creditBadge: '积分解锁',
    creditTitle: '积分按需解锁',
    creditDesc: '买积分包，按需解锁单集 AI 漫剧。适合先试后追的探索党，积分永不过期。',
    creditFeatures: ['按需解锁单集', '积分永不过期', '低至 $4.99 起', '首集永远免费'],
    creditCta: '查看积分包 →',

    // 精选
    featuredKicker: '本周热门 AI 漫剧',
    featuredTitle: '精选 AI 漫剧',
    featuredSubtitle: '点击卡片查看详情，开启你的 AI 漫剧追剧之旅。',
    aiBadge: 'AI漫剧',
    episodes: (n) => `${n}集`,

    // 为什么选择星幕
    whyKicker: '为什么选择星幕',
    whyTitle: '为 AI 漫剧爱好者而生',

    // 平台实力
    scaleKicker: '平台实力',
    scaleTitle: '专注 AI 漫剧的流媒体平台',
    scaleSubtitle: '星幕短剧 Marastel 专注 AI 漫剧领域，自研 StarNovel 和 StarBrush 系列 AI 模型，为全球观众提供独特的 AI 生成内容体验。',
    scaleItems: {
      dailyUpdate: '每日更新',
      aiNewWorks: 'AI 生成新作',
      serviceUptime: '服务可用性'
    },

    // CTA
    ctaTitle: '准备好进入 AI 漫剧世界了吗？',
    ctaSubtitle: '选一个适合你的套餐，立即开启独一无二的 AI 漫剧体验。',
    viewPricing: '查看定价',

    // Modal
    modalEpisodes: (n) => `${n}集`,
    modalRegion: (r) => r,
    aiStudioLabel: 'AI工作室',
    aiModelLabel: 'AI模型',
    watchNow: '立即观看',
    keepBrowsing: '继续浏览',

    // Features
    features: [
      {
        iconKey: 'aiExclusive',
        title: 'AI 独家内容',
        description: '全部漫剧由自研 StarNovel + StarBrush AI 模型生成，画风独特，故事新颖。'
      },
      {
        iconKey: 'speedUpdate',
        title: '极速更新',
        description: 'AI 生产效率更高，每日上新 AI 生成新作，有更多新剧可追。'
      },
      {
        iconKey: 'securePayment',
        title: '安全支付',
        description: 'Stripe 全球加密支付，支持 Visa、Mastercard、Apple Pay、Google Pay。'
      },
      {
        iconKey: 'creditsForever',
        title: '积分永不过期',
        description: '购买的积分永久有效，想什么时候解锁就什么时候解锁。'
      },
      {
        iconKey: 'support',
        title: '7×24 客服',
        description: '全天候在线客服，有问题随时联系我们，快速响应解决。'
      },
      {
        iconKey: 'cancelAnytime',
        title: '随时可取消',
        description: 'VIP 会员无签约无罚金，想取消就取消，无任何后顾之忧。'
      }
    ],

    // Fallback dramas (API 挂了时展示)
    fallbackDramas: [
      { id: 'fb-1', title: '隐秘的爱恋', genre: '爱情', episodes: 80, rating: 4.8, region: '华语', description: '一对青梅竹马因为家族恩怨被迫分离，多年后重逢却发现一切都已不同。', gradient: 'linear-gradient(135deg, #C86D26, #6b3410)' },
      { id: 'fb-2', title: '午夜契约', genre: '悬疑', episodes: 60, rating: 4.6, region: '韩语', description: '一份神秘的契约，将平凡女孩卷入一场惊天阴谋。', gradient: 'linear-gradient(135deg, #2d5a3d, #0f1f15)' },
      { id: 'fb-3', title: '城市之光', genre: '都市', episodes: 100, rating: 4.7, region: '华语', description: '三个北漂年轻人在城市的钢筋水泥中寻找属于自己的那束光。', gradient: 'linear-gradient(135deg, #4a3b6b, #1a1430)' },
      { id: 'fb-4', title: '甜蜜的复仇', genre: '喜剧', episodes: 48, rating: 4.5, region: '华语', description: '被渣男劈腿的女主决定化身复仇天使，笑中带泪的都市轻喜剧。', gradient: 'linear-gradient(135deg, #b5651d, #5c2f08)' },
      { id: 'fb-5', title: '你的回声', genre: '爱情', episodes: 72, rating: 4.9, region: '韩语', description: '穿越时间的羁绊，两个孤独的灵魂通过一部对讲机连接在一起。', gradient: 'linear-gradient(135deg, #7d2e4a, #341220)' },
      { id: 'fb-6', title: '继承人', genre: '商战', episodes: 90, rating: 4.7, region: '华语', description: '一夜之间从灰姑娘变成豪门继承人，步步为营，方能笑到最后。', gradient: 'linear-gradient(135deg, #1a4a5c, #08222b)' }
    ]
  },

  pricing: {
    kicker: '定价方案',
    title: '简单透明，解锁 AI 漫剧',
    subtitle: '全价以 USD 显示。随时可取消。每部 AI 漫剧的第一集永远免费。',

    vipKicker: 'VIP 会员',
    vipTitle: 'AI 漫剧无限畅享',
    vipSubtitle: '订阅星幕短剧 VIP，全站 AI 漫剧无限制畅看，无广告无等待。',
    mostPopular: '最受欢迎',
    choosePlan: '选择此方案',

    vipPlans: [
      { name: '月度会员', price: '9.99', period: '/月', description: '灵活月付，随时可取消。最适合初次体验 AI 漫剧的新用户。', features: ['全站 AI 漫剧无限制畅看', 'HD 高清画质 (1080p)', '多设备同步观看', '无广告无弹窗', '随时可取消'] },
      { name: '季度会员', price: '24.99', period: '/3 月', description: 'AI 漫剧追剧首选，对比月付节省 17%。', features: ['包含月度全部权益', '4K 超高清画质', '相比月付省 17%', '优先客服响应', 'AI 新片提前解锁'], popular: true },
      { name: '年度会员', price: '79.99', period: '/年', description: '极致省钱，老粉必备，节省高达 33%。', features: ['包含季度全部权益', '相比月付省 33%', '独家 AI 会员专享内容', '离线下载观看', '专属客服通道'] }
    ],

    creditKicker: '积分包',
    creditTitle: '按需付费，永不过期',
    creditSubtitle: '购买积分解锁单集，1 积分解锁 1 集。适合先看再追的尝试党。',
    creditLabel: '积分',
    creditRate: '约 $0.10 / 集',
    buyCreditPack: '购买积分包',

    creditPacks: [
      { credits: '50', price: '4.99', features: ['50 集 AI 漫剧解锁额度', '积分永不过期', '入门首选'] },
      { credits: '200', price: '19.99', features: ['200 集 AI 漫剧解锁额度', '积分永不过期', '最受欢迎'] },
      { credits: '500', price: '49.99', features: ['500 集 AI 漫剧解锁额度', '积分永不过期', '超值首选'] }
    ],

    faqKicker: '常见问题',
    faqTitle: '你可能想知道',
    faqs: [
      { q: '支持哪些支付方式？', a: '我们支持 Visa、Mastercard、American Express 主流信用卡/借记卡，以及 Apple Pay 和 Google Pay。所有支付由 Stripe 安全加密处理。' },
      { q: '可以随时取消会员吗？', a: '可以。VIP 会员可在会员中心或联系客服随时取消，取消后当前计费周期内仍可正常使用，到期自动停止续费。' },
      { q: '积分会过期吗？', a: '不会。购买的积分永久有效，想什么时候解锁就什么时候解锁，没有时间压力。' },
      { q: '首集真的免费？', a: '是的。星幕短剧平台每一部 AI 漫剧的第一集完全免费，无需注册无需付费，直接点击即可观看。' },
      { q: '用什么货币结算？', a: '全平台以美元 (USD) 定价和结算。如果你的银行卡使用其他货币，会由银行按当天汇率自动换算。' },
      { q: '退款政策是怎样的？', a: '由于数字内容一经解锁无法返还，已消费的积分和已观看的剧集概不退款。但订阅费用可在 7 天内申请退还，具体请查看我们的退款政策页面。' }
    ],

    ctaTitle: '还有问题？',
    ctaSubtitle: '我们的团队随时准备帮你选择最适合的 AI 漫剧套餐。',

    modalVip: 'VIP 会员',
    modalCredit: '积分包',
    modalDefaultDesc: '灵活按需，积分永不过期。',
    modalContact: '联系客服购买',
    modalKeepBrowsing: '继续浏览',
    modalCreditPack: (n) => `${n} 积分包`
  },

  contact: {
    kicker: '联系我们',
    title: '与星幕联系',
    subtitle: '关于套餐、支付、内容的任何问题？我们 7×24 小时在线，随时为你服务。',

    directTitle: '直接联系',
    emailLabel: '电子邮箱',
    phoneLabel: '客服电话',
    addressLabel: '办公地址',
    hoursLabel: '服务时间',
    hoursDetail: '客服中心：7×24 小时\n计费部门：周一至周五 9:00–18:00 (GMT+8)',

    formTitle: '发送消息',
    nameLabel: '姓名',
    namePlaceholder: '请输入你的姓名',
    emailField: '电子邮箱',
    emailPlaceholder: 'you@marastel.com',
    subjectLabel: '主题',
    subjectPlaceholder: '请问有什么可以帮你的？',
    messageLabel: '留言内容',
    messagePlaceholder: '请详细描述你的问题或建议...',
    sendButton: '发送消息',
    sending: '发送中...',

    errorDefault: '发送失败，请稍后再试。',
    errorNetwork: '网络连接异常，请检查网络后重试。',

    successTitle: '消息发送成功！',
    resend: '再发一条'
  },

  router: {
    home: '首页',
    pricing: '定价',
    vip: 'VIP 会员',
    credits: '积分充值',
    contact: '联系我们',
    privacy: '隐私政策',
    refund: '退款政策',
    terms: '服务条款'
  },

  // VIP 会员中心页面
  vipPage: {
    // 页面头部
    kicker: 'VIP 会员中心',
    title: '星幕 VIP 会员',
    subtitle: '订阅 VIP 会员，全站 AI 漫剧无限制畅看。',

    // 状态卡
    statusActive: 'VIP 会员',
    statusExpired: '非会员',
    daysLeft: (n) => `剩余 ${n} 天`,
    levelSource: '通过月度套餐开通',
    levelNormal: '普通会员',
    levelSilver: '白银会员',
    levelGold: '黄金会员',
    levelPlatinum: '铂金会员',
    days: (n) => `${n} 天`,

    // 权益对比
    benefitTitle: 'VIP 会员权益',
    benefitSubtitle: '不同等级享受不同专属权益',
    benefitHeaders: ['权益', '普通用户', '月度 VIP', '季度 VIP', '年度 VIP'],
    benefits: [
      { name: '全站 AI 漫剧畅看', free: false, monthly: true, quarterly: true, yearly: true },
      { name: '每日新片更新', free: true, monthly: true, quarterly: true, yearly: true },
      { name: '无广告无弹窗', free: false, monthly: true, quarterly: true, yearly: true },
      { name: '1080P HD 高清画质', free: false, monthly: true, quarterly: true, yearly: true },
      { name: '4K 超高清画质', free: false, monthly: false, quarterly: true, yearly: true },
      { name: '新片提前解锁', free: false, monthly: false, quarterly: true, yearly: true },
      { name: '离线下载观看', free: false, monthly: false, quarterly: false, yearly: true },
      { name: 'VIP 独家内容', free: false, monthly: false, quarterly: false, yearly: true }
    ],
    benefitYes: '✓',
    benefitNo: '—',

    // 套餐推荐
    planSectionKicker: '选择套餐',
    planSectionTitle: '立即升级 VIP 会员',
    planSectionSubtitle: '灵活月付，随时可取消。季度/年度更省钱。',
    renewBtn: '续费',
    subscribeBtn: '立即开通',

    // 购买记录
    historyTitle: '购买记录',
    historyEmpty: '暂无购买记录',
    historyColumns: ['套餐', '购买时间', '有效期', '状态', '金额'],
    historyStatusActive: '生效中',
    historyStatusExpired: '已过期',

    // 静态历史数据
    historyData: [
      { name: '季度会员', purchasedAt: '2026-08-15', validFrom: '2026-08-15', validTo: '2026-11-14', status: 'active', price: '$24.99' },
      { name: '月度会员', purchasedAt: '2026-06-01', validFrom: '2026-06-01', validTo: '2026-06-30', status: 'expired', price: '$9.99' },
      { name: '年度会员', purchasedAt: '2026-01-10', validFrom: '2026-01-10', validTo: '2026-01-09', status: 'expired', price: '$79.99' }
    ],

    // CTA
    ctaTitle: '还有疑问？',
    ctaSubtitle: '我们的团队随时为你解答关于 VIP 会员的任何问题。'
  },

  // 积分充值页面
  creditsPage: {
    kicker: '积分充值',
    title: '积分 — 灵活解锁每一集',
    subtitle: '购买积分包，按需解锁单集 AI 漫剧。积分永不过期，想什么时候解锁就什么时候解锁。',

    // 余额卡
    balanceLabel: '我的积分',
    balanceHint: '1 积分 = 解锁 1 集 AI 漫剧',
    purchaseBtn: '去充值',

    // 充值包
    rechargeTitle: '选择积分包',
    rechargeSubtitle: '适合想先看再追的探索党，买到就是赚到。',
    popularTag: '最受欢迎',
    buyBtn: '立即购买',

    // 充值包详细数据（带折扣）
    rechargePacks: [
      { credits: '50', price: '4.99', originalPrice: '6.00', bonus: null, tag: null, perks: ['解锁 50 集 AI 漫剧', '积分永不过期', '入门首选'] },
      { credits: '200', price: '19.99', originalPrice: '24.00', bonus: '+30', tag: '最受欢迎', perks: ['解锁 200 集 AI 漫剧', '赠送 30 积分', '积分永不过期', '约 $0.09 / 集'] },
      { credits: '500', price: '49.99', originalPrice: '60.00', bonus: '+100', tag: '超值首选', perks: ['解锁 500 集 AI 漫剧', '赠送 100 积分', '积分永不过期', '约 $0.08 / 集'] },
      { credits: '1000', price: '99.99', originalPrice: '120.00', bonus: '+250', tag: '资深追剧党', perks: ['解锁 1000 集 AI 漫剧', '赠送 250 积分', '积分永不过期', '约 $0.07 / 集'] }
    ],

    // 使用说明
    howToTitle: '积分怎么用？',
    howToSubtitle: '简单三步，灵活解锁',
    steps: [
      { title: '购买积分包', desc: '选择适合你的积分包，通过 Stripe 安全支付。' },
      { title: '浏览 AI 漫剧', desc: '第一集永远免费，预览后决定要不要解锁。' },
      { title: '解锁想看的集数', desc: '点击"解锁"即可消耗 1 积分观看完整一集。' }
    ],

    // 明细
    historyTitle: '积分明细',
    historyEmpty: '暂无积分记录',
    historyColumns: ['类型', '描述', '时间', '变动'],
    typePurchase: '充值',
    typeUnlock: '解锁',
    typeBonus: '赠送',
    typeRefund: '退款',

    // 静态历史数据
    historyData: [
      { type: 'purchase', desc: '购买 200 积分包', time: '2026-09-08 14:32', delta: 230 },
      { type: 'unlock', desc: '解锁《隐秘的爱恋》第 24 集', time: '2026-09-08 15:10', delta: -1 },
      { type: 'unlock', desc: '解锁《午夜契约》第 8 集', time: '2026-09-07 21:05', delta: -1 },
      { type: 'unlock', desc: '解锁《城市之光》第 15 集', time: '2026-09-07 12:48', delta: -1 },
      { type: 'bonus', desc: '季度会员赠送积分', time: '2026-08-15 09:00', delta: 50 },
      { type: 'purchase', desc: '购买 50 积分包', time: '2026-08-10 16:20', delta: 50 }
    ],

    plus: (n) => `+${n}`,
    minus: (n) => `-${n}`,
    today: '今天',
    yesterday: '昨天',

    // CTA
    ctaTitle: '对积分还有疑问？',
    ctaSubtitle: '联系客服，我们随时为你解答。'
  },

  lang: {
    switchToEn: 'English',
    switchToZh: '中文',
    label: '语言'
  },

  // 公司信息（同时出现在 AppFooter 和 ContactView）
  company: {
    name: '香港饮冰文化传媒有限公司',
    address: 'RM 1503-09, 15/F, Causeway Bay Centre, 15-23 Sugar St, Causeway Bay, Hong Kong',
    email: 'trendameasor@mail.com',
    phone: '+852 6123 4567'
  }
}
