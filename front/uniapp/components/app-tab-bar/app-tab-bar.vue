<template>
  <view class="app-tabbar-wrap">
    <view class="app-tabbar-spacer"></view>
    <view class="app-tabbar">
      <view
        v-for="item in tabs"
        :key="item.key"
        class="app-tabbar-item"
        :class="{ active: activeKey === item.key }"
        @click="go(item)"
      >
        <view class="app-tabbar-icon">{{ item.icon }}</view>
        <view class="app-tabbar-label">{{ item.labelKey ? t(item.labelKey) : item.label }}</view>
        <view v-if="item.key === 'mine' && unreadDot" class="app-tabbar-dot"></view>
      </view>
    </view>
  </view>
</template>

<script>
import { getLocale, t as translate } from '../../utils/i18n.js'

const TAB_ROUTES = {
  home: '/pages/index/index',
  theater: '/pages/theater/theater',
  store: '/pages/store/store',
  mine: '/pages/mine/mine'
}

export default {
  name: 'AppTabBar',
  props: {
    current: {
      type: String,
      default: ''
    },
    unreadDot: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      locale: getLocale(),
      tabs: [
        { key: 'home', labelKey: 'homeTab', icon: '⌂', url: TAB_ROUTES.home },
        { key: 'theater', labelKey: 'theaterTab', icon: '▦', url: TAB_ROUTES.theater },
        { key: 'store', labelKey: 'storeTab', icon: '◇', url: TAB_ROUTES.store },
        { key: 'mine', labelKey: 'mineTab', icon: '◎', url: TAB_ROUTES.mine }
      ]
    }
  },
  computed: {
    activeKey() {
      if (this.current) return this.current
      const pages = getCurrentPages()
      const route = pages.length ? '/' + pages[pages.length - 1].route : ''
      if (route.indexOf('/pages/index/') === 0) return 'home'
      if (route.indexOf('/pages/theater/') === 0 || route.indexOf('/pages/category/') === 0 || route.indexOf('/pages/detail/') === 0) return 'theater'
      if (route.indexOf('/pages/store/') === 0 || route.indexOf('/pages/recharge/') === 0 || route.indexOf('/pages/webview/') === 0) return 'store'
      if (route.indexOf('/pages/mine/') === 0) return 'mine'
      return 'home'
    }
  },
  methods: {
    go(item) {
      if (item.key === this.activeKey) return
      uni.redirectTo({ url: item.url })
    },
    t(key, params) {
      return translate(key, params, this.locale)
    }
  }
}
</script>

<style>
.app-tabbar-spacer {
  height: calc(88rpx + env(safe-area-inset-bottom));
}

.app-tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: space-around;
  height: calc(80rpx + env(safe-area-inset-bottom));
  padding: 6rpx 8rpx calc(6rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  background: rgba(10, 10, 12, 0.94);
  border-top: 1rpx solid rgba(255, 255, 255, 0.08);
}

.app-tabbar-item {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2rpx;
  color: rgba(255, 255, 255, 0.58);
  font-size: 18rpx;
  line-height: 22rpx;
}

.app-tabbar-item.active {
  color: #fff;
}

.app-tabbar-icon {
  height: 30rpx;
  line-height: 30rpx;
  font-size: 26rpx;
  font-weight: 800;
}

.app-tabbar-label {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-tabbar-dot {
  position: absolute;
  right: 28rpx;
  top: 6rpx;
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: #ff4d5f;
}
</style>
