/**
 * Shared formatting utilities for frontend (admin + uniapp)
 * Import from: '../../shared/utils/format.js'
 */

export function formatNumber(value, locale = 'en-US') {
  if (value == null) return '0'
  return new Intl.NumberFormat(locale).format(Number(value) || 0)
}

export function formatDate(dateStr, locale = 'en-US', showTime = false) {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  if (isNaN(date.getTime())) return String(dateStr)
  const options = { year: 'numeric', month: '2-digit', day: '2-digit' }
  if (showTime) {
    options.hour = '2-digit'
    options.minute = '2-digit'
  }
  return date.toLocaleDateString(locale, options)
}

export function formatMoney(cents, currency = 'USD', locale = 'en-US') {
  if (cents == null) cents = 0
  const amount = Number(cents) / 100
  try {
    return new Intl.NumberFormat(locale, { style: 'currency', currency }).format(amount)
  } catch {
    const symbols = { USD: '$', EUR: '€', GBP: '£', CNY: '¥', JPY: '¥', KRW: '₩' }
    const symbol = symbols[currency] || '$'
    return `${symbol}${amount.toFixed(2)}`
  }
}

export function field(obj, ...keys) {
  if (!obj) return null
  for (const key of keys) {
    const camel = key.replace(/_([a-z])/g, (_, c) => c.toUpperCase())
    if (obj[key] !== undefined && obj[key] !== null) return obj[key]
    if (obj[camel] !== undefined && obj[camel] !== null) return obj[camel]
  }
  return null
}

export function toCamel(str) {
  return str.replace(/_([a-z])/g, (_, c) => c.toUpperCase())
}

export function planPoints(plan) {
  return field(plan, 'points') || 0
}

export function planBonusPoints(plan) {
  return field(plan, 'bonus_points') || 0
}

export function productPoints(item) {
  return (Number(field(item, 'points')) || 0) + (Number(field(item, 'bonus_points', 'bonusPoints')) || 0)
}

export const LEVEL_MAP = {
  NONE: {
    nameKey: 'membershipLevelNone',
    shortKey: 'membershipLevelShortNone',
    color: '#888',
    gradient: 'linear-gradient(135deg, #666, #444)'
  },
  SILVER: {
    nameKey: 'membershipLevelSilver',
    shortKey: 'membershipLevelShortSilver',
    color: '#a8b8c8',
    gradient: 'linear-gradient(135deg, #c0c0c0, #808080)'
  },
  GOLD: {
    nameKey: 'membershipLevelGold',
    shortKey: 'membershipLevelShortGold',
    color: '#d4af68',
    gradient: 'linear-gradient(135deg, #ffe0a1, #f3b84d)'
  },
  DIAMOND: {
    nameKey: 'membershipLevelDiamond',
    shortKey: 'membershipLevelShortDiamond',
    color: '#b388ff',
    gradient: 'linear-gradient(135deg, #80deea, #00bcd4)'
  }
}

export function levelColor(level) {
  return (LEVEL_MAP[level] || LEVEL_MAP.NONE).color
}

export function levelGradient(level) {
  return (LEVEL_MAP[level] || LEVEL_MAP.NONE).gradient
}

export function formatDuration(seconds, locale = 'en-US', options = {}) {
  const total = Math.round(Number(seconds) || 0)
  const zeroUnits = {
    'zh-CN': '0秒',
    'en-US': '0s',
    'ja-JP': '0秒',
    'ko-KR': '0초'
  }
  if (total <= 0) return options.returnEmpty ? '' : (zeroUnits[locale] || zeroUnits['en-US'])
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  const units = {
    'zh-CN': { h: '时', m: '分', s: '秒' },
    'en-US': { h: 'h', m: 'm', s: 's' },
    'ja-JP': { h: '時間', m: '分', s: '秒' },
    'ko-KR': { h: '시간', m: '분', s: '초' }
  }
  const unit = units[locale] || units['en-US']
  let text = ''
  if (h > 0) text += h + unit.h
  if (m > 0 || h > 0) text += m + unit.m
  text += s + unit.s
  return text
}

export function formatPercent(value, decimals = 1) {
  return `${Number(value || 0).toFixed(decimals)}%`
}

export function formatDateTime(dateStr, locale = 'en-US') {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  if (isNaN(date.getTime())) return String(dateStr)
  return date.toLocaleDateString(locale, {
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}