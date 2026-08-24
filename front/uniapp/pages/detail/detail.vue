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
            <button class="ghost" @click="toggleFavorite">{{ drama.favorite ? t('saved') : t('save') }}</button>
          </view>
        </view>
      </view>

      <view class="section-head">
        <view class="section-title">{{ t('episodes') }}</view>
        <button v-if="wholePrice && hasLockedEpisode" class="unlock-all" @click="unlockWhole">{{ t('unlockDrama') }} · {{ wholePrice }}</button>
      </view>

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

      <view class="section-head">
        <view class="section-title">{{ t('recommend') }}</view>
      </view>
      <view class="recommend">
        <view v-for="item in recommendations" :key="item.id" class="poster" @click="openDrama(item.id)">
          <image :src="coverOf(item)" mode="aspectFill" />
          <text>{{ item.title }}</text>
        </view>
      </view>
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
</style>
