<template>
  <div class="credits-page">
    <!-- Hero -->
    <section class="page-hero">
      <div class="container">
        <p class="kicker">{{ t('creditsPage.kicker') }}</p>
        <h1 class="page-title">{{ t('creditsPage.title') }}</h1>
        <p class="page-subtitle">{{ t('creditsPage.subtitle') }}</p>
      </div>
    </section>

    <!-- 余额卡 -->
    <section class="section">
      <div class="container">
        <div class="balance-card">
          <div class="balance-glow"></div>
          <div class="balance-inner">
            <div class="balance-left">
              <div class="balance-label">{{ t('creditsPage.balanceLabel') }}</div>
              <div class="balance-amount">
                <svg viewBox="0 0 32 32" width="28" height="28" class="coin-icon" aria-hidden="true">
                  <defs>
                    <linearGradient id="coinG" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0%" stop-color="#ffe0a1"/>
                      <stop offset="100%" stop-color="#c9a24f"/>
                    </linearGradient>
                  </defs>
                  <circle cx="16" cy="16" r="14" fill="url(#coinG)" stroke="#b8923e" stroke-width="1" />
                  <text x="16" y="21" text-anchor="middle" font-size="16" font-weight="900" fill="#1a1612">★</text>
                </svg>
                <span class="balance-num">230</span>
              </div>
              <div class="balance-hint">{{ t('creditsPage.balanceHint') }}</div>
            </div>
            <div class="balance-cta">
              <a href="#recharge" class="btn btn-gold btn-lg">{{ t('creditsPage.purchaseBtn') }}</a>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 充值包 -->
    <section class="section recharge-section" id="recharge">
      <div class="container">
        <div class="section-head">
          <p class="kicker">{{ t('creditsPage.rechargeTitle') }}</p>
          <h2 class="section-title">{{ t('creditsPage.rechargeTitle') }}</h2>
          <p class="section-subtitle">{{ t('creditsPage.rechargeSubtitle') }}</p>
        </div>
        <div class="recharge-grid">
          <div
            v-for="(pack, i) in packs"
            :key="i"
            class="recharge-card"
            :class="{ popular: pack.tag === t('creditsPage.popularTag') }"
          >
            <div v-if="pack.tag" class="pack-tag">{{ pack.tag }}</div>
            <div class="pack-credits">
              <svg viewBox="0 0 32 32" width="20" height="20" class="pack-coin" aria-hidden="true">
                <circle cx="16" cy="16" r="14" fill="url(#coinG2)" stroke="#b8923e" stroke-width="1" />
                <text x="16" y="21" text-anchor="middle" font-size="14" font-weight="900" fill="#1a1612">★</text>
              </svg>
              <span class="credits-num">{{ pack.credits }}</span>
              <span class="credits-unit">{{ t('pricing.creditLabel') }}</span>
              <span v-if="pack.bonus" class="pack-bonus">{{ pack.bonus }}</span>
            </div>
            <div class="pack-price-row">
              <span class="price-current">${{ pack.price }}</span>
              <span class="price-original">${{ pack.originalPrice }}</span>
            </div>
            <ul class="pack-perks">
              <li v-for="(perk, pi) in pack.perks" :key="pi">{{ perk }}</li>
            </ul>
            <router-link to="/contact" class="btn btn-gold">{{ t('creditsPage.buyBtn') }}</router-link>
          </div>
        </div>
      </div>
    </section>

    <!-- 使用说明 -->
    <section class="section howto-section">
      <div class="container">
        <div class="section-head">
          <p class="kicker">{{ t('creditsPage.howToTitle') }}</p>
          <h2 class="section-title">{{ t('creditsPage.howToTitle') }}</h2>
          <p class="section-subtitle">{{ t('creditsPage.howToSubtitle') }}</p>
        </div>
        <div class="howto-grid">
          <div v-for="(step, i) in t('creditsPage.steps')" :key="i" class="howto-card">
            <div class="step-num">{{ i + 1 }}</div>
            <h3>{{ step.title }}</h3>
            <p>{{ step.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- 积分明细 -->
    <section class="section">
      <div class="container">
        <div class="section-head">
          <p class="kicker">{{ t('creditsPage.historyTitle') }}</p>
          <h2 class="section-title">{{ t('creditsPage.historyTitle') }}</h2>
        </div>
        <div class="history-table-wrap">
          <table class="history-table">
            <thead>
              <tr>
                <th v-for="(h, i) in t('creditsPage.historyColumns')" :key="i">{{ h }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(rec, i) in history" :key="i">
                <td>
                  <span class="type-pill" :class="`type-${rec.type}`">
                    {{ typeLabel(rec.type) }}
                  </span>
                </td>
                <td class="rec-desc">{{ rec.desc }}</td>
                <td class="rec-time">{{ rec.time }}</td>
                <td class="rec-delta" :class="{ plus: rec.delta > 0, minus: rec.delta < 0 }">
                  {{ rec.delta > 0 ? t('creditsPage.plus', rec.delta) : t('creditsPage.minus', rec.delta) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <!-- CTA -->
    <section class="cta-section">
      <div class="container cta-inner">
        <h2>{{ t('creditsPage.ctaTitle') }}</h2>
        <p>{{ t('creditsPage.ctaSubtitle') }}</p>
        <router-link to="/contact" class="btn btn-gold btn-lg">{{ t('nav.contact') }}</router-link>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from '../i18n'

const { t } = useI18n()
const packs = computed(() => t('creditsPage.rechargePacks'))
const history = computed(() => t('creditsPage.historyData'))

function typeLabel(type) {
  const map = {
    purchase: t('creditsPage.typePurchase'),
    unlock: t('creditsPage.typeUnlock'),
    bonus: t('creditsPage.typeBonus'),
    refund: t('creditsPage.typeRefund')
  }
  return map[type] || type
}
</script>

<style scoped>
.page-hero { padding: 64px 0 24px; text-align: center; }
.page-title { font-size: clamp(30px, 5vw, 44px); font-weight: 900; letter-spacing: -0.02em; margin-bottom: 16px; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.page-subtitle { font-size: 18px; color: var(--text-soft); max-width: 620px; margin: 0 auto; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.section-head { text-align: center; margin-bottom: 48px; }
.section-head .section-subtitle { margin: 12px auto 0; }

/* 余额卡 */
.balance-card {
  position: relative;
  background:
    radial-gradient(circle at 10% 30%, rgba(247, 198, 106, 0.22), transparent 55%),
    linear-gradient(135deg, rgba(247, 198, 106, 0.14), rgba(200, 109, 38, 0.08)),
    var(--bg-card);
  border: 1px solid rgba(247, 198, 106, 0.3);
  border-radius: 20px;
  padding: 28px 32px;
  overflow: hidden;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.32);
}
.balance-glow { position: absolute; inset: -1px; background: linear-gradient(135deg, rgba(247,198,106,0.2), transparent 60%); filter: blur(30px); z-index: -1; }
.balance-inner { display: flex; justify-content: space-between; align-items: center; gap: 20px; flex-wrap: wrap; }
.balance-left { flex: 1; min-width: 260px; }
.balance-label { font-size: 14px; font-weight: 700; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.08em; margin-bottom: 10px; }
.balance-amount { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.coin-icon { flex-shrink: 0; }
.balance-num { font-size: 56px; font-weight: 900; color: var(--gold); font-family: 'Inter', 'Noto Sans SC', sans-serif; letter-spacing: -0.02em; line-height: 1; text-shadow: 0 0 30px rgba(247, 198, 106, 0.4); }
.balance-hint { font-size: 14px; color: var(--text-muted); }
.balance-cta .btn-lg { padding: 14px 32px; font-size: 16px; }

/* 充值包 */
.recharge-section { background: var(--bg-soft); border-top: 1px solid var(--border); border-bottom: 1px solid var(--border); }
.recharge-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px; }
.recharge-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 28px 22px 24px;
  position: relative;
  text-align: center;
  transition: transform 0.3s, border-color 0.3s;
  display: flex; flex-direction: column;
}
.recharge-card:hover { transform: translateY(-4px); border-color: var(--gold-muted); }
.recharge-card.popular { border-color: var(--gold); box-shadow: 0 0 0 1px var(--gold-muted), 0 16px 48px rgba(212, 175, 104, 0.12); }
.pack-tag { position: absolute; top: -12px; left: 50%; transform: translateX(-50%); background: linear-gradient(135deg, var(--gold), #c9a24f); color: #1a1612; font-size: 11px; font-weight: 800; padding: 4px 14px; border-radius: 20px; letter-spacing: 0.03em; white-space: nowrap; }
.pack-credits { display: flex; align-items: center; justify-content: center; gap: 6px; margin-bottom: 8px; flex-wrap: wrap; }
.pack-coin { flex-shrink: 0; }
.credits-num { font-size: 40px; font-weight: 900; color: var(--gold); font-family: 'Inter', 'Noto Sans SC', sans-serif; line-height: 1; }
.credits-unit { font-size: 14px; color: var(--text-muted); margin-left: 2px; }
.pack-bonus { background: rgba(95, 229, 168, 0.15); color: #5fe5a8; font-size: 12px; font-weight: 700; padding: 3px 10px; border-radius: 12px; margin-left: 4px; }
.pack-price-row { display: flex; align-items: baseline; justify-content: center; gap: 8px; margin-bottom: 16px; }
.price-current { font-size: 28px; font-weight: 800; color: var(--text-main); }
.price-original { font-size: 14px; color: var(--text-muted); text-decoration: line-through; }
.pack-perks { list-style: none; text-align: left; margin-bottom: 20px; flex: 1; }
.pack-perks li { font-size: 13px; color: var(--text-soft); padding: 6px 0; border-bottom: 1px dashed rgba(255, 255, 255, 0.08); font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.pack-perks li:last-child { border-bottom: none; }
.pack-perks li::before { content: '\2713'; color: var(--gold); margin-right: 8px; font-weight: 700; }
.recharge-card .btn { width: 100%; }

/* 如何使用 */
.howto-section { padding: 80px 0; }
.howto-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 24px; }
.howto-card { background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius); padding: 36px 28px; text-align: center; transition: transform 0.3s; }
.howto-card:hover { transform: translateY(-4px); border-color: var(--gold-muted); }
.step-num {
  width: 56px; height: 56px; border-radius: 50%;
  background: linear-gradient(135deg, rgba(247, 198, 106, 0.2), rgba(200, 109, 38, 0.15));
  color: var(--gold);
  font-size: 24px; font-weight: 900;
  display: flex; align-items: center; justify-content: center;
  margin: 0 auto 20px;
  border: 1px solid rgba(247, 198, 106, 0.3);
}
.howto-card h3 { font-size: 20px; margin-bottom: 12px; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.howto-card p { color: var(--text-soft); font-size: 14px; line-height: 1.7; font-family: 'Noto Sans SC', 'Inter', sans-serif; }

/* 明细表 */
.history-table-wrap { overflow-x: auto; border-radius: var(--radius); border: 1px solid var(--border); background: var(--bg-card); }
.history-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.history-table th, .history-table td { padding: 14px 18px; border-bottom: 1px solid var(--border); text-align: left; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.history-table thead th { background: var(--bg-soft); font-weight: 700; color: var(--gold); font-size: 13px; text-transform: uppercase; letter-spacing: 0.04em; }
.history-table tbody tr:last-child td { border-bottom: none; }
.history-table tbody tr:hover { background: rgba(255, 255, 255, 0.02); }

.type-pill { font-size: 12px; font-weight: 700; padding: 4px 12px; border-radius: 20px; letter-spacing: 0.03em; }
.type-pill.type-purchase { background: rgba(247, 198, 106, 0.2); color: var(--gold); }
.type-pill.type-unlock { background: rgba(125, 46, 74, 0.2); color: #e88da8; }
.type-pill.type-bonus { background: rgba(95, 229, 168, 0.15); color: #5fe5a8; }
.type-pill.type-refund { background: rgba(255, 123, 123, 0.15); color: #ff8a8a; }

.rec-desc { font-weight: 500; }
.rec-time { color: var(--text-muted); font-size: 13px; }
.rec-delta { font-weight: 800; font-size: 16px; text-align: right; }
.rec-delta.plus { color: #5fe5a8; }
.rec-delta.minus { color: #ff7b7b; }

.cta-section { padding: 80px 0; text-align: center; }
.cta-inner h2 { font-size: clamp(26px, 4vw, 36px); margin-bottom: 12px; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.cta-inner p { color: var(--text-soft); margin-bottom: 28px; font-family: 'Noto Sans SC', 'Inter', sans-serif; }

@media (max-width: 900px) {
  .recharge-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 760px) {
  .balance-inner { flex-direction: column; text-align: center; }
  .howto-grid { grid-template-columns: 1fr; }
}
@media (max-width: 520px) {
  .recharge-grid { grid-template-columns: 1fr; }
}
</style>
