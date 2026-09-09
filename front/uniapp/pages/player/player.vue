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
              :controls="false"
              :show-center-play-btn="false"
              :show-fullscreen-btn="false"
              :show-progress="false"
              :show-play-btn="false"
              object-fit="contain"
              @timeupdate="timeupdate"
              @ended="ended"
              @error="videoError"
              @play="onVideoPlay"
              @pause="onVideoPause"
            >
              <cover-view class="video-tap-area" @tap="togglePlayback"></cover-view>
              <!-- 播放/暂停反馈图标 -->
              <cover-view v-if="ep.id === activeId && playHint" class="video-play-hint">
                <cover-image
                  class="video-play-hint-img"
                  :src="playHint === 'pause' ? '/static/images/playhint/pause.png' : '/static/images/playhint/play.png'"
                />
              </cover-view>
              <!-- #ifndef APP-PLUS -->
              <cover-view
                class="video-progress-bar"
                @touchstart="onProgressTouchStart"
                @touchmove="onProgressTouchMove"
                @touchend="onProgressTouchEnd"
              >
                <cover-view class="video-progress-time">{{ formatCurrentTime }}</cover-view>
                <cover-view class="video-progress-track"></cover-view>
                <cover-view class="video-progress-fill" :style="{ width: progressPercent + '%' }"></cover-view>
                <cover-view class="video-progress-thumb" :style="{ left: progressPercent + '%' }"></cover-view>
                <cover-view class="video-progress-time video-progress-time-right">{{ formatTotalTime }}</cover-view>
              </cover-view>
              <!-- #endif -->
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
        </swiper-item>
      </swiper>

      <view v-else class="locked-screen">
        <image class="locked-cover" :src="coverOf(drama)" mode="aspectFill" />
      </view>

      <!-- #ifndef APP-PLUS -->
      <view class="back-btn" @click="back">←</view>

      <view class="side-actions" :class="{ 'is-landscape': isLandscape }">
        <view class="action" @click="toggleFavorite">
          <view class="action-icon-wrap is-favorite" :class="{ active: favorite }">
            <u-icon :name="favorite ? 'heart-fill' : 'heart'" size="42" color="#ffffff"></u-icon>
          </view>
          <text class="action-label">{{ favorite ? t('favorited') : t('favorite') }}</text>
        </view>
        <view class="action" @click="toggleLike">
          <view class="action-icon-wrap is-like" :class="{ active: like }">
            <u-icon :name="like ? 'thumb-up-fill' : 'thumb-up'" size="42" color="#ffffff"></u-icon>
          </view>
          <text class="action-label action-num">{{ formatCount(likeCount) }}</text>
        </view>
        <view class="action" @click="openComments">
          <view class="action-icon-wrap is-comment">
            <u-icon name="chat" size="42" color="#ffffff"></u-icon>
          </view>
          <text class="action-label action-num">{{ commentCount > 0 ? formatCount(commentCount) : t('comment') }}</text>
        </view>
        <view class="action" @click="share">
          <view class="action-icon-wrap is-share">
            <u-icon name="share-fill" size="42" color="#ffffff"></u-icon>
          </view>
          <text class="action-label">{{ t('share') }}</text>
        </view>
        <view class="action" @click="goDetail">
          <view class="action-icon-wrap is-detail">
            <u-icon name="list-dot" size="42" color="#ffffff"></u-icon>
          </view>
          <text class="action-label">{{ t('details') }}</text>
        </view>
      </view>

      <view class="bottom-info" v-if="!isLandscape">
        <view class="info-author" v-if="dramaAuthor">{{ dramaAuthor }}</view>
        <view class="info-title">{{ drama ? drama.title : '' }}</view>
        <view class="info-desc" v-if="drama && drama.description">{{ drama.description }}</view>
        <view class="episode-pill" @click.stop="openSheet">
          {{ t('collectionEntry', { num: activeEpisode ? activeEpisode.episodeNo : 1, total: episodes.length }) }}
        </view>
      </view>
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
          <text class="comments-panel-title">{{ t('commentTitle') }}<text v-if="commentsTotal > 0" class="comments-total-num">{{ commentsTotal }}</text></text>
          <text class="comments-panel-close" @click.stop="closeComments">⌄</text>
        </view>
        <scroll-view scroll-y class="comments-scroll" @touchmove.stop.prevent @click.stop="blurCommentInput">
          <view v-if="commentsLoading" class="comments-empty">{{ t('loading') }}</view>
          <view v-else-if="!comments.length" class="comments-empty">{{ t('commentEmpty') }}</view>
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

export default {
  data() {
    return {
      dramaId: null,
      episodeId: null,
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
      commentsDramaId: null,
      myUserId: null,
      replyingTo: null,
      replyingToNick: '',
      replyingToRoot: null,
      isDownloading: false,
      downloadProgress: 0,
      downloadedFilePath: '',
      isLandscape: false,
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
    this.load()
  },
  onShow() {
    console.log('[PLAYER] onShow called')
    this.locale = getLocale()
    this.loadPoints()
    uni.$on('playerOverlayEvent', this.handleOverlayEvent)
    this.showOverlay()
    this.updateOrientation()
    uni.onWindowResize(this.handleResize)
  },
  onHide() {
    console.log('[PLAYER] onHide called')
    this.persistFeedResume()
    uni.$off('playerOverlayEvent', this.handleOverlayEvent)
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
        const targetIndex = this.episodes.findIndex(item => String(item.id) === String(this.episodeId))
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
      const ov = uni.getSubNVueById('playerOverlay')
      console.log('[PLAYER] showOverlay, subNVue exists:', !!ov)
      if (ov) {
        ov.show()
        this.sendOverlayUpdate()
      } else {
        console.warn('[PLAYER] showOverlay FAILED: subNVue playerOverlay not found')
      }
    },
    hideOverlay() {
      const ov = uni.getSubNVueById('playerOverlay')
      console.log('[PLAYER] hideOverlay, subNVue exists:', !!ov)
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
        case 'episode': this.openSheet(); break
        case 'tap': this.togglePlayback(); break
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
        uni.createVideoContext('mainVideo', this).play()
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
    onProgressTouchStart(e) {
      this.draggingProgress = true
      this.seekByTouch(e)
    },
    onProgressTouchMove(e) {
      if (this.draggingProgress) this.seekByTouch(e)
    },
    onProgressTouchEnd() {
      this.draggingProgress = false
    },
    seekByTouch(e) {
      const touch = (e.touches && e.touches[0]) || (e.changedTouches && e.changedTouches[0])
      if (!touch) return
      const sys = uni.getSystemInfoSync()
      const pad = 130 * sys.windowWidth / 750
      const usable = sys.windowWidth - pad * 2
      const raw = touch.clientX - pad
      const percent = Math.max(0, Math.min(100, (raw / usable) * 100))
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
      if (this.feedIndex < this.episodes.length - 1) this.jumpTo(this.feedIndex + 1)
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

.video-tap-area {
  width: 100%;
  height: 100%;
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

/* cover-view 版进度条：贴底，左右时间，可拖动 */
.video-progress-bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: calc(68rpx + env(safe-area-inset-bottom));
  height: 80rpx;
  pointer-events: auto;
}
.video-progress-time {
  position: absolute;
  top: 16rpx;
  left: 24rpx;
  font-size: 22rpx;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.9);
  white-space: nowrap;
}
.video-progress-time-right {
  left: auto;
  right: 24rpx;
}
.video-progress-track {
  position: absolute;
  left: 130rpx;
  right: 130rpx;
  top: 28rpx;
  height: 4rpx;
  background-color: rgba(255, 255, 255, 0.3);
  border-radius: 2rpx;
}
.video-progress-fill {
  position: absolute;
  left: 130rpx;
  top: 26rpx;
  height: 8rpx;
  background-color: #ffffff;
  border-radius: 4rpx;
}
.video-progress-thumb {
  position: absolute;
  top: 18rpx;
  width: 24rpx;
  height: 24rpx;
  margin-left: -12rpx;
  border-radius: 12rpx;
  background-color: #ffffff;
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.5);
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

.side-actions {
  position: fixed;
  right: 16rpx;
  bottom: calc(220rpx + env(safe-area-inset-bottom));
  z-index: 15;
  display: flex;
  flex-direction: column;
  align-items: center;
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

.side-actions.is-landscape .action-label {
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

/* 彩色渐变图标底座：每个功能独立配色 */
.action-icon-wrap {
  width: 92rpx;
  height: 92rpx;
  border-radius: 30rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow:
    0 8rpx 20rpx rgba(0, 0, 0, 0.35),
    0 4rpx 14rpx rgba(0, 0, 0, 0.28),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.45),
    inset 0 -2rpx 6rpx rgba(0, 0, 0, 0.18);
  border: 1rpx solid rgba(255, 255, 255, 0.32);
}

.is-favorite {
  background: linear-gradient(135deg, #ff8aa8, #ff2d55);
  box-shadow:
    0 8rpx 22rpx rgba(255, 45, 85, 0.42),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.45),
    inset 0 -2rpx 6rpx rgba(0, 0, 0, 0.18);
}

.is-like {
  background: linear-gradient(135deg, #ffe3a6, #f0a832);
  box-shadow:
    0 8rpx 22rpx rgba(240, 168, 50, 0.45),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.55),
    inset 0 -2rpx 6rpx rgba(122, 74, 10, 0.3);
}

.is-comment {
  background: linear-gradient(135deg, #ab90ff, #7c5cff);
  box-shadow:
    0 8rpx 22rpx rgba(124, 92, 255, 0.42),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.45),
    inset 0 -2rpx 6rpx rgba(0, 0, 0, 0.18);
}

.is-share {
  background: linear-gradient(135deg, #5fe3da, #2ea8ff);
  box-shadow:
    0 8rpx 22rpx rgba(46, 168, 255, 0.42),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.45),
    inset 0 -2rpx 6rpx rgba(0, 0, 0, 0.18);
}

.is-detail {
  background: linear-gradient(135deg, #ffc06b, #ff8a3d);
  box-shadow:
    0 8rpx 22rpx rgba(255, 138, 61, 0.42),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.45),
    inset 0 -2rpx 6rpx rgba(0, 0, 0, 0.18);
}

/* 激活态（已点赞/已追剧）：渐变提亮 + 外发光增强 */
.is-favorite.active {
  background: linear-gradient(135deg, #ff5c7c, #e61742);
  box-shadow:
    0 8rpx 28rpx rgba(255, 45, 85, 0.65),
    0 0 14rpx rgba(255, 92, 124, 0.5),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
}

.is-like.active {
  background: linear-gradient(135deg, #ffd257, #f08c1a);
  box-shadow:
    0 8rpx 28rpx rgba(240, 140, 26, 0.65),
    0 0 14rpx rgba(255, 200, 80, 0.55),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.55);
}

.action-label {
  display: block;
  margin-top: 10rpx;
  font-size: 20rpx;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.92);
  text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.7);
}

.action-num {
  letter-spacing: 0.5rpx;
}

.bottom-info {
  position: fixed;
  left: 0;
  right: 140rpx;
  bottom: calc(158rpx + env(safe-area-inset-bottom));
  z-index: 14;
  padding: 0 24rpx;
  pointer-events: none;
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

.info-desc {
  margin-top: 6rpx;
  font-size: 26rpx;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.85);
  text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.7);
  overflow: hidden;
  text-overflow: ellipsis;
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
  background-color: #111;
  border-top: 1rpx solid rgba(255, 255, 255, 0.08);
}

.comments-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 24rpx 16rpx;
}

.comments-panel-title {
  font-size: 28rpx;
  font-weight: 800;
  color: #fff;
}

.comments-total-num {
  margin-left: 10rpx;
  font-size: 22rpx;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.55);
}

.comments-panel-close {
  font-size: 36rpx;
  line-height: 1;
  color: rgba(255, 255, 255, 0.6);
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
  padding: 60rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.5);
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
