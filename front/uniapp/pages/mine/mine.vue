<template>
  <view class="page mine-page">
    <view class="page-head">
      <view class="page-title">{{ t('profile') }}</view>
      <picker :range="localeNames" :value="localeIndex" @change="changeLocale">
        <view class="language">{{ currentLocaleShort }}</view>
      </picker>
    </view>

    <view class="profile-card">
      <view class="avatar-block" @click="user ? go('/pages/mine/profile-edit') : goLogin()">
        <image v-if="user && (user.avatarUrl || user.avatar_url)" class="avatar" :src="user.avatarUrl || user.avatar_url" mode="aspectFill" />
        <view v-else class="avatar-placeholder">{{ avatarInitial }}</view>
      </view>
      <view class="user-info" @click="user ? go('/pages/mine/profile-edit') : goLogin()">
        <view class="name">
          {{ user ? (user.nickname || user.username) : t('guest') }}
          <text v-if="membershipLevel && membershipLevel !== 'NONE'" class="level-badge" :style="{ color: levelColor(membershipLevel) }">{{ levelDisplayName(membershipLevel) }}</text>
        </view>
        <view class="points" v-if="user">{{ t('creditsAvailable', { count: user.points || 0 }) }}</view>
        <view class="points" v-else>{{ t('pleaseSignIn') }}</view>
      </view>
      <button class="login-btn" @click.stop="user ? logout() : goLogin()">{{ user ? t('signOut') : t('signIn') }}</button>
    </view>

    <view class="actions">
      <view class="item" @click="checkin">
        <view class="item-icon">🎁</view>
        <view class="item-label">{{ t('dailyReward') }}</view>
        <view class="item-arrow">›</view>
      </view>
      <view class="item" @click="go('/pages/mine/favorites')">
        <view class="item-icon">⭐</view>
        <view class="item-label">{{ t('myList') }}</view>
        <view class="item-arrow">›</view>
      </view>
      <view class="item" @click="go('/pages/mine/history')">
        <view class="item-icon">📺</view>
        <view class="item-label">{{ t('watchHistory') }}</view>
        <view class="item-arrow">›</view>
      </view>
      <view class="item" @click="goTab('/pages/theater/theater')">
        <view class="item-icon">🔎</view>
        <view class="item-label">{{ t('browse') }}</view>
        <view class="item-arrow">›</view>
      </view>
      <view class="item" @click="goTab('/pages/store/store?tab=recharge')">
        <view class="item-icon">💳</view>
        <view class="item-label">{{ t('rechargeCredits') }}</view>
        <view class="item-arrow">›</view>
      </view>
      <view class="item" @click="goTab('/pages/store/store?tab=orders')">
        <view class="item-icon">📦</view>
        <view class="item-label">{{ t('orders') }}</view>
        <view class="item-arrow">›</view>
      </view>
      <view class="item" @click="goTab('/pages/store/store?tab=vip')">
        <view class="item-icon">👑</view>
        <view class="item-label">{{ t('vipMember') }}</view>
        <view class="item-arrow">›</view>
      </view>
      <view class="item" @click="go('/pages/mine/membership')">
        <view class="item-icon">💎</view>
        <view class="item-label">{{ t('membershipTitle') }}</view>
        <view class="item-arrow">›</view>
      </view>
      <view class="item" @click="goTab('/pages/store/store?tab=shop')">
        <view class="item-icon">🛒</view>
        <view class="item-label">{{ t('shopTitle') }}</view>
        <view class="item-arrow">›</view>
      </view>
      <view class="item" @click="go('/pages/mine/announcements')">
        <view class="item-icon">📢</view>
        <view class="item-label">{{ t('announcements') }}</view>
        <view class="item-arrow">›</view>
      </view>
      <view class="item" @click="go('/pages/mine/feedback')">
        <view class="item-icon">💬</view>
        <view class="item-label">{{ t('feedback') }}</view>
        <view class="item-arrow">›</view>
      </view>
      <view class="item" @click="go('/pages/mine/settings')">
        <view class="item-icon">⚙️</view>
        <view class="item-label">{{ t('settings') }}</view>
        <view class="item-arrow">›</view>
      </view>
      <view v-if="user" class="item" @click="go('/pages/mine/profile-edit')">
        <view class="item-icon">✏️</view>
        <view class="item-label">{{ t('editProfile') }}</view>
        <view class="item-arrow">›</view>
      </view>
    </view>

    <view class="records" v-if="user">
      <view class="section-title">{{ t('creditActivity') }}</view>
      <view v-if="records.length" class="record-list">
        <view v-for="item in records" :key="item.id" class="record">
          <text class="record-remark">{{ item.remark || item.biz_type }}</text>
          <text :class="{ plus: item.delta > 0 }">{{ item.delta > 0 ? '+' : '' }}{{ item.delta }}</text>
        </view>
      </view>
      <view v-else class="empty-tip">--</view>
    </view>

    <view class="ad-reward" v-if="user">
      <button class="ad-btn" @click="watchAd">📺 {{ t('watchAd') }}</button>
    </view>
    <app-tab-bar current="mine" />
  </view>
</template>

<script>
import api from '../../utils/api.js'
import { getLocale, localeOptions, setLocale, t as translate } from '../../utils/i18n.js'
import { notifyDataChanged, APP_DATA_EVENTS } from '../../utils/app-state.js'
import { levelColor, LEVEL_MAP } from '../../../shared/utils/format.js'

export default {
  data() {
    return {
      user: null,
      records: [],
      locale: getLocale(),
      localeOptions,
      membershipLevel: 'NONE'
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
    },
    avatarInitial() {
      return (this.user?.nickname || this.user?.username || '?').slice(0, 1).toUpperCase()
    }
  },
  onShow() {
    this.refreshLocale()
    this.load()
  },
  methods: {
    async load() {
      if (!uni.getStorageSync('token')) {
        this.user = null
        this.records = []
        this.membershipLevel = 'NONE'
        return
      }
      try {
        const data = await api.points()
        this.user = data.user
        this.records = data.records || []
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
      }
      try {
        const ms = await api.membershipStatus()
        this.membershipLevel = ms.currentLevel || ms.current_level || 'NONE'
      } catch (err) {
        this.membershipLevel = 'NONE'
      }
    },
    async checkin() {
      if (!uni.getStorageSync('token')) {
        this.goLogin()
        return
      }
      try {
        this.user = await api.checkin()
        notifyDataChanged(APP_DATA_EVENTS.points)
        await this.load()
        uni.showToast({ title: this.t('rewardAdded'), icon: 'none' })
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
      }
    },
    async watchAd() {
      if (!uni.getStorageSync('token')) {
        this.goLogin()
        return
      }
      try {
        const data = await api.adReward()
        uni.showToast({ title: '+' + (data.delta || data.points || 0) + ' ' + this.t('credits'), icon: 'none' })
        notifyDataChanged(APP_DATA_EVENTS.points)
        await this.load()
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
      }
    },
    go(url) {
      if (!uni.getStorageSync('token')) {
        this.goLogin()
        return
      }
      uni.navigateTo({ url })
    },
    goTab(url) {
      uni.redirectTo({ url })
    },
    goLogin() {
      uni.navigateTo({ url: '/pages/login/login' })
    },
    logout() {
      uni.removeStorageSync('token')
      uni.removeStorageSync('refreshToken')
      uni.removeStorageSync('user')
      this.user = null
      this.records = []
      uni.redirectTo({ url: '/pages/login/login' })
    },
    refreshLocale() {
      this.locale = getLocale()
      uni.setNavigationBarTitle({ title: this.t('profile') })
    },
    changeLocale(e) {
      const item = this.localeOptions[Number(e.detail.value)]
      if (!item) return
      setLocale(item.code)
      this.refreshLocale()
    },
    levelDisplayName(level) {
      const entry = LEVEL_MAP[level] || LEVEL_MAP.NONE
      return this.t(entry.nameKey)
    },
    levelColor,
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
    radial-gradient(circle at 12% 4%, rgba(247, 198, 106, 0.16), transparent 38%),
    radial-gradient(circle at 88% 12%, rgba(77, 208, 225, 0.12), transparent 40%),
    repeating-linear-gradient(90deg, rgba(255, 255, 255, 0.035) 0 1rpx, transparent 1rpx 96rpx),
    linear-gradient(145deg, rgba(30, 116, 129, 0.2), transparent 38%),
    linear-gradient(225deg, rgba(123, 45, 56, 0.22), transparent 40%),
    #080a10;
  padding: 20rpx;
  box-sizing: border-box;
  color: #fff;
  font-family: -apple-system, BlinkMacSystemFont, "Inter", "SF Pro Display", "Segoe UI", sans-serif;
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8rpx 4rpx 16rpx;
}

.page-title {
  display: flex;
  align-items: center;
  font-size: 30rpx;
  font-weight: 800;
  letter-spacing: -1rpx;
  background: linear-gradient(135deg, #ffffff 30%, rgba(255, 255, 255, 0.78) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.page-title::before {
  content: "";
  display: inline-block;
  width: 8rpx;
  height: 34rpx;
  margin-right: 14rpx;
  background: linear-gradient(180deg, #ffe0a1, #f3b84d);
  border-radius: 5rpx;
  box-shadow: 0 3rpx 10rpx rgba(247, 198, 106, 0.5);
}

.language {
  width: 72rpx;
  height: 48rpx;
  line-height: 48rpx;
  text-align: center;
  color: #11100d;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  border-radius: 999rpx;
  font-size: 20rpx;
  font-weight: 800;
  letter-spacing: 1rpx;
  box-shadow:
    0 6rpx 16rpx rgba(247, 198, 106, 0.36),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
  transition: transform 0.18s ease;
}

.language:active {
  transform: scale(0.94);
}

.profile-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 20rpx;
  background:
    linear-gradient(135deg, rgba(247, 198, 106, 0.22), rgba(77, 208, 225, 0.1)),
    rgba(12, 15, 24, 0.85);
  border: 1rpx solid rgba(255, 255, 255, 0.18);
  border-radius: 24rpx;
  padding: 22rpx;
  margin-bottom: 16rpx;
  box-shadow:
    0 20rpx 50rpx rgba(0, 0, 0, 0.36),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(20rpx);
  overflow: hidden;
}

.profile-card::before {
  content: "";
  position: absolute;
  top: -30rpx;
  right: -30rpx;
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(247, 198, 106, 0.18), transparent 70%);
  filter: blur(16rpx);
  pointer-events: none;
}

.avatar-block {
  position: relative;
  flex-shrink: 0;
  width: 80rpx;
  height: 80rpx;
}

.avatar {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  border: 3rpx solid transparent;
  background:
    linear-gradient(#0c0f18, #0c0f18) padding-box,
    linear-gradient(135deg, #f7c66a, #4dd0e1) border-box;
  box-shadow: 0 8rpx 20rpx rgba(247, 198, 106, 0.32);
}

.avatar-placeholder {
  width: 80rpx;
  height: 80rpx;
  line-height: 80rpx;
  text-align: center;
  border-radius: 50%;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  color: #11100d;
  font-size: 30rpx;
  font-weight: 800;
  box-shadow:
    0 8rpx 20rpx rgba(247, 198, 106, 0.42),
    inset 0 2rpx 4rpx rgba(255, 255, 255, 0.5);
}

.user-info {
  flex: 1;
  min-width: 0;
  position: relative;
  z-index: 1;
}

.name {
  font-size: 28rpx;
  font-weight: 800;
  letter-spacing: -0.5rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.level-badge {
  margin-left: 10rpx;
  font-size: 20rpx;
  font-weight: 700;
  padding: 2rpx 10rpx;
  border-radius: 999rpx;
  border: 1rpx solid currentColor;
  vertical-align: middle;
  letter-spacing: 0.5rpx;
}

.points {
  margin-top: 8rpx;
  color: rgba(255, 255, 255, 0.68);
  font-size: 22rpx;
  font-weight: 600;
}

.login-btn {
  flex-shrink: 0;
  width: 130rpx;
  height: 60rpx;
  line-height: 60rpx;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  color: #11100d;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 800;
  letter-spacing: 0.5rpx;
  padding: 0;
  box-shadow:
    0 8rpx 20rpx rgba(247, 198, 106, 0.32),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
  transition: transform 0.18s ease;
}

.login-btn:active {
  transform: scale(0.95);
}

.actions {
  position: relative;
  background:
    linear-gradient(160deg, rgba(255, 255, 255, 0.14), rgba(255, 255, 255, 0.055)),
    rgba(12, 15, 24, 0.78);
  border: 1rpx solid rgba(255, 255, 255, 0.18);
  border-radius: 24rpx;
  margin-bottom: 16rpx;
  box-shadow:
    0 20rpx 50rpx rgba(0, 0, 0, 0.32),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(20rpx);
  overflow: hidden;
}

.item {
  display: flex;
  align-items: center;
  padding: 20rpx 24rpx;
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.08);
  font-size: 26rpx;
  font-weight: 700;
  letter-spacing: 0.3rpx;
  transition: all 0.2s ease;
}

.item:active {
  background: rgba(247, 198, 106, 0.08);
  padding-left: 28rpx;
}

.item:last-child {
  border-bottom: 0;
}

.item-icon {
  width: 44rpx;
  height: 44rpx;
  margin-right: 16rpx;
  line-height: 44rpx;
  font-size: 26rpx;
  text-align: center;
  border-radius: 12rpx;
  background: linear-gradient(135deg, rgba(247, 198, 106, 0.2), rgba(247, 198, 106, 0.06));
  border: 1rpx solid rgba(247, 198, 106, 0.25);
}

.item-label {
  flex: 1;
}

.item-arrow {
  color: rgba(255, 255, 255, 0.4);
  font-size: 30rpx;
  font-weight: 300;
}

.records {
  background:
    linear-gradient(160deg, rgba(255, 255, 255, 0.14), rgba(255, 255, 255, 0.055)),
    rgba(12, 15, 24, 0.78);
  border: 1rpx solid rgba(255, 255, 255, 0.18);
  border-radius: 24rpx;
  padding: 20rpx 24rpx;
  margin-bottom: 16rpx;
  box-shadow:
    0 20rpx 50rpx rgba(0, 0, 0, 0.32),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(20rpx);
}

.section-title {
  display: flex;
  align-items: center;
  font-weight: 800;
  margin-bottom: 14rpx;
  font-size: 28rpx;
  letter-spacing: 0.3rpx;
}

.section-title::before {
  content: "";
  display: inline-block;
  width: 5rpx;
  height: 24rpx;
  margin-right: 12rpx;
  background: linear-gradient(180deg, #ffe0a1, #f3b84d);
  border-radius: 3rpx;
}

.record-list {
  display: flex;
  flex-direction: column;
}

.record {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14rpx 0;
  color: rgba(255, 255, 255, 0.72);
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.06);
  font-size: 24rpx;
}

.record:last-child {
  border-bottom: 0;
}

.record-remark {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plus {
  color: #5fe5a8;
  font-weight: 800;
  font-size: 28rpx;
}

.empty-tip {
  padding: 16rpx 0;
  color: rgba(255, 255, 255, 0.32);
  text-align: center;
  font-size: 22rpx;
  font-weight: 600;
}

.product {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 16rpx 0;
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.08);
}

.product:last-child {
  border-bottom: 0;
}

.product-info {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.product-name {
  font-weight: 700;
  font-size: 28rpx;
  letter-spacing: 0.3rpx;
}

.product-points {
  color: #f7c66a;
  font-size: 24rpx;
  font-weight: 700;
}

.buy-btn {
  min-width: 180rpx;
  margin: 0;
  color: #11100d;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 800;
  letter-spacing: 0.5rpx;
  padding: 14rpx 26rpx;
  box-shadow:
    0 10rpx 24rpx rgba(247, 198, 106, 0.32),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
  transition: transform 0.18s ease;
}

.buy-btn:active {
  transform: scale(0.95);
}

.ad-reward {
  margin-top: 4rpx;
}

.ad-btn {
  position: relative;
  width: 100%;
  background: linear-gradient(135deg, rgba(77, 208, 225, 0.22), rgba(30, 116, 129, 0.32));
  border: 1rpx solid rgba(77, 208, 225, 0.4);
  color: #6fe0f0;
  border-radius: 20rpx;
  font-size: 24rpx;
  font-weight: 700;
  letter-spacing: 0.5rpx;
  padding: 18rpx;
  box-shadow:
    0 10rpx 24rpx rgba(77, 208, 225, 0.16),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(10rpx);
  transition: transform 0.18s ease;
}

.ad-btn:active {
  transform: scale(0.98);
  background: linear-gradient(135deg, rgba(77, 208, 225, 0.32), rgba(30, 116, 129, 0.42));
}
</style>
