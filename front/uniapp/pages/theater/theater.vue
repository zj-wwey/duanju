<template>
  <view class="page">
    <view class="hero">
      <view class="hero-left">
        <view class="eyebrow">
          <view class="eyebrow-bar"></view>
          <text>{{ t('theaterTab') }}</text>
        </view>
        <view class="title">{{ t('browse') }}</view>
        <view class="hero-sub">发现精彩短剧，即刻开启追剧之旅</view>
      </view>
      <view class="filter-btn" @click="showFilters = true">
        <text class="filter-icon">⇅</text>
        <text class="filter-text">{{ t('filter') }}</text>
      </view>
    </view>

    <view class="search-box">
      <view class="search-wrap">
        <text class="search-ic">🔍</text>
        <input
          v-model.trim="params.keyword"
          class="search-input"
          :placeholder="t('searchDrama')"
          confirm-type="search"
          @confirm="loadDramas"
        />
        <text v-if="params.keyword" class="search-clear" @click="params.keyword = ''">✕</text>
      </view>
      <button class="search-btn" @click="loadDramas">{{ t('search') }}</button>
    </view>

    <view class="tabs-row">
      <scroll-view scroll-x class="filter-bar">
        <view class="chip" :class="{ active: !params.contentType }" @click="setFilter('contentType', '')">{{ t('all') }}</view>
        <view
          v-for="option in contentTypes"
          :key="option.key"
          class="chip"
          :class="{ active: params.contentType === option.key }"
          @click="setFilter('contentType', option.key)"
        >
          {{ t(option.label) }}
        </view>
      </scroll-view>
    </view>

    <view class="section-row">
      <view class="section-title">{{ t('episodes') }}<text class="count">· {{ dramaCount }}</text></view>
      <view class="section-action" @click="clearFilters">{{ t('clear') }}</view>
    </view>

    <view v-if="loading" class="state">{{ t('loading') }}</view>
    <view v-else-if="!dramas.length" class="state">{{ t('noTitles') }}</view>
    <view v-else class="grid">
      <view v-for="item in dramas" :key="item.id" class="card" @click="openDetail(item)">
        <view class="cover">
          <image :src="coverOf(item)" mode="aspectFill" />
          <view class="cover-overlay"></view>
          <view class="badge">{{ contentTypeLabel(item) }}</view>
          <view class="play" @click.stop="play(item)">
            <text class="play-ic">▶</text>
          </view>
          <view class="ep-badge">{{ episodeMeta(item) }}</view>
        </view>
        <view class="card-title">{{ item.title }}</view>
      </view>
    </view>

    <app-tab-bar current="theater" />

    <!-- ========== 筛选遮罩面板 ========== -->
    <view v-if="showFilters" class="filter-mask" @click="showFilters = false">
      <view class="filter-sheet" @click.stop>
        <view class="sheet-top">
          <view class="sheet-title">{{ t('filter') }}</view>
          <view class="sheet-close" @click="showFilters = false">✕</view>
        </view>
        <view class="sheet-groups">
          <view v-for="group in visibleGroups" :key="group.key" class="group">
            <view class="group-title">{{ t(group.label) }}<text v-if="params[group.key] !== 'all'" class="group-clear" @click="clearGroup(group.key)">清除</text></view>
            <view class="chip-row">
              <view
                v-for="option in group.options"
                :key="option.key"
                class="chip mini"
                :class="{ active: (params[group.key] || 'all') === option.key }"
                @click="toggleFilter(group.key, option.key)"
              >
                {{ t(option.label) }}
              </view>
            </view>
          </view>
        </view>
        <button class="sheet-btn" @click="applyFilters">{{ t('confirm') }}</button>
      </view>
    </view>
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
        background: 'all',
        theme: 'all',
        setting: 'all',
        audience: 'all',
        time: 'all',
        sort: 'all'
      },
      loading: true,
      showFilters: false,
      locale: getLocale()
    }
  },
  computed: {
    contentTypes() {
      return this.optionsOf('contentType')
    },
    visibleGroups() {
      return this.groups.filter(group => group.key && group.key !== 'contentType' && (group.options || []).length)
    },
    dramaCount() {
      return this.dramas.length
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
    toggleFilter(key, value) {
      // 点击同一个已选中的选项 → 取消选择，回到"全部"
      if (this.params[key] === value) {
        this.params[key] = 'all'
        return
      }
      this.params[key] = value
    },
    clearGroup(key) {
      this.params[key] = 'all'
    },
    clearFilters() {
      this.params.keyword = ''
      this.params.contentType = ''
      this.params.background = 'all'
      this.params.theme = 'all'
      this.params.setting = 'all'
      this.params.audience = 'all'
      this.params.time = 'all'
      this.params.sort = 'all'
      this.loadDramas()
    },
    applyFilters() {
      this.showFilters = false
      this.loadDramas()
    },
    focusSearch() {},
    optionsOf(key) {
      const group = this.groups.find(item => item.key === key)
      return (group && group.options) || []
    },
    optionLabel(key, value) {
      const item = this.optionsOf(key).find(option => option.key === value)
      return item ? this.t(item.label) : ''
    },
    contentTypeLabel(item) {
      const type = item.contentType || item.content_type
      const label = this.optionLabel('contentType', type)
      return label || (type ? this.t('category.' + type + 'Drama') : this.t('theaterTab'))
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
  padding: 40rpx 24rpx 160rpx;
  box-sizing: border-box;
  color: #fff;
  background:
    radial-gradient(circle at 16% 0, rgba(247,198,106,0.32), transparent 30%),
    radial-gradient(circle at 90% 18%, rgba(77,208,225,0.18), transparent 36%),
    radial-gradient(circle at 50% 100%, rgba(255, 138, 31, 0.08), transparent 50%),
    #080a10;
  font-family: -apple-system, BlinkMacSystemFont, "Inter", "SF Pro Display", "Segoe UI", sans-serif;
}

/* ===== Hero ===== */
.hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 32rpx;
}

.hero-left { flex: 1; min-width: 0; }

.eyebrow {
  display: inline-flex;
  align-items: center;
  color: #f7c66a;
  font-size: 24rpx;
  font-weight: 900;
  letter-spacing: 4rpx;
  text-transform: uppercase;
  margin-bottom: 14rpx;
}

.eyebrow::before {
  content: "";
  display: inline-block;
  width: 32rpx;
  height: 3rpx;
  margin-right: 14rpx;
  background: linear-gradient(90deg, transparent, #f7c66a);
  border-radius: 2rpx;
}

.title {
  font-size: 52rpx;
  font-weight: 900;
  letter-spacing: -1.5rpx;
  background: linear-gradient(135deg, #ffffff 30%, rgba(255, 255, 255, 0.78) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.hero-sub {
  margin-top: 10rpx;
  color: rgba(255,255,255,0.56);
  font-size: 24rpx;
  font-weight: 600;
  letter-spacing: 0.3rpx;
}

.filter-btn {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  height: 64rpx;
  padding: 0 24rpx;
  margin-top: 8rpx;
  background: linear-gradient(160deg, rgba(255,255,255,0.14), rgba(255,255,255,0.055)), rgba(12,15,24,0.78);
  border: 1rpx solid rgba(255,255,255,0.16);
  border-radius: 16rpx;
  backdrop-filter: blur(20rpx);
  transition: all 0.18s ease;
}

.filter-btn:active {
  transform: scale(0.96);
}

.filter-icon { font-size: 22rpx; color: #f7c66a; font-weight: 700; }
.filter-text { font-size: 24rpx; font-weight: 700; color: #f7c66a; }

/* ===== Search ===== */
.search-box {
  display: flex;
  align-items: center;
  gap: 14rpx;
  margin-bottom: 26rpx;
}

.search-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  height: 80rpx;
  padding: 0 28rpx;
  border-radius: 40rpx;
  background: linear-gradient(160deg, rgba(255,255,255,0.14), rgba(255,255,255,0.055)), rgba(12,15,24,0.78);
  border: 1rpx solid rgba(255,255,255,0.16);
  backdrop-filter: blur(20rpx);
  transition: all 0.2s ease;
}

.search-ic { font-size: 26rpx; margin-right: 12rpx; opacity: 0.5; }

.search-input {
  flex: 1;
  height: 80rpx;
  font-size: 28rpx;
  color: #ffffff;
  background: transparent;
}

.search-input::placeholder { color: rgba(255,255,255,0.3); }

.search-clear {
  width: 36rpx; height: 36rpx;
  line-height: 36rpx; text-align: center;
  border-radius: 50%;
  background: rgba(247,198,106,0.2);
  color: #f7c66a;
  font-size: 20rpx;
}

.search-btn {
  width: 132rpx;
  height: 80rpx;
  line-height: 80rpx;
  padding: 0;
  border: none;
  border-radius: 40rpx;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  color: #11100d;
  font-size: 26rpx;
  font-weight: 900;
  letter-spacing: 2rpx;
  box-shadow:
    0 12rpx 28rpx rgba(247, 198, 106, 0.36),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
  transition: all 0.18s ease;
}

.search-btn:active { transform: scale(0.95); }

/* ===== Tabs / Chips ===== */
.tabs-row { margin-bottom: 24rpx; }
.filter-bar { white-space: nowrap; }

.chip {
  display: inline-flex;
  align-items: center;
  height: 72rpx;
  margin-right: 14rpx;
  padding: 0 30rpx;
  border-radius: 999rpx;
  color: rgba(255,255,255,0.62);
  background: rgba(255,255,255,0.09);
  font-size: 25rpx;
  font-weight: 900;
  letter-spacing: 0.5rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.08);
  transition: all 0.2s ease;
}

.chip:active { transform: scale(0.95); }

.chip.mini {
  height: 60rpx;
  padding: 0 24rpx;
  font-size: 23rpx;
  font-weight: 700;
  margin-right: 12rpx;
  margin-bottom: 12rpx;
}

.chip.active {
  color: #11100d;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  border-color: transparent;
  box-shadow:
    0 12rpx 28rpx rgba(247, 198, 106, 0.36),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
}

/* ===== Section ===== */
.section-row {
  display: flex;
  align-items: center;
  margin: 30rpx 4rpx 20rpx;
}

.section-title {
  flex: 1;
  display: flex;
  align-items: center;
  font-size: 32rpx;
  font-weight: 900;
  letter-spacing: -0.3rpx;
  color: #f7c66a;
}

.section-title::after {
  content: "";
  flex: 1;
  height: 1rpx;
  margin-left: 18rpx;
  background: linear-gradient(90deg, rgba(247, 198, 106, 0.3), transparent);
}

.count { margin-left: 14rpx; color: rgba(255,255,255,0.5); font-size: 24rpx; font-weight: 600; }
.section-action { color: #f7c66a; font-size: 24rpx; font-weight: 700; }

/* ===== Grid & Cards ===== */
.grid {
  display: flex;
  flex-wrap: wrap;
  gap: 20rpx 14rpx;
}

.card {
  width: calc((100% - 28rpx) / 3);
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  transition: transform 0.2s ease;
}

.card:active { transform: scale(0.97); }

.cover {
  position: relative;
  width: 100%;
  aspect-ratio: 3 / 4;
  border-radius: 18rpx;
  overflow: hidden;
  background: rgba(0,0,0,0.3);
  box-shadow: 0 8rpx 20rpx rgba(0, 0, 0, 0.4);
}

.cover image {
  width: 100%;
  height: 100%;
  position: absolute;
  top: 0; left: 0;
}

.cover-overlay {
  position: absolute;
  left: 0; right: 0; bottom: 0;
  height: 90rpx;
  background: linear-gradient(to top, rgba(0,0,0,0.75), transparent);
  pointer-events: none;
}

.badge {
  position: absolute;
  top: 10rpx; left: 10rpx;
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  background: rgba(17,16,13,0.85);
  border: 1rpx solid rgba(247,198,106,0.4);
  color: #f7c66a;
  font-size: 18rpx;
  font-weight: 700;
}

.play {
  position: absolute;
  right: 10rpx; bottom: 10rpx;
  width: 48rpx; height: 48rpx;
  border-radius: 50%;
  background: rgba(255,255,255,0.92);
  box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.18s ease;
}

.play:active { background: linear-gradient(135deg, #ffe0a1, #f3b84d); transform: scale(0.9); }
.play:active .play-ic { color: #11100d; }
.play-ic { font-size: 16rpx; color: #11100d; margin-left: 2rpx; font-weight: 900; }

.ep-badge {
  position: absolute;
  left: 0; right: 0; bottom: 12rpx;
  text-align: center;
  color: rgba(255,255,255,0.9);
  font-size: 18rpx;
  font-weight: 600;
}

.card-title {
  margin-top: 10rpx;
  font-size: 25rpx;
  font-weight: 600;
  color: #ffffff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.state {
  padding: 140rpx 0;
  text-align: center;
  color: rgba(255,255,255,0.56);
  font-size: 26rpx;
  font-weight: 600;
}

/* ===== Filter Sheet ===== */
.filter-mask {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.55);
  z-index: 999;
  display: flex;
  align-items: flex-end;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }

.filter-sheet {
  width: 100%;
  background: #141623;
  border-radius: 24rpx 24rpx 0 0;
  padding: 24rpx 32rpx 40rpx;
  padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
  max-height: 80vh;
  overflow-y: auto;
  animation: slideUp 0.28s cubic-bezier(0.22, 1, 0.36, 1);
}

@keyframes slideUp {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}

.sheet-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 20rpx;
  margin-bottom: 8rpx;
  border-bottom: 1rpx solid rgba(255,255,255,0.08);
}

.sheet-title { font-size: 32rpx; font-weight: 700; color: #fff; }

.sheet-close {
  color: rgba(255,255,255,0.5);
  font-size: 28rpx;
  padding: 8rpx;
}

.sheet-groups { padding-top: 8rpx; }
.group + .group { margin-top: 24rpx; }

.group-title {
  display: flex;
  align-items: center;
  margin: 10rpx 4rpx 16rpx;
  color: rgba(255,255,255,0.55);
  font-size: 26rpx;
  font-weight: 700;
  letter-spacing: 2rpx;
}

.group-clear {
  margin-left: auto;
  font-size: 22rpx;
  font-weight: 600;
  color: #f7c66a;
  padding: 6rpx 14rpx;
}

.chip-row { display: flex; flex-wrap: wrap; gap: 12rpx 12rpx; }

.sheet-btn {
  width: 100%;
  margin-top: 36rpx;
  height: 88rpx;
  line-height: 88rpx;
  padding: 0;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  color: #11100d;
  border: none;
  border-radius: 44rpx;
  font-size: 30rpx;
  font-weight: 900;
  letter-spacing: 2rpx;
  box-shadow:
    0 14rpx 32rpx rgba(247, 198, 106, 0.32),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
  transition: transform 0.18s ease;
}

.sheet-btn:active { transform: scale(0.97); }
</style>