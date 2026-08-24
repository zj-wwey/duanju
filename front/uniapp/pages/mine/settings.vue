<template>
  <view class="page">
    <view class="page-head">
      <view class="page-title">{{ t('settings') }}</view>
    </view>

    <view class="section">
      <view class="section-title">{{ t('accountSettings') }}</view>
      <view class="setting-item" @click="go('/pages/mine/profile-edit')">
        <view class="setting-icon">✏️</view>
        <view class="setting-label">{{ t('editProfile') }}</view>
        <view class="setting-arrow">›</view>
      </view>
      <view class="setting-item" @click="changePasswordPanel = true">
        <view class="setting-icon">🔒</view>
        <view class="setting-label">{{ t('changePassword') }}</view>
        <view class="setting-arrow">›</view>
      </view>
    </view>

    <view class="section">
      <view class="section-title">{{ t('playbackSettings') }}</view>
      <view class="setting-item">
        <view class="setting-icon">🔔</view>
        <view class="setting-label">{{ t('notifications') }}</view>
        <switch :checked="settings.notice" @change="toggleSetting('notice', $event)" color="#f7c66a" />
      </view>
      <view class="setting-item">
        <view class="setting-icon">▶️</view>
        <view class="setting-label">{{ t('autoPlay') }}</view>
        <switch :checked="settings.autoNext" @change="toggleSetting('autoNext', $event)" color="#f7c66a" />
      </view>
    </view>

    <view class="section password-panel" v-if="changePasswordPanel">
      <view class="section-title">{{ t('changePassword') }}</view>
      <view class="form-item">
        <view class="form-label">{{ t('oldPassword') }}</view>
        <input class="form-input" v-model="passwordForm.oldPassword" type="password" :placeholder="t('oldPassword')" />
      </view>
      <view class="form-item">
        <view class="form-label">{{ t('newPassword') }}</view>
        <input class="form-input" v-model="passwordForm.newPassword" type="password" :placeholder="t('newPassword')" />
      </view>
      <view class="form-item">
        <view class="form-label">{{ t('confirmPassword') }}</view>
        <input class="form-input" v-model="passwordForm.confirmPassword" type="password" :placeholder="t('confirmPassword')" />
      </view>
      <view class="btn-row">
        <button class="btn-cancel" @click="changePasswordPanel = false">{{ t('cancel') }}</button>
        <button class="btn-save" @click="savePassword">{{ t('changePassword') }}</button>
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
      locale: getLocale(),
      settings: { notice: true, autoNext: true },
      passwordForm: { oldPassword: '', newPassword: '', confirmPassword: '' },
      changePasswordPanel: false
    }
  },
  onShow() {
    this.loadSettings()
  },
  methods: {
    async loadSettings() {
      try {
        const data = await api.getSettings()
        this.settings.notice = data.notice !== false
        this.settings.autoNext = data.autoNext !== false
      } catch (err) {
        // Use defaults if API fails
      }
    },
    async toggleSetting(key, e) {
      this.settings[key] = e.detail.value
      try {
        await api.updateSettings({
          notice: this.settings.notice,
          autoNext: this.settings.autoNext
        })
        uni.showToast({ title: this.t('settingsUpdated'), icon: 'none' })
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
        // Revert on failure
        this.settings[key] = !e.detail.value
      }
    },
    go(url) {
      uni.navigateTo({ url })
    },
    async savePassword() {
      if (!this.passwordForm.oldPassword || !this.passwordForm.newPassword || !this.passwordForm.confirmPassword) {
        uni.showToast({ title: this.t('requiredFields'), icon: 'none' })
        return
      }
      if (this.passwordForm.newPassword !== this.passwordForm.confirmPassword) {
        uni.showToast({ title: this.t('passwordMismatch'), icon: 'none' })
        return
      }
      try {
        await api.updatePassword({
          oldPassword: this.passwordForm.oldPassword,
          newPassword: this.passwordForm.newPassword
        })
        uni.showToast({ title: this.t('passwordUpdated'), icon: 'none' })
        this.passwordForm = { oldPassword: '', newPassword: '', confirmPassword: '' }
        this.changePasswordPanel = false
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
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

.section {
  background:
    linear-gradient(160deg, rgba(255, 255, 255, 0.13), rgba(255, 255, 255, 0.052)),
    rgba(12, 15, 24, 0.72);
  border: 1rpx solid rgba(255, 255, 255, 0.15);
  border-radius: 24rpx;
  padding: 28rpx 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 24rpx 70rpx rgba(0, 0, 0, 0.28);
}

.section-title {
  font-weight: 800;
  letter-spacing: 0.2rpx;
  margin-bottom: 18rpx;
  font-size: 26rpx;
  text-transform: uppercase;
  color: rgba(247, 198, 106, 0.78);
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.section-title::before {
  content: '';
  width: 5rpx;
  height: 22rpx;
  border-radius: 3rpx;
  background: linear-gradient(180deg, #ffe0a1, #f3b84d);
  box-shadow: 0 0 8rpx rgba(247, 198, 106, 0.5);
}

.setting-item {
  display: flex;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.08);
  transition: opacity 0.2s ease;
}

.setting-item:last-child {
  border-bottom: 0;
}

.setting-item:active {
  opacity: 0.7;
}

.setting-icon {
  width: 48rpx;
  margin-right: 20rpx;
  font-size: 36rpx;
  text-align: center;
}

.setting-label {
  flex: 1;
  font-size: 30rpx;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.92);
}

.setting-arrow {
  color: rgba(255, 255, 255, 0.4);
  font-size: 32rpx;
  font-weight: 300;
}

.form-item {
  margin-bottom: 24rpx;
}

.form-item:last-child {
  margin-bottom: 0;
}

.form-label {
  font-size: 26rpx;
  font-weight: 700;
  letter-spacing: 0.3rpx;
  color: rgba(255, 255, 255, 0.6);
  margin-bottom: 12rpx;
}

.form-input {
  width: 100%;
  height: 80rpx;
  padding: 0 24rpx;
  background: rgba(255, 255, 255, 0.08);
  border: 1rpx solid rgba(255, 255, 255, 0.15);
  border-radius: 18rpx;
  color: #fff;
  font-size: 28rpx;
  box-sizing: border-box;
  transition: border-color 0.2s ease;
}

.btn-row {
  display: flex;
  gap: 24rpx;
  margin-top: 24rpx;
}

.btn-cancel {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  background: rgba(255, 255, 255, 0.1);
  border: 1rpx solid rgba(255, 255, 255, 0.14);
  color: rgba(255, 255, 255, 0.72);
  border-radius: 999rpx;
  font-size: 28rpx;
  font-weight: 700;
  transition: transform 0.18s ease;
}

.btn-cancel:active {
  transform: scale(0.97);
}

.btn-save {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  color: #11100d;
  border-radius: 999rpx;
  font-size: 28rpx;
  font-weight: 800;
  letter-spacing: 0.4rpx;
  box-shadow:
    0 12rpx 28rpx rgba(247, 198, 106, 0.36),
    inset 0 2rpx 0 rgba(255, 255, 255, 0.5);
  transition: transform 0.18s ease;
}

.btn-save:active {
  transform: scale(0.97);
}
</style>
