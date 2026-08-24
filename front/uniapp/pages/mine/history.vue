<template>
  <view class="page">
    <view class="page-head">
      <view class="page-title">{{ t('watchHistory') }}</view>
      <view class="head-actions">
        <view v-if="list.length" class="clear-btn" @click="clearHistory">{{ t('clear') }}</view>
        <picker :range="localeNames" :value="localeIndex" @change="changeLocale">
          <view class="language">{{ currentLocaleShort }}</view>
        </picker>
      </view>
    </view>
    <view v-for="item in list" :key="item.episodeId" class="row" @click="open(item)">
      <image :src="item.coverUrl" mode="aspectFill" />
      <view class="copy">
        <view class="title">{{ item.dramaTitle }}</view>
        <view class="desc">{{ t('episodeTitle', { num: item.episodeNo }) }} · {{ item.episodeTitle }}</view>
        <view class="desc">{{ t('secondsWatched', { count: item.progressSeconds || 0 }) }} · {{ item.progressText || formatDuration(item.progressSeconds) }}</view>
      </view>
      <view class="row-actions" @click.stop="removeItem(item)">
        <view class="remove-btn">✕</view>
      </view>
    </view>
    <view v-if="!list.length" class="empty">{{ t('noWatchHistory') }}</view>
    <app-tab-bar />
  </view>
</template>

<script>
import api from '../../utils/api.js'
import { getLocale, localeOptions, setLocale, t as translate, formatDuration } from '../../utils/i18n.js'

export default {
  data() {
    return {
      list: [],
      locale: getLocale(),
      localeOptions
    }
  },
  computed: {
    localeNames() {
      return this.localeOptions.map(item => item.label)
    },
    localeIndex() {
      return Math.max(0, this.localeOptions.findIndex(item => item.code === this.locale))
    },
    currentLocaleShort() {
      return this.localeOptions[this.localeIndex].short
    }
  },
  onShow() {
    this.refreshLocale()
    this.load()
  },
  methods: {
    async load() {
      try {
        const res = await api.histories()
        this.list = (res || []).map(item => ({
          ...item,
          dramaId: item.dramaId || item.drama_id,
          episodeId: item.episodeId || item.episode_id,
          progressSeconds: item.progressSeconds || item.progress_seconds,
          progressText: item.progressText || item.progress_text,
          dramaTitle: item.dramaTitle || item.drama_title,
          coverUrl: item.coverUrl || item.cover_url,
          episodeNo: item.episodeNo || item.episode_no,
          episodeTitle: item.episodeTitle || item.episode_title
        }))
      } catch (err) {
        this.list = []
        uni.showToast({ title: err.message, icon: 'none' })
      }
    },
    async removeItem(item) {
      try {
        await api.deleteHistory(item.dramaId)
        this.list = this.list.filter(i => i.episodeId !== item.episodeId || i.dramaId !== item.dramaId)
        uni.showToast({ title: this.t('historyDeleted'), icon: 'none' })
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
      }
    },
    clearHistory() {
      uni.showModal({
        title: this.t('clearHistory'),
        content: this.t('deleteConfirm'),
        success: async (res) => {
          if (res.confirm) {
            try {
              await api.clearHistory()
              this.list = []
              uni.showToast({ title: this.t('historyCleared'), icon: 'none' })
            } catch (err) {
              uni.showToast({ title: err.message, icon: 'none' })
            }
          }
        }
      })
    },
    open(item) {
      uni.navigateTo({ url: '/pages/index/index?id=' + item.dramaId + '&courseDetailsId=' + item.episodeId })
    },
    refreshLocale() {
      this.locale = getLocale()
      uni.setNavigationBarTitle({ title: this.t('watchHistory') })
    },
    changeLocale(e) {
      const item = this.localeOptions[Number(e.detail.value)]
      if (!item) return
      setLocale(item.code)
      this.refreshLocale()
    },
    t(key, params) {
      return translate(key, params, this.locale)
    },
    formatDuration(seconds) {
      return formatDuration(seconds)
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
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18rpx 6rpx 30rpx;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.page-title {
  font-size: 44rpx;
  font-weight: 800;
  letter-spacing: -0.6rpx;
}

.clear-btn {
  color: rgba(255, 255, 255, 0.68);
  font-size: 24rpx;
  font-weight: 700;
  letter-spacing: 0.3rpx;
  padding: 10rpx 22rpx;
  background: rgba(255, 255, 255, 0.08);
  border: 1rpx solid rgba(255, 255, 255, 0.14);
  border-radius: 999rpx;
  transition: all 0.2s ease;
}

.clear-btn:active {
  transform: scale(0.95);
  background: rgba(255, 90, 92, 0.18);
  color: #ff6b6e;
  border-color: rgba(255, 90, 92, 0.36);
}

.language {
  width: 86rpx;
  height: 58rpx;
  line-height: 58rpx;
  text-align: center;
  color: #11100d;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 800;
  letter-spacing: 0.3rpx;
  box-shadow:
    0 8rpx 22rpx rgba(247, 198, 106, 0.36),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
}

.row {
  display: flex;
  align-items: center;
  padding: 22rpx;
  margin-bottom: 20rpx;
  background:
    linear-gradient(160deg, rgba(255, 255, 255, 0.13), rgba(255, 255, 255, 0.052)),
    rgba(12, 15, 24, 0.72);
  border: 1rpx solid rgba(255, 255, 255, 0.15);
  border-radius: 22rpx;
  box-shadow: 0 22rpx 64rpx rgba(0, 0, 0, 0.26);
  transition: transform 0.2s ease, border-color 0.2s ease;
}

.row:active {
  transform: scale(0.99);
  border-color: rgba(247, 198, 106, 0.4);
}

.row image {
  width: 150rpx;
  height: 200rpx;
  border-radius: 14rpx;
  background: #1a1d26;
  flex-shrink: 0;
  box-shadow: 0 6rpx 16rpx rgba(0, 0, 0, 0.4);
}

.copy {
  flex: 1;
  margin-left: 20rpx;
  min-width: 0;
}

.title {
  font-size: 32rpx;
  font-weight: 800;
  letter-spacing: -0.2rpx;
}

.desc {
  margin-top: 10rpx;
  color: rgba(255, 255, 255, 0.62);
  font-size: 26rpx;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-actions {
  flex-shrink: 0;
  margin-left: 12rpx;
}

.remove-btn {
  width: 56rpx;
  height: 56rpx;
  line-height: 56rpx;
  text-align: center;
  background: rgba(255, 255, 255, 0.08);
  border: 1rpx solid rgba(255, 255, 255, 0.14);
  color: rgba(255, 255, 255, 0.6);
  border-radius: 50%;
  font-size: 24rpx;
  transition: all 0.2s ease;
}

.remove-btn:active {
  transform: scale(0.88);
  background: rgba(255, 90, 92, 0.22);
  color: #ff6b6e;
  border-color: rgba(255, 90, 92, 0.4);
}

.empty {
  padding: 120rpx 0;
  text-align: center;
  color: rgba(255, 255, 255, 0.62);
  font-size: 28rpx;
}
</style>
