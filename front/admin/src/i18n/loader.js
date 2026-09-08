// i18n loader - lazy loading of translation files
import { i18n } from './index.js'
import { languageOptions } from '../../../shared/i18n/locale-options.js'

export const STORAGE_KEY = 'appLocaleV3'
export const DEFAULT_LOCALE = 'zh-CN'

const loadedLocales = new Set()

export function detectLocale() {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
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

  return DEFAULT_LOCALE
}

export function applyLocaleSideEffects(locale) {
  const option = languageOptions.find(o => o.code === locale) || languageOptions[0]
  document.documentElement.lang = option.code
  document.documentElement.dir = option.dir || 'ltr'
  try {
    localStorage.setItem(STORAGE_KEY, option.code)
  } catch (e) {}
}

export async function loadLocaleMessages(locale) {
  if (loadedLocales.has(locale)) return
  try {
    const module = await import(`../../../shared/i18n/${locale}.json`)
    i18n.global.setLocaleMessage(locale, module.default)
    loadedLocales.add(locale)
  } catch (err) {
    console.warn(`Failed to load locale: ${locale}`, err)
    if (locale !== 'en-US' && !loadedLocales.has('en-US')) {
      try {
        const enModule = await import(`../../../shared/i18n/en-US.json`)
        i18n.global.setLocaleMessage('en-US', enModule.default)
        loadedLocales.add('en-US')
      } catch (e2) {
        console.warn('Failed to load en-US fallback:', e2)
      }
    }
  }
}

export function isLocaleLoaded(locale) {
  return loadedLocales.has(locale)
}