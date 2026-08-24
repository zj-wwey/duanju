/**
 * Shared currency utilities for frontend (admin + uniapp)
 * Import from: '../../shared/utils/currency.js'
 */

const RAW_CURRENCY_MAP = {
  'zh-CN': { code: 'USD', symbol: '$', name: '美元', decimals: 2, shortLocale: 'zh-Hans' },
  'zh-TW': { code: 'USD', symbol: '$', name: '美元', decimals: 2, shortLocale: 'zh-Hant' },
  'en-US': { code: 'USD', symbol: '$', name: '美元', decimals: 2, shortLocale: 'en' },
  'en-GB': { code: 'USD', symbol: '$', name: '美元', decimals: 2, shortLocale: 'en' },
  'ja-JP': { code: 'USD', symbol: '$', name: '美元', decimals: 2, shortLocale: 'ja' },
  'ko-KR': { code: 'USD', symbol: '$', name: '美元', decimals: 2, shortLocale: 'ko' },
  'th-TH': { code: 'USD', symbol: '$', name: '美元', decimals: 2, shortLocale: 'th' },
  'vi-VN': { code: 'USD', symbol: '$', name: '美元', decimals: 2, shortLocale: 'vi' },
  'id-ID': { code: 'USD', symbol: '$', name: '美元', decimals: 2, shortLocale: 'id' },
  'ms-MY': { code: 'USD', symbol: '$', name: '美元', decimals: 2, shortLocale: 'ms' },
  'es-ES': { code: 'USD', symbol: '$', name: '美元', decimals: 2, shortLocale: 'es' },
  'fr-FR': { code: 'USD', symbol: '$', name: '美元', decimals: 2, shortLocale: 'fr' },
  'de-DE': { code: 'USD', symbol: '$', name: '美元', decimals: 2, shortLocale: 'de' },
  'it-IT': { code: 'USD', symbol: '$', name: '美元', decimals: 2, shortLocale: 'it' },
  'pt-BR': { code: 'USD', symbol: '$', name: '美元', decimals: 2, shortLocale: 'pt' },
  'ru-RU': { code: 'USD', symbol: '$', name: '美元', decimals: 2, shortLocale: 'ru' },
  'tr-TR': { code: 'USD', symbol: '$', name: '美元', decimals: 2, shortLocale: 'tr' },
  'ar-SA': { code: 'USD', symbol: '$', name: '美元', decimals: 2, shortLocale: 'ar' }
}

export const CURRENCY_MAP = {}
for (const [locale, config] of Object.entries(RAW_CURRENCY_MAP)) {
  CURRENCY_MAP[locale] = {
    ...config,
    currencyCode: config.code,
    currencyName: config.name
  }
}

const SHORT_LOCALE_MAP = {}
for (const [fullLocale, config] of Object.entries(CURRENCY_MAP)) {
  if (!SHORT_LOCALE_MAP[config.shortLocale]) {
    SHORT_LOCALE_MAP[config.shortLocale] = config
  }
}

const CODE_MAP = {}
for (const config of Object.values(CURRENCY_MAP)) {
  if (!CODE_MAP[config.code]) {
    CODE_MAP[config.code] = config
  }
}

export function getCurrencyByLocale(locale) {
  return CURRENCY_MAP[locale] || SHORT_LOCALE_MAP[locale] || CURRENCY_MAP['en-US']
}

export function getCurrencyByCode(code) {
  return CODE_MAP[code] || CURRENCY_MAP['en-US']
}

export function formatPrice(priceCents, currencyCode) {
  const config = getCurrencyByCode(currencyCode)
  if (!config) return `${priceCents}`

  const decimals = config.decimals || 2
  const amount = priceCents / Math.pow(10, decimals)

  let formatted
  if (decimals === 0) {
    formatted = Math.round(amount).toLocaleString('en-US')
  } else {
    formatted = amount.toLocaleString('en-US', {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals
    })
  }

  return `${config.symbol}${formatted}`
}

export function getCurrencyOptions() {
  return Object.values(CODE_MAP).map(config => ({
    code: config.code,
    symbol: config.symbol,
    label: `${config.name} (${config.code})`,
    decimals: config.decimals
  }))
}

export const localeOptions = [
  { code: 'zh-CN', label: '简体中文' },
  { code: 'zh-TW', label: '繁體中文' },
  { code: 'en-US', label: 'English (US)' },
  { code: 'en-GB', label: 'English (UK)' },
  { code: 'ja-JP', label: '日本語' },
  { code: 'ko-KR', label: '한국어' },
  { code: 'th-TH', label: 'ภาษาไทย' },
  { code: 'vi-VN', label: 'Tiếng Việt' },
  { code: 'id-ID', label: 'Bahasa Indonesia' },
  { code: 'ms-MY', label: 'Bahasa Melayu' },
  { code: 'es-ES', label: 'Español' },
  { code: 'fr-FR', label: 'Français' },
  { code: 'de-DE', label: 'Deutsch' },
  { code: 'it-IT', label: 'Italiano' },
  { code: 'pt-BR', label: 'Português' },
  { code: 'ru-RU', label: 'Русский' },
  { code: 'tr-TR', label: 'Türkçe' },
  { code: 'ar-SA', label: 'العربية' }
]