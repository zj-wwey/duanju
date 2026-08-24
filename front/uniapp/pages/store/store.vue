<template>
  <view class="page">
    <view class="store-head">
      <view>
        <view class="eyebrow">{{ t('storeTab') }}</view>
        <view class="title">{{ t('storeCenter') }}</view>
      </view>
      <view class="balance-pill" @click="ensureLogin">{{ points }} {{ t('credits') }}</view>
    </view>

    <view class="status-card">
      <view>
        <view class="status-label">{{ t('creditsAvailable', { count: points }) }}</view>
        <view class="status-title">{{ membershipTitle }}</view>
      </view>
      <button class="status-btn" @click="activeTab = 'vip'">{{ t('vipMember') }}</button>
    </view>

    <scroll-view scroll-x class="tabs">
      <view class="tab" :class="{ active: activeTab === 'recharge' }" @click="activeTab = 'recharge'">{{ t('rechargeCredits') }}</view>
      <view class="tab" :class="{ active: activeTab === 'vip' }" @click="activeTab = 'vip'">{{ t('vipMember') }}</view>
      <view class="tab" :class="{ active: activeTab === 'shop' }" @click="activeTab = 'shop'">{{ t('shopTitle') }}</view>
      <view class="tab" :class="{ active: activeTab === 'orders' }" @click="activeTab = 'orders'">{{ t('orders') }}</view>
    </scroll-view>

    <view v-if="loading" class="state">{{ t('loading') }}</view>

    <view v-else-if="activeTab === 'recharge'" class="panel">
      <view class="pay-methods">
        <view class="method" :class="{ active: payChannel === 'STRIPE' }" @click="payChannel = 'STRIPE'">Stripe</view>
        <view class="method" :class="{ active: payChannel === 'PAYPAL' }" @click="payChannel = 'PAYPAL'">PayPal</view>
      </view>
      <view v-if="!rechargeProducts.length" class="state small">{{ t('noPackages') }}</view>
      <view v-else class="product-grid">
        <view v-for="item in rechargeProducts" :key="item.id" class="product-card">
          <view class="product-name">{{ item.name }}</view>
          <view class="product-points">{{ productPoints(item) }} {{ t('credits') }}</view>
          <view v-if="bonusPoints(item)" class="product-bonus">+{{ bonusPoints(item) }} {{ t('credits') }}</view>
          <view class="product-price">{{ formatMoney(item.localPriceCents || item.priceCents || item.price_cents, item) }}</view>
          <button class="buy-btn" :disabled="paying" @click="buyProduct(item)">{{ t('confirmRecharge') }}</button>
        </view>
      </view>
    </view>

    <view v-else-if="activeTab === 'vip'" class="panel">
      <view class="vip-card">
        <view class="vip-badge">{{ levelShortName(membershipLevel) }}</view>
        <view>
          <view class="vip-title">{{ membershipTitle }}</view>
          <view class="vip-desc">{{ vipInfo && (vipInfo.expireAt || vipInfo.expire_at) ? t('vipActive') : t('vipStatus') }}</view>
        </view>
      </view>
      <view v-if="!vipProducts.length" class="state small">{{ t('noPackages') }}</view>
      <view v-else class="product-list">
        <view v-for="item in vipProducts" :key="item.id" class="wide-card">
          <view class="wide-main">
            <view class="product-name">{{ item.name }}</view>
            <view class="product-points">{{ productPoints(item) }} {{ t('credits') }}</view>
            <view v-if="item.durationDays || item.duration_days" class="product-bonus">{{ t('durationDays', { count: item.durationDays || item.duration_days }) }}</view>
          </view>
          <view class="wide-side">
            <view class="product-price">{{ formatMoney(item.localPriceCents || item.priceCents || item.price_cents, item) }}</view>
            <button class="buy-btn compact" :disabled="paying" @click="buyProduct(item)">{{ t('subscribe') }}</button>
          </view>
        </view>
      </view>
      <view class="link-row" @click="go('/pages/mine/membership')">{{ t('membershipTitle') }} ›</view>
    </view>

    <view v-else-if="activeTab === 'shop'" class="panel">
      <view v-if="!shopItems.length" class="state small">{{ t('shopNoItems') }}</view>
      <view v-else class="product-list">
        <view v-for="item in shopItems" :key="item.id" class="wide-card">
          <view class="wide-main">
            <view class="product-name">{{ item.name || item.itemName }}</view>
            <view class="shop-desc">{{ item.description || '-' }}</view>
            <view class="product-points">{{ formatNumber(item.pointsCost || item.points_cost) }} {{ t('credits') }}</view>
          </view>
          <button class="buy-btn compact" :disabled="exchangingId === item.id || isOutOfStock(item)" @click="exchangeItem(item)">
            {{ isOutOfStock(item) ? t('shopItemOutOfStock') : t('shopExchange') }}
          </button>
        </view>
      </view>
      <view class="section-title">{{ t('shopRecords') }}</view>
      <view v-if="!shopRecords.length" class="state small">{{ t('shopNoRecords') }}</view>
      <view v-else class="order-list">
        <view v-for="record in shopRecords" :key="record.id" class="order-row">
          <view>
            <view class="order-title">{{ record.itemName || record.item_name || '-' }}</view>
            <view class="order-no">{{ formatDate(record.createdAt || record.created_at) }}</view>
          </view>
          <view class="order-status">{{ record.deliveryStatus || record.delivery_status || '-' }}</view>
        </view>
      </view>
    </view>

    <view v-else class="panel">
      <view v-if="!orders.length" class="state small">{{ t('noOrders') }}</view>
      <view v-else class="order-list">
        <view v-for="order in orders" :key="order.orderNo || order.order_no || order.id" class="order-row">
          <view>
            <view class="order-title">{{ order.productName || order.product_name || order.name || t('storeTab') }}</view>
            <view class="order-no">{{ order.orderNo || order.order_no }}</view>
          </view>
          <view class="order-status" :class="{ paid: order.status === 'PAID' }">{{ order.status || '-' }}</view>
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
import { notifyDataChanged, APP_DATA_EVENTS, requireLogin } from '../../utils/app-state.js'
import { productPoints, formatNumber, formatDate as formatDateUtil, LEVEL_MAP } from '../../../shared/utils/format.js'

export default {
  data() {
    return {
      activeTab: 'recharge',
      payChannel: 'STRIPE',
      points: 0,
      products: [],
      orders: [],
      shopItems: [],
      shopRecords: [],
      vipInfo: null,
      membershipInfo: null,
      loading: true,
      paying: false,
      exchangingId: null,
      pollingTimer: null,
      pollingAttempts: 0,
      locale: getLocale()
    }
  },
  computed: {
    rechargeProducts() {
      return this.products.filter(item => {
        const category = item.productCategory || item.product_category
        return !category || category === 'RECHARGE'
      })
    },
    vipProducts() {
      return this.products.filter(item => {
        const category = item.productCategory || item.product_category
        return category === 'VIP'
      })
    },
    membershipLevel() {
      return this.membershipInfo?.currentLevel || this.membershipInfo?.current_level || 'NONE'
    },
    membershipTitle() {
      const entry = LEVEL_MAP[this.membershipLevel] || LEVEL_MAP.NONE
      return this.t(entry.nameKey)
    }
  },
  onLoad(options) {
    if (options.tab) this.activeTab = options.tab
    this.handleReturn(options)
  },
  onShow() {
    this.locale = getLocale()
    this.load()
  },
  onUnload() {
    this.stopPolling()
  },
  methods: {
    async load() {
      this.loading = true
      const loggedIn = !!uni.getStorageSync('token')
      try {
        const [pointsData, products, orders, shopItems, shopRecords, vipInfo, membershipInfo] = await Promise.all([
          loggedIn ? api.points().catch(() => null) : Promise.resolve(null),
          api.productsByLocale().catch(() => api.pointProducts()).catch(() => []),
          loggedIn ? api.orders().catch(() => []) : Promise.resolve([]),
          loggedIn ? api.shopItems().catch(() => []) : Promise.resolve([]),
          loggedIn ? api.shopRecords().catch(() => []) : Promise.resolve([]),
          loggedIn ? api.vipStatus().catch(() => null) : Promise.resolve(null),
          loggedIn ? api.membershipStatus().catch(() => null) : Promise.resolve(null)
        ])
        this.points = Number(pointsData?.user?.points || pointsData?.points || 0)
        this.products = Array.isArray(products) ? products : []
        this.orders = Array.isArray(orders) ? orders.slice(0, 30) : []
        this.shopItems = Array.isArray(shopItems) ? shopItems : (shopItems?.records || shopItems?.list || [])
        this.shopRecords = Array.isArray(shopRecords) ? shopRecords : (shopRecords?.records || shopRecords?.list || [])
        this.vipInfo = vipInfo
        this.membershipInfo = membershipInfo
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
      } finally {
        this.loading = false
      }
    },
    async buyProduct(item) {
      if (!this.ensureLogin() || this.paying) return
      this.paying = true
      try {
        const order = await api.createOrder({ productId: item.id, payChannel: this.payChannel })
        const orderNo = order.orderNo || order.order_no
        if (!orderNo) throw new Error(this.t('orderCreateFailed'))
        const checkout = this.payChannel === 'PAYPAL'
          ? await api.paypalCheckout(orderNo)
          : await api.stripeCheckout(orderNo)
        const payUrl = checkout.sessionUrl || checkout.session_url || checkout.approveUrl || checkout.approve_url
        if (!payUrl) throw new Error(this.t('paymentLinkFailed'))
        uni.setStorageSync('pendingPayment', { orderNo, payChannel: this.payChannel, createdAt: Date.now() })
        notifyDataChanged(APP_DATA_EVENTS.order, { orderNo })
        this.openPayment(payUrl)
      } catch (err) {
        uni.showToast({ title: err.message || this.t('paymentFailed'), icon: 'none' })
      } finally {
        this.paying = false
      }
    },
    async exchangeItem(item) {
      if (!this.ensureLogin() || this.exchangingId || this.isOutOfStock(item)) return
      const confirmed = await new Promise(resolve => {
        uni.showModal({
          title: this.t('shopExchange'),
          content: this.t('shopExchangeConfirm', {
            points: this.formatNumber(item.pointsCost || item.points_cost),
            name: item.name || item.itemName
          }),
          success: res => resolve(res.confirm),
          fail: () => resolve(false)
        })
      })
      if (!confirmed) return
      this.exchangingId = item.id
      try {
        await api.shopExchange(item.id)
        notifyDataChanged(APP_DATA_EVENTS.shop, { itemId: item.id })
        notifyDataChanged(APP_DATA_EVENTS.points)
        uni.showToast({ title: this.t('shopExchangeSuccess'), icon: 'none' })
        await this.load()
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
      } finally {
        this.exchangingId = null
      }
    },
    openPayment(url) {
      // #ifdef H5
      window.location.href = url
      // #endif
      // #ifndef H5
      uni.navigateTo({ url: '/pages/webview/webview?url=' + encodeURIComponent(url) })
      // #endif
    },
    async handleReturn(options) {
      const pending = uni.getStorageSync('pendingPayment')
      const orderNo = options.orderNo || pending?.orderNo
      const paypalOrderId = options.token || options.paypalOrderId
      if (!orderNo) return
      if (paypalOrderId) api.paypalCapture(orderNo, paypalOrderId).catch(() => {})
      this.startPolling(orderNo)
    },
    startPolling(orderNo) {
      this.stopPolling()
      this.pollingAttempts = 0
      this.pollingTimer = setInterval(async () => {
        this.pollingAttempts += 1
        try {
          const orders = await api.orders()
          const target = (orders || []).find(item => (item.orderNo || item.order_no) === orderNo)
          if (target && target.status === 'PAID') {
            uni.removeStorageSync('pendingPayment')
            notifyDataChanged(APP_DATA_EVENTS.order, { orderNo, status: 'PAID' })
            notifyDataChanged(APP_DATA_EVENTS.points)
            notifyDataChanged(APP_DATA_EVENTS.membership)
            this.stopPolling()
            uni.showToast({ title: this.t('rechargeSuccess'), icon: 'none' })
            await this.load()
          }
        } catch (_) {}
        if (this.pollingAttempts >= 15) {
          this.stopPolling()
          this.load()
        }
      }, 4000)
    },
    stopPolling() {
      if (this.pollingTimer) {
        clearInterval(this.pollingTimer)
        this.pollingTimer = null
      }
    },
    ensureLogin() {
      return requireLogin()
    },
    go(url) {
      if (!this.ensureLogin()) return
      uni.navigateTo({ url })
    },
    isOutOfStock(item) {
      return Number(item.stock || 0) === 0
    },
    bonusPoints(item) {
      return Number(item.bonusPoints || item.bonus_points || 0)
    },
    productPoints,
    formatNumber,
    formatDate(str) {
      return str ? formatDateUtil(str, this.locale, false) : ''
    },
    formatMoney(cents, item) {
      return formatPrice(Number(cents || 0), (item && item.currency) || getCurrencyByLocale(this.locale).code)
    },
    levelShortName(level) {
      return this.t((LEVEL_MAP[level] || LEVEL_MAP.NONE).shortKey)
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
  padding: 40rpx 24rpx 0;
  box-sizing: border-box;
  color: #fff;
  background:
    radial-gradient(circle at 16% 0, rgba(247,198,106,0.32), transparent 30%),
    radial-gradient(circle at 90% 18%, rgba(77,208,225,0.18), transparent 36%),
    radial-gradient(circle at 50% 100%, rgba(255, 138, 31, 0.08), transparent 50%),
    #080a10;
  font-family: -apple-system, BlinkMacSystemFont, "Inter", "SF Pro Display", "Segoe UI", sans-serif;
}

.store-head,
.status-card,
.wide-card,
.order-row,
.vip-card {
  display: flex;
  align-items: center;
}

.store-head {
  justify-content: space-between;
  margin-bottom: 28rpx;
}

.eyebrow {
  display: flex;
  align-items: center;
  color: #f7c66a;
  font-size: 24rpx;
  font-weight: 900;
  letter-spacing: 4rpx;
  text-transform: uppercase;
}

.eyebrow::before {
  content: "";
  display: inline-block;
  width: 32rpx;
  height: 3rpx;
  margin-right: 14rpx;
  background: linear-gradient(90deg, transparent, #f7c66a);
  border-radius: 2rpx;
}

.title {
  margin-top: 8rpx;
  font-size: 52rpx;
  font-weight: 900;
  letter-spacing: -1.5rpx;
  background: linear-gradient(135deg, #ffffff 30%, rgba(255, 255, 255, 0.78) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.balance-pill,
.status-btn {
  padding: 16rpx 26rpx;
  border-radius: 999rpx;
  color: #11100d;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  font-size: 24rpx;
  font-weight: 900;
  letter-spacing: 0.5rpx;
  box-shadow:
    0 12rpx 28rpx rgba(247, 198, 106, 0.36),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
  transition: transform 0.18s ease;
}

.balance-pill:active,
.status-btn:active {
  transform: scale(0.95);
}

.status-card {
  position: relative;
  justify-content: space-between;
  padding: 38rpx;
  margin-bottom: 26rpx;
  border-radius: 32rpx;
  background: linear-gradient(145deg, rgba(247,198,106,0.22), rgba(255,255,255,0.08));
  border: 1rpx solid rgba(247,198,106,0.32);
  box-shadow:
    0 28rpx 70rpx rgba(0, 0, 0, 0.32),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(20rpx);
  overflow: hidden;
}

.status-card::before {
  content: "";
  position: absolute;
  top: -50rpx;
  right: -50rpx;
  width: 220rpx;
  height: 220rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(247, 198, 106, 0.16), transparent 70%);
  filter: blur(20rpx);
  pointer-events: none;
}

.status-label {
  color: rgba(255,255,255,0.62);
  font-size: 24rpx;
  font-weight: 600;
  letter-spacing: 0.5rpx;
}

.status-title {
  margin-top: 10rpx;
  font-size: 38rpx;
  font-weight: 900;
  letter-spacing: -0.5rpx;
}

.status-btn {
  height: 72rpx;
  line-height: 72rpx;
  padding: 0 28rpx;
}

.tabs {
  white-space: nowrap;
  margin-bottom: 26rpx;
}

.tab {
  display: inline-flex;
  align-items: center;
  height: 72rpx;
  margin-right: 14rpx;
  padding: 0 30rpx;
  border-radius: 999rpx;
  color: rgba(255,255,255,0.62);
  background: rgba(255,255,255,0.09);
  font-size: 25rpx;
  font-weight: 900;
  letter-spacing: 0.5rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.08);
  transition: all 0.2s ease;
}

.tab:active {
  transform: scale(0.95);
}

.tab.active,
.method.active {
  color: #11100d;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  border-color: transparent;
  box-shadow:
    0 12rpx 28rpx rgba(247, 198, 106, 0.36),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
}

.panel {
  min-height: 300rpx;
}

.pay-methods {
  display: flex;
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.method {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  text-align: center;
  border-radius: 999rpx;
  color: rgba(255,255,255,0.74);
  background: rgba(255,255,255,0.1);
  border: 1rpx solid rgba(255, 255, 255, 0.14);
  font-weight: 900;
  letter-spacing: 0.5rpx;
  transition: all 0.2s ease;
}

.method:active {
  transform: scale(0.97);
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 18rpx;
}

.product-card,
.wide-card,
.vip-card,
.order-row {
  border: 1rpx solid rgba(255,255,255,0.16);
  background: linear-gradient(160deg, rgba(255,255,255,0.14), rgba(255,255,255,0.055)), rgba(12,15,24,0.78);
  border-radius: 28rpx;
  box-shadow:
    0 22rpx 50rpx rgba(0, 0, 0, 0.32),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(20rpx);
  transition: transform 0.2s ease;
}

.product-card {
  padding: 30rpx;
}

.product-card:active,
.wide-card:active,
.vip-card:active,
.order-row:active {
  transform: scale(0.98);
}

.product-name {
  font-size: 30rpx;
  font-weight: 900;
  letter-spacing: 0.3rpx;
}

.product-points {
  margin-top: 18rpx;
  color: #f7c66a;
  font-size: 36rpx;
  font-weight: 900;
  letter-spacing: -0.5rpx;
}

.product-bonus,
.shop-desc,
.order-no,
.vip-desc {
  margin-top: 8rpx;
  color: rgba(255,255,255,0.56);
  font-size: 23rpx;
  font-weight: 600;
}

.product-price {
  margin-top: 18rpx;
  font-size: 30rpx;
  font-weight: 900;
  letter-spacing: -0.3rpx;
}

.buy-btn {
  width: 100%;
  height: 72rpx;
  line-height: 72rpx;
  margin-top: 20rpx;
  padding: 0;
  border-radius: 999rpx;
  color: #11100d;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  font-size: 25rpx;
  font-weight: 900;
  letter-spacing: 1rpx;
  box-shadow:
    0 14rpx 32rpx rgba(247, 198, 106, 0.32),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
  transition: transform 0.18s ease;
}

.buy-btn:active {
  transform: scale(0.97);
}

.buy-btn.compact {
  width: 170rpx;
  flex-shrink: 0;
}

.product-list,
.order-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.wide-card {
  justify-content: space-between;
  gap: 20rpx;
  padding: 30rpx;
}

.wide-main {
  flex: 1;
  min-width: 0;
}

.wide-side {
  width: 190rpx;
  flex-shrink: 0;
}

.vip-card {
  gap: 22rpx;
  padding: 30rpx;
  margin-bottom: 18rpx;
  position: relative;
  overflow: hidden;
}

.vip-card::before {
  content: "";
  position: absolute;
  top: -30rpx;
  right: -30rpx;
  width: 160rpx;
  height: 160rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(247, 198, 106, 0.14), transparent 70%);
  filter: blur(18rpx);
  pointer-events: none;
}

.vip-badge {
  width: 96rpx;
  height: 96rpx;
  line-height: 96rpx;
  text-align: center;
  border-radius: 50%;
  color: #11100d;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  font-weight: 900;
  font-size: 36rpx;
  box-shadow:
    0 12rpx 28rpx rgba(247, 198, 106, 0.42),
    inset 0 2rpx 6rpx rgba(255, 255, 255, 0.5);
}

.vip-title {
  font-size: 34rpx;
  font-weight: 900;
  letter-spacing: -0.3rpx;
  position: relative;
  z-index: 1;
}

.link-row,
.section-title {
  display: flex;
  align-items: center;
  margin: 30rpx 4rpx 20rpx;
  font-size: 32rpx;
  font-weight: 900;
  letter-spacing: -0.3rpx;
}

.link-row {
  color: #f7c66a;
}

.link-row::after,
.section-title::after {
  content: "";
  flex: 1;
  height: 1rpx;
  margin-left: 18rpx;
  background: linear-gradient(90deg, rgba(247, 198, 106, 0.3), transparent);
}

.order-row {
  justify-content: space-between;
  gap: 18rpx;
  padding: 26rpx;
}

.order-title {
  max-width: 480rpx;
  font-size: 28rpx;
  font-weight: 900;
  letter-spacing: 0.3rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-status {
  flex-shrink: 0;
  padding: 6rpx 16rpx;
  color: rgba(255,255,255,0.62);
  background: rgba(255, 255, 255, 0.08);
  border: 1rpx solid rgba(255, 255, 255, 0.14);
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 800;
  letter-spacing: 0.5rpx;
}

.order-status.paid {
  color: #71e2a3;
  background: rgba(113, 226, 163, 0.14);
  border-color: rgba(113, 226, 163, 0.4);
}

.state {
  padding: 140rpx 0;
  text-align: center;
  color: rgba(255,255,255,0.56);
  font-size: 26rpx;
  font-weight: 600;
}

.state.small {
  padding: 60rpx 0;
}
</style>
