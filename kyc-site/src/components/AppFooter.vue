<template>
  <footer class="site-footer">
    <div class="container footer-inner">
      <div class="footer-col footer-brand">
        <div class="brand">
          <svg viewBox="0 0 64 64" width="32" height="32" aria-hidden="true">
            <defs>
              <linearGradient id="starGradF" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0%" stop-color="#D4AF68" />
                <stop offset="100%" stop-color="#C86D26" />
              </linearGradient>
            </defs>
            <rect width="64" height="64" rx="14" fill="var(--bg-card)" stroke="url(#starGradF)" stroke-width="2" />
            <path d="M32 14 L35.5 26 L48 26 L38 33 L42 45 L32 37 L22 45 L26 33 L16 26 L28.5 26 Z" fill="url(#starGradF)" />
          </svg>
          <span class="brand-name">{{ t('brand.name') }}</span>
        </div>
        <p class="footer-desc">
          {{ t('footer.desc') }}
        </p>
      </div>

      <div class="footer-col">
        <h4>{{ t('footer.platform') }}</h4>
        <router-link to="/">{{ t('nav.home') }}</router-link>
        <router-link to="/pricing">{{ t('nav.pricing') }}</router-link>
        <router-link to="/vip">{{ t('nav.vip') }}</router-link>
        <router-link to="/credits">{{ t('nav.credits') }}</router-link>
        <router-link to="/contact">{{ t('nav.contact') }}</router-link>
      </div>

      <div class="footer-col">
        <h4>{{ t('footer.legal') }}</h4>
        <router-link to="/privacy">{{ t('footer.privacy') }}</router-link>
        <router-link to="/refund">{{ t('footer.refund') }}</router-link>
        <router-link to="/terms">{{ t('footer.terms') }}</router-link>
      </div>

      <div class="footer-col">
        <h4>{{ t('footer.company') }}</h4>
        <p>{{ t('company.name') }}</p>
        <p>{{ t('company.address') }}</p>
        <a :href="`mailto:${t('company.email')}`">{{ t('company.email') }}</a>
        <p>{{ t('company.phone') }}</p>
      </div>
    </div>

    <div class="footer-bottom">
      <div class="container">
        <p>&copy; {{ year }} {{ t('company.name') }}. {{ t('brand.copyright') }}</p>
        <p class="payment-note">{{ t('brand.paymentNote') }}</p>
      </div>
    </div>
  </footer>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from '../i18n'

const { t } = useI18n()
const year = computed(() => new Date().getFullYear())
</script>

<style scoped>
.site-footer {
  background: var(--bg-soft);
  border-top: 1px solid var(--border);
  margin-top: 40px;
}
.footer-inner {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1.5fr;
  gap: 40px;
  padding: 64px 24px 48px;
}
.footer-brand .brand {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}
.brand-name {
  font-size: 20px;
  font-weight: 800;
  color: var(--text-main);
  font-family: 'Noto Sans SC', 'Inter', sans-serif;
}
.footer-desc {
  color: var(--text-muted);
  font-size: 14px;
  line-height: 1.7;
  max-width: 340px;
  font-family: 'Noto Sans SC', 'Inter', sans-serif;
}
.footer-col h4 {
  font-size: 14px;
  font-weight: 700;
  color: var(--gold);
  margin-bottom: 16px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}
.footer-col a,
.footer-col p {
  display: block;
  color: var(--text-soft);
  font-size: 14px;
  margin-bottom: 10px;
  transition: color 0.2s;
  font-family: 'Noto Sans SC', 'Inter', sans-serif;
}
.footer-col a:hover {
  color: var(--gold);
}
.footer-bottom {
  border-top: 1px solid var(--border);
  padding: 20px 0;
}
.footer-bottom .container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.footer-bottom p {
  color: var(--text-muted);
  font-size: 13px;
}
.payment-note {
  font-size: 12px !important;
}

@media (max-width: 860px) {
  .footer-inner {
    grid-template-columns: 1fr 1fr;
    gap: 32px;
  }
}
@media (max-width: 560px) {
  .footer-inner {
    grid-template-columns: 1fr;
  }
  .footer-bottom .container {
    flex-direction: column;
    text-align: center;
  }
}
</style>
