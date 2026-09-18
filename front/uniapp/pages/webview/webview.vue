<template>
  <view class="page webview-page">
    <web-view v-if="url" :src="url" @message="onMessage" />
    <view v-else class="empty">{{ t('requestFailed') }}</view>
    <app-tab-bar current="store" />
  </view>
</template>

<script>
import { getLocale, t as translate } from '../../utils/i18n.js'

const AUTO_CLOSE_DELAY = 10000 // 支付结果页 10 秒后自动关闭 webview

export default {
  data() {
    return {
      url: '',
      locale: getLocale(),
      autoCloseTimer: null
    }
  },
  onLoad(options) {
    this.url = decodeURIComponent(options.url || '')
    // 如果 URL 是 Stripe/PayPal 支付结果页,启动自动关闭兜底 timer
    // 后端 HTML 里的 uni.navigateBack() 在部分 iOS 环境可能不生效,这里双重保险
    if (this.url && /stripe\/return|paypal\/return|orderNo=/i.test(this.url)) {
      this.startAutoCloseTimer()
    }
  },
  onShow() {
    this.locale = getLocale()
  },
  onUnload() {
    this.clearAutoCloseTimer()
  },
  methods: {
    onMessage(e) {
      // uni-app web-view 的 postMessage 会在特定时机触发
      // 后端 HTML 里 uni.navigateBack() 时会触发此事件
      if (e && e.detail && e.detail.data) {
        const msgs = Array.isArray(e.detail.data) ? e.detail.data : [e.detail.data]
        for (const msg of msgs) {
          if (msg && (msg.type === 'close' || msg.action === 'close')) {
            this.clearAutoCloseTimer()
            uni.navigateBack({ delta: 1 })
            return
          }
        }
      }
      // 没收到明确消息也可以尝试关闭 (兜底)
      this.clearAutoCloseTimer()
      uni.navigateBack({ delta: 1 })
    },
    startAutoCloseTimer() {
      this.clearAutoCloseTimer()
      this.autoCloseTimer = setTimeout(() => {
        this.autoCloseTimer = null
        uni.navigateBack({ delta: 1 })
      }, AUTO_CLOSE_DELAY)
    },
    clearAutoCloseTimer() {
      if (this.autoCloseTimer) {
        clearTimeout(this.autoCloseTimer)
        this.autoCloseTimer = null
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
  width: 100%;
  height: 100vh;
  background: #080a10;
}

.empty {
  padding: 56rpx 24rpx;
  color: rgba(255,255,255,0.62);
  text-align: center;
}
</style>
