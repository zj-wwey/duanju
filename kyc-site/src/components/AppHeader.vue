<template>
  <header class="site-header" :class="{ scrolled }">
    <div class="container header-inner">
      <router-link to="/" class="brand">
        <span class="brand-logo">
          <svg viewBox="0 0 64 64" width="36" height="36" aria-hidden="true">
            <defs>
              <linearGradient id="starGrad" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0%" stop-color="#D4AF68" />
                <stop offset="100%" stop-color="#C86D26" />
              </linearGradient>
            </defs>
            <rect width="64" height="64" rx="14" fill="var(--bg-card)" stroke="url(#starGrad)" stroke-width="2" />
            <path d="M32 14 L35.5 26 L48 26 L38 33 L42 45 L32 37 L22 45 L26 33 L16 26 L28.5 26 Z" fill="url(#starGrad)" />
          </svg>
        </span>
        <span class="brand-name">{{ t('brand.name') }}</span>
      </router-link>

      <nav class="nav-desktop">
        <router-link to="/" class="nav-link">{{ t('nav.home') }}</router-link>
        <router-link to="/pricing" class="nav-link">{{ t('nav.pricing') }}</router-link>
        <router-link to="/vip" class="nav-link nav-vip">{{ t('nav.vip') }}</router-link>
        <router-link to="/credits" class="nav-link">{{ t('nav.credits') }}</router-link>
        <router-link to="/contact" class="nav-link">{{ t('nav.contact') }}</router-link>
        <div class="lang-switch" role="group" :title="t('lang.label')">
          <button class="lang-btn" :class="{ active: locale === 'zh-CN' }" @click="setLocale('zh-CN')">中文</button>
          <span class="lang-sep">/</span>
          <button class="lang-btn" :class="{ active: locale === 'en-US' }" @click="setLocale('en-US')">English</button>
        </div>
        <router-link to="/pricing" class="btn btn-gold nav-cta">{{ t('nav.startNow') }}</router-link>
      </nav>

      <button class="nav-toggle" :class="{ active: menuOpen }" @click="menuOpen = !menuOpen" :aria-label="t('nav.menuAria')">
        <span></span><span></span><span></span>
      </button>
    </div>

    <transition name="slide">
      <nav v-if="menuOpen" class="nav-mobile">
        <router-link to="/" class="nav-link" @click="menuOpen = false">{{ t('nav.home') }}</router-link>
        <router-link to="/pricing" class="nav-link" @click="menuOpen = false">{{ t('nav.pricing') }}</router-link>
        <router-link to="/vip" class="nav-link" @click="menuOpen = false">{{ t('nav.vip') }}</router-link>
        <router-link to="/credits" class="nav-link" @click="menuOpen = false">{{ t('nav.credits') }}</router-link>
        <router-link to="/contact" class="nav-link" @click="menuOpen = false">{{ t('nav.contact') }}</router-link>
        <div class="nav-mobile-extra">
          <div class="lang-switch mobile">
            <button class="lang-btn" :class="{ active: locale === 'zh-CN' }" @click="setLocale('zh-CN')">中文</button>
            <span class="lang-sep">/</span>
            <button class="lang-btn" :class="{ active: locale === 'en-US' }" @click="setLocale('en-US')">English</button>
          </div>
          <router-link to="/pricing" class="btn btn-gold" @click="menuOpen = false">{{ t('nav.startNow') }}</router-link>
        </div>
      </nav>
    </transition>
  </header>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from '../i18n'

const { locale, t, setLocale } = useI18n()

const menuOpen = ref(false)
const scrolled = ref(false)

function onScroll() {
  scrolled.value = window.scrollY > 20
}

onMounted(() => window.addEventListener('scroll', onScroll))
onUnmounted(() => window.removeEventListener('scroll', onScroll))
</script>

<style scoped>
.site-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(10, 9, 8, 0.85);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid transparent;
  transition: border-color 0.3s, background 0.3s;
}
.site-header.scrolled {
  border-bottom-color: var(--border);
  background: rgba(10, 9, 8, 0.95);
}
.header-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 68px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--text-main);
}
.brand-name {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.02em;
  font-family: 'Noto Sans SC', 'Inter', sans-serif;
}
.nav-desktop {
  display: flex;
  align-items: center;
  gap: 8px;
}
.nav-link {
  color: var(--text-soft);
  font-size: 15px;
  font-weight: 500;
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  transition: color 0.2s;
  font-family: 'Noto Sans SC', 'Inter', sans-serif;
}
.nav-link:hover {
  color: var(--gold);
}
.router-link-active.nav-link {
  color: var(--gold);
}
.nav-link.nav-vip {
  color: var(--gold);
  font-weight: 600;
  background: var(--gold-muted);
  padding: 6px 14px;
  border-radius: 16px;
  border: 1px solid rgba(212, 175, 104, 0.3);
}
.nav-link.nav-vip:hover {
  background: rgba(212, 175, 104, 0.22);
}
.router-link-active.nav-link.nav-vip {
  background: rgba(212, 175, 104, 0.28);
}
.nav-cta {
  margin-left: 8px;
}

.lang-switch {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 3px 4px;
}
.lang-btn {
  background: none;
  border: none;
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 600;
  padding: 4px 12px;
  border-radius: 16px;
  cursor: pointer;
  transition: color 0.2s, background 0.2s;
  white-space: nowrap;
  font-family: inherit;
}
.lang-btn:hover {
  color: var(--text-soft);
}
.lang-btn.active {
  background: linear-gradient(135deg, var(--gold), #c9a24f);
  color: #1a1612;
  box-shadow: 0 2px 8px rgba(247, 198, 106, 0.3);
}
.lang-sep {
  color: var(--border);
  font-size: 11px;
  font-weight: 400;
}
.lang-switch.mobile {
  width: 100%;
  justify-content: center;
  padding: 5px;
}
.nav-mobile-extra {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 8px;
  border-top: 1px solid var(--border);
  margin-top: 4px;
}

.nav-toggle {
  display: none;
  flex-direction: column;
  gap: 5px;
  background: none;
  border: none;
  cursor: pointer;
  padding: 8px;
}
.nav-toggle span {
  width: 24px;
  height: 2px;
  background: var(--text-main);
  border-radius: 2px;
  transition: 0.3s;
}
.nav-toggle.active span:nth-child(1) {
  transform: translateY(7px) rotate(45deg);
}
.nav-toggle.active span:nth-child(2) {
  opacity: 0;
}
.nav-toggle.active span:nth-child(3) {
  transform: translateY(-7px) rotate(-45deg);
}
.nav-mobile {
  display: none;
  flex-direction: column;
  padding: 16px 24px 24px;
  gap: 4px;
  background: var(--bg-soft);
  border-bottom: 1px solid var(--border);
}
.nav-mobile .nav-link {
  padding: 14px 8px;
  font-size: 17px;
  border-bottom: 1px solid var(--border);
}
.nav-mobile .btn {
  margin-top: 0;
}
.slide-enter-active,
.slide-leave-active {
  transition: all 0.3s ease;
}
.slide-enter-from,
.slide-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

@media (max-width: 760px) {
  .nav-desktop {
    display: none;
  }
  .nav-toggle {
    display: flex;
  }
  .nav-mobile {
    display: flex;
  }
}
</style>
