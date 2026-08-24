<template>
  <section class="stream-page vip-page">
    <button class="back-link back-button" type="button" @click="router.back()">← {{ t('common.back') }}</button>

    <div class="vip-page-header">
      <div>
        <p class="stream-kicker">VIP</p>
        <h1>{{ t('vip.pageTitle') }}</h1>
        <p class="vip-page-desc">{{ t('vip.pageDesc') }}</p>
      </div>
    </div>

    <div class="vip-status-card" :class="{ active: vipStatus?.active }" v-if="vipStatus">
      <div class="vip-status-badge">👑</div>
      <div class="vip-status-info">
        <div class="vip-status-title">
          {{ vipStatus.active ? t('vip.active') : t('vip.expired') }}
          <span v-if="membershipStatus?.current_level && membershipStatus.current_level !== 'NONE'"
            class="vip-level-badge" :style="{ color: levelColor(membershipStatus.current_level) }">
            {{ levelDisplayName(membershipStatus.current_level) }}
          </span>
        </div>
        <div v-if="vipStatus.active && vipStatus.days_left" class="vip-status-days">
          {{ t('vip.daysLeft', { count: vipStatus.days_left }) }}
        </div>
        <div v-if="vipStatus.active && vipStatus.expire_at" class="vip-status-expire">
          {{ t('vip.expireDate') }}：{{ formatDate(vipStatus.expire_at, locale) }}
        </div>
      </div>
      <div class="vip-status-actions">
        <button class="vip-action-btn" @click="scrollToPlans">{{ t('vip.renew') }}</button>
      </div>
      <div class="vip-status-glow"></div>
    </div>
    <div v-else class="vip-status-card inactive">
      <div class="vip-status-badge">👑</div>
      <div class="vip-status-info">
        <div class="vip-status-title">{{ t('vip.notActive') }}</div>
        <div class="vip-status-days">{{ t('vip.notActiveDesc') }}</div>
      </div>
      <div class="vip-status-actions">
        <button class="vip-action-btn" @click="scrollToPlans">{{ t('vip.buyNow') }}</button>
      </div>
    </div>

    <section class="vip-privileges">
      <h2 class="section-title">{{ t('vip.privileges') }}</h2>
      <div class="privilege-grid">
        <div v-for="item in privileges" :key="item.key" class="privilege-item">
          <span class="privilege-icon">{{ item.icon }}</span>
          <span class="privilege-text">{{ item.label }}</span>
        </div>
      </div>
    </section>

    <!-- 支付方式选择 -->
    <section class="vip-payment-section">
      <h2 class="section-title">{{ t('payment.method') }}</h2>
      <div class="payment-method-list">
        <label
          v-for="method in paymentMethods"
          :key="method.key"
          class="payment-method-item"
          :class="{ active: selectedMethod === method.key }"
        >
          <input
            type="radio"
            :value="method.key"
            v-model="selectedMethod"
            name="vip-payment-method"
          />
          <span class="payment-method-icon">{{ method.icon }}</span>
          <span class="payment-method-label">{{ method.label }}</span>
        </label>
      </div>
    </section>

    <section ref="plansRef" class="vip-plans-section">
      <h2 class="section-title">{{ t('vip.selectPlan') }}</h2>
      <div v-if="loading" class="stream-loading">{{ t('common.loading') }}</div>
      <div v-else-if="plans.length" class="vip-plan-grid">
        <article v-for="plan in plans" :key="plan.id" class="vip-plan-card" :class="{ recommended: field(plan, 'tag_text') }">
          <div class="vip-plan-badge" v-if="field(plan, 'tag_text')">{{ field(plan, 'tag_text') }}</div>
          <div class="vip-plan-name">{{ field(plan, 'name') }}</div>
          <div class="vip-plan-level" v-if="field(plan, 'membership_level') || field(plan, 'membershipLevel')"
            :style="{ color: levelColor(field(plan, 'membership_level') || field(plan, 'membershipLevel')) }">
            {{ levelDisplayName(field(plan, 'membership_level') || field(plan, 'membershipLevel')) }}
          </div>
          <div class="vip-plan-points">
            <span class="points-num">{{ formatNumber(planPoints(plan), locale) }}</span>
            <span class="points-unit">{{ t('common.points') }}</span>
          </div>
          <div v-if="field(plan, 'duration_days')" class="vip-plan-duration">
            {{ field(plan, 'duration_days') }} {{ t('vip.daysDuration') }}
          </div>
          <div v-if="planBonusPoints(plan)" class="vip-plan-bonus">
            +{{ planBonusPoints(plan) }} {{ t('recharge.bonus') }}
          </div>
          <div class="vip-plan-price">
            <span v-if="field(plan, 'original_price_cents')" class="price-original">
              {{ formatMoney(field(plan, 'original_price_cents'), field(plan, 'currency') || 'USD', locale) }}
            </span>
            <span class="price-current">{{ formatMoney(field(plan, 'price_cents'), field(plan, 'currency') || 'USD', locale) }}</span>
          </div>
          <button class="vip-plan-buy" :disabled="buyingId === plan.id" @click="buyPlan(plan)">
            {{ buyingId === plan.id ? t('common.processing') : t('vip.buyPlan') }}
          </button>
        </article>
      </div>
      <div v-else class="stream-empty">{{ t('vip.noPlans') }}</div>
    </section>

    <section class="vip-records-section">
      <h2 class="section-title">{{ t('vip.myRecords') }}</h2>
      <div v-if="loadingRecords" class="stream-loading">{{ t('common.loading') }}</div>
      <div v-else-if="records.length" class="vip-records-list">
        <article v-for="record in records" :key="record.id" class="vip-record-card">
          <div class="record-info">
            <div class="record-product">{{ field(record, 'product_name') || field(record, 'productName') }}</div>
            <div class="record-meta">
              <span class="record-type">{{ t('vip.vipMember') }}</span>
              <span class="record-time">{{ formatDate(record.created_at, locale) }}</span>
            </div>
          </div>
          <div class="record-status" :class="'status-' + record.status">
            {{ vipStatusLabel(record.status) }}
          </div>
        </article>
      </div>
      <div v-else class="stream-empty">{{ t('vip.noRecords') }}</div>
    </section>

    <!-- 等级预告 -->
    <section v-if="showLevelPreview && levelPreview" class="vip-level-preview">
      <div class="level-preview-card">
        <div class="level-preview-icon">🎉</div>
        <div class="level-preview-text">
          <span v-if="levelPreview.will_upgrade">{{ t('recharge.willUpgrade') }}</span>
          <span class="level-preview-new" :style="{ color: levelColor(levelPreview.new_level) }">
            {{ levelDisplayName(levelPreview.new_level) }}
          </span>
        </div>
        <button class="level-preview-close" @click="showLevelPreview = false">✕</button>
      </div>
    </section>

    <!-- IAP 验证对话框 -->
    <IapVerifyDialog
      v-model:visible="iapDialogVisible"
      :order-no="iapOrderNo"
      :store="selectedMethod === 'APPLE_IAP' ? 'APPLE' : 'GOOGLE'"
      @verified="onIapVerified"
    />

    <!-- 底部导航入口 -->
    <section class="vip-nav-links">
      <router-link to="/membership" class="vip-nav-link">
        <span class="vip-nav-icon">👑</span>
        <span class="vip-nav-text">{{ t('membership.title') }}</span>
        <span class="vip-nav-arrow">→</span>
      </router-link>
      <router-link to="/membership#exchange" class="vip-nav-link">
        <span class="vip-nav-icon">🔄</span>
        <span class="vip-nav-text">{{ t('vip.exchangeEntry') }}</span>
        <span class="vip-nav-arrow">→</span>
      </router-link>
    </section>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useStreamI18n } from '../locales/streamI18n.js'
import { ElMessage } from 'element-plus'
import { api } from '../api.js'
import { useDramaStore } from '../user/store.js'
import { field, formatNumber, formatMoney, formatDate, planPoints, planBonusPoints, levelColor, LEVEL_MAP } from '../utils/helpers.js'
import IapVerifyDialog from '../components/IapVerifyDialog.vue'

const router = useRouter()
const store = useDramaStore()
const { t, locale } = useStreamI18n()

const vipStatus = ref(null)
const membershipStatus = ref(null)
const plans = ref([])
const records = ref([])
const loading = ref(false)
const loadingRecords = ref(false)
const buyingId = ref(null)
const plansRef = ref(null)
const selectedMethod = ref('STRIPE')
const showLevelPreview = ref(false)
const levelPreview = ref(null)
const iapDialogVisible = ref(false)
const iapOrderNo = ref('')

// Web 环境仅保留可在浏览器完成的支付渠道。
// Apple 应用内购买 (APPLE_IAP) / Google Play 应用内购买 (GOOGLE_PLAY) 需要原生 App 拉起系统支付，
// 无法在纯网页端使用，因此在 front/admin 用户端不展示。
const paymentMethods = computed(() => [
  { key: 'STRIPE', icon: '💳', label: t('payment.creditCard') },
  { key: 'PAYPAL', icon: '🅿️', label: t('payment.paypal') }
])

const privileges = computed(() => [
  { key: 'free', icon: '🎬', label: t('vip.privilegeFree') },
  { key: 'hd', icon: '💎', label: t('vip.privilegeHd') },
  { key: 'noAd', icon: '🚫', label: t('vip.privilegeNoAd') },
  { key: 'update', icon: '⚡', label: t('vip.privilegeUpdate') },
  { key: 'service', icon: '💬', label: t('vip.privilegeService') },
  { key: 'cache', icon: '📥', label: t('vip.privilegeCache') }
])

function planTypeLabel(type) {
  const map = { POINT: t('vip.typePoint'), VIP: t('vip.typeVip'), BOTH: t('vip.typeBoth') }
  return map[type] || type || ''
}

function vipStatusLabel(status) {
  const map = { ACTIVE: t('vip.active'), EXPIRED: t('vip.expired'), CANCELLED: t('vip.cancelled') }
  return map[status] || status
}

function levelDisplayName(level) {
  const map = {
    NONE: t('membership.levelNone'),
    SILVER: t('membership.levelSilver'),
    GOLD: t('membership.levelGold'),
    DIAMOND: t('membership.levelDiamond')
  }
  return map[level] || level
}

function scrollToPlans() {
  plansRef.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

async function loadData() {
  loading.value = true
  loadingRecords.value = true
  try {
    await Promise.allSettled([
      loadVipStatus(),
      loadMembershipStatus(),
      loadPlans(),
      loadRecords()
    ])
  } finally {
    loading.value = false
    loadingRecords.value = false
  }
}

async function loadVipStatus() {
  try {
    vipStatus.value = await api.userVipStatus()
  } catch (err) {
    console.warn('加载VIP状态失败:', err)
  }
}

async function loadMembershipStatus() {
  try {
    membershipStatus.value = await api.membershipStatus()
  } catch (err) {
    console.warn('加载会员等级失败:', err)
  }
}

async function loadPlans() {
  try {
    const data = await api.pointProducts()
    const all = Array.isArray(data) ? data : data?.records || data?.list || []
    plans.value = all.filter(
      p => field(p, 'product_category') === 'VIP' || field(p, 'productCategory') === 'VIP'
    )
  } catch (err) {
    console.warn('加载套餐失败:', err)
  }
}

async function loadRecords() {
  try {
    const data = await api.userVipRecords()
    records.value = Array.isArray(data) ? data : data?.records || data?.list || []
  } catch (err) {
    console.warn('加载VIP记录失败:', err)
  }
}

async function buyPlan(plan) {
  if (buyingId.value) return
  try {
    buyingId.value = plan.id
    // Step 1: 创建订单
    const order = await api.userCreateOrder({
      productId: plan.id,
      payChannel: selectedMethod.value
    })
    if (!order?.order_no) {
      throw new Error('Failed to create order')
    }

    const orderNo = order.order_no

    // 等级预告
    if (order.level_preview) {
      levelPreview.value = order.level_preview
      showLevelPreview.value = true
    }

    // Step 2: 根据支付渠道跳转到对应支付页面
    if (selectedMethod.value === 'STRIPE') {
      const checkout = await api.userStripeCheckout(orderNo)
      if (checkout?.session_url) {
        window.location.href = checkout.session_url
        return
      }
    } else if (selectedMethod.value === 'PAYPAL') {
      const checkout = await api.userPayPalCheckout(orderNo)
      if (checkout?.approve_url) {
        window.location.href = checkout.approve_url
        return
      }
    } else if (selectedMethod.value === 'APPLE_IAP' || selectedMethod.value === 'GOOGLE_PLAY') {
      iapOrderNo.value = orderNo
      iapDialogVisible.value = true
      return
    }
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    buyingId.value = null
  }
}

async function handlePayPalReturn(orderNo, paypalOrderId) {
  if (!orderNo || !paypalOrderId) return
  loading.value = true
  try {
    const result = await api.userPayPalCapture(orderNo, paypalOrderId)
    if (result?.status === 'PAID') {
      ElMessage.success(t('recharge.rechargeSuccess'))
      store.loadProfile?.()
      await loadData()
    }
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}

async function onIapVerified() {
  ElMessage.success(t('recharge.rechargeSuccess'))
  store.loadProfile?.()
  await loadData()
}

onMounted(async () => {
  const params = new URLSearchParams(window.location.search)
  const paypalOrderId = params.get('token')
  const orderNo = params.get('orderNo')
  const status = params.get('status')

  if (paypalOrderId && orderNo && status === 'success') {
    await handlePayPalReturn(orderNo, paypalOrderId)
    window.history.replaceState({}, '', window.location.pathname)
  } else if (orderNo && status === 'success') {
    ElMessage.success(t('recharge.rechargeSuccess'))
    store.loadProfile?.()
    window.history.replaceState({}, '', window.location.pathname)
  } else if (status === 'cancelled') {
    ElMessage.warning(t('recharge.paymentCancelled'))
    window.history.replaceState({}, '', window.location.pathname)
  }

  await loadData()
})
</script>

<style scoped>
.vip-payment-section {
  margin-bottom: 24px;
}

.payment-method-list {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.payment-method-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  border: 2px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  background: rgba(255, 255, 255, 0.05);
}

.payment-method-item:hover {
  border-color: rgba(212, 175, 104, 0.5);
}

.payment-method-item.active {
  border-color: #d4af68;
  background: rgba(212, 175, 104, 0.1);
}

.payment-method-item input[type="radio"] {
  display: none;
}

.payment-method-icon {
  font-size: 20px;
}

.payment-method-label {
  color: #e0d5c2;
  font-size: 14px;
}

/* 会员等级徽章 */
.vip-level-badge {
  display: inline-block;
  margin-left: 8px;
  padding: 2px 10px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  font-size: 13px;
  font-weight: 600;
}

/* 套餐等级标签 */
.vip-plan-level {
  padding: 3px 10px;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 8px;
}

/* 套餐赠送积分 */
.vip-plan-bonus {
  padding: 2px 8px;
  background: rgba(76, 175, 80, 0.1);
  border-radius: 6px;
  font-size: 12px;
  color: #4caf50;
  margin-bottom: 8px;
}

/* 等级预告 */
.vip-level-preview {
  margin-bottom: 24px;
}
.level-preview-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  background: rgba(212, 175, 104, 0.1);
  border: 1px solid rgba(212, 175, 104, 0.3);
  border-radius: 12px;
}
.level-preview-icon {
  font-size: 28px;
}
.level-preview-text {
  flex: 1;
  font-size: 14px;
  color: #e0d5c2;
}
.level-preview-new {
  font-weight: 700;
  margin-left: 4px;
}
.level-preview-close {
  padding: 4px 8px;
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 6px;
  color: #999;
  font-size: 14px;
  cursor: pointer;
}

/* 底部导航 */
.vip-nav-links {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 16px;
  margin-bottom: 24px;
}
.vip-nav-link {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 12px;
  text-decoration: none;
  transition: background 0.2s;
}
.vip-nav-link:hover {
  background: rgba(212, 175, 104, 0.1);
}
.vip-nav-icon {
  font-size: 22px;
}
.vip-nav-text {
  flex: 1;
  font-size: 15px;
  color: #e0d5c2;
  font-weight: 500;
}
.vip-nav-arrow {
  color: #d4af68;
  font-size: 16px;
}
</style>
