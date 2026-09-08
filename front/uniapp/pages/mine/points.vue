<template>
  <view class="page">
    <view class="page-head">
      <view class="head-title">积分明细</view>
      <view v-if="user" class="head-points">{{ t('creditsAvailable', { count: user.points || 0 }) }}</view>
    </view>

    <view class="card">
      <view v-if="records.length === 0" class="empty">
        <text class="empty-icon">📋</text>
        <text class="empty-text">暂无积分记录</text>
      </view>

      <view v-for="item in records" :key="item.id" class="row">
        <view class="row-left">
          <view class="row-title">{{ item.remark || item.biz_type }}</view>
          <view class="row-time">{{ formatTime(item.createdAt) }}</view>
        </view>
        <view class="row-right">
          <text :class="item.delta > 0 ? 'plus' : 'minus'">
            {{ item.delta > 0 ? '+' : '' }}{{ item.delta }}
          </text>
        </view>
      </view>
    </view>

    <app-tab-bar />
  </view>
</template>

<script>
import api from '../../utils/api.js'
import { getLocale, t as translate } from '../../utils/i18n.js'

export default {
  data() {
    return {
      user: null,
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
        const data = await api.points()
        this.user = data.user
        this.records = data.records || []
      } catch (err) {
        uni.showToast({ title: err.message || '加载失败', icon: 'none' })
      }
    },
    formatTime(ts) {
      if (!ts) return ''
      // 后端返回可能是 "2026-09-04T10:30:00" 或时间戳
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
    t(key, params) {
      return translate(key, params, getLocale())
    }
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #080a10;
  padding: 0 24rpx 160rpx;
  box-sizing: border-box;
}

.page-head {
  padding: 32rpx 16rpx 24rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.head-title {
  font-size: 36rpx;
  font-weight: 800;
  color: #fff;
  letter-spacing: 0.5rpx;
}

.head-points {
  font-size: 26rpx;
  color: #f7c66a;
  font-weight: 600;
}

.card {
  background: rgba(12, 15, 24, 0.88);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
  border-radius: 24rpx;
  padding: 8rpx 24rpx;
  backdrop-filter: blur(20rpx);
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

.row-left {
  flex: 1;
  min-width: 0;
}

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
  margin-left: 24rpx;
}

.plus {
  color: #5fe5a8;
  font-weight: 800;
  font-size: 32rpx;
}

.minus {
  color: #ff7b7b;
  font-weight: 800;
  font-size: 32rpx;
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
