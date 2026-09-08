<template>
  <view class="page mine-subpage">
    <view class="page-head">
      <view class="page-title">{{ t('feedback') }}</view>
    </view>

    <view class="tabs">
      <view class="tab" :class="{ active: mode === 'list' }" @click="mode = 'list'">
        {{ t('feedbackList') }}
      </view>
      <view class="tab" :class="{ active: mode === 'submit' }" @click="switchSubmit">
        {{ t('feedbackSubmit') }}
      </view>
    </view>

    <view v-if="mode === 'submit'" class="submit-form">
      <view class="form-item">
        <view class="form-label">{{ t('feedbackType') }}</view>
        <picker :range="typeOptions" :value="typeIndex" @change="onTypeChange">
          <view class="picker-value">{{ typeOptions[typeIndex] }}</view>
        </picker>
      </view>
      <view class="form-item">
        <view class="form-label">{{ t('feedbackTitle') }}</view>
        <input class="form-input" v-model="form.title" :placeholder="t('feedbackTitle')" />
      </view>
      <view class="form-item">
        <view class="form-label">{{ t('feedbackContent') }}</view>
        <textarea class="form-textarea" v-model="form.content" :placeholder="t('feedbackPlaceholder')" />
      </view>
      <button class="submit-btn" :disabled="submitting" @click="submit">
        {{ t('feedbackSubmitBtn') }}
      </button>
    </view>

    <view v-else-if="mode === 'list'" class="feedback-list">
      <view v-if="loading" class="loading">
        <text>{{ t('loading') }}...</text>
      </view>
      <view v-else-if="!list.length" class="empty">
        <text class="empty-icon">📝</text>
        <text class="empty-text">{{ t('feedbackEmpty') }}</text>
      </view>
      <view v-else>
        <view v-for="item in list" :key="item.id" class="feedback-card" @click="openDetail(item)">
          <view class="card-header">
            <view class="type-tag">{{ feedbackTypeLabel(item.type) }}</view>
            <view class="status-tag" :class="statusClass(item.status)">
              {{ feedbackStatusLabel(item.status) }}
            </view>
          </view>
          <view class="card-title">{{ item.title }}</view>
          <view class="card-content">{{ truncate(item.content, 100) }}</view>
          <view v-if="item.adminReply" class="reply-preview">
            <text class="reply-label">{{ t('feedbackReply') }}：</text>
            <text class="reply-text">{{ truncate(item.adminReply, 60) }}</text>
          </view>
          <view class="card-footer">
            <text class="time">{{ formatTime(item.createdAt || item.created_at) }}</text>
            <text class="delete-btn" @click.stop="remove(item)">{{ t('delete') }}</text>
          </view>
        </view>
      </view>
    </view>

    <view v-if="detail" class="detail-overlay" @click="closeDetail">
      <view class="detail-content" @click.stop>
        <view class="detail-header">
          <text class="detail-title">{{ detail.title }}</text>
          <text class="close-btn" @click="closeDetail">✕</text>
        </view>
        <view class="detail-meta">
          <view class="type-tag">{{ feedbackTypeLabel(detail.type) }}</view>
          <view class="status-tag" :class="statusClass(detail.status)">
            {{ feedbackStatusLabel(detail.status) }}
          </view>
          <text class="time">{{ formatTime(detail.createdAt || detail.created_at) }}</text>
        </view>
        <view class="detail-section">
          <view class="section-label">{{ t('feedbackContent') }}</view>
          <view class="section-body">{{ detail.content }}</view>
        </view>
        <view v-if="detail.adminReply" class="detail-section">
          <view class="section-label">{{ t('feedbackReply') }}</view>
          <view class="section-body reply-body">{{ detail.adminReply }}</view>
        </view>
      </view>
      <view class="detail-mask" @click="closeDetail"></view>
    </view>
    <app-tab-bar />
  </view>
</template>

<script>
import api from '../../utils/api.js'
import { getLocale, t as translate } from '../../utils/i18n.js'

export default {
  data() {
    return {
      mode: 'list',
      list: [],
      loading: false,
      submitting: false,
      form: { type: 'BUG', title: '', content: '' },
      typeOptions: [],
      typeValues: ['BUG', 'SUGGESTION', 'CONSULTATION', 'COMPLAINT', 'OTHER'],
      typeIndex: 0,
      detail: null,
      locale: getLocale()
    }
  },
  onShow() {
    this.locale = getLocale()
    this.typeOptions = [
      this.t('feedbackBug'),
      this.t('feedbackSuggestion'),
      this.t('feedbackConsultation'),
      this.t('feedbackComplaint'),
      this.t('feedbackOther')
    ]
    uni.setNavigationBarTitle({ title: this.t('feedback') })
    this.loadList()
  },
  methods: {
    switchSubmit() {
      if (!uni.getStorageSync('token')) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }
      this.mode = 'submit'
    },
    async loadList() {
      if (!uni.getStorageSync('token')) return
      this.loading = true
      try {
        this.list = await api.myFeedback()
      } catch (e) {
        this.list = []
      }
      this.loading = false
    },
    onTypeChange(e) {
      this.typeIndex = Number(e.detail.value)
      this.form.type = this.typeValues[this.typeIndex]
    },
    async submit() {
      if (!this.form.title || !this.form.content) {
        uni.showToast({ title: this.t('fillRequired'), icon: 'none' })
        return
      }
      this.submitting = true
      try {
        await api.submitFeedback({
          type: this.form.type,
          title: this.form.title,
          content: this.form.content
        })
        uni.showToast({ title: this.t('submitSuccess'), icon: 'none' })
        this.form = { type: this.form.type, title: '', content: '' }
        this.typeIndex = 0
        this.mode = 'list'
        this.loadList()
      } catch (err) {
        uni.showToast({ title: err.message, icon: 'none' })
      } finally {
        this.submitting = false
      }
    },
    openDetail(item) {
      this.detail = item
    },
    closeDetail() {
      this.detail = null
    },
    async remove(item) {
      uni.showModal({
        title: this.t('deleteConfirm'),
        success: async (res) => {
          if (res.confirm) {
            try {
              await api.deleteFeedback(item.id)
              this.list = this.list.filter(i => i.id !== item.id)
              uni.showToast({ title: this.t('historyDeleted'), icon: 'none' })
            } catch (err) {
              uni.showToast({ title: err.message, icon: 'none' })
            }
          }
        }
      })
    },
    feedbackTypeLabel(type) {
      const map = {
        BUG: this.t('feedbackBug'),
        SUGGESTION: this.t('feedbackSuggestion'),
        CONSULTATION: this.t('feedbackConsultation'),
        COMPLAINT: this.t('feedbackComplaint'),
        OTHER: this.t('feedbackOther')
      }
      return map[type] || type
    },
    feedbackStatusLabel(status) {
      const map = {
        PENDING: this.t('feedbackStatusPending'),
        REPLIED: this.t('feedbackStatusReplied'),
        RESOLVED: this.t('feedbackStatusResolved'),
        CLOSED: this.t('feedbackStatusClosed')
      }
      return map[status] || status
    },
    statusClass(status) {
      return 'status-' + (status || 'pending').toLowerCase()
    },
    truncate(text, len) {
      if (!text) return ''
      return text.length > len ? text.slice(0, len) + '...' : text
    },
    formatTime(str) {
      if (!str) return ''
      try {
        const d = new Date(str)
        return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
      } catch (e) {
        return str
      }
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
    repeating-linear-gradient(90deg, rgba(255, 255, 255, 0.035) 0 1rpx, transparent 1rpx 96rpx),
    linear-gradient(145deg, rgba(30, 116, 129, 0.2), transparent 38%),
    linear-gradient(225deg, rgba(123, 45, 56, 0.22), transparent 40%),
    #080a10;
  padding: 24rpx;
  box-sizing: border-box;
  color: #fff;
  font-family: -apple-system, BlinkMacSystemFont, "Inter", "SF Pro Display", "Segoe UI", sans-serif;
}

.page-head {
  padding: 18rpx 6rpx 30rpx;
}

.page-title {
  font-size: 34rpx;
  font-weight: 800;
  letter-spacing: -0.6rpx;
}

.tabs {
  display: flex;
  background:
    linear-gradient(160deg, rgba(255, 255, 255, 0.1), rgba(255, 255, 255, 0.04)),
    rgba(12, 15, 24, 0.78);
  border: 1rpx solid rgba(255, 255, 255, 0.14);
  border-radius: 999rpx;
  padding: 6rpx;
  margin-bottom: 32rpx;
  backdrop-filter: blur(14rpx);
  box-shadow: 0 12rpx 30rpx rgba(0, 0, 0, 0.25);
}

.tab {
  flex: 1;
  text-align: center;
  padding: 20rpx 0;
  font-size: 28rpx;
  font-weight: 700;
  letter-spacing: 0.3rpx;
  color: rgba(255, 255, 255, 0.55);
  border-radius: 999rpx;
  transition: all 0.25s ease;
}

.tab:active {
  transform: scale(0.97);
}

.tab.active {
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  color: #11100d;
  box-shadow:
    0 10rpx 24rpx rgba(247, 198, 106, 0.42),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
}

.submit-form {
  background:
    linear-gradient(160deg, rgba(255, 255, 255, 0.13), rgba(255, 255, 255, 0.052)),
    rgba(12, 15, 24, 0.78);
  border: 1rpx solid rgba(255, 255, 255, 0.15);
  border-radius: 24rpx;
  padding: 32rpx;
  box-shadow: 0 24rpx 70rpx rgba(0, 0, 0, 0.28);
}

.form-item {
  margin-bottom: 28rpx;
}

.form-label {
  font-size: 26rpx;
  font-weight: 700;
  letter-spacing: 0.3rpx;
  color: rgba(255, 255, 255, 0.62);
  margin-bottom: 16rpx;
}

.form-input {
  width: 100%;
  height: 88rpx;
  box-sizing: border-box;
  background: rgba(255, 255, 255, 0.08);
  border: 1rpx solid rgba(255, 255, 255, 0.14);
  border-radius: 18rpx;
  padding: 0 24rpx;
  font-size: 28rpx;
  color: #fff;
  transition: border-color 0.2s ease, background 0.2s ease;
}

.form-textarea {
  width: 100%;
  min-height: 148rpx;
  background: rgba(255, 255, 255, 0.08);
  border: 1rpx solid rgba(255, 255, 255, 0.14);
  border-radius: 18rpx;
  padding: 24rpx;
  font-size: 28rpx;
  color: #fff;
  box-sizing: border-box;
  line-height: 1.55;
}

.picker-value {
  width: 100%;
  box-sizing: border-box;
  background: rgba(255, 255, 255, 0.08);
  border: 1rpx solid rgba(255, 255, 255, 0.14);
  border-radius: 18rpx;
  padding: 24rpx;
  font-size: 28rpx;
  color: #fff;
}

.submit-btn {
  width: 100%;
  background: linear-gradient(135deg, #ffe0a1, #f3b84d);
  color: #11100d;
  border-radius: 999rpx;
  font-size: 30rpx;
  font-weight: 800;
  letter-spacing: 0.5rpx;
  padding: 24rpx;
  margin-top: 16rpx;
  box-shadow:
    0 16rpx 38rpx rgba(247, 198, 106, 0.4),
    inset 0 2rpx 0 rgba(255, 255, 255, 0.5);
  transition: transform 0.18s ease;
}

.submit-btn:active {
  transform: scale(0.97);
}

.submit-btn[disabled] {
  opacity: 0.6;
}

.feedback-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.loading, .empty {
  padding: 56rpx 0;
  text-align: center;
  color: rgba(255, 255, 255, 0.5);
}

.empty-icon {
  font-size: 44rpx;
  display: block;
  margin-bottom: 20rpx;
}

.feedback-card {
  background: linear-gradient(160deg, rgba(255, 255, 255, 0.13), rgba(255, 255, 255, 0.052)), rgba(12, 15, 24, 0.72);
  border: 1rpx solid rgba(255, 255, 255, 0.15);
  border-radius: 24rpx;
  padding: 28rpx 32rpx;
  box-shadow: 0 24rpx 70rpx rgba(0, 0, 0, 0.28);
  transition: transform 0.2s ease, border-color 0.2s ease;
}

.feedback-card:active {
  transform: scale(0.99);
  border-color: rgba(247, 198, 106, 0.4);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 14rpx;
}

.type-tag {
  font-size: 20rpx;
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(77, 208, 225, 0.2);
  color: #4dd0e1;
  font-weight: 700;
  letter-spacing: 0.3rpx;
}

.status-tag {
  font-size: 20rpx;
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
  font-weight: 700;
  letter-spacing: 0.3rpx;
}

.status-pending {
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.7);
}

.status-replied {
  background: rgba(77, 208, 225, 0.2);
  color: #4dd0e1;
}

.status-resolved {
  background: rgba(13, 143, 88, 0.2);
  color: #34d399;
}

.status-closed {
  background: rgba(255, 77, 79, 0.2);
  color: #ff6b6e;
}

.card-title {
  font-size: 30rpx;
  font-weight: 800;
  letter-spacing: -0.2rpx;
  margin-bottom: 10rpx;
}

.card-content {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.62);
  line-height: 1.55;
  margin-bottom: 16rpx;
}

.reply-preview {
  background: linear-gradient(135deg, rgba(77, 208, 225, 0.14), rgba(77, 208, 225, 0.06));
  border: 1rpx solid rgba(77, 208, 225, 0.22);
  border-radius: 14rpx;
  padding: 16rpx 20rpx;
  margin-bottom: 16rpx;
  font-size: 24rpx;
  line-height: 1.5;
}

.reply-label {
  color: #4dd0e1;
  font-weight: 700;
}

.reply-text {
  color: rgba(255, 255, 255, 0.74);
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.4);
}

.delete-btn {
  color: #ff6b6e;
  font-weight: 600;
}

.detail-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 999;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}

.detail-mask {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.66);
  backdrop-filter: blur(8rpx);
}

.detail-content {
  position: relative;
  background:
    linear-gradient(180deg, rgba(20, 24, 32, 0.98), rgba(10, 12, 18, 0.98));
  border-top: 1rpx solid rgba(247, 198, 106, 0.28);
  border-radius: 36rpx 36rpx 0 0;
  padding: 40rpx 32rpx calc(40rpx + env(safe-area-inset-bottom));
  max-height: 80vh;
  overflow-y: auto;
  box-shadow: 0 -20rpx 50rpx rgba(0, 0, 0, 0.5);
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20rpx;
}

.detail-title {
  font-size: 36rpx;
  font-weight: 800;
  letter-spacing: -0.4rpx;
  flex: 1;
  margin-right: 20rpx;
}

.close-btn {
  font-size: 36rpx;
  color: rgba(255, 255, 255, 0.5);
  padding: 10rpx 14rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 28rpx;
  flex-wrap: wrap;
}

.detail-meta .time {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.4);
}

.detail-section {
  margin-bottom: 28rpx;
}

.section-label {
  font-size: 22rpx;
  text-transform: uppercase;
  letter-spacing: 1.2rpx;
  color: rgba(247, 198, 106, 0.7);
  margin-bottom: 12rpx;
  font-weight: 700;
}

.section-body {
  font-size: 28rpx;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.85);
  white-space: pre-wrap;
}

.reply-body {
  background: linear-gradient(135deg, rgba(77, 208, 225, 0.14), rgba(77, 208, 225, 0.06));
  border: 1rpx solid rgba(77, 208, 225, 0.22);
  padding: 22rpx;
  border-radius: 18rpx;
  color: rgba(255, 255, 255, 0.88);
}
</style>
