<template>
  <view class="page">
    <!-- 背景图片（移动端竖屏比例 · 短剧电影氛围感） -->
    <image
      class="bg-image"
      src="https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=cinematic%20movie%20theater%20stage%20spotlight%20with%20golden%20film%20reel%20curtain%20dramatic%20lighting%20dark%20purple%20and%20amber%20gradient%20bokeh%20blur%20short%20drama%20app%20login%20background%20atmospheric%20no%20text&image_size=portrait_16_9"
      mode="aspectFill"
    />
    <!-- 图片暗色遮罩：保证文字可读 + 统一色调 -->
    <view class="bg-mask" />
    <!-- 背景光斑（叠在遮罩上，保留氛围） -->
    <view class="bg-decor bg-decor-1" />
    <view class="bg-decor bg-decor-2" />
    <view class="bg-decor bg-decor-3" />
    <scroll-view scroll-y class="login-scroll">
      <view class="login-content" :class="{ registering: mode === 'register' }">
        <picker :range="localeNames" :value="localeIndex" @change="changeLocale">
          <view class="language">{{ currentLocaleShort }}</view>
        </picker>

        <view class="brand-hero enter-anim enter-1">
          <view class="hero-logo">
            <view class="hero-logo-inner">
              <text class="hero-logo-text">SD</text>
              <!-- Logo 表面扫光 -->
              <view class="hero-shine" />
            </view>
            <view class="hero-glow" />
            <!-- Logo 外发光双层环 -->
            <view class="hero-ring hero-ring-1" />
            <view class="hero-ring hero-ring-2" />
          </view>
          <view class="hero-eyebrow">{{ t('loginEyebrow') }}</view>
          <view class="hero-title">
            <text class="hero-title-text">{{ t('loginTitle') }}</text>
          </view>
          <view class="hero-sub">{{ heroSubtitle }}</view>
        </view>

        <view class="panel enter-anim enter-2">
          <!-- 面板顶部细金边 + 角标 -->
          <view class="panel-trim" />
          <view class="panel-corner panel-corner-tl" />
          <view class="panel-corner panel-corner-tr" />
          <view class="panel-corner panel-corner-bl" />
          <view class="panel-corner panel-corner-br" />

          <view class="mode-tabs">
            <view class="mode-tab" :class="{ active: mode === 'login' }" @click="switchMode('login')">
              <text class="mode-tab-text">{{ t('signIn') }}</text>
            </view>
            <view class="mode-tab" :class="{ active: mode === 'register' }" @click="switchMode('register')">
              <text class="mode-tab-text">{{ t('createAccount') }}</text>
            </view>
            <view class="mode-indicator" :style="indicatorStyle" />
          </view>

          <view class="form-space" />

          <view class="field-group">
            <view class="field-icon-wrap field-icon-wrap-user">
              <view class="field-icon-bg" />
              <text class="field-icon">👤</text>
            </view>
            <view class="field-input-wrap">
              <view class="field-label-row">
                <text class="required-mark">*</text>
                <text class="field-label">{{ t('accountLabel') }}</text>
              </view>
              <input v-model.trim="form.account" class="input" :placeholder="t('accountPlaceholder')" placeholder-class="ph" />
            </view>
          </view>

          <view class="field-group">
            <view class="field-icon-wrap field-icon-wrap-pwd">
              <view class="field-icon-bg" />
              <text class="field-icon">🔒</text>
            </view>
            <view class="field-input-wrap">
              <view class="field-label-row">
                <text class="required-mark">*</text>
                <text class="field-label">{{ t('passwordLabel') }}</text>
              </view>
              <view class="pwd-wrap">
                <input v-model="form.password" class="input pwd-input" :password="!showPwd" :placeholder="t('passwordPlaceholder')" placeholder-class="ph" />
                <view class="pwd-eye" @click="showPwd = !showPwd">
                  <text class="pwd-eye-text">{{ showPwd ? '🙈' : '👁️' }}</text>
                </view>
              </view>
            </view>
          </view>

          <view v-if="mode === 'register'" class="field-group">
            <view class="field-icon-wrap field-icon-wrap-name">
              <view class="field-icon-bg" />
              <text class="field-icon">💬</text>
            </view>
            <view class="field-input-wrap">
              <view class="field-label-row">
                <text class="field-label">{{ t('nicknameLabel') }}</text>
              </view>
              <input v-model.trim="form.nickname" class="input" :placeholder="t('nicknamePlaceholder')" placeholder-class="ph" />
            </view>
          </view>

          <view class="field-group captcha-group">
            <view class="field-icon-wrap field-icon-wrap-cap">
              <view class="field-icon-bg" />
              <text class="field-icon">🛡️</text>
            </view>
            <view class="field-input-wrap">
              <view class="field-label-row">
                <text class="required-mark">*</text>
                <text class="field-label">{{ t('captchaLabel') }}</text>
              </view>
              <view class="captcha-row">
                <input v-model.trim="form.captchaCode" class="input captcha-input" :placeholder="t('captchaPlaceholder')" placeholder-class="ph" />
                <view class="captcha-box" @click="loadCaptcha">
                  <image v-if="captchaImage" class="captcha-image" :src="captchaImage" mode="aspectFill" />
                  <view v-else class="captcha-spinner" />
                </view>
              </view>
            </view>
          </view>

          <view class="btn-area">
            <button class="primary" :loading="loading" :disabled="loading" @click="submit">
              <text v-if="!loading" class="primary-text">{{ mode === 'login' ? t('signIn') : t('createAccount') }}</text>
              <!-- 按钮扫光 -->
              <view v-if="!loading" class="primary-shimmer" />
            </button>
          </view>

          <view class="switch-line" @click="switchMode(mode === 'login' ? 'register' : 'login')">
            <view class="switch-line-bar" />
            <text class="switch-link">{{ mode === 'login' ? t('newAccount') : t('existingAccount') }}</text>
            <view class="switch-line-bar" />
          </view>
        </view>

        <view class="foot-tip enter-anim enter-3">
          <text class="foot-text">{{ t('loginFooterTip') || '' }}</text>
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
      showPwd: false,
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
    },
    indicatorStyle() {
      const left = this.mode === 'login' ? '0%' : '50%'
      return `left: ${left};`
    },
    heroSubtitle() {
      // 翻译缺失时 i18n 会返回 key 本身（如 "loginSubtitle"），此时用 signInCreate 兜底
      const raw = this.t('loginSubtitle')
      if (raw && raw !== 'loginSubtitle') return raw
      return this.t('signInCreate')
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
      this.showPwd = false
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
  background: #0a0c14;
  color: #fff;
  box-sizing: border-box;
  font-family: -apple-system, BlinkMacSystemFont, "Inter", "SF Pro Display", "Segoe UI", sans-serif;
  overflow: hidden;
}

/* ============ 背景图片 + 遮罩 ============ */
.bg-image {
  position: absolute;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  z-index: 0;
  pointer-events: none;
  animation: bg-zoom 18s ease-in-out infinite alternate;
  transform-origin: center;
}
@keyframes bg-zoom {
  0%   { transform: scale(1); }
  100% { transform: scale(1.06); }
}
.bg-mask {
  position: absolute;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  z-index: 1;
  pointer-events: none;
  /* 顶部稍亮让Logo可读，中部稍深保证面板对比，底部渐变过渡 */
  background:
    linear-gradient(180deg,
      rgba(10, 12, 20, 0.48) 0%,
      rgba(10, 12, 20, 0.7) 32%,
      rgba(10, 12, 20, 0.86) 65%,
      rgba(7, 9, 15, 0.96) 100%);
}

/* ============ 背景装饰层（纯装饰，不拦截点击 + 缓慢浮动） ============ */
.bg-decor {
  position: absolute;
  z-index: 2;
  pointer-events: none;
  border-radius: 50%;
  filter: blur(80rpx);
  opacity: 0.55;
  will-change: transform;
}
.bg-decor-1 {
  width: 520rpx;
  height: 520rpx;
  top: -180rpx;
  right: -160rpx;
  background: radial-gradient(circle, rgba(247,198,106,0.6) 0%, rgba(247,198,106,0) 70%);
  animation: float-a 14s ease-in-out infinite;
}
.bg-decor-2 {
  width: 460rpx;
  height: 460rpx;
  bottom: -120rpx;
  left: -140rpx;
  background: radial-gradient(circle, rgba(120,100,255,0.38) 0%, rgba(120,100,255,0) 72%);
  animation: float-b 16s ease-in-out infinite;
}
.bg-decor-3 {
  width: 380rpx;
  height: 380rpx;
  top: 40%;
  left: 40%;
  background: radial-gradient(circle, rgba(255,170,130,0.22) 0%, rgba(255,150,120,0) 70%);
  animation: float-c 20s ease-in-out infinite;
}
@keyframes float-a {
  0%,100% { transform: translate(0,0); }
  50%     { transform: translate(-20rpx, 18rpx); }
}
@keyframes float-b {
  0%,100% { transform: translate(0,0); }
  50%     { transform: translate(24rpx, -20rpx); }
}
@keyframes float-c {
  0%,100% { transform: translate(0,0) scale(1); }
  50%     { transform: translate(-16rpx, 14rpx) scale(1.08); }
}

.login-scroll {
  position: relative;
  z-index: 5;
  height: 100vh;
  padding: 0;
  box-sizing: border-box;
}

.login-content {
  position: relative;
  min-height: 100vh;
  padding: 20rpx 32rpx calc(40rpx + env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  justify-content: center;
  box-sizing: border-box;
}

/* ============ 入场动画（Hero 1 → Panel 2 → Foot 3） ============ */
.enter-anim {
  opacity: 0;
  transform: translateY(24rpx);
  animation: enter 0.7s cubic-bezier(0.2, 0.8, 0.2, 1) forwards;
}
.enter-1 { animation-delay: 0.05s; }
.enter-2 { animation-delay: 0.22s; }
.enter-3 { animation-delay: 0.4s; }
@keyframes enter {
  to { opacity: 1; transform: translateY(0); }
}

/* ============ 语言切换 ============ */
.language {
  position: absolute;
  top: 18rpx;
  right: 0;
  z-index: 3;
  padding: 8rpx 22rpx;
  text-align: center;
  color: #11100d;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  border-radius: 999rpx;
  font-size: 20rpx;
  font-weight: 800;
  box-shadow:
    0 6rpx 18rpx rgba(247, 198, 106, 0.32),
    inset 0 1rpx 0 rgba(255,255,255,0.6);
  letter-spacing: 0.5rpx;
}
.language:active {
  transform: scale(0.94);
  transition: transform 0.18s ease;
}

/* ============ Hero 品牌区 ============ */
.brand-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  margin: 20rpx 0 30rpx;
  padding: 10rpx 0;
}
.hero-logo {
  position: relative;
  width: 160rpx;
  height: 160rpx;
  margin-bottom: 26rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.hero-logo-inner {
  position: relative;
  z-index: 3;
  width: 128rpx;
  height: 128rpx;
  border-radius: 36rpx;
  overflow: hidden;
  background:
    linear-gradient(160deg, #fff4cf 0%, #ffd989 28%, #f4b846 60%, #d99426 85%, #b97619 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow:
    0 18rpx 44rpx rgba(247, 198, 106, 0.5),
    0 0 0 2rpx rgba(255, 243, 207, 0.35),
    inset 0 3rpx 6rpx rgba(255, 255, 255, 0.7),
    inset 0 -6rpx 12rpx rgba(160, 100, 15, 0.3);
}
.hero-logo-text {
  position: relative;
  z-index: 2;
  color: #1a1206;
  font-size: 52rpx;
  font-weight: 900;
  letter-spacing: 2rpx;
  text-shadow: 0 1rpx 0 rgba(255,255,255,0.35);
}
/* Logo 表面扫光（像金属/玻璃反光斜扫） */
.hero-shine {
  position: absolute;
  top: -60%;
  left: -60%;
  width: 60%;
  height: 220%;
  background: linear-gradient(115deg,
    rgba(255,255,255,0) 30%,
    rgba(255,255,255,0.55) 50%,
    rgba(255,255,255,0) 70%);
  transform: rotate(18deg);
  animation: shine 3.6s ease-in-out infinite;
  z-index: 4;
  pointer-events: none;
}
@keyframes shine {
  0%   { left: -60%; }
  55%  { left: 120%; }
  100% { left: 120%; }
}
.hero-glow {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 200rpx;
  height: 200rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(247,198,106,0.5) 0%, rgba(247,198,106,0) 70%);
  filter: blur(24rpx);
  z-index: 1;
  animation: hero-pulse 3.2s ease-in-out infinite;
}
/* Logo 外双环（缓慢呼吸+扩散） */
.hero-ring {
  position: absolute;
  top: 50%;
  left: 50%;
  border-radius: 50%;
  border: 1rpx solid rgba(247, 198, 106, 0.45);
  transform: translate(-50%, -50%);
  pointer-events: none;
}
.hero-ring-1 {
  width: 140rpx;
  height: 140rpx;
  animation: ring-pulse 3.2s ease-in-out infinite;
  z-index: 2;
}
.hero-ring-2 {
  width: 170rpx;
  height: 170rpx;
  border-color: rgba(247, 198, 106, 0.22);
  animation: ring-pulse 3.2s ease-in-out infinite 0.6s;
  z-index: 2;
}
@keyframes hero-pulse {
  0%, 100% { transform: translate(-50%, -50%) scale(1); opacity: 0.85; }
  50% { transform: translate(-50%, -50%) scale(1.15); opacity: 1; }
}
@keyframes ring-pulse {
  0%,100% { transform: translate(-50%,-50%) scale(0.96); opacity: 0.85; }
  50%     { transform: translate(-50%,-50%) scale(1.08); opacity: 0.35; }
}

.hero-eyebrow {
  color: #ffd98a;
  font-size: 24rpx;
  font-weight: 800;
  letter-spacing: 8rpx;
  text-transform: uppercase;
  margin-bottom: 10rpx;
  text-shadow: 0 0 16rpx rgba(247,198,106,0.4);
}
.hero-title {
  position: relative;
  margin-bottom: 12rpx;
}
.hero-title-text {
  position: relative;
  font-size: 58rpx;
  font-weight: 900;
  line-height: 1.15;
  letter-spacing: -0.5rpx;
  text-align: center;
  background: linear-gradient(180deg,
    #ffffff 0%,
    #fff5d6 45%,
    #ffd98a 75%,
    #e7ac4b 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  text-shadow: 0 0 30rpx rgba(247,198,106,0.22);
  filter: drop-shadow(0 4rpx 10rpx rgba(0,0,0,0.5));
}
.hero-sub {
  color: rgba(255, 255, 255, 0.6);
  font-size: 26rpx;
  font-weight: 500;
  letter-spacing: 1rpx;
  text-align: center;
  background: linear-gradient(90deg,
    rgba(255,255,255,0.45),
    rgba(255, 218, 150, 0.78),
    rgba(255,255,255,0.45));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  opacity: 0.9;
}

/* ============ 面板 ============ */
.panel {
  position: relative;
  z-index: 2;
  background:
    linear-gradient(180deg,
      rgba(26, 30, 46, 0.88) 0%,
      rgba(16, 18, 30, 0.88) 45%,
      rgba(12, 14, 24, 0.9) 100%);
  border: 1rpx solid rgba(255, 220, 160, 0.18);
  border-radius: 36rpx;
  padding: 32rpx 30rpx 36rpx;
  overflow: hidden;
  box-shadow:
    0 24rpx 72rpx rgba(0, 0, 0, 0.6),
    0 0 0 1rpx rgba(255,255,255,0.03),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(32rpx) saturate(135%);
  -webkit-backdrop-filter: blur(32rpx) saturate(135%);
}
/* 面板顶部细金边 */
.panel-trim {
  position: absolute;
  top: 0;
  left: 12%;
  right: 12%;
  height: 2rpx;
  background: linear-gradient(90deg,
    rgba(247,198,106,0) 0%,
    rgba(247,198,106,0.7) 50%,
    rgba(247,198,106,0) 100%);
  border-radius: 2rpx;
  pointer-events: none;
}
/* 面板角括号（电影胶片感四角） */
.panel-corner {
  position: absolute;
  width: 20rpx;
  height: 20rpx;
  border: 2rpx solid rgba(247, 198, 106, 0.55);
  pointer-events: none;
}
.panel-corner-tl { top: 10rpx; left: 10rpx;  border-right: none; border-bottom: none; border-top-left-radius: 10rpx; }
.panel-corner-tr { top: 10rpx; right: 10rpx; border-left: none;  border-bottom: none; border-top-right-radius: 10rpx; }
.panel-corner-bl { bottom: 10rpx; left: 10rpx;  border-right: none; border-top: none; border-bottom-left-radius: 10rpx; }
.panel-corner-br { bottom: 10rpx; right: 10rpx; border-left: none;  border-top: none; border-bottom-right-radius: 10rpx; }

/* ============ 模式切换 Tab ============ */
.mode-tabs {
  position: relative;
  display: flex;
  padding: 6rpx;
  border-radius: 22rpx;
  background: linear-gradient(180deg,
    rgba(255, 255, 255, 0.06) 0%,
    rgba(255, 255, 255, 0.025) 100%);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
  box-shadow: inset 0 1rpx 0 rgba(255,255,255,0.05);
}
.mode-tab {
  position: relative;
  z-index: 2;
  flex: 1;
  height: 76rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.48);
  font-size: 28rpx;
  font-weight: 700;
  transition: color 0.28s ease;
}
.mode-tab.active {
  color: #1a1206;
}
.mode-tab-text {
  letter-spacing: 1.5rpx;
}
.mode-indicator {
  position: absolute;
  top: 6rpx;
  bottom: 6rpx;
  width: calc(50% - 6rpx);
  border-radius: 18rpx;
  background:
    linear-gradient(160deg, #fff4cf 0%, #ffd989 28%, #f4b846 62%, #d99426 85%, #b97619 100%);
  box-shadow:
    0 10rpx 28rpx rgba(247, 198, 106, 0.5),
    0 0 0 1rpx rgba(255, 240, 185, 0.45),
    inset 0 2rpx 4rpx rgba(255, 255, 255, 0.6),
    inset 0 -3rpx 7rpx rgba(160, 100, 15, 0.28);
  transition: left 0.34s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 1;
}

.form-space {
  height: 26rpx;
}

/* ============ 输入框组 ============ */
.field-group {
  display: flex;
  align-items: stretch;
  margin-bottom: 24rpx;
  padding: 12rpx 14rpx 12rpx 12rpx;
  background:
    linear-gradient(180deg,
      rgba(255, 255, 255, 0.05) 0%,
      rgba(255, 255, 255, 0.022) 100%);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
  border-radius: 24rpx;
  transition: all 0.25s ease;
  box-shadow: inset 0 1rpx 0 rgba(255,255,255,0.04);
}
.field-group:focus-within {
  background:
    linear-gradient(180deg,
      rgba(255, 255, 255, 0.08) 0%,
      rgba(255, 255, 255, 0.035) 100%);
  border-color: rgba(247, 198, 106, 0.6);
  box-shadow:
    0 0 0 5rpx rgba(247, 198, 106, 0.14),
    inset 0 1rpx 0 rgba(255,255,255,0.06);
}
.field-group.captcha-group {
  margin-bottom: 10rpx;
}
.login-content.registering .field-group {
  margin-bottom: 20rpx;
}

.field-icon-wrap {
  position: relative;
  flex: 0 0 auto;
  width: 76rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 8rpx;
  overflow: hidden;
  border-radius: 18rpx;
}
.field-icon-bg {
  position: absolute;
  inset: 6rpx;
  border-radius: 16rpx;
  background: linear-gradient(160deg,
    rgba(247, 198, 106, 0.28) 0%,
    rgba(247, 198, 106, 0.1) 55%,
    rgba(247, 198, 106, 0.02) 100%);
  border: 1rpx solid rgba(247, 198, 106, 0.28);
  box-shadow: inset 0 1rpx 0 rgba(255,255,255,0.1);
}
/* 每个图标色带略不同，增加区分度 */
.field-icon-wrap-pwd  .field-icon-bg {
  background: linear-gradient(160deg, rgba(130,160,255,0.28) 0%, rgba(130,160,255,0.06) 60%, rgba(130,160,255,0.02) 100%);
  border-color: rgba(130,160,255,0.28);
}
.field-icon-wrap-name .field-icon-bg {
  background: linear-gradient(160deg, rgba(180,130,255,0.28) 0%, rgba(180,130,255,0.06) 60%, rgba(180,130,255,0.02) 100%);
  border-color: rgba(180,130,255,0.28);
}
.field-icon-wrap-cap  .field-icon-bg {
  background: linear-gradient(160deg, rgba(120,220,180,0.28) 0%, rgba(120,220,180,0.06) 60%, rgba(120,220,180,0.02) 100%);
  border-color: rgba(120,220,180,0.3);
}
.field-icon {
  position: relative;
  z-index: 2;
  font-size: 34rpx;
  filter:
    drop-shadow(0 2rpx 4rpx rgba(0, 0, 0, 0.35))
    drop-shadow(0 0 8rpx rgba(247, 198, 106, 0.25));
}

.field-input-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.field-label-row {
  display: flex;
  align-items: center;
  margin: 0 0 6rpx 2rpx;
}
.field-label {
  color: rgba(255, 255, 255, 0.66);
  font-size: 22rpx;
  font-weight: 700;
  letter-spacing: 0.6rpx;
}
.required-mark {
  margin-right: 6rpx;
  color: #ff7a7a;
  font-size: 22rpx;
  font-weight: 800;
  filter: drop-shadow(0 0 6rpx rgba(255,80,80,0.35));
}

.input {
  height: 60rpx;
  padding: 0 6rpx;
  background: transparent;
  color: #fff;
  border: none;
  border-radius: 0;
  font-size: 30rpx;
  font-weight: 500;
  letter-spacing: 0.3rpx;
}
.input:focus {
  background: transparent;
  border: none;
}
.ph {
  color: rgba(255, 255, 255, 0.32);
  font-weight: 400;
  letter-spacing: 0.2rpx;
}

/* ============ 密码框 ============ */
.pwd-wrap {
  position: relative;
  display: flex;
  align-items: center;
}
.pwd-input {
  flex: 1;
  padding-right: 68rpx;
}
.pwd-eye {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 68rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.pwd-eye:active {
  opacity: 0.6;
}
.pwd-eye-text {
  font-size: 32rpx;
  filter: drop-shadow(0 0 6rpx rgba(247,198,106,0.2));
}

/* ============ 验证码 ============ */
.captcha-row {
  display: flex;
  gap: 14rpx;
  align-items: center;
}
.captcha-input {
  flex: 1;
}
.captcha-box {
  width: 180rpx;
  height: 68rpx;
  border-radius: 18rpx;
  background:
    linear-gradient(180deg,
      rgba(255, 255, 255, 0.1) 0%,
      rgba(255, 255, 255, 0.04) 100%);
  border: 1rpx solid rgba(255, 255, 255, 0.14);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
  box-shadow:
    inset 0 1rpx 0 rgba(255,255,255,0.1),
    0 4rpx 12rpx rgba(0,0,0,0.22);
}
.captcha-box:active {
  transform: scale(0.97);
  opacity: 0.85;
  transition: all 0.15s ease;
}
.captcha-image {
  width: 100%;
  height: 100%;
  border-radius: 18rpx;
}
.captcha-spinner {
  width: 30rpx;
  height: 30rpx;
  border: 3rpx solid rgba(247, 198, 106, 0.25);
  border-top-color: #f7c66a;
  border-radius: 50%;
  animation: captcha-spin 0.8s linear infinite;
}
@keyframes captcha-spin {
  to { transform: rotate(360deg); }
}

/* ============ 主按钮（多层 + 扫光 shimmer） ============ */
.btn-area {
  margin-top: 34rpx;
}
.primary {
  position: relative;
  height: 96rpx;
  line-height: 96rpx;
  padding: 0;
  background:
    linear-gradient(160deg, #fff4cf 0%, #ffd989 22%, #f4b846 52%, #d99426 80%, #b97619 100%);
  color: #161207;
  border: none;
  border-radius: 26rpx;
  font-weight: 900;
  font-size: 32rpx;
  letter-spacing: 2.5rpx;
  overflow: hidden;
  box-shadow:
    0 18rpx 44rpx rgba(247, 198, 106, 0.55),
    0 0 0 1rpx rgba(255, 240, 185, 0.5),
    0 0 0 5rpx rgba(247, 198, 106, 0.08),
    inset 0 2rpx 4rpx rgba(255, 255, 255, 0.7),
    inset 0 -5rpx 10rpx rgba(160, 100, 15, 0.3);
  transition: all 0.22s ease;
}
.primary::after {
  border: none;
}
.primary-text {
  position: relative;
  z-index: 2;
  text-shadow: 0 1rpx 0 rgba(255,255,255,0.35);
}
/* 按钮扫光 */
.primary-shimmer {
  position: absolute;
  top: -60%;
  left: -60%;
  width: 45%;
  height: 260%;
  background: linear-gradient(110deg,
    rgba(255,255,255,0) 35%,
    rgba(255,255,255,0.72) 50%,
    rgba(255,255,255,0) 65%);
  transform: rotate(16deg);
  animation: btn-shimmer 2.8s ease-in-out infinite;
  z-index: 3;
  pointer-events: none;
}
@keyframes btn-shimmer {
  0%   { left: -60%; }
  60%  { left: 130%; }
  100% { left: 130%; }
}
.primary:active {
  transform: translateY(3rpx) scale(0.985);
  box-shadow:
    0 10rpx 26rpx rgba(247, 198, 106, 0.48),
    0 0 0 1rpx rgba(255, 240, 185, 0.4),
    0 0 0 3rpx rgba(247, 198, 106, 0.06),
    inset 0 2rpx 4rpx rgba(255, 255, 255, 0.55),
    inset 0 -4rpx 8rpx rgba(160, 100, 15, 0.3);
}
.primary[disabled] {
  opacity: 0.65;
  filter: grayscale(0.2);
}

/* ============ 切换模式链接行 ============ */
.switch-line {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  margin: 32rpx 0 4rpx;
  padding: 10rpx 0;
}
.switch-line-bar {
  flex: 1;
  height: 1rpx;
  background: linear-gradient(90deg,
    rgba(255,255,255,0) 0%,
    rgba(255, 218, 150, 0.25) 50%,
    rgba(255,255,255,0) 100%);
}
.switch-link {
  color: #ffd589;
  font-size: 24rpx;
  font-weight: 600;
  letter-spacing: 0.5rpx;
  white-space: nowrap;
  text-shadow: 0 0 14rpx rgba(247,198,106,0.35);
  padding: 6rpx 4rpx;
}
.switch-link:active {
  opacity: 0.7;
  text-decoration: underline;
}

/* ============ 底部提示 ============ */
.foot-tip {
  margin-top: 30rpx;
  padding: 0 20rpx;
  text-align: center;
}
.foot-text {
  color: rgba(255, 220, 170, 0.28);
  font-size: 22rpx;
  line-height: 1.6;
  letter-spacing: 0.4rpx;
}

/* ============ 注册模式微调 ============ */
.login-content.registering .form-space {
  height: 20rpx;
}

/* ============ 小屏幕适配（max-height 680px） ============ */
@media screen and (max-height: 680px) {
  .login-content {
    justify-content: flex-start;
    padding-top: 60rpx;
  }
  .brand-hero {
    margin: 8rpx 0 20rpx;
  }
  .hero-logo {
    width: 120rpx;
    height: 120rpx;
    margin-bottom: 18rpx;
  }
  .hero-logo-inner {
    width: 100rpx;
    height: 100rpx;
    border-radius: 28rpx;
  }
  .hero-logo-text {
    font-size: 42rpx;
  }
  .hero-title-text {
    font-size: 46rpx;
  }
  .hero-sub {
    font-size: 24rpx;
  }
  .panel {
    padding: 24rpx 24rpx 28rpx;
    border-radius: 32rpx;
  }
  .mode-tab {
    height: 68rpx;
    font-size: 26rpx;
  }
  .primary {
    height: 86rpx;
    line-height: 86rpx;
    font-size: 28rpx;
  }
  .btn-area {
    margin-top: 26rpx;
  }
  .switch-line {
    margin: 26rpx 0 2rpx;
  }
  .field-group {
    margin-bottom: 20rpx;
  }
}
</style>
