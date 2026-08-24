<template>
  <view class="page">
    <view class="page-head">
      <view class="page-title">{{ t('announcementList') }}</view>
    </view>

    <view v-if="loading && !list.length" class="loading">
      <text>{{ t('loading') }}...</text>
    </view>

    <view v-else-if="!list.length" class="empty">
      <text class="empty-icon">📢</text>
      <text class="empty-text">{{ t('announcementEmpty') }}</text>
    </view>

    <view v-else class="announcement-list">
      <view v-for="item in list" :key="item.id" class="announcement-card" @click="openDetail(item)">
        <view class="card-header">
          <view class="tag" :class="item.type === 'SYSTEM' ? 'tag-system' : 'tag-activity'">
            {{ t(item.type === 'SYSTEM' ? 'systemAnnouncement' : 'activityAnnouncement') }}
          </view>
          <view v-if="item.isTop === 1" class="top-tag">📌</view>
          <view v-if="item.read === false" class="unread-dot"></view>
        </view>
        <view class="card-title">{{ item.title }}</view>
        <view class="card-content">{{ truncate(item.content, 80) }}</view>
        <view class="card-footer">
          <text class="time">{{ formatTime(item.publishedAt || item.published_at) }}</text>
          <text class="read-count" v-if="item.readCount || item.read_count">
            {{ item.readCount || item.read_count }} {{ t('views') }}
          </text>
        </view>
      </view>
    </view>

    <view v-if="detail" class="detail-overlay" @click="closeDetail">
      <view class="detail-content" @click.stop>
        <view class="detail-header">
          <text class="detail-title">{{ detail.title }}</text>
          <text class="close-btn" @click="closeDetail">✕</text>
        </view>
        <view class="detail-meta">
          <text class="tag" :class="detail.type === 'SYSTEM' ? 'tag-system' : 'tag-activity'">
            {{ t(detail.type === 'SYSTEM' ? 'systemAnnouncement' : 'activityAnnouncement') }}
          </text>
          <text class="time">{{ formatTime(detail.publishedAt || detail.published_at) }}</text>
        </view>
        <view class="detail-body">{{ detail.content }}</view>
      </view>
      <view class="detail-mask" @click="closeDetail"></view>
    </view>
    <app-tab-bar />
  </view>
</template>

<script>
import api from '../../utils/api.js'
import { getLocale, setLocale, t as translate } from '../../utils/i18n.js'

export default {
  data() {
    return {
      list: [],
      loading: false,
      detail: null,
      locale: getLocale()
    }
  },
  onShow() {
    this.locale = getLocale()
    uni.setNavigationBarTitle({ title: this.t('announcementList') })
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        this.list = await api.announcements()
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
      } finally {
        this.loading = false
      }
    },
    async openDetail(item) {
      this.detail = item
      if (!item.read) {
        try {
          await api.announcementRead(item.id)
          item.read = true
        } catch (e) {}
      }
    },
    closeDetail() {
      this.detail = null
      this.load()
    },
    truncate(text, len) {
      if (!text) return ''
      return text.length > len ? text.slice(0, len) + '...' : text
    },
    formatTime(str) {
      if (!str) return ''
      try {
        const d = new Date(str)
        return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
      } catch (e) {
        return str
      }
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

.loading, .empty {
  padding: 120rpx 0;
  text-align: center;
  color: rgba(255, 255, 255, 0.5);
}

.empty-icon {
  font-size: 80rpx;
  display: block;
  margin-bottom: 20rpx;
}

.announcement-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.announcement-card {
  background: linear-gradient(160deg, rgba(255, 255, 255, 0.13), rgba(255, 255, 255, 0.052)), rgba(12, 15, 24, 0.72);
  border: 1rpx solid rgba(255, 255, 255, 0.15);
  border-radius: 24rpx;
  padding: 28rpx 32rpx;
  box-shadow: 0 24rpx 70rpx rgba(0, 0, 0, 0.28);
  transition: transform 0.2s ease, border-color 0.2s ease;
}

.announcement-card:active {
  transform: scale(0.99);
  border-color: rgba(247, 198, 106, 0.4);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 16rpx;
}

.tag {
  font-size: 20rpx;
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
  font-weight: 700;
  letter-spacing: 0.3rpx;
}

.tag-system {
  background: rgba(247, 198, 106, 0.22);
  color: #f7c66a;
}

.tag-activity {
  background: rgba(77, 208, 225, 0.2);
  color: #4dd0e1;
}

.top-tag {
  font-size: 24rpx;
}

.unread-dot {
  width: 16rpx;
  height: 16rpx;
  background: #ff5a5c;
  border-radius: 50%;
  margin-left: auto;
  box-shadow:
    0 0 12rpx rgba(255, 90, 92, 0.7),
    0 2rpx 4rpx rgba(0, 0, 0, 0.4);
}

.card-title {
  font-size: 32rpx;
  font-weight: 800;
  letter-spacing: -0.2rpx;
  margin-bottom: 12rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-content {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.62);
  line-height: 1.55;
  margin-bottom: 16rpx;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.4);
}

.detail-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 999;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}

.detail-mask {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.66);
  backdrop-filter: blur(8rpx);
}

.detail-content {
  position: relative;
  background:
    linear-gradient(180deg, rgba(20, 24, 32, 0.98), rgba(10, 12, 18, 0.98));
  border-top: 1rpx solid rgba(247, 198, 106, 0.28);
  border-radius: 36rpx 36rpx 0 0;
  padding: 40rpx 32rpx calc(40rpx + env(safe-area-inset-bottom));
  max-height: 80vh;
  overflow-y: auto;
  box-shadow: 0 -20rpx 50rpx rgba(0, 0, 0, 0.5);
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24rpx;
}

.detail-title {
  font-size: 36rpx;
  font-weight: 800;
  letter-spacing: -0.4rpx;
  flex: 1;
  margin-right: 20rpx;
}

.close-btn {
  font-size: 36rpx;
  color: rgba(255, 255, 255, 0.5);
  padding: 10rpx 14rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.detail-meta .time {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.4);
}

.detail-body {
  font-size: 28rpx;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.85);
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
