import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './style.css'
import './rtl.css'
import App from './App.vue'
import { router } from './router/index.js'
import { i18n } from './i18n/index.js'
import { detectLocale, loadLocaleMessages, applyLocaleSideEffects } from './i18n/loader.js'
import { streamI18n, loadStreamLocaleMessages } from './locales/streamI18n.js'
import { adminI18n, normalizeLocale } from './i18n.js'

async function bootstrap() {
  let mountError = null
  try {
    const initialLocale = detectLocale()
    console.log('[bootstrap] detected locale:', initialLocale)

    try {
      await loadLocaleMessages(initialLocale)
      console.log('[bootstrap] loaded locale:', initialLocale)
    } catch (e) {
      console.warn('[bootstrap] failed to load locale:', initialLocale, e)
    }

    try {
      await loadLocaleMessages('en-US')
      console.log('[bootstrap] loaded fallback locale: en-US')
    } catch (e) {
      console.warn('[bootstrap] failed to load fallback locale: en-US', e)
    }

    try {
      await loadStreamLocaleMessages(initialLocale)
      console.log('[bootstrap] loaded stream locale:', initialLocale)
    } catch (e) {
      console.warn('[bootstrap] failed to load stream locale:', initialLocale, e)
    }

    applyLocaleSideEffects(initialLocale)
    streamI18n.global.locale.value = initialLocale
    adminI18n.global.locale.value = normalizeLocale(initialLocale)

    console.log('[bootstrap] creating Vue app...')
    const app = createApp(App)
    app.use(ElementPlus)
    app.use(router)
    app.use(i18n)
    app.mount('#app')
    console.log('[bootstrap] Vue app mounted successfully')
  } catch (err) {
    mountError = err
    console.error('[bootstrap] FATAL:', err)
    console.error('[bootstrap] stack:', err?.stack)
    const el = document.getElementById('app')
    if (el) {
      el.innerHTML = '<div style="padding:40px;text-align:center;font-family:sans-serif;color:#c00;font-size:16px;">页面加载失败，请刷新重试<br><small style="color:#999;">' + (err?.message || err) + '</small></div>'
    }
  }
}

bootstrap().catch(err => {
  console.error('[bootstrap] unhandled error:', err)
  const el = document.getElementById('app')
  if (el) {
    el.innerHTML = '<div style="padding:40px;text-align:center;font-family:sans-serif;color:#c00;font-size:16px;">页面加载失败，请刷新重试<br><small style="color:#999;">' + (err?.message || err) + '</small></div>'
  }
})
