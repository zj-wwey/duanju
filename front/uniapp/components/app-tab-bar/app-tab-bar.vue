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
        <view class="app-tabbar-icon-wrap">
          <u-icon
            :name="activeKey === item.key ? item.activeIcon : item.icon"
            :size="40"
            :color="activeKey === item.key ? '#f7c66a' : 'rgba(255,255,255,0.85)'"
          ></u-icon>
        </view>
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
        { key: 'home', labelKey: 'homeTab', icon: 'home', activeIcon: 'home-fill', url: TAB_ROUTES.home },
        { key: 'theater', labelKey: 'theaterTab', icon: 'grid', activeIcon: 'grid-fill', url: TAB_ROUTES.theater },
        { key: 'store', labelKey: 'storeTab', icon: 'shopping-cart', activeIcon: 'shopping-cart-fill', url: TAB_ROUTES.store },
        { key: 'mine', labelKey: 'mineTab', icon: 'account', activeIcon: 'account-fill', url: TAB_ROUTES.mine }
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
  height: calc(104rpx + env(safe-area-inset-bottom));
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
  height: calc(96rpx + env(safe-area-inset-bottom));
  padding: 10rpx 8rpx calc(8rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  background: linear-gradient(180deg, rgba(17, 17, 22, 0.96), rgba(8, 8, 10, 0.99));
  backdrop-filter: blur(16rpx);
  border-top: 1rpx solid rgba(247, 198, 106, 0.18);
  box-shadow: 0 -6rpx 24rpx rgba(0, 0, 0, 0.45);
}

.app-tabbar-item {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4rpx;
  color: rgba(255, 255, 255, 0.85);
  font-size: 22rpx;
  line-height: 22rpx;
  transition: transform 0.18s ease;
}

.app-tabbar-item:active {
  transform: scale(0.92);
}

/* 选中态：金色文字 + 图标微微上浮 */
.app-tabbar-item.active {
  color: #f7c66a;
}

.app-tabbar-item.active .app-tabbar-label {
  font-family: 'PingFang SC', 'HarmonyOS Sans', 'Noto Sans SC', 'Microsoft YaHei', sans-serif;
  color: #f7c66a;
  font-weight: 800;
  text-shadow: 0 0 12rpx rgba(247, 198, 106, 0.45);
}

.app-tabbar-item.active .app-tabbar-icon-wrap {
  transform: translateY(-4rpx);
}

.app-tabbar-icon-wrap {
  width: 56rpx;
  height: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.18s ease;
}

.app-tabbar-label {
  font-family: 'PingFang SC', 'HarmonyOS Sans', 'Noto Sans SC', 'Microsoft YaHei', sans-serif;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-tabbar-dot {
  position: absolute;
  right: 30rpx;
  top: 2rpx;
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff6b81, #ff2d55);
  box-shadow: 0 0 8rpx rgba(255, 45, 85, 0.7);
}
</style>
