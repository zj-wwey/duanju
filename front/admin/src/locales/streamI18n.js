import { computed } from 'vue'
import { createI18n } from 'vue-i18n'
import { languageOptions } from '../../../shared/i18n/locale-options.js'

export const STREAM_LOCALE_KEY = 'appLocaleV3'

import zhCNMessages from '../../../shared/i18n/stream/zh-CN.json'
import enUSMessages from '../../../shared/i18n/stream/en-US.json'

const loadedLocales = new Set(['zh-CN', 'en-US'])

function detectLocale() {
  try {
    const stored = localStorage.getItem(STREAM_LOCALE_KEY)
    if (stored && languageOptions.find(o => o.code === stored)) return stored
  } catch (e) {}
  try {
    const browser = navigator.language
    const exact = languageOptions.find(o => o.code === browser)
    if (exact) return exact.code
    const prefix = browser.split('-')[0]
    const fuzzy = languageOptions.find(o => o.code.startsWith(prefix))
    if (fuzzy) return fuzzy.code
  } catch (e) {}
  return 'zh-CN'
}

export function applyLocaleSideEffects(locale) {
  const option = languageOptions.find(item => item.code === locale) || languageOptions[0]
  document.documentElement.lang = option.code
  document.documentElement.dir = option.dir || 'ltr'
  try { localStorage.setItem(STREAM_LOCALE_KEY, option.code) } catch (e) {}
}

const RTL_LOCALE_SET = new Set(['ar-SA', 'he-IL', 'fa-IR', 'ur-PK', 'ku-IQ', 'ps-AF', 'syc-SY', 'dv-MV'])

export function getTextDirection(locale) {
  if (!locale) locale = streamI18n.global.locale.value
  const option = languageOptions.find(item => item.code === locale)
  if (option) return option.dir || 'ltr'
  const base = locale.split('-')[0]
  return RTL_LOCALE_SET.has(locale) || ['ar', 'he', 'fa', 'ur', 'ku', 'ps', 'syc', 'dv'].includes(base) ? 'rtl' : 'ltr'
}

export function getCurrentDirection() {
  return getTextDirection(streamI18n.global.locale.value)
}

export const streamI18n = createI18n({
  legacy: false,
  globalInjection: true,
  flatJson: true,
  locale: detectLocale(),
  fallbackLocale: 'en-US',
  messages: {
    'zh-CN': zhCNMessages,
    'en-US': enUSMessages
  }
})

applyLocaleSideEffects(streamI18n.global.locale.value)

export async function loadStreamLocaleMessages(locale) {
  if (loadedLocales.has(locale)) return
  try {
    const module = await import(`../../../shared/i18n/stream/${locale}.json`)
    streamI18n.global.setLocaleMessage(locale, module.default)
    loadedLocales.add(locale)
  } catch (err) {
    console.warn(`Failed to load stream locale: ${locale}`, err)
  }
}

export function isStreamLocaleLoaded(locale) {
  return loadedLocales.has(locale)
}

const _initial = streamI18n.global.locale.value
if (!loadedLocales.has(_initial)) {
  loadStreamLocaleMessages(_initial)
}

export function useStreamI18n() {
  const direction = computed(() => getTextDirection(streamI18n.global.locale.value))
  return {
    t: streamI18n.global.t,
    locale: streamI18n.global.locale,
    direction,
    setLocale: async (locale) => {
      await loadStreamLocaleMessages(locale)
      streamI18n.global.locale.value = locale
      applyLocaleSideEffects(locale)
    }
  }
}
