<template>
  <view class="page">
    <view class="page-head">
      <view class="page-title">{{ t('editProfile') }}</view>
    </view>

    <view class="form-section">
      <view class="avatar-editor">
        <view class="avatar-preview" @click="selectAvatar">
          <image v-if="avatarPreview" class="avatar-img" :src="avatarPreview" mode="aspectFill" />
          <view v-else class="avatar-placeholder">{{ avatarInitial }}</view>
        </view>
        <button class="upload-btn" @click="selectAvatar">{{ t('uploadAvatar') }}</button>
      </view>

      <view class="form-item">
        <view class="form-label">{{ t('editNickname') }}</view>
        <input class="form-input" v-model="form.nickname" type="text" :maxlength="24" :placeholder="t('nicknamePlaceholder')" />
      </view>
    </view>

    <button class="save-btn" @click="saveProfile">{{ t('save') }}</button>

    <view class="divider"></view>

    <view class="page-title small">{{ t('changePassword') }}</view>
    <view class="form-section">
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
    </view>
    <button class="save-btn" @click="savePassword">{{ t('changePassword') }}</button>
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
      form: { nickname: '' },
      passwordForm: { oldPassword: '', newPassword: '', confirmPassword: '' },
      avatarPreview: ''
    }
  },
  computed: {
    avatarInitial() {
      return (this.form.nickname || '?').slice(0, 1).toUpperCase()
    }
  },
  onLoad() {
    this.loadProfile()
  },
  methods: {
    async loadProfile() {
      try {
        const data = await api.getProfile()
        this.form.nickname = data.nickname || data.username || ''
        this.avatarPreview = data.avatar_url || data.avatarUrl || ''
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
      }
    },
    selectAvatar() {
      uni.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera'],
        success: (res) => {
          const tempPath = res.tempFilePaths[0]
          this.readAsDataUrl(tempPath)
        }
      })
    },
    readAsDataUrl(path) {
      uni.getFileSystemManager().readFile({
        filePath: path,
        encoding: 'base64',
        success: (res) => {
          this.avatarPreview = 'data:image/jpeg;base64,' + res.data
        },
        fail: () => {
          this.avatarPreview = path
        }
      })
    },
    async saveProfile() {
      if (!this.form.nickname || !this.form.nickname.trim()) {
        uni.showToast({ title: this.t('nicknamePlaceholder'), icon: 'none' })
        return
      }
      try {
        const payload = { nickname: this.form.nickname.trim() }
        if (this.avatarPreview && this.avatarPreview.startsWith('data:')) {
          payload.avatarUrl = this.avatarPreview
        } else if (this.avatarPreview) {
          payload.avatarUrl = this.avatarPreview
        }
        const user = await api.updateProfile(payload)
        uni.showToast({ title: this.t('profileUpdated'), icon: 'none' })
        setTimeout(() => uni.navigateBack(), 800)
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
      }
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

.page-title.small {
  font-size: 32rpx;
  font-weight: 800;
  letter-spacing: -0.2rpx;
  margin: 20rpx 0;
  color: rgba(255, 255, 255, 0.72);
}

.form-section {
  background:
    linear-gradient(160deg, rgba(255, 255, 255, 0.13), rgba(255, 255, 255, 0.052)),
    rgba(12, 15, 24, 0.72);
  border: 1rpx solid rgba(255, 255, 255, 0.15);
  border-radius: 24rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 24rpx 70rpx rgba(0, 0, 0, 0.28);
}

.avatar-editor {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 32rpx;
}

.avatar-preview {
  width: 180rpx;
  height: 180rpx;
  border-radius: 50%;
  border: 3rpx solid #f7c66a;
  overflow: hidden;
  margin-bottom: 16rpx;
  background: rgba(255, 255, 255, 0.1);
  box-shadow:
    0 0 0 4rpx rgba(247, 198, 106, 0.18),
    0 14rpx 30rpx rgba(0, 0, 0, 0.35);
  transition: transform 0.2s ease;
}

.avatar-preview:active {
  transform: scale(0.95);
}

.avatar-img {
  width: 180rpx;
  height: 180rpx;
}

.avatar-placeholder {
  width: 180rpx;
  height: 180rpx;
  line-height: 180rpx;
  text-align: center;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  color: #11100d;
  font-size: 72rpx;
  font-weight: 900;
  letter-spacing: 0.4rpx;
}

.upload-btn {
  background: transparent;
  color: #f7c66a;
  font-size: 26rpx;
  font-weight: 700;
  letter-spacing: 0.3rpx;
  padding: 12rpx 32rpx;
  border: 1rpx solid rgba(247, 198, 106, 0.6);
  border-radius: 999rpx;
  transition: all 0.2s ease;
}

.upload-btn:active {
  transform: scale(0.96);
  background: rgba(247, 198, 106, 0.15);
  border-color: rgba(247, 198, 106, 0.9);
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

.save-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  color: #11100d;
  border-radius: 999rpx;
  font-size: 30rpx;
  font-weight: 800;
  letter-spacing: 0.5rpx;
  margin-top: 16rpx;
  margin-bottom: 16rpx;
  box-shadow:
    0 16rpx 38rpx rgba(247, 198, 106, 0.4),
    inset 0 2rpx 0 rgba(255, 255, 255, 0.5);
  transition: transform 0.18s ease;
}

.save-btn:active {
  transform: scale(0.97);
}

.divider {
  height: 1rpx;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.15), transparent);
  margin: 32rpx 0;
}
</style>
