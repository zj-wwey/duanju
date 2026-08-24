<template>
  <view class="page">
    <view class="hero">
      <view>
        <view class="eyebrow">{{ t('theaterTab') }}</view>
        <view class="title">{{ t('browse') }}</view>
      </view>
      <view class="search" @click="focusSearch">⌕</view>
    </view>

    <view class="search-box">
      <input
        v-model.trim="params.keyword"
        class="search-input"
        :placeholder="t('searchDrama')"
        confirm-type="search"
        @confirm="loadDramas"
      />
      <button class="search-btn" @click="loadDramas">{{ t('search') }}</button>
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

    <view class="filter-panel">
      <view v-for="group in visibleGroups" :key="group.key" class="group">
        <view class="group-title">{{ group.label }}</view>
        <scroll-view scroll-x class="filter-bar small">
          <view class="chip mini" :class="{ active: !params[group.key] }" @click="setFilter(group.key, '')">{{ t('all') }}</view>
          <view
            v-for="option in group.options"
            :key="option.key"
            class="chip mini"
            :class="{ active: params[group.key] === option.key }"
            @click="setFilter(group.key, option.key)"
          >
            {{ option.label }}
          </view>
        </scroll-view>
      </view>
    </view>

    <view class="section-row">
      <view class="section-title">{{ t('episodes') }}</view>
      <view class="section-action" @click="clearFilters">{{ t('clear') }}</view>
    </view>

    <view v-if="loading" class="state">{{ t('loading') }}</view>
    <view v-else-if="!dramas.length" class="state">{{ t('noTitles') }}</view>
    <view v-else class="grid">
      <view v-for="item in dramas" :key="item.id" class="card" @click="openDetail(item)">
        <image :src="coverOf(item)" mode="aspectFill" />
        <view class="badge">{{ contentTypeLabel(item) }}</view>
        <view class="card-title">{{ item.title }}</view>
        <view class="card-meta">{{ episodeMeta(item) }}</view>
        <button class="play" @click.stop="play(item)">{{ t('play') }}</button>
      </view>
    </view>

    <app-tab-bar current="theater" />
  </view>
</template>

<script>
import api from '../../utils/api.js'
import { getLocale, t as translate } from '../../utils/i18n.js'

const FILTER_KEYS = ['contentType', 'background', 'theme', 'setting', 'audience', 'time', 'sort']

export default {
  data() {
    return {
      groups: [],
      dramas: [],
      params: {
        keyword: '',
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
    visibleGroups() {
      return this.groups.filter(group => group.key && group.key !== 'contentType' && (group.options || []).length)
    }
  },
  onLoad(options) {
    FILTER_KEYS.forEach(key => {
      if (options[key]) this.params[key] = options[key]
    })
    if (options.keyword) this.params.keyword = options.keyword
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
    clearFilters() {
      this.params.keyword = ''
      FILTER_KEYS.forEach(key => { this.params[key] = '' })
      this.loadDramas()
    },
    focusSearch() {},
    optionsOf(key) {
      const group = this.groups.find(item => item.key === key)
      return group?.options || []
    },
    optionLabel(key, value) {
      const item = this.optionsOf(key).find(option => option.key === value)
      return item?.label || ''
    },
    contentTypeLabel(item) {
      const type = item.contentType || item.content_type
      return this.optionLabel('contentType', type) || type || this.t('theaterTab')
    },
    episodeMeta(item) {
      const total = item.totalEpisodes || item.total_episodes || item.episodeCount || item.episode_count
      const updated = item.updatedEpisodes || item.updated_episodes
      if (total && updated) return updated + '/' + total + ' ' + this.t('episodes')
      if (total) return total + ' ' + this.t('episodes')
      return item.tags || item.description || ''
    },
    openDetail(item) {
      uni.navigateTo({ url: '/pages/detail/detail?id=' + item.id })
    },
    play(item) {
      uni.navigateTo({ url: '/pages/player/player?dramaId=' + item.id })
    },
    coverOf(item) {
      return item.verticalCoverUrl || item.vertical_cover_url || item.coverUrl || item.cover_url || ''
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
  padding: 40rpx 24rpx 0;
  box-sizing: border-box;
  background:
    radial-gradient(circle at 12% 0%, rgba(255, 138, 31, 0.1), transparent 40%),
    radial-gradient(circle at 90% 10%, rgba(132, 92, 60, 0.06), transparent 38%),
    #f7f4ed;
  color: #161616;
  font-family: -apple-system, BlinkMacSystemFont, "Inter", "SF Pro Display", "Segoe UI", sans-serif;
}

.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 26rpx;
}

.eyebrow {
  display: flex;
  align-items: center;
  color: #f48a20;
  font-size: 24rpx;
  font-weight: 900;
  letter-spacing: 4rpx;
  text-transform: uppercase;
}

.eyebrow::before {
  content: "";
  display: inline-block;
  width: 32rpx;
  height: 3rpx;
  margin-right: 14rpx;
  background: linear-gradient(90deg, transparent, #ff8a1f);
  border-radius: 2rpx;
}

.title {
  margin-top: 8rpx;
  font-size: 56rpx;
  font-weight: 900;
  letter-spacing: -1.5rpx;
  line-height: 1.05;
  background: linear-gradient(135deg, #1a1a1a 30%, #4a4a4a 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.search {
  width: 80rpx;
  height: 80rpx;
  line-height: 80rpx;
  text-align: center;
  border-radius: 50%;
  background: #fff;
  font-size: 42rpx;
  box-shadow:
    0 12rpx 34rpx rgba(60, 50, 30, 0.1),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.8);
  transition: transform 0.18s ease;
}

.search:active {
  transform: scale(0.92);
}

.search-box {
  display: flex;
  align-items: center;
  gap: 14rpx;
  margin-bottom: 24rpx;
}

.search-input {
  flex: 1;
  height: 82rpx;
  padding: 0 30rpx;
  border-radius: 999rpx;
  background: #fff;
  font-size: 27rpx;
  box-shadow:
    0 10rpx 28rpx rgba(60, 50, 30, 0.08),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.8);
}

.search-btn {
  width: 140rpx;
  height: 82rpx;
  line-height: 82rpx;
  padding: 0;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #ff9d3d, #f48a20);
  color: #fff;
  font-size: 26rpx;
  font-weight: 800;
  letter-spacing: 0.5rpx;
  box-shadow:
    0 12rpx 28rpx rgba(255, 138, 31, 0.36),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.4);
  transition: transform 0.18s ease;
}

.search-btn:active {
  transform: scale(0.95);
}

.filter-bar {
  white-space: nowrap;
  margin-bottom: 18rpx;
}

.filter-bar.small {
  margin-bottom: 8rpx;
}

.filter-panel {
  padding: 26rpx 22rpx 10rpx;
  background: #fff;
  border-radius: 32rpx;
  box-shadow:
    0 16rpx 40rpx rgba(60, 50, 30, 0.08),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.8);
}

.chip {
  display: inline-flex;
  align-items: center;
  height: 68rpx;
  margin-right: 14rpx;
  padding: 0 30rpx;
  color: #6b655c;
  background: #f5f1e8;
  border-radius: 999rpx;
  font-size: 25rpx;
  font-weight: 800;
  letter-spacing: 0.3rpx;
  transition: all 0.2s ease;
}

.chip:active {
  transform: scale(0.95);
}

.chip.mini {
  height: 58rpx;
  padding: 0 24rpx;
  background: #f5f1e8;
  font-size: 23rpx;
}

.chip.active {
  color: #fff;
  background: linear-gradient(135deg, #ff9d3d, #f48a20);
  box-shadow:
    0 10rpx 24rpx rgba(255, 138, 31, 0.36),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.4);
}

.group-title {
  display: flex;
  align-items: center;
  margin: 6rpx 4rpx 14rpx;
  color: #9a948b;
  font-size: 22rpx;
  font-weight: 900;
  letter-spacing: 3rpx;
  text-transform: uppercase;
}

.group-title::before {
  content: "";
  display: inline-block;
  width: 24rpx;
  height: 2rpx;
  margin-right: 12rpx;
  background: #c4bdb0;
}

.section-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 38rpx 4rpx 22rpx;
}

.section-title {
  display: flex;
  align-items: center;
  font-size: 36rpx;
  font-weight: 900;
  letter-spacing: -0.5rpx;
}

.section-title::before {
  content: "";
  display: inline-block;
  width: 8rpx;
  height: 32rpx;
  margin-right: 16rpx;
  background: linear-gradient(180deg, #ff9d3d, #f48a20);
  border-radius: 4rpx;
  box-shadow: 0 4rpx 12rpx rgba(255, 138, 31, 0.4);
}

.section-action {
  display: flex;
  align-items: center;
  color: #f48a20;
  font-size: 24rpx;
  font-weight: 800;
  letter-spacing: 0.5rpx;
}

.section-action::after {
  content: "›";
  margin-left: 6rpx;
  font-size: 28rpx;
  font-weight: 700;
}

.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 26rpx 18rpx;
}

.card {
  position: relative;
  min-width: 0;
  transition: transform 0.2s ease;
}

.card:active {
  transform: scale(0.96);
}

.card image {
  width: 100%;
  height: 286rpx;
  border-radius: 22rpx;
  background: #e8e2d8;
  box-shadow:
    0 14rpx 32rpx rgba(60, 50, 30, 0.12),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.6);
}

.badge {
  position: absolute;
  top: 14rpx;
  right: 12rpx;
  padding: 5rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(8rpx);
  color: #fff;
  font-size: 19rpx;
  font-weight: 800;
  letter-spacing: 0.5rpx;
}

.card-title {
  margin-top: 14rpx;
  font-size: 28rpx;
  font-weight: 900;
  letter-spacing: -0.3rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-meta {
  margin-top: 6rpx;
  color: #9a948b;
  font-size: 22rpx;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.play {
  margin-top: 12rpx;
  height: 56rpx;
  line-height: 56rpx;
  padding: 0;
  border-radius: 999rpx;
  color: #fff;
  background: linear-gradient(135deg, #2a2a2a, #171717);
  font-size: 22rpx;
  font-weight: 800;
  letter-spacing: 0.5rpx;
  box-shadow: 0 8rpx 20rpx rgba(0, 0, 0, 0.18);
  transition: transform 0.18s ease;
}

.play:active {
  transform: scale(0.95);
  background: linear-gradient(135deg, #ff9d3d, #f48a20);
}

.state {
  padding: 140rpx 0;
  text-align: center;
  color: #9a948b;
  font-size: 26rpx;
  font-weight: 600;
}
</style>
