import { createI18n } from 'vue-i18n'
import { detectLocale, loadLocaleMessages, applyLocaleSideEffects, DEFAULT_LOCALE } from './loader.js'
import { adminI18n, normalizeLocale } from '../i18n.js'

const initialLocale = detectLocale()

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: initialLocale,
  fallbackLocale: ['en-US', 'zh-CN'],
  messages: {},
  missingWarn: import.meta.env.DEV,
  fallbackWarn: import.meta.env.DEV
})

export function useI18n() {
  return {
    t: i18n.global.t,
    locale: i18n.global.locale,
    setLocale: async (locale) => {
      await loadLocaleMessages(locale)
      const normalized = normalizeLocale(locale)
      i18n.global.locale.value = locale
      adminI18n.global.locale.value = normalized
      applyLocaleSideEffects(locale)
    }
  }
}

export { initialLocale }