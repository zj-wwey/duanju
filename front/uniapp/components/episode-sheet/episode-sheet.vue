<template>
  <u-popup :value="show" mode="bottom" height="820rpx" border-radius="20" :closeable="true" @input="onPopupInput">
    <view class="sheet">
      <view class="sheet-head">
        <view class="sheet-title">{{ dramaTitle }}</view>
        <view class="sheet-sub">{{ t('seasonTotal', { total: episodeList.length }) }}</view>
      </view>
      <view class="sheet-tabs">
        <view class="sheet-tab" :class="{ active: sheetTab === 'intro' }" @click="sheetTab = 'intro'">{{ t('synopsisTab') }}</view>
        <view class="sheet-tab" :class="{ active: sheetTab === 'episodes' }" @click="sheetTab = 'episodes'">{{ t('episodesTab') }}</view>
        <view v-if="hasSeries" class="sheet-tab" :class="{ active: sheetTab === 'series' }" @click="sheetTab = 'series'">{{ t('seriesTab') }}</view>
      </view>

      <view v-if="sheetTab === 'episodes'" class="sheet-body">
        <scroll-view v-if="episodePages.length > 1" scroll-x class="page-tabs">
          <view
            v-for="page in episodePages"
            :key="page.index"
            class="page-tab"
            :class="{ active: episodePage === page.index }"
            @click="episodePage = page.index"
          >
            {{ page.label }}
          </view>
        </scroll-view>
        <scroll-view scroll-y class="grid-scroll">
          <view class="grid">
            <view
              v-for="ep in pagedEpisodes"
              :key="ep.id"
              class="grid-item"
              :class="{ active: ep.id === activeId, locked: !ep.unlocked && !ep.videoUrl }"
              @click="select(ep)"
            >
              <text class="grid-no">{{ ep.no }}</text>
              <text v-if="ep.id === activeId" class="grid-state now">{{ t('nowPlaying') }}</text>
              <text v-else-if="!ep.unlocked && !ep.videoUrl" class="grid-state lock">{{ ep.pricePoints }} {{ t('credits') }}</text>
              <text v-else class="grid-state free">{{ t('unlocked') }}</text>
            </view>
          </view>
        </scroll-view>
      </view>

      <scroll-view v-else-if="sheetTab === 'intro'" scroll-y class="sheet-body intro-scroll">
        <view class="intro-cover" v-if="dramaCover">
          <image :src="dramaCover" mode="aspectFill" />
        </view>
        <view class="intro-text">{{ dramaDescription }}</view>
      </scroll-view>

      <scroll-view v-else scroll-y class="sheet-body series-scroll">
        <view class="series-list">
          <view
            v-for="d in seriesList"
            :key="d.id"
            class="series-item"
            :class="{ current: isCurrentDrama(d.id) }"
            @click="onSwitchDrama(d.id)"
          >
            <image :src="coverOf(d)" mode="aspectFill" />
            <view class="series-copy">
              <text class="series-name">{{ pick(d, 'title') || '' }}</text>
              <text class="series-meta">{{ seriesMeta(d) }}</text>
            </view>
            <text v-if="isCurrentDrama(d.id)" class="series-playing">{{ t('nowPlaying') }}</text>
          </view>
        </view>
      </scroll-view>
    </view>
  </u-popup>
</template>

<script>
import { t as translate, getLocale } from '../../utils/i18n.js'

const PAGE_SIZE = 30

export default {
  name: 'episode-sheet',
  props: {
    show: { type: Boolean, default: false },
    drama: { type: Object, default: null },
    // 兼容首页 meunList 与播放页 episodes 两种字段命名
    episodes: { type: Array, default: () => [] },
    activeId: { type: [String, Number], default: null },
    dramas: { type: Array, default: () => [] }
  },
  data() {
    return {
      sheetTab: 'episodes',
      episodePage: 0,
      locale: getLocale()
    }
  },
  computed: {
    episodeList() {
      return (this.episodes || []).map((item, index) => ({
        raw: item,
        id: this.pick(item, 'courseDetailsId', 'id'),
        no: this.pick(item, 'num', 'episodeNo', 'episode_no') || index + 1,
        videoUrl: this.pick(item, 'videoUrl', 'video_url') || '',
        unlocked: !!item.unlocked,
        pricePoints: Number(this.pick(item, 'pricePoints', 'price_points') || 0)
      }))
    },
    episodePages() {
      const total = this.episodeList.length
      if (!total) return []
      const pages = []
      for (let start = 0; start < total; start += PAGE_SIZE) {
        const end = Math.min(start + PAGE_SIZE, total)
        pages.push({ index: pages.length, label: (start + 1) + '-' + end })
      }
      return pages
    },
    pagedEpisodes() {
      return this.episodeList.slice(this.episodePage * PAGE_SIZE, this.episodePage * PAGE_SIZE + PAGE_SIZE)
    },
    dramaTitle() {
      return this.drama ? (this.pick(this.drama, 'title') || this.t('appTitle')) : ''
    },
    dramaCover() {
      return this.drama ? (this.pick(this.drama, 'cover_url', 'coverUrl') || '') : ''
    },
    dramaDescription() {
      return this.drama ? (this.pick(this.drama, 'description') || this.t('noSynopsis')) : ''
    },
    hasSeries() {
      return (this.dramas || []).length > 0
    },
    seriesList() {
      const list = (this.dramas || []).filter(item => !this.drama || String(item.id) !== String(this.drama.id))
      return list.length ? list : (this.dramas || [])
    }
  },
  watch: {
    show(val) {
      if (val) {
        this.locateActive()
      }
    }
  },
  methods: {
    t(key, params) {
      return translate(key, params, this.locale)
    },
    pick(source, ...keys) {
      for (const key of keys) {
        if (source && source[key] !== undefined && source[key] !== null) {
          return source[key]
        }
      }
      return undefined
    },
    locateActive() {
      this.sheetTab = 'episodes'
      const activeIndex = this.episodeList.findIndex(ep => String(ep.id) === String(this.activeId))
      this.episodePage = activeIndex >= 0 ? Math.floor(activeIndex / PAGE_SIZE) : 0
    },
    onPopupInput(val) {
      if (!val) this.$emit('update:show', false)
    },
    select(ep) {
      this.$emit('select', ep.raw)
    },
    isCurrentDrama(id) {
      return this.drama && String(id) === String(this.drama.id)
    },
    onSwitchDrama(id) {
      if (this.isCurrentDrama(id)) return
      this.$emit('switch-drama', id)
    },
    coverOf(item) {
      return this.pick(item, 'cover_url', 'coverUrl', 'titleImg') || ''
    },
    seriesMeta(item) {
      const total = Number(this.pick(item, 'total_episodes', 'totalEpisodes') || 0)
      return total > 0 ? this.t('episodeCount', { total }) : ''
    }
  }
}
</script>

<style lang="scss" scoped>
.sheet {
  background: linear-gradient(180deg, #0d0f15 0%, #07080b 100%);
  color: #fff;
  padding: 22rpx 18rpx 28rpx;
}

.sheet-head {
  padding: 4rpx 6rpx 2rpx;
}

.sheet-title {
  font-size: 32rpx;
  font-weight: 800;
  letter-spacing: -0.3rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sheet-sub {
  margin-top: 6rpx;
  font-size: 22rpx;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.6);
}

.sheet-tabs {
  display: flex;
  gap: 28rpx;
  margin-top: 18rpx;
  padding: 0 6rpx;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.sheet-tab {
  position: relative;
  padding: 14rpx 2rpx;
  font-size: 26rpx;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.55);
  transition: color 0.2s ease;
}

.sheet-tab.active {
  color: #fff;
}

.sheet-tab.active::after {
  content: "";
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 44rpx;
  height: 6rpx;
  transform: translateX(-50%);
  background: linear-gradient(90deg, #ffe0a1, #f3b84d);
  border-radius: 999rpx;
  box-shadow: 0 2rpx 10rpx rgba(247, 198, 106, 0.5);
}

.sheet-body {
  margin-top: 16rpx;
}

.page-tabs {
  white-space: nowrap;
  margin-bottom: 16rpx;
}

.page-tab {
  display: inline-flex;
  align-items: center;
  height: 52rpx;
  margin-right: 12rpx;
  padding: 0 22rpx;
  font-size: 22rpx;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.06);
  transition: all 0.2s ease;
}

.page-tab.active {
  color: #07080b;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  border-color: transparent;
  box-shadow: 0 6rpx 16rpx rgba(247, 198, 106, 0.42);
}

.grid-scroll {
  height: 470rpx;
}

.grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14rpx;
}

.grid-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 96rpx;
  border-radius: 16rpx;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: linear-gradient(160deg, rgba(255, 255, 255, 0.08), rgba(255, 255, 255, 0.025)), rgba(15, 17, 24, 0.8);
  transition: transform 0.18s ease;
}

.grid-item:active {
  transform: scale(0.93);
}

.grid-item.active {
  border-color: rgba(247, 198, 106, 0.65);
  background: linear-gradient(160deg, rgba(247, 198, 106, 0.2), rgba(255, 255, 255, 0.04)), rgba(15, 17, 24, 0.85);
  box-shadow: 0 6rpx 18rpx rgba(247, 198, 106, 0.22);
}

.grid-item.locked {
  border-color: rgba(255, 255, 255, 0.08);
  opacity: 0.72;
}

.grid-no {
  font-size: 30rpx;
  font-weight: 800;
  color: #fff;
}

.grid-item.active .grid-no {
  color: #f7c66a;
}

.grid-state {
  margin-top: 4rpx;
  font-size: 16rpx;
  font-weight: 700;
}

.grid-state.now {
  color: #f7c66a;
}

.grid-state.free {
  color: rgba(85, 229, 155, 0.85);
}

.grid-state.lock {
  color: rgba(255, 255, 255, 0.45);
}

.intro-scroll {
  height: 470rpx;
}

.intro-cover {
  width: 240rpx;
  height: 330rpx;
  margin: 4rpx auto 18rpx;
  border-radius: 18rpx;
  overflow: hidden;
  background: #1a1d26;
  box-shadow: 0 10rpx 26rpx rgba(0, 0, 0, 0.4);
}

.intro-cover image {
  width: 100%;
  height: 100%;
}

.intro-text {
  padding: 0 6rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.78);
}

.series-scroll {
  height: 470rpx;
}

.series-item {
  display: flex;
  align-items: center;
  margin-bottom: 14rpx;
  padding: 12rpx;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 16rpx;
  background: linear-gradient(160deg, rgba(255, 255, 255, 0.08), rgba(255, 255, 255, 0.025)), rgba(15, 17, 24, 0.8);
  transition: transform 0.18s ease;
}

.series-item:active {
  transform: scale(0.98);
}

.series-item.current {
  border-color: rgba(247, 198, 106, 0.55);
  background: linear-gradient(160deg, rgba(247, 198, 106, 0.14), rgba(255, 255, 255, 0.03)), rgba(15, 17, 24, 0.85);
}

.series-item image {
  width: 96rpx;
  height: 128rpx;
  border-radius: 12rpx;
  background: #1a1d26;
  flex-shrink: 0;
}

.series-copy {
  flex: 1;
  min-width: 0;
  margin-left: 14rpx;
}

.series-name {
  display: block;
  font-size: 26rpx;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.series-meta {
  display: block;
  margin-top: 6rpx;
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.55);
}

.series-playing {
  flex-shrink: 0;
  margin-left: 10rpx;
  padding: 4rpx 12rpx;
  font-size: 18rpx;
  font-weight: 800;
  color: #0c6f47;
  background: rgba(85, 229, 155, 0.18);
  border: 1px solid rgba(85, 229, 155, 0.4);
  border-radius: 999rpx;
}
</style>
