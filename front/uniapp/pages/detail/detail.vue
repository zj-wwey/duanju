<template>
  <view class="page">
    <view v-if="loading" class="state">{{ t('loading') }}</view>
    <template v-else-if="drama">
      <view class="hero">
        <image class="cover" :src="coverOf(drama)" mode="aspectFill" />
        <view class="copy">
          <view class="kicker">{{ drama.contentType === 'ai' || drama.content_type === 'ai' ? 'AI Drama' : 'Short Drama' }}</view>
          <view class="title">{{ drama.title }}</view>
          <view class="meta">{{ episodeTotalText }}</view>
          <view class="desc">{{ drama.description || t('noSynopsis') }}</view>
          <view class="buttons">
            <button class="primary" @click="playFirst">{{ t('play') }}</button>
            <button class="ghost" @click="toggleFavorite">{{ drama.favorite ? t('favorited') : t('favorite') }}</button>
          </view>
        </view>
      </view>

      <view class="section-head">
        <view class="section-title">{{ t('recommend') }}</view>
      </view>
      <view class="recommend">
        <view v-for="item in recommendations" :key="item.id" class="poster" @click="openDrama(item.id)">
          <image :src="coverOf(item)" mode="aspectFill" />
          <text>{{ item.title }}</text>
        </view>
      </view>

      <!-- 底部固定选集栏（悬浮于底部 tabbar 之上） -->
      <view class="episode-dock" @click="showEpisodes = true">
        <view class="episode-dock-icon">
          <u-icon name="list-dot" size="42" color="#11100d" />
        </view>
        <view class="episode-dock-text">
          <view class="episode-dock-title">{{ t('episodes') }}</view>
          <view class="episode-dock-sub">{{ episodeTotalText || ('共 ' + episodes.length + ' 集') }}</view>
        </view>
        <view class="episode-dock-go">
          <u-icon name="arrow-up" size="32" color="#11100d" />
        </view>
      </view>

      <!-- 选集弹出层 -->
      <u-popup v-model="showEpisodes" mode="bottom" border-radius="24" :closeable="true">
        <view class="episode-sheet">
          <view class="section-head episode-sheet-head">
            <view class="section-title">{{ t('episodes') }}</view>
            <button v-if="wholePrice && hasLockedEpisode" class="unlock-all" @click="unlockWhole">{{ t('unlockDrama') }} · {{ wholePrice }}</button>
          </view>
          <scroll-view scroll-y class="episode-sheet-scroll">
            <view class="episode-grid">
              <view
                v-for="episode in episodes"
                :key="episode.id"
                class="episode"
                :class="{ locked: !episode.unlocked && !episode.videoUrl }"
                @click="playEpisode(episode)"
              >
                <view class="episode-no">{{ episode.episodeNo }}</view>
                <view class="episode-title">{{ episode.title }}</view>
                <view class="episode-status">{{ episode.unlocked || episode.videoUrl ? t('unlocked') : episode.pricePoints + ' ' + t('credits') }}</view>
              </view>
            </view>
          </scroll-view>
        </view>
      </u-popup>
    </template>
    <view v-else class="state">{{ t('noTitles') }}</view>
    <app-tab-bar />
  </view>
</template>

<script>
import api from '../../utils/api.js'
import { getLocale, t as translate } from '../../utils/i18n.js'
import { notifyDataChanged, APP_DATA_EVENTS } from '../../utils/app-state.js'

export default {
  data() {
    return {
      id: null,
      drama: null,
      episodes: [],
      recommendations: [],
      loading: true,
      showEpisodes: false,
      locale: getLocale()
    }
  },
  computed: {
    episodeTotalText() {
      const total = this.drama?.totalEpisodes || this.drama?.total_episodes || this.episodes.length
      return total ? total + ' Episodes' : ''
    },
    wholePrice() {
      return Number(this.drama?.wholePricePoints || this.drama?.whole_price_points || 0)
    },
    hasLockedEpisode() {
      return this.episodes.some(item => !item.unlocked && !item.videoUrl)
    }
  },
  onLoad(options) {
    this.id = options.id
    this.load()
  },
  onShow() {
    this.locale = getLocale()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const [drama, dramas] = await Promise.all([
          api.drama(this.id),
          api.dramas()
        ])
        this.drama = drama
        this.episodes = (drama.episodes || []).map((item, index) => this.normalizeEpisode(item, index))
        this.recommendations = (dramas || []).filter(item => String(item.id) !== String(this.id)).slice(0, 8)
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
      } finally {
        this.loading = false
      }
    },
    normalizeEpisode(item, index) {
      const episodeNo = item.episodeNo || item.episode_no || index + 1
      return {
        id: item.id,
        title: item.title || this.t('episodeTitle', { num: episodeNo }),
        episodeNo,
        videoUrl: item.videoUrl || item.video_url,
        coverUrl: item.coverUrl || item.cover_url || this.coverOf(this.drama),
        unlocked: Boolean(item.unlocked),
        pricePoints: Number(item.pricePoints || item.price_points || 0)
      }
    },
    playFirst() {
      this.playEpisode(this.episodes[0])
    },
    playEpisode(episode) {
      if (!episode) return
      this.showEpisodes = false
      uni.navigateTo({ url: '/pages/player/player?dramaId=' + this.id + '&episodeId=' + episode.id })
    },
    async toggleFavorite() {
      if (!this.ensureLogin()) return
      try {
        const res = await api.toggleFavorite(this.id)
        this.drama.favorite = Boolean(res.favorite)
        notifyDataChanged(APP_DATA_EVENTS.favorite, { dramaId: this.id, favorite: this.drama.favorite })
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
      }
    },
    async unlockWhole() {
      if (!this.ensureLogin()) return
      try {
        await api.unlockDrama(this.id)
        uni.showToast({ title: this.t('unlockedToast'), icon: 'none' })
        notifyDataChanged(APP_DATA_EVENTS.points)
        await this.load()
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
        this.offerRecharge(err.message)
      }
    },
    offerRecharge(message) {
      if (!/point|credit|积分|余额|insufficient/i.test(message || '')) return
      setTimeout(() => uni.redirectTo({ url: '/pages/store/store?tab=recharge' }), 600)
    },
    openDrama(id) {
      uni.redirectTo({ url: '/pages/detail/detail?id=' + id })
    },
    coverOf(item) {
      return item?.coverUrl || item?.cover_url || item?.titleImg || ''
    },
    ensureLogin() {
      if (uni.getStorageSync('token')) return true
      uni.navigateTo({ url: '/pages/login/login' })
      return false
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
  background:
    radial-gradient(circle at 12% 4%, rgba(247, 198, 106, 0.16), transparent 38%),
    radial-gradient(circle at 90% 14%, rgba(77, 208, 225, 0.1), transparent 40%),
    #080a10;
  color: #fff;
  padding: 28rpx;
  box-sizing: border-box;
  font-family: -apple-system, BlinkMacSystemFont, "Inter", "SF Pro Display", "Segoe UI", sans-serif;
}

.state {
  padding: 200rpx 0;
  text-align: center;
  color: rgba(255,255,255,0.62);
  font-size: 28rpx;
  font-weight: 600;
}

.hero {
  position: relative;
  display: flex;
  gap: 26rpx;
  padding: 28rpx;
  border-radius: 28rpx;
  background: linear-gradient(145deg, rgba(247,198,106,0.18), rgba(77,208,225,0.08)), rgba(12,15,24,0.85);
  border: 1rpx solid rgba(255,255,255,0.16);
  box-shadow:
    0 28rpx 70rpx rgba(0, 0, 0, 0.36),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(20rpx);
  overflow: hidden;
}

.hero::before {
  content: "";
  position: absolute;
  top: -40rpx;
  right: -40rpx;
  width: 200rpx;
  height: 200rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(247, 198, 106, 0.16), transparent 70%);
  filter: blur(20rpx);
  pointer-events: none;
}

.cover {
  position: relative;
  z-index: 1;
  width: 240rpx;
  height: 330rpx;
  border-radius: 20rpx;
  background: #1a1d26;
  box-shadow:
    0 14rpx 32rpx rgba(0, 0, 0, 0.5),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.1);
}

.copy {
  flex: 1;
  min-width: 0;
  position: relative;
  z-index: 1;
}

.kicker {
  display: flex;
  align-items: center;
  color: #f7c66a;
  font-size: 22rpx;
  font-weight: 800;
  letter-spacing: 3rpx;
  text-transform: uppercase;
}

.kicker::before {
  content: "";
  display: inline-block;
  width: 28rpx;
  height: 3rpx;
  margin-right: 12rpx;
  background: linear-gradient(90deg, transparent, #f7c66a);
  border-radius: 2rpx;
}

.title {
  margin-top: 10rpx;
  font-size: 42rpx;
  line-height: 1.12;
  font-weight: 800;
  letter-spacing: -0.5rpx;
  background: linear-gradient(135deg, #ffffff 30%, rgba(255, 255, 255, 0.82) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.meta,
.desc {
  margin-top: 14rpx;
  color: rgba(255,255,255,0.66);
  font-size: 24rpx;
  line-height: 1.5;
}

.desc {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.buttons {
  display: flex;
  gap: 14rpx;
  margin-top: 24rpx;
}

.primary,
.ghost,
.unlock-all {
  height: 70rpx;
  line-height: 70rpx;
  border-radius: 999rpx;
  font-size: 25rpx;
  font-weight: 800;
  letter-spacing: 0.5rpx;
  padding: 0 28rpx;
  transition: transform 0.18s ease;
}

.primary {
  color: #11100d;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  box-shadow:
    0 14rpx 32rpx rgba(247, 198, 106, 0.36),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
}

.primary:active {
  transform: scale(0.95);
}

.ghost,
.unlock-all {
  color: #fff;
  background: rgba(255,255,255,0.1);
  border: 1rpx solid rgba(255,255,255,0.2);
  backdrop-filter: blur(10rpx);
}

.ghost:active,
.unlock-all:active {
  transform: scale(0.95);
  background: rgba(255, 255, 255, 0.16);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 38rpx 4rpx 20rpx;
}

.section-title {
  display: flex;
  align-items: center;
  font-size: 34rpx;
  font-weight: 800;
  letter-spacing: -0.3rpx;
}

.section-title::before {
  content: "";
  display: inline-block;
  width: 8rpx;
  height: 30rpx;
  margin-right: 16rpx;
  background: linear-gradient(180deg, #ffe0a1, #f3b84d);
  border-radius: 4rpx;
  box-shadow: 0 4rpx 12rpx rgba(247, 198, 106, 0.4);
}

.episode-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14rpx;
}

.episode {
  min-height: 116rpx;
  padding: 14rpx;
  border-radius: 20rpx;
  background: linear-gradient(160deg, rgba(255,255,255,0.1), rgba(255,255,255,0.03)), rgba(15, 17, 24, 0.78);
  border: 1rpx solid rgba(255,255,255,0.12);
  transition: all 0.2s ease;
}

.episode:active {
  transform: scale(0.95);
  background: linear-gradient(160deg, rgba(247,198,106,0.16), rgba(255,255,255,0.05)), rgba(15, 17, 24, 0.85);
}

.episode.locked {
  border-color: rgba(247,198,106,0.4);
  background: linear-gradient(160deg, rgba(247,198,106,0.1), rgba(255,255,255,0.03)), rgba(15, 17, 24, 0.78);
}

.episode-no {
  color: #f7c66a;
  font-size: 30rpx;
  font-weight: 800;
  letter-spacing: -0.3rpx;
}

.episode-title,
.episode-status {
  margin-top: 6rpx;
  font-size: 20rpx;
  color: rgba(255,255,255,0.7);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommend {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18rpx;
}

.poster {
  transition: transform 0.2s ease;
}

.poster:active {
  transform: scale(0.96);
}

.poster image {
  width: 100%;
  height: 270rpx;
  border-radius: 18rpx;
  background: #1a1d26;
  box-shadow: 0 10rpx 26rpx rgba(0, 0, 0, 0.4);
}

.poster text {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  font-weight: 700;
  letter-spacing: 0.2rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 给底部固定选集栏 + tabbar 留位，避免推荐内容被遮挡 */
.page {
  padding-bottom: calc(220rpx + env(safe-area-inset-bottom));
}

/* ===== 底部固定选集栏 ===== */
.episode-dock {
  position: fixed;
  left: 24rpx;
  right: 24rpx;
  bottom: calc(112rpx + env(safe-area-inset-bottom));
  z-index: 998;
  display: flex;
  align-items: center;
  height: 96rpx;
  padding: 0 24rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  box-shadow:
    0 12rpx 30rpx rgba(247, 198, 106, 0.4),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.55);
  transition: transform 0.18s ease;
}

.episode-dock:active {
  transform: scale(0.97);
}

.episode-dock-icon {
  width: 56rpx;
  height: 56rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(17, 16, 13, 0.12);
}

.episode-dock-text {
  flex: 1;
  margin-left: 18rpx;
  min-width: 0;
}

.episode-dock-title {
  font-size: 28rpx;
  font-weight: 900;
  color: #11100d;
  line-height: 1.2;
}

.episode-dock-sub {
  margin-top: 4rpx;
  font-size: 22rpx;
  font-weight: 700;
  color: rgba(17, 16, 13, 0.66);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.episode-dock-go {
  width: 48rpx;
  height: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ===== 选集弹出层 ===== */
.episode-sheet {
  background: #0d0f15;
  color: #fff;
  padding: 12rpx 24rpx calc(32rpx + env(safe-area-inset-bottom));
  border-radius: 24rpx;
}

.episode-sheet-head {
  margin-top: 12rpx;
}

.episode-sheet-scroll {
  max-height: 60vh;
}
</style>
