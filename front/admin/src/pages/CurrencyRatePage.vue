<template>
  <section class="stream-page currency-rate-page">
    <header class="stream-section-head">
      <div>
        <p class="stream-kicker">{{ t('currencyRate.kicker') }}</p>
        <h1>{{ t('currencyRate.title') }}</h1>
      </div>
      <div class="page-actions">
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          {{ t('currencyRate.addCurrency') }}
        </el-button>
        <el-button @click="refreshCache">
          <el-icon><Refresh /></el-icon>
          {{ t('currencyRate.refreshCache') }}
        </el-button>
      </div>
    </header>

    <section class="rate-stats">
      <div class="stat-card">
        <span class="stat-value">{{ rates.length }}</span>
        <span class="stat-label">{{ t('currencyRate.totalCurrencies') }}</span>
      </div>
      <div class="stat-card">
        <span class="stat-value">{{ enabledCount }}</span>
        <span class="stat-label">{{ t('currencyRate.enabledCurrencies') }}</span>
      </div>
    </section>

    <section class="rate-table-section">
      <el-table :data="pagedRates" v-loading="loading" stripe>
        <el-table-column prop="currencyCode" :label="t('currencyRate.code')" width="100" />
        <el-table-column prop="currencyName" :label="t('currencyRate.name')" width="120" />
        <el-table-column prop="symbol" :label="t('currencyRate.symbol')" width="80">
          <template #default="{ row }">
            <span class="currency-symbol">{{ row.symbol }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="rateToUsd" :label="t('currencyRate.rateToUsd')" width="150">
          <template #default="{ row }">
            1 USD = {{ row.rateToUsd?.toFixed(6) }} {{ row.currencyCode }}
          </template>
        </el-table-column>
        <el-table-column prop="decimals" :label="t('currencyRate.decimals')" width="80" />
        <el-table-column prop="locale" :label="t('currencyRate.locale')" width="120" />
        <el-table-column prop="enabled" :label="t('currencyRate.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? t('common.enabled') : t('common.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEditDialog(row)">
              {{ t('common.edit') }}
            </el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pager.page"
          v-model:page-size="pager.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="rates.length"
          layout="total, sizes, prev, pager, next, jumper"
          background
        />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form :model="formData" label-width="120px">
        <el-form-item :label="t('currencyRate.locale')">
          <el-select v-model="formData.locale" placeholder="选择语言">
            <el-option 
              v-for="opt in localeOptions" 
              :key="opt.code" 
              :label="opt.label" 
              :value="opt.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('currencyRate.code')">
          <el-input v-model="formData.currencyCode" :disabled="!isEdit" placeholder="选择语言后自动填充" />
        </el-form-item>
        <el-form-item :label="t('currencyRate.name')">
          <el-input v-model="formData.currencyName" :disabled="!isEdit" placeholder="选择语言后自动填充" />
        </el-form-item>
        <el-form-item :label="t('currencyRate.symbol')">
          <el-input v-model="formData.symbol" :disabled="!isEdit" placeholder="选择语言后自动填充" />
        </el-form-item>
        <el-form-item :label="t('currencyRate.decimals')">
          <el-select v-model="formData.decimals" :disabled="!isEdit">
            <el-option :value="0" label="0" />
            <el-option :value="2" label="2" />
            <el-option :value="3" label="3" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('currencyRate.rateToUsd')">
          <div class="rate-input-wrapper">
            <el-input-number v-model="formData.rateToUsd" :precision="6" :step="0.01" />
            <el-button 
              type="primary" 
              link 
              :disabled="!formData.currencyCode" 
              @click="fetchLiveRate"
            >
              {{ t('currencyRate.fetchLiveRate') }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item :label="t('currencyRate.status')">
          <el-switch v-model="formData.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSubmit">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { api } from '../api.js'
import { currencyPresets, getCurrencyPreset, localeOptions } from '../utils/currencyPresets.js'
import { adminI18n } from '../i18n.js'

// 从 adminI18n 的 messages 中获取中文翻译（支持嵌套键）
function t(key) {
  const zhCN = adminI18n.global.messages.value['zh-CN']
  if (!zhCN) return key
  const parts = key.split('.')
  let value = zhCN
  for (const part of parts) {
    if (value && typeof value === 'object' && part in value) {
      value = value[part]
    } else {
      return key
    }
  }
  return typeof value === 'string' ? value : key
}

const rates = ref([])
const pager = reactive({ page: 1, pageSize: 10 })
const pagedRates = computed(() => {
  const start = (pager.page - 1) * pager.pageSize
  return rates.value.slice(start, start + pager.pageSize)
})
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formData = reactive({
  id: null,
  currencyCode: '',
  currencyName: '',
  symbol: '',
  rateToUsd: 1,
  decimals: 2,
  locale: '',
  enabled: 1,
  sortOrder: 0
})

// 监听语言选择变化，自动填充币种信息
watch(() => formData.locale, (newLocale) => {
  if (!isEdit.value && newLocale) {
    const preset = getCurrencyPreset(newLocale)
    if (preset) {
      formData.currencyCode = preset.currencyCode
      formData.currencyName = preset.currencyName
      formData.symbol = preset.symbol
      formData.decimals = preset.decimals
    }
  }
})

const enabledCount = computed(() => rates.value.filter(r => r.enabled).length)
const dialogTitle = computed(() => isEdit.value ? t('currencyRate.editCurrency') : t('currencyRate.addCurrency'))

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const res = await api.getCurrencyRates()
    rates.value = res || []
  } catch (e) {
    console.error('Load currency rates failed:', e)
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  isEdit.value = false
  Object.assign(formData, {
    id: null,
    currencyCode: '',
    currencyName: '',
    symbol: '',
    rateToUsd: 1,
    decimals: 2,
    locale: '',
    enabled: 1,
    sortOrder: 0
  })
  dialogVisible.value = true
}

function openEditDialog(row) {
  isEdit.value = true
  Object.assign(formData, {
    ...row
  })
  dialogVisible.value = true
}

// 从免费API获取实时汇率
async function fetchLiveRate() {
  if (!formData.currencyCode || formData.currencyCode === 'USD' || !/^[A-Z]{3}$/.test(formData.currencyCode)) {
    ElMessage.warning(t('currencyRate.selectCurrencyFirst'))
    return
  }

  try {
    const response = await fetch(`https://api.frankfurter.app/latest?from=USD&to=${formData.currencyCode}`)
    if (!response.ok) {
      throw new Error('API请求失败')
    }
    const data = await response.json()
    if (data.rates && data.rates[formData.currencyCode]) {
      formData.rateToUsd = data.rates[formData.currencyCode]
      ElMessage.success(t('currencyRate.rateUpdated'))
    } else {
      throw new Error('汇率数据不存在')
    }
  } catch (e) {
    console.error('Fetch live rate failed:', e)
    ElMessage.error(t('currencyRate.fetchRateFailed'))
  }
}

async function handleSubmit() {
  try {
    if (isEdit.value) {
      await api.updateCurrencyRate(formData.id, formData)
      ElMessage.success(t('common.updateSuccess'))
    } else {
      await api.createCurrencyRate(formData)
      ElMessage.success(t('common.createSuccess'))
    }
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    console.error('Submit failed:', e)
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      t('currencyRate.deleteConfirm'),
      t('common.confirm'),
      { type: 'warning' }
    )
    await api.deleteCurrencyRate(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    await loadData()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('Delete failed:', e)
    }
  }
}

async function refreshCache() {
  try {
    await api.refreshCurrencyRateCache()
    ElMessage.success(t('currencyRate.refreshSuccess'))
  } catch (e) {
    console.error('Refresh cache failed:', e)
  }
}
</script>

<style scoped>
.currency-rate-page {
  padding: 24px;
}

.page-actions {
  display: flex;
  gap: 12px;
}

.rate-stats {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  flex: 1;
  background: linear-gradient(135deg, rgba(255, 215, 107, 0.1), rgba(255, 165, 0, 0.1));
  border: 1px solid rgba(212, 175, 104, 0.3);
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #d4af68;
}

.stat-label {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);
}

.rate-table-section {
  background: rgba(255, 255, 255, 0.05);
  border-radius: 12px;
  padding: 16px 16px 0;
}

.currency-symbol {
  font-size: 18px;
  font-weight: 600;
  color: #d4af68;
}

.rate-input-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>