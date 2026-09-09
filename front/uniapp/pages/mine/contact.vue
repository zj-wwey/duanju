<template>
  <view class="page">
    <view class="page-head">
      <view class="page-title">{{ t('contactUs') }}</view>
    </view>

    <view class="contact-card">
      <view class="card-icon">📧</view>
      <view class="card-info">
        <view class="card-label">{{ t('email') }}</view>
        <view class="card-value">{{ t('contactEmail') }}</view>
      </view>
      <view class="copy-btn" @click="copy(t('contactEmail'))">{{ t('copy') }}</view>
    </view>

    <view class="contact-card">
      <view class="card-icon">💬</view>
      <view class="card-info">
        <view class="card-label">QQ</view>
        <view class="card-value">{{ t('contactQQ') }}</view>
      </view>
      <view class="copy-btn" @click="copy(t('contactQQ'))">{{ t('copy') }}</view>
    </view>

    <view class="contact-card">
      <view class="card-icon">🕐</view>
      <view class="card-info">
        <view class="card-label">{{ t('serviceHours') }}</view>
        <view class="card-value">{{ t('serviceHoursValue') }}</view>
      </view>
    </view>

    <view class="tip">{{ t('contactTip') }}</view>
  </view>
</template>

<script>
import { getLocale, t as translate } from '../../utils/i18n.js'
export default {
  name: 'Contact',
  data() {
    return { locale: getLocale() }
  },
  methods: {
    t(key) { return translate(key, this.locale) },
    copy(val) {
      uni.setClipboardData({
        data: val,
        success: () => uni.showToast({ title: this.t('copied'), icon: 'none' })
      })
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
  font-size: 34rpx;
  font-weight: 800;
  letter-spacing: -0.6rpx;
}

.contact-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 16rpx;
  background:
    linear-gradient(160deg, rgba(255, 255, 255, 0.13), rgba(255, 255, 255, 0.052)),
    rgba(12, 15, 24, 0.78);
  border: 1rpx solid rgba(255, 255, 255, 0.15);
  border-radius: 24rpx;
  box-shadow: 0 24rpx 70rpx rgba(0, 0, 0, 0.28);
}

.card-icon {
  font-size: 40rpx;
  width: 56rpx;
  text-align: center;
  flex-shrink: 0;
}

.card-info {
  flex: 1;
  min-width: 0;
}

.card-label {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.5);
  margin-bottom: 4rpx;
}

.card-value {
  font-size: 28rpx;
  font-weight: 600;
  color: #fff;
}

.copy-btn {
  padding: 10rpx 22rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  color: #1a1005;
  font-size: 22rpx;
  font-weight: 700;
  box-shadow: 0 6rpx 16rpx rgba(247, 198, 106, 0.36);
}

.tip {
  margin-top: 48rpx;
  text-align: center;
  color: rgba(255, 255, 255, 0.4);
  font-size: 24rpx;
  line-height: 1.6;
}
</style>
