<template>
  <view class="page">
    <!-- #ifndef APP-PLUS -->
    <view class="topbar">
      <scroll-view scroll-x class="tabs">
        <view class="tab" :class="{ active: !categoryId }" @click="selectCategory(null)">{{ t('all') }}</view>
        <view
          v-for="item in categories"
          :key="item.id"
          class="tab"
          :class="{ active: categoryId === item.id }"
          @click="selectCategory(item.id)"
        >
          {{ t(item.name) }}
        </view>
      </scroll-view>
      <picker :range="localeNames" :value="localeIndex" @change="changeLocale">
        <view class="language">{{ currentLocaleShort }}</view>
      </picker>
      <view class="browse" @click="goCategory">{{ t('browse') }}</view>
      <view class="mine" @click="goMine">{{ t('profile') }}</view>
    </view>
    <!-- #endif -->

    <swiper
      v-if="videoList.length"
      class="swipers"
      :current="current"
      :vertical="true"
      :indicator-dots="false"
      :autoplay="false"
      :duration="250"
      @change="change"
    >
      <swiper-item v-for="(item, index) in videoList" :key="item.courseDetailsId">
        <view class="swipers-items">
          <video
            v-if="shouldShowVideo(item, index)"
            class="swipers-items-video"
            :id="'myVideo' + item.courseDetailsId"
            :src="videoSrc(item)"
            :poster="item.titleImg"
            :autoplay="true"
            :loop="false"
            :controls="false"
            :show-center-play-btn="false"
            :show-fullscreen-btn="false"
            :show-progress="false"
            :show-play-btn="false"
            object-fit="cover"
            @play="videoReady"
            @pause="onVideoPause"
            @canplay="videoReady"
            @timeupdate="timeupdate"
            @ended="ended"
            @error="videoError"
          >
            <cover-view class="video-tap-area" @tap="togglePlayback(item)"></cover-view>
          </video>
          <view v-else class="poster-stage" @click="playCurrent(item)">
            <image class="swipers-items-imgsbg" :src="item.titleImg" mode="aspectFill" />
            <view v-if="item.videoUrl" class="poster-play">▶</view>
            <view v-else class="poster-lock">{{ t('premiumEpisode') }}</view>
          </view>
          <view v-if="shouldShowVideoError(index)" class="video-error-mask">
            <view class="video-error-title">{{ t('videoLoadFailed') }}</view>
            <view class="video-error-url">{{ videoLoadError }}</view>
            <view class="video-error-actions">
              <button class="video-error-btn primary" @click.stop="retryVideo">{{ t('retry') }}</button>
              <button class="video-error-btn" @click.stop="goDetail">{{ t('details') }}</button>
            </view>
          </view>

          <!-- #ifndef APP-PLUS -->
          <view class="swipers-items-info">
            <view class="swipers-items-info-title">{{ item.courseDetailsName }}</view>
            <view class="swipers-items-info-content">{{ item.content }}</view>
            <view class="swipers-items-info-num" @click="openShow">
              {{ t('episodeBrowse', { num: item.num, total: meunList.length }) }}
            </view>
          </view>

          <view class="swipers-items-right">
            <view class="action" @click="toggleFavorite">
              <u-icon name="heart-fill" :color="favorite ? '#ff4d67' : '#ffffff'" size="42" />
              <text>{{ favorite ? t('saved') : t('save') }}</text>
            </view>
            <view class="action" @click="toggleLike">
              <u-icon name="thumb-up" color="#ffffff" size="40" />
              <text>{{ t('like') }}</text>
            </view>
            <view class="action" @click="openComments">
              <u-icon name="chat" color="#ffffff" size="40" />
              <text>{{ t('comment') }}</text>
            </view>
            <view class="action" @click="share">
              <u-icon name="share" color="#ffffff" size="40" />
              <text>{{ t('share') }}</text>
            </view>
            <view class="action" @click="goDetail">
              <u-icon name="list" color="#ffffff" size="40" />
              <text>{{ t('details') }}</text>
            </view>
          </view>
          <!-- #endif -->
        </view>
      </swiper-item>
    </swiper>

    <view v-else-if="pageLoading" class="loading-state">
      <view class="loading-spinner"></view>
      <text class="loading-text">{{ t('loading') }}</text>
      <view v-if="loadError" class="loading-error">{{ loadError }}</view>
      <view v-if="loadError" class="retry-btn" @click="onLoadRetry">重试</view>
    </view>

    <view v-else-if="!pageLoading" class="empty">
      <text>{{ t('noTitles') }}</text>
      <view v-if="loadError" class="loading-error">{{ loadError }}</view>
      <view v-if="loadError" class="retry-btn" @click="onLoadRetry">重试</view>
    </view>

    <u-popup v-model="show" mode="bottom" height="560rpx" border-radius="20" :closeable="true">
      <view class="list">
        <view class="list-title">{{ t('episodeTitle', { num }) }}</view>
        <scroll-view scroll-y class="list-scroll" :scroll-into-view="scrollIntoViews">
          <view
            v-for="(item, index) in meunList"
            :key="item.courseDetailsId"
            :id="item.viewInfo"
            class="list-item"
            @click="selectPlay(item)"
          >
            <image :src="item.titleImg" mode="aspectFill" />
            <view class="list-copy">
              <view class="list-row">
                <text>{{ t('episodeTitle', { num: item.num }) }}</text>
                <text v-if="item.courseDetailsId === activeEpisodeId" class="playing">{{ t('nowPlaying') }}</text>
                <text v-else-if="!item.unlocked" class="locked">{{ t('creditCost', { count: item.pricePoints }) }}</text>
                <text v-else class="free">{{ t('unlocked') }}</text>
                <text v-if="item.durationText || item.durationSeconds" class="duration">{{ item.durationText || formatDuration(item.durationSeconds) }}</text>
              </view>
              <view class="list-name">{{ item.courseDetailsName }}</view>
              <view class="list-desc">{{ item.content }}</view>
            </view>
          </view>
        </scroll-view>
      </view>
    </u-popup>

    <u-popup v-model="showPay" mode="bottom" border-radius="20" :closeable="true">
      <view class="pay">
        <view class="pay-kicker">{{ t('premiumEpisode') }}</view>
        <view class="pay-title">{{ t('unlockChapter') }}</view>
        <view class="pay-subtitle">{{ t('unlockSubtitle', { count: pendingEpisode ? pendingEpisode.pricePoints : 0 }) }}</view>
        <button class="pay-button" @click="unlockEpisode">{{ t('unlockWithCredits') }}</button>
        <view v-if="drama && dramaTotalPrice" class="unlock-drama-section">
          <view class="unlock-drama-title">{{ t('unlockDrama') }}</view>
          <view class="unlock-drama-subtitle">{{ t('unlockDramaConfirm', { count: dramaTotalPrice }) }}</view>
          <button class="pay-button drama-button" @click="unlockFullDrama">{{ t('unlockDrama') }}</button>
        </view>
        <button class="pay-ghost" @click="hasToken ? goRecharge() : goLogin()">{{ hasToken ? t('rechargeCredits') : t('signInCreate') }}</button>
      </view>
    </u-popup>
    <app-tab-bar current="home" />
  </view>
</template>

<script>
import api from '../../utils/api.js'
import { getLocale, localeOptions, setLocale, t as translate, formatDuration } from '../../utils/i18n.js'
import { notifyDataChanged, APP_DATA_EVENTS } from '../../utils/app-state.js'
import { resolveEpisodeSource } from '../../utils/playback.js'

export default {
  data() {
    return {
      categories: [],
      categoryId: null,
      dramas: [],
      drama: null,
      videoList: [],
      meunList: [],
      current: 0,
      num: 1,
      show: false,
      showPay: false,
      pendingEpisode: null,
      scrollIntoViews: 'video0',
      favorite: false,
      progressSeconds: 0,
      progressPercent: 0,
      lastHistoryKey: '',
      lastHistorySecond: -1,
      playingEpisodeId: null,
      videoLoadError: '',
      videoLoadTimer: null,
      feedPaused: false,
      locale: getLocale(),
      localeOptions,
      pageLoading: true,
      loadError: '',
      MAX_PLAY_RETRY: 1,
      playRetryCount: 0
    }
  },
  computed: {
    activeEpisodeId() {
      return this.videoList[this.current] ? this.videoList[this.current].courseDetailsId : null
    },
    localeNames() {
      return this.localeOptions.map(item => item.label)
    },
    localeIndex() {
      return Math.max(0, this.localeOptions.findIndex(item => item.code === this.locale))
    },
    currentLocaleShort() {
      return this.localeOptions[this.localeIndex].short
    },
    hasToken() {
      return !!uni.getStorageSync('token')
    },
    dramaTotalPrice() {
      if (!this.drama) return 0
      return Number(this.pick(this.drama, 'whole_price_points', 'wholePricePoints') || 0)
    }
  },
  onLoad(options) {
    this.refreshLocale()
    this.bootstrap(options.id, options.courseDetailsId)
  },
  onShow() {
    this.refreshLocale()
    uni.$on('overlayEvent', this.handleOverlayEvent)
  },
  onHide() {
    uni.$off('overlayEvent', this.handleOverlayEvent)
    this.hideOverlay()
  },
  onUnload() {
    this.clearVideoTimer()
    uni.$off('overlayEvent', this.handleOverlayEvent)
    this.hideOverlay()
  },
  onPullDownRefresh() {
    this.bootstrap(this.drama ? this.drama.id : null).finally(() => uni.stopPullDownRefresh())
  },
  methods: {
    async bootstrap(dramaId, episodeId) {
      this.pageLoading = true
      this.loadError = ''
      const timer = setTimeout(() => {
        this.pageLoading = false
        if (!this.videoList.length) {
          this.loadError = this.t('loading') || '加载超时，请重试'
        }
      }, 8000)
      try {
        this.categories = await api.categories()
        this.dramas = await api.dramas(this.categoryId)
        const targetId = dramaId || (this.dramas && this.dramas[0] && this.dramas[0].id)
        if (targetId) {
          await this.loadDrama(targetId, episodeId)
        } else {
          this.videoList = []
        }
      } catch (err) {
        this.loadError = err.message || this.t('loading') || '加载失败'
        this.videoList = []
      } finally {
        clearTimeout(timer)
        this.pageLoading = false
      }
    },
    onLoadRetry() {
      this.bootstrap()
    },
    sendOverlayUpdate() {
      const item = this.videoList[this.current]
      if (!item) return
      uni.$emit('updateOverlay', {
        categories: this.categories.map(c => ({ id: c.id, name: this.t(c.name) })),
        categoryId: this.categoryId,
        allText: this.t('all'),
        favorite: this.favorite,
        favoriteText: this.favorite ? this.t('saved') : this.t('save'),
        likeText: this.t('like'),
        commentText: this.t('comment'),
        shareText: this.t('share'),
        detailsText: this.t('details'),
        browseText: this.t('browse'),
        profileText: this.t('profile'),
        episodeBrowseText: this.t('episodeBrowse', { num: item.num, total: this.meunList.length }),
        dramaTitle: item.courseDetailsName,
        episodeDesc: item.content,
        episodeNo: item.num,
        total: this.meunList.length,
        progressPercent: this.progressPercent || 0
      })
    },
    showOverlay() {
      const ov = uni.getSubNVueById('indexOverlay')
      if (ov) {
        ov.show()
        this.sendOverlayUpdate()
      }
    },
    hideOverlay() {
      const ov = uni.getSubNVueById('indexOverlay')
      if (ov) ov.hide()
    },
    handleOverlayEvent(e) {
      if (!e || !e.type) return
      switch (e.type) {
        case 'category': this.selectCategory(e.data); break
        case 'browse': this.goCategory(); break
        case 'profile': this.goMine(); break
        case 'favorite': this.toggleFavorite(); break
        case 'like': this.toggleLike(); break
        case 'comment': this.openComments(); break
        case 'share': this.share(); break
        case 'details': this.goDetail(); break
        case 'episode': this.openShow(); break
        case 'tap': this.togglePlayback(this.videoList[this.current]); break
      }
    },
    async selectCategory(categoryId) {
      this.categoryId = categoryId
      this.current = 0
      await this.bootstrap()
    },
    async loadDrama(id, episodeId) {
      const drama = await api.drama(id)
      this.drama = drama
      this.favorite = !!drama.favorite
      this.meunList = (drama.episodes || []).map((item, index) => this.normalizeEpisode(item, drama, index))
      const startIndex = Math.max(0, this.meunList.findIndex(item => item.courseDetailsId === Number(episodeId)))
      this.videoList = this.meunList
      this.current = startIndex
      this.num = this.videoList[this.current] ? this.videoList[this.current].num : 1
      this.stopPlayback()
      this.$nextTick(() => {
        this.playCurrent()
      })
    },
    normalizeEpisode(item, drama, index) {
      const episodeNo = this.pick(item, 'episode_no', 'episodeNo') || index + 1
      return {
        courseId: this.pick(drama, 'id'),
        courseDetailsId: this.pick(item, 'id'),
        courseDetailsName: this.pick(item, 'title') || this.t('episodeTitle', { num: episodeNo }),
        content: this.pick(item, 'description') || this.pick(drama, 'description') || '',
        isCollect: drama.favorite ? 1 : 0,
        goodNum: 0,
        videoUrl: this.pick(item, 'video_url', 'videoUrl'),
        playbackUrl: '',
        titleImg: this.pick(item, 'cover_url', 'coverUrl') || this.pick(drama, 'cover_url', 'coverUrl'),
        num: episodeNo,
        viewInfo: 'video' + index,
        unlocked: !!item.unlocked,
        pricePoints: this.pick(item, 'price_points', 'pricePoints') || 0,
        durationSeconds: this.pick(item, 'duration_seconds', 'durationSeconds'),
        durationText: this.pick(item, 'duration_text', 'durationText')
      }
    },
    pick(source, ...keys) {
      for (const key of keys) {
        if (source && source[key] !== undefined && source[key] !== null) {
          return source[key]
        }
      }
      return undefined
    },
    change(e) {
      const prevIndex = this.current
      if (prevIndex !== Number(e.detail.current)) {
        const prevItem = this.videoList[prevIndex]
        if (prevItem && this.progressSeconds > 0) {
          this.setHistor(prevItem.courseId, prevItem.courseDetailsId)
        }
      }
      this.current = Number(e.detail.current)
      const item = this.videoList[this.current]
      if (!item) return
      this.num = item.num
      this.scrollIntoViews = item.viewInfo
      this.progressSeconds = 0
      this.lastHistoryKey = ''
      this.lastHistorySecond = -1
      this.videoLoadError = ''
      this.stopPlayback()
      this.$nextTick(() => {
        this.playCurrent(item)
      })
    },
    shouldShowVideo(item, index) {
      return this.current === index && this.videoSrc(item) && this.playingEpisodeId === item.courseDetailsId && !this.videoLoadError
    },
    shouldShowVideoError(index) {
      return this.current === index && !!this.videoLoadError
    },
    videoSrc(item) {
      return item ? (item.playbackUrl || item.videoUrl || '') : ''
    },
    async playCurrent(item) {
      item = item || this.videoList[this.current]
      if (!item) return
      if (!item.videoUrl) {
        this.openPay(item)
        return
      }
      this.progressSeconds = 0
      this.videoLoadError = ''
      var episodeId = item.courseDetailsId
      try {
        var source = await resolveEpisodeSource(item.courseDetailsId, item.videoUrl, api)
        if (this.activeEpisodeId !== episodeId) return
        this.$set(item, 'playbackUrl', source)
        this.playingEpisodeId = episodeId
        this.videoLoadError = ''
        this.playRetryCount = 0
        this.resetVideoState(item)
        this.$nextTick(() => {
          uni.createVideoContext('myVideo' + item.courseDetailsId, this).play()
          this.setHistor(item.courseId, item.courseDetailsId)
          this.showOverlay()
        })
      } catch (err) {
        console.error('[index player] playCurrent error:', err)
        this.clearVideoTimer()
        this.playingEpisodeId = null
        this.videoLoadError = err.message || item.videoUrl || this.t('requestFailed')
      }
    },
    stopPlayback() {
      this.clearVideoTimer()
      this.playingEpisodeId = null
      this.videoLoadError = ''
      this.feedPaused = false
      this.hideOverlay()
    },
    timeupdate(e) {
      this.progressSeconds = Math.floor(e.detail.currentTime || 0)
      if (this.progressSeconds > 0) this.videoReady()
      const dur = e.detail.duration || 0
      this.progressPercent = dur > 0 ? Math.min(100, (this.progressSeconds / dur) * 100) : 0
      const item = this.videoList[this.current]
      if (item && this.progressSeconds > 0 && this.progressSeconds % 10 === 0) {
        this.setHistor(item.courseId, item.courseDetailsId)
      }
      this.sendOverlayUpdate()
    },
    resetVideoState(item) {
      this.clearVideoTimer()
      this.videoLoadError = ''
      if (!item || !this.videoSrc(item)) return
      const episodeId = item.courseDetailsId
      this.videoLoadTimer = setTimeout(() => {
        if (this.activeEpisodeId === episodeId && this.progressSeconds <= 0) {
          this.videoLoadError = this.t('videoLoadFailed')
        }
      }, 30000)
    },
    clearVideoTimer() {
      if (this.videoLoadTimer) {
        clearTimeout(this.videoLoadTimer)
        this.videoLoadTimer = null
      }
    },
    videoReady() {
      this.clearVideoTimer()
      this.videoLoadError = ''
      this.feedPaused = false
    },
    onVideoPause() {
      this.feedPaused = true
    },
    togglePlayback(item) {
      item = item || this.videoList[this.current]
      if (!item) return
      const context = uni.createVideoContext('myVideo' + item.courseDetailsId, this)
      if (this.feedPaused) {
        context.play()
        this.feedPaused = false
      } else {
        context.pause()
        this.feedPaused = true
      }
    },
    videoError() {
      var item = this.videoList[this.current]
      this.clearVideoTimer()
      if (!item) return
      this.playingEpisodeId = null
      this.videoLoadError = '视频加载失败，请点击重试'
      this.$set(item, 'playbackUrl', '')
    },
    retryVideo() {
      var item = this.videoList[this.current]
      if (!item) return
      this.progressSeconds = 0
      this.playRetryCount = 0
      this.videoLoadError = ''
      this.$set(item, 'playbackUrl', '')
      this.playingEpisodeId = null
      this.playCurrent(item)
    },
    ended() {
      if (this.current < this.videoList.length - 1) {
        this.current += 1
      }
    },
    openShow() {
      this.show = true
      this.$nextTick(() => {
        this.scrollIntoViews = this.videoList[this.current] ? this.videoList[this.current].viewInfo : 'video0'
      })
    },
    selectPlay(item) {
      const index = this.videoList.findIndex(video => video.courseDetailsId === item.courseDetailsId)
      this.current = index < 0 ? 0 : index
      this.show = false
      this.playCurrent(item)
    },
    openPay(item) {
      this.pendingEpisode = item
      this.showPay = true
    },
    async unlockEpisode() {
      if (!uni.getStorageSync('token')) {
        this.goLogin()
        return
      }
      if (!this.pendingEpisode) return
      try {
        await api.unlock(this.pendingEpisode.courseDetailsId)
        this.showPay = false
        notifyDataChanged(APP_DATA_EVENTS.points)
        await this.loadDrama(this.pendingEpisode.courseId, this.pendingEpisode.courseDetailsId)
        this.toast(this.t('unlockedToast'))
      } catch (err) {
        this.toast(err.message)
        this.offerRecharge(err.message)
      }
    },
    async unlockFullDrama() {
      if (!uni.getStorageSync('token')) {
        this.goLogin()
        return
      }
      if (!this.drama) return
      try {
        await api.unlockDrama(this.drama.id)
        this.showPay = false
        notifyDataChanged(APP_DATA_EVENTS.points)
        await this.loadDrama(this.drama.id, this.pendingEpisode ? this.pendingEpisode.courseDetailsId : null)
        this.toast(this.t('unlockedToast'))
      } catch (err) {
        this.toast(err.message)
        this.offerRecharge(err.message)
      }
    },
    async toggleFavorite() {
      if (!uni.getStorageSync('token')) {
        this.goLogin()
        return
      }
      try {
        const res = await api.toggleFavorite(this.drama.id)
        this.favorite = res.favorite
        notifyDataChanged(APP_DATA_EVENTS.favorite, { dramaId: this.drama.id, favorite: this.favorite })
        this.sendOverlayUpdate()
      } catch (err) {
        this.toast(err.message)
      }
    },
    setHistor(courseId, courseDetailsId) {
      if (!uni.getStorageSync('token') || !courseId || !courseDetailsId) return
      const key = courseId + ':' + courseDetailsId
      if (key === this.lastHistoryKey && this.progressSeconds === this.lastHistorySecond) return
      this.lastHistoryKey = key
      this.lastHistorySecond = this.progressSeconds
      api.saveHistory({
        dramaId: courseId,
        episodeId: courseDetailsId,
        progressSeconds: this.progressSeconds
      }).then(() => notifyDataChanged(APP_DATA_EVENTS.history, { dramaId: courseId, episodeId: courseDetailsId })).catch(() => {})
    },
    toggleLike() {
      if (!uni.getStorageSync('token')) {
        this.goLogin()
        return
      }
      this.toast(this.t('likePending'))
    },
    openComments() {
      if (!uni.getStorageSync('token')) {
        this.goLogin()
        return
      }
      this.toast(this.t('commentPending'))
    },
    share() {
      const item = this.videoList[this.current]
      const path = '/pages/index/index?id=' + item.courseId + '&courseDetailsId=' + item.courseDetailsId
      uni.setClipboardData({ data: path })
      if (uni.getStorageSync('token') && item.courseId) {
        api.rewardShare(item.courseId).then(() => notifyDataChanged(APP_DATA_EVENTS.points)).catch(() => {})
      }
    },
    offerRecharge(message) {
      if (!/point|credit|积分|余额|insufficient/i.test(message || '')) return
      setTimeout(() => this.goRecharge(), 600)
    },
    goDetail() {
      if (!this.drama) return
      uni.navigateTo({ url: '/pages/detail/detail?id=' + this.drama.id })
    },
    goCategory() {
      uni.redirectTo({ url: '/pages/theater/theater' + (this.categoryId ? '?contentType=' + encodeURIComponent(this.categoryId) : '') })
    },
    goRecharge() {
      uni.redirectTo({ url: '/pages/store/store?tab=recharge' })
    },
    goLogin() {
      uni.navigateTo({ url: '/pages/login/login' })
    },
    goMine() {
      uni.navigateTo({ url: '/pages/mine/mine' })
    },
    refreshLocale() {
      this.locale = getLocale()
      uni.setNavigationBarTitle({ title: this.t('appTitle') })
    },
    changeLocale(e) {
      const item = this.localeOptions[Number(e.detail.value)]
      if (!item) return
      setLocale(item.code)
      this.refreshLocale()
    },
    t(key, params) {
      return translate(key, params, this.locale)
    },
    formatDuration(seconds) {
      return formatDuration(seconds)
    },
    toast(title) {
      uni.showToast({ title, icon: 'none' })
    }
  }
}
</script>

<style lang="scss">
page,
.page {
  width: 100%;
  height: 100vh;
  background: #06070a;
  color: #fff;
  font-family: -apple-system, BlinkMacSystemFont, "Inter", "SF Pro Display", "Segoe UI", sans-serif;
}

.topbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 10rpx 16rpx 0;
  background: linear-gradient(to bottom, rgba(6, 7, 10, 0.5), rgba(6, 7, 10, 0));
  backdrop-filter: blur(18rpx);
}

.tabs {
  flex: 1;
  white-space: nowrap;
}

.tab {
  display: inline-flex;
  align-items: center;
  height: 48rpx;
  margin-right: 10rpx;
  padding: 0 18rpx;
  color: rgba(255, 255, 255, 0.72);
  font-size: 24rpx;
  font-weight: 700;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.08);
  transition: all 0.2s ease;
}

.tab:active {
  transform: scale(0.94);
}

.tab.active {
  color: #07080b;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  border-color: transparent;
  box-shadow:
    0 6rpx 16rpx rgba(247, 198, 106, 0.42),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
}

.mine {
  min-width: 60rpx;
  height: 48rpx;
  line-height: 48rpx;
  text-align: center;
  font-size: 22rpx;
  font-weight: 700;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(10rpx);
  transition: transform 0.18s ease;
}

.mine:active {
  transform: scale(0.94);
  background: rgba(255, 255, 255, 0.18);
}

.browse {
  min-width: 56rpx;
  height: 48rpx;
  margin-right: 8rpx;
  line-height: 48rpx;
  text-align: center;
  font-size: 22rpx;
  font-weight: 800;
  color: #07080b;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  box-shadow:
    0 6rpx 14rpx rgba(247, 198, 106, 0.36),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.45);
  transition: transform 0.18s ease;
}

.browse:active {
  transform: scale(0.94);
}

.language {
  width: 40rpx;
  height: 38rpx;
  margin-right: 8rpx;
  line-height: 38rpx;
  text-align: center;
  font-size: 16rpx;
  font-weight: 800;
  color: #07080b;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  box-shadow: 0 4rpx 12rpx rgba(247, 198, 106, 0.32);
  transition: transform 0.18s ease;
}

.language:active {
  transform: scale(0.92);
}

.swipers,
.swipers-items,
.swipers-items-video,
.poster-stage,
.swipers-items-imgsbg {
  width: 100%;
  height: calc(100vh - 88rpx - env(safe-area-inset-bottom));
}

.swipers-items {
  position: relative;
  background: #000;
}

.video-tap-area {
  width: 100%;
  height: 100%;
}

.poster-stage {
  position: relative;
  overflow: hidden;
}

.poster-play,
.poster-lock {
  position: absolute;
  left: 50%;
  top: 50%;
  z-index: 8;
  transform: translate(-50%, -50%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.poster-play {
  width: 80rpx;
  height: 80rpx;
  padding-left: 6rpx;
  border-radius: 50%;
  color: #111;
  background: rgba(255, 255, 255, 0.96);
  font-size: 36rpx;
  box-sizing: border-box;
  box-shadow:
    0 8rpx 24rpx rgba(0, 0, 0, 0.4),
    inset 0 1rpx 4rpx rgba(255, 255, 255, 0.6);
  animation: pulsePlay 2.2s ease-in-out infinite;
}

@keyframes pulsePlay {
  0%, 100% { transform: translate(-50%, -50%) scale(1); box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.4), 0 0 0 0 rgba(255, 255, 255, 0.4); }
  50% { transform: translate(-50%, -50%) scale(1.06); box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.4), 0 0 0 12rpx rgba(255, 255, 255, 0); }
}

.poster-lock {
  min-width: 160rpx;
  height: 48rpx;
  padding: 0 20rpx;
  border-radius: 999rpx;
  color: #111;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  font-size: 19rpx;
  font-weight: 900;
  letter-spacing: 1rpx;
  box-shadow:
    0 8rpx 20rpx rgba(247, 198, 106, 0.42),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
}

.swipers-items::after {
  position: absolute;
  inset: 0;
  content: "";
  pointer-events: none;
  background:
    linear-gradient(to bottom, rgba(0, 0, 0, 0.16), rgba(0, 0, 0, 0) 28%),
    linear-gradient(to top, rgba(0, 0, 0, 0.84), rgba(0, 0, 0, 0.08) 54%, rgba(0, 0, 0, 0));
}

.video-error-mask {
  position: absolute;
  inset: 0;
  z-index: 30;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 28rpx;
  text-align: center;
  background: rgba(0, 0, 0, 0.66);
  box-sizing: border-box;
}

.video-error-title {
  margin-bottom: 12rpx;
  font-size: 22rpx;
  font-weight: 800;
  color: #fff;
}

.video-error-url {
  max-width: 420rpx;
  margin-bottom: 18rpx;
  font-size: 17rpx;
  line-height: 1.45;
  color: rgba(255, 255, 255, 0.58);
  word-break: break-all;
}

.video-error-actions {
  display: flex;
  gap: 12rpx;
}

.video-error-btn {
  min-width: 110rpx;
  height: 52rpx;
  line-height: 52rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  color: #fff;
  font-size: 19rpx;
  font-weight: 700;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.2);
  transition: transform 0.18s ease;
}

.video-error-btn:active {
  transform: scale(0.95);
}

.video-error-btn.primary {
  color: #0d0d10;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  border-color: transparent;
  box-shadow:
    0 6rpx 16rpx rgba(247, 198, 106, 0.36),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.4);
}

.swipers-items-info {
  position: absolute;
  left: 24rpx;
  right: 130rpx;
  bottom: calc(24rpx + env(safe-area-inset-bottom));
  z-index: 10;
}

.swipers-items-info-title {
  font-size: 30rpx;
  font-weight: 700;
  line-height: 1.15;
  text-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.55);
  background: linear-gradient(180deg, #ffffff 30%, rgba(255, 255, 255, 0.82) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.swipers-items-info-content {
  margin-top: 8rpx;
  font-size: 22rpx;
  line-height: 1.45;
  color: rgba(255, 255, 255, 0.8);
  text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.6);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.swipers-items-info-num {
  display: inline-flex;
  align-items: center;
  margin-top: 10rpx;
  padding: 8rpx 16rpx;
  font-size: 22rpx;
  font-weight: 800;
  color: #0d0d10;
  background: linear-gradient(135deg, #ffffff, #f6f6f6);
  border-radius: 999rpx;
  box-shadow:
    0 6rpx 16rpx rgba(0, 0, 0, 0.4),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.8);
  transition: transform 0.18s ease;
}

.swipers-items-info-num:active {
  transform: scale(0.95);
}

.swipers-items-right {
  position: absolute;
  right: 16rpx;
  bottom: calc(100rpx + env(safe-area-inset-bottom));
  z-index: 10;
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

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  color: rgba(255, 255, 255, 0.7);
  font-size: 22rpx;
  gap: 18rpx;
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

.loading-error {
  color: #ff8a65;
  font-size: 20rpx;
  margin-top: 8rpx;
}

.retry-btn {
  margin-top: 14rpx;
  padding: 10rpx 28rpx;
  color: #11100d;
  font-size: 20rpx;
  font-weight: 700;
  background: #f7c66a;
  border-radius: 999rpx;
}

.list,
.pay {
  background: linear-gradient(180deg, #0d0f15 0%, #07080b 100%);
  color: #fff;
  padding: 22rpx 18rpx 28rpx;
}

.list-title,
.pay-title {
  display: flex;
  align-items: center;
  font-size: 24rpx;
  font-weight: 800;
  margin-bottom: 14rpx;
}

.list-title::before,
.pay-title::before {
  content: "";
  display: inline-block;
  width: 6rpx;
  height: 20rpx;
  margin-right: 10rpx;
  background: linear-gradient(180deg, #ffe0a1, #f3b84d);
  border-radius: 4rpx;
  box-shadow: 0 2rpx 8rpx rgba(247, 198, 106, 0.45);
}

.pay-kicker {
  display: flex;
  align-items: center;
  margin-bottom: 8rpx;
  color: #f7c66a;
  font-size: 17rpx;
  font-weight: 800;
  letter-spacing: 3rpx;
  text-transform: uppercase;
}

.pay-kicker::before {
  content: "";
  display: inline-block;
  width: 20rpx;
  height: 2rpx;
  margin-right: 8rpx;
  background: linear-gradient(90deg, transparent, #f7c66a);
  border-radius: 2rpx;
}

.list-scroll {
  height: 420rpx;
}

.list-item {
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

.list-item:active {
  transform: scale(0.98);
  background: linear-gradient(160deg, rgba(247, 198, 106, 0.12), rgba(255, 255, 255, 0.045)), rgba(15, 17, 24, 0.85);
}

.list-item image {
  width: 88rpx;
  height: 112rpx;
  border-radius: 10rpx;
  background: #1a1d26;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.4);
  flex-shrink: 0;
}

.list-copy {
  flex: 1;
  min-width: 0;
  margin-left: 12rpx;
}

.list-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 20rpx;
  font-weight: 800;
}

.playing,
.free {
  display: inline-block;
  padding: 2rpx 8rpx;
  color: #0c6f47;
  background: rgba(85, 229, 155, 0.18);
  border: 1px solid rgba(85, 229, 155, 0.4);
  border-radius: 999rpx;
  font-size: 16rpx;
  font-weight: 800;
}

.locked {
  display: inline-block;
  padding: 2rpx 8rpx;
  color: #f7c66a;
  background: rgba(247, 198, 106, 0.14);
  border: 1px solid rgba(247, 198, 106, 0.4);
  border-radius: 999rpx;
  font-size: 16rpx;
  font-weight: 800;
}

.duration {
  display: inline-block;
  padding: 0 6rpx;
  color: rgba(255, 255, 255, 0.5);
  font-size: 16rpx;
  margin-left: 6rpx;
  font-weight: 600;
}

.list-name {
  margin-top: 6rpx;
  font-size: 19rpx;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.list-desc,
.pay-subtitle {
  margin-top: 4rpx;
  color: rgba(255, 255, 255, 0.64);
  font-size: 17rpx;
  line-height: 1.5;
}

.pay-button,
.pay-ghost {
  margin-top: 16rpx;
  height: 56rpx;
  line-height: 56rpx;
  border-radius: 999rpx;
  font-size: 20rpx;
  font-weight: 800;
  letter-spacing: 0.5rpx;
  transition: all 0.2s ease;
}

.pay-button {
  position: relative;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  color: #11100d;
  box-shadow:
    0 8rpx 22rpx rgba(247, 198, 106, 0.36),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
  overflow: hidden;
}

.pay-button:active {
  transform: scale(0.97);
  box-shadow:
    0 6rpx 16rpx rgba(247, 198, 106, 0.32),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
}

.pay-ghost {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10rpx);
}

.pay-ghost:active {
  transform: scale(0.97);
  background: rgba(255, 255, 255, 0.16);
}

.unlock-drama-section {
  margin-top: 14rpx;
  padding: 14rpx;
  background: linear-gradient(135deg, rgba(247, 198, 106, 0.14), rgba(77, 208, 225, 0.06));
  border: 1px solid rgba(247, 198, 106, 0.35);
  border-radius: 14rpx;
  box-shadow: 0 6rpx 18rpx rgba(247, 198, 106, 0.12);
}

.unlock-drama-title {
  display: flex;
  align-items: center;
  font-size: 20rpx;
  font-weight: 800;
  color: #f7c66a;
}

.unlock-drama-subtitle {
  margin-top: 4rpx;
  font-size: 17rpx;
  color: rgba(255, 255, 255, 0.74);
  line-height: 1.5;
}

.drama-button {
  margin-top: 12rpx;
  background: linear-gradient(135deg, #4dd0e1, #26c6da);
  color: #0d0d10;
  box-shadow:
    0 8rpx 18rpx rgba(77, 208, 225, 0.32),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.4);
}
</style>
