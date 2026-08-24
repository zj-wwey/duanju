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
  const initialLocale = detectLocale()
  await loadLocaleMessages(initialLocale)
  await loadLocaleMessages('en-US')
  await loadStreamLocaleMessages(initialLocale)
  applyLocaleSideEffects(initialLocale)
  streamI18n.global.locale.value = initialLocale
  adminI18n.global.locale.value = normalizeLocale(initialLocale)

  const app = createApp(App)
  app.use(ElementPlus)
  app.use(router)
  app.use(i18n)
  app.mount('#app')
}

bootstrap()
