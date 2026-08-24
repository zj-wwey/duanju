<template>
  <view class="page">
    <view class="balance-card">
      <view class="label">{{ t('rechargeCredits') }}</view>
      <view class="balance">{{ points }}</view>
      <view class="sub">{{ t('rechargeCredits') }}</view>
    </view>

    <view class="section-title">{{ t('selectPlan') }}</view>
    <view v-if="loading" class="state">{{ t('loading') }}</view>
    <view v-else class="plans">
      <view
        v-for="item in plans"
        :key="item.id"
        class="plan"
        :class="{ active: selected && selected.id === item.id }"
        @click="selected = item"
      >
        <view class="plan-name">{{ item.name }}</view>
        <view class="plan-points">{{ productPoints(item) }} {{ t('credits') }}</view>
        <view class="plan-price">{{ formatMoney(item.localPriceCents || item.priceCents || item.price_cents, item.currency) }}</view>
      </view>
    </view>

    <view v-if="selected" class="checkout">
      <view class="section-title">{{ t('payMethod') }}</view>
      <view class="methods">
        <view class="method" :class="{ active: payChannel === 'STRIPE' }" @click="payChannel = 'STRIPE'">Stripe</view>
        <view class="method" :class="{ active: payChannel === 'PAYPAL' }" @click="payChannel = 'PAYPAL'">PayPal</view>
      </view>
      <button class="pay-btn" :loading="submitting" :disabled="submitting" @click="submit">
        {{ t('confirmRecharge') }} · {{ formatMoney(selected.localPriceCents || selected.priceCents || selected.price_cents, selected.currency) }}
      </button>
    </view>

    <view class="section-title">{{ t('orders') }}</view>
    <view v-if="orders.length" class="orders">
      <view v-for="order in orders" :key="order.orderNo || order.order_no || order.id" class="order">
        <view>
          <view class="order-name">{{ order.productName || order.product_name || order.name || t('rechargeCredits') }}</view>
          <view class="order-no">{{ order.orderNo || order.order_no }}</view>
        </view>
        <view class="order-status" :class="{ paid: order.status === 'PAID' }">{{ order.status || '-' }}</view>
      </view>
    </view>
    <view v-else class="empty">{{ t('noOrders') }}</view>
    <app-tab-bar current="store" />
  </view>
</template>

<script>
import api from '../../utils/api.js'
import { getLocale, t as translate } from '../../utils/i18n.js'
import { formatPrice, getCurrencyByLocale } from '../../utils/currencyConfig.js'
import { productPoints } from '../../../shared/utils/format.js'

export default {
  data() {
    return {
      points: 0,
      plans: [],
      orders: [],
      selected: null,
      payChannel: 'STRIPE',
      submitting: false,
      loading: true,
      pollingTimer: null,
      pollingAttempts: 0,
      locale: getLocale()
    }
  },
  onLoad(options) {
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
      if (!this.ensureLogin()) return
      this.loading = true
      try {
        const [pointsData, products, orders] = await Promise.all([
          api.points().catch(() => null),
          api.productsByLocale().catch(() => api.pointProducts()),
          api.orders().catch(() => [])
        ])
        this.points = Number(pointsData?.user?.points || pointsData?.points || 0)
        this.plans = (products || []).filter(item => {
          const category = item.productCategory || item.product_category
          return !category || category === 'RECHARGE'
        })
        this.orders = (orders || []).slice(0, 20)
        if (!this.selected && this.plans.length) this.selected = this.plans[0]
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
      } finally {
        this.loading = false
      }
    },
    async submit() {
      if (!this.selected || this.submitting) return
      this.submitting = true
      try {
        const order = await api.createOrder({
          productId: this.selected.id,
          payChannel: this.payChannel
        })
        const orderNo = order.orderNo || order.order_no
        if (!orderNo) throw new Error('订单创建失败')
        let checkout
        let payUrl
        if (this.payChannel === 'PAYPAL') {
          checkout = await api.paypalCheckout(orderNo)
          payUrl = checkout.approveUrl || checkout.approve_url
        } else {
          checkout = await api.stripeCheckout(orderNo)
          payUrl = checkout.sessionUrl || checkout.session_url
        }
        if (!payUrl) throw new Error('支付链接获取失败')
        uni.setStorageSync('pendingPayment', { orderNo, payChannel: this.payChannel, createdAt: Date.now() })
        this.openPayment(payUrl)
      } catch (err) {
        uni.showToast({ title: err.message || '支付发起失败', icon: 'none' })
      } finally {
        this.submitting = false
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
      if (paypalOrderId) {
        api.paypalCapture(orderNo, paypalOrderId).catch(() => {})
      }
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
            this.stopPolling()
            uni.showToast({ title: this.t('rewardAdded'), icon: 'none' })
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
      if (uni.getStorageSync('token')) return true
      uni.navigateTo({ url: '/pages/login/login' })
      return false
    },
    productPoints,
    formatMoney(cents, currency) {
      return formatPrice(Number(cents || 0), currency || getCurrencyByLocale(this.locale).code)
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
  padding: 28rpx;
  box-sizing: border-box;
  background:
    radial-gradient(circle at 16% 0, rgba(247,198,106,0.2), transparent 32%),
    radial-gradient(circle at 90% 14%, rgba(77,208,225,0.1), transparent 38%),
    #080a10;
  color: #fff;
  font-family: -apple-system, BlinkMacSystemFont, "Inter", "SF Pro Display", "Segoe UI", sans-serif;
}

.balance-card,
.checkout,
.plan,
.order {
  background: linear-gradient(160deg, rgba(255,255,255,0.14), rgba(255,255,255,0.055)), rgba(12,15,24,0.78);
  border: 1rpx solid rgba(255,255,255,0.16);
  border-radius: 28rpx;
  box-shadow:
    0 22rpx 50rpx rgba(0, 0, 0, 0.32),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(20rpx);
}

.balance-card {
  position: relative;
  padding: 38rpx;
  margin-bottom: 32rpx;
  background: linear-gradient(145deg, rgba(247,198,106,0.22), rgba(255,255,255,0.08)), rgba(12,15,24,0.85);
  border: 1rpx solid rgba(247,198,106,0.32);
  overflow: hidden;
}

.balance-card::before {
  content: "";
  position: absolute;
  top: -50rpx;
  right: -50rpx;
  width: 220rpx;
  height: 220rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(247, 198, 106, 0.18), transparent 70%);
  filter: blur(20rpx);
  pointer-events: none;
}

.label,
.sub,
.order-no {
  color: rgba(255,255,255,0.62);
  font-size: 24rpx;
  font-weight: 600;
  letter-spacing: 0.5rpx;
}

.balance {
  position: relative;
  z-index: 1;
  margin-top: 12rpx;
  color: #f7c66a;
  font-size: 76rpx;
  font-weight: 900;
  letter-spacing: -2rpx;
  text-shadow: 0 4rpx 22rpx rgba(247, 198, 106, 0.32);
}

.section-title {
  display: flex;
  align-items: center;
  margin: 30rpx 4rpx 20rpx;
  font-size: 34rpx;
  font-weight: 800;
  letter-spacing: -0.3rpx;
}

.section-title::before {
  content: "";
  display: inline-block;
  width: 8rpx;
  height: 32rpx;
  margin-right: 16rpx;
  background: linear-gradient(180deg, #ffe0a1, #f3b84d);
  border-radius: 4rpx;
  box-shadow: 0 4rpx 12rpx rgba(247, 198, 106, 0.4);
}

.plans {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 18rpx;
}

.plan {
  padding: 28rpx;
  transition: all 0.2s ease;
}

.plan:active {
  transform: scale(0.97);
}

.plan.active,
.method.active {
  border-color: rgba(247,198,106,0.7);
  box-shadow:
    0 0 0 2rpx rgba(247,198,106,0.32),
    0 22rpx 50rpx rgba(0, 0, 0, 0.32),
    0 0 30rpx rgba(247, 198, 106, 0.18);
  background: linear-gradient(160deg, rgba(247,198,106,0.18), rgba(255,255,255,0.055)), rgba(12,15,24,0.85);
}

.plan-name {
  font-size: 30rpx;
  font-weight: 800;
  letter-spacing: 0.3rpx;
}

.plan-points {
  margin-top: 18rpx;
  color: #f7c66a;
  font-size: 38rpx;
  font-weight: 900;
  letter-spacing: -1rpx;
}

.plan-price {
  margin-top: 12rpx;
  color: rgba(255,255,255,0.74);
  font-weight: 700;
}

.checkout {
  margin-top: 30rpx;
  padding: 26rpx;
}

.methods {
  display: flex;
  gap: 18rpx;
}

.method {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  text-align: center;
  border-radius: 18rpx;
  background: rgba(255,255,255,0.08);
  border: 1rpx solid rgba(255,255,255,0.14);
  font-weight: 800;
  letter-spacing: 0.5rpx;
  transition: all 0.2s ease;
}

.method:active {
  transform: scale(0.97);
}

.pay-btn {
  margin-top: 26rpx;
  height: 92rpx;
  line-height: 92rpx;
  border-radius: 999rpx;
  color: #11100d;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  font-size: 30rpx;
  font-weight: 900;
  letter-spacing: 1rpx;
  box-shadow:
    0 18rpx 42rpx rgba(247, 198, 106, 0.4),
    inset 0 2rpx 0 rgba(255, 255, 255, 0.5);
  transition: transform 0.18s ease;
}

.pay-btn:active {
  transform: scale(0.97);
}

.orders {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.order {
  display: flex;
  justify-content: space-between;
  gap: 20rpx;
  padding: 24rpx;
  transition: transform 0.2s ease;
}

.order:active {
  transform: scale(0.98);
}

.order-name {
  font-size: 28rpx;
  font-weight: 800;
  letter-spacing: 0.3rpx;
}

.order-status {
  padding: 6rpx 16rpx;
  color: #f7c66a;
  background: rgba(247, 198, 106, 0.12);
  border: 1rpx solid rgba(247, 198, 106, 0.4);
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 800;
  letter-spacing: 0.5rpx;
}

.order-status.paid {
  color: #5fe5a8;
  background: rgba(95, 229, 168, 0.14);
  border-color: rgba(95, 229, 168, 0.4);
}

.state,
.empty {
  padding: 100rpx 0;
  color: rgba(255,255,255,0.58);
  text-align: center;
  font-size: 26rpx;
  font-weight: 600;
}
</style>
