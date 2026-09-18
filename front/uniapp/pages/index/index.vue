<template>
  <view class="page" :class="{ 'is-landscape': isLandscape }">
    <!-- #ifndef APP-PLUS -->
    <view class="topbar" v-if="!isLandscape">
      <view class="topbar-side"></view>
      <view class="feed-tabs">
        <view
          v-for="tab in feedTabs"
          :key="tab.key"
          class="feed-tab"
          :class="{ active: feedTab === tab.key }"
          @tap.stop="selectFeedTab(tab)"
        >{{ tab.label }}</view>
      </view>
      <picker class="topbar-side" :range="localeNames" :value="localeIndex" @change="changeLocale">
        <view class="language">{{ currentLocaleShort }}</view>
      </picker>
    </view>
    <!-- #endif -->

    <swiper
      v-if="videoList.length"
      class="swipers"
      :class="{ 'is-landscape': isLandscape }"
      :style="{ height: (isLandscape ? windowHeight + 'px' : 'calc(' + windowHeight + 'px - 104rpx - 88rpx - env(safe-area-inset-top))') }"
      :current="current"
      :vertical="true"
      :indicator-dots="false"
      :autoplay="false"
      :duration="250"
      @change="change"
      @touchstart="onSwiperTouchStart"
      @touchmove="onSwiperTouchMove"
      @touchend="onSwiperTouchEnd"
    >
      <swiper-item v-for="(item, index) in videoList" :key="item.episodeId">
        <view class="feed-column" :style="{ height: (isLandscape ? windowHeight + 'px' : 'calc(' + windowHeight + 'px - 104rpx - 88rpx - env(safe-area-inset-top))') }">
          <!-- 上半：视频区（评论区收起时占满整列） -->
          <view class="video-area" :style="{ height: videoAreaHeight }">
            <!-- 模糊封面背景：填充视频 letterbox 黑边（H5 端可见） -->
            <view class="video-bg-fill" :style="{ backgroundImage: 'url(' + (item.cover_url || '') + ')' }" />
            <video
              v-if="shouldRenderVideo(item, index)"
              class="swipers-items-video"
              :class="{ 'video-hidden': isVideoHidden(item, index) || isVideoPreload(item, index) }"
              :id="'myVideo' + item.episodeId"
              :src="item.playback_url"
              :poster="item.cover_url"
              :autoplay="shouldAutoplay(item, index)"
              :loop="false"
              :controls="false"
              :show-center-play-btn="false"
              :show-fullscreen-btn="false"
              :show-progress="false"
              :show-play-btn="false"
              object-fit="contain"
              @play="videoReady"
              @pause="onVideoPause"
              @canplay="videoReady"
              @timeupdate="timeupdate"
              @ended="ended"
              @error="videoError"
            >
              <cover-view class="video-tap-area" @tap="togglePlayback(item)"></cover-view>
              <!-- 点击视频时的播放/暂停反馈图标（cover-view 才能盖在 APP 原生 video 上） -->
              <cover-view v-if="index === current && playHint" class="video-play-hint">
                <cover-image
                  class="video-play-hint-img"
                  :src="playHint === 'pause' ? '/static/images/playhint/pause.png' : '/static/images/playhint/play.png'"
                />
              </cover-view>
              <!-- #ifndef APP-PLUS -->
              <!-- H5/小程序端进度条（放在 video 外面用 view overlay） -->
              <!-- #endif -->
            </video>
            <view v-else class="poster-stage" @click="playCurrent(item)">
              <image class="swipers-items-imgsbg" :src="item.cover_url" mode="aspectFill" />
              <view v-if="item.playback_url && item.playback_url.length" class="poster-play">▶</view>
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

            <!-- 底部渐变遮罩：保证标题/简介文字可读（不拦截点击） -->
            <view class="video-bottom-shade" />

            <!-- #ifndef APP-PLUS -->
            <view class="swipers-items-info">
              <view class="swipers-items-info-author" v-if="item.author_name">{{ item.author_name }}</view>
              <view class="swipers-items-info-title">{{ item.title }}</view>
              <view class="desc-row">
                <view class="swipers-items-info-content" :class="{ expanded: descExpanded }">{{ item.description }}</view>
                <view class="desc-more-btn" @click.stop="descExpanded = !descExpanded">{{ descExpanded ? '收起' : '更多' }}</view>
              </view>
              <view class="swipers-items-info-num" @click="openShow(item)">
                {{ t('collectionEntry', { num: item.episode_no, total: item.total_episodes }) }}
              </view>
              <!-- 进度条：紧跟在合集选集入口下方（H5/小程序端） -->
              <view
                v-if="index === current && !isDownloading"
                class="video-progress-bar-h5"
                @touchstart.stop.prevent="onProgressTouchStart"
                @touchmove.stop.prevent="onProgressTouchMove"
                @touchend.stop="onProgressTouchEnd"
                @mousedown.stop.prevent="onProgressTouchStart"
                @mousemove.stop.prevent="onProgressTouchMove"
                @mouseup.stop="onProgressTouchEnd"
                @mouseleave.stop="onProgressTouchEnd"
              >
                <text class="video-progress-time">{{ formatCurrentTime }}</text>
                <view class="video-progress-track">
                  <view class="video-progress-fill" :style="{ width: progressPercent + '%' }"></view>
                  <view class="video-progress-thumb" :style="{ left: progressPercent + '%' }"></view>
                </view>
                <text class="video-progress-time video-progress-time-right">{{ formatTotalTime }}</text>
              </view>
            </view>

            <view class="swipers-items-right">
              <view class="action" @click.stop="toggleFavorite">
                <u-icon :name="item.favorite ? 'bookmark-fill' : 'bookmark'" size="56" :color="item.favorite ? '#ff4d67' : '#ffffff'" />
                <text :style="{ color: item.favorite ? '#ff4d67' : 'rgba(255,255,255,0.9)' }">{{ item.favorite ? t('favorited') : t('favorite') }}</text>
              </view>
              <view class="action" @click.stop="toggleLike">
                <u-icon :name="item.liked ? 'thumb-up-fill' : 'thumb-up'" size="56" :color="item.liked ? '#f3b84d' : '#ffffff'" />
                <text :style="{ color: item.liked ? '#f3b84d' : 'rgba(255,255,255,0.9)' }">{{ formatCount(item.like_count) }}</text>
              </view>
              <view class="action" @click.stop="toggleCommentPanel">
                <u-icon name="chat" color="#ffffff" size="56" />
                <text>{{ commentCount > 0 ? formatCount(commentCount) : t('comment') }}</text>
              </view>
              <view class="action" @click.stop="share">
                <u-icon name="share-fill" color="#ffffff" size="56" />
                <text>{{ t('share') }}</text>
              </view>
              <view class="action" @click.stop="goDetail">
                <u-icon name="list-dot" color="#ffffff" size="56" />
                <text>{{ t('details') }}</text>
              </view>
            </view>
            <!-- #endif -->
          </view>

          <!-- 下半：评论区（点击评论按钮才展开） -->
          <view v-show="showCommentPanel" class="comment-area" @click="blurCommentInput">
            <view class="comment-area-header">
              <view class="comment-area-title">{{ t('commentTitle') }}<text v-if="commentCount > 0" class="comment-count-badge">{{ commentCount }}</text></view>
              <view class="comment-area-actions">
                <text class="comment-area-more" @click.stop="goDetail">{{ t('commentMore') }} ›</text>
                <text class="comment-area-collapse" @click.stop="closeCommentPanel">⌄</text>
              </view>
            </view>
            <scroll-view
              scroll-y
              class="comment-scroll"
              :scroll-with-animation="true"
              :scroll-top="commentScrollTop"
              @touchmove.stop.prevent="onCommentTouchMove"
              @click.stop="blurCommentInput"
            >
              <view v-if="commentsLoading" class="comment-loading">{{ t('loading') }}</view>
              <view v-else-if="!comments.length" class="comment-empty">
                <view class="comment-empty-icon">💬</view>
                <view>{{ t('commentEmpty') }}</view>
              </view>
              <view v-else>
                <!-- 根评论 + 嵌套回复 -->
                <view v-for="c in comments" :key="c.id" class="comment-item" @click.stop>
                  <image v-if="c.avatar_url" class="comment-avatar" :src="c.avatar_url" mode="aspectFill" />
                  <view v-else class="comment-avatar comment-avatar-fallback"></view>
                  <view class="comment-body">
                    <view class="comment-nick">{{ c.nickname || '匿名' }}</view>
                    <view class="comment-text">
                <text v-if="!isTruncated(c)">{{ c.content }}</text>
                <text v-else>{{ truncateText(c.content) }}</text>
                <text v-if="isTruncated(c)" class="comment-expand" @click.stop="toggleExpand(c)">{{ c._expanded ? '收起' : '展开' }}</text>
              </view>
                    <view class="comment-meta">
                      <text class="comment-reply-btn" @click.stop="startReply(c, null)">回复</text>
                      <text v-if="myUserId && String(c.user_id) === String(myUserId)" class="comment-del" @click.stop="removeComment(c)">{{ t('commentDelete') }}</text>
                    </view>
                    <!-- 该根评论下的回复 -->
                    <view v-if="c.children && c.children.length" class="comment-reply-list">
                      <view v-for="r in c.children" :key="r.id" class="comment-item comment-item-reply" @click.stop>
                        <view class="comment-reply-text">
                          <text class="comment-nick-mini">{{ r.nickname || '匿名' }}</text>
                          <text v-if="r.reply_to_nickname" class="reply-at">回复 <text class="comment-nick-mini">{{ r.reply_to_nickname }}</text></text>
                          <text class="reply-colon">：</text>
                          <text><text v-if="!isTruncated(r)">{{ r.content }}</text><text v-else>{{ truncateText(r.content) }}</text><text v-if="isTruncated(r)" class="comment-expand" @click.stop="toggleExpand(r)">{{ r._expanded ? '收起' : '展开' }}</text></text>
                        </view>
                        <view class="comment-reply-actions">
                          <text class="comment-reply-btn" @click.stop="startReply(r, c)">回复</text>
                          <text v-if="myUserId && String(r.user_id) === String(myUserId)" class="comment-del" @click.stop="removeComment(r)">{{ t('commentDelete') }}</text>
                        </view>
                      </view>
                      <view v-if="c.replies_total > c.children.length" class="comment-more-replies" @click.stop="loadMoreReplies(c)">
                        查看全部 {{ c.replies_total }} 条回复
                      </view>
                    </view>
                  </view>
                </view>
              </view>
              <view v-if="comments.length >= 20" class="comment-more-link" @click.stop="goDetail">{{ t('commentViewMore') }} ›</view>
            </scroll-view>
            <view class="comment-input-bar" @click.stop>
              <view v-if="replyingTo" class="comment-replying-to">
                回复 @{{ replyingToNick }}
                <text class="reply-cancel" @click.stop="cancelReply">×</text>
              </view>
              <input
                class="comment-input"
                v-model="commentText"
                :placeholder="replyingTo ? '回复 @' + replyingToNick : t('commentPlaceholder')"
                maxlength="500"
                confirm-type="send"
                @confirm="submitComment"
                @focus="onCommentFocus"
                @blur="onCommentBlur"
              />
              <button class="comment-send-btn" :disabled="commentSubmitting || !commentText.trim()" @click.stop="submitComment">发送</button>
            </view>
          </view>
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

    <episode-sheet
      :show.sync="show"
      :drama="sheetDrama"
      :episodes="sheetEpisodes"
      :active-id="activeEpisodeId"
      :dramas="sheetDramas"
      @select="selectPlay"
      @switch-drama="switchDrama"
    />

    <u-popup v-model="showPay" mode="bottom" border-radius="20" :closeable="true">
      <view class="pay">
        <view class="pay-kicker">{{ t('premiumEpisode') }}</view>
        <view class="pay-title">{{ t('unlockChapter') }}</view>
        <view class="pay-subtitle">{{ t('unlockSubtitle', { count: pendingEpisode ? pendingEpisode.pricePoints : 0 }) }}</view>
        <button class="pay-button" @click="unlockEpisode">{{ t('unlockWithCredits') }}</button>
        <view v-if="pendingEpisode && pendingEpisode.pricePoints" class="unlock-drama-section">
          <view class="unlock-drama-title">{{ t('unlockDrama') }}</view>
          <view class="unlock-drama-subtitle">{{ t('unlockDramaConfirm', { count: pendingEpisode.pricePoints }) }}</view>
          <button class="pay-button drama-button" @click="unlockFullDrama">{{ t('unlockDrama') }}</button>
        </view>
        <button class="pay-ghost" @click="hasToken ? goRecharge() : goLogin()">{{ hasToken ? t('rechargeCredits') : t('signInCreate') }}</button>
      </view>
    </u-popup>
    <u-popup v-model="showCheckin" mode="center" border-radius="20" width="560rpx" :closeable="true">
      <view class="checkin">
        <view class="checkin-title">{{ t('checkinTitle') }}</view>
        <view class="checkin-credits">{{ t('myCredits') }} · {{ pointsBalance }}</view>
        <button class="pay-button checkin-button" :disabled="checkinBusy" @click="doCheckin">{{ t('checkinNow') }}</button>
        <button class="pay-ghost" @click="goRecharge">{{ t('rechargeCredits') }}</button>
      </view>
    </u-popup>
    <app-tab-bar current="home" />
  </view>
</template>

<script>
import api from '../../utils/api.js'
import { getLocale, localeOptions, setLocale, t as translate } from '../../utils/i18n.js'
import { notifyDataChanged, APP_DATA_EVENTS } from '../../utils/app-state.js'
import { setupVideo, cleanupVideo } from '../../utils/hls-adapter.js'

// 模块级闭包变量：用于防竞态的请求序号（不依赖 Vue data，避免下划线属性被忽略）
let _requestCounter = 0
// Tab 视频列表缓存：{ [tabKey]: { list: [...], ts: timestamp } }
// 让 Tab 切换像底部导航一样快——第二次点立即显示缓存，同时后台静默刷新
const _feedCache = {}

export default {
  data() {
    return {
      categories: [],
      feedTab: 'recommend',
      feedTabs: [],
      videoList: [],
      feedPage: 1,
      feedHasMore: true,
      feedLoading: false,
      sheetDrama: null,
      sheetEpisodes: [],
      sheetDramas: null,
      showDramaId: null,
      showDramaTitle: null,
      current: 0,
      num: 1,
      show: false,
      showPay: false,
      showCheckin: false,
      checkinBusy: false,
      pointsBalance: 0,
      pendingEpisode: null,
      commentCount: 0,
      comments: [],
      commentsLoading: false,
      commentsTotal: 0,
      commentsDramaId: null,
      showCommentPanel: false,
      commentScrollTop: 0,
      commentText: '',
      commentSubmitting: false,
      myUserId: null,
      replyingTo: null,
      replyingToNick: '',
      replyingToRoot: null,
      progressSeconds: 0,
      progressPercent: 0,
      draggingProgress: false,
      videoDuration: 0,
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
      playRetryCount: 0,
      isLandscape: false,
      windowHeight: 0,
      windowWidth: 0,
      boundaryHintTimer: null,
      playHint: null,
      descExpanded: false,
      autoNext: true,
      rewardedEpisodeId: null
    }
  },
  computed: {
    activeEpisodeId() {
      return this.videoList[this.current] ? this.videoList[this.current].episodeId : null
    },
    currentItem() {
      return this.videoList[this.current] || null
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
    videoAreaHeight() {
      // 评论区展开时视频压缩到上半部分，收起时占满整列
      if (!this.showCommentPanel) return '100%'
      return this.isLandscape ? '60%' : '58%'
    },
    formatCurrentTime() {
      return this.formatSec(this.progressSeconds)
    },
    formatTotalTime() {
      return this.videoDuration ? this.formatSec(this.videoDuration) : '0:00'
    },
  },
  onLoad(options) {
    this.refreshLocale()
    this.bootstrap()
    // #ifndef APP-PLUS
    // 进度条拖动：document 捕获阶段绑定，彻底绕过 swiper 的事件拦截
    document.addEventListener('pointerdown', this.onDocPointerDown, true)
    document.addEventListener('pointermove', this.onDocPointerMove, true)
    document.addEventListener('pointerup', this.onDocPointerUp, true)
    // #endif
  },
  onShow() {
    this.refreshLocale()
    uni.$on('overlayEvent', this.handleOverlayEvent)
    uni.$on('commentChanged', this.handleCommentChanged)
    this.updateOrientation()
    uni.onWindowResize(this.handleResize)
    this.tryResumeFeed()
    // 回到首页时清评论缓存键，确保跨页（播放页）的评论变更能同步
    this.commentsDramaId = null
    // 确保 overlay 层重新显示并同步最新状态（离开首页期间可能被隐藏过）
    // #ifdef APP-PLUS
    if (!this.show && !this.showCommentPanel) {
      this.showOverlay()
    } else {
      this.sendOverlayUpdate()
    }
    // #endif
  },
  onHide() {
    uni.$off('overlayEvent', this.handleOverlayEvent)
    uni.$off('commentChanged', this.handleCommentChanged)
    uni.offWindowResize(this.handleResize)
    this._stopFull()
  },
  onUnload() {
    this.clearVideoTimer()
    uni.$off('overlayEvent', this.handleOverlayEvent)
    uni.$off('commentChanged', this.handleCommentChanged)
    uni.offWindowResize(this.handleResize)
    this._stopFull()
    // #ifndef APP-PLUS
    document.removeEventListener('pointerdown', this.onDocPointerDown, true)
    document.removeEventListener('pointermove', this.onDocPointerMove, true)
    document.removeEventListener('pointerup', this.onDocPointerUp, true)
    // #endif
  },
  watch: {
    show(val) {
      // APP 端：弹窗打开时隐藏原生 overlay（否则原生层盖住弹窗），关闭时恢复
      if (val) {
        this.hideOverlay()
        // 弹窗打开时暂停视频（不销毁 video 元素，避免重新加载）
        const item = this.currentItem
        if (item && item.playback_url) {
          try {
            const ctx = uni.createVideoContext('myVideo' + item.episodeId, this)
            if (ctx && ctx.pause) ctx.pause()
          } catch (_) {}
          // #ifdef H5
          if (this._hlsHandle) {
            try {
              const videoEl = document.getElementById('myVideo' + item.episodeId)
              const ve = videoEl && videoEl.tagName === 'VIDEO' ? videoEl : videoEl && videoEl.querySelector ? videoEl.querySelector('video') : null
              if (ve) ve.pause()
            } catch (_) {}
          }
          // #endif
        }
      } else {
        // 选集弹窗关闭时：如果评论区也没展开，才恢复 overlay
        if (!this.showCommentPanel) this.showOverlay()
        this.$nextTick(() => {
          const item = this.currentItem
          if (item && item.playback_url) {
            try {
              const ctx = uni.createVideoContext('myVideo' + item.episodeId, this)
              if (ctx && ctx.play) ctx.play()
            } catch (_) {}
          }
        })
      }
    },
    showCommentPanel(val) {
      // 评论区展开 → 隐藏 APP 原生 overlay（右侧按钮会遮挡评论区内容）
      // 评论区收起 → 恢复 overlay（前提是选集弹窗没开）
      // #ifdef APP-PLUS
      if (val) {
        this.hideOverlay()
      } else if (!this.show) {
        this.showOverlay()
      }
      // #endif
    }
  },
  // 抖音式 feed 页面禁用系统下拉刷新（与 vertical swiper 手势冲突导致上滑被识别为下拉刷新）
  // 如果需要"下拉看最新"，应在 swiper 内部自定义实现，不能用 enablePullDownRefresh
  methods: {
    formatSec(s) {
      s = s || 0
      const m = Math.floor(s / 60), sec = Math.floor(s % 60)
      return m + ':' + (sec < 10 ? '0' : '') + sec
    },
    async bootstrap(dramaId, episodeId) {
      this.pageLoading = true
      this.loadError = ''
      // 先塞默认值，防止 api.categories() 还没返回时用户已经点了 Tab（此时 feedTabs 为空数组，find 找不到任何 tab）
      this.feedTabs = [
        { key: 'ai', label: 'AI 短剧', contentType: 'ai' },
        { key: 'recommend', label: this.t('recommendTab'), recommended: true }
      ]
      this.feedTab = 'recommend'
      // 立即把默认值同步给 nvue overlay（防止 categories 还没返回时 nvue 里 feedTabs=[]）
      this.sendOverlayUpdate()
      // 异步拉取后台配置的 contentType 选项，成功后覆盖默认
      api.categories().then(contentTypeOptions => {
        if (contentTypeOptions && contentTypeOptions.length) {
          const dynamicTabs = contentTypeOptions.map(opt => {
            const rawLabel = opt.name || opt.label || opt.id
            const translated = this.t(rawLabel)
            const label = translated && translated !== rawLabel ? translated : rawLabel
            return { key: opt.id, label, contentType: opt.id }
          })
          dynamicTabs.push({ key: 'recommend', label: this.t('recommendTab'), recommended: true })
          this.feedTabs = dynamicTabs
          this.sendOverlayUpdate()
        }
      }).catch(err => console.warn('categories fetch failed, using defaults', err))
      // 首屏直接拉推荐 Tab
      const timer = setTimeout(() => {
        this.pageLoading = false
        if (!this.videoList.length) {
          this.loadError = '加载超时，请重试'
        }
      }, 8000)
      // 异步加载用户设置（autoNext 开关）
      if (uni.getStorageSync('token')) {
        api.getSettings().then(settings => {
          if (settings) this.autoNext = settings.autoNext !== false && settings.auto_next !== false
        }).catch(() => {})
      }
      try {
        await this.loadFeed(1, true)
      } catch (err) {
        console.error('bootstrap loadFeed failed', err)
        this.loadError = err.message || '加载失败'
      } finally {
        clearTimeout(timer)
        this.pageLoading = false
      }
    },
    onLoadRetry() {
      this.bootstrap()
    },
    sendOverlayUpdate() {
      // feedTab/feedTabs 必须无条件同步（不能依赖 videoList，否则 bootstrap 初始值发不出去）
      uni.$emit('updateOverlay', {
        feedTab: this.feedTab,
        feedTabs: this.feedTabs.map(t => ({ key: t.key, label: t.label }))
      })
      const item = this.videoList[this.current]
      if (!item) return
      const likeCountText = item.like_count > 0 ? this.formatCount(item.like_count) : this.t('like')
      const commentCountText = this.commentCount > 0 ? this.formatCount(this.commentCount) : ''
      uni.$emit('updateOverlay', {
        favoriteActive: item.favorite === true,
        favoriteLabel: item.favorite === true ? this.t('favorited') : this.t('favorite'),
        likeActive: item.liked === true,
        likeCountText,
        commentCountText,
        commentLabel: this.t('comment'),
        shareLabel: this.t('share'),
        detailLabel: this.t('details'),
        episodeBrowseText: this.t('collectionEntry', { num: item.episode_no, total: item.total_episodes }),
        dramaTitle: item.title,
        episodeDesc: item.description,
        progressPercent: this.progressPercent || 0,
        progressCurrent: this.formatSec(this.progressSeconds),
        progressTotal: this.videoDuration ? this.formatSec(this.videoDuration) : '0:00',
        hideUI: !!this.showCommentPanel
      })
    },
    showOverlay() {
      // #ifdef APP-PLUS
      const ov = uni.getSubNVueById('indexOverlay')
      if (ov) {
        ov.show()
        this.sendOverlayUpdate()
      }
      // #endif
    },
    hideOverlay() {
      // #ifdef APP-PLUS
      const ov = uni.getSubNVueById('indexOverlay')
      if (ov) ov.hide()
      // #endif
    },
    handleOverlayEvent(e) {
      if (!e || !e.type) return
      switch (e.type) {
        case 'category': break // 旧分类子 tab 已移除，保留分支兼容
        case 'feedTab': this.selectFeedTab(e.data); break
        case 'browse': this.goCategory(); break
        case 'profile': this.goMine(); break
        case 'favorite': this.toggleFavorite(); break
        case 'like': this.toggleLike(); break
        case 'comment': this.openComments(); break
        case 'share': this.share(); break
        case 'details': this.goDetail(); break
        case 'episode': this.openShow(); break
        case 'tap': this.togglePlayback(this.videoList[this.current]); break
        case 'seek': this.onOverlaySeek(e.data); break
        case 'seekEnd': this.onOverlaySeekEnd(); break
      }
    },
    onOverlaySeek(data) {
      if (!data) return
      console.log('[INDEX] onOverlaySeek percent=', data.percent.toFixed(1), 'duration=', this.videoDuration)
      this.draggingProgress = true
      const percent = Math.max(0, Math.min(100, data.percent))
      this.progressPercent = percent
      const dur = this.videoDuration
      if (dur > 0) {
        const seconds = Math.floor(percent / 100 * dur)
        this.progressSeconds = seconds
        const item = this.currentItem
        if (item) {
          const ctx = uni.createVideoContext('myVideo' + item.episodeId, this)
          console.log('[INDEX] seeking to', seconds, 'ctx exists:', !!ctx, 'ctx.seek exists:', !!(ctx && ctx.seek))
          if (ctx && ctx.seek) ctx.seek(seconds)
        }
      } else {
        console.warn('[INDEX] videoDuration is 0, cannot seek')
      }
    },
    onOverlaySeekEnd() {
      this.draggingProgress = false
      this.sendOverlayUpdate()
    },
    // 顶部 Tab 切换：优先读缓存实现"瞬间切换"，同时后台静默刷新
    selectFeedTab(tab) {
      const key = typeof tab === 'object' ? tab.key : tab
      if (!key) return
      if (this.feedTab === key) return
      this.feedTab = key
      // 重置分页状态
      this.feedPage = 1
      this.feedHasMore = true
      this.sendOverlayUpdate()
      // 有缓存 → 立即显示（像底部 tabbar 一样秒切），同时后台静默刷新
      const cached = _feedCache[key]
      if (cached && cached.list && cached.list.length) {
        this.stopPlayback()
        this.videoList = cached.list
        this.current = 0
        this.num = 1
        this.$nextTick(() => {
          if (cached.list.length) this.playCurrent()
          this.sendOverlayUpdate()
        })
        // 后台静默刷新（不阻塞 UI）
        this._doLoadFeed(1, true, true)
      } else {
        // 没缓存 → 先停掉当前视频让用户看到"切换中"，然后发请求
        this.stopPlayback()
        this.videoList = []
        this.loading = true
        this._doLoadFeed(1, true, false)
      }
    },
    _doLoadFeed(page, reset, silent = false) {
      const currentTab = this.feedTabs.find(t => t.key === this.feedTab)
      if (!currentTab) return
      if (!reset && !this.feedHasMore) return
      if (this.feedLoading) return
      this.feedLoading = true
      const params = { page, size: 10 }
      if (currentTab.recommended) params.recommended = true
      else if (currentTab.contentType) params.contentType = currentTab.contentType
      api.feed(params).then(data => {
        const list = (data?.list || []).map(item => this.normalizeFeedItem(item))
        this.feedLoading = false
        // 判断是否还有下一页：优先用后端 hasMore
        if (data?.hasMore !== undefined) {
          this.feedHasMore = !!data.hasMore && list.length > 0
        } else {
          const total = Number(data?.total || 0)
          const fetched = reset ? list.length : this.videoList.length + list.length
          this.feedHasMore = total > fetched && list.length > 0
        }
        this.feedPage = page
        if (reset) {
          _feedCache[this.feedTab] = { list, ts: Date.now() }
          try { const oldCtx = uni.createVideoContext('myVideo' + this.activeEpisodeId, this); if (oldCtx) oldCtx.pause() } catch (_) {}
          this.stopPlayback()
          this.videoList = list
          this.current = 0
          this.num = 1
          this.$nextTick(() => {
            if (list.length) this.playCurrent()
            this.sendOverlayUpdate()
          })
        } else {
          this.videoList = this.videoList.concat(list)
        }
      }).catch(err => {
        this.feedLoading = false
        console.error('feed failed', err)
        if (reset) {
          this.loadError = String(err?.message || err || 'load failed')
        }
      })
    },
    async openCheckin() {
      if (!this.hasToken) {
        this.goLogin()
        return
      }
      this.showCheckin = true
      try {
        const data = await api.points()
        this.pointsBalance = Number(data?.user?.points || data?.points || 0)
      } catch (_) {}
    },
    async doCheckin() {
      if (this.checkinBusy) return
      this.checkinBusy = true
      try {
        const user = await api.checkin()
        this.pointsBalance = Number(user?.points || user?.user?.points || this.pointsBalance)
        notifyDataChanged(APP_DATA_EVENTS.points)
        this.toast(this.t('rewardAdded'))
        this.showCheckin = false
      } catch (err) {
        this.toast(err.message)
      } finally {
        this.checkinBusy = false
      }
    },
    async loadFeed(page, reset = false) {
      this._doLoadFeed(page, reset)
    },
    async loadMoreFeed() {
      this._doLoadFeed(this.feedPage + 1, false)
    },
    normalizeFeedItem(raw) {
      // 后端 feed 已返回 camelCase 的 dramaId/episodeId/playback_url
      const playbackUrl = raw.playback_url || raw.hls_url || raw.video_url || ''
      return {
        dramaId: raw.dramaId || raw.drama_id,
        episodeId: raw.episodeId || raw.episode_id,
        title: raw.title,
        description: raw.description || '',
        author_name: raw.author_name,
        cover_url: raw.cover_url,
        horizontal_cover_url: raw.horizontal_cover_url,
        vertical_cover_url: raw.vertical_cover_url,
        episode_no: raw.episode_no,
        episode_title: raw.episode_title,
        episode_desc: raw.episode_desc,
        playback_url: playbackUrl,
        playback_type: raw.playback_type,
        cloudflare_uid: raw.cloudflare_uid,
        liked: this.toBool(raw.liked ?? raw.is_liked ?? raw.isLiked),
        favorite: this.toBool(raw.favorite ?? raw.is_favorite ?? raw.isFavorite),
        like_count: Number(raw.like_count || 0),
        total_episodes: Number(raw.total_episodes || 0),
        unlocked: !!raw.unlocked,
        is_free: raw.is_free,
        free_episode_count: raw.free_episode_count,
        episode_price_points: raw.episode_price_points,
        duration_seconds: raw.duration_seconds,
        video_duration: raw.video_duration
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
    toBool(value) {
      if (value === undefined || value === null) return false
      if (typeof value === 'boolean') return value
      if (typeof value === 'number') return value !== 0
      if (typeof value === 'string') {
        const lowered = value.toLowerCase().trim()
        return lowered === 'true' || lowered === '1' || lowered === 'yes'
      }
      return !!value
    },
    change(e) {
      const newIndex = Number(e.detail.current)
      const prevIndex = this.current
      if (prevIndex === newIndex) return
      if (newIndex < 0 || newIndex >= this.videoList.length) return
      this.descExpanded = false

      // 触底加载
      if (this.feedHasMore && !this.feedLoading && newIndex >= this.videoList.length - 2) {
        this.loadMoreFeed()
      }

      // 保存历史
      const prevItem = this.videoList[prevIndex]
      if (prevItem && this.progressSeconds > 0) {
        this.setHistor(prevItem.dramaId, prevItem.episodeId)
      }

      // 停旧：停止当前播放实例，但保留预加载实例（可能正好是即将播放的下一个）
      // #ifdef H5
      if (this._hlsHandle) {
        try { this._hlsHandle.destroy() } catch (_) {}
        this._hlsHandle = null
      }
      // 如果滑到的是预加载的视频，不销毁预加载实例，由 playCurrent 接管
      if (newIndex !== prevIndex + 1 && this._prefetchHandle) {
        try { this._prefetchHandle.destroy() } catch (_) {}
        this._prefetchHandle = null
      }
      // #endif
      // #ifndef H5
      this.stopPlayback()
      // #endif
      // 注意：不要在这里 this.current = newIndex，swiper 内部已经是 newIndex 了
      // :current 只用于初始加载时的定位，后续不回写避免循环触发
      this.$set(this, 'current', newIndex)

      const item = this.videoList[newIndex]
      if (!item) return
      // H5 端：立即设置 playingEpisodeId，避免 Vue 重渲染时预加载的 video 元素被销毁
      // #ifdef H5
      this.playingEpisodeId = item.episodeId
      // #endif
      this.num = item.episode_no || 1
      this.progressSeconds = 0
      this.commentCount = 0
      this.comments = []
      this.commentsTotal = 0
      this.commentsDramaId = null
      this.cancelReply()
      this.prefetchCommentCount(item.dramaId, item.episodeId)
      this.sendOverlayUpdate()
      this.playCurrent(item)
      if (this.showCommentPanel) {
        this.loadComments()
      }
    },
    async prefetchCommentCount(dramaId, episodeId) {
      if (!dramaId) return
      try {
        const data = await api.dramaComments(dramaId, episodeId, 1, 1)
        const total = Number(data && data.total || 0)
        if (this.currentItem && String(this.currentItem.dramaId) === String(dramaId)) {
          this.commentCount = total
          // 评论数更新后同步到 overlay（显示在评论按钮下方）
          this.sendOverlayUpdate()
        }
      } catch (_) {}
    },
    onSwiperTouchStart(e) {
      const t = e.touches && e.touches[0]
      if (t) {
        this._touchStartY = t.clientY
      }
    },
    onSwiperTouchMove(e) {
      // 只更新位置，边界检测移到 touchend（APP 端 swiper 内部会吞 touchmove 冒泡）
    },
    onSwiperTouchEnd(e) {
      if (this._touchStartY == null) return
      const t = e.changedTouches && e.changedTouches[0]
      if (!t) return
      const deltaY = t.clientY - this._touchStartY
      this._touchStartY = null
      // 上滑到底：在最后一条时继续向上滑（deltaY 为负）超过 50px
      if (this.current === this.videoList.length - 1 && deltaY < -50 && !this.feedHasMore) {
        this.showBoundaryHint(this.t('lastEpisode'))
      }
      // 下滑到顶：在第一条时继续向下滑（deltaY 为正）超过 50px
      else if (this.current === 0 && deltaY > 50) {
        this.showBoundaryHint(this.t('firstEpisode'))
      }
    },
    showBoundaryHint(msg) {
      if (this.boundaryHintTimer) return
      uni.showToast({ title: msg, icon: 'none', duration: 1200 })
      this.boundaryHintTimer = setTimeout(() => { this.boundaryHintTimer = null }, 1500)
    },
    // 当前正在播放的视频：渲染 video 元素并播放
    shouldShowVideo(item, index) {
      return this.current === index && item && item.playback_url && this.playingEpisodeId === item.episodeId && !this.videoLoadError
    },
    // 预加载下一个视频：当前视频的下一集，有 playback_url，且尚未播放过
    shouldPreloadVideo(item, index) {
      return index === this.current + 1 && item && item.playback_url && !this.videoLoadError
    },
    // 统一判断是否渲染 video 元素（当前播放 + 预加载下一个）
    shouldRenderVideo(item, index) {
      return this.shouldShowVideo(item, index) || this.shouldPreloadVideo(item, index)
    },
    // 评论区打开时当前视频不销毁，只是隐藏（暂停）
    isVideoHidden(item, index) {
      return this.show && index === this.current
    },
    isVideoPreload(item, index) {
      return this.shouldPreloadVideo(item, index)
    },
    // 只有当前播放的视频才自动播放，预加载的不播
    shouldAutoplay(item, index) {
      return this.shouldShowVideo(item, index)
    },
    shouldShowVideoError(index) {
      return this.current === index && !!this.videoLoadError
    },
    async playCurrent(item) {
      item = item || this.currentItem
      if (!item) return
      // feed 模型：后端已直接返回 playback_url
      if (!item.playback_url) {
        this.openPay(item)
        return
      }
      this.progressSeconds = 0
      this.videoLoadError = ''
      const episodeId = item.episodeId
      this.playingEpisodeId = episodeId
      this.videoLoadError = ''
      this.playRetryCount = 0
      this.resetVideoState(item)
      this.$nextTick(() => {
        // #ifdef H5
        // H5 端：等 video DOM 元素出现后用 hls-adapter 接管
        this._attachH5Video(item, 10)
        // #endif
        // #ifndef H5
        uni.createVideoContext('myVideo' + item.episodeId, this).play()
        // #endif
        this.setHistor(item.dramaId, item.episodeId)
        this.sendOverlayUpdate()
        // 预加载下一个视频
        this._prefetchNextVideo()
      })
    },
    // H5 端：找到 video DOM 并 setupVideo（重试直到元素出现）
    // 如果视频已经被预加载（已有 __hls），直接 play 而不重新创建
    _attachH5Video(item, retries) {
      const videoId = 'myVideo' + item.episodeId
      // uni-app video 渲染后 DOM 是 <view id="myVideo123"><video></video></view>
      let videoEl = document.getElementById(videoId)
      if (videoEl && videoEl.tagName !== 'VIDEO') {
        videoEl = videoEl.querySelector('video')
      }
      if (!videoEl && retries > 0) {
        setTimeout(() => this._attachH5Video(item, retries - 1), 150)
        return
      }
      if (!videoEl) return

      // 如果预加载已经创建了 HLS 实例，直接 play
      if (videoEl.__hls) {
        videoEl.muted = true
        try { videoEl.play().catch(() => {}) } catch (_) {}
        this._hlsHandle = { destroy: () => { try { videoEl.__hls.destroy() } catch (_) {} ; videoEl.__hls = null }, hls: videoEl.__hls }
        return
      }

      // 清理旧实例
      if (this._hlsHandle) {
        try { this._hlsHandle.destroy() } catch (_) {}
        this._hlsHandle = null
      }

      // 用 hls-adapter 接管
      this._hlsHandle = setupVideo(videoEl, item.playback_url, true)
    },
    // H5 端：预加载下一个视频的 manifest+首片，切换时直接播放
    _prefetchNextVideo(retries) {
      // #ifdef H5
      if (retries == null) retries = 10
      const nextIndex = this.current + 1
      const nextItem = this.videoList[nextIndex]
      if (!nextItem || !nextItem.playback_url) return

      const videoId = 'myVideo' + nextItem.episodeId
      let videoEl = document.getElementById(videoId)
      if (videoEl && videoEl.tagName !== 'VIDEO') {
        videoEl = videoEl.querySelector('video')
      }
      if (!videoEl && retries > 0) {
        setTimeout(() => this._prefetchNextVideo(retries - 1), 150)
        return
      }
      if (!videoEl) return
      if (videoEl.__hls) return // 已预加载

      // 静默预加载：autoplay=false，只加载 manifest 和首片
      const handle = setupVideo(videoEl, nextItem.playback_url, false)
      this._prefetchHandle = handle
      // #endif
    },
    stopPlayback() {
      this.clearVideoTimer()
      // #ifdef H5
      if (this._hlsHandle) {
        try { this._hlsHandle.destroy() } catch (_) {}
        this._hlsHandle = null
      }
      if (this._prefetchHandle) {
        try { this._prefetchHandle.destroy() } catch (_) {}
        this._prefetchHandle = null
      }
      // #endif
      this.playingEpisodeId = null
      this.videoLoadError = ''
      this.feedPaused = false
      this.playHint = null
      if (this._playHintTimer) {
        clearTimeout(this._playHintTimer)
        this._playHintTimer = null
      }
    },
    // 停止+隐藏 overlay（仅 onHide/onUnload 时用）
    _stopFull() {
      this.stopPlayback()
      this.hideOverlay()
    },
    timeupdate(e) {
      if (this.draggingProgress) return
      this.progressSeconds = Math.floor(e.detail.currentTime || 0)
      if (this.progressSeconds > 0) this.videoReady()
      const dur = e.detail.duration || 0
      if (dur > 0) this.videoDuration = dur
      this.progressPercent = dur > 0 ? Math.min(100, (this.progressSeconds / dur) * 100) : 0
      const item = this.currentItem
      if (item && this.progressSeconds > 0 && this.progressSeconds % 10 === 0) {
        this.setHistor(item.dramaId, item.episodeId)
      }
      this.sendOverlayUpdate()
    },
    // document 捕获阶段的进度条拖动：彻底绕过 swiper 的事件拦截
    onDocPointerDown(e) {
      const bar = e.target && e.target.closest && e.target.closest('.video-progress-bar-h5')
      if (!bar) return
      e.stopImmediatePropagation()
      e.preventDefault()
      this.draggingProgress = true
      this._progressTrackEl = bar.querySelector('.video-progress-track')
      this.seekByClientX(e.clientX)
    },
    onDocPointerMove(e) {
      if (!this.draggingProgress) return
      e.stopImmediatePropagation()
      e.preventDefault()
      this.seekByClientX(e.clientX)
    },
    onDocPointerUp(e) {
      if (!this.draggingProgress) return
      this.draggingProgress = false
    },
    seekByClientX(clientX) {
      const track = this._progressTrackEl
      if (!track) return
      const rect = track.getBoundingClientRect()
      if (!rect || !rect.width) return
      const percent = Math.max(0, Math.min(100, ((clientX - rect.left) / rect.width) * 100))
      this.progressPercent = percent
      const dur = this.videoDuration
      if (dur > 0) {
        const seconds = Math.floor(percent / 100 * dur)
        this.progressSeconds = seconds
        const item = this.currentItem
        if (item) {
          const ctx = uni.createVideoContext('myVideo' + item.episodeId, this)
          if (ctx && ctx.seek) ctx.seek(seconds)
        }
      }
    },
    onProgressTouchStart(e) {
      this.draggingProgress = true
      this.seekByTouch(e)
      // 鼠标拖动：把 mousemove/mouseup 挂到 window，防止鼠标移出进度条后事件丢失
      // #ifndef APP-PLUS
      if (e.type && e.type.indexOf('mouse') === 0) {
        if (!this._winMouseMove) {
          this._winMouseMove = (ev) => { if (this.draggingProgress) this.seekByTouch(ev) }
          this._winMouseUp = () => { this.draggingProgress = false; window.removeEventListener('mousemove', this._winMouseMove); window.removeEventListener('mouseup', this._winMouseUp) }
        }
        window.addEventListener('mousemove', this._winMouseMove)
        window.addEventListener('mouseup', this._winMouseUp)
      }
      // #endif
    },
    onProgressTouchMove(e) {
      if (this.draggingProgress) this.seekByTouch(e)
    },
    onProgressTouchEnd() {
      this.draggingProgress = false
    },
    seekByTouch(e) {
      // 兼容 touch 事件和 mouse 事件（桌面浏览器测试用鼠标拖动）
      const touch = (e.touches && e.touches[0]) || (e.changedTouches && e.changedTouches[0]) || e
      const clientX = touch && touch.clientX
      if (clientX == null) return
      // 优先用进度条轨道的真实位置；window 事件没有 currentTarget 时用缓存
      let target = e.currentTarget || this._progressTrackEl
      // currentTarget 可能是整个进度条容器，需要找到子元素轨道
      if (target && target.querySelector) {
        const track = target.querySelector('.video-progress-track')
        if (track) target = track
      }
      const rect = target && target.getBoundingClientRect && target.getBoundingClientRect()
      if (!rect || !rect.width) return
      this._progressTrackEl = target
      const percent = Math.max(0, Math.min(100, ((clientX - rect.left) / rect.width) * 100))
      this.progressPercent = percent
      const dur = this.videoDuration
      if (dur > 0) {
        const seconds = Math.floor(percent / 100 * dur)
        this.progressSeconds = seconds
        const item = this.currentItem
        if (item) {
          const ctx = uni.createVideoContext('myVideo' + item.episodeId, this)
          if (ctx && ctx.seek) ctx.seek(seconds)
        }
      }
    },
    resetVideoState(item) {
      this.clearVideoTimer()
      this.videoLoadError = ''
      if (!item || !item.playback_url) return
      const episodeId = item.episodeId
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
      // 点视频区 → 先收起评论键盘，再切播放状态
      uni.hideKeyboard()
      this.blurCommentInput()
      item = item || this.currentItem
      if (!item) return
      const context = uni.createVideoContext('myVideo' + item.episodeId, this)
      if (this.feedPaused) {
        context.play()
        this.feedPaused = false
        this._showPlayHint('pause')
      } else {
        context.pause()
        this.feedPaused = true
        this._showPlayHint('play')
      }
    },
    _showPlayHint(type) {
      this.playHint = type
      if (this._playHintTimer) clearTimeout(this._playHintTimer)
      this._playHintTimer = setTimeout(() => {
        this.playHint = null
        this._playHintTimer = null
      }, 1200)
    },
    blurCommentInput() {
      // 让评论输入框失焦，收起键盘
      try {
        const el = document.querySelector('.comment-input')
        if (el) el.blur()
      } catch (_) {}
      // #ifndef H5
      uni.hideKeyboard()
      // #endif
    },
    onCommentFocus() {
      // 输入框获焦时，视频自动暂停，让用户专注写评论
      const item = this.currentItem
      if (item) {
        try { uni.createVideoContext('myVideo' + item.episodeId, this).pause() } catch (_) {}
        this.feedPaused = true
      }
    },
    onCommentBlur() {
      // 输入框失焦时，视频自动恢复播放
      const item = this.currentItem
      if (item) {
        try { uni.createVideoContext('myVideo' + item.episodeId, this).play() } catch (_) {}
        this.feedPaused = false
      }
    },
    videoError() {
      var item = this.videoList[this.current]
      this.clearVideoTimer()
      if (!item) return
      this.playingEpisodeId = null
      this.videoLoadError = this.t('videoLoadFailed')
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
      const item = this.videoList[this.current]
      if (!item) return
      // 保存历史记录
      this.setHistor(item.dramaId, item.episodeId)
      // 积分奖励（每集一次）
      if (item.episodeId && this.rewardedEpisodeId !== item.episodeId) {
        this.rewardedEpisodeId = item.episodeId
        api.rewardEpisode(item.episodeId).then(() => {
          notifyDataChanged(APP_DATA_EVENTS.points)
        }).catch(() => {})
      }
      // autoNext 开关关闭则不自动连播
      if (!this.autoNext) return
      // 如果当前是某剧的第一集，跳转到播放页从第二集开始播放
      if (Number(item.episode_no) === 1 && item.dramaId) {
        uni.navigateTo({
          url: '/pages/player/player?dramaId=' + item.dramaId + '&startEpisode=2'
        })
        return
      }
      // 否则滑到 Feed 中的下一个视频
      if (this.current < this.videoList.length - 1) {
        this.current += 1
      }
    },
    openShow(item) {
      const target = item || this.currentItem
      if (!target || !target.dramaId) return
      this.showDramaId = target.dramaId
      this.showDramaTitle = target.title || ''
      this.show = true
      // 异步加载完整剧集列表给选集弹窗
      api.drama(target.dramaId).then(drama => {
        this.sheetDrama = drama
        this.sheetEpisodes = (drama.episodes || []).map((ep, i) => {
          const epNo = this.pick(ep, 'episode_no', 'episodeNo') || i + 1
          return {
            id: this.pick(ep, 'id'),
            dramaId: target.dramaId,
            no: epNo,
            title: this.pick(ep, 'title') || this.t('episodeTitle', { num: epNo }),
            unlocked: !!ep.unlocked,
            videoUrl: this.pick(ep, 'video_url', 'videoUrl'),
            pricePoints: this.pick(ep, 'price_points', 'pricePoints') || 0,
            coverUrl: this.pick(ep, 'cover_url', 'coverUrl') || this.pick(drama, 'cover_url', 'coverUrl')
          }
        })
        this.sheetDramas = null // feed 模型下不展示"系列剧"Tab
      }).catch(() => {})
    },
    async switchDrama(id) {
      if (!id) return
      this.show = false
      await this.loadFeed(1, true)
      // 找到目标剧，滑到它
      const idx = this.videoList.findIndex(v => String(v.dramaId) === String(id))
      if (idx >= 0) {
        this.current = idx
      }
    },
    selectPlay(item) {
      if (!item) return
      this.show = false
      const dramaId = this.pick(item, 'dramaId', 'drama_id')
      const epId = this.pick(item, 'id', 'episodeId', 'episode_id')
      if (dramaId == null || epId == null) return
      // 跳转独立播放页：全屏播放 + 底部合集条 + 选集弹窗（未解锁集由播放页展示解锁界面）
      uni.navigateTo({ url: '/pages/player/player?dramaId=' + dramaId + '&episodeId=' + epId })
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
      const item = this.pendingEpisode
      if (!item) return
      try {
        await api.unlock(item.episodeId)
        this.showPay = false
        notifyDataChanged(APP_DATA_EVENTS.points)
        // 更新当前 feed item：标记已解锁，重新获取播放权限
        const targetIdx = this.videoList.findIndex(v => v.episodeId === item.episodeId)
        if (targetIdx >= 0) {
          this.videoList[targetIdx].unlocked = true
        }
        this.toast(this.t('unlockedToast'))
        this.playCurrent()
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
      const item = this.pendingEpisode || this.currentItem
      if (!item || !item.dramaId) return
      try {
        await api.unlockDrama(item.dramaId)
        this.showPay = false
        notifyDataChanged(APP_DATA_EVENTS.points)
        // 刷新 feed（重新拉第1页，重置状态）
        await this.loadFeed(1, true)
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
      const item = this.currentItem
      if (!item) return
      try {
        const res = await api.toggleFavorite(item.dramaId)
        item.favorite = this.toBool(res.favorite)
        notifyDataChanged(APP_DATA_EVENTS.favorite, { dramaId: item.dramaId, favorite: item.favorite })
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
    async toggleLike() {
      if (!uni.getStorageSync('token')) {
        this.goLogin()
        return
      }
      const item = this.currentItem
      if (!item) return
      try {
        const res = await api.toggleLike(item.dramaId)
        item.liked = this.toBool(res.liked)
        item.like_count = Number(res.likeCount || res.like_count || 0)
        this.sendOverlayUpdate()
      } catch (err) {
        this.toast(err.message)
      }
    },
    async openComments() {
      // 展开/显示评论区（H5 点击按钮与 APP overlay 的评论事件共用）
      this.showCommentPanel = true
      this.fetchMyUserId()
      this.loadComments()
      this.$nextTick(() => {
        this.commentScrollTop = 999999
      })
    },
    async loadComments() {
      const item = this.currentItem
      const dramaId = item ? item.dramaId : null
      const episodeId = item ? item.episodeId : null
      if (!dramaId) return
      // 缓存键含 episodeId，同一部剧不同集评论独立
      const cacheKey = dramaId + '|' + (episodeId || '')
      if (String(this.commentsDramaId || '') === cacheKey && this.comments.length > 0) return
      this.commentsDramaId = cacheKey
      this.commentsLoading = true
      try {
        const data = await api.dramaComments(dramaId, episodeId)
        this.comments = (data && data.records) || []
        this.commentsTotal = Number((data && data.total) || 0)
        this.commentCount = this.commentsTotal
      } catch (err) {
        this.toast(err.message)
      } finally {
        this.commentsLoading = false
      }
    },
    // 跨页评论变更：播放页发/删评论后，首页同步刷新
    handleCommentChanged(payload) {
      if (!payload) return
      const item = this.currentItem
      if (!item) return
      if (String(payload.dramaId) !== String(item.dramaId)) return
      if (payload.episodeId && item.episodeId && String(payload.episodeId) !== String(item.episodeId)) return
      this.commentsDramaId = null
      this.commentsTotal = 0
      this.commentCount = 0
      if (this.showCommentPanel) this.loadComments()
    },
    toggleCommentPanel() {
      // 点击右侧评论按钮 → 展开/收起评论区
      if (this.showCommentPanel) {
        this.closeCommentPanel()
        return
      }
      this.openComments()
    },
    closeCommentPanel() {
      this.showCommentPanel = false
      this.blurCommentInput()
    },
    onCommentTouchMove() {
      // 阻止评论区的触摸事件冒泡到 swiper，防止误切剧
    },
    async fetchMyUserId() {
      if (!this.hasToken || this.myUserId) return
      try {
        const me = await api.me()
        this.myUserId = me && me.id ? me.id : null
      } catch (_) {}
    },
    async submitComment() {
      const text = (this.commentText || '').trim()
      if (!text || this.commentSubmitting) return
      if (!this.hasToken) {
        this.goLogin()
        return
      }
      // 确保 myUserId 已获取（openComments 里是异步触发没 await，这里兜底）
      await this.fetchMyUserId()
      const item = this.currentItem
      if (!item || !item.dramaId) return
      this.commentSubmitting = true
      try {
        if (this.replyingTo) {
          // 回复
          const parentId = this.replyingTo.id
          const replyToUserId = this.replyingTo.user_id
          const parentOfParent = this.replyingToRoot || this.replyingTo
          const rootId = parentOfParent.id
          const reply = await api.replyComment(
            item.dramaId, item.episodeId, parentId, replyToUserId, text)
          // 插入到根评论的 children
          const root = this.comments.find(c => String(c.id) === String(rootId))
          if (root) {
            if (!root.children) root.children = []
            root.children.push({ ...reply, reply_to_nickname: this.replyingTo.nickname || '匿名' })
            root.replies_total = (root.replies_total || 0) + 1
          }
          this.commentCount = Math.max(0, this.commentCount) + 1
          this.cancelReply()
        } else {
          // 根评论
          const comment = await api.addComment(item.dramaId, text, item.episodeId)
          this.comments.unshift({ ...comment, children: [], replies_total: 0 })
          this.commentCount += 1
        }
        // 通知其他页面（播放页）评论已变更，需重新拉取
        uni.$emit('commentChanged', { dramaId: item.dramaId, episodeId: item.episodeId })
        this.commentText = ''
        this.toast(this.t('commentAdded'))
      } catch (err) {
        this.toast(err.message)
      } finally {
        this.commentSubmitting = false
      }
    },
    startReply(target, rootParent) {
      // target = 被回复的评论/回复；rootParent = 其根评论（如果 target 本身就是根则为 null）
      this.replyingTo = target
      this.replyingToNick = target.nickname || '匿名'
      this.replyingToRoot = rootParent || target
      // 聚焦输入框
      this.$nextTick(() => {
        const el = document.querySelector('.comment-input')
        if (el) el.focus()
      })
    },
    cancelReply() {
      this.replyingTo = null
      this.replyingToNick = ''
      this.replyingToRoot = null
    },
    async loadMoreReplies(root) {
      try {
        const data = await api.dramaCommentReplies(root.id, 1, 100)
        root.children = (data && data.records) || []
        root.replies_total = Number((data && data.total) || root.replies_total || 0)
      } catch (err) {
        this.toast(err.message)
      }
    },
    async removeComment(comment) {
      uni.showModal({
        title: '确认删除',
        content: '删除后将从评论区消失,此操作不可恢复',
        confirmText: '删除',
        confirmColor: '#ff6b6b',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await api.deleteComment(comment.id)
            // 删根评论 or 删回复
            let removed = false
            for (const cc of this.comments) {
              if (cc.children && cc.children.some(r => String(r.id) === String(comment.id))) {
                cc.children = cc.children.filter(r => String(r.id) !== String(comment.id))
                cc.replies_total = Math.max(0, (cc.replies_total || 0) - 1)
                removed = true
                break
              }
            }
            if (!removed) {
              this.comments = this.comments.filter(c => String(c.id) !== String(comment.id))
              this.commentsTotal = Math.max(0, this.commentsTotal - 1)
              this.commentCount = this.commentsTotal
            }
            const cur = this.currentItem
            if (cur) uni.$emit('commentChanged', { dramaId: cur.dramaId, episodeId: cur.episodeId })
            this.toast(this.t('commentDeleted'))
          } catch (err) {
            this.toast(err.message)
          }
        }
      })
    },
    // 评论内容截断：超过 20 字显示"展开/收起"
    isTruncated(comment) {
      if (!comment || !comment.content) return false
      const maxLen = 20
      return comment.content.length > maxLen && !comment._expanded
    },
    truncateText(content) {
      if (!content) return ''
      const maxLen = 20
      if (content.length <= maxLen) return content
      return content.substring(0, maxLen) + '…'
    },
    toggleExpand(comment) {
      this.$set(comment, '_expanded', !comment._expanded)
    },
    formatCount(num) {
      const n = Number(num || 0)
      if (n >= 10000) return (n / 10000).toFixed(1).replace(/\.0$/, '') + 'w'
      if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
      return String(n)
    },
    share() {
      const item = this.currentItem
      if (!item || !item.dramaId) return
      const path = '/pages/detail/detail?id=' + item.dramaId
      uni.setClipboardData({ data: path })
      if (uni.getStorageSync('token') && item.dramaId) {
        api.rewardShare(item.dramaId).then(() => notifyDataChanged(APP_DATA_EVENTS.points)).catch(() => {})
      }
    },
    offerRecharge(message) {
      if (!/point|credit|积分|余额|insufficient/i.test(message || '')) return
      setTimeout(() => this.goRecharge(), 600)
    },
    goDetail() {
      const item = this.currentItem
      if (!item) return
      uni.navigateTo({ url: '/pages/detail/detail?id=' + item.dramaId })
    },
    goCategory() {
      // "浏览"按钮：跳转剧场，带当前 tab 筛选参数
      const currentTab = this.feedTabs.find(t => t.key === this.feedTab)
      let qs = ''
      if (currentTab) {
        if (currentTab.recommended) qs = '?recommended=1'
        else if (currentTab.contentType) qs = '?contentType=' + encodeURIComponent(currentTab.contentType)
      }
      uni.redirectTo({ url: '/pages/theater/theater' + qs })
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
    updateOrientation() {
      const sys = uni.getSystemInfoSync()
      this.windowWidth = sys.windowWidth || sys.screenWidth || 375
      this.windowHeight = sys.windowHeight || sys.screenHeight || 667
      this.isLandscape = this.windowWidth > this.windowHeight
    },
    handleResize() {
      this.updateOrientation()
      this.$nextTick(() => {
        if (!this.feedPaused) {
          try {
            const v = uni.createVideoContext('myVideo' + this.activeEpisodeId, this)
            v && v.play && v.play()
          } catch (_) {}
        }
      })
    },
    refreshLocale() {
      this.locale = getLocale()
      uni.setNavigationBarTitle({ title: this.t('appTitle') })
    },
    tryResumeFeed() {
      // feed 模型：每剧1条，只按 dramaId 定位
      if (!this.videoList.length) return
      let resume
      try { resume = uni.getStorageSync('feed:resume') } catch (_) { return }
      if (!resume || !resume.dramaId) return
      if (resume.savedAt && Date.now() - resume.savedAt > 10 * 60 * 1000) {
        uni.removeStorageSync('feed:resume')
        return
      }
      const idx = this.videoList.findIndex(v => String(v.dramaId) === String(resume.dramaId))
      if (idx < 0) {
        uni.removeStorageSync('feed:resume')
        return
      }
      // 恢复到对应剧
      if (idx !== this.current) {
        this.stopPlayback()
        this.current = idx
        this.$nextTick(() => {
          this.playCurrent()
          this.seekTo(resume.progressSeconds || 0)
        })
      } else {
        this.seekTo(resume.progressSeconds || 0)
      }
      uni.removeStorageSync('feed:resume')
    },
    seekTo(seconds) {
      if (!seconds) return
      this.$nextTick(() => {
        try {
          const ctx = uni.createVideoContext('myVideo' + this.activeEpisodeId, this)
          if (ctx && ctx.seek) ctx.seek(seconds)
          this.progressSeconds = seconds
        } catch (_) {}
      })
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
  
}

.topbar {
  position: relative;
  top: 0;
  left: 0;
  right: 0;
  z-index: 999;
  pointer-events: auto;
  display: flex;
  align-items: center;
  height: calc(88rpx + env(safe-area-inset-top));
  padding: env(safe-area-inset-top) 20rpx 0;
  box-sizing: border-box;
  background: linear-gradient(to bottom, rgba(6, 7, 10, 0.55), rgba(6, 7, 10, 0));
}

/* 左右等宽占位：把 tabs 精确挤到视觉正中，右侧语言钮放最右 */
.topbar-side {
  width: 56rpx;
  flex-shrink: 0;
  display: flex;
  justify-content: flex-end;
}

.feed-tabs {
  flex: 1;
  position: relative;
  z-index: 19;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 48rpx;
  pointer-events: auto;
}

.feed-tab {
  position: relative;
  padding: 12rpx 8rpx 14rpx;
  margin: 0 16rpx;
  font-size: 34rpx;
  font-weight: 800;
  color: rgba(255, 255, 255, 0.55);
  transition: all 0.28s cubic-bezier(0.4, 0, 0.2, 1);
  background: transparent;
}

.feed-tab.active {
  color: #fff;
  font-weight: 900;
  text-shadow: 0 0 10rpx rgba(247, 198, 106, 0.4);
}

.feed-tab.active::after {
  content: "";
  position: absolute;
  left: 50%;
  bottom: 2rpx;
  width: 48rpx;
  height: 8rpx;
  transform: translateX(-50%);
  background: linear-gradient(90deg, #ffe0a1, #f7c66a);
  border-radius: 999rpx;
  box-shadow: 0 2rpx 16rpx rgba(247, 198, 106, 0.6);
}

.checkin {
  background: linear-gradient(180deg, #0d0f15 0%, #07080b 100%);
  color: #fff;
  padding: 40rpx 32rpx 36rpx;
  text-align: center;
}

.checkin-title {
  font-size: 32rpx;
  font-weight: 800;
}

.checkin-credits {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.65);
}

.checkin-button {
  margin-top: 28rpx;
}

.swipers-items-info-author {
  margin-bottom: 8rpx;
  font-size: 24rpx;
  font-weight: 700;
  color: #f7c66a;
  text-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.7);
}

/* ===== feed 新布局 ===== */
.feed-column {
  display: flex;
  flex-direction: column;
  width: 100%;
  background: #0b0b10;
}

.video-area {
  position: relative;
  flex-shrink: 0;
  overflow: hidden;
  background: #000;
}

.comment-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: #0b0b10;
  border-top: 1rpx solid rgba(255, 255, 255, 0.06);
}

.comment-area-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 24rpx 10rpx;
  flex-shrink: 0;
}

.comment-area-actions {
  display: flex;
  align-items: center;
  gap: 24rpx;
  flex-shrink: 0;
}

.comment-area-more {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.6);
}

.comment-area-collapse {
  font-size: 34rpx;
  line-height: 1;
  color: rgba(255, 255, 255, 0.6);
  padding: 4rpx 10rpx;
}
.comment-area-collapse:active {
  color: #f7c66a;
}

.comment-area-title {
  font-size: 28rpx;
  font-weight: 800;
  color: #fff;
}

.comment-count-badge {
  margin-left: 10rpx;
  font-size: 22rpx;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.55);
}

.comment-area-actions {
  font-size: 24rpx;
  color: #7f8a99;
}

.comment-scroll {
  flex: 1;
  min-height: 0;
  padding: 0 24rpx;
}

.comment-loading,
.comment-empty {
  padding: 40rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.45);
}

.comment-empty-icon {
  font-size: 48rpx;
  margin-bottom: 10rpx;
}

.comment-item {
  display: flex;
  align-items: flex-start;
  padding: 14rpx 0;
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.04);
}

.comment-avatar {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.1);
}

.comment-avatar-fallback {
  background: linear-gradient(135deg, #3a3f55, #2a2e42);
}

.comment-body {
  flex: 1;
  margin-left: 16rpx;
  min-width: 0;
}

.comment-nick {
  font-size: 24rpx;
  font-weight: 700;
  color: #f7c66a;
}

.comment-text {
  margin-top: 4rpx;
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.9);
  line-height: 1.5;
  word-break: break-all;
}
.comment-expand {
  display: inline-block;
  margin-left: 8rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.55);
}

.comment-del {
  flex-shrink: 0;
  margin-left: 12rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.4);
}

.comment-reply-btn {
  flex-shrink: 0;
  margin-left: 18rpx;
  font-size: 22rpx;
  color: #5b7fff;
}

.comment-meta {
  display: flex;
  align-items: center;
  margin-top: 8rpx;
}

.comment-reply-list {
  margin-top: 14rpx;
  padding: 16rpx 18rpx;
  background: rgba(255, 255, 255, 0.04);
  border-radius: 16rpx;
}

.comment-item-reply {
  padding: 10rpx 0;
  flex-direction: column;
  align-items: flex-start;
  border-bottom: none;
}

.comment-item-reply + .comment-item-reply {
  border-top: 1rpx solid rgba(255, 255, 255, 0.05);
  padding-top: 12rpx;
}

.comment-reply-text {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.85);
  line-height: 1.55;
  word-break: break-all;
}

.comment-nick-mini {
  font-size: 24rpx;
  font-weight: 700;
  color: #5b7fff;
}

.reply-at {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.5);
  margin: 0 4rpx;
}

.reply-colon {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.5);
}

.comment-reply-actions {
  display: flex;
  align-items: center;
  margin-top: 4rpx;
}

.comment-more-replies {
  padding-top: 10rpx;
  font-size: 22rpx;
  color: #5b7fff;
}

.comment-replying-to {
  position: absolute;
  top: -48rpx;
  left: 20rpx;
  right: 20rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8rpx 20rpx;
  font-size: 22rpx;
  color: #5b7fff;
  background: #1a1a26;
  border: 1rpx solid rgba(91, 127, 255, 0.4);
  border-radius: 999rpx;
}

.reply-cancel {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.5);
  padding: 0 10rpx;
}

.comment-more-link {
  padding: 24rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #5b7fff;
}

.comment-input-bar {
  position: relative;
  display: flex;
  align-items: center;
  padding: 12rpx 20rpx;
  padding-bottom: calc(12rpx + env(safe-area-inset-bottom));
  flex-shrink: 0;
  background: #111118;
  border-top: 1rpx solid rgba(255, 255, 255, 0.06);
  gap: 14rpx;
}

.comment-input {
  flex: 1;
  height: 64rpx;
  padding: 0 24rpx;
  font-size: 26rpx;
  color: #fff;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 999rpx;
}

.comment-send-btn {
  width: 140rpx;
  height: 64rpx;
  line-height: 64rpx;
  font-size: 26rpx;
  font-weight: 800;
  color: #11100d;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  border-radius: 999rpx;
  padding: 0;
  margin: 0;
  border: none;
}

.comment-send-btn[disabled] {
  opacity: 0.4;
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
  width: 44rpx;
  height: 38rpx;
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

.swipers {
  position: relative;
  width: 100%;
  background: #0b0b10;
}

/* 模糊封面背景：铺满视频区，作为 contain 模式黑边区域的填充 */
.video-bg-fill {
  position: absolute;
  inset: 0;
  z-index: 0;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  filter: blur(40rpx) brightness(0.55) saturate(1.1);
  transform: scale(1.2);
  pointer-events: none;
}

.video-area .swipers-items-video,
.video-area .poster-stage,
.video-area .swipers-items-imgsbg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  background: #000;
}

/* #ifdef H5 */
/* H5 端：视频组件背景透明，contain 模式的 letterbox 露出下方模糊封面 */
.video-area .swipers-items-video {
  background: transparent !important;
  object-fit: contain;
  object-position: center 42%;
}
.video-area uni-video,
.video-area uni-video .uni-video-container,
.video-area uni-video video {
  background-color: transparent !important;
  background: transparent !important;
}
/* #endif */

/* 预加载或评论区打开时隐藏视频但不销毁 */
.video-hidden {
  visibility: hidden !important;
  pointer-events: none !important;
}

.video-tap-area {
  /* APP 端原生 video 内的 cover-view 必须绝对定位才能铺满，否则点击层大小为 0 */
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 220rpx;
  width: auto;
  height: auto;
}

/* 底部渐变遮罩：衬托作者/标题/简介，不拦截点击 */
.video-bottom-shade {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 280rpx;
  z-index: 5;
  pointer-events: none;
  background: linear-gradient(180deg,
    rgba(0, 0, 0, 0) 0%,
    rgba(0, 0, 0, 0.35) 45%,
    rgba(0, 0, 0, 0.72) 100%);
}

/* 抖音风格进度条：贴底 tabbar 上方，左右时间 */
.video-progress-bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 80rpx;
  height: 60rpx;
  pointer-events: auto;
}
/* H5/小程序端进度条：位于合集选集入口下方，时间 | 轨道 | 时间 */
.video-progress-bar-h5 {
  display: flex;
  align-items: center;
  width: 100%;
  height: 56rpx;
  margin-top: 12rpx;
  pointer-events: auto;
  z-index: 20;
}
.video-progress-bar-h5 .video-progress-time {
  flex-shrink: 0;
  width: 84rpx;
  font-size: 22rpx;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.9);
  white-space: nowrap;
}
.video-progress-bar-h5 .video-progress-time-right {
  text-align: right;
}
.video-progress-bar-h5 .video-progress-track {
  position: relative;
  flex: 1;
  height: 56rpx;
  margin: 0 12rpx;
  background-color: transparent;
  border-radius: 0;
  touch-action: none;
}
.video-progress-bar-h5 .video-progress-track::after {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  margin-top: -2rpx;
  height: 4rpx;
  background-color: rgba(255, 255, 255, 0.3);
  border-radius: 2rpx;
}
.video-progress-bar-h5 .video-progress-fill {
  position: absolute;
  left: 0;
  top: 50%;
  margin-top: -4rpx;
  height: 8rpx;
  background-color: #ffffff;
  border-radius: 4rpx;
  z-index: 1;
}
.video-progress-bar-h5 .video-progress-thumb {
  position: absolute;
  top: 50%;
  width: 24rpx;
  height: 24rpx;
  margin: -12rpx 0 0 -12rpx;
  border-radius: 12rpx;
  background-color: #ffffff;
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.5);
  z-index: 2;
}

.video-tap-area {
  /* APP 端原生 video 内的 cover-view 必须绝对定位才能铺满，否则点击层大小为 0 */
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 220rpx;
  width: auto;
  height: auto;
}

/* 播放/暂停反馈提示（cover-view 不支持 flex/transform/animation） */
.video-play-hint {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
}
.video-play-hint-img {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 140rpx;
  height: 140rpx;
  margin-top: -70rpx;
  margin-left: -70rpx;
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

.video-area::after {
  position: absolute;
  inset: 0;
  content: "";
  pointer-events: none;
  background:
    linear-gradient(to bottom, rgba(0, 0, 0, 0.16), rgba(0, 0, 0, 0) 28%),
    linear-gradient(to top, rgba(0, 0, 0, 0.6), rgba(0, 0, 0, 0.08) 54%, rgba(0, 0, 0, 0));
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
  right: 140rpx;
  bottom: 60rpx;
  z-index: 10;
  touch-action: none;
}

.swipers-items-info-title {
  font-size: 30rpx;
  font-weight: 700;
  line-height: 1.3;
  color: #ffffff;
  text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.8), 0 0 20rpx rgba(0, 0, 0, 0.4);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.swipers-items-info-content {
  flex: 1;
  margin-top: 6rpx;
  font-size: 22rpx;
  line-height: 1.4;
  color: rgba(255, 255, 255, 0.85);
  text-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.7);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.swipers-items-info-content.expanded {
  -webkit-line-clamp: unset;
  display: block;
  overflow: visible;
}
.desc-row {
  display: flex;
  align-items: flex-end;
}
.desc-more-btn {
  flex-shrink: 0;
  margin-left: 8rpx;
  font-size: 22rpx;
  color: #f7c66a;
  text-decoration: underline;
  white-space: nowrap;
}

.swipers-items-info-num {
  display: inline-flex;
  align-items: center;
  margin-top: 8rpx;
  padding: 6rpx 14rpx;
  font-size: 22rpx;
  font-weight: 800;
  color: #0d0d10;
  background: linear-gradient(135deg, #ffffff, #f6f6f6);
  border-radius: 999rpx;
  box-shadow:
    0 4rpx 12rpx rgba(0, 0, 0, 0.5),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.8);
  transition: transform 0.18s ease;
}

.swipers-items-info-num:active {
  transform: scale(0.95);
}

.swipers-items-right {
  position: absolute;
  right: 16rpx;
  top: 42%;
  z-index: 10;
}

/* 彩色底座按钮：本页完整定义（H5 页面样式按页加载，不可依赖其他页面的全局样式） */
.action {
  display: flex;
  flex-direction: column;
  align-items: center;
  transition: transform 0.2s ease;
}

.action:active {
  transform: scale(0.88);
}

.action text {
  margin-top: 8rpx;
  font-size: 22rpx;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.9);
  text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.7);
}

.swipers-items-right .action {
  width: 104rpx;
  margin-bottom: 26rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.swipers-items-right .action text {
  font-size: 26rpx;
  margin-top: 8rpx;
}

.swipers-items-right .action u-icon {
  text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.6);
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

.pay-title {
  display: flex;
  align-items: center;
  font-size: 24rpx;
  font-weight: 800;
  margin-bottom: 14rpx;
}

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
