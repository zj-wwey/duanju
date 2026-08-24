<template>
  <view class="page webview-page">
    <web-view v-if="url" :src="url" @message="onMessage" />
    <view v-else class="empty">{{ t('requestFailed') }}</view>
    <app-tab-bar current="store" />
  </view>
</template>

<script>
import { getLocale, t as translate } from '../../utils/i18n.js'

export default {
  data() {
    return {
      url: '',
      locale: getLocale()
    }
  },
  onLoad(options) {
    this.url = decodeURIComponent(options.url || '')
  },
  onShow() {
    this.locale = getLocale()
  },
  methods: {
    onMessage() {},
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
