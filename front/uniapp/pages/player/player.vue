<template>
  <view class="page">
    <view v-if="loading" class="loading-state">
      <view class="loading-spinner"></view>
      <text class="loading-text">{{ t('loading') }}</text>
    </view>

    <template v-else>
      <view class="screen">
        <view v-if="isDownloading" class="downloading-mask">
          <view class="downloading-spinner"></view>
          <view class="downloading-text">{{ t('downloadingVideo') }} {{ downloadProgress }}%</view>
          <view class="downloading-bar">
            <view class="downloading-bar-fill" :style="{ width: downloadProgress + '%' }"></view>
          </view>
        </view>

        <video
          v-if="activeEpisode && activeEpisode.playbackUrl && !isDownloading"
          id="mainVideo"
          class="video"
          :src="activeEpisode.playbackUrl"
          :poster="activeEpisode.coverUrl || coverOf(drama)"
          :autoplay="true"
          :controls="false"
          :show-center-play-btn="false"
          :show-fullscreen-btn="false"
          :show-progress="false"
          :show-play-btn="false"
          object-fit="cover"
          @timeupdate="timeupdate"
          @ended="ended"
          @error="videoError"
          @play="onVideoPlay"
          @pause="onVideoPause"
        >
          <cover-view class="video-tap-area" @tap="togglePlayback"></cover-view>
        </video>

        <view v-else-if="activeEpisode && activeEpisode.videoUrl && !isDownloading && videoLoadError" class="locked-screen">
          <image class="locked-cover" :src="activeEpisode.coverUrl || coverOf(drama)" mode="aspectFill" />
          <view class="locked-mask">
            <view class="locked-title">{{ videoLoadError }}</view>
            <view class="locked-desc">{{ activeEpisode ? (activeEpisode.playbackUrl || activeEpisode.videoUrl) : '' }}</view>
            <button class="primary-btn" @click="playActiveEpisode">{{ t('retry') }}</button>
          </view>
        </view>

        <view v-else-if="activeEpisode && !activeEpisode.videoUrl" class="locked-screen">
          <image class="locked-cover" :src="activeEpisode ? (activeEpisode.coverUrl || coverOf(drama)) : coverOf(drama)" mode="aspectFill" />
          <view class="locked-mask">
            <view class="locked-title">{{ t('premiumEpisode') }}</view>
            <view class="locked-desc">{{ t('unlockSubtitle', { count: activeEpisode ? activeEpisode.pricePoints : 0 }) }}</view>
            <button class="primary-btn" @click="unlockEpisode">{{ t('unlockWithCredits') }}</button>
            <button v-if="wholePrice" class="ghost-btn" @click="unlockWhole">{{ t('unlockDrama') }} · {{ wholePrice }}</button>
            <button class="plain-btn" @click="goRecharge">{{ t('rechargeCredits') }}</button>
          </view>
        </view>

        <view v-else class="locked-screen">
          <image class="locked-cover" :src="coverOf(drama)" mode="aspectFill" />
        </view>
      </view>

      <!-- 选集弹窗 -->
      <u-popup v-model="showEpisodePanel" mode="bottom" height="560rpx" border-radius="20" :closeable="true">
        <view class="panel">
          <view class="panel-title">{{ t('episodeTitle', { num: activeEpisode ? activeEpisode.episodeNo : 1 }) }}</view>
          <scroll-view scroll-y class="panel-scroll">
            <view
              v-for="item in episodes"
              :key="item.id"
              class="panel-item"
              :class="{ active: activeEpisode && item.id === activeEpisode.id }"
              @click="selectEpisode(item)"
            >
              <image :src="item.coverUrl || coverOf(drama)" mode="aspectFill" />
              <view class="panel-item-copy">
                <view class="panel-item-title">{{ t('episodeTitle', { num: item.episodeNo }) }} · {{ item.title }}</view>
                <view class="panel-item-sub">{{ item.videoUrl || item.unlocked ? t('unlocked') : item.pricePoints + ' ' + t('credits') }}</view>
              </view>
            </view>
          </scroll-view>
        </view>
      </u-popup>
    </template>
  </view>
</template>

<script>
import api from '../../utils/api.js'
import { getLocale, t as translate } from '../../utils/i18n.js'
import { notifyDataChanged, APP_DATA_EVENTS } from '../../utils/app-state.js'
import { resolveEpisodeSource } from '../../utils/playback.js'

export default {
  data() {
    return {
      dramaId: null,
      episodeId: null,
      drama: null,
      episodes: [],
      activeEpisode: null,
      pointBalance: 0,
      autoNext: true,
      progressSeconds: 0,
      progressPercent: 0,
      videoDuration: 0,
      lastSavedSecond: -1,
      lastRewardedMinute: 0,
      rewardedEpisodeId: null,
      videoLoadError: '',
      loading: true,
      locale: getLocale(),
      playRetryCount: 0,
      MAX_PLAY_RETRY: 1,
      feedPaused: false,
      showEpisodePanel: false,
      favorite: false,
      isDownloading: false,
      downloadProgress: 0,
      downloadedFilePath: ''
    }
  },
  computed: {
    wholePrice() {
      return Number(this.drama?.wholePricePoints || this.drama?.whole_price_points || 0)
    }
  },
  onLoad(options) {
    this.dramaId = options.dramaId || options.id
    this.episodeId = options.episodeId || options.courseDetailsId
    this.load()
  },
  onShow() {
    this.locale = getLocale()
    this.loadPoints()
    uni.$on('playerOverlayEvent', this.handleOverlayEvent)
    this.showOverlay()
  },
  onHide() {
    uni.$off('playerOverlayEvent', this.handleOverlayEvent)
    this.hideOverlay()
  },
  onUnload() {
    if (this.downloadedFilePath) {
      uni.removeSavedFile({
        filePath: this.downloadedFilePath,
        complete: () => {}
      })
      this.downloadedFilePath = ''
    }
    uni.$off('playerOverlayEvent', this.handleOverlayEvent)
    this.hideOverlay()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const [drama, settings] = await Promise.all([
          api.drama(this.dramaId),
          uni.getStorageSync('token') ? api.getSettings().catch(() => null) : Promise.resolve(null)
        ])
        this.drama = drama
        this.favorite = !!drama.favorite
        this.episodes = (drama.episodes || []).map((item, index) => this.normalizeEpisode(item, index))
        this.activeEpisode = this.episodes.find(item => String(item.id) === String(this.episodeId)) || this.episodes[0] || null
        if (settings) this.autoNext = settings.autoNext !== false && settings.auto_next !== false
        await this.loadPoints()
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
      } finally {
        this.loading = false
        this.$nextTick(() => this.playActiveEpisode())
        this.sendOverlayUpdate()
      }
    },
    sendOverlayUpdate() {
      uni.$emit('updatePlayerOverlay', {
        favorite: this.favorite,
        favoriteText: this.favorite ? this.t('saved') : this.t('save'),
        likeText: this.t('like'),
        commentText: this.t('comment'),
        shareText: this.t('share'),
        detailsText: this.t('details'),
        episodeBrowseText: this.t('episodeBrowse', { num: this.activeEpisode ? this.activeEpisode.episodeNo : 1, total: this.episodes.length }),
        dramaTitle: this.drama ? this.drama.title : '',
        episodeTitle: this.activeEpisode ? this.activeEpisode.title : '',
        episodeNo: this.activeEpisode ? this.activeEpisode.episodeNo : 1,
        total: this.episodes.length,
        progressPercent: this.progressPercent || 0
      })
    },
    showOverlay() {
      const ov = uni.getSubNVueById('playerOverlay')
      if (ov) {
        ov.show()
        this.sendOverlayUpdate()
      }
    },
    hideOverlay() {
      const ov = uni.getSubNVueById('playerOverlay')
      if (ov) ov.hide()
    },
    handleOverlayEvent(e) {
      if (!e || !e.type) return
      switch (e.type) {
        case 'back': this.back(); break
        case 'favorite': this.toggleFavorite(); break
        case 'like': this.toggleLike(); break
        case 'comment': this.openComments(); break
        case 'share': this.share(); break
        case 'details': this.goDetail(); break
        case 'episode': this.toggleEpisodePanel(); break
        case 'tap': this.togglePlayback(); break
      }
    },
    normalizeEpisode(item, index) {
      const episodeNo = item.episodeNo || item.episode_no || index + 1
      return {
        id: item.id,
        title: item.title || '',
        episodeNo,
        videoUrl: item.videoUrl || item.video_url,
        playbackUrl: '',
        coverUrl: item.coverUrl || item.cover_url,
        unlocked: Boolean(item.unlocked),
        pricePoints: Number(item.pricePoints || item.price_points || 0)
      }
    },
    async loadPoints() {
      if (!uni.getStorageSync('token')) {
        this.pointBalance = 0
        return
      }
      try {
        const data = await api.points()
        this.pointBalance = Number(data?.user?.points || data?.points || 0)
      } catch (_) {}
    },
    openEpisode(item) {
      this.saveHistory()
      this.activeEpisode = item
      this.episodeId = item.id
      this.progressSeconds = 0
      this.progressPercent = 0
      this.lastSavedSecond = -1
      this.lastRewardedMinute = 0
      this.videoLoadError = ''
      this.playRetryCount = 0
      this.feedPaused = false
      if (!item.videoUrl) return
      this.playActiveEpisode()
    },
    async playActiveEpisode() {
      const item = this.activeEpisode
      if (!item || !item.videoUrl) return
      const episodeId = item.id
      this.videoLoadError = ''
      try {
        const source = await resolveEpisodeSource(item.id, item.videoUrl, api)
        if (!this.activeEpisode || this.activeEpisode.id !== episodeId) return
        this.playRetryCount = 0
        this.$set(this.activeEpisode, 'playbackUrl', source)
        this.$nextTick(() => {
          uni.createVideoContext('mainVideo', this).play()
        })
      } catch (err) {
        if (this.activeEpisode && this.activeEpisode.id === episodeId) {
          this.videoLoadError = err.message || this.t('videoLoadFailed')
        }
      }
    },
    videoError(e) {
      if (!this.activeEpisode) return
      this.videoLoadError = '视频加载失败，请点击重试'
      this.$set(this.activeEpisode, 'playbackUrl', '')
    },
    async unlockEpisode() {
      if (!this.ensureLogin() || !this.activeEpisode) return
      try {
        await api.unlock(this.activeEpisode.id)
        uni.showToast({ title: this.t('unlockedToast'), icon: 'none' })
        notifyDataChanged(APP_DATA_EVENTS.points)
        await this.load()
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
        this.offerRecharge(err.message)
      }
    },
    async unlockWhole() {
      if (!this.ensureLogin()) return
      try {
        await api.unlockDrama(this.dramaId)
        uni.showToast({ title: this.t('unlockedToast'), icon: 'none' })
        notifyDataChanged(APP_DATA_EVENTS.points)
        await this.load()
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
        this.offerRecharge(err.message)
      }
    },
    timeupdate(e) {
      this.progressSeconds = Math.floor(e.detail.currentTime || 0)
      const duration = e.detail.duration || this.videoDuration || 0
      if (duration > 0) {
        this.videoDuration = duration
        this.progressPercent = Math.min(100, (this.progressSeconds / duration) * 100)
      }
      if (this.progressSeconds > 0 && this.progressSeconds % 10 === 0) this.saveHistory()
      const watchedMinute = Math.floor(this.progressSeconds / 60)
      if (watchedMinute > this.lastRewardedMinute) {
        this.lastRewardedMinute = watchedMinute
        api.rewardDuration(1).then(() => {
          notifyDataChanged(APP_DATA_EVENTS.points)
          this.loadPoints()
        }).catch(() => {})
      }
      if (this.progressSeconds % 2 === 0) this.sendOverlayUpdate()
    },
    ended() {
      this.saveHistory()
      this.progressPercent = 0
      if (this.activeEpisode && this.rewardedEpisodeId !== this.activeEpisode.id) {
        this.rewardedEpisodeId = this.activeEpisode.id
        api.rewardEpisode(this.activeEpisode.id).then(() => {
          notifyDataChanged(APP_DATA_EVENTS.points)
          this.loadPoints()
        }).catch(() => {})
      }
      if (!this.autoNext) return
      const index = this.episodes.findIndex(item => this.activeEpisode && item.id === this.activeEpisode.id)
      const next = this.episodes[index + 1]
      if (next) this.openEpisode(next)
    },
    saveHistory() {
      if (!uni.getStorageSync('token') || !this.dramaId || !this.activeEpisode) return
      if (this.progressSeconds === this.lastSavedSecond) return
      this.lastSavedSecond = this.progressSeconds
      api.saveHistory({
        dramaId: this.dramaId,
        episodeId: this.activeEpisode.id,
        progressSeconds: this.progressSeconds
      }).catch(() => {})
    },
    onVideoPlay() {
      this.feedPaused = false
    },
    onVideoPause() {
      this.feedPaused = true
    },
    togglePlayback() {
      const context = uni.createVideoContext('mainVideo', this)
      if (this.feedPaused) {
        context.play()
        this.feedPaused = false
      } else {
        context.pause()
        this.feedPaused = true
      }
    },
    toggleEpisodePanel() {
      this.showEpisodePanel = !this.showEpisodePanel
    },
    selectEpisode(item) {
      this.showEpisodePanel = false
      this.openEpisode(item)
    },
    async toggleFavorite() {
      if (!this.ensureLogin()) return
      try {
        const res = await api.toggleFavorite(this.dramaId)
        this.favorite = res.favorite
        notifyDataChanged(APP_DATA_EVENTS.favorite, { dramaId: this.dramaId, favorite: this.favorite })
        this.sendOverlayUpdate()
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
      }
    },
    toggleLike() {
      if (!this.ensureLogin()) return
      uni.showToast({ title: this.t('likePending'), icon: 'none' })
    },
    openComments() {
      if (!this.ensureLogin()) return
      uni.showToast({ title: this.t('commentPending'), icon: 'none' })
    },
    share() {
      const item = this.activeEpisode
      if (!item) return
      const path = '/pages/player/player?dramaId=' + this.dramaId + '&episodeId=' + item.id
      uni.setClipboardData({ data: path })
      if (uni.getStorageSync('token') && this.dramaId) {
        api.rewardShare(this.dramaId).then(() => notifyDataChanged(APP_DATA_EVENTS.points)).catch(() => {})
      }
    },
    goDetail() {
      if (!this.drama) return
      uni.navigateTo({ url: '/pages/detail/detail?id=' + this.dramaId })
    },
    offerRecharge(message) {
      if (!/point|credit|积分|余额|insufficient/i.test(message || '')) return
      setTimeout(() => this.goRecharge(), 600)
    },
    goRecharge() {
      if (!this.ensureLogin()) return
      uni.navigateTo({ url: '/pages/store/store?tab=recharge' })
    },
    ensureLogin() {
      if (uni.getStorageSync('token')) return true
      uni.navigateTo({ url: '/pages/login/login' })
      return false
    },
    coverOf(item) {
      return item?.coverUrl || item?.cover_url || ''
    },
    back() {
      uni.navigateBack()
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
  background: #050609;
  color: #fff;
  font-family: -apple-system, BlinkMacSystemFont, "Inter", "SF Pro Display", "Segoe UI", sans-serif;
  overflow: hidden;
}

.back-btn {
  position: fixed;
  top: calc(12rpx + env(safe-area-inset-top));
  left: 16rpx;
  z-index: 20;
  width: 56rpx;
  height: 56rpx;
  line-height: 50rpx;
  text-align: center;
  font-size: 48rpx;
  font-weight: 300;
  color: rgba(255, 255, 255, 0.8);
  text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.5);
  transition: transform 0.18s ease;
}

.back-btn:active {
  transform: scale(0.9);
  color: rgba(255, 255, 255, 1);
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  gap: 18rpx;
}

.loading-spinner {
  width: 54rpx;
  height: 54rpx;
  border: 4rpx solid rgba(247, 198, 106, 0.25);
  border-top-color: #f7c66a;
  border-radius: 50%;
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading-text {
  color: rgba(255, 255, 255, 0.8);
  font-size: 22rpx;
}

.screen {
  width: 100%;
  height: 100vh;
  background: #000;
  position: relative;
}

.video,
.locked-screen,
.locked-cover {
  width: 100%;
  height: 100%;
}

.video-tap-area {
  width: 100%;
  height: 100%;
}

.progress-bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: calc(120rpx + env(safe-area-inset-bottom));
  height: 4rpx;
  background: rgba(255, 255, 255, 0.15);
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #ffe0a1, #f3b84d);
  border-radius: 0 2rpx 2rpx 0;
}

.downloading-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  background: rgba(0, 0, 0, 0.85);
  z-index: 5;
}

.downloading-spinner {
  width: 54rpx;
  height: 54rpx;
  border: 4rpx solid rgba(247, 198, 106, 0.25);
  border-top-color: #f7c66a;
  border-radius: 50%;
  animation: spin 0.9s linear infinite;
  margin-bottom: 18rpx;
}

.downloading-text {
  color: rgba(255, 255, 255, 0.92);
  font-size: 19rpx;
  font-weight: 700;
  margin-bottom: 14rpx;
}

.downloading-bar {
  width: 50%;
  height: 6rpx;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 999rpx;
  overflow: hidden;
}

.downloading-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #ffe0a1, #f3b84d);
  border-radius: 999rpx;
  transition: width 0.3s ease;
}

.locked-screen {
  position: relative;
}

.locked-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 40rpx;
  background: radial-gradient(circle at 50% 40%, rgba(247, 198, 106, 0.18), transparent 50%), rgba(0, 0, 0, 0.66);
  backdrop-filter: blur(8rpx);
}

.locked-title {
  font-size: 28rpx;
  font-weight: 900;
  text-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.6);
}

.locked-desc {
  margin-top: 8rpx;
  color: rgba(255, 255, 255, 0.78);
  font-size: 18rpx;
  text-align: center;
  line-height: 1.5;
}

.primary-btn,
.ghost-btn,
.plain-btn {
  width: 360rpx;
  height: 56rpx;
  line-height: 56rpx;
  margin-top: 14rpx;
  border-radius: 999rpx;
  font-size: 20rpx;
  font-weight: 800;
  transition: transform 0.18s ease;
}

.primary-btn {
  color: #11100d;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  box-shadow: 0 8rpx 22rpx rgba(247, 198, 106, 0.4), inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
}

.primary-btn:active {
  transform: scale(0.97);
}

.ghost-btn,
.plain-btn {
  color: #fff;
  background: rgba(255, 255, 255, 0.14);
  border: 1rpx solid rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10rpx);
}

.ghost-btn:active,
.plain-btn:active {
  transform: scale(0.97);
  background: rgba(255, 255, 255, 0.2);
}

.side-actions {
  position: fixed;
  right: 16rpx;
  bottom: calc(200rpx + env(safe-area-inset-bottom));
  z-index: 15;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.action {
  width: 96rpx;
  margin-bottom: 24rpx;
  text-align: center;
  font-size: 22rpx;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.9);
  text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.6);
  transition: transform 0.2s ease;
}

.action:active {
  transform: scale(0.88);
}

.bottom-info {
  position: fixed;
  left: 0;
  right: 0;
  bottom: calc(80rpx + env(safe-area-inset-bottom));
  z-index: 14;
  padding: 8rpx 24rpx 6rpx;
  background: linear-gradient(0deg, rgba(0, 0, 0, 0.7) 0%, rgba(0, 0, 0, 0.3) 70%, transparent 100%);
  pointer-events: none;
}

.info-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #fff;
  text-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.6);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.info-desc {
  margin-top: 6rpx;
  font-size: 22rpx;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.72);
  text-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.6);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.episode-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 15;
  height: 56rpx;
  line-height: 56rpx;
  padding: 0 24rpx;
  padding-bottom: env(safe-area-inset-bottom);
  background: linear-gradient(to top, rgba(0, 0, 0, 0.65), rgba(0, 0, 0, 0.35));
  display: flex;
  align-items: center;
  box-sizing: content-box;
}

.episode-text {
  font-size: 22rpx;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.9);
}

.episode-bar:active .episode-text {
  color: #f7c66a;
}

.panel {
  background: linear-gradient(180deg, #0d0f15 0%, #07080b 100%);
  color: #fff;
  padding: 22rpx 18rpx 28rpx;
}

.panel-title {
  display: flex;
  align-items: center;
  font-size: 24rpx;
  font-weight: 800;
  margin-bottom: 14rpx;
}

.panel-title::before {
  content: "";
  display: inline-block;
  width: 6rpx;
  height: 20rpx;
  margin-right: 10rpx;
  background: linear-gradient(180deg, #ffe0a1, #f3b84d);
  border-radius: 4rpx;
}

.panel-scroll {
  height: 420rpx;
}

.panel-item {
  display: flex;
  margin-bottom: 10rpx;
  padding: 10rpx;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 14rpx;
  background: linear-gradient(160deg, rgba(255, 255, 255, 0.08), rgba(255, 255, 255, 0.025)), rgba(15, 17, 24, 0.8);
  backdrop-filter: blur(10rpx);
  box-shadow: 0 6rpx 16rpx rgba(0, 0, 0, 0.32);
  transition: transform 0.2s ease;
}

.panel-item:active {
  transform: scale(0.98);
}

.panel-item.active {
  border-color: rgba(247, 198, 106, 0.6);
  background: linear-gradient(160deg, rgba(247, 198, 106, 0.16), rgba(255, 255, 255, 0.04)), rgba(15, 17, 24, 0.85);
  box-shadow: 0 0 20rpx rgba(247, 198, 106, 0.18);
}

.panel-item image {
  width: 88rpx;
  height: 112rpx;
  border-radius: 10rpx;
  background: #1a1d26;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.4);
  flex-shrink: 0;
}

.panel-item-copy {
  flex: 1;
  min-width: 0;
  margin-left: 12rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.panel-item-title {
  font-size: 20rpx;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.panel-item-sub {
  margin-top: 6rpx;
  color: #f7c66a;
  font-size: 17rpx;
  font-weight: 700;
}

/* === cover-view 覆盖元素（在 video 内部，Android 原生层级） === */
.cv-back {
  position: absolute;
  top: calc(12rpx + env(safe-area-inset-top));
  left: 16rpx;
  width: 56rpx;
  height: 56rpx;
  line-height: 50rpx;
  text-align: center;
  font-size: 48rpx;
  font-weight: 300;
  color: rgba(255, 255, 255, 0.85);
  text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.5);
}

.cv-actions {
  position: absolute;
  right: 16rpx;
  bottom: calc(200rpx + env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  align-items: center;
}

.cv-action {
  width: 96rpx;
  margin-bottom: 24rpx;
  text-align: center;
  font-size: 22rpx;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.9);
}

.cv-icon {
  font-size: 48rpx;
  line-height: 52rpx;
  color: #fff;
  text-align: center;
}

.cv-label {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.85);
  text-align: center;
}

.cv-bottom {
  position: absolute;
  left: 24rpx;
  right: 120rpx;
  bottom: calc(80rpx + env(safe-area-inset-bottom));
}

.cv-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #fff;
  text-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.6);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cv-desc {
  margin-top: 6rpx;
  font-size: 22rpx;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.72);
  text-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.6);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cv-episode {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 56rpx;
  line-height: 56rpx;
  padding: 0 24rpx;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.65), rgba(0, 0, 0, 0.35));
  display: flex;
  align-items: center;
  box-sizing: border-box;
}

.cv-episode-text {
  font-size: 22rpx;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.9);
}
</style>
