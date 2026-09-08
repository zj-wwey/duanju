<template>
  <view class="page">
    <view class="page-head">
      <view class="head-title">兑换记录</view>
    </view>

    <view class="card">
      <view v-if="records.length === 0" class="empty">
        <text class="empty-icon">🎁</text>
        <text class="empty-text">暂无兑换记录</text>
      </view>

      <view v-for="item in records" :key="item.id" class="row">
        <view class="row-left">
          <view class="row-title">{{ item.itemName || item.item_name || '-' }}</view>
          <view class="row-time">{{ formatTime(item.createdAt || item.created_at) }}</view>
        </view>
        <view class="row-right">
          <view class="row-points">-{{ item.pointsCost || item.points_cost || 0 }}</view>
          <view class="row-status" :class="statusClass(item.deliveryStatus || item.delivery_status)">
            {{ labelStatus(item.deliveryStatus || item.delivery_status) }}
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import api from '../../utils/api.js'

export default {
  data() {
    return {
      records: []
    }
  },
  onShow() {
    this.load()
  },
  methods: {
    async load() {
      if (!uni.getStorageSync('token')) {
        uni.showToast({ title: '请先登录', icon: 'none' })
        setTimeout(() => uni.navigateBack(), 1500)
        return
      }
      try {
        const data = await api.shopRecords()
        this.records = data.records || data || []
      } catch (err) {
        uni.showToast({ title: err.message || '加载失败', icon: 'none' })
      }
    },
    formatTime(ts) {
      if (!ts) return ''
      let d
      if (typeof ts === 'string') {
        d = new Date(ts.replace('T', ' '))
      } else {
        d = new Date(ts)
      }
      if (isNaN(d.getTime())) return ''
      const now = new Date()
      const sameDay = d.toDateString() === now.toDateString()
      const yesterday = new Date(now.getTime() - 86400000).toDateString() === d.toDateString()
      const hh = String(d.getHours()).padStart(2, '0')
      const mm = String(d.getMinutes()).padStart(2, '0')
      if (sameDay) return `今天 ${hh}:${mm}`
      if (yesterday) return `昨天 ${hh}:${mm}`
      const mo = String(d.getMonth() + 1).padStart(2, '0')
      const dd = String(d.getDate()).padStart(2, '0')
      return `${d.getFullYear()}-${mo}-${dd} ${hh}:${mm}`
    },
    labelStatus(s) {
      const map = { PENDING: '处理中', PROCESSING: '处理中', DELIVERED: '已发货', SUCCESS: '已完成', FAILED: '失败' }
      return map[s] || s || '-'
    },
    statusClass(s) {
      if (['DELIVERED', 'SUCCESS'].includes(s)) return 'ok'
      if (['FAILED'].includes(s)) return 'bad'
      return ''
    }
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #080a10;
  padding: 0 24rpx 60rpx;
  box-sizing: border-box;
}

.page-head {
  padding: 32rpx 16rpx 24rpx;
}

.head-title {
  font-size: 36rpx;
  font-weight: 800;
  color: #fff;
  letter-spacing: 0.5rpx;
}

.card {
  background: rgba(12, 15, 24, 0.88);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
  border-radius: 24rpx;
  padding: 8rpx 24rpx;
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.06);

  &:last-child {
    border-bottom: 0;
  }
}

.row-left { flex: 1; min-width: 0; }

.row-title {
  font-size: 28rpx;
  color: rgba(255, 255, 255, 0.88);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-time {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.38);
  margin-top: 8rpx;
}

.row-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6rpx;
  margin-left: 16rpx;
}

.row-points {
  font-size: 26rpx;
  color: #ff7b7b;
  font-weight: 700;
}

.row-status {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.5);
  padding: 2rpx 12rpx;
  border-radius: 8rpx;
  background: rgba(255, 255, 255, 0.06);

  &.ok { color: #5fe5a8; background: rgba(95, 229, 168, 0.1); }
  &.bad { color: #ff7b7b; background: rgba(255, 123, 123, 0.1); }
}

.empty {
  padding: 80rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty-icon {
  font-size: 64rpx;
  margin-bottom: 16rpx;
}

.empty-text {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.4);
}
</style>
