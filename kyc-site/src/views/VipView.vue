<template>
  <div class="vip-page">
    <!-- Hero -->
    <section class="page-hero">
      <div class="container">
        <p class="kicker">{{ t('vipPage.kicker') }}</p>
        <h1 class="page-title">{{ t('vipPage.title') }}</h1>
        <p class="page-subtitle">{{ t('vipPage.subtitle') }}</p>
      </div>
    </section>

    <!-- 会员状态卡 -->
    <section class="section">
      <div class="container">
        <div class="status-card">
          <div class="status-glow"></div>
          <div class="status-inner">
            <div class="status-badge">
              <svg viewBox="0 0 64 64" width="48" height="48" aria-hidden="true">
                <path d="M32 8 L38 24 L56 24 L42 34 L48 52 L32 42 L16 52 L22 34 L8 24 L26 24 Z" fill="currentColor" />
              </svg>
            </div>
            <div class="status-info">
              <div class="status-level">{{ t('vipPage.statusActive') }}</div>
              <div class="status-days">{{ t('vipPage.daysLeft', 87) }}</div>
              <div class="status-source">{{ t('vipPage.levelSource') }}</div>
            </div>
            <div class="status-cta">
              <router-link to="/pricing" class="btn btn-gold">{{ t('vipPage.renewBtn') }}</router-link>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 权益对比表 -->
    <section class="section benefit-section">
      <div class="container">
        <div class="section-head">
          <p class="kicker">{{ t('vipPage.benefitTitle') }}</p>
          <h2 class="section-title">{{ t('vipPage.benefitTitle') }}</h2>
          <p class="section-subtitle">{{ t('vipPage.benefitSubtitle') }}</p>
        </div>
        <div class="benefit-table-wrap">
          <table class="benefit-table">
            <thead>
              <tr>
                <th v-for="(h, i) in t('vipPage.benefitHeaders')" :key="i" :class="{ first: i === 0 }">{{ h }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(b, i) in benefits" :key="i">
                <td class="feature-name">{{ b.name }}</td>
                <td class="val" :class="b.free ? 'yes' : 'no'">{{ b.free ? t('vipPage.benefitYes') : t('vipPage.benefitNo') }}</td>
                <td class="val" :class="b.monthly ? 'yes' : 'no'">{{ b.monthly ? t('vipPage.benefitYes') : t('vipPage.benefitNo') }}</td>
                <td class="val" :class="b.quarterly ? 'yes' : 'no'">{{ b.quarterly ? t('vipPage.benefitYes') : t('vipPage.benefitNo') }}</td>
                <td class="val best" :class="b.yearly ? 'yes' : 'no'">{{ b.yearly ? t('vipPage.benefitYes') : t('vipPage.benefitNo') }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <!-- 套餐推荐 -->
    <section class="section plan-section">
      <div class="container">
        <div class="section-head">
          <p class="kicker">{{ t('vipPage.planSectionKicker') }}</p>
          <h2 class="section-title">{{ t('vipPage.planSectionTitle') }}</h2>
          <p class="section-subtitle">{{ t('vipPage.planSectionSubtitle') }}</p>
        </div>
        <div class="grid-3">
          <div
            v-for="(plan, idx) in t('pricing.vipPlans')"
            :key="idx"
            class="plan-card"
            :class="{ popular: plan.popular }"
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
                <span class="check">{{ t('vipPage.benefitYes') }}</span>
                {{ feature }}
              </li>
            </ul>
            <router-link to="/pricing" class="btn" :class="plan.popular ? 'btn-gold' : 'btn-ghost'">{{ t('vipPage.subscribeBtn') }}</router-link>
          </div>
        </div>
      </div>
    </section>

    <!-- 购买记录 -->
    <section class="section">
      <div class="container">
        <div class="section-head">
          <p class="kicker">{{ t('vipPage.historyTitle') }}</p>
          <h2 class="section-title">{{ t('vipPage.historyTitle') }}</h2>
        </div>
        <div class="history-table-wrap">
          <table class="history-table">
            <thead>
              <tr>
                <th v-for="(h, i) in t('vipPage.historyColumns')" :key="i">{{ h }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(rec, i) in history" :key="i">
                <td class="rec-plan">{{ rec.name }}</td>
                <td>{{ rec.purchasedAt }}</td>
                <td class="rec-period">{{ rec.validFrom }} → {{ rec.validTo }}</td>
                <td>
                  <span class="status-pill" :class="rec.status">
                    {{ rec.status === 'active' ? t('vipPage.historyStatusActive') : t('vipPage.historyStatusExpired') }}
                  </span>
                </td>
                <td class="rec-price">{{ rec.price }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <!-- CTA -->
    <section class="cta-section">
      <div class="container cta-inner">
        <h2>{{ t('vipPage.ctaTitle') }}</h2>
        <p>{{ t('vipPage.ctaSubtitle') }}</p>
        <router-link to="/contact" class="btn btn-gold btn-lg">{{ t('nav.contact') }}</router-link>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from '../i18n'

const { t } = useI18n()
const benefits = computed(() => t('vipPage.benefits'))
const history = computed(() => t('vipPage.historyData'))
</script>

<style scoped>
.page-hero { padding: 64px 0 24px; text-align: center; }
.page-title { font-size: clamp(30px, 5vw, 44px); font-weight: 900; letter-spacing: -0.02em; margin-bottom: 16px; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.page-subtitle { font-size: 18px; color: var(--text-soft); max-width: 560px; margin: 0 auto; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.section-head { text-align: center; margin-bottom: 48px; }
.section-head .section-subtitle { margin: 12px auto 0; }

/* 状态卡 */
.status-card {
  position: relative;
  background:
    radial-gradient(circle at 20% 20%, rgba(247, 198, 106, 0.22), transparent 55%),
    linear-gradient(135deg, rgba(247, 198, 106, 0.16), rgba(77, 208, 225, 0.10)),
    var(--bg-card);
  border: 1px solid rgba(247, 198, 106, 0.35);
  border-radius: 20px;
  padding: 28px 32px;
  overflow: hidden;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.35), inset 0 1px 0 rgba(255, 255, 255, 0.06);
}
.status-glow { position: absolute; inset: -1px; background: linear-gradient(135deg, rgba(247,198,106,0.25), transparent 50%, rgba(200,109,38,0.2)); filter: blur(30px); z-index: -1; }
.status-inner { display: flex; align-items: center; gap: 24px; }
.status-badge {
  width: 68px; height: 68px; border-radius: 50%;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  color: #11100d;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 10px 28px rgba(247, 198, 106, 0.4), inset 0 2px 0 rgba(255, 255, 255, 0.5);
}
.status-info { flex: 1; }
.status-level { font-size: 28px; font-weight: 900; font-family: 'Noto Sans SC', 'Inter', sans-serif; letter-spacing: -0.01em; }
.status-days { font-size: 15px; color: var(--gold); margin-top: 6px; font-weight: 600; }
.status-source { font-size: 13px; color: var(--text-muted); margin-top: 4px; }

/* 权益表 */
.benefit-section { background: var(--bg-soft); border-top: 1px solid var(--border); border-bottom: 1px solid var(--border); }
.benefit-table-wrap { overflow-x: auto; max-width: 960px; margin: 0 auto; border-radius: var(--radius); border: 1px solid var(--border); background: var(--bg-card); }
.benefit-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.benefit-table th, .benefit-table td { padding: 14px 18px; text-align: center; border-bottom: 1px solid var(--border); font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.benefit-table thead th { background: var(--bg-soft); font-weight: 700; color: var(--gold); font-size: 13px; text-transform: uppercase; letter-spacing: 0.04em; }
.benefit-table th.first, .benefit-table td.feature-name { text-align: left; font-weight: 600; }
.benefit-table tbody tr:last-child td { border-bottom: none; }
.benefit-table tbody tr:hover { background: rgba(255, 255, 255, 0.02); }
.val.yes { color: var(--gold); font-weight: 800; font-size: 18px; }
.val.no { color: var(--text-muted); }
.val.best { background: rgba(247, 198, 106, 0.08); }

/* 套餐卡 */
.plan-card { background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius); padding: 36px 28px; position: relative; display: flex; flex-direction: column; }
.plan-card.popular { border-color: var(--gold); box-shadow: 0 0 0 1px var(--gold-muted), 0 16px 48px rgba(212, 175, 104, 0.12); }
.popular-badge { position: absolute; top: -13px; left: 50%; transform: translateX(-50%); background: linear-gradient(135deg, var(--gold), #c9a24f); color: #1a1612; font-size: 12px; font-weight: 700; padding: 5px 16px; border-radius: 20px; }
.plan-name { font-size: 20px; font-family: 'Noto Sans SC', 'Inter', sans-serif; margin-bottom: 12px; }
.plan-price { display: flex; align-items: baseline; gap: 2px; margin-bottom: 8px; }
.plan-price .currency { font-size: 22px; color: var(--gold); font-weight: 700; }
.plan-price .amount { font-size: 48px; font-weight: 900; }
.plan-price .period { font-size: 16px; color: var(--text-muted); margin-left: 4px; }
.plan-desc { color: var(--text-soft); font-size: 14px; margin-bottom: 24px; min-height: 40px; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.plan-features { list-style: none; margin-bottom: 28px; flex: 1; }
.plan-features li { display: flex; gap: 10px; align-items: flex-start; color: var(--text-soft); font-size: 14px; margin-bottom: 12px; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.plan-features .check { color: var(--gold); font-weight: 700; flex-shrink: 0; }
.plan-card .btn { width: 100%; }

/* 记录表格 */
.history-table-wrap { overflow-x: auto; border-radius: var(--radius); border: 1px solid var(--border); background: var(--bg-card); }
.history-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.history-table th, .history-table td { padding: 14px 18px; border-bottom: 1px solid var(--border); text-align: left; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.history-table thead th { background: var(--bg-soft); font-weight: 700; color: var(--gold); font-size: 13px; text-transform: uppercase; letter-spacing: 0.04em; }
.history-table tbody tr:last-child td { border-bottom: none; }
.history-table tbody tr:hover { background: rgba(255, 255, 255, 0.02); }
.rec-plan { font-weight: 600; }
.rec-period { color: var(--text-muted); font-size: 13px; }
.rec-price { font-weight: 700; color: var(--gold); }

.status-pill { font-size: 12px; font-weight: 700; padding: 4px 14px; border-radius: 20px; letter-spacing: 0.03em; }
.status-pill.active { background: var(--gold-muted); color: var(--gold); }
.status-pill.expired { background: rgba(255, 255, 255, 0.08); color: var(--text-muted); }

.cta-section { padding: 80px 0; text-align: center; }
.cta-inner h2 { font-size: clamp(26px, 4vw, 36px); margin-bottom: 12px; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.cta-inner p { color: var(--text-soft); margin-bottom: 28px; font-family: 'Noto Sans SC', 'Inter', sans-serif; }

@media (max-width: 760px) {
  .status-inner { flex-direction: column; text-align: center; }
  .status-cta { width: 100%; margin-top: 16px; }
  .status-cta .btn { width: 100%; }
}
</style>
