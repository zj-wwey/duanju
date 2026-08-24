<template>
  <div class="stream-shell app-layout">
    <NavHeader
      :brand="t('brand')"
      :active-name="String(route.name || 'home')"
      :user-label="store.userLabel.value"
      :avatar-url="store.user.value?.avatar_url || store.user.value?.avatarUrl || ''"
      :points-text="t('nav.points', { points: formatNumber(store.points.value) })"
      :checking-in="checkingIn"
      :unread-count="unreadAnnouncements"
      @home="goHome"
      @category="goCategory"
      @profile="goProfile"
      @search="goCategory"
      @checkin="checkin"
      @logout="logout"
      @vip="goVip"
      @announcements="openAnnouncements"
      @feedback="openFeedbackDialog"
      @recharge="goRecharge"
    />

    <main class="layout-content">
      <RouterView v-slot="{ Component }">
        <Transition name="stream-fade" mode="out-in">
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>

    <el-dialog
      v-model="showAnnouncementDialog"
      :title="t('announcement.title')"
      width="640px"
      :close-on-click-modal="true"
      append-to-body
      class="announcement-dialog"
    >
      <div v-if="announcements.length" class="announcement-dialog-list">
        <div v-for="item in announcements" :key="item.id" class="announcement-dialog-item">
          <div class="announcement-dialog-header">
            <span class="announcement-type-tag" :class="item.type === 'SYSTEM' ? 'type-system' : 'type-activity'">
              {{ announcementTypeLabel(item.type) }}
            </span>
            <span class="announcement-time">{{ formatDate(item.created_at) }}</span>
          </div>
          <h4 class="announcement-dialog-title">{{ item.title }}</h4>
          <p class="announcement-dialog-summary">{{ item.content?.slice(0, 120) }}{{ item.content && item.content.length > 120 ? '...' : '' }}</p>
        </div>
      </div>
      <div v-else class="stream-empty">{{ t('announcement.empty') }}</div>
      <template #footer>
        <el-button @click="showAnnouncementDialog = false">{{ t('common.close') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="showFeedbackDialog"
      :title="t('feedback.title')"
      width="520px"
      :close-on-click-modal="true"
      append-to-body
      class="feedback-dialog"
    >
      <div class="feedback-dialog-form">
        <label class="stream-field">
          <span>{{ t('feedback.type') }}</span>
          <select v-model="feedbackForm.type" class="stream-select">
            <option value="BUG">{{ t('feedback.bug') }}</option>
            <option value="SUGGESTION">{{ t('feedback.suggestion') }}</option>
            <option value="CONSULTATION">{{ t('feedback.consultation') }}</option>
            <option value="COMPLAINT">{{ t('feedback.complaint') }}</option>
            <option value="OTHER">{{ t('feedback.other') }}</option>
          </select>
        </label>
        <label class="stream-field">
          <span><span class="required-mark">*</span>{{ t('feedback.inputTitle') }}</span>
          <input v-model.trim="feedbackForm.title" type="text" :placeholder="t('feedback.placeholderTitle')" />
        </label>
        <label class="stream-field">
          <span><span class="required-mark">*</span>{{ t('feedback.inputContent') }}</span>
          <textarea v-model.trim="feedbackForm.content" rows="5" :placeholder="t('feedback.placeholderContent')" />
        </label>
      </div>
      <template #footer>
        <el-button @click="showFeedbackDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="feedbackSubmitting" @click="submitFeedback">
          {{ feedbackSubmitting ? t('common.processing') : t('feedback.submit') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { inject, onMounted, reactive, ref } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { useStreamI18n } from '../locales/streamI18n.js'
import { ElMessage } from 'element-plus'
import NavHeader from '../components/drama/NavHeader.vue'
import { useDramaStore } from '../user/store.js'
import { api } from '../api.js'
import { formatNumber as formatNumberUtil, formatDate as formatDateUtil } from '../utils/helpers.js'

const route = useRoute()
const router = useRouter()
const store = useDramaStore()
const logoutUser = inject('logoutUser', null)
const checkingIn = ref(false)
const unreadAnnouncements = ref(0)
const showAnnouncementDialog = ref(false)
const showFeedbackDialog = ref(false)
const announcements = ref([])
const feedbackForm = reactive({ type: 'BUG', title: '', content: '' })
const feedbackSubmitting = ref(false)
const { t, locale } = useStreamI18n()

onMounted(async () => {
  try {
    await store.loadProfile()
    try {
      const res = await api.announcementUnreadCount()
      unreadAnnouncements.value = res?.count ?? 0
    } catch (e) { /* ignore */ }
  } catch (err) {
    ElMessage.error(err.message)
  }
})

function goHome() {
  router.push({ name: 'home' })
}

function goCategory(keyword) {
  const query = {}
  if (typeof keyword === 'string' && keyword.trim()) {
    query.keyword = keyword.trim()
  }
  router.push({ name: 'category', query })
}

function goProfile() {
  router.push({ name: 'profile' })
}

async function checkin() {
  if (checkingIn.value) return
  try {
    checkingIn.value = true
    await store.checkin()
    ElMessage.success(t('common.checkinSuccess'))
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    checkingIn.value = false
  }
}

function logout() {
  logoutUser?.()
}

function goVip() {
  router.push({ name: 'vip' })
}

function goRecharge() {
  router.push({ name: 'recharge' })
}

async function openAnnouncements() {
  try {
    announcements.value = await api.announcements()
    const res = await api.announcementUnreadCount()
    unreadAnnouncements.value = res?.count ?? 0
    showAnnouncementDialog.value = true
  } catch (err) {
    console.warn('加载公告失败:', err)
  }
}

function openFeedbackDialog() {
  feedbackForm.type = 'BUG'
  feedbackForm.title = ''
  feedbackForm.content = ''
  showFeedbackDialog.value = true
}

async function submitFeedback() {
  if (!feedbackForm.title.trim() || !feedbackForm.content.trim()) {
    ElMessage.warning(t('feedback.fillRequired'))
    return
  }
  try {
    feedbackSubmitting.value = true
    await api.submitFeedback({
      type: feedbackForm.type,
      title: feedbackForm.title.trim(),
      content: feedbackForm.content.trim()
    })
    ElMessage.success(t('feedback.submitSuccess'))
    showFeedbackDialog.value = false
    feedbackForm.title = ''
    feedbackForm.content = ''
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    feedbackSubmitting.value = false
  }
}

function announcementTypeLabel(type) {
  return type === 'SYSTEM' ? t('announcement.typeSystem') : t('announcement.typeActivity')
}

function formatDate(dateStr) {
  return formatDateUtil(dateStr, locale.value, true)
}

function formatNumber(value) {
  return formatNumberUtil(value, locale.value)
}
</script>
