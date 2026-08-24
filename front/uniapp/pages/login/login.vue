<template>
  <view class="page">
    <scroll-view scroll-y class="login-scroll">
      <view class="login-content" :class="{ registering: mode === 'register' }">
        <picker :range="localeNames" :value="localeIndex" @change="changeLocale">
          <view class="language">{{ currentLocaleShort }}</view>
        </picker>
        <view class="brand">
          <view class="brand-mark">SD</view>
          <view class="brand-copy">
            <view class="eyebrow">{{ t('loginEyebrow') }}</view>
            <view class="title">{{ t('loginTitle') }}</view>
          </view>
        </view>
        <view class="panel">
          <view class="mode-tabs">
            <view class="mode-tab" :class="{ active: mode === 'login' }" @click="switchMode('login')">{{ t('signIn') }}</view>
            <view class="mode-tab" :class="{ active: mode === 'register' }" @click="switchMode('register')">{{ t('createAccount') }}</view>
          </view>
          <view class="field-group">
            <view class="field-label"><text class="required-mark">*</text>{{ t('accountLabel') }}</view>
            <input v-model.trim="form.account" class="input" :placeholder="t('accountPlaceholder')" />
          </view>
          <view class="field-group">
            <view class="field-label"><text class="required-mark">*</text>{{ t('passwordLabel') }}</view>
            <input v-model="form.password" class="input" password :placeholder="t('passwordPlaceholder')" />
          </view>
          <view v-if="mode === 'register'" class="field-group">
            <view class="field-label">{{ t('nicknameLabel') }}</view>
            <input v-model.trim="form.nickname" class="input" :placeholder="t('nicknamePlaceholder')" />
          </view>
          <view class="field-group">
            <view class="field-label"><text class="required-mark">*</text>{{ t('captchaLabel') }}</view>
            <view class="captcha-row">
              <input v-model.trim="form.captchaCode" class="input captcha-input" :placeholder="t('captchaPlaceholder')" />
              <view class="captcha-box" @click="loadCaptcha">
                <image v-if="captchaImage" class="captcha-image" :src="captchaImage" mode="aspectFill" />
                <view v-else class="captcha-spinner"></view>
              </view>
            </view>
          </view>
          <button class="primary" :loading="loading" :disabled="loading" @click="submit">
            {{ mode === 'login' ? t('signIn') : t('createAccount') }}
          </button>
          <view class="switch" @click="switchMode(mode === 'login' ? 'register' : 'login')">
            {{ mode === 'login' ? t('newAccount') : t('existingAccount') }}
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import api from '../../utils/api.js'
import { getLocale, localeOptions, setLocale, t as translate } from '../../utils/i18n.js'
import { validatePassword, validateUsername } from '../../../shared/utils/validate.js'

export default {
  data() {
    return {
      mode: 'login',
      form: {
        account: '',
        password: '',
        nickname: '',
        captchaId: '',
        captchaCode: ''
      },
      captchaImage: '',
      loading: false,
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
    this.loadCaptcha()
  },
  methods: {
    async loadCaptcha() {
      try {
        const data = await api.captcha(this.mode === 'register' ? 'REGISTER' : 'LOGIN')
        this.form.captchaId = data.captchaId
        this.captchaImage = data.imageData
      } catch (err) {
        this.toast(err.message)
      }
    },
    switchMode(next) {
      this.mode = next
      this.form.captchaCode = ''
      this.loadCaptcha()
    },
    async submit() {
      const account = this.form.account.trim()
      if (!account || !this.form.password || !this.form.captchaCode) {
        this.toast(this.t('requiredFields'))
        return
      }
      if (this.mode === 'register') {
        if (!validateUsername(account)) {
          this.toast(this.t('usernameInvalid'))
          return
        }
        if (!validatePassword(this.form.password)) {
          this.toast(this.t('passwordInvalid'))
          return
        }
      }
      if (this.loading) return
      this.loading = true
      try {
        const payload = this.mode === 'login'
          ? { username: account, password: this.form.password, captchaId: this.form.captchaId, captchaCode: this.form.captchaCode }
          : { username: account, password: this.form.password, nickname: this.form.nickname.trim(), captchaId: this.form.captchaId, captchaCode: this.form.captchaCode }
        const res = this.mode === 'login' ? await api.login(payload) : await api.register(payload)
        if (this.mode === 'register') {
          this.toast(this.t('registered'))
          this.form.password = ''
          this.form.nickname = ''
          this.switchMode('login')
          return
        }
        if (res.role !== 'USER' || !res.user) {
          throw new Error(this.t('adminLoginOnly'))
        }
        uni.setStorageSync('token', res.token)
        uni.setStorageSync('refreshToken', res.refreshToken)
        uni.setStorageSync('user', res.user)
        uni.showToast({ title: this.t('signedIn'), icon: 'none' })
        setTimeout(() => this.afterSignedIn(), 500)
      } catch (err) {
        this.loadCaptcha()
        this.toast(err.message)
      } finally {
        this.loading = false
      }
    },
    afterSignedIn() {
      const pages = getCurrentPages()
      const previous = pages.length > 1 ? pages[pages.length - 2] : null
      if (previous && previous.route !== 'pages/login/login') {
        uni.navigateBack()
        return
      }
      uni.redirectTo({ url: '/pages/index/index' })
    },
    refreshLocale() {
      this.locale = getLocale()
      uni.setNavigationBarTitle({ title: this.t('signIn') })
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
    toast(title) {
      uni.showToast({ title, icon: 'none' })
    }
  }
}
</script>

<style>
page,
.page {
  position: relative;
  height: 100vh;
  background: linear-gradient(160deg, #0a0c14 0%, #0d1018 40%, #07090f 100%);
  color: #fff;
  box-sizing: border-box;
  font-family: -apple-system, BlinkMacSystemFont, "Inter", "SF Pro Display", "Segoe UI", sans-serif;
  overflow: hidden;
}

.login-scroll {
  position: relative;
  z-index: 1;
  height: 100vh;
  padding: 0;
  box-sizing: border-box;
}

.login-content {
  min-height: 100vh;
  padding: 20rpx 24rpx calc(24rpx + env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  justify-content: center;
  box-sizing: border-box;
}

.language {
  position: absolute;
  top: 18rpx;
  right: 18rpx;
  z-index: 2;
  width: 48rpx;
  height: 32rpx;
  line-height: 32rpx;
  text-align: center;
  color: #11100d;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  border-radius: 999rpx;
  font-size: 16rpx;
  font-weight: 800;
  box-shadow: 0 4rpx 14rpx rgba(247, 198, 106, 0.3);
}

.language:active {
  transform: scale(0.94);
  transition: transform 0.18s ease;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin: 0 0 18rpx;
}

.brand-mark {
  flex: 0 0 auto;
  width: 56rpx;
  height: 56rpx;
  line-height: 56rpx;
  text-align: center;
  color: #11100d;
  background: linear-gradient(135deg, #ffe4a8, #f4b846);
  border-radius: 16rpx;
  font-size: 22rpx;
  font-weight: 900;
  letter-spacing: 0.5rpx;
  box-shadow: 0 6rpx 18rpx rgba(247, 198, 106, 0.28);
}

.brand-copy {
  flex: 1;
  min-width: 0;
}

.eyebrow {
  color: #f7c66a;
  font-size: 18rpx;
  font-weight: 800;
  letter-spacing: 3rpx;
  text-transform: uppercase;
  margin-bottom: 2rpx;
}

.title {
  font-size: 32rpx;
  font-weight: 800;
  line-height: 1.15;
  letter-spacing: -0.5rpx;
  background: linear-gradient(135deg, #ffffff 30%, rgba(255, 255, 255, 0.78) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.panel {
  position: relative;
  background: rgba(14, 17, 26, 0.72);
  border: 1rpx solid rgba(255, 255, 255, 0.12);
  border-radius: 20rpx;
  padding: 20rpx 24rpx;
  box-shadow: 0 10rpx 28rpx rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(24rpx);
}

.mode-tabs {
  display: flex;
  height: 40rpx;
  padding: 3rpx;
  margin-bottom: 16rpx;
  border-radius: 12rpx;
  background: rgba(255, 255, 255, 0.06);
}

.mode-tab {
  flex: 1;
  height: 34rpx;
  line-height: 34rpx;
  text-align: center;
  color: rgba(255, 255, 255, 0.55);
  border-radius: 9rpx;
  font-size: 18rpx;
  font-weight: 800;
  transition: all 0.25s ease;
}

.mode-tab.active {
  color: #11100d;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  box-shadow: 0 4rpx 14rpx rgba(247, 198, 106, 0.3);
}

.mode-tab:active {
  transform: scale(0.97);
}

.field-group {
  margin-bottom: 14rpx;
}

.field-group:last-of-type {
  margin-bottom: 0;
}

.field-label {
  display: flex;
  align-items: center;
  margin: 0 0 6rpx 4rpx;
  color: rgba(255, 255, 255, 0.72);
  font-size: 16rpx;
  font-weight: 700;
  letter-spacing: 0.5rpx;
}

.required-mark {
  margin-right: 4rpx;
  color: #ff7a7a;
  font-size: 18rpx;
}

.input {
  height: 52rpx;
  padding: 0 14rpx;
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
  border-radius: 10rpx;
  border: 1px solid rgba(255, 255, 255, 0.1);
  font-size: 20rpx;
  transition: all 0.2s ease;
}

.input:focus {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(247, 198, 106, 0.45);
}

.captcha-row {
  display: flex;
  gap: 10rpx;
  align-items: center;
}

.captcha-input {
  flex: 1;
}

.captcha-box {
  width: 120rpx;
  height: 52rpx;
  border-radius: 10rpx;
  background: rgba(255, 255, 255, 0.08);
  border: 1rpx solid rgba(255, 255, 255, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
}

.captcha-image {
  width: 100%;
  height: 100%;
  border-radius: 10rpx;
}

.captcha-spinner {
  width: 22rpx;
  height: 22rpx;
  border: 3rpx solid rgba(247, 198, 106, 0.25);
  border-top-color: #f7c66a;
  border-radius: 50%;
  animation: captcha-spin 0.8s linear infinite;
}

@keyframes captcha-spin {
  to { transform: rotate(360deg); }
}

.primary {
  height: 52rpx;
  line-height: 52rpx;
  margin-top: 10rpx;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  color: #11100d;
  border-radius: 999rpx;
  font-weight: 800;
  font-size: 20rpx;
  letter-spacing: 0.5rpx;
  box-shadow: 0 8rpx 22rpx rgba(247, 198, 106, 0.32);
  transition: all 0.2s ease;
}

.primary:active {
  transform: scale(0.97);
  box-shadow: 0 6rpx 16rpx rgba(247, 198, 106, 0.28);
}

.primary[disabled] {
  opacity: 0.6;
}

.switch {
  margin-top: 8rpx;
  text-align: center;
  color: rgba(255, 255, 255, 0.6);
  font-size: 17rpx;
  font-weight: 600;
}

.switch:active {
  color: #f7c66a;
}

.login-content.registering .brand {
  margin-bottom: 14rpx;
}

.login-content.registering .field-group {
  margin-bottom: 10rpx;
}

@media screen and (max-height: 680px) {
  .login-content {
    justify-content: flex-start;
    padding-top: 28rpx;
  }

  .brand {
    margin-bottom: 14rpx;
  }

  .field-group {
    margin-bottom: 10rpx;
  }
}
</style>
