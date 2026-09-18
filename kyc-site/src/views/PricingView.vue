<template>
  <div class="pricing-page">
    <!-- Header -->
    <section class="page-hero">
      <div class="container">
        <p class="kicker">{{ t('pricing.kicker') }}</p>
        <h1 class="page-title">{{ t('pricing.title') }}</h1>
        <p class="page-subtitle">
          {{ t('pricing.subtitle') }}
        </p>
      </div>
    </section>

    <!-- VIP Plans -->
    <section class="section">
      <div class="container">
        <div class="section-head">
          <p class="kicker">{{ t('pricing.vipKicker') }}</p>
          <h2 class="section-title">{{ t('pricing.vipTitle') }}</h2>
          <p class="section-subtitle">{{ t('pricing.vipSubtitle') }}</p>
        </div>
        <div class="grid-3 plan-list">
          <div
            v-for="(plan, idx) in t('pricing.vipPlans')"
            :key="idx"
            class="pricing-card clickable"
            :class="{ popular: plan.popular }"
            @click="openPlan(plan, 'vip')"
          >
            <div v-if="plan.popular" class="popular-badge">{{ t('pricing.mostPopular') }}</div>
            <h3 class="plan-name">{{ plan.name }}</h3>
            <div class="plan-price">
              <span class="currency">$</span>
              <span class="amount">{{ plan.price }}</span>
              <span class="period">{{ plan.period }}</span>
            </div>
            <p class="plan-desc">{{ plan.description }}</p>
            <ul class="plan-features">
              <li v-for="(feature, fi) in plan.features" :key="fi">
                <span class="check">&#10003;</span>
                {{ feature }}
              </li>
            </ul>
            <button class="btn" :class="plan.popular ? 'btn-gold' : 'btn-ghost'">{{ t('pricing.choosePlan') }}</button>
          </div>
        </div>
      </div>
    </section>

    <!-- Credit Packs -->
    <section class="section credit-section">
      <div class="container">
        <div class="section-head">
          <p class="kicker">{{ t('pricing.creditKicker') }}</p>
          <h2 class="section-title">{{ t('pricing.creditTitle') }}</h2>
          <p class="section-subtitle">
            {{ t('pricing.creditSubtitle') }}
          </p>
        </div>
        <div class="grid-3 credit-list">
          <div
            v-for="(pack, pi) in t('pricing.creditPacks')"
            :key="pi"
            class="credit-card clickable"
            @click="openPlan(pack, 'credit')"
          >
            <div class="credit-amount">
              <span class="credits">{{ pack.credits }}</span>
              <span class="credit-label">{{ t('pricing.creditLabel') }}</span>
            </div>
            <div class="credit-price">${{ pack.price }}</div>
            <p class="credit-rate">{{ t('pricing.creditRate') }}</p>
            <ul class="credit-features">
              <li v-for="(feature, fi) in pack.features" :key="fi">{{ feature }}</li>
            </ul>
            <button class="btn btn-ghost">{{ t('pricing.buyCreditPack') }}</button>
          </div>
        </div>
      </div>
    </section>

    <!-- FAQ -->
    <section class="section">
      <div class="container">
        <div class="section-head">
          <p class="kicker">{{ t('pricing.faqKicker') }}</p>
          <h2 class="section-title">{{ t('pricing.faqTitle') }}</h2>
        </div>
        <div class="faq-list">
          <div v-for="(item, i) in t('pricing.faqs')" :key="i" class="faq-item">
            <button class="faq-q" :class="{ open: openFaq === i }" @click="openFaq = openFaq === i ? -1 : i">
              {{ item.q }}
              <span class="faq-icon">{{ openFaq === i ? '\u2212' : '+' }}</span>
            </button>
            <transition name="expand">
              <div v-if="openFaq === i" class="faq-a"><p>{{ item.a }}</p></div>
            </transition>
          </div>
        </div>
      </div>
    </section>

    <!-- CTA -->
    <section class="cta-section">
      <div class="container cta-inner">
        <h2>{{ t('pricing.ctaTitle') }}</h2>
        <p>{{ t('pricing.ctaSubtitle') }}</p>
        <router-link to="/contact" class="btn btn-gold btn-lg">{{ t('nav.contact') }}</router-link>
      </div>
    </section>

    <!-- 选择方案弹窗 -->
    <transition name="modal">
      <div v-if="selectedPlan" class="modal-overlay" @click.self="selectedPlan = null">
        <div class="modal-card">
          <button class="modal-close" @click="selectedPlan = null" :aria-label="t('nav.closeAria')">&times;</button>
          <div class="modal-body">
            <div class="modal-plan-type">
              <span :class="planType === 'vip' ? 'badge-vip' : 'badge-credit'">
                {{ planType === 'vip' ? t('pricing.modalVip') : t('pricing.modalCredit') }}
              </span>
            </div>
            <h2 class="modal-title">{{ selectedPlan.name || t('pricing.modalCreditPack', selectedPlan.credits) }}</h2>
            <div class="modal-price-row">
              <span class="modal-price">${{ selectedPlan.price }}</span>
              <span class="modal-period">{{ selectedPlan.period || '' }}</span>
            </div>
            <p class="modal-desc">{{ selectedPlan.description || t('pricing.modalDefaultDesc') }}</p>
            <div class="modal-actions">
              <router-link to="/contact" class="btn btn-gold btn-lg" @click="selectedPlan = null">
                {{ t('pricing.modalContact') }}
              </router-link>
              <button class="btn btn-ghost" @click="selectedPlan = null">{{ t('pricing.modalKeepBrowsing') }}</button>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useI18n } from '../i18n'

const { t } = useI18n()

const openFaq = ref(-1)
const selectedPlan = ref(null)
const planType = ref('vip')

function openPlan(plan, type) {
  selectedPlan.value = plan
  planType.value = type
}
</script>

<style scoped>
.page-hero { padding: 64px 0 24px; text-align: center; }
.page-title { font-size: clamp(30px, 5vw, 44px); font-weight: 900; letter-spacing: -0.02em; margin-bottom: 16px; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.page-subtitle { font-size: 18px; color: var(--text-soft); max-width: 560px; margin: 0 auto; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.section-head { text-align: center; margin-bottom: 48px; }
.section-head .section-subtitle { margin: 12px auto 0; }

.pricing-card { background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius); padding: 36px 28px; position: relative; transition: transform 0.3s, border-color 0.3s; display: flex; flex-direction: column; }
.pricing-card.clickable { cursor: pointer; }
.pricing-card.clickable:hover { transform: translateY(-6px); }
.pricing-card.popular { border-color: var(--gold); box-shadow: 0 0 0 1px var(--gold-muted), 0 16px 48px rgba(212, 175, 104, 0.12); }
.popular-badge { position: absolute; top: -13px; left: 50%; transform: translateX(-50%); background: linear-gradient(135deg, var(--gold), #c9a24f); color: #1a1612; font-size: 12px; font-weight: 700; padding: 5px 16px; border-radius: 20px; white-space: nowrap; }
.plan-name { font-size: 20px; color: var(--text-main); margin-bottom: 12px; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.plan-price { display: flex; align-items: baseline; gap: 2px; margin-bottom: 8px; }
.currency { font-size: 22px; color: var(--gold); font-weight: 700; }
.amount { font-size: 48px; font-weight: 900; color: var(--text-main); line-height: 1; }
.period { font-size: 16px; color: var(--text-muted); margin-left: 4px; }
.plan-desc { color: var(--text-soft); font-size: 14px; margin-bottom: 24px; min-height: 40px; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.plan-features { list-style: none; margin-bottom: 28px; flex: 1; }
.plan-features li { display: flex; align-items: flex-start; gap: 10px; color: var(--text-soft); font-size: 14px; margin-bottom: 12px; line-height: 1.5; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.check { color: var(--gold); font-weight: 700; flex-shrink: 0; }
.pricing-card .btn { width: 100%; }

.credit-section { background: var(--bg-soft); border-top: 1px solid var(--border); border-bottom: 1px solid var(--border); }
.credit-card { background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius); padding: 36px 28px; text-align: center; display: flex; flex-direction: column; align-items: center; transition: transform 0.3s, border-color 0.3s; }
.credit-card.clickable { cursor: pointer; }
.credit-card.clickable:hover { transform: translateY(-4px); border-color: var(--gold-muted); }
.credit-amount { display: flex; align-items: baseline; gap: 6px; margin-bottom: 8px; }
.credits { font-size: 48px; font-weight: 900; color: var(--gold); line-height: 1; }
.credit-label { font-size: 16px; color: var(--text-muted); }
.credit-price { font-size: 28px; font-weight: 800; color: var(--text-main); margin-bottom: 4px; }
.credit-rate { font-size: 13px; color: var(--text-muted); margin-bottom: 24px; }
.credit-features { list-style: none; margin-bottom: 24px; width: 100%; }
.credit-features li { color: var(--text-soft); font-size: 14px; padding: 8px 0; border-bottom: 1px solid var(--border); font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.credit-features li::before { content: '\2713'; color: var(--gold); margin-right: 10px; }
.credit-card .btn { width: 100%; }

.faq-list { max-width: 760px; margin: 0 auto; }
.faq-item { border-bottom: 1px solid var(--border); }
.faq-q { width: 100%; background: none; border: none; text-align: left; padding: 22px 4px; font-size: 17px; font-weight: 600; color: var(--text-main); cursor: pointer; display: flex; justify-content: space-between; align-items: center; gap: 16px; transition: color 0.2s; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.faq-q:hover { color: var(--gold); }
.faq-q.open { color: var(--gold); }
.faq-icon { font-size: 24px; flex-shrink: 0; color: var(--gold); }
.faq-a { padding: 0 4px 20px; }
.faq-a p { color: var(--text-soft); font-size: 15px; line-height: 1.8; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.expand-enter-active, .expand-leave-active { transition: all 0.3s ease; overflow: hidden; }
.expand-enter-from, .expand-leave-to { opacity: 0; max-height: 0; padding-bottom: 0; }

.cta-section { padding: 80px 0; text-align: center; }
.cta-inner h2 { font-size: clamp(26px, 4vw, 36px); margin-bottom: 12px; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.cta-inner p { color: var(--text-soft); margin-bottom: 28px; font-family: 'Noto Sans SC', 'Inter', sans-serif; }

.modal-overlay { position: fixed; inset: 0; background: rgba(0, 0, 0, 0.8); backdrop-filter: blur(8px); z-index: 1000; display: flex; align-items: center; justify-content: center; padding: 24px; }
.modal-card { background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius); max-width: 440px; width: 100%; overflow: hidden; position: relative; }
.modal-close { position: absolute; top: 12px; right: 12px; width: 36px; height: 36px; border-radius: 50%; background: rgba(0, 0, 0, 0.5); border: none; color: #fff; font-size: 22px; cursor: pointer; z-index: 2; transition: background 0.2s; }
.modal-close:hover { background: rgba(0, 0, 0, 0.8); }
.modal-body { padding: 36px 28px 28px; text-align: center; }
.modal-plan-type { margin-bottom: 12px; }
.badge-vip { background: var(--gold-muted); color: var(--gold); font-size: 12px; font-weight: 700; letter-spacing: 0.08em; padding: 5px 16px; border-radius: 20px; }
.badge-credit { background: rgba(200, 109, 38, 0.15); color: var(--primary); font-size: 12px; font-weight: 700; letter-spacing: 0.08em; padding: 5px 16px; border-radius: 20px; }
.modal-title { font-size: 24px; margin-bottom: 8px; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.modal-price-row { display: flex; align-items: baseline; justify-content: center; gap: 4px; margin-bottom: 12px; }
.modal-price { font-size: 42px; font-weight: 900; color: var(--gold); }
.modal-period { font-size: 14px; color: var(--text-muted); }
.modal-desc { color: var(--text-soft); font-size: 15px; line-height: 1.7; margin-bottom: 24px; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.modal-actions { display: flex; gap: 12px; flex-wrap: wrap; justify-content: center; }
.modal-actions .btn { flex: 1; min-width: 140px; }

.modal-enter-active, .modal-leave-active { transition: opacity 0.25s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
.modal-enter-active .modal-card, .modal-leave-active .modal-card { transition: transform 0.25s; }
.modal-enter-from .modal-card, .modal-leave-to .modal-card { transform: scale(0.92); }

@media (max-width: 860px) {
  .plan-list, .credit-list { grid-template-columns: 1fr; max-width: 420px; margin: 0 auto; }
}
</style>
