<template>
  <view class="page">
    <view class="head">
      <view class="title">{{ t('browse') }}</view>
      <button class="mine" @click="goMine">{{ t('profile') }}</button>
    </view>

    <scroll-view scroll-x class="filter-bar">
      <view class="chip" :class="{ active: !params.contentType }" @click="setFilter('contentType', '')">{{ t('all') }}</view>
      <view
        v-for="option in contentTypes"
        :key="option.key"
        class="chip"
        :class="{ active: params.contentType === option.key }"
        @click="setFilter('contentType', option.key)"
      >
        {{ option.label }}
      </view>
    </scroll-view>

    <view v-for="group in secondaryGroups" :key="group.key" class="group">
      <view class="group-title">{{ group.label }}</view>
      <scroll-view scroll-x class="filter-bar small">
        <view class="chip" :class="{ active: !params[group.key] }" @click="setFilter(group.key, '')">{{ t('all') }}</view>
        <view
          v-for="option in group.options"
          :key="option.key"
          class="chip"
          :class="{ active: params[group.key] === option.key }"
          @click="setFilter(group.key, option.key)"
        >
          {{ option.label }}
        </view>
      </scroll-view>
    </view>

    <view v-if="loading" class="state">{{ t('loading') }}</view>
    <view v-else-if="!dramas.length" class="state">{{ t('noTitles') }}</view>
    <view v-else class="grid">
      <view v-for="item in dramas" :key="item.id" class="card" @click="openDetail(item)">
        <image :src="coverOf(item)" mode="aspectFill" />
        <view class="card-title">{{ item.title }}</view>
        <view class="card-desc">{{ item.description }}</view>
        <button class="play" @click.stop="play(item)">{{ t('play') }}</button>
      </view>
    </view>
    <app-tab-bar current="theater" />
  </view>
</template>

<script>
import api from '../../utils/api.js'
import { getLocale, t as translate } from '../../utils/i18n.js'

export default {
  data() {
    return {
      groups: [],
      dramas: [],
      params: {
        contentType: '',
        background: '',
        theme: '',
        setting: '',
        audience: '',
        time: '',
        sort: ''
      },
      loading: true,
      locale: getLocale()
    }
  },
  computed: {
    contentTypes() {
      return this.optionsOf('contentType')
    },
    secondaryGroups() {
      return this.groups.filter(group => group.key && group.key !== 'contentType' && (group.options || []).length)
    }
  },
  onLoad(options) {
    if (options.contentType) this.params.contentType = options.contentType
    this.loadFilters()
    this.loadDramas()
  },
  onShow() {
    this.locale = getLocale()
  },
  onPullDownRefresh() {
    this.loadDramas().finally(() => uni.stopPullDownRefresh())
  },
  methods: {
    async loadFilters() {
      try {
        this.groups = await api.categoryFilters()
      } catch (_) {
        this.groups = []
      }
    },
    async loadDramas() {
      this.loading = true
      try {
        this.dramas = await api.dramas(this.params)
      } catch (err) {
        this.dramas = []
        uni.showToast({ title: err.message, icon: 'none' })
      } finally {
        this.loading = false
      }
    },
    setFilter(key, value) {
      this.params[key] = value
      this.loadDramas()
    },
    optionsOf(key) {
      const group = this.groups.find(item => item.key === key)
      return group?.options || []
    },
    openDetail(item) {
      uni.navigateTo({ url: '/pages/detail/detail?id=' + item.id })
    },
    play(item) {
      uni.navigateTo({ url: '/pages/player/player?dramaId=' + item.id })
    },
    goMine() {
      uni.navigateTo({ url: '/pages/mine/mine' })
    },
    coverOf(item) {
      return item.coverUrl || item.cover_url || ''
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
  padding: 28rpx;
  box-sizing: border-box;
  background:
    radial-gradient(circle at 12% 4%, rgba(247, 198, 106, 0.14), transparent 38%),
    radial-gradient(circle at 90% 14%, rgba(77, 208, 225, 0.08), transparent 40%),
    #080a10;
  color: #fff;
  font-family: -apple-system, BlinkMacSystemFont, "Inter", "SF Pro Display", "Segoe UI", sans-serif;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
}

.title {
  display: flex;
  align-items: center;
  font-size: 50rpx;
  font-weight: 900;
  letter-spacing: -1.5rpx;
  background: linear-gradient(135deg, #ffffff 30%, rgba(255, 255, 255, 0.78) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.title::before {
  content: "";
  display: inline-block;
  width: 10rpx;
  height: 42rpx;
  margin-right: 20rpx;
  background: linear-gradient(180deg, #ffe0a1, #f3b84d);
  border-radius: 6rpx;
  box-shadow: 0 4rpx 14rpx rgba(247, 198, 106, 0.5);
}

.mine {
  height: 66rpx;
  line-height: 66rpx;
  padding: 0 30rpx;
  color: #11100d;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 800;
  letter-spacing: 0.5rpx;
  box-shadow:
    0 10rpx 26rpx rgba(247, 198, 106, 0.36),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
  transition: transform 0.18s ease;
}

.mine:active {
  transform: scale(0.95);
}

.filter-bar {
  white-space: nowrap;
  margin-bottom: 20rpx;
}

.filter-bar.small {
  margin-bottom: 14rpx;
}

.chip {
  display: inline-flex;
  align-items: center;
  height: 64rpx;
  margin-right: 14rpx;
  padding: 0 26rpx;
  color: rgba(255,255,255,0.74);
  background: rgba(255,255,255,0.08);
  border: 1rpx solid rgba(255,255,255,0.14);
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 700;
  letter-spacing: 0.3rpx;
  transition: all 0.2s ease;
}

.chip:active {
  transform: scale(0.95);
}

.chip.active {
  color: #11100d;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  border-color: transparent;
  box-shadow:
    0 10rpx 24rpx rgba(247, 198, 106, 0.36),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
}

.group-title {
  display: flex;
  align-items: center;
  margin: 14rpx 4rpx;
  color: rgba(255,255,255,0.6);
  font-size: 22rpx;
  font-weight: 800;
  letter-spacing: 3rpx;
  text-transform: uppercase;
}

.group-title::before {
  content: "";
  display: inline-block;
  width: 24rpx;
  height: 2rpx;
  margin-right: 12rpx;
  background: rgba(255, 255, 255, 0.3);
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 22rpx;
  margin-top: 26rpx;
}

.card {
  padding: 18rpx;
  border-radius: 24rpx;
  background: linear-gradient(160deg, rgba(255,255,255,0.14), rgba(255,255,255,0.055)), rgba(12,15,24,0.78);
  border: 1rpx solid rgba(255,255,255,0.16);
  box-shadow:
    0 22rpx 50rpx rgba(0, 0, 0, 0.32),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(20rpx);
  transition: transform 0.2s ease;
}

.card:active {
  transform: scale(0.97);
}

.card image {
  width: 100%;
  height: 380rpx;
  border-radius: 18rpx;
  background: #1a1d26;
  box-shadow: 0 8rpx 22rpx rgba(0, 0, 0, 0.4);
}

.card-title {
  margin-top: 16rpx;
  font-size: 30rpx;
  font-weight: 800;
  letter-spacing: -0.3rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-desc {
  height: 68rpx;
  margin-top: 8rpx;
  color: rgba(255,255,255,0.62);
  font-size: 22rpx;
  line-height: 1.5;
  overflow: hidden;
}

.play {
  margin-top: 16rpx;
  height: 64rpx;
  line-height: 64rpx;
  color: #11100d;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 800;
  letter-spacing: 0.5rpx;
  box-shadow:
    0 12rpx 28rpx rgba(247, 198, 106, 0.32),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
  transition: transform 0.18s ease;
}

.play:active {
  transform: scale(0.95);
}

.state {
  padding: 140rpx 0;
  text-align: center;
  color: rgba(255,255,255,0.58);
  font-size: 26rpx;
  font-weight: 600;
}
</style>
