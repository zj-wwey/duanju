<template>
  <view class="page">
    <view class="page-head">
      <view class="page-title">{{ t('vipMember') }}</view>
    </view>

    <!-- ① 会员等级状态卡 -->
    <view class="vip-status-card" v-if="membershipInfo">
      <view class="vip-badge" :style="{ background: levelGradient(membershipLevel) }">
        {{ levelShortName(membershipLevel) }}
      </view>
      <view class="vip-info">
        <view class="vip-name">
          {{ levelDisplayName(membershipLevel) }}
        </view>
        <view v-if="vipInfo && vipInfo.expireAt" class="vip-days">
          {{ t('vipDaysLeft', { count: vipInfo.daysLeft || 0 }) }}
        </view>
        <view class="vip-level-source" v-if="membershipInfo.levelSource || membershipInfo.level_source">
          {{ membershipInfo.levelSource || membershipInfo.level_source }}
        </view>
      </view>
    </view>

    <!-- ② 备选：旧 VIP 状态（兼容） -->
    <view class="vip-status-card" v-else-if="vipInfo">
      <view class="vip-badge">{{ t('vipStatus') }}</view>
      <view class="vip-info">
        <view class="vip-name">
          {{ vipInfo.isActive ? t('vipActive') : t('vipExpired') }}
        </view>
        <view v-if="vipInfo.expireAt" class="vip-days">
          {{ t('vipDaysLeft', { count: vipInfo.daysLeft || 0 }) }}
        </view>
      </view>
    </view>

    <!-- ③ 套餐列表 - 仅显示 VIP 类商品 -->
    <view class="section-title">{{ t('vipPackages') }}</view>

    <view v-if="loading" class="loading">
      <text>{{ t('loading') }}...</text>
    </view>

    <view v-else-if="!vipProducts.length" class="empty">
      <text class="empty-icon">💎</text>
      <text class="empty-text">{{ t('noPackages') }}</text>
    </view>

    <view v-else class="package-list">
      <view v-for="item in vipProducts" :key="item.id" class="package-card" :class="{ recommended: item.tagText }">
        <view class="package-badge" v-if="item.tagText">{{ item.tagText }}</view>
        <view class="package-name">{{ item.name }}</view>
        <view class="package-level" v-if="item.membershipLevel || item.membership_level">
          <text class="level-tag" :style="{ color: levelColor(item.membershipLevel || item.membership_level), borderColor: levelColor(item.membershipLevel || item.membership_level) }">
            {{ levelDisplayName(item.membershipLevel || item.membership_level) }}
          </text>
        </view>
        <view class="package-points">
          <text class="points-num">{{ productPoints(item) }}</text>
          <text class="points-unit">{{ t('credits') }}</text>
        </view>
        <view v-if="item.durationDays" class="package-duration">
          {{ t('durationDays', { count: item.durationDays }) }}
        </view>
        <view class="package-price">
          <text v-if="item.localOriginalPriceCents" class="price-original">
            {{ formatMoney(item.localOriginalPriceCents, item) }}
          </text>
          <text class="price-current">{{ formatMoney(item.localPriceCents || item.priceCents, item) }}</text>
        </view>
        <button :disabled="paying" class="subscribe-btn" @click="buy(item)">
          {{ t('subscribe') }}
        </button>
      </view>
    </view>

    <!-- ④ 底部导航入口 -->
    <view class="nav-links">
      <view class="nav-link" @click="goMembership">
        <text class="nav-link-icon">💎</text>
        <text class="nav-link-label">{{ t('membershipTitle') }}</text>
        <text class="nav-link-arrow">›</text>
      </view>
    </view>

    <!-- ⑤ 购买记录 -->
    <view class="section-title" v-if="vipRecords.length">{{ t('vipRecords') }}</view>
    <view v-if="vipRecords.length" class="record-list">
      <view v-for="rec in vipRecords" :key="rec.id" class="record-item">
        <view class="record-info">
          <view class="record-name">{{ rec.packageName || '-' }}</view>
          <view class="record-dates">{{ formatDate(rec.startAt) }} ~ {{ formatDate(rec.expireAt) }}</view>
        </view>
        <view class="record-status" :class="{ active: rec.status === 1 }">
          {{ rec.status === 1 ? t('vipActive') : t('vipExpired') }}
        </view>
      </view>
    </view>
    <app-tab-bar current="store" />
  </view>
</template>

<script>
import api from '../../utils/api.js'
import { getLocale, t as translate } from '../../utils/i18n.js'
import { formatPrice, getCurrencyByLocale } from '../../utils/currencyConfig.js'
import { productPoints, formatDate as formatDateUtil, levelColor, levelGradient, LEVEL_MAP } from '../../../shared/utils/format.js'

export default {
  data() {
    return {
      products: [],
      vipInfo: null,
      membershipInfo: null,
      vipRecords: [],
      loading: false,
      paying: false,
      locale: getLocale()
    }
  },
  computed: {
    membershipLevel() {
      if (this.membershipInfo) {
        return this.membershipInfo.currentLevel || this.membershipInfo.current_level || 'NONE'
      }
      return 'NONE'
    },
    vipProducts() {
      return this.products.filter(item => {
        const cat = item.productCategory || item.product_category
        return cat === 'VIP'
      })
    }
  },
  onShow() {
    this.locale = getLocale()
    uni.setNavigationBarTitle({ title: this.t('vipMember') })
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        this.membershipInfo = await api.membershipStatus()
      } catch (e) {
        this.membershipInfo = null
      }
      try {
        const data = await api.vipStatus()
        this.vipInfo = data
      } catch (e) {}
      try {
        this.products = await api.productsByLocale().catch(() => api.pointProducts())
      } catch (e) {
        this.products = []
      }
      try {
        this.vipRecords = await api.vipRecords()
      } catch (e) {
        this.vipRecords = []
      }
      this.loading = false
    },
    async buy(item) {
      if (!uni.getStorageSync('token')) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }
      if (this.paying) return
      this.paying = true
      try {
        const order = await api.createOrder(item.id, 'STRIPE')
        const checkout = await api.stripeCheckout(order.order_no || order.orderNo)
        const sessionUrl = checkout.session_url || checkout.sessionUrl
        if (sessionUrl) {
          uni.setStorageSync('pendingPayment', {
            orderNo: order.order_no || order.orderNo,
            payChannel: 'STRIPE',
            createdAt: Date.now()
          })
          // #ifdef H5
          window.location.href = sessionUrl
          // #endif
          // #ifndef H5
          uni.navigateTo({
            url: '/pages/webview/webview?url=' + encodeURIComponent(sessionUrl)
          })
          // #endif
        } else {
          throw new Error('支付链接获取失败')
        }
      } catch (err) {
        uni.showToast({ title: err.message || '支付发起失败', icon: 'none' })
      } finally {
        this.paying = false
      }
    },
    goMembership() {
      if (!uni.getStorageSync('token')) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }
      uni.navigateTo({ url: '/pages/mine/membership' })
    },
    productPoints,
    formatMoney(cents, item) {
      const currencyCode = (item && item.currency) || getCurrencyByLocale(this.locale).code
      return formatPrice(Number(cents || 0), currencyCode)
    },
    formatDate(str) {
      if (!str) return ''
      return formatDateUtil(str, this.locale, false)
    },
    levelDisplayName(level) {
      return this.t((LEVEL_MAP[level] || LEVEL_MAP.NONE).nameKey)
    },
    levelShortName(level) {
      return this.t((LEVEL_MAP[level] || LEVEL_MAP.NONE).shortKey)
    },
    levelColor,
    levelGradient,
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
  font-size: 44rpx;
  font-weight: 800;
  letter-spacing: -0.6rpx;
}

/* ① 会员等级状态卡 */
.vip-status-card {
  background:
    radial-gradient(circle at 20% 20%, rgba(247, 198, 106, 0.28), transparent 60%),
    linear-gradient(135deg, rgba(247, 198, 106, 0.2), rgba(77, 208, 225, 0.12)),
    rgba(12, 15, 24, 0.78);
  border: 1rpx solid rgba(247, 198, 106, 0.4);
  border-radius: 24rpx;
  padding: 28rpx 32rpx;
  margin-bottom: 32rpx;
  display: flex;
  align-items: center;
  gap: 24rpx;
  box-shadow:
    0 24rpx 70rpx rgba(0, 0, 0, 0.32),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.08);
}

.vip-badge {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  color: #11100d;
  font-weight: 800;
  font-size: 20rpx;
  letter-spacing: 0.4rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow:
    0 10rpx 24rpx rgba(247, 198, 106, 0.42),
    inset 0 2rpx 0 rgba(255, 255, 255, 0.5);
}

.vip-info {
  flex: 1;
}

.vip-name {
  font-size: 36rpx;
  font-weight: 800;
  letter-spacing: -0.4rpx;
}

.vip-days {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.68);
  margin-top: 8rpx;
}

.vip-level-source {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.45);
  margin-top: 4rpx;
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

.loading, .empty {
  padding: 80rpx 0;
  text-align: center;
  color: rgba(255, 255, 255, 0.5);
}

.empty-icon {
  font-size: 80rpx;
  display: block;
  margin-bottom: 20rpx;
}

/* ② 套餐列表 */
.package-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  margin-bottom: 40rpx;
}

.package-card {
  background: linear-gradient(160deg, rgba(255, 255, 255, 0.13), rgba(255, 255, 255, 0.052)), rgba(12, 15, 24, 0.72);
  border: 1rpx solid rgba(255, 255, 255, 0.15);
  border-radius: 28rpx;
  padding: 36rpx 32rpx;
  position: relative;
  overflow: hidden;
  box-shadow: 0 24rpx 70rpx rgba(0, 0, 0, 0.28);
  transition: transform 0.2s ease, border-color 0.2s ease;
}

.package-card:active {
  transform: scale(0.99);
}

.package-card.recommended {
  border-color: rgba(247, 198, 106, 0.55);
  background:
    radial-gradient(circle at 90% 0%, rgba(247, 198, 106, 0.2), transparent 55%),
    linear-gradient(160deg, rgba(255, 255, 255, 0.13), rgba(255, 255, 255, 0.052)), rgba(12, 15, 24, 0.78);
  box-shadow:
    0 24rpx 70rpx rgba(0, 0, 0, 0.32),
    0 0 30rpx rgba(247, 198, 106, 0.18);
}

.package-badge {
  position: absolute;
  top: 20rpx;
  right: 20rpx;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  color: #11100d;
  font-size: 20rpx;
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
  font-weight: 800;
  letter-spacing: 0.3rpx;
  box-shadow: 0 6rpx 14rpx rgba(247, 198, 106, 0.4);
}

.package-name {
  font-size: 36rpx;
  font-weight: 800;
  letter-spacing: -0.4rpx;
  margin-bottom: 8rpx;
}

.package-level {
  margin-bottom: 16rpx;
}

.level-tag {
  font-size: 22rpx;
  font-weight: 700;
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
  border: 1rpx solid;
  letter-spacing: 0.3rpx;
}

.package-points {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
  margin-bottom: 8rpx;
}

.points-num {
  font-size: 60rpx;
  font-weight: 900;
  letter-spacing: -0.6rpx;
  color: #f7c66a;
  text-shadow: 0 0 20rpx rgba(247, 198, 106, 0.4);
}

.points-unit {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.5);
}

.package-duration {
  font-size: 24rpx;
  color: rgba(77, 208, 225, 0.85);
  margin-bottom: 16rpx;
  font-weight: 600;
}

.package-price {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
  margin-bottom: 24rpx;
}

.price-original {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.4);
  text-decoration: line-through;
}

.price-current {
  font-size: 44rpx;
  font-weight: 800;
  letter-spacing: -0.4rpx;
  color: #f7c66a;
}

.subscribe-btn {
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

.subscribe-btn:active {
  transform: scale(0.97);
}

.subscribe-btn[disabled] {
  opacity: 0.6;
}

/* ③ 底部导航 */
.nav-links {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  margin-bottom: 32rpx;
}

.nav-link {
  display: flex;
  align-items: center;
  background: linear-gradient(160deg, rgba(255, 255, 255, 0.13), rgba(255, 255, 255, 0.052)), rgba(12, 15, 24, 0.72);
  border: 1rpx solid rgba(255, 255, 255, 0.15);
  border-radius: 20rpx;
  padding: 24rpx 28rpx;
  transition: transform 0.2s ease, border-color 0.2s ease;
}

.nav-link:active {
  transform: scale(0.99);
  border-color: rgba(247, 198, 106, 0.4);
}

.nav-link-icon {
  font-size: 36rpx;
  margin-right: 16rpx;
}

.nav-link-label {
  flex: 1;
  font-size: 28rpx;
  font-weight: 700;
}

.nav-link-arrow {
  color: rgba(255, 255, 255, 0.4);
  font-size: 32rpx;
}

/* ④ 购买记录 */
.record-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.record-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background:
    linear-gradient(160deg, rgba(255, 255, 255, 0.1), rgba(255, 255, 255, 0.035)),
    rgba(12, 15, 24, 0.72);
  border: 1rpx solid rgba(255, 255, 255, 0.12);
  border-radius: 20rpx;
  padding: 24rpx 28rpx;
}

.record-info {
  flex: 1;
}

.record-name {
  font-size: 28rpx;
  font-weight: 700;
  margin-bottom: 6rpx;
}

.record-dates {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.45);
}

.record-status {
  font-size: 22rpx;
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.6);
  font-weight: 700;
  letter-spacing: 0.3rpx;
}

.record-status.active {
  background: rgba(247, 198, 106, 0.22);
  color: #f7c66a;
  border: 1rpx solid rgba(247, 198, 106, 0.35);
}
</style>
