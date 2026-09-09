<template>
  <view class="page">
    <view class="page-head">
      <view class="page-title">{{ t('orders') }}</view>
    </view>

    <view v-if="loading" class="loading">{{ t('loading') }}</view>

    <view v-else-if="!orders.length" class="empty">{{ t('noOrders') }}</view>

    <view v-else class="order-list">
      <view v-for="item in orders" :key="item.id" class="order-card">
        <view class="order-header">
          <view class="order-no">{{ t('orderNo') }}: {{ item.order_no || item.orderNo || item.id }}</view>
          <view class="order-status" :class="orderStatusClass(item.status)">{{ formatStatus(item.status) }}</view>
        </view>
        <view class="order-body">
          <view class="order-info">
            <view class="order-name">{{ item.product_name || item.productName || item.name }}</view>
            <view class="order-points">
              <text>{{ t('credits') }}: {{ item.points || item.points_amount || 0 }}</text>
              <text class="order-amount" v-if="item.amount_cents">
                {{ formatMoney(item.amount_cents, item.currency) }}
              </text>
            </view>
          </view>
        </view>
        <view class="order-footer">
          <text class="order-time">{{ formatDate(item.created_at || item.createdAt) }}</text>
          <text v-if="canDelete(item.status)" class="order-delete" @click="onDelete(item)">删除</text>
        </view>
      </view>
    </view>
    <app-tab-bar current="mine" />
  </view>
</template>

<script>
import api from '../../utils/api.js'
import { getLocale, t as translate } from '../../utils/i18n.js'
import { formatPrice } from '../../utils/currencyConfig.js'
import { formatDate as formatDateUtil } from '../../../shared/utils/format.js'

export default {
  data() {
    return {
      locale: getLocale(),
      orders: [],
      loading: true
    }
  },
  onShow() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const data = await api.orders()
        this.orders = (data || []).map(item => ({
          ...item,
          order_no: item.order_no || item.orderNo,
          product_name: item.product_name || item.productName,
          amount_cents: item.amount_cents || item.amountCents,
          created_at: item.created_at || item.createdAt
        }))
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
      } finally {
        this.loading = false
      }
    },
    formatStatus(status) {
      const map = {
        PENDING: this.t('pending'),
        PAID: this.t('paid'),
        REFUNDED: this.t('refunded'),
        CANCELLED: this.t('cancelled'),
        CLOSED: this.t('closed')
      }
      return map[status] || status || '-'
    },
    orderStatusClass(status) {
      if (status === 'PAID') return 'status-paid'
      if (status === 'PENDING') return 'status-pending'
      if (status === 'REFUNDED') return 'status-refunded'
      if (status === 'CANCELLED' || status === 'CLOSED') return 'status-closed'
      return 'status-expired'
    },
    formatMoney(cents, currency) {
      return formatPrice(Number(cents || 0), currency || 'USD')
    },
    formatDate(value) {
      return formatDateUtil(value, this.locale, true)
    },
    canDelete(status) {
      return ['CANCELLED', 'CLOSED', 'REFUNDED'].includes(status)
    },
    onDelete(item) {
      const orderNo = item.order_no || item.orderNo || item.id
      uni.showModal({
        title: '确认删除',
        content: '删除后将从我的订单中消失,此操作不可恢复',
        confirmText: '删除',
        confirmColor: '#ff6b6b',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await api.deleteOrder(orderNo)
            this.orders = this.orders.filter(o => {
              const n = o.order_no || o.orderNo || o.id
              return String(n) !== String(orderNo)
            })
            uni.showToast({ title: '已删除', icon: 'success' })
          } catch (err) {
            uni.showToast({ title: err.message || '删除失败', icon: 'none' })
          }
        }
      })
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
  font-size: 44rpx;
  font-weight: 800;
  letter-spacing: -0.6rpx;
}

.loading,
.empty {
  padding: 120rpx 0;
  text-align: center;
  color: rgba(255, 255, 255, 0.5);
  font-size: 28rpx;
}

.order-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.order-card {
  background:
    linear-gradient(160deg, rgba(255, 255, 255, 0.13), rgba(255, 255, 255, 0.052)),
    rgba(12, 15, 24, 0.72);
  border: 1rpx solid rgba(255, 255, 255, 0.15);
  border-radius: 24rpx;
  padding: 28rpx 32rpx;
  box-shadow: 0 24rpx 70rpx rgba(0, 0, 0, 0.28);
  transition: transform 0.2s ease, border-color 0.2s ease;
}

.order-card:active {
  transform: scale(0.99);
  border-color: rgba(247, 198, 106, 0.4);
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.order-no {
  font-size: 22rpx;
  font-weight: 600;
  letter-spacing: 0.3rpx;
  color: rgba(255, 255, 255, 0.5);
}

.order-status {
  font-size: 22rpx;
  font-weight: 800;
  letter-spacing: 0.3rpx;
  padding: 6rpx 18rpx;
  border-radius: 999rpx;
}

.status-paid {
  background: rgba(13, 143, 88, 0.2);
  color: #34d399;
  border: 1rpx solid rgba(52, 211, 153, 0.35);
}

.status-pending {
  background: rgba(247, 198, 106, 0.22);
  color: #f7c66a;
  border: 1rpx solid rgba(247, 198, 106, 0.4);
}

.status-expired {
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.55);
}

.status-refunded {
  background: rgba(220, 38, 38, 0.2);
  color: #ff6b6e;
  border: 1rpx solid rgba(255, 107, 110, 0.35);
}

.status-closed {
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.4);
}

.order-body {
  padding: 16rpx 0;
}

.order-name {
  font-size: 30rpx;
  font-weight: 700;
  letter-spacing: -0.2rpx;
  margin-bottom: 12rpx;
}

.order-points {
  display: flex;
  gap: 20rpx;
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.66);
}

.order-amount {
  color: #f7c66a;
  font-weight: 800;
}

.order-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid rgba(255, 255, 255, 0.08);
}

.order-delete {
  font-size: 22rpx;
  font-weight: 700;
  letter-spacing: 0.5rpx;
  color: #ff6b6b;
  padding: 8rpx 22rpx;
  border-radius: 999rpx;
  background: rgba(255, 107, 107, 0.14);
  border: 1rpx solid rgba(255, 107, 107, 0.32);
  transition: transform 0.18s ease, background 0.18s ease;
}

.order-delete:active {
  transform: scale(0.94);
  background: rgba(255, 107, 107, 0.26);
}

.order-time {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.42);
  letter-spacing: 0.2rpx;
}
</style>
