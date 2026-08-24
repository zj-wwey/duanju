<template>
  <section class="stream-page shop-page">
    <button class="back-link back-button" type="button" @click="router.back()">← {{ t('common.back') }}</button>

    <div class="shop-header">
      <p class="stream-kicker">Shop</p>
      <h1>{{ t('shop.title') }}</h1>
    </div>

    <!-- 商品列表 -->
    <section class="shop-items">
      <h2 class="section-title">{{ t('shop.title') }}</h2>
      <div v-if="loading" class="stream-loading">{{ t('common.loading') }}</div>
      <div v-else-if="items.length" class="shop-item-grid">
        <article v-for="item in items" :key="item.id" class="shop-item-card">
          <div class="shop-item-icon">🎁</div>
          <div class="shop-item-name">{{ item.name }}</div>
          <div class="shop-item-price">
            <div class="shop-price-main">
              <span class="price-value">{{ formatNumber(item.pointsCost, locale) }}</span>
              <span class="price-unit">{{ t('common.points') }}</span>
            </div>
            <div v-if="item.vipPointsCost && item.vipPointsCost < item.pointsCost" class="shop-price-vip">
              <span class="vip-label">{{ t('shop.vipPrice') }}</span>
              <span class="vip-value">{{ formatNumber(item.vipPointsCost, locale) }}</span>
              <span class="vip-unit">{{ t('common.points') }}</span>
            </div>
          </div>
          <div class="shop-item-stock" v-if="item.stock >= 0">
            {{ t('shop.stock') }}：{{ item.stock }}
          </div>
          <button
            class="shop-exchange-btn"
            :disabled="exchangingId === item.id || item.stock === 0"
            @click="exchangeItem(item)"
          >
            {{ exchangingId === item.id ? t('common.processing') : item.stock === 0 ? t('shop.itemOutOfStock') : t('shop.exchange') }}
          </button>
        </article>
      </div>
      <div v-else class="stream-empty">{{ t('shop.noItems') }}</div>
    </section>

    <!-- 兑换记录 -->
    <section class="shop-records">
      <h2 class="section-title">{{ t('shop.records') }}</h2>
      <div v-if="loadingRecords" class="stream-loading">{{ t('common.loading') }}</div>
      <div v-else-if="records.length" class="shop-records-list">
        <article v-for="record in records" :key="record.id" class="shop-record-card">
          <div class="record-icon">📦</div>
          <div class="record-info">
            <div class="record-name">{{ record.itemName }}</div>
            <div class="record-meta">
              <span class="record-cost">-{{ formatNumber(record.pointsCost, locale) }} {{ t('common.points') }}</span>
              <span class="record-time">{{ formatDate(record.createdAt || record.created_at, locale, true) }}</span>
            </div>
          </div>
          <div class="record-delivery" :class="'delivery-' + (record.deliveryStatus || record.delivery_status)">
            {{ record.deliveryStatus === 'DELIVERED' || record.delivery_status === 'DELIVERED' ? t('shop.delivered') : t('shop.pending') }}
          </div>
        </article>
      </div>
      <div v-else class="stream-empty">{{ t('shop.noRecords') }}</div>
    </section>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useStreamI18n } from '../locales/streamI18n.js'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api.js'
import { useDramaStore } from '../user/store.js'
import { formatNumber, formatDate } from '../utils/helpers.js'

const router = useRouter()
const store = useDramaStore()
const { t, locale } = useStreamI18n()

const items = ref([])
const records = ref([])
const loading = ref(false)
const loadingRecords = ref(false)
const exchangingId = ref(null)

async function loadData() {
  loading.value = true
  loadingRecords.value = true
  try {
    await Promise.allSettled([loadItems(), loadRecords()])
  } finally {
    loading.value = false
    loadingRecords.value = false
  }
}

async function loadItems() {
  try {
    const data = await api.shopItems()
    items.value = Array.isArray(data) ? data : data?.records || data?.list || []
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function loadRecords() {
  try {
    const data = await api.shopRecords()
    records.value = Array.isArray(data) ? data : data?.records || data?.list || []
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function exchangeItem(item) {
  if (exchangingId.value) return
  try {
    await ElMessageBox.confirm(
      t('shop.exchangeConfirm', { points: item.pointsCost, name: item.name }),
      t('shop.exchange'),
      { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'warning' }
    )
  } catch {
    return
  }
  exchangingId.value = item.id
  try {
    await api.shopExchange(item.id)
    ElMessage.success(t('shop.exchangeSuccess'))
    store.loadProfile?.()
    await Promise.all([loadItems(), loadRecords()])
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    exchangingId.value = null
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.shop-header {
  margin-bottom: 24px;
}

/* 商品列表 */
.shop-item-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}
.shop-item-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 16px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  transition: border-color 0.2s;
}
.shop-item-card:hover {
  border-color: rgba(212, 175, 104, 0.3);
}
.shop-item-icon {
  font-size: 36px;
  margin-bottom: 10px;
}
.shop-item-name {
  font-size: 15px;
  font-weight: 600;
  color: #e0d5c2;
  margin-bottom: 10px;
  text-align: center;
}
.shop-item-price {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  margin-bottom: 10px;
}
.shop-price-main {
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.price-value {
  font-size: 22px;
  font-weight: 700;
  color: #d4af68;
}
.price-unit {
  font-size: 12px;
  color: #999;
}
.shop-price-vip {
  display: flex;
  align-items: baseline;
  gap: 4px;
  padding: 2px 8px;
  background: rgba(76, 175, 80, 0.1);
  border-radius: 6px;
}
.vip-label {
  font-size: 10px;
  color: #4caf50;
}
.vip-value {
  font-size: 14px;
  font-weight: 600;
  color: #4caf50;
}
.vip-unit {
  font-size: 10px;
  color: #4caf50;
}
.shop-item-stock {
  font-size: 12px;
  color: #999;
  margin-bottom: 12px;
}
.shop-exchange-btn {
  width: 100%;
  padding: 10px;
  background: linear-gradient(135deg, #d4af68, #c09a4e);
  border: none;
  border-radius: 8px;
  color: #1a1a2e;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s;
}
.shop-exchange-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 兑换记录 */
.shop-records-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 24px;
}
.shop-record-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 10px;
}
.record-icon {
  font-size: 24px;
}
.record-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.record-name {
  font-size: 14px;
  color: #e0d5c2;
}
.record-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #999;
}
.record-cost {
  color: #f44336;
}
.record-delivery {
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
}
.record-delivery.delivery-PENDING {
  background: rgba(255, 152, 0, 0.15);
  color: #ff9800;
}
.record-delivery.delivery-DELIVERED {
  background: rgba(76, 175, 80, 0.15);
  color: #4caf50;
}
</style>