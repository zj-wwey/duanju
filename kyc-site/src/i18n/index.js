import { reactive, computed } from 'vue'
import zhCN from './zh-CN.js'
import enUS from './en-US.js'

const messages = {
  'zh-CN': zhCN,
  'en-US': enUS
}

// 从 localStorage 读取，默认中文
const saved = (typeof localStorage !== 'undefined' && localStorage.getItem('marastel-locale')) || 'zh-CN'
const supported = ['zh-CN', 'en-US']
const current = supported.includes(saved) ? saved : 'zh-CN'

const state = reactive({ locale: current })

export const locale = computed(() => state.locale)

export function t(key, ...args) {
  const parts = key.split('.')
  let node = messages[state.locale] || {}
  for (const p of parts) {
    if (node && typeof node === 'object' && p in node) {
      node = node[p]
    } else {
      // fallback: 从另一种语言找，找不到返回 key 本身
      let fb = messages[state.locale === 'zh-CN' ? 'en-US' : 'zh-CN']
      for (const q of parts) {
        if (fb && typeof fb === 'object' && q in fb) fb = fb[q]; else { fb = undefined; break }
      }
      const result = fb ?? key
      return typeof result === 'function' ? result(...args) : result
    }
  }
  return typeof node === 'function' ? node(...args) : node
}

export function setLocale(loc) {
  if (supported.includes(loc)) {
    state.locale = loc
    try { localStorage.setItem('marastel-locale', loc) } catch {}
  }
}

export function toggleLocale() {
  setLocale(state.locale === 'zh-CN' ? 'en-US' : 'zh-CN')
}

export function useI18n() {
  return { locale, t, setLocale, toggleLocale }
}
