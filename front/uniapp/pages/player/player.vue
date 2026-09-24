<template>
  <view class="page">
    <view v-if="loading" class="loading-state">
      <view class="loading-spinner"></view>
      <text class="loading-text">{{ t('loading') }}</text>
    </view>

    <template v-else>
      <view class="player-wrap" :style="{ height: windowHeight + 'px' }">
      <swiper
        v-if="episodes.length"
        class="feed-swiper"
        :class="{ 'is-landscape': isLandscape }"
        :style="{ height: videoAreaHeight + 'px' }"
        :current="feedIndex"
        :vertical="true"
        :indicator-dots="false"
        :autoplay="false"
        :duration="250"
        @change="onFeedChange"
        @touchstart="onSwiperTouchStart"
        @touchmove="onSwiperTouchMove"
      >
        <swiper-item v-for="(ep, index) in episodes" :key="ep.id">
          <view class="screen" :style="{ height: videoAreaHeight + 'px' }">
            <video
              v-if="ep.id === activeId && ep.playbackUrl && !isDownloading && !videoLoadError && !showSheet"
              id="mainVideo"
              class="video"
              :src="ep.playbackUrl"
              :poster="ep.coverUrl || coverOf(drama)"
              :autoplay="true"
              :controls="isFullScreen"
              :show-center-play-btn="false"
              :show-fullscreen-btn="isFullScreen"
              :show-progress="isFullScreen"
              :show-play-btn="isFullScreen"
              object-fit="contain"
              @timeupdate="timeupdate"
              @ended="ended"
              @error="videoError"
              @play="onVideoPlay"
              @pause="onVideoPause"
              @fullscreenchange="onFullScreenChange"
              @loadedmetadata="onVideoLoadedMetadata"
            >
              <cover-view class="video-tap-area" @tap="togglePlayback"></cover-view>
              <!-- 播放/暂停反馈图标 -->
              <cover-view v-if="ep.id === activeId && playHint" class="video-play-hint">
                <cover-image
                  class="video-play-hint-img"
                  :src="playHint === 'pause' ? '/static/images/playhint/pause.png' : '/static/images/playhint/play.png'"
                />
              </cover-view>
            </video>

            <view v-else-if="ep.id === activeId && ep.videoUrl && videoLoadError" class="locked-screen" @click="retryCurrent">
              <image class="locked-cover" :src="ep.coverUrl || coverOf(drama)" mode="aspectFill" />
              <view class="locked-mask">
                <view class="locked-title">{{ videoLoadError }}</view>
                <button class="primary-btn" @click.stop="retryCurrent">{{ t('retry') }}</button>
              </view>
            </view>

            <view v-else-if="ep.id === activeId && !ep.videoUrl" class="locked-screen">
              <image class="locked-cover" :src="ep.coverUrl || coverOf(drama)" mode="aspectFill" />
              <view class="locked-mask">
                <view class="locked-title">{{ t('premiumEpisode') }}</view>
                <view class="locked-desc">{{ t('unlockSubtitle', { count: ep.pricePoints }) }}</view>
                <button class="primary-btn" @click="unlockEpisode">{{ t('unlockWithCredits') }}</button>
                <button v-if="wholePrice" class="ghost-btn" @click="unlockWhole">{{ t('unlockDrama') }} · {{ wholePrice }}</button>
                <button class="plain-btn" @click="goRecharge">{{ t('rechargeCredits') }}</button>
              </view>
            </view>

            <view v-else class="locked-screen" @click="jumpTo(index)">
              <image class="locked-cover" :src="ep.coverUrl || coverOf(drama)" mode="aspectFill" />
            </view>

            <view v-if="isDownloading" class="downloading-mask">
              <view class="downloading-spinner"></view>
              <view class="downloading-text">{{ t('downloadingVideo') }} {{ downloadProgress }}%</view>
              <view class="downloading-bar">
                <view class="downloading-bar-fill" :style="{ width: downloadProgress + '%' }"></view>
              </view>
            </view>
          </view>

          <!-- #ifndef APP-PLUS -->
          <!-- 右侧功能栏：跟首页一样，绝对定位在视频画面底部 -->
          <view class="side-actions" :class="{ 'is-landscape': isLandscape }">
            <view class="action" @click="toggleFavorite">
              <u-icon :name="favorite ? 'bookmark-fill' : 'bookmark'" size="56" :color="favorite ? '#ff4d67' : '#ffffff'" />
              <text :style="{ color: favorite ? '#ff4d67' : 'rgba(255,255,255,0.9)' }">{{ favorite ? t('favorited') : t('favorite') }}</text>
            </view>
            <view class="action" @click="toggleLike">
              <u-icon :name="like ? 'thumb-up-fill' : 'thumb-up'" size="56" :color="like ? '#f3b84d' : '#ffffff'" />
              <text :style="{ color: like ? '#f3b84d' : 'rgba(255,255,255,0.9)' }">{{ formatCount(likeCount) }}</text>
            </view>
            <view class="action" @click="openComments">
              <u-icon name="chat" color="#ffffff" size="56" />
              <text>{{ commentCount > 0 ? formatCount(commentCount) : t('comment') }}</text>
            </view>
            <view class="action" @click="share">
              <u-icon name="share-fill" color="#ffffff" size="56" />
              <text>{{ t('share') }}</text>
            </view>
            <view class="action" @click="goDetail">
              <u-icon name="list-dot" color="#ffffff" size="56" />
              <text>{{ t('details') }}</text>
            </view>
          </view>

          <!-- 全屏按钮：药丸样式，视频下方居中 -->
          <view v-if="!isFullScreen && !showComments && !showSheet && isLandscapeVideo" class="fullscreen-pill" @click.stop="toggleFullScreen">
            <text class="fullscreen-pill-icon">⛶</text>
            <text class="fullscreen-pill-text">全屏观看</text>
          </view>

          <!-- 底部信息栏：标题+简介+进度条+合集选集，贴视频画面底部 -->
          <view class="bottom-info" v-if="!isLandscape">
            <view class="info-title">{{ drama ? drama.title : '' }}<text class="info-episode-no" v-if="activeEpisode"> · 第{{ activeEpisode.episodeNo }}集</text></view>
            <view class="desc-row" v-if="(activeEpisode && activeEpisode.description) || (drama && drama.description)">
              <view class="info-desc" :class="{ expanded: descExpanded }" v-if="activeEpisode && activeEpisode.description">{{ activeEpisode.description }}</view>
              <view class="info-desc" :class="{ expanded: descExpanded }" v-else>{{ drama.description }}</view>
              <view class="desc-more-btn" @click.stop="descExpanded = !descExpanded">{{ descExpanded ? '收起' : '更多' }}</view>
            </view>
            <view class="video-progress-bar"
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
            <view class="episode-pill" @click.stop="openSheet">
              {{ t('collectionEntry', { num: activeEpisode ? activeEpisode.episodeNo : 1, total: episodes.length }) }}
            </view>
          </view>
          <!-- #endif -->
        </swiper-item>
      </swiper>

      <view v-else class="locked-screen">
        <image class="locked-cover" :src="coverOf(drama)" mode="aspectFill" />
      </view>

      <!-- #ifndef APP-PLUS -->
      <view class="back-btn" @click="back">←</view>
      <view class="landscape-pill" v-if="isLandscape" @click="openSheet">
        {{ t('collectionEntry', { num: activeEpisode ? activeEpisode.episodeNo : 1, total: episodes.length }) }}
      </view>
      <!-- #endif -->

      <episode-sheet
        :show.sync="showSheet"
        :drama="drama"
        :episodes="episodes"
        :active-id="activeId"
        :dramas="seriesDramas"
        @select="selectEpisode"
        @switch-drama="switchDrama"
      />

      <!-- 内嵌式评论区（跟首页一致：视频压缩 + 内嵌评论区，根评论 + 嵌套回复 + 截断展开 + 回复按钮） -->
      <view v-show="showComments" class="comments-panel" @click="blurCommentInput">
        <view class="comments-panel-header">
          <view class="comments-panel-title">{{ t('commentTitle') }}<text v-if="commentsTotal > 0" class="comments-total-num">{{ commentsTotal }}</text></view>
          <view class="comments-panel-actions">
            <text class="comments-panel-more" @click.stop="goDetail">{{ t('commentMore') }} ›</text>
            <text class="comments-panel-close" @click.stop="closeComments">⌄</text>
          </view>
        </view>
        <scroll-view
          scroll-y
          class="comments-scroll"
          :scroll-with-animation="true"
          :scroll-top="commentScrollTop"
          @touchmove.stop.prevent="onCommentTouchMove"
          @click.stop="blurCommentInput"
        >
          <view v-if="commentsLoading" class="comments-empty">{{ t('loading') }}</view>
          <view v-else-if="!comments.length" class="comments-empty">
            <view class="comments-empty-icon">💬</view>
            <view>{{ t('commentEmpty') }}</view>
          </view>
          <view v-else>
            <!-- 根评论 + 嵌套回复（结构与首页 index.vue 完全一致） -->
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
        </scroll-view>
        <view class="comments-input-row" @click.stop>
          <view v-if="replyingTo" class="comment-replying-to">
            回复 @{{ replyingToNick }}
            <text class="reply-cancel" @click.stop="cancelReply">×</text>
          </view>
          <input
            class="comments-input"
            v-model="commentText"
            :placeholder="replyingTo ? '回复 @' + replyingToNick : t('commentPlaceholder')"
            maxlength="500"
            confirm-type="send"
            @confirm="submitComment"
            @focus="onCommentFocus"
            @blur="onCommentBlur"
          />
          <button class="comments-send" :disabled="commentSubmitting || !commentText.trim()" @click.stop="submitComment">{{ t('commentSend') }}</button>
        </view>
      </view>

      </view><!-- /player-wrap -->
    </template>
  </view>
</template>

<script>
import api from '../../utils/api.js'
import { getLocale, t as translate } from '../../utils/i18n.js'
import { notifyDataChanged, APP_DATA_EVENTS } from '../../utils/app-state.js'
import { resolveEpisodeSource } from '../../utils/playback.js'
import { setupVideo, cleanupVideo } from '../../utils/hls-adapter.js'

export default {
  data() {
    return {
      dramaId: null,
      episodeId: null,
      startEpisodeNo: null,
      drama: null,
      seriesDramas: [],
      episodes: [],
      feedIndex: 0,
      showSheet: false,
      pointBalance: 0,
      autoNext: true,
      progressSeconds: 0,
      progressPercent: 0,
      draggingProgress: false,
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
      playHint: null,
      descExpanded: false,
      favorite: false,
      like: false,
      likeCount: 0,
      commentCount: 0,
      showComments: false,
      comments: [],
      commentsTotal: 0,
      commentsLoading: false,
      commentSubmitting: false,
      commentText: '',
      commentScrollTop: 0,
      commentsDramaId: null,
      myUserId: null,
      replyingTo: null,
      replyingToNick: '',
      replyingToRoot: null,
      isDownloading: false,
      downloadProgress: 0,
      downloadedFilePath: '',
      isLandscape: false,
      isFullScreen: false,
      isLandscapeVideo: false,
      windowHeight: 0,
      windowWidth: 0,
      boundaryHintTimer: null,
    }
  },
  computed: {
    activeId() {
      return this.activeEpisode ? this.activeEpisode.id : null
    },
    activeEpisode() {
      return this.episodes[this.feedIndex] || null
    },
    wholePrice() {
      return Number(this.drama?.wholePricePoints || this.drama?.whole_price_points || 0)
    },
    dramaAuthor() {
      return this.drama ? (this.drama.author_name || this.drama.authorName || '') : ''
    },
    videoAreaHeight() {
      // 评论区展开时视频压缩到 58%，选集弹窗时视频也压缩
      if (this.showComments || this.showSheet) {
        return Math.round(this.windowHeight * 0.58)
      }
      return this.windowHeight
    },
    formatCurrentTime() {
      return this.formatSec(this.progressSeconds)
    },
    formatTotalTime() {
      return this.videoDuration ? this.formatSec(this.videoDuration) : '0:00'
    }
  },
  onLoad(options) {
    this.dramaId = options.dramaId || options.id
    this.episodeId = options.episodeId || options.courseDetailsId
    this.startEpisodeNo = options.startEpisode ? Number(options.startEpisode) : null
    this.load()
    // #ifndef APP-PLUS
    // 进度条拖动：document 捕获阶段绑定，彻底绕过 swiper 的事件拦截
    document.addEventListener('pointerdown', this.onDocPointerDown, true)
    document.addEventListener('pointermove', this.onDocPointerMove, true)
    document.addEventListener('pointerup', this.onDocPointerUp, true)
    // #endif
  },
  onShow() {
    console.log('[PLAYER] onShow called')
    this.locale = getLocale()
    this.loadPoints()
    uni.$on('playerOverlayEvent', this.handleOverlayEvent)
    uni.$on('commentChanged', this.handleCommentChanged)
    this.showOverlay()
    this.updateOrientation()
    uni.onWindowResize(this.handleResize)
    // 回到播放页时清评论缓存键，确保跨页（首页）的评论变更能同步
    this.commentsDramaId = null
  },
  onHide() {
    console.log('[PLAYER] onHide called')
    this.persistFeedResume()
    uni.$off('playerOverlayEvent', this.handleOverlayEvent)
    uni.$off('commentChanged', this.handleCommentChanged)
    uni.offWindowResize(this.handleResize)
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
    uni.offWindowResize(this.handleResize)
    this.hideOverlay()
    // #ifndef APP-PLUS
    document.removeEventListener('pointerdown', this.onDocPointerDown, true)
    document.removeEventListener('pointermove', this.onDocPointerMove, true)
    document.removeEventListener('pointerup', this.onDocPointerUp, true)
    // #endif
  },
  watch: {
    showSheet(val) {
      console.log('[PLAYER] watch showSheet:', val)
      if (val) {
        this.hideOverlay()
      } else {
        this.showOverlay()
        this.$nextTick(() => {
          if (this.activeEpisode && this.activeEpisode.playbackUrl) {
            try {
              const ctx = uni.createVideoContext('mainVideo', this)
              if (ctx && ctx.play) ctx.play()
            } catch (_) {}
          }
        })
      }
    },
    showComments(val) {
      console.log('[PLAYER] watch showComments:', val)
      if (val) {
        this.hideOverlay()
      } else if (!this.showSheet) {
        this.showOverlay()
        // 评论关闭后恢复播放
        this.$nextTick(() => {
          if (this.activeEpisode && this.activeEpisode.playbackUrl) {
            try {
              const ctx = uni.createVideoContext('mainVideo', this)
              if (ctx && ctx.play) ctx.play()
            } catch (_) {}
          }
        })
      }
    }
  },
  methods: {
    formatSec(s) {
      s = s || 0
      const m = Math.floor(s / 60), sec = Math.floor(s % 60)
      return m + ':' + (sec < 10 ? '0' : '') + sec
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
    async load() {
      this.loading = true
      try {
        const [drama, settings, dramas] = await Promise.all([
          api.drama(this.dramaId),
          uni.getStorageSync('token') ? api.getSettings().catch(() => null) : Promise.resolve(null),
          api.dramas().catch(() => [])
        ])
        this.drama = drama
        this.favorite = this.toBool(drama.favorite)
        this.like = this.toBool(drama.liked)
        this.likeCount = Number(drama.like_count || drama.likeCount || 0)
        this.seriesDramas = (dramas || []).filter(item => String(item.id) !== String(this.dramaId))
        this.episodes = (drama.episodes || []).map((item, index) => this.normalizeEpisode(item, index))
        let targetIndex = -1
        // 优先用 episodeId 定位
        if (this.episodeId) {
          targetIndex = this.episodes.findIndex(item => String(item.id) === String(this.episodeId))
        }
        // 如果没有 episodeId 但有 startEpisodeNo，按集号定位
        if (targetIndex < 0 && this.startEpisodeNo) {
          targetIndex = this.episodes.findIndex(item => Number(item.episodeNo) === this.startEpisodeNo)
        }
        this.feedIndex = targetIndex >= 0 ? targetIndex : 0
        if (settings) this.autoNext = settings.autoNext !== false && settings.auto_next !== false
        await this.loadPoints()
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
      } finally {
        this.loading = false
        this.$nextTick(() => {
          this.playActiveEpisode()
          this.sendOverlayUpdate()
        })
      }
    },
    sendOverlayUpdate() {
      uni.$emit('updatePlayerOverlay', {
        favoriteActive: this.favorite === true,
        favoriteText: this.favorite === true ? this.t('favorited') : this.t('favorite'),
        likeActive: this.like === true,
        likeText: this.likeCount > 0 ? this.formatCount(this.likeCount) : this.t('like'),
        commentText: this.commentCount > 0 ? this.t('comment') + ' ' + this.formatCount(this.commentCount) : this.t('comment'),
        shareText: this.t('share'),
        detailsText: this.t('details'),
        episodeBrowseText: this.t('collectionEntry', { num: this.activeEpisode ? this.activeEpisode.episodeNo : 1, total: this.episodes.length }),
        dramaTitle: this.drama ? this.drama.title : '',
        episodeTitle: this.activeEpisode ? this.activeEpisode.title : '',
        episodeNo: this.activeEpisode ? this.activeEpisode.episodeNo : 1,
        total: this.episodes.length,
        // 进度条数据
        progressPercent: this.progressPercent,
        progressCurrent: this.formatSec(this.progressSeconds),
        progressTotal: this.videoDuration ? this.formatSec(this.videoDuration) : '0:00'
      })
    },
    showOverlay() {
      // #ifdef APP-PLUS
      const ov = uni.getSubNVueById('playerOverlay')
      console.log('[PLAYER] showOverlay, subNVue exists:', !!ov)
      if (ov) {
        ov.show()
        this.sendOverlayUpdate()
      } else {
        console.warn('[PLAYER] showOverlay FAILED: subNVue playerOverlay not found')
      }
      // #endif
    },
    hideOverlay() {
      // #ifdef APP-PLUS
      const ov = uni.getSubNVueById('playerOverlay')
      console.log('[PLAYER] hideOverlay, subNVue exists:', !!ov)
      if (ov) ov.hide()
      // #endif
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
        case 'episode': this.openSheet(); break
        case 'tap': this.togglePlayback(); break
        case 'fullscreen': this.toggleFullScreen(); break
        case 'seek': {
          const percent = e.data ? e.data.percent : 0
          this.draggingProgress = true
          this.progressPercent = percent
          const dur = this.videoDuration
          if (dur > 0) {
            this.progressSeconds = Math.floor(percent / 100 * dur)
          }
          break
        }
        case 'seekEnd': {
          this.draggingProgress = false
          const ctx = uni.createVideoContext('mainVideo', this)
          if (ctx && ctx.seek) ctx.seek(this.progressSeconds)
          // seek 完成后同步一次 overlay 显示
          this.sendOverlayUpdate()
          break
        }
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
    // swiper 滑动切换：上滑下一集 / 下滑上一集
    onFeedChange(e) {
      const newIndex = Number(e.detail.current)
      if (newIndex === this.feedIndex) return
      if (this.progressSeconds > 0) this.saveHistory()
      this.feedIndex = newIndex
      this.descExpanded = false
      this.resetProgressState()
      this.$nextTick(() => {
        this.playActiveEpisode()
        this.sendOverlayUpdate()
      })
    },
    onSwiperTouchStart(e) {
      this._touchStartY = e.touches && e.touches[0] ? e.touches[0].clientY : 0
    },
    onSwiperTouchMove(e) {
      if (this.feedIndex === 0 && this._touchStartY != null) {
        const deltaY = e.touches[0].clientY - this._touchStartY
        // 向下滑（deltaY > 0）且幅度超过 50px，在第一集边界提示
        if (deltaY > 50) {
          this.showBoundaryHint(this.t('firstEpisode'))
          this._touchStartY = null // 防止连续触发
        }
      } else if (this.feedIndex === this.episodes.length - 1 && this._touchStartY != null) {
        const deltaY = e.touches[0].clientY - this._touchStartY
        // 向上滑（deltaY < 0）且幅度超过 50px，在最后一集边界提示
        if (deltaY < -50) {
          this.showBoundaryHint(this.t('lastEpisode'))
          this._touchStartY = null
        }
      }
    },
    showBoundaryHint(msg) {
      if (this.boundaryHintTimer) return
      uni.showToast({ title: msg, icon: 'none', duration: 1200 })
      this.boundaryHintTimer = setTimeout(() => { this.boundaryHintTimer = null }, 1500)
    },
    // 程序化切集（选集/自动连播）
    jumpTo(index) {
      if (index < 0 || index >= this.episodes.length || index === this.feedIndex) return
      if (this.progressSeconds > 0) this.saveHistory()
      this.feedIndex = index
      this.resetProgressState()
      // 切集后评论按集独立，重置评论缓存
      this.commentsDramaId = null
      this.comments = []
      this.commentsTotal = 0
      this.commentCount = 0
      this.cancelReply()
      if (this.showComments) this.loadComments()
      this.$nextTick(() => {
        this.playActiveEpisode()
        this.sendOverlayUpdate()
      })
    },
    selectEpisode(item) {
      this.showSheet = false
      const index = this.episodes.findIndex(ep => String(ep.id) === String(item.id))
      if (index >= 0) this.jumpTo(index)
    },
    async switchDrama(id) {
      if (!id || (this.drama && String(this.drama.id) === String(id))) return
      this.showSheet = false
      this.dramaId = id
      this.episodeId = null
      this.startEpisodeNo = null
      // 切剧后重置评论
      this.commentsDramaId = null
      this.comments = []
      this.commentsTotal = 0
      this.commentCount = 0
      this.cancelReply()
      await this.load()
      if (this.showComments) this.loadComments()
    },
    resetProgressState() {
      this.progressSeconds = 0
      this.progressPercent = 0
      this.videoDuration = 0
      this.lastSavedSecond = -1
      this.lastRewardedMinute = 0
      this.videoLoadError = ''
      this.playRetryCount = 0
      this.feedPaused = false
    },
    async playActiveEpisode() {
      const item = this.activeEpisode
      if (!item || !item.videoUrl) return
      const episodeId = item.id
      this.videoLoadError = ''
      // 已有预取地址则直接复用
      if (item.playbackUrl) {
        this.startPlayback(item)
        this.prefetchNext()
        return
      }
      try {
        const source = await resolveEpisodeSource(item.id, item.videoUrl, api)
        if (!this.activeEpisode || this.activeEpisode.id !== episodeId) return
        this.playRetryCount = 0
        this.$set(this.activeEpisode, 'playbackUrl', source)
        this.startPlayback(item)
        this.prefetchNext()
      } catch (err) {
        if (this.activeEpisode && this.activeEpisode.id === episodeId) {
          this.videoLoadError = err.message || this.t('videoLoadFailed')
        }
      }
    },
    startPlayback(item) {
      this.$nextTick(() => {
        // #ifdef H5
        // 清理旧实例
        if (this._hlsHandle) {
          try { this._hlsHandle.destroy() } catch (_) {}
          this._hlsHandle = null
        }
        // 等 video DOM 出现
        const attach = (retries) => {
          let videoEl = document.getElementById('mainVideo')
          if (videoEl && videoEl.tagName !== 'VIDEO') {
            videoEl = videoEl.querySelector('video')
          }
          if (!videoEl && retries > 0) {
            setTimeout(() => attach(retries - 1), 150)
            return
          }
          if (!videoEl) {
            uni.createVideoContext('mainVideo', this).play()
            return
          }
          this._hlsHandle = setupVideo(videoEl, item.playbackUrl || '', true)
        }
        attach(10)
        // #endif
        // #ifndef H5
        uni.createVideoContext('mainVideo', this).play()
        // #endif
      })
    },
    // 预加载下一集播放地址，切换时无缝续播
    prefetchNext() {
      const next = this.episodes[this.feedIndex + 1]
      if (!next || !next.videoUrl || next.playbackUrl) return
      resolveEpisodeSource(next.id, next.videoUrl, api).then(source => {
        if (next.videoUrl) this.$set(next, 'playbackUrl', source)
      }).catch(() => {})
    },
    retryCurrent() {
      const item = this.activeEpisode
      if (!item) return
      this.videoLoadError = ''
      this.playRetryCount = 0
      this.$set(item, 'playbackUrl', '')
      this.playActiveEpisode()
    },
    videoError(e) {
      if (!this.activeEpisode) return
      this.videoLoadError = this.t('videoLoadFailed')
      this.$set(this.activeEpisode, 'playbackUrl', '')
    },
    async unlockEpisode() {
      if (!this.ensureLogin() || !this.activeEpisode) return
      try {
        await api.unlock(this.activeEpisode.id)
        uni.showToast({ title: this.t('unlockedToast'), icon: 'none' })
        notifyDataChanged(APP_DATA_EVENTS.points)
        await this.reloadEpisodes()
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
        await this.reloadEpisodes()
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
        this.offerRecharge(err.message)
      }
    },
    // 解锁后仅刷新集数数据，保持当前播放位置
    async reloadEpisodes() {
      try {
        const drama = await api.drama(this.dramaId)
        this.drama = drama
        this.favorite = this.toBool(drama.favorite)
        this.like = this.toBool(drama.liked)
        this.likeCount = Number(drama.like_count || drama.likeCount || 0)
        const currentId = this.activeId
        this.episodes = (drama.episodes || []).map((item, index) => this.normalizeEpisode(item, index))
        const targetIndex = this.episodes.findIndex(item => String(item.id) === String(currentId))
        this.feedIndex = targetIndex >= 0 ? targetIndex : 0
        this.$nextTick(() => this.playActiveEpisode())
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
      }
    },
    timeupdate(e) {
      if (this.draggingProgress) return
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
      // 每次 timeupdate 都同步进度给 nvue overlay（约 250ms 一次）
      this.sendOverlayUpdate()
    },
    // document 捕获阶段的进度条拖动：彻底绕过 swiper 的事件拦截
    onDocPointerDown(e) {
      const bar = e.target && e.target.closest && e.target.closest('.video-progress-bar')
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
        const ctx = uni.createVideoContext('mainVideo', this)
        if (ctx && ctx.seek) ctx.seek(seconds)
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
        const ctx = uni.createVideoContext('mainVideo', this)
        if (ctx && ctx.seek) ctx.seek(seconds)
      }
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
      if (this.feedIndex < this.episodes.length - 1) {
        // 本剧还有下一集 → 播下一集
        this.jumpTo(this.feedIndex + 1)
      } else if (this.seriesDramas && this.seriesDramas.length > 0) {
        // 本剧最后一集播完 → 跳到下一部剧的第一集
        const nextDrama = this.seriesDramas[0]
        this.switchDrama(nextDrama.id)
      }
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
        this._showPlayHint('pause')
      } else {
        context.pause()
        this.feedPaused = true
        this._showPlayHint('play')
      }
    },
    toggleFullScreen() {
      const ctx = uni.createVideoContext('mainVideo', this)
      if (this.isFullScreen) {
        ctx.exitFullScreen()
      } else {
        ctx.requestFullScreen()
      }
    },
    onFullScreenChange(e) {
      this.isFullScreen = !!(e.detail && e.detail.fullScreen)
    },
    onVideoLoadedMetadata(e) {
      const w = e.detail && e.detail.width
      const h = e.detail && e.detail.height
      if (w && h) {
        this.isLandscapeVideo = w > h
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
    openSheet() {
      this.showSheet = true
    },
    async toggleFavorite() {
      if (!this.ensureLogin()) return
      try {
        const res = await api.toggleFavorite(this.dramaId)
        this.favorite = this.toBool(res.favorite)
        notifyDataChanged(APP_DATA_EVENTS.favorite, { dramaId: this.dramaId, favorite: this.favorite })
        this.sendOverlayUpdate()
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
      }
    },
    toggleLike() {
      if (!this.ensureLogin()) return
      if (!this.drama) return
      api.toggleLike(this.drama.id).then(res => {
        this.like = this.toBool(res.liked)
        this.likeCount = Number(res.likeCount || 0)
        this.sendOverlayUpdate()
      }).catch(err => {
        uni.showToast({ title: err.message, icon: 'none' })
      })
    },
    openComments() {
      this.showComments = true
      this.fetchMyUserId()
      this.loadComments()
    },
    loadComments() {
      if (!this.drama) return
      const dramaId = String(this.drama.id)
      const episodeId = this.activeEpisode ? this.activeEpisode.id : null
      // 缓存键含 episodeId，同一部剧不同集评论独立
      const cacheKey = dramaId + '|' + (episodeId || '')
      if (String(this.commentsDramaId || '') === cacheKey && this.comments.length > 0) return
      this.commentsDramaId = cacheKey
      this.commentsLoading = true
      api.dramaComments(this.drama.id, episodeId).then(data => {
        this.comments = (data && data.records) || []
        this.commentsTotal = Number((data && data.total) || 0)
        this.commentCount = this.commentsTotal
      }).catch(err => {
        uni.showToast({ title: err.message, icon: 'none' })
      }).finally(() => {
        this.commentsLoading = false
      })
    },
    // 跨页评论变更：首页发/删评论后，播放页同步刷新
    handleCommentChanged(payload) {
      if (!payload || !this.drama) return
      const myDrama = String(this.drama.id)
      const myEp = this.activeEpisode ? String(this.activeEpisode.id) : ''
      if (String(payload.dramaId) !== myDrama) return
      if (payload.episodeId && myEp && String(payload.episodeId) !== myEp) return
      this.commentsDramaId = null
      this.commentsTotal = 0
      this.commentCount = 0
      if (this.showComments) this.loadComments()
    },
    fetchMyUserId() {
      if (!uni.getStorageSync('token') || this.myUserId) return
      api.me().then(me => {
        this.myUserId = me && me.id ? me.id : null
      }).catch(() => {})
    },
    closeComments() {
      this.showComments = false
      this.blurCommentInput()
      this.cancelReply()
    },
    onCommentFocus() {
      // 评论输入框获焦 → 暂停视频
      try { uni.createVideoContext('mainVideo', this).pause() } catch (_) {}
      this.feedPaused = true
    },
    onCommentTouchMove() {
      // 阻止评论区的触摸事件冒泡到 swiper，防止误切剧
    },
    onCommentBlur() {
      // 评论输入框失焦 → 恢复播放
      try { uni.createVideoContext('mainVideo', this).play() } catch (_) {}
      this.feedPaused = false
    },
    blurCommentInput() {
      // #ifdef APP-PLUS
      uni.hideKeyboard()
      // #endif
    },
    async submitComment() {
      const text = (this.commentText || '').trim()
      if (!text || this.commentSubmitting) return
      if (!this.ensureLogin()) return
      if (!this.drama) return
      // 确保 myUserId 已获取（openComments 里是异步触发没 await，这里兜底）
      await this.fetchMyUserId()
      const dramaId = this.drama.id
      const episodeId = this.activeEpisode ? this.activeEpisode.id : null
      this.commentSubmitting = true
      try {
        if (this.replyingTo) {
          // 回复
          const parentId = this.replyingTo.id
          const replyToUserId = this.replyingTo.user_id
          const parentOfParent = this.replyingToRoot || this.replyingTo
          const rootId = parentOfParent.id
          const reply = await api.replyComment(dramaId, episodeId, parentId, replyToUserId, text)
          // 插入到根评论的 children
          const root = this.comments.find(c => String(c.id) === String(rootId))
          if (root) {
            if (!root.children) root.children = []
            root.children.push({ ...reply, reply_to_nickname: this.replyingTo.nickname || '匿名' })
            root.replies_total = (root.replies_total || 0) + 1
          }
          this.cancelReply()
        } else {
          // 根评论
          const comment = await api.addComment(dramaId, text, episodeId)
          this.comments.unshift({ ...comment, children: [], replies_total: 0 })
          this.commentsTotal += 1
          this.commentCount = this.commentsTotal
        }
        // 通知其他页面（首页）评论已变更，需重新拉取
        uni.$emit('commentChanged', { dramaId, episodeId })
        this.commentText = ''
        uni.showToast({ title: this.t('commentAdded'), icon: 'none' })
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
      } finally {
        this.commentSubmitting = false
      }
    },
    startReply(target, rootParent) {
      // target = 被回复的评论/回复；rootParent = 其根评论（如果 target 本身就是根则为 null）
      this.replyingTo = target
      this.replyingToNick = target.nickname || '匿名'
      this.replyingToRoot = rootParent || target
      this.$nextTick(() => {
        try {
          uni.createVideoContext('mainVideo', this).pause()
          this.feedPaused = true
        } catch (_) {}
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
        uni.showToast({ title: err.message, icon: 'none' })
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
            uni.$emit('commentChanged', { dramaId: this.drama.id, episodeId: this.activeEpisode ? this.activeEpisode.id : null })
            uni.showToast({ title: this.t('commentDeleted'), icon: 'none' })
          } catch (err) {
            uni.showToast({ title: err.message, icon: 'none' })
          }
        }
      })
    },
    // 评论内容截断：超过 20 字显示"展开/收起"（与首页一致）
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
      // 上一页就是详情时直接返回，避免 播放↔详情 反复进出导致页面栈堆积
      const pages = getCurrentPages()
      const prev = pages && pages.length >= 2 ? pages[pages.length - 2] : null
      if (prev && String(prev.route || '').indexOf('detail') !== -1) {
        uni.navigateBack()
        return
      }
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
      this.persistFeedResume()
      const pages = getCurrentPages()
      // 无上级页面（如 H5 直接刷新进入播放页）：回首页
      if (!pages || pages.length <= 1) {
        uni.reLaunch({ url: '/pages/index/index' })
        return
      }
      // 栈顶往下连续的播放页一次性退出（播放↔详情反复进出会堆栈，一次 back 即可离开）
      let delta = 1
      for (let i = pages.length - 2; i >= 0; i--) {
        const route = String(pages[i].route || '')
        if (route.indexOf('player') !== -1) delta++
        else break
      }
      uni.navigateBack({ delta: Math.min(delta, pages.length - 1) })
    },
    persistFeedResume() {
      if (!this.dramaId || !this.activeEpisode) return
      try {
        uni.setStorageSync('feed:resume', {
          dramaId: String(this.dramaId),
          episodeId: String(this.activeEpisode.id),
          episodeNo: this.activeEpisode.episodeNo || 0,
          progressSeconds: this.progressSeconds || 0,
          savedAt: Date.now()
        })
      } catch (_) {}
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
        // 横竖屏切换后若视频在播放则恢复播放
        if (!this.feedPaused) {
          try { uni.createVideoContext('mainVideo', this).play() } catch (_) {}
        }
      })
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
  height: 100%;
  background: #050609;
  color: #fff;
  font-family: -apple-system, BlinkMacSystemFont, "Inter", "SF Pro Display", "Segoe UI", sans-serif;
  overflow: hidden;
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

.feed-swiper,
.screen {
  width: 100%;
}

.screen {
  position: relative;
  background: #000;
}

.video,
.locked-screen,
.locked-cover {
  width: 100%;
  height: 100%;
}

.video {
  object-fit: contain;
  object-position: center 42%;
}

.video-tap-area {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 200rpx;
  width: auto;
  height: auto;
}
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

/* H5/小程序端进度条：位于合集选集入口下方，时间 | 轨道 | 时间 */
.video-progress-bar {
  display: flex;
  align-items: center;
  width: 100%;
  height: 56rpx;
  margin-top: 12rpx;
  pointer-events: auto;
}
.video-progress-time {
  flex-shrink: 0;
  width: 84rpx;
  font-size: 22rpx;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.9);
  white-space: nowrap;
}
.video-progress-time-right {
  text-align: right;
}
.video-progress-track {
  position: relative;
  flex: 1;
  height: 56rpx;
  margin: 0 12rpx;
  touch-action: none;
}
.video-progress-track::after {
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
.video-progress-fill {
  position: absolute;
  left: 0;
  top: 50%;
  margin-top: -4rpx;
  height: 8rpx;
  background-color: #ffffff;
  border-radius: 4rpx;
  z-index: 1;
}
.video-progress-thumb {
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

.landscape-pill {
  position: fixed;
  top: calc(12rpx + env(safe-area-inset-top));
  right: 120rpx;
  z-index: 18;
  display: inline-flex;
  align-items: center;
  padding: 10rpx 22rpx;
  font-size: 26rpx;
  font-weight: 800;
  color: #fff;
  background: rgba(255, 255, 255, 0.22);
  border-radius: 999rpx;
  backdrop-filter: blur(10rpx);
  transition: transform 0.18s ease;
}

.landscape-pill:active {
  transform: scale(0.95);
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

.back-btn {
  position: fixed;
  top: calc(8rpx + env(safe-area-inset-top));
  left: 10rpx;
  z-index: 20;
  width: 88rpx;
  height: 88rpx;
  line-height: 80rpx;
  text-align: center;
  font-size: 48rpx;
  font-weight: 300;
  color: rgba(255, 255, 255, 0.85);
  background: rgba(0, 0, 0, 0.28);
  border-radius: 50%;
  text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.5);
  transition: transform 0.18s ease;
}

.back-btn:active {
  transform: scale(0.9);
  color: #fff;
}

/* 右侧操作栏：绝对定位到视频画面，跟首页一致 */
.side-actions {
  position: absolute;
  right: 16rpx;
  top: 42%;
  z-index: 15;
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* 全屏按钮：药丸样式，与评论按钮同高 */
.fullscreen-pill {
  position: absolute;
  left: 50%;
  top: 42%;
  transform: translate(-50%, 264rpx);
  z-index: 15;
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 28rpx;
  background: rgba(0, 0, 0, 0.6);
  border-radius: 999rpx;
  pointer-events: auto;
  transition: transform 0.18s ease;
  backdrop-filter: blur(8rpx);
}

.fullscreen-pill:active {
  transform: translate(-50%, 264rpx) scale(0.92);
}

.fullscreen-pill-icon {
  font-size: 32rpx;
  color: #ffffff;
  line-height: 1;
}

.fullscreen-pill-text {
  font-size: 26rpx;
  font-weight: 700;
  color: #ffffff;
  white-space: nowrap;
}

.side-actions.is-landscape {
  right: auto;
  left: 20rpx;
  bottom: auto;
  top: 50%;
  transform: translateY(-50%);
  flex-direction: row;
  flex-wrap: wrap;
  width: 120rpx;
}

.side-actions.is-landscape .action {
  margin-bottom: 18rpx;
}

.side-actions.is-landscape .action text {
  font-size: 18rpx;
}

.action {
  width: 104rpx;
  margin-bottom: 26rpx;
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
  font-size: 26rpx;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.9);
  text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.7);
}

.action u-icon {
  text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.6);
}

.bottom-info {
  position: absolute;
  left: 0;
  right: 140rpx;
  bottom: 60rpx;
  z-index: 14;
  padding: 0 24rpx;
  pointer-events: none;
  touch-action: none;
}

.bottom-info .episode-pill,
.bottom-info .video-progress-bar {
  pointer-events: auto;
}

.info-title {
  font-size: 34rpx;
  font-weight: 700;
  color: #fff;
  text-shadow: 0 2rpx 10rpx rgba(0, 0, 0, 0.8);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.info-episode-no {
  font-size: 26rpx;
  font-weight: 600;
  color: rgba(247, 198, 106, 0.9);
  margin-left: 4rpx;
}

.info-desc {
  flex: 1;
  margin-top: 6rpx;
  font-size: 26rpx;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.85);
  text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.7);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.info-desc.expanded {
  -webkit-line-clamp: unset;
  display: block;
  overflow: visible;
  white-space: normal;
}
.desc-row {
  display: flex;
  align-items: flex-end;
}
.desc-more-btn {
  flex-shrink: 0;
  margin-left: 8rpx;
  font-size: 24rpx;
  color: #f7c66a;
  text-decoration: underline;
  white-space: nowrap;
}

.episode-pill {
  display: inline-flex;
  align-items: center;
  margin-top: 12rpx;
  padding: 10rpx 20rpx;
  font-size: 22rpx;
  font-weight: 800;
  color: #fff;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 999rpx;
  backdrop-filter: blur(10rpx);
  pointer-events: auto;
  transition: transform 0.18s ease;
}

.episode-pill:active {
  transform: scale(0.95);
}

.info-author {
  margin-bottom: 4rpx;
  font-size: 24rpx;
  font-weight: 700;
  color: #f7c66a;
  text-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.7);
}

.player-wrap {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background-color: #000;
}

.comments-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background-color: #0b0b10;
  border-top: 1rpx solid rgba(255, 255, 255, 0.06);
}

.comments-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 24rpx 10rpx;
  flex-shrink: 0;
}

.comments-panel-title {
  font-size: 28rpx;
  font-weight: 800;
  color: #fff;
}

.comments-panel-actions {
  display: flex;
  align-items: center;
  gap: 24rpx;
  flex-shrink: 0;
}

.comments-panel-more {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.6);
}

.comments-total-num {
  margin-left: 10rpx;
  font-size: 22rpx;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.55);
}

.comments-panel-close {
  font-size: 34rpx;
  line-height: 1;
  color: rgba(255, 255, 255, 0.6);
  padding: 4rpx 10rpx;
}

.comments-panel-close:active {
  color: #f7c66a;
}

.comments-scroll {
  flex: 1;
  min-height: 0;
  padding: 0 24rpx;
}

.comments-empty {
  padding: 40rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.45);
}

.comments-empty-icon {
  font-size: 48rpx;
  margin-bottom: 10rpx;
}

.comment-item {
  display: flex;
  align-items: flex-start;
  padding: 16rpx 0;
}

.comment-avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.12);
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
  font-size: 22rpx;
  font-weight: 700;
  color: rgba(247, 198, 106, 0.9);
}

.comment-text {
  margin-top: 4rpx;
  font-size: 26rpx;
  color: #fff;
  line-height: 1.5;
  word-break: break-all;
}

.comment-del {
  flex-shrink: 0;
  margin-left: 12rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.45);
}

/* 长评论展开/收起 */
.comment-expand {
  display: inline-block;
  margin-left: 8rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.55);
}

/* 评论元信息行 */
.comment-meta {
  display: flex;
  align-items: center;
  margin-top: 8rpx;
}
.comment-time {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.4);
}
.comment-reply-btn {
  flex-shrink: 0;
  margin-left: 18rpx;
  font-size: 22rpx;
  color: #5b7fff;
}
.comment-reply-btn:active {
  color: rgba(255, 255, 255, 0.9);
}

/* 嵌套回复（与首页 index.vue 完全一致） */
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

/* 回复占位条（与首页一致） */
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

.comments-input-row {
  position: relative;
  display: flex;
  align-items: center;
  padding: 12rpx 24rpx calc(12rpx + env(safe-area-inset-bottom));
  flex-shrink: 0;
  background: #111118;
  border-top: 1rpx solid rgba(255, 255, 255, 0.06);
  gap: 14rpx;
}

.comments-input {
  flex: 1;
  height: 64rpx;
  padding: 0 24rpx;
  font-size: 26rpx;
  color: #fff;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 999rpx;
}

.comments-send {
  width: 140rpx;
  height: 64rpx;
  line-height: 64rpx;
  margin-left: 0;
  font-size: 26rpx;
  font-weight: 800;
  color: #11100d;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  border-radius: 999rpx;
  padding: 0;
}

.comments-send[disabled] {
  opacity: 0.4;
  color: #11100d;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
}
</style>
