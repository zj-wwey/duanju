<template>
  <section class="stream-page recharge-page">
    <button class="back-link back-button" type="button" @click="router.back()">← {{ t('common.back') }}</button>

    <!-- 头部：当前余额 -->
    <div class="recharge-balance-card">
      <div class="balance-icon">💰</div>
      <div class="balance-info">
        <div class="balance-label">{{ t('recharge.currentBalance') }}</div>
        <div class="balance-value">{{ formatNumber(store.points.value, locale) }} <span class="balance-unit">{{ t('common.points') }}</span></div>
      </div>
      <div class="balance-actions">
        <button class="balance-action-btn" @click="router.push({ name: 'profile', query: { panel: 'orders' } })">
          {{ t('recharge.viewRecords') }}
        </button>
      </div>
      <div class="balance-glow"></div>
    </div>

    <!-- 选择套餐 -->
    <section class="recharge-plans">
      <h2 class="section-title">{{ t('recharge.selectPlan') }}</h2>
      <div v-if="loading" class="stream-loading">{{ t('common.loading') }}</div>
      <div v-else-if="plans.length" class="recharge-plan-grid">
        <article
          v-for="plan in plans"
          :key="plan.id"
          class="recharge-plan-card"
          :class="{ selected: selectedPlan?.id === plan.id, recommended: field(plan, 'tag_text') }"
          @click="selectedPlan = plan"
        >
          <div class="recharge-plan-badge" v-if="field(plan, 'tag_text')">{{ field(plan, 'tag_text') }}</div>
          <div class="recharge-plan-points">
            <span class="points-num">{{ formatNumber(planPoints(plan), locale) }}</span>
            <span class="points-unit">{{ t('common.points') }}</span>
          </div>
          <div v-if="field(plan, 'bonus_points')" class="recharge-plan-bonus">
            {{ t('recharge.bonusPoints', { count: field(plan, 'bonus_points') }) }}
          </div>
          <div class="recharge-plan-name">{{ field(plan, 'name') }}</div>
          <div v-if="field(plan, 'first_purchase_bonus') || field(plan, 'firstPurchaseBonus')" class="recharge-plan-first">
            {{ t('recharge.firstPurchaseBonus') }}：+{{ formatNumber(field(plan, 'first_purchase_bonus') || field(plan, 'firstPurchaseBonus'), locale) }}
          </div>
          <div class="recharge-plan-price">
            <span class="price-current">{{ formatMoney(field(plan, 'price_cents'), field(plan, 'currency') || 'USD', locale) }}</span>
          </div>
          <div class="recharge-plan-check" v-if="selectedPlan?.id === plan.id">✓</div>
        </article>
      </div>
      <div v-else class="stream-empty">{{ t('recharge.noPlans') }}</div>
    </section>

    <!-- 支付方式和确认 -->
    <section v-if="selectedPlan" class="recharge-confirm">
      <h2 class="section-title">{{ t('recharge.confirmPayment') }}</h2>
      <div class="recharge-confirm-card">
        <div class="confirm-plan-info">
          <div class="confirm-plan-name">{{ field(selectedPlan, 'name') }}</div>
          <div class="confirm-plan-points">
            {{ formatNumber(planPoints(selectedPlan), locale) }} {{ t('common.points') }}
            <span v-if="field(selectedPlan, 'bonus_points')" class="confirm-bonus">
              + {{ field(selectedPlan, 'bonus_points') }} {{ t('recharge.bonus') }}
            </span>
          </div>
        </div>
        <div class="confirm-methods">
          <span class="confirm-label">{{ t('recharge.payMethod') }}</span>
          <div class="method-options">
            <button
              v-for="method in payMethods"
              :key="method.key"
              class="method-btn"
              :class="{ active: selectedMethod === method.key }"
              @click="selectedMethod = method.key"
            >
              <span class="method-icon">{{ method.icon }}</span>
              <span class="method-name">{{ method.label }}</span>
            </button>
          </div>
        </div>
        <div class="confirm-total">
          <span>{{ t('recharge.totalAmount') }}</span>
          <span class="total-price">{{ formatMoney(field(selectedPlan, 'price_cents'), field(selectedPlan, 'currency') || 'USD', locale) }}</span>
        </div>
        <button class="recharge-submit-btn" :disabled="submitting" @click="submitRecharge">
          {{ submitting ? t('common.processing') : t('recharge.confirmRecharge') }}
        </button>
      </div>
    </section>

    <!-- 等级预告 -->
    <section v-if="showLevelPreview && levelPreview" class="recharge-level-preview">
      <div class="level-preview-card">
        <div class="level-preview-icon">🎉</div>
        <div class="level-preview-text">
          <span v-if="levelPreview.will_upgrade">{{ t('recharge.willUpgrade') }}</span>
          <span class="level-preview-new" :style="{ color: levelColor(levelPreview.new_level) }">
            {{ levelDisplayName(levelPreview.new_level) }}
          </span>
          <div v-if="levelPreview.first_purchase_bonus" class="level-preview-bonus">
            {{ t('recharge.firstPurchaseBonus') }}：+{{ formatNumber(levelPreview.first_purchase_bonus, locale) }} {{ t('common.points') }}
          </div>
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

    <!-- 积分说明 -->
    <section class="recharge-notice">
      <h3>{{ t('recharge.noticeTitle') }}</h3>
      <ul>
        <li>{{ t('recharge.notice1') }}</li>
        <li>{{ t('recharge.notice2') }}</li>
        <li>{{ t('recharge.notice3') }}</li>
        <li class="notice-expire" v-if="pointsExpireAt">{{ t('recharge.pointsExpireHint', { date: formatDate(pointsExpireAt) }) }}</li>
      </ul>
    </section>

    <!-- 底部导航入口 -->
    <section class="recharge-nav-links">
      <router-link to="/shop" class="recharge-nav-link">
        <span class="recharge-nav-icon">🛒</span>
        <span class="recharge-nav-text">{{ t('shop.title') }}</span>
        <span class="recharge-nav-arrow">→</span>
      </router-link>
    </section>
  </section>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useStreamI18n } from '../locales/streamI18n.js'
import { ElMessage } from 'element-plus'
import { api } from '../api.js'
import { useDramaStore } from '../user/store.js'
import { field, formatNumber, formatMoney, formatDate, planPoints, planBonusPoints, levelColor } from '../utils/helpers.js'
import IapVerifyDialog from '../components/IapVerifyDialog.vue'

const router = useRouter()
const store = useDramaStore()
const { t, locale } = useStreamI18n()
const { pointsExpireAt } = store

const plans = ref([])
const loading = ref(false)
const submitting = ref(false)
const selectedPlan = ref(null)
const selectedMethod = ref('STRIPE')
const levelPreview = ref(null)
const showLevelPreview = ref(false)
const membershipStatus = ref(null)
const iapDialogVisible = ref(false)
const iapOrderNo = ref('')
// 支付回跳后轮询订单状态
const pollingOrderNo = ref('')
const pollingTimer = ref(null)
const pollingMaxAttempts = 15
const pollingIntervalMs = 4000
let pollingAttempts = 0

// Web 环境仅保留可在浏览器完成的支付渠道。
const payMethods = computed(() => [
  { key: 'STRIPE', icon: '💳', label: t('recharge.creditCard') },
  { key: 'PAYPAL', icon: '🅿️', label: t('recharge.paypal') }
])

function levelDisplayName(level) {
  const map = {
    NONE: t('membership.levelNone'),
    SILVER: t('membership.levelSilver'),
    GOLD: t('membership.levelGold'),
    DIAMOND: t('membership.levelDiamond')
  }
  return map[level] || level
}

async function loadPlans() {
  loading.value = true
  try {
    const data = await api.pointProducts()
    const all = Array.isArray(data) ? data : data?.records || data?.list || []
    plans.value = all.filter(p => {
      const category = field(p, 'product_category', 'productCategory')
      return !category || category === 'RECHARGE'
    })
  } catch (err) {
    console.warn('加载套餐失败:', err)
  } finally {
    loading.value = false
  }
}

async function submitRecharge() {
  if (!selectedPlan.value || submitting.value) return
  try {
    submitting.value = true
    const order = await api.userCreateOrder({
      productId: selectedPlan.value.id,
      payChannel: selectedMethod.value
    })
    if (!order?.order_no) {
      throw new Error('Failed to create order')
    }

    const orderNo = order.order_no

    if (order.level_preview) {
      levelPreview.value = order.level_preview
      showLevelPreview.value = true
    }

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
    submitting.value = false
  }
}

function startPollingOrderStatus(orderNo) {
  if (!orderNo) return
  pollingOrderNo.value = orderNo
  pollingAttempts = 0
  stopPollingOrderStatus()
  pollingTimer.value = setInterval(async () => {
    pollingAttempts++
    try {
      const list = await api.orders({ limit: 50 })
      const orders = Array.isArray(list) ? list : list?.records || list?.list || []
      const target = orders.find(o => (o.order_no || o.orderNo) === orderNo)
      if (target) {
        const status = target.status || 'PENDING'
        if (status === 'PAID') {
          stopPollingOrderStatus()
          ElMessage.success(t('recharge.rechargeSuccess'))
          selectedPlan.value = null
          store.loadProfile?.()
        } else if (status === 'REFUNDED' || status === 'CANCELLED') {
          stopPollingOrderStatus()
          if (status === 'REFUNDED') ElMessage.warning(t('recharge.paymentRefunded'))
          else ElMessage.info(t('recharge.paymentCancelled'))
        }
      }
      if (pollingAttempts >= pollingMaxAttempts) {
        stopPollingOrderStatus()
        ElMessage.info(t('recharge.paymentPendingHint'))
      }
    } catch (err) {
      if (pollingAttempts >= pollingMaxAttempts) {
        stopPollingOrderStatus()
      }
    }
  }, pollingIntervalMs)
}

function stopPollingOrderStatus() {
  if (pollingTimer.value) {
    clearInterval(pollingTimer.value)
    pollingTimer.value = null
  }
}

async function handlePayPalReturn(orderNo, paypalOrderId) {
  if (!orderNo) return
  try {
    submitting.value = true
    if (paypalOrderId) {
      const result = await api.userPayPalCapture(orderNo, paypalOrderId)
      if (result?.status === 'PAID') {
        ElMessage.success(t('recharge.rechargeSuccess'))
        selectedPlan.value = null
        store.loadProfile?.()
        return
      }
    }
  } catch (_err) {
  } finally {
    submitting.value = false
  }
  ElMessage.info(t('recharge.confirmingPayment'))
  startPollingOrderStatus(orderNo)
}

async function onIapVerified() {
  ElMessage.success(t('recharge.rechargeSuccess'))
  selectedPlan.value = null
  store.loadProfile?.()
}

onMounted(async () => {
  const params = new URLSearchParams(window.location.search)
  const paypalOrderId = params.get('token') || params.get('paypalOrderId')
  const orderNo = params.get('orderNo')
  const rawStatus = params.get('status')

  if (rawStatus === 'cancelled') {
    ElMessage.warning(t('recharge.paymentCancelled'))
    window.history.replaceState({}, '', window.location.pathname)
  } else if (orderNo) {
    if (paypalOrderId) {
      await handlePayPalReturn(orderNo, paypalOrderId)
    } else {
      ElMessage.info(t('recharge.confirmingPayment'))
      startPollingOrderStatus(orderNo)
    }
    window.history.replaceState({}, '', window.location.pathname)
  }

  await loadPlans()
})

onBeforeUnmount(() => {
  stopPollingOrderStatus()
})
</script>

<style scoped>
/* 首充标识 */
.recharge-plan-first {
  padding: 3px 8px;
  background: rgba(255, 152, 0, 0.12);
  border-radius: 6px;
  font-size: 11px;
  color: #ff9800;
  margin-bottom: 8px;
}

/* 等级预告 */
.recharge-level-preview {
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
.level-preview-bonus {
  margin-top: 4px;
  font-size: 12px;
  color: #4caf50;
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

/* 积分过期提示 */
.notice-expire {
  color: #ff9800;
  font-weight: 500;
}

/* 底部导航 */
.recharge-nav-links {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 16px;
  margin-bottom: 24px;
}
.recharge-nav-link {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 12px;
  text-decoration: none;
  transition: background 0.2s;
}
.recharge-nav-link:hover {
  background: rgba(212, 175, 104, 0.1);
}
.recharge-nav-icon {
  font-size: 22px;
}
.recharge-nav-text {
  flex: 1;
  font-size: 15px;
  color: #e0d5c2;
  font-weight: 500;
}
.recharge-nav-arrow {
  color: #d4af68;
  font-size: 16px;
}
</style>
