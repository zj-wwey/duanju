<template>
  <section class="stream-page profile-page">
    <button class="back-link back-button" type="button" @click="router.back()">← {{ t('common.back') }}</button>

    <div class="profile-layout">
      <aside class="profile-side">
        <div class="profile-user-card">
          <div class="avatar-frame">
            <img v-if="avatarPreview" :src="avatarPreview" :alt="t('profile.avatarAlt')" />
            <span v-else>{{ avatarInitial }}</span>
          </div>
          <h2>{{ profileForm.nickname || store.userLabel.value }}</h2>
          <p>ID {{ store.user.value?.id || '-' }}</p>
          <SealTag active>{{ t('nav.points', { points: formatNumber(store.points.value) }) }}</SealTag>
        <div class="profile-quick-actions">
          <button class="quick-btn" @click="router.push({ name: 'recharge' })">
            <span class="quick-icon">💳</span>
            <span class="quick-label">{{ t('profile.rechargePoints') }}</span>
          </button>
          <button class="quick-btn" @click="router.push({ name: 'vip' })">
            <span class="quick-icon">👑</span>
            <span class="quick-label">{{ t('profile.openVip') }}</span>
          </button>
          <button class="quick-btn" @click="$emit('open-announcements')">
            <span class="quick-icon">📢</span>
            <span class="quick-label">{{ t('profile.viewAnnouncements') }}</span>
          </button>
        </div>
        </div>

        <nav class="profile-menu" :aria-label="t('profile.title')">
          <button
            v-for="item in menuItems"
            :key="item.key"
            :class="{ active: activePanel === item.key }"
            type="button"
            @click="activePanel = item.key"
          >
            <span>{{ item.index }}</span>
            {{ item.label }}
          </button>
        </nav>
      </aside>

      <section class="profile-content eastern-panel">
        <Transition name="stream-fade" mode="out-in">
          <div :key="activePanel" class="profile-panel">
            <template v-if="activePanel === 'profile'">
              <PanelTitle :title="t('profile.personal')" />
              <div class="profile-form-grid">
                <div class="avatar-editor">
                  <div class="avatar-preview">
                    <img v-if="avatarPreview" :src="avatarPreview" :alt="t('profile.avatarPreview')" />
                    <span v-else>{{ avatarInitial }}</span>
                  </div>
                  <input ref="avatarInput" type="file" accept="image/*" hidden @change="selectAvatar" />
                  <GoldButton variant="seal" compact @click="avatarInput?.click()">{{ t('profile.uploadAvatar') }}</GoldButton>
                </div>

                <div class="form-stack">
                  <label class="stream-field">
                    <span><span class="required-mark" aria-hidden="true">*</span>{{ t('profile.nickname') }}</span>
                    <input v-model.trim="profileForm.nickname" type="text" maxlength="24" :placeholder="t('profile.nicknamePlaceholder')" />
                  </label>
                  <label class="stream-field">
                    <span>{{ t('profile.username') }}</span>
                    <input :value="store.user.value?.username || '-'" type="text" disabled />
                  </label>
                  <label class="stream-field">
                    <span>{{ t('profile.phoneReserved') }}</span>
                    <input :value="store.user.value?.phone || '-'" type="text" disabled />
                  </label>
                  <label class="stream-field">
                    <span>{{ t('profile.accountId') }}</span>
                    <input :value="store.user.value?.id || '-'" type="text" disabled />
                  </label>
                  <GoldButton @click="saveProfile">{{ t('common.save') }}</GoldButton>
                </div>
              </div>
            </template>

            <template v-else-if="activePanel === 'password'">
              <PanelTitle :title="t('profile.password')" />
              <div class="form-stack narrow">
                <label class="stream-field">
                  <span><span class="required-mark" aria-hidden="true">*</span>{{ t('profile.oldPassword') }}</span>
                  <input v-model="passwordForm.oldPassword" type="password" :placeholder="t('profile.oldPasswordPlaceholder')" />
                </label>
                <label class="stream-field">
                  <span><span class="required-mark" aria-hidden="true">*</span>{{ t('profile.newPassword') }}</span>
                  <input v-model="passwordForm.newPassword" type="password" :placeholder="t('profile.newPasswordPlaceholder')" />
                </label>
                <label class="stream-field">
                  <span><span class="required-mark" aria-hidden="true">*</span>{{ t('profile.confirmPassword') }}</span>
                  <input v-model="passwordForm.confirmPassword" type="password" :placeholder="t('profile.confirmPasswordPlaceholder')" />
                </label>
                <div class="action-row">
                  <GoldButton @click="savePassword">{{ t('profile.confirmChange') }}</GoldButton>
                  <GoldButton variant="ghost" @click="resetPassword">{{ t('common.reset') }}</GoldButton>
                </div>
              </div>
            </template>

            <template v-else-if="activePanel === 'history'">
              <PanelTitle :title="t('profile.history')" />
              <div v-if="visibleHistories.length" class="record-list">
                <article v-for="item in visibleHistories" :key="`${field(item, 'drama_id')}-${field(item, 'episode_id')}`" class="history-row" @click="openHistory(item)">
                  <img :src="coverFromRow(item)" :alt="field(item, 'drama_title')" />
                  <div>
                    <h3>{{ field(item, 'drama_title') || t('common.shortDrama') }}</h3>
                    <p>{{ t('profile.lastWatched', { episode: field(item, 'episode_no') || '-', time: formatDate(field(item, 'updated_at')) }) }}</p>
                  </div>
                  <button type="button" @click.stop="removeLocalHistory(item)">{{ t('common.delete') }}</button>
                </article>
                <div class="action-row">
                  <GoldButton v-if="histories.length > visibleHistories.length" variant="ghost" compact @click="loadMore('history')">{{ t('common.loadMore') }}</GoldButton>
                  <GoldButton variant="seal" compact @click="clearLocalHistory">{{ t('common.clearAll') }}</GoldButton>
                </div>
              </div>
              <EmptyState v-else :text="t('profile.emptyHistory')" />
            </template>

            <template v-else-if="activePanel === 'favorites'">
              <PanelTitle :title="t('profile.favorites')" />
              <div v-if="visibleFavorites.length" class="poster-grid profile-grid">
                <div v-for="item in visibleFavorites" :key="item.id" class="favorite-card">
                  <DramaCard :drama="normalizeFavorite(item)" @select="openDetail" />
                  <GoldButton variant="seal" compact @click="cancelFavorite(item)">{{ t('profile.cancelFavorite') }}</GoldButton>
                </div>
                <GoldButton v-if="favorites.length > visibleFavorites.length" variant="ghost" compact @click="loadMore('favorites')">{{ t('common.loadMore') }}</GoldButton>
              </div>
              <EmptyState v-else :text="t('profile.emptyFavorites')" />
            </template>

            <template v-else-if="activePanel === 'orders'">
              <PanelTitle :title="t('profile.orders')" />
              <DataTable v-if="visibleOrders.length" :rows="visibleOrders" :columns="orderColumns" />
              <EmptyState v-else :text="t('profile.emptyOrders')" />
              <GoldButton v-if="orders.length > visibleOrders.length" variant="ghost" compact @click="loadMore('orders')">{{ t('common.loadMore') }}</GoldButton>
            </template>

            <template v-else-if="activePanel === 'recharge'">
              <PanelTitle :title="t('profile.pointRecords') || '积分记录'" />
              <DataTable v-if="visibleRechargeRows.length" :rows="visibleRechargeRows" :columns="rechargeColumns" />
              <EmptyState v-else :text="t('profile.emptyRecharge')" />
              <GoldButton v-if="rechargeRows.length > visibleRechargeRows.length" variant="ghost" compact @click="loadMore('recharge')">{{ t('common.loadMore') }}</GoldButton>
            </template>

            <template v-else-if="activePanel === 'settings'">
              <PanelTitle :title="t('profile.settings')" />
              <div class="settings-list">
                <label>
                  <span>{{ t('profile.notice') }}</span>
                  <input v-model="settings.notice" type="checkbox" @change="saveSettings" />
                </label>
                <label>
                  <span>{{ t('profile.autoNext') }}</span>
                  <input v-model="settings.autoNext" type="checkbox" @change="saveSettings" />
                </label>
              </div>
            </template>

            
          </div>
        </Transition>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useStreamI18n } from '../locales/streamI18n.js'
import { ElMessage } from 'element-plus'
import DramaCard from '../components/drama/DramaCard.vue'
import GoldButton from '../components/drama/GoldButton.vue'
import SealTag from '../components/drama/SealTag.vue'
import { api } from '../api.js'
import { useDramaStore } from '../user/store.js'
import { formatNumber as formatNumberUtil, formatMoney as formatMoneyUtil, formatDate as formatDateUtil, field as fieldUtil } from '../utils/helpers.js'

const router = useRouter()
const route = useRoute()
const { t, locale } = useStreamI18n()
const store = useDramaStore()
const activePanel = ref('profile')
const avatarInput = ref(null)
const avatarPreview = ref('')
const histories = ref([])
const favorites = ref([])
const orders = ref([])
const pointRecords = ref([])
const pointProducts = ref([])
const rechargeLoadingId = ref(null)
const historyLimit = ref(8)
const favoriteLimit = ref(12)
const orderLimit = ref(10)
const rechargeLimit = ref(10)
const profileForm = reactive({ nickname: '' })
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const settings = reactive({ notice: true, autoNext: true })
const announcements = ref([])
const announcementDetail = ref(null)
const vipStatus = ref(null)
const vipRecords = ref([])
const feedbackList = ref([])
const feedbackForm = reactive({ type: 'BUG', title: '', content: '' })
const feedbackLoading = ref(false)
const feedbackDetailData = ref(null)

const emit = defineEmits(['open-announcements', 'open-feedback'])

const menuItems = computed(() => [
  { key: 'profile', index: '01', label: t('profile.personal') },
  { key: 'password', index: '02', label: t('profile.password') },
  { key: 'history', index: '03', label: t('profile.history') },
  { key: 'favorites', index: '04', label: t('profile.favorites') },
  { key: 'orders', index: '05', label: t('profile.orders') },
  { key: 'settings', index: '06', label: t('profile.settings') }
])

const avatarInitial = computed(() => (profileForm.nickname || store.userLabel.value || t('brand')).slice(0, 1).toUpperCase())
const visibleHistories = computed(() => histories.value.slice(0, historyLimit.value))
const visibleFavorites = computed(() => favorites.value.slice(0, favoriteLimit.value))
const visibleOrders = computed(() => orders.value.slice(0, orderLimit.value))
const rechargeRows = computed(() => [
  ...orders.value.filter(row => field(row, 'order_type') === 'RECHARGE' || field(row, 'orderType') === 'RECHARGE'),
  ...pointRecords.value.filter(row => field(row, 'biz_type') === 'RECHARGE')
])
const visibleRechargeRows = computed(() => rechargeRows.value.slice(0, rechargeLimit.value))

const orderColumns = computed(() => [
  { key: 'order_no', label: t('profile.orderNo') },
  { key: 'product_name', label: t('profile.orderContent'), fallback: t('profile.pointOrder') },
  { key: 'points', label: t('profile.pointsAmount'), format: row => `${formatNumber(field(row, 'points'))}${t('common.points')} / ${formatMoney(field(row, 'amount_cents'), field(row, 'currency'))}` },
  { key: 'created_at', label: t('profile.orderTime'), format: row => formatDate(field(row, 'created_at')) },
  { key: 'status', label: t('common.status'), format: row => orderStatus(field(row, 'status')) }
])

const rechargeColumns = computed(() => [
  { key: 'created_at', label: t('profile.rechargeTime'), format: row => formatDate(field(row, 'created_at')) },
  { key: 'product_name', label: t('profile.rechargePlan'), fallback: t('profile.pointRecharge') },
  { key: 'amount_cents', label: t('profile.payAmount'), format: row => formatMoney(field(row, 'amount_cents'), field(row, 'currency')) },
  { key: 'points', label: t('profile.receivedPoints'), format: row => `${formatNumber(field(row, 'points') || Math.abs(Number(field(row, 'delta')) || 0))}${t('common.points')}` },
  { key: 'status', label: t('common.status'), format: row => orderStatus(field(row, 'status') || 'PAID') }
])

const vipPlans = computed(() => pointProducts.value.filter(p => field(p, 'product_category') === 'VIP' || field(p, 'productCategory') === 'VIP'))

const vipRecordColumns = computed(() => [
  { key: 'created_at', label: t('profile.orderTime'), format: row => formatDate(field(row, 'created_at')) },
  { key: 'product_name', label: t('profile.orderContent'), fallback: t('profile.vipRecords') },
  { key: 'status', label: t('common.status'), format: row => vipStatusLabel(field(row, 'status')) }
])

const PanelTitle = defineComponent({
  props: { title: String, description: String },
  setup(props) {
    return () => h('div', { class: 'panel-title-block' }, [
      h('p', { class: 'stream-kicker' }, t('profile.title')),
      h('h1', props.title),
      props.description ? h('p', { class: 'panel-title-desc' }, props.description) : null
    ])
  }
})

const EmptyState = defineComponent({
  props: { text: String },
  setup(props) {
    return () => h('div', { class: 'profile-empty' }, [
      h('span'),
      h('p', props.text)
    ])
  }
})

const DataTable = defineComponent({
  props: { rows: Array, columns: Array },
  setup(props) {
    return () => h('div', { class: 'profile-table' }, [
      h('div', { class: 'profile-table-head' }, props.columns.map(column => h('span', column.label))),
      ...props.rows.map(row => h('div', { class: 'profile-table-row' }, props.columns.map(column => {
        const value = column.format ? column.format(row) : field(row, column.key) || column.fallback || '-'
        return h('span', value)
      })))
    ])
  }
})

onMounted(async () => {
  // 支持从其他页面带 ?panel=orders 跳转过来自动切 tab
  const queryPanel = route.query.panel
  if (typeof queryPanel === 'string' && queryPanel.trim()) {
    activePanel.value = queryPanel
  }
  await loadProfileData()
})

async function loadProfileData() {
  try {
    const [profile, historyRows, favoriteRows, orderRows, pointsData, settingsData, productRows, announcementRows, vipStatusData, vipRecordRows, feedbackRows] = await Promise.all([
      store.loadProfile(),
      api.userHistory().catch(() => []),
      api.userFavorites().catch(() => []),
      api.userOrders({ limit: 100 }).catch(() => []),
      api.userPoints().catch(() => ({ records: [] })),
      api.userSettings().catch(() => null),
      api.pointProducts().catch(() => []),
      api.announcements().catch(() => []),
      api.userVipStatus().catch(() => null),
      api.userVipRecords({ limit: 100 }).catch(() => []),
      api.myFeedback({ limit: 100 }).catch(() => [])
    ])
    profileForm.nickname = profile?.user?.nickname || store.user.value?.nickname || ''
    avatarPreview.value = profile?.user?.avatar_url || profile?.user?.avatarUrl || ''
    histories.value = historyRows
    favorites.value = favoriteRows
    orders.value = orderRows
    pointRecords.value = pointsData.records || []
    pointProducts.value = productRows
    announcements.value = announcementRows
    vipStatus.value = vipStatusData
    vipRecords.value = vipRecordRows
    feedbackList.value = feedbackRows
    if (settingsData) {
      settings.notice = settingsData.notice !== false
      settings.autoNext = settingsData.autoNext !== false
    }
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function buyProduct(product) {
  if (rechargeLoadingId.value !== null) return
  rechargeLoadingId.value = product.id
  try {
    const order = await api.userCreateOrder({ productId: product.id, payChannel: 'STRIPE' })
    const orderNo = field(order, 'order_no', 'orderNo')
    if (!orderNo) throw new Error('Failed to create order')
    const checkout = await api.userStripeCheckout(orderNo)
    if (checkout?.session_url) {
      window.location.href = checkout.session_url
      return
    }
    throw new Error('Failed to get checkout URL')
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    rechargeLoadingId.value = null
  }
}

async function saveSettings() {
  try {
    const data = await api.userUpdateSettings({
      notice: Boolean(settings.notice),
      autoNext: Boolean(settings.autoNext)
    })
    settings.notice = data.notice !== false
    settings.autoNext = data.autoNext !== false
    ElMessage.success(t('profile.settingsSaved'))
  } catch (err) {
    ElMessage.error(err.message)
  }
}

function selectAvatar(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning(t('profile.selectImage'))
    return
  }
  const reader = new FileReader()
  reader.onload = () => cropSquarePreview(String(reader.result || ''))
  reader.readAsDataURL(file)
}

function cropSquarePreview(src) {
  const image = new Image()
  image.onload = () => {
    const size = Math.min(image.width, image.height)
    const canvas = document.createElement('canvas')
    canvas.width = 360
    canvas.height = 360
    const context = canvas.getContext('2d')
    context.drawImage(image, (image.width - size) / 2, (image.height - size) / 2, size, size, 0, 0, 360, 360)
    avatarPreview.value = canvas.toDataURL('image/jpeg', 0.88)
  }
  image.src = src
}

async function saveProfile() {
  if (!profileForm.nickname) {
    ElMessage.warning(t('profile.nicknameRequired'))
    return
  }
  try {
    const user = await api.userUpdateProfile({
      nickname: profileForm.nickname,
      avatarUrl: avatarPreview.value
    })
    store.user.value = user
    avatarPreview.value = user.avatar_url || user.avatarUrl || ''
    sessionStorage.setItem('user', JSON.stringify(user))
    ElMessage.success(t('profile.profileSaved'))
  } catch (err) {
    ElMessage.error(err.message)
  }
}

function savePassword() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword || !passwordForm.confirmPassword) {
    ElMessage.warning(t('profile.passwordRequired'))
    return
  }
  if (!/^(?=\S+$)(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]).{8,64}$/.test(passwordForm.newPassword)) {
    ElMessage.warning(t('profile.passwordLength'))
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning(t('profile.passwordMismatch'))
    return
  }
  savePasswordRequest()
}

async function savePasswordRequest() {
  try {
    await api.userChangePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    resetPassword()
    ElMessage.success(t('profile.passwordChanged'))
  } catch (err) {
    ElMessage.error(err.message)
  }
}

function resetPassword() {
  Object.assign(passwordForm, { oldPassword: '', newPassword: '', confirmPassword: '' })
}

function openHistory(row) {
  router.push({ name: 'player', params: { dramaId: field(row, 'drama_id'), episodeId: field(row, 'episode_id') } })
}

async function removeLocalHistory(row) {
  try {
    await api.userDeleteHistory(field(row, 'drama_id'))
    histories.value = histories.value.filter(item => item !== row)
    ElMessage.success(t('profile.localRemoved'))
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function clearLocalHistory() {
  try {
    await api.userClearHistory()
    histories.value = []
    ElMessage.success(t('profile.historyCleared'))
  } catch (err) {
    ElMessage.error(err.message)
  }
}

function openDetail(drama) {
  router.push({ name: 'detail', params: { id: drama.id } })
}

async function cancelFavorite(row) {
  try {
    await api.userToggleFavorite(row.id)
    favorites.value = favorites.value.filter(item => item.id !== row.id)
    ElMessage.success(t('profile.favoriteRemoved'))
  } catch (err) {
    ElMessage.error(err.message)
  }
}

function normalizeFavorite(row) {
  return {
    ...row,
    coverUrl: row.coverUrl || row.cover_url,
    title: row.title || row.drama_title,
    totalEpisodes: row.totalEpisodes || row.total_episodes
  }
}

function coverFromRow(row) {
  return row.coverUrl || row.cover_url || store.coverOf(row)
}

function loadMore(type) {
  if (type === 'history') historyLimit.value += 8
  if (type === 'favorites') favoriteLimit.value += 12
  if (type === 'orders') orderLimit.value += 10
  if (type === 'recharge') rechargeLimit.value += 10
}

function field(row, ...keys) {
  return fieldUtil(row, ...keys)
}

function formatNumber(value) {
  return formatNumberUtil(value, locale.value)
}

function formatMoney(cents, currency = 'USD') {
  return formatMoneyUtil(cents, currency, locale.value)
}

function formatDate(value) {
  return formatDateUtil(value, locale.value, true)
}

function truncate(text, len) {
  if (!text) return ''
  return text.length > len ? text.slice(0, len) + '...' : text
}

function orderStatus(status) {
  return {
    PENDING: t('orderStatus.PENDING'),
    PAID: t('orderStatus.PAID'),
    REFUNDED: t('orderStatus.REFUNDED'),
    CANCELLED: t('orderStatus.CANCELLED'),
    CLOSED: t('orderStatus.CLOSED')
  }[status] || status || '-'
}

async function openAnnouncementDetail(item) {
  try {
    const detail = await api.announcementDetail(item.id)
    announcementDetail.value = detail
    if (!item.read) {
      await api.markAnnouncementRead(item.id)
      item.read = true
    }
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function loadAnnouncements() {
  try {
    announcements.value = await api.announcements()
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function loadVipStatus() {
  try {
    const [status, records] = await Promise.all([
      api.userVipStatus().catch(() => null),
      api.userVipRecords({ limit: 100 }).catch(() => [])
    ])
    vipStatus.value = status
    vipRecords.value = records
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function loadFeedback() {
  try {
    feedbackList.value = await api.myFeedback({ limit: 100 })
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function submitFeedback() {
  if (!feedbackForm.title || !feedbackForm.content) {
    ElMessage.warning(t('profile.passwordRequired'))
    return
  }
  feedbackLoading.value = true
  try {
    await api.submitFeedback({
      type: feedbackForm.type,
      title: feedbackForm.title,
      content: feedbackForm.content
    })
    ElMessage.success(t('profile.feedbackSubmit'))
    resetFeedbackForm()
    loadFeedback()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    feedbackLoading.value = false
  }
}

function resetFeedbackForm() {
  feedbackForm.type = 'BUG'
  feedbackForm.title = ''
  feedbackForm.content = ''
  feedbackDetailData.value = null
}

function openFeedbackDetail(item) {
  feedbackDetailData.value = item
}

async function buyVipProduct(product) {
  await buyProduct(product)
}

function vipTypeLabel(type) {
  return {
    MONTH: '月卡',
    QUARTER: '季卡',
    YEAR: '年卡',
    WEEK: '周卡',
    DAY: '日卡'
  }[type] || type || '-'
}

function feedbackTypeLabel(type) {
  return {
    BUG: t('profile.feedbackBug'),
    SUGGESTION: t('profile.feedbackSuggestion'),
    CONSULTATION: t('profile.feedbackConsultation'),
    COMPLAINT: t('profile.feedbackComplaint'),
    OTHER: t('profile.feedbackOther')
  }[type] || type || '-'
}

function feedbackStatusLabel(status) {
  return {
    PENDING: t('profile.feedbackStatusPending'),
    REPLIED: t('profile.feedbackStatusReplied'),
    RESOLVED: t('profile.feedbackStatusResolved'),
    CLOSED: t('profile.feedbackStatusClosed')
  }[status] || status || '-'
}

function vipStatusLabel(status) {
  return {
    ACTIVE: t('profile.vipActive'),
    EXPIRED: t('profile.vipExpired'),
    PENDING: t('profile.orderStatusPending'),
    CANCELLED: t('orderStatus.CANCELLED')
  }[status] || status || '-'
}
</script>
