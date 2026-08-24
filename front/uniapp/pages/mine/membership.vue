<template>
  <view class="page mine-subpage">
    <view class="page-head">
      <view class="page-title">{{ t('membershipTitle') }}</view>
    </view>

    <!-- ① 等级状态卡 -->
    <view class="status-card" v-if="status" :style="{ borderColor: levelColor(status.current_level) }">
      <view class="status-badge" :style="{ background: levelColor(status.current_level) }">
        {{ levelDisplayName(status.current_level) }}
      </view>
      <view class="status-info">
        <view class="status-source" v-if="status.level_source">
          <text class="source-label">{{ t('membershipLevelSource') }}：</text>
          <text>{{ status.level_source === 'SPEND' ? t('membershipLevelSourceSpend') : t('membershipLevelSourcePurchase') }}</text>
        </view>
        <view v-if="status.purchase && status.purchase.expire_at" class="status-purchase">
          {{ t('membershipLevelSourcePurchase') }}：{{ formatDate(status.purchase.expire_at) }}
        </view>
      </view>
    </view>

    <!-- ② 消费进度条 -->
    <view class="progress-section" v-if="status && status.spend_progress">
      <view class="section-title">{{ t('membershipSpendProgress') }}</view>
      <view class="progress-bar-wrap">
        <view class="progress-bar">
          <view class="progress-fill" :style="{ width: progressPercent + '%', background: levelColor(status.current_level) }"></view>
        </view>
        <view class="progress-text">{{ formatMoney(status.spend_progress.total_spent_cents, status.spend_progress) }} / {{ formatMoney(status.spend_progress.next_threshold_cents, status.spend_progress) }}</view>
      </view>
      <view v-if="status.spend_progress.next_level" class="progress-hint">
        {{ t('membershipToNextLevel', { amount: formatMoney(status.spend_progress.remaining_cents, status.spend_progress), level: levelDisplayName(status.spend_progress.next_level) }) }}
      </view>
    </view>

    <!-- ③ 当前权益 -->
    <view class="benefits-section" v-if="status && status.benefits">
      <view class="section-title">{{ t('membershipBenefitsTitle') }}</view>
      <view class="benefit-list">
        <view class="benefit-item">
          <text class="benefit-icon">⭐</text>
          <text class="benefit-label">{{ t('membershipPointMultiplier') }}</text>
          <text class="benefit-value">{{ status.benefits.multiplier }}x</text>
        </view>
        <view class="benefit-item">
          <text class="benefit-icon">🎬</text>
          <text class="benefit-label">{{ t('membershipDailyFreeEpisodes') }}</text>
          <text class="benefit-value">{{ status.benefits.daily_free_episodes || 0 }}</text>
        </view>
        <view class="benefit-item">
          <text class="benefit-icon">💰</text>
          <text class="benefit-label">{{ t('membershipEpisodeDiscount') }}</text>
          <text class="benefit-value">{{ Math.round((status.benefits.episode_discount * 100)) || 0 }}%</text>
        </view>
        <view class="benefit-item">
          <text class="benefit-icon">💰</text>
          <text class="benefit-label">{{ t('membershipDramaDiscount') }}</text>
          <text class="benefit-value">{{ Math.round((status.benefits.drama_discount * 100)) || 0 }}%</text>
        </view>
        <view class="benefit-item" v-if="status.benefits.ad_free">
          <text class="benefit-icon">🚫</text>
          <text class="benefit-label">{{ t('membershipAdFree') }}</text>
          <text class="benefit-value">✓</text>
        </view>
        <view class="benefit-item" v-if="status.benefits.offline_cache">
          <text class="benefit-icon">📥</text>
          <text class="benefit-label">{{ t('membershipOfflineCache') }}</text>
          <text class="benefit-value">✓</text>
        </view>
        <view class="benefit-item" v-if="status.benefits.premium_support">
          <text class="benefit-icon">💬</text>
          <text class="benefit-label">{{ t('membershipPremiumSupport') }}</text>
          <text class="benefit-value">✓</text>
        </view>
        <view class="benefit-item" v-if="status.benefits.birthday_gift">
          <text class="benefit-icon">🎂</text>
          <text class="benefit-label">{{ t('membershipBirthdayGift') }}</text>
          <text class="benefit-value">✓</text>
        </view>
      </view>
    </view>

    <!-- ④ 每日免费额度 -->
    <view class="daily-section" v-if="status && status.daily_free">
      <view class="section-title">{{ t('membershipDailyFreeEpisodes') }}</view>
      <view class="daily-info">
        <text>{{ t('membershipDailyFreeUsed') }}：{{ status.daily_free.used || 0 }}</text>
        <text>{{ t('membershipDailyFreeRemaining') }}：{{ status.daily_free.remaining || 0 }}</text>
      </view>
      <view v-if="status.daily_free.reset_at" class="daily-reset">
        {{ t('membershipDailyFreeResetAt') }}：{{ formatDate(status.daily_free.reset_at) }}
      </view>
    </view>

    <!-- ⑤ 积分兑换会员 -->
    <view class="exchange-section">
      <view class="section-title">{{ t('membershipExchangeTitle') }}</view>
      <view class="exchange-form">
        <view class="form-row">
          <text class="form-label">{{ t('membershipCurrentLevel') }}</text>
          <view class="level-picker">
            <view v-for="lv in exchangeLevels" :key="lv"
              class="level-option" :class="{ active: exchangeLevel === lv }"
              :style="exchangeLevel === lv ? { background: levelColor(lv), color: '#111' } : {}"
              @click="exchangeLevel = lv">
              {{ levelDisplayName(lv) }}
            </view>
          </view>
        </view>
        <view class="form-row">
          <text class="form-label">{{ t('membershipExchangeDays') }}</text>
          <view class="days-picker">
            <view v-for="d in exchangeDaysOptions" :key="d"
              class="days-option" :class="{ active: exchangeDays === d }"
              @click="exchangeDays = d">
              {{ d }}{{ t('membershipExchangeDays') }}
            </view>
          </view>
        </view>
        <view class="exchange-price-row">
          <text class="price-label">{{ t('membershipExchangeBtn') }}</text>
          <text class="price-value">{{ exchangeCost }} {{ t('credits') }}</text>
          <text v-if="isRenewal" class="renewal-tag">{{ renewalDiscountText }}</text>
        </view>
        <button :disabled="exchanging" class="exchange-btn" @click="doExchange">
          {{ exchanging ? t('loading') : t('membershipExchangeBtn') }}
        </button>
      </view>
    </view>

    <!-- ⑥ 自动续费 -->
    <view class="auto-renewal-section" v-if="autoRenewal">
      <view class="section-title">{{ t('autoRenewalTitle') }}</view>
      <view class="renewal-card">
        <view class="renewal-status">
          <text class="renewal-dot" :class="{ active: autoRenewal.active }"></text>
          <text>{{ autoRenewal.active ? t('autoRenewalActive') : t('autoRenewalInactive') }}</text>
        </view>
        <view v-if="autoRenewal.active && autoRenewal.next_charge_at" class="renewal-next">
          {{ t('autoRenewalNextChargeAt') }}：{{ formatDate(autoRenewal.next_charge_at) }}
        </view>
        <view v-if="autoRenewal.active && autoRenewal.fail_count" class="renewal-fail">
          {{ t('autoRenewalFailCount') }}：{{ autoRenewal.fail_count }}
        </view>
        <button v-if="!autoRenewal.active" class="renewal-btn" @click="doSubscribe">
          {{ t('autoRenewalSubscribe') }}
        </button>
        <button v-else class="renewal-btn cancel" @click="doCancelRenewal">
          {{ t('autoRenewalCancel') }}
        </button>
      </view>
    </view>

    <!-- ⑦ 等级历史 -->
    <view class="history-section">
      <view class="section-title">{{ t('membershipHistoryTitle') }}</view>
      <view v-if="loadingHistory" class="loading">
        <text>{{ t('loading') }}...</text>
      </view>
      <view v-else-if="!history.length" class="empty">
        <text>{{ t('membershipNoHistory') }}</text>
      </view>
      <view v-else class="history-list">
        <view v-for="rec in history" :key="rec.id" class="history-item">
          <view class="history-info">
            <view class="history-level" :style="{ color: levelColor(rec.level || rec.from_level) }">
              {{ levelDisplayName(rec.level || rec.from_level) }}
              <text v-if="rec.to_level"> → {{ levelDisplayName(rec.to_level) }}</text>
            </view>
            <view class="history-time">{{ formatDate(rec.created_at || rec.createdAt) }}</view>
          </view>
          <view class="history-reason">{{ rec.remark || rec.reason || '-' }}</view>
        </view>
      </view>
    </view>
    <app-tab-bar />
  </view>
</template>

<script>
import api from '../../utils/api.js'
import { getLocale, t as translate } from '../../utils/i18n.js'
import { formatPrice, getCurrencyByLocale } from '../../utils/currencyConfig.js'
import { formatNumber, formatDate, levelColor, LEVEL_MAP } from '../../../shared/utils/format.js'

const EXCHANGE_PRICE = {
  SILVER: { 1: 200, 7: 1200 },
  GOLD: { 1: 350, 7: 2100 },
  DIAMOND: { 1: 600, 7: 3600 }
}
const RENEWAL_DISCOUNT = 0.9

export default {
  data() {
    return {
      status: null,
      history: [],
      autoRenewal: null,
      vipProducts: [],
      loading: false,
      loadingHistory: false,
      exchanging: false,
      exchangeLevel: 'SILVER',
      exchangeDays: 1,
      locale: getLocale()
    }
  },
  computed: {
    exchangeLevels() {
      return ['SILVER', 'GOLD', 'DIAMOND']
    },
    exchangeDaysOptions() {
      return [1, 7]
    },
    exchangeCost() {
      const backendPrices = this.status?.exchange_prices || this.status?.exchangePrices
      const priceTable = backendPrices || EXCHANGE_PRICE
      const price = (priceTable[this.exchangeLevel] || {})[this.exchangeDays] || 0
      const final = this.isRenewal ? Math.ceil(price * RENEWAL_DISCOUNT) : price
      return this.formatNumber(final)
    },
    renewalDiscountText() {
      if (this.locale === 'zh-CN' || this.locale === 'zh-TW') {
        return this.t('renewalDiscount', { percent: Math.round(RENEWAL_DISCOUNT * 10) })
      }
      return this.t('renewalDiscount', { percent: Math.round((1 - RENEWAL_DISCOUNT) * 100) })
    },
    isRenewal() {
      if (!this.status || !this.status.current_level) return false
      return this.status.current_level === this.exchangeLevel
    },
    progressPercent() {
      if (!this.status || !this.status.spend_progress) return 0
      const p = this.status.spend_progress
      if (!p.next_threshold_cents || p.next_threshold_cents <= 0) return 100
      return Math.min(100, Math.round((p.total_spent_cents / p.next_threshold_cents) * 100))
    }
  },
  onShow() {
    this.locale = getLocale()
    uni.setNavigationBarTitle({ title: this.t('membershipTitle') })
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      this.loadingHistory = true
      try {
        const results = await Promise.allSettled([
          this.loadStatus(),
          this.loadHistory(),
          this.loadAutoRenewal(),
          this.loadVipProducts()
        ])
      } finally {
        this.loading = false
        this.loadingHistory = false
      }
    },
    async loadStatus() {
      try {
        this.status = await api.membershipStatus()
      } catch (e) {
        console.warn('[membership] loadStatus failed:', e.message)
      }
    },
    async loadHistory() {
      try {
        const data = await api.membershipHistory()
        this.history = Array.isArray(data) ? data : (data?.records || data?.list || [])
      } catch (e) {
        this.history = []
      }
    },
    async loadAutoRenewal() {
      try {
        this.autoRenewal = await api.autoRenewalStatus()
      } catch (e) {
        this.autoRenewal = null
      }
    },
    async loadVipProducts() {
      try {
        const data = await api.pointProducts()
        this.vipProducts = (data || []).filter(item => {
          const cat = item.productCategory || item.product_category
          return cat === 'VIP'
        })
      } catch (e) {
        this.vipProducts = []
      }
    },
    async doExchange() {
      if (this.exchanging) return
      const result = await new Promise((resolve) => {
        uni.showModal({
          title: this.t('membershipExchangeTitle'),
          content: this.t('membershipExchangeConfirm', {
            points: this.exchangeCost,
            days: this.exchangeDays,
            level: this.levelDisplayName(this.exchangeLevel)
          }),
          success: (res) => resolve(res.confirm),
          fail: () => resolve(false)
        })
      })
      if (!result) return
      this.exchanging = true
      try {
        await api.membershipExchange({ level: this.exchangeLevel, days: this.exchangeDays })
        uni.showToast({ title: this.t('membershipExchangeSuccess'), icon: 'none' })
        await this.loadStatus()
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
      } finally {
        this.exchanging = false
      }
    },
    async doSubscribe() {
      if (!this.vipProducts || !this.vipProducts.length) {
        uni.showToast({ title: this.t('noPackages'), icon: 'none' })
        return
      }
      const tapIndex = await new Promise((resolve) => {
        uni.showActionSheet({
          itemList: this.vipProducts.map(p => p.name || `Product #${p.id}`),
          success: (res) => resolve(res.tapIndex),
          fail: () => resolve(-1)
        })
      })
      if (tapIndex < 0) return
      const product = this.vipProducts[tapIndex]
      const result = await new Promise((resolve) => {
        uni.showModal({
          title: this.t('autoRenewalSubscribe'),
          content: this.t('autoRenewalSubscribeConfirm', { name: product.name }),
          success: (res) => resolve(res.confirm),
          fail: () => resolve(false)
        })
      })
      if (!result) return
      try {
        await api.autoRenewalSubscribe({ productId: product.id, payChannel: 'WECHAT', payMethodToken: '' })
        uni.showToast({ title: this.t('autoRenewalSubscribe'), icon: 'none' })
        await this.loadAutoRenewal()
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
      }
    },
    async doCancelRenewal() {
      const result = await new Promise((resolve) => {
        uni.showModal({
          title: this.t('autoRenewalCancel'),
          content: this.t('autoRenewalCancel'),
          success: (res) => resolve(res.confirm),
          fail: () => resolve(false)
        })
      })
      if (!result) return
      try {
        await api.autoRenewalCancel()
        uni.showToast({ title: this.t('autoRenewalCancelSuccess'), icon: 'none' })
        this.autoRenewal = { active: false }
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
      }
    },

    // --- helpers ---
    levelDisplayName(level) {
      const entry = LEVEL_MAP[level] || LEVEL_MAP.NONE
      return this.t(entry.nameKey) || level
    },
    levelColor,
    formatNumber(value) {
      return formatNumber(value, this.locale)
    },
    formatDate(dateStr, showTime) {
      return formatDate(dateStr, this.locale, showTime)
    },
    formatMoney(cents, item) {
      const currencyCode = (item && item.currency) || getCurrencyByLocale(this.locale).code
      return formatPrice(Number(cents || 0), currencyCode)
    },
    t(key, params) {
      return translate(key, params, this.locale)
    }
  }
}
</script>

<style>
page,
.page {
  min-height: 100vh;
  background:
    repeating-linear-gradient(90deg, rgba(255, 255, 255, 0.035) 0 1rpx, transparent 1rpx 96rpx),
    linear-gradient(145deg, rgba(30, 116, 129, 0.2), transparent 38%),
    linear-gradient(225deg, rgba(123, 45, 56, 0.22), transparent 40%),
    #080a10;
  padding: 24rpx;
  box-sizing: border-box;
  color: #fff;
  font-family: -apple-system, BlinkMacSystemFont, "Inter", "SF Pro Display", "Segoe UI", sans-serif;
}

.page-head {
  padding: 18rpx 6rpx 30rpx;
}

.page-title {
  font-size: 34rpx;
  font-weight: 800;
  letter-spacing: -0.6rpx;
}

/* ① 等级状态卡 */
.status-card {
  background: linear-gradient(160deg, rgba(255,255,255,0.13), rgba(255,255,255,0.052)), rgba(12,15,24,0.72);
  border: 2rpx solid rgba(255,255,255,0.15);
  border-radius: 24rpx;
  padding: 28rpx 32rpx;
  margin-bottom: 32rpx;
  display: flex;
  align-items: center;
  gap: 24rpx;
  box-shadow: 0 24rpx 70rpx rgba(0, 0, 0, 0.28);
}

.status-badge {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  color: #111;
  font-weight: 800;
  font-size: 24rpx;
  letter-spacing: 0.4rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow:
    0 12rpx 30rpx rgba(0, 0, 0, 0.3),
    inset 0 2rpx 0 rgba(255, 255, 255, 0.4);
}

.status-info {
  flex: 1;
}

.status-source {
  font-size: 26rpx;
  color: rgba(255,255,255,0.7);
  margin-bottom: 6rpx;
}

.source-label {
  color: rgba(255,255,255,0.5);
}

.status-purchase {
  font-size: 24rpx;
  color: rgba(77,208,225,0.85);
  margin-top: 6rpx;
}

/* ② 进度条 */
.progress-section {
  margin-bottom: 32rpx;
}

.section-title {
  font-size: 32rpx;
  font-weight: 800;
  letter-spacing: -0.2rpx;
  margin-bottom: 20rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.section-title::before {
  content: '';
  width: 6rpx;
  height: 32rpx;
  border-radius: 3rpx;
  background: linear-gradient(180deg, #ffe0a1, #f3b84d);
  box-shadow: 0 0 10rpx rgba(247, 198, 106, 0.5);
}

.progress-bar-wrap {
  margin-bottom: 12rpx;
}

.progress-bar {
  height: 14rpx;
  background: rgba(255,255,255,0.08);
  border-radius: 8rpx;
  overflow: hidden;
  margin-bottom: 10rpx;
  box-shadow: inset 0 1rpx 2rpx rgba(0, 0, 0, 0.4);
}

.progress-fill {
  height: 100%;
  border-radius: 8rpx;
  transition: width 0.6s ease;
  box-shadow: 0 0 12rpx rgba(247, 198, 106, 0.5);
}

.progress-text {
  font-size: 24rpx;
  color: rgba(255,255,255,0.5);
  text-align: right;
}

.progress-hint {
  font-size: 24rpx;
  color: rgba(77,208,225,0.85);
  margin-top: 8rpx;
}

/* ③ 权益 */
.benefits-section {
  margin-bottom: 32rpx;
}

.benefit-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.benefit-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 24rpx;
  background:
    linear-gradient(160deg, rgba(255,255,255,0.1), rgba(255,255,255,0.035)),
    rgba(12,15,24,0.72);
  border: 1rpx solid rgba(255,255,255,0.12);
  border-radius: 18rpx;
  transition: border-color 0.2s ease;
}

.benefit-item:active {
  border-color: rgba(247, 198, 106, 0.4);
}

.benefit-icon {
  font-size: 34rpx;
  width: 48rpx;
  text-align: center;
}

.benefit-label {
  flex: 1;
  font-size: 28rpx;
  font-weight: 600;
  color: rgba(255,255,255,0.82);
}

.benefit-value {
  font-size: 28rpx;
  font-weight: 800;
  letter-spacing: 0.3rpx;
  color: #f7c66a;
}

/* ④ 每日免费 */
.daily-section {
  margin-bottom: 32rpx;
}

.daily-info {
  display: flex;
  gap: 32rpx;
  font-size: 26rpx;
  color: rgba(255,255,255,0.72);
  margin-bottom: 8rpx;
}

.daily-reset {
  font-size: 24rpx;
  color: rgba(255,255,255,0.45);
}

/* ⑤ 兑换 */
.exchange-section {
  margin-bottom: 32rpx;
}

.exchange-form {
  background:
    linear-gradient(160deg, rgba(255,255,255,0.13), rgba(255,255,255,0.052)),
    rgba(12,15,24,0.72);
  border: 1rpx solid rgba(255,255,255,0.15);
  border-radius: 24rpx;
  padding: 28rpx;
  box-shadow: 0 24rpx 70rpx rgba(0, 0, 0, 0.28);
}

.form-row {
  margin-bottom: 24rpx;
}

.form-label {
  font-size: 26rpx;
  font-weight: 700;
  letter-spacing: 0.3rpx;
  color: rgba(255,255,255,0.5);
  margin-bottom: 12rpx;
  display: block;
}

.level-picker {
  display: flex;
  gap: 16rpx;
}

.level-option {
  flex: 1;
  text-align: center;
  padding: 18rpx 0;
  border-radius: 16rpx;
  font-size: 26rpx;
  font-weight: 700;
  letter-spacing: 0.3rpx;
  background: rgba(255,255,255,0.08);
  border: 1rpx solid rgba(255,255,255,0.1);
  color: rgba(255,255,255,0.7);
  transition: all 0.22s ease;
}

.level-option:active {
  transform: scale(0.96);
}

.level-option.active {
  font-weight: 800;
  border-color: rgba(247, 198, 106, 0.5);
  box-shadow: 0 8rpx 20rpx rgba(0, 0, 0, 0.3);
}

.days-picker {
  display: flex;
  gap: 16rpx;
}

.days-option {
  flex: 1;
  text-align: center;
  padding: 18rpx 0;
  border-radius: 16rpx;
  font-size: 26rpx;
  font-weight: 700;
  background: rgba(255,255,255,0.08);
  border: 1rpx solid rgba(255,255,255,0.1);
  color: rgba(255,255,255,0.7);
  transition: all 0.22s ease;
}

.days-option:active {
  transform: scale(0.96);
}

.days-option.active {
  background: linear-gradient(135deg, rgba(247,198,106,0.22), rgba(247,198,106,0.08));
  color: #f7c66a;
  border: 1rpx solid rgba(247,198,106,0.5);
  box-shadow: 0 0 18rpx rgba(247, 198, 106, 0.2);
}

.exchange-price-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 24rpx;
  padding: 20rpx 24rpx;
  background: rgba(0, 0, 0, 0.22);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
  border-radius: 18rpx;
}

.price-label {
  font-size: 26rpx;
  color: rgba(255,255,255,0.55);
  flex: 1;
}

.price-value {
  font-size: 32rpx;
  font-weight: 800;
  letter-spacing: -0.2rpx;
  color: #f7c66a;
  text-shadow: 0 0 18rpx rgba(247, 198, 106, 0.35);
}

.renewal-tag {
  font-size: 20rpx;
  padding: 6rpx 14rpx;
  background: rgba(76,175,80,0.18);
  color: #5fd07a;
  border: 1rpx solid rgba(76, 175, 80, 0.32);
  border-radius: 999rpx;
  font-weight: 700;
  letter-spacing: 0.3rpx;
}

.exchange-btn {
  width: 100%;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  color: #11100d;
  border-radius: 999rpx;
  font-size: 30rpx;
  font-weight: 800;
  letter-spacing: 0.5rpx;
  padding: 20rpx;
  height: auto;
  line-height: 1.4;
  box-shadow:
    0 16rpx 38rpx rgba(247, 198, 106, 0.4),
    inset 0 2rpx 0 rgba(255, 255, 255, 0.5);
  transition: transform 0.18s ease;
}

.exchange-btn:active {
  transform: scale(0.97);
}

.exchange-btn[disabled] {
  opacity: 0.6;
}

/* ⑥ 自动续费 */
.auto-renewal-section {
  margin-bottom: 32rpx;
}

.renewal-card {
  background:
    linear-gradient(160deg, rgba(255,255,255,0.13), rgba(255,255,255,0.052)),
    rgba(12,15,24,0.72);
  border: 1rpx solid rgba(255,255,255,0.15);
  border-radius: 24rpx;
  padding: 28rpx;
  box-shadow: 0 24rpx 70rpx rgba(0, 0, 0, 0.28);
}

.renewal-status {
  display: flex;
  align-items: center;
  gap: 12rpx;
  font-size: 28rpx;
  font-weight: 700;
  margin-bottom: 12rpx;
}

.renewal-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: rgba(255,255,255,0.3);
  transition: background 0.3s ease, box-shadow 0.3s ease;
}

.renewal-dot.active {
  background: #5fd07a;
  box-shadow: 0 0 12rpx rgba(95, 208, 122, 0.7);
}

.renewal-next {
  font-size: 24rpx;
  color: rgba(255,255,255,0.55);
  margin-bottom: 6rpx;
}

.renewal-fail {
  font-size: 24rpx;
  color: #ff7a7c;
  margin-bottom: 12rpx;
}

.renewal-btn {
  width: 100%;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  color: #11100d;
  border-radius: 999rpx;
  font-size: 28rpx;
  font-weight: 800;
  letter-spacing: 0.4rpx;
  padding: 18rpx;
  height: auto;
  line-height: 1.4;
  margin-top: 16rpx;
  box-shadow:
    0 12rpx 30rpx rgba(247, 198, 106, 0.36),
    inset 0 2rpx 0 rgba(255, 255, 255, 0.5);
  transition: transform 0.18s ease;
}

.renewal-btn:active {
  transform: scale(0.97);
}

.renewal-btn.cancel {
  background: rgba(255,255,255,0.1);
  border: 1rpx solid rgba(255, 122, 124, 0.32);
  color: #ff7a7c;
  box-shadow: none;
}

/* ⑦ 历史 */
.history-section {
  margin-bottom: 32rpx;
}

.loading, .empty {
  padding: 56rpx 0;
  text-align: center;
  color: rgba(255,255,255,0.5);
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.history-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background:
    linear-gradient(160deg, rgba(255,255,255,0.1), rgba(255,255,255,0.035)),
    rgba(12,15,24,0.72);
  border: 1rpx solid rgba(255,255,255,0.12);
  border-radius: 20rpx;
  padding: 24rpx 28rpx;
  transition: border-color 0.2s ease;
}

.history-item:active {
  border-color: rgba(247, 198, 106, 0.4);
}

.history-info {
  flex: 1;
}

.history-level {
  font-size: 28rpx;
  font-weight: 700;
  margin-bottom: 6rpx;
}

.history-time {
  font-size: 22rpx;
  color: rgba(255,255,255,0.45);
}

.history-reason {
  font-size: 24rpx;
  color: rgba(255,255,255,0.5);
  max-width: 200rpx;
  text-align: right;
}
</style>
