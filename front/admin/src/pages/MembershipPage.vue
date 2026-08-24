<template>
  <section class="stream-page membership-page">
    <button class="back-link back-button" type="button" @click="router.back()">← {{ t('common.back') }}</button>

    <div class="membership-header">
      <p class="stream-kicker">Membership</p>
      <h1>{{ t('membership.title') }}</h1>
    </div>

    <!-- 等级状态卡 -->
    <div class="membership-level-card" v-if="status">
      <div class="level-badge" :style="{ color: status.badge?.color }">
        <span class="level-icon">👑</span>
        <span class="level-name">{{ levelDisplayName(status.current_level) }}</span>
      </div>
      <div class="level-source">
        {{ t('membership.levelSource') }}：
        {{ status.level_source === 'PURCHASE' ? t('membership.levelSourcePurchase') : t('membership.levelSourceSpend') }}
      </div>
      <div class="level-stats">
        <div class="level-stat">
          <span class="stat-value">{{ status.benefits?.multiplier }}x</span>
          <span class="stat-label">{{ t('membership.pointMultiplier') }}</span>
        </div>
        <div class="level-stat">
          <span class="stat-value">{{ status.benefits?.daily_free }}</span>
          <span class="stat-label">{{ t('membership.dailyFreeEpisodes') }}</span>
        </div>
        <div class="level-stat" v-if="status.purchase?.days_remaining > 0">
          <span class="stat-value">{{ status.purchase.days_remaining }}</span>
          <span class="stat-label">{{ t('vip.daysLeft', { count: status.purchase.days_remaining }) }}</span>
        </div>
      </div>
      <div class="level-glow"></div>
    </div>
    <div v-else-if="loading" class="stream-loading">{{ t('common.loading') }}</div>

    <!-- 消费进度条 -->
    <section class="membership-progress" v-if="status?.spend_progress">
      <h2 class="section-title">{{ t('membership.spendProgress') }}</h2>
      <div class="progress-card">
        <div class="progress-info">
          <span>{{ formatMoney(status.spend_progress.total_spent_cents, currency, locale) }}</span>
          <span v-if="status.spend_progress.next_level" class="progress-next">
            {{ t('membership.toNextLevel', {
              amount: formatMoney(status.spend_progress.remaining_cents, currency, locale),
              level: levelDisplayName(status.spend_progress.next_level)
            }) }}
          </span>
          <span v-else class="progress-max">{{ t('membership.maxLevelReached') }}</span>
        </div>
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: status.spend_progress.progress_percent + '%' }"></div>
        </div>
      </div>
    </section>

    <!-- 当前权益 -->
    <section class="membership-benefits" v-if="status?.benefits">
      <h2 class="section-title">{{ t('vip.privileges') }}</h2>
      <div class="benefit-grid">
        <div class="benefit-item">
          <span class="benefit-icon">🎬</span>
          <span class="benefit-text">{{ t('membership.dailyFreeEpisodes') }}：{{ status.benefits.daily_free }}</span>
        </div>
        <div class="benefit-item">
          <span class="benefit-icon">⭐</span>
          <span class="benefit-text">{{ t('membership.pointMultiplier') }}：{{ status.benefits.multiplier }}x</span>
        </div>
        <div class="benefit-item">
          <span class="benefit-icon">🏷️</span>
          <span class="benefit-text">{{ t('membership.episodeDiscount') }}：{{ Math.round(status.benefits.episode_discount * 100) }}%</span>
        </div>
        <div class="benefit-item">
          <span class="benefit-icon">📦</span>
          <span class="benefit-text">{{ t('membership.dramaDiscount') }}：{{ Math.round(status.benefits.drama_discount * 100) }}%</span>
        </div>
        <div class="benefit-item" v-if="status.benefits.ad_free !== 'NONE'">
          <span class="benefit-icon">🚫</span>
          <span class="benefit-text">{{ t('membership.adFree') }}：{{ status.benefits.ad_free === 'FULL' ? t('membership.adFreeFull') : t('membership.adFreePartial') }}</span>
        </div>
        <div class="benefit-item" v-if="status.benefits.offline_cache > 0">
          <span class="benefit-icon">📥</span>
          <span class="benefit-text">{{ t('membership.offlineCache') }}：{{ status.benefits.offline_cache === -1 ? t('membership.unlimited') : status.benefits.offline_cache + ' ' + t('membership.daysUnit') }}</span>
        </div>
        <div class="benefit-item" v-if="status.benefits.premium_support">
          <span class="benefit-icon">💬</span>
          <span class="benefit-text">{{ t('membership.premiumSupport') }}</span>
        </div>
        <div class="benefit-item" v-if="status.benefits.birthday_gift > 0">
          <span class="benefit-icon">🎂</span>
          <span class="benefit-text">{{ t('membership.birthdayGift') }}：{{ status.benefits.birthday_gift }} {{ t('common.points') }}</span>
        </div>
        <div class="benefit-item" v-if="status.benefits.danmu_style?.glow">
          <span class="benefit-icon">✨</span>
          <span class="benefit-text">{{ t('membership.benefitDanmuStyle') }}</span>
        </div>
      </div>
    </section>

    <!-- 每日免费额度 -->
    <section class="membership-daily-free" v-if="status?.daily_free">
      <h2 class="section-title">{{ t('membership.dailyFreeEpisodes') }}</h2>
      <div class="daily-free-card">
        <div class="daily-free-stat">
          <span class="daily-label">{{ t('membership.dailyFreeUsed') }}</span>
          <span class="daily-value">{{ status.daily_free.used }}</span>
        </div>
        <div class="daily-free-stat">
          <span class="daily-label">{{ t('membership.dailyFreeRemaining') }}</span>
          <span class="daily-value">{{ status.daily_free.remaining }}</span>
        </div>
        <div class="daily-free-stat">
          <span class="daily-label">{{ t('membership.dailyFreeResetAt') }}</span>
          <span class="daily-value">{{ formatDate(status.daily_free.reset_at, locale) }}</span>
        </div>
      </div>
    </section>

    <!-- 等级权益对比表 -->
    <section class="membership-compare">
      <h2 class="section-title">{{ t('membership.benefitsTitle') }}</h2>
      <div v-if="loadingBenefits" class="stream-loading">{{ t('common.loading') }}</div>
      <div v-else-if="benefits" class="compare-table-wrapper">
        <table class="compare-table">
          <thead>
            <tr>
              <th>{{ t('vip.privileges') }}</th>
              <th>{{ t('membership.levelNone') }}</th>
              <th>{{ t('membership.levelSilver') }}</th>
              <th>{{ t('membership.levelGold') }}</th>
              <th :class="{ active: status?.current_level === 'DIAMOND' }">{{ t('membership.levelDiamond') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>{{ t('membership.dailyFreeEpisodes') }}</td>
              <td>{{ benefits.NONE?.daily_free }}</td>
              <td>{{ benefits.SILVER?.daily_free }}</td>
              <td>{{ benefits.GOLD?.daily_free }}</td>
              <td>{{ benefits.DIAMOND?.daily_free }}</td>
            </tr>
            <tr>
              <td>{{ t('membership.pointMultiplier') }}</td>
              <td>{{ benefits.NONE?.point_multiplier }}x</td>
              <td>{{ benefits.SILVER?.point_multiplier }}x</td>
              <td>{{ benefits.GOLD?.point_multiplier }}x</td>
              <td>{{ benefits.DIAMOND?.point_multiplier }}x</td>
            </tr>
            <tr>
              <td>{{ t('membership.episodeDiscount') }}</td>
              <td>{{ Math.round(benefits.NONE?.episode_discount * 100) }}%</td>
              <td>{{ Math.round(benefits.SILVER?.episode_discount * 100) }}%</td>
              <td>{{ Math.round(benefits.GOLD?.episode_discount * 100) }}%</td>
              <td>{{ Math.round(benefits.DIAMOND?.episode_discount * 100) }}%</td>
            </tr>
            <tr>
              <td>{{ t('membership.dramaDiscount') }}</td>
              <td>{{ Math.round(benefits.NONE?.drama_discount * 100) }}%</td>
              <td>{{ Math.round(benefits.SILVER?.drama_discount * 100) }}%</td>
              <td>{{ Math.round(benefits.GOLD?.drama_discount * 100) }}%</td>
              <td>{{ Math.round(benefits.DIAMOND?.drama_discount * 100) }}%</td>
            </tr>
            <tr>
              <td>{{ t('membership.adFree') }}</td>
              <td>✕</td>
              <td>✕</td>
              <td>{{ t('membership.adFreePartial') }}</td>
              <td>✓</td>
            </tr>
            <tr>
              <td>{{ t('membership.offlineCache') }}</td>
              <td>-</td>
              <td>{{ benefits.SILVER?.offline_cache }} {{ t('membership.daysUnit') }}</td>
              <td>{{ benefits.GOLD?.offline_cache }} {{ t('membership.daysUnit') }}</td>
              <td>{{ t('membership.unlimited') }}</td>
            </tr>
            <tr>
              <td>{{ t('membership.premiumSupport') }}</td>
              <td>✕</td>
              <td>✕</td>
              <td>✓</td>
              <td>✓</td>
            </tr>
            <tr>
              <td>{{ t('membership.birthdayGift') }}</td>
              <td>-</td>
              <td>{{ benefits.SILVER?.birthday_gift }} {{ t('common.points') }}</td>
              <td>{{ benefits.GOLD?.birthday_gift }} {{ t('common.points') }}</td>
              <td>{{ benefits.DIAMOND?.birthday_gift }} {{ t('common.points') }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- 积分兑换会员 -->
    <section class="membership-exchange" id="exchange">
      <h2 class="section-title">{{ t('membership.exchangeTitle') }}</h2>
      <div class="exchange-card">
        <div class="exchange-form">
          <div class="exchange-field">
            <label>{{ t('membership.currentLevel') }}</label>
            <select v-model="exchangeLevel" class="exchange-select">
              <option value="SILVER">{{ t('membership.levelSilver') }}</option>
              <option value="GOLD">{{ t('membership.levelGold') }}</option>
              <option value="DIAMOND">{{ t('membership.levelDiamond') }}</option>
            </select>
          </div>
          <div class="exchange-field">
            <label>{{ t('membership.exchangeDays') }}</label>
            <select v-model="exchangeDays" class="exchange-select">
              <option :value="1">1 {{ t('membership.daysUnit') }}</option>
              <option :value="7">7 {{ t('membership.daysUnit') }}</option>
            </select>
          </div>
        </div>
        <div class="exchange-preview" v-if="exchangePrice > 0">
          <span class="exchange-price-label">{{ t('recharge.points') }}</span>
          <span class="exchange-price-value">{{ formatNumber(exchangePrice, locale) }}</span>
          <span v-if="isRenewal" class="exchange-renewal-hint">{{ t('membership.renewalDiscount') }}</span>
        </div>
        <button
          class="exchange-submit-btn"
          :disabled="exchanging"
          @click="submitExchange"
        >
          {{ exchanging ? t('common.processing') : t('membership.exchangeBtn') }}
        </button>
      </div>
    </section>

    <!-- 自动续费 -->
    <section class="membership-autorenewal">
      <h2 class="section-title">{{ t('autoRenewal.title') }}</h2>
      <div class="autorenewal-card">
        <div v-if="autoRenewal?.active" class="autorenewal-active">
          <div class="autorenewal-info">
            <span class="autorenewal-status-badge active">{{ t('autoRenewal.active') }}</span>
            <div class="autorenewal-detail">
              <span>{{ t('autoRenewal.nextChargeAt') }}：{{ formatDate(autoRenewal.next_charge_at, locale, true) }}</span>
              <span v-if="autoRenewal.fail_count > 0">{{ t('autoRenewal.failCount') }}：{{ autoRenewal.fail_count }}</span>
            </div>
          </div>
          <button class="autorenewal-cancel-btn" @click="cancelAutoRenewalFn">
            {{ t('autoRenewal.cancel') }}
          </button>
        </div>
        <div v-else class="autorenewal-inactive">
          <span>{{ t('autoRenewal.inactive') }}</span>
          <button class="autorenewal-subscribe-btn" @click="goToVipPage">
            {{ t('autoRenewal.subscribe') }}
          </button>
        </div>
      </div>
    </section>

    <!-- 等级变更历史 -->
    <section class="membership-history">
      <h2 class="section-title">{{ t('membership.historyTitle') }}</h2>
      <div v-if="loadingHistory" class="stream-loading">{{ t('common.loading') }}</div>
      <div v-else-if="history.length" class="history-list">
        <article v-for="item in history" :key="item.id" class="history-card">
          <div class="history-info">
            <div class="history-product">{{ field(item, 'product_name') || field(item, 'productName') || t('vip.vipMember') }}</div>
            <div class="history-meta">
              <span class="history-type">{{ field(item, 'vip_type') || field(item, 'vipType') }}</span>
              <span class="history-time">{{ formatDate(item.created_at, locale, true) }}</span>
            </div>
          </div>
          <div class="history-status" :class="'status-' + item.status">
            {{ item.status === 1 ? t('vip.active') : t('vip.expired') }}
          </div>
        </article>
      </div>
      <div v-else class="stream-empty">{{ t('membership.noHistory') }}</div>
    </section>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useStreamI18n } from '../locales/streamI18n.js'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api.js'
import { useDramaStore } from '../user/store.js'
import { field, formatNumber, formatMoney, formatDate } from '../utils/helpers.js'

const router = useRouter()
const store = useDramaStore()
const { t, locale } = useStreamI18n()

const status = ref(null)
const benefits = ref(null)
const history = ref([])
const loading = ref(false)
const loadingBenefits = ref(false)
const loadingHistory = ref(false)
const exchanging = ref(false)
const autoRenewal = ref(null)

const exchangeLevel = ref('SILVER')
const exchangeDays = ref(1)

const EXCHANGE_PRICES = {
  SILVER: { 1: 200, 7: 1200 },
  GOLD: { 1: 350, 7: 2100 },
  DIAMOND: { 1: 600, 7: 3600 }
}

const exchangePrice = computed(() => {
  const prices = status.value?.exchange_prices || EXCHANGE_PRICES
  const base = (prices[exchangeLevel.value] || {})[exchangeDays.value] || 0
  if (isRenewal.value) return Math.floor(base * 0.9)
  return base
})

const isRenewal = computed(() => {
  return status.value?.current_level === exchangeLevel.value
})

const currency = computed(() => status.value?.spend_progress?.currency || status.value?.currency || 'USD')

function levelDisplayName(level) {
  const map = {
    NONE: t('membership.levelNone'),
    SILVER: t('membership.levelSilver'),
    GOLD: t('membership.levelGold'),
    DIAMOND: t('membership.levelDiamond')
  }
  return map[level] || level
}

async function loadData() {
  loading.value = true
  loadingBenefits.value = true
  loadingHistory.value = true
  try {
    await Promise.allSettled([
      loadStatus(),
      loadBenefits(),
      loadHistory(),
      loadAutoRenewal()
    ])
  } finally {
    loading.value = false
    loadingBenefits.value = false
    loadingHistory.value = false
  }
}

async function loadStatus() {
  try {
    status.value = await api.membershipStatus()
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function loadBenefits() {
  try {
    benefits.value = await api.membershipBenefits()
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function loadHistory() {
  try {
    const data = await api.membershipHistory()
    history.value = Array.isArray(data) ? data : data?.records || data?.list || []
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function loadAutoRenewal() {
  try {
    autoRenewal.value = await api.autoRenewalStatus()
  } catch (e) {
    console.warn('加载自动续费状态失败:', e)
  }
}

async function submitExchange() {
  if (exchanging.value) return
  try {
    await ElMessageBox.confirm(
      t('membership.exchangeConfirm', {
        points: exchangePrice.value,
        days: exchangeDays.value,
        level: levelDisplayName(exchangeLevel.value)
      }),
      t('membership.exchangeTitle'),
      { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'warning' }
    )
  } catch {
    return
  }
  exchanging.value = true
  try {
    await api.membershipExchange({ level: exchangeLevel.value, days: exchangeDays.value })
    ElMessage.success(t('membership.exchangeSuccess'))
    await loadStatus()
    store.loadProfile?.()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    exchanging.value = false
  }
}

async function cancelAutoRenewalFn() {
  try {
    await ElMessageBox.confirm(
      t('membership.cancelAutoRenewalConfirm'),
      t('autoRenewal.cancel'),
      { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'warning' }
    )
    await api.autoRenewalCancel()
    ElMessage.success(t('autoRenewal.cancelSuccess'))
    autoRenewal.value = { active: false }
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') {
      ElMessage.error(err.message)
    }
  }
}

function goToVipPage() {
  router.push('/vip')
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.membership-header {
  margin-bottom: 24px;
}

/* 等级状态卡 */
.membership-level-card {
  position: relative;
  background: linear-gradient(135deg, rgba(212, 175, 104, 0.15), rgba(212, 175, 104, 0.05));
  border: 1px solid rgba(212, 175, 104, 0.3);
  border-radius: 16px;
  padding: 24px;
  margin-bottom: 24px;
  overflow: hidden;
}
.level-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 8px;
}
.level-icon {
  font-size: 32px;
}
.level-source {
  color: #999;
  font-size: 13px;
  margin-bottom: 16px;
}
.level-stats {
  display: flex;
  gap: 32px;
}
.level-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: #d4af68;
}
.stat-label {
  font-size: 12px;
  color: #999;
}
.level-glow {
  position: absolute;
  top: -50%;
  right: -20%;
  width: 200px;
  height: 200px;
  background: radial-gradient(circle, rgba(212, 175, 104, 0.1), transparent);
  pointer-events: none;
}

/* 消费进度条 */
.progress-card {
  background: rgba(255, 255, 255, 0.05);
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 24px;
}
.progress-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-size: 14px;
}
.progress-next {
  color: #d4af68;
  font-size: 13px;
}
.progress-max {
  color: #4caf50;
  font-size: 13px;
}
.progress-bar {
  height: 8px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 4px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #d4af68, #f0c87a);
  border-radius: 4px;
  transition: width 0.5s ease;
}

/* 权益 */
.benefit-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
  margin-bottom: 24px;
}
.benefit-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 10px;
  font-size: 14px;
}
.benefit-icon {
  font-size: 20px;
}
.benefit-text {
  color: #e0d5c2;
}

/* 每日免费 */
.daily-free-card {
  display: flex;
  gap: 24px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 12px;
  margin-bottom: 24px;
}
.daily-free-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.daily-label {
  font-size: 12px;
  color: #999;
}
.daily-value {
  font-size: 18px;
  font-weight: 600;
  color: #d4af68;
}

/* 等级对比表 */
.compare-table-wrapper {
  overflow-x: auto;
  margin-bottom: 24px;
}
.compare-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.compare-table th,
.compare-table td {
  padding: 10px 12px;
  text-align: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}
.compare-table th {
  background: rgba(255, 255, 255, 0.05);
  color: #d4af68;
  font-weight: 600;
  white-space: nowrap;
}
.compare-table th.active {
  background: rgba(212, 175, 104, 0.15);
}
.compare-table td:first-child {
  text-align: left;
  color: #999;
  white-space: nowrap;
}
.compare-table td {
  color: #e0d5c2;
}

/* 积分兑换 */
.exchange-card {
  background: rgba(255, 255, 255, 0.05);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 24px;
}
.exchange-form {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}
.exchange-field {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.exchange-field label {
  font-size: 13px;
  color: #999;
}
.exchange-select {
  padding: 10px 14px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  color: #e0d5c2;
  font-size: 14px;
  cursor: pointer;
}
.exchange-preview {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding: 12px;
  background: rgba(212, 175, 104, 0.1);
  border-radius: 8px;
}
.exchange-price-label {
  color: #999;
  font-size: 14px;
}
.exchange-price-value {
  font-size: 24px;
  font-weight: 700;
  color: #d4af68;
}
.exchange-renewal-hint {
  font-size: 12px;
  color: #4caf50;
}
.exchange-submit-btn {
  width: 100%;
  padding: 14px;
  background: linear-gradient(135deg, #d4af68, #c09a4e);
  border: none;
  border-radius: 10px;
  color: #1a1a2e;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s;
}
.exchange-submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 自动续费 */
.autorenewal-card {
  background: rgba(255, 255, 255, 0.05);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 24px;
}
.autorenewal-active {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.autorenewal-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.autorenewal-status-badge {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
}
.autorenewal-status-badge.active {
  background: rgba(76, 175, 80, 0.2);
  color: #4caf50;
}
.autorenewal-detail {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: #999;
}
.autorenewal-cancel-btn {
  padding: 8px 20px;
  background: rgba(244, 67, 54, 0.15);
  border: 1px solid rgba(244, 67, 54, 0.3);
  border-radius: 8px;
  color: #f44336;
  font-size: 13px;
  cursor: pointer;
}
.autorenewal-inactive {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #999;
  font-size: 14px;
}
.autorenewal-subscribe-btn {
  padding: 8px 20px;
  background: rgba(212, 175, 104, 0.15);
  border: 1px solid rgba(212, 175, 104, 0.3);
  border-radius: 8px;
  color: #d4af68;
  font-size: 13px;
  cursor: pointer;
}

/* 等级历史 */
.history-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 24px;
}
.history-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 10px;
}
.history-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.history-product {
  font-size: 14px;
  color: #e0d5c2;
}
.history-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #999;
}
.history-status {
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
}
.history-status.status-1 {
  background: rgba(76, 175, 80, 0.15);
  color: #4caf50;
}
.history-status.status-0 {
  background: rgba(255, 255, 255, 0.08);
  color: #999;
}
</style>