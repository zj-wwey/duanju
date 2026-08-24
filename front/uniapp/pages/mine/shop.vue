<template>
  <view class="page">
    <view class="page-head">
      <view class="page-title">{{ t('shopTitle') }}</view>
    </view>

    <!-- ① 商品列表 -->
    <view class="section-title">{{ t('shopExchange') }}</view>

    <view v-if="loading" class="loading">
      <text>{{ t('loading') }}...</text>
    </view>

    <view v-else-if="!items.length" class="empty">
      <text class="empty-icon">🛒</text>
      <text class="empty-text">{{ t('shopNoItems') }}</text>
    </view>

    <view v-else class="item-list">
      <view v-for="item in items" :key="item.id" class="item-card">
        <view class="item-info">
          <view class="item-name">{{ item.name || item.itemName }}</view>
          <view class="item-meta">
            <view class="item-points">
              <text class="points-num">{{ formatNumber(item.pointsCost || item.points_cost) }}</text>
              <text class="points-unit">{{ t('credits') }}</text>
            </view>
            <view v-if="item.vipPointsCost || item.vip_points_cost" class="item-vip-price">
              {{ t('shopVipPrice') }}：{{ formatNumber(item.vipPointsCost || item.vip_points_cost) }} {{ t('credits') }}
            </view>
          </view>
          <view class="item-stock">
            <text class="stock-label">{{ t('shopStock') }}：</text>
            <text class="stock-value" :class="{ low: (item.stock || 0) <= 5 }">{{ item.stock || 0 }}</text>
          </view>
        </view>
        <button
          class="exchange-btn"
          :disabled="exchangingId === item.id || (item.stock || 0) <= 0"
          :class="{ 'out-of-stock': (item.stock || 0) <= 0 }"
          @click="exchangeItem(item)">
          <text v-if="exchangingId === item.id">{{ t('loading') }}</text>
          <text v-else-if="(item.stock || 0) <= 0">{{ t('shopItemOutOfStock') }}</text>
          <text v-else>{{ t('shopExchange') }}</text>
        </button>
      </view>
    </view>

    <!-- ② 兑换记录 -->
    <view class="section-title">{{ t('shopRecords') }}</view>

    <view v-if="loadingRecords" class="loading">
      <text>{{ t('loading') }}...</text>
    </view>

    <view v-else-if="!records.length" class="empty">
      <text>{{ t('shopNoRecords') }}</text>
    </view>

    <view v-else class="record-list">
      <view v-for="rec in records" :key="rec.id" class="record-item">
        <view class="record-info">
          <view class="record-name">{{ rec.itemName || rec.item_name || '-' }}</view>
          <view class="record-time">{{ formatDate(rec.createdAt || rec.created_at) }}</view>
        </view>
        <view class="record-right">
          <view class="record-cost">-{{ formatNumber(rec.pointsCost || rec.points_cost) }}</view>
          <view class="record-status" :class="{ delivered: (rec.deliveryStatus || rec.delivery_status) === 'DELIVERED' }">
            {{ (rec.deliveryStatus || rec.delivery_status) === 'DELIVERED' ? t('shopDelivered') : t('shopPending') }}
          </view>
        </view>
      </view>
    </view>
    <app-tab-bar current="store" />
  </view>
</template>

<script>
import api from '../../utils/api.js'
import { getLocale, t as translate } from '../../utils/i18n.js'
import { formatNumber, formatDate } from '../../../shared/utils/format.js'

export default {
  data() {
    return {
      items: [],
      records: [],
      loading: false,
      loadingRecords: false,
      exchangingId: null,
      locale: getLocale()
    }
  },
  onShow() {
    this.locale = getLocale()
    uni.setNavigationBarTitle({ title: this.t('shopTitle') })
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      this.loadingRecords = true
      try {
        await Promise.allSettled([
          this.loadItems(),
          this.loadRecords()
        ])
      } finally {
        this.loading = false
        this.loadingRecords = false
      }
    },
    async loadItems() {
      try {
        const data = await api.shopItems()
        this.items = Array.isArray(data) ? data : (data?.records || data?.list || [])
      } catch (e) {
        this.items = []
        uni.showToast({ title: e.message, icon: 'none' })
      }
    },
    async loadRecords() {
      try {
        const data = await api.shopRecords()
        this.records = Array.isArray(data) ? data : (data?.records || data?.list || [])
      } catch (e) {
        this.records = []
        uni.showToast({ title: e.message, icon: 'none' })
      }
    },
    async exchangeItem(item) {
      if (!uni.getStorageSync('token')) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }
      if (this.exchangingId) return
      if ((item.stock || 0) <= 0) {
        uni.showToast({ title: this.t('shopItemOutOfStock'), icon: 'none' })
        return
      }

      const result = await new Promise((resolve) => {
        uni.showModal({
          title: this.t('shopExchange'),
          content: this.t('shopExchangeConfirm', {
            points: this.formatNumber(item.pointsCost || item.points_cost),
            name: item.name || item.itemName
          }),
          success: (res) => resolve(res.confirm),
          fail: () => resolve(false)
        })
      })
      if (!result) return

      this.exchangingId = item.id
      try {
        await api.shopExchange(item.id)
        uni.showToast({ title: this.t('shopExchangeSuccess'), icon: 'none' })
        await this.load()
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
      } finally {
        this.exchangingId = null
      }
    },

    formatNumber,
    formatDate,
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
  color: rgba(255,255,255,0.5);
}

.empty-icon {
  font-size: 80rpx;
  display: block;
  margin-bottom: 20rpx;
}

/* 商品列表 */
.item-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  margin-bottom: 40rpx;
}

.item-card {
  background: linear-gradient(160deg, rgba(255,255,255,0.13), rgba(255,255,255,0.052)), rgba(12,15,24,0.72);
  border: 1rpx solid rgba(255,255,255,0.15);
  border-radius: 28rpx;
  padding: 36rpx 32rpx;
  box-shadow: 0 24rpx 70rpx rgba(0, 0, 0, 0.28);
  transition: transform 0.2s ease, border-color 0.2s ease;
}

.item-card:active {
  transform: scale(0.99);
  border-color: rgba(247, 198, 106, 0.4);
}

.item-info {
  margin-bottom: 24rpx;
}

.item-name {
  font-size: 36rpx;
  font-weight: 800;
  letter-spacing: -0.4rpx;
  margin-bottom: 16rpx;
}

.item-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx 32rpx;
  margin-bottom: 16rpx;
}

.item-points {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
}

.points-num {
  font-size: 48rpx;
  font-weight: 900;
  letter-spacing: -0.4rpx;
  color: #f7c66a;
  text-shadow: 0 0 18rpx rgba(247, 198, 106, 0.35);
}

.points-unit {
  font-size: 24rpx;
  color: rgba(255,255,255,0.5);
}

.item-vip-price {
  font-size: 22rpx;
  font-weight: 700;
  letter-spacing: 0.3rpx;
  color: rgba(77,208,225,0.9);
  padding: 6rpx 14rpx;
  background: rgba(77,208,225,0.14);
  border: 1rpx solid rgba(77, 208, 225, 0.28);
  border-radius: 10rpx;
}

.item-stock {
  font-size: 24rpx;
  color: rgba(255,255,255,0.5);
}

.stock-label {
  color: rgba(255,255,255,0.4);
}

.stock-value {
  color: rgba(255,255,255,0.72);
  font-weight: 700;
}

.stock-value.low {
  color: #ff9a3c;
  text-shadow: 0 0 10rpx rgba(255, 154, 60, 0.45);
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

.exchange-btn.out-of-stock {
  background: rgba(255,255,255,0.1);
  color: rgba(255,255,255,0.4);
  box-shadow: none;
}

/* 兑换记录 */
.record-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  margin-bottom: 40rpx;
}

.record-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background:
    linear-gradient(160deg, rgba(255,255,255,0.1), rgba(255,255,255,0.035)),
    rgba(12,15,24,0.72);
  border: 1rpx solid rgba(255,255,255,0.12);
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

.record-time {
  font-size: 22rpx;
  color: rgba(255,255,255,0.45);
}

.record-right {
  text-align: right;
}

.record-cost {
  font-size: 32rpx;
  font-weight: 800;
  letter-spacing: -0.2rpx;
  color: #f7c66a;
  margin-bottom: 6rpx;
}

.record-status {
  font-size: 20rpx;
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(255,255,255,0.1);
  color: rgba(255,255,255,0.5);
  font-weight: 700;
  letter-spacing: 0.3rpx;
}

.record-status.delivered {
  background: rgba(76,175,80,0.18);
  color: #5fd07a;
  border: 1rpx solid rgba(76, 175, 80, 0.32);
}
</style>
