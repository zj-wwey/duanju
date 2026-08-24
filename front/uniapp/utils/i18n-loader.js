import {
  LANG_MAP,
  FALLBACK_LANG,
  SUPPORTED_LANGS,
  localeOptions,
  flatten,
  UNIAPP_OVERRIDES,
  zhHansOverrides
} from './uniapp-data.js'
import { formatDuration as formatDurationUtil } from '../../shared/utils/format.js'

const LOADED = {}

const RTL_LANGUAGES = new Set([
  'ar',
  'he',
  'fa',
  'ur',
  'ku',
  'ps',
  'syc',
  'dv'
])

const LANG_DIR_MAP = {
  'ar': 'rtl',
  'ar-SA': 'rtl',
  'he': 'rtl',
  'he-IL': 'rtl',
  'fa': 'rtl',
  'fa-IR': 'rtl',
  'ur': 'rtl',
  'ur-PK': 'rtl',
  'ku': 'rtl',
  'ku-IQ': 'rtl',
  'ps': 'rtl',
  'ps-AF': 'rtl',
  'syc': 'rtl',
  'syc-SY': 'rtl',
  'dv': 'rtl',
  'dv-MV': 'rtl'
}

function getTextDirection(locale) {
  if (!locale) locale = getLocale()
  if (LANG_DIR_MAP[locale]) return LANG_DIR_MAP[locale]
  const base = locale.split('-')[0]
  return RTL_LANGUAGES.has(base) ? 'rtl' : 'ltr'
}

function getCurrentDirection() {
  return getTextDirection(getLocale())
}

function mapLocale(uniappLocale) {
  return LANG_MAP[uniappLocale] || FALLBACK_LANG
}

function getSupportedLanguages() {
  return SUPPORTED_LANGS.slice()
}

function getLocale() {
  return uni.getStorageSync('locale') || 'en'
}

function setLocale(locale) {
  if (!LANG_MAP[locale] && locale !== 'en' && locale !== 'zh-Hans' && locale !== 'zh-Hant') {
    locale = 'en'
  }
  uni.setStorageSync('locale', locale)
  LOADED[locale] = null
}

async function loadMessages(uniappLocale) {
  if (LOADED[uniappLocale]) return LOADED[uniappLocale]

  const sharedLang = mapLocale(uniappLocale)
  const fallbackSharedLang = FALLBACK_LANG

  const [primaryData, fallbackData] = await Promise.all([
    loadJson(sharedLang),
    sharedLang !== fallbackSharedLang ? loadJson(fallbackSharedLang) : Promise.resolve(null)
  ])

  const primaryFlat = flatten(primaryData || {})
  const fallbackFlat = flatten(fallbackData || {})

  const overrides = UNIAPP_OVERRIDES[sharedLang] ||
                     (sharedLang === 'zh-CN' ? zhHansOverrides['zh-CN'] : null) ||
                     UNIAPP_OVERRIDES['en-US']

  const merged = { ...fallbackFlat, ...primaryFlat, ...overrides }

  LOADED[uniappLocale] = merged

  if (_cache[uniappLocale]) {
    _cache[uniappLocale] = { ..._cache[uniappLocale], ...merged }
  } else {
    _cache[uniappLocale] = merged
  }

  return merged
}

const _cache = {}

function getMessagesSync(uniappLocale) {
  if (_cache[uniappLocale]) return _cache[uniappLocale]

  const sharedLang = mapLocale(uniappLocale)
  const base = LOADED[uniappLocale] || UNIAPP_OVERRIDES['en-US']
  const langOverrides = UNIAPP_OVERRIDES[sharedLang] ||
                        (sharedLang === 'zh-CN' ? zhHansOverrides['zh-CN'] : null) ||
                        {}
  const merged = { ...UNIAPP_OVERRIDES['en-US'], ...base, ...langOverrides }
  _cache[uniappLocale] = merged
  return merged
}

function clearCache() {
  Object.keys(LOADED).forEach(k => LOADED[k] = null)
  Object.keys(_cache).forEach(k => delete _cache[k])
}

function t(key, params = {}, locale) {
  const currentLocale = locale || getLocale()
  const messages = getMessagesSync(currentLocale)
  const fallbackMessages = getMessagesSync('en')

  const template = (messages && messages[key]) ||
                   (fallbackMessages && fallbackMessages[key]) ||
                   key

  if (!params || Object.keys(params).length === 0) return template

  return Object.keys(params).reduce((text, name) => {
    return text.replace(new RegExp('\\{' + name + '\\}', 'g'), params[name])
  }, template)
}

function formatDuration(seconds) {
  const sharedLocale = mapLocale(getLocale())
  return formatDurationUtil(seconds, sharedLocale)
}

function loadJson(lang) {
  const url = `/static/i18n/${lang}.json`
  return new Promise((resolve) => {
    uni.request({
      url,
      method: 'GET',
      success: (res) => {
        if (res.statusCode === 200 && res.data) {
          resolve(typeof res.data === 'string' ? JSON.parse(res.data) : res.data)
        } else {
          resolve({})
        }
      },
      fail: () => resolve({})
    })
  })
}

function init() {
  const currentLocale = getLocale()
  if (currentLocale !== 'en' && currentLocale !== 'zh-Hans') {
    loadMessages(currentLocale)
  }
}

init()

export {
  getLocale,
  setLocale,
  getSupportedLanguages,
  getTextDirection,
  getCurrentDirection,
  t,
  formatDuration,
  loadMessages,
  clearCache,
  localeOptions
}
