// Feature card 图标 —— 金色渐变风格，直接用于 v-html
// 每个图标 28x28，颜色用 currentColor，由外层 .feature-icon 统一控制

export const featureIcons = {
  // AI 独家内容：芯片 + 星
  aiExclusive: `<svg viewBox="0 0 28 28" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
    <rect x="5" y="5" width="18" height="18" rx="3"/>
    <rect x="9" y="9" width="10" height="10" rx="2"/>
    <path d="M9 2v3M14 2v3M19 2v3M9 23v3M14 23v3M19 23v3M2 9h3M2 14h3M2 19h3M23 9h3M23 14h3M23 19h3"/>
    <path d="M14 10.5l1.2 2.5 2.6.4-1.9 1.9.5 2.6-2.4-1.3-2.4 1.3.5-2.6-1.9-1.9 2.6-.4z" fill="currentColor" stroke="none"/>
  </svg>`,

  // 极速更新：闪电 + 旋转箭头
  speedUpdate: `<svg viewBox="0 0 28 28" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
    <path d="M15 3l-4 9h4l-2 13 10-14h-5l3-8z" fill="currentColor" fill-opacity="0.15"/>
    <path d="M15 3l-4 9h4l-2 13 10-14h-5l3-8z"/>
    <path d="M5 16c0-3.3 2.7-6 6-6" stroke-dasharray="2 2"/>
    <path d="M7 14l-2 2.5 2.3 1.3"/>
  </svg>`,

  // 安全支付：盾牌 + 锁
  securePayment: `<svg viewBox="0 0 28 28" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
    <path d="M14 3l9 3.5V13c0 5-3.8 8.7-9 10-5.2-1.3-9-5-9-10V6.5L14 3z" fill="currentColor" fill-opacity="0.1"/>
    <path d="M14 3l9 3.5V13c0 5-3.8 8.7-9 10-5.2-1.3-9-5-9-10V6.5L14 3z"/>
    <rect x="10" y="12" width="8" height="6" rx="1.5" fill="currentColor" fill-opacity="0.2"/>
    <path d="M12 12v-2a2 2 0 014 0v2"/>
    <circle cx="14" cy="15" r="0.8" fill="currentColor"/>
  </svg>`,

  // 积分永不过期：金币 + ∞ 无限符号
  creditsForever: `<svg viewBox="0 0 28 28" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
    <circle cx="9" cy="14" r="5" fill="currentColor" fill-opacity="0.15"/>
    <circle cx="9" cy="14" r="5"/>
    <text x="9" y="16.5" text-anchor="middle" font-size="7" font-weight="900" fill="currentColor" stroke="none">★</text>
    <path d="M19 10a2.5 2.5 0 111.8 4.2L17 19H14l2.5-3.2" stroke-width="2"/>
    <path d="M19.5 14.5l-1 1.3" stroke-width="2"/>
  </svg>`,

  // 7×24 客服：耳机 + 对话气泡
  support: `<svg viewBox="0 0 28 28" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
    <path d="M6 15v2a3 3 0 003 3h1v-7H9a3 3 0 00-3 2z" fill="currentColor" fill-opacity="0.15"/>
    <path d="M6 15v2a3 3 0 003 3h1v-7H9a3 3 0 00-3 2z"/>
    <path d="M22 15v2a3 3 0 01-3 3h-1v-7h1a3 3 0 013 2z" fill="currentColor" fill-opacity="0.15"/>
    <path d="M22 15v2a3 3 0 01-3 3h-1v-7h1a3 3 0 013 2z"/>
    <path d="M7 14c0-3.9 3.1-7 7-7s7 3.1 7 7"/>
    <circle cx="10" cy="20" r="0.8" fill="currentColor"/>
    <circle cx="14" cy="21" r="0.8" fill="currentColor"/>
    <circle cx="18" cy="20" r="0.8" fill="currentColor"/>
  </svg>`,

  // 随时可取消：对勾 + 圆环 + 破折表示无合同
  cancelAnytime: `<svg viewBox="0 0 28 28" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
    <path d="M4 14c0-5.5 4.5-10 10-10s10 4.5 10 10-4.5 10-10 10S4 19.5 4 14z" fill="currentColor" fill-opacity="0.08"/>
    <path d="M4 14c0-5.5 4.5-10 10-10s10 4.5 10 10-4.5 10-10 10S4 19.5 4 14z"/>
    <path d="M14 8v6l4 2" stroke-width="2"/>
    <path d="M9 21.5l-1.5 2M19 21.5l1.5 2" stroke-width="1.2" stroke-opacity="0.6"/>
  </svg>`
}
