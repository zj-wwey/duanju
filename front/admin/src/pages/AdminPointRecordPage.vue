<template>
  <section class="admin-page-section">
    <div class="toolbar">
      <div>
        <p class="eyebrow">{{ eyebrow }}</p>
        <h2>{{ title }}</h2>
      </div>
      <div class="toolbar-actions">
        <el-input v-model="filters.userId" :placeholder="adminT('userId')" clearable @keyup.enter="load" @clear="load" />
        <el-select v-model="filters.bizType" clearable :placeholder="'业务类型'" @change="load" @clear="load">
          <el-option label="签到" value="CHECKIN" />
          <el-option label="广告奖励" value="AD_REWARD" />
          <el-option label="购买" value="PURCHASE" />
          <el-option label="解锁消费" value="UNLOCK" />
          <el-option label="商城兑换" value="SHOP_EXCHANGE" />
          <el-option label="管理赠送" value="ADMIN_GRANT" />
          <el-option label="分享奖励" value="SHARE_REWARD" />
          <el-option label="观看奖励" value="WATCH_REWARD" />
          <el-option label="评论奖励" value="COMMENT_REWARD" />
        </el-select>
        <el-date-picker v-model="filters.dateRange" type="daterange" range-separator="至"
          start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD"
          @change="load" />
        <el-button @click="load">{{ adminT('search') }}</el-button>
        <el-button type="primary" @click="loadStats">统计</el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="metric-grid" v-if="statsData">
      <div class="metric-card">
        <span>{{ formatNumber(field(statsData, 'total_records')) }}</span>
        <label>总记录数</label>
      </div>
      <div class="metric-card accent">
        <span>{{ formatNumber(field(statsData, 'total_earned')) }}</span>
        <label>总获取积分</label>
      </div>
      <div class="metric-card">
        <span>{{ formatNumber(field(statsData, 'total_spent')) }}</span>
        <label>总消费积分</label>
      </div>
    </div>

    <el-table :data="pagedItems" border v-loading="loading">
      <el-table-column prop="id" :label="adminT('id')" width="80" />
      <el-table-column :label="adminT('userId')" width="100">
        <template #default="{ row }">{{ field(row, 'userId', 'user_id') }}</template>
      </el-table-column>
      <el-table-column :label="'积分变动'" width="110">
        <template #default="{ row }">
          <span :style="{ color: (field(row, 'delta') || 0) >= 0 ? '#67c23a' : '#f56c6c', fontWeight: 'bold' }">
            {{ (field(row, 'delta') || 0) >= 0 ? '+' : '' }}{{ formatNumber(field(row, 'delta')) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column :label="'变动后余额'" width="110">
        <template #default="{ row }">{{ formatNumber(field(row, 'balance', 'balance_after')) }}</template>
      </el-table-column>
      <el-table-column :label="'业务类型'" width="120">
        <template #default="{ row }">{{ bizTypeLabel(field(row, 'bizType', 'biz_type')) }}</template>
      </el-table-column>
      <el-table-column :label="'业务ID'" width="100">
        <template #default="{ row }">{{ field(row, 'bizId', 'biz_id') || '-' }}</template>
      </el-table-column>
      <el-table-column :label="'描述'" min-width="180">
        <template #default="{ row }">{{ field(row, 'description') || field(row, 'remark') || '-' }}</template>
      </el-table-column>
      <el-table-column :label="'时间'" width="160">
        <template #default="{ row }">{{ formatDateTime(field(row, 'createdAt', 'created_at')) }}</template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="pager.page"
        v-model:page-size="pager.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="items.length"
        layout="total, sizes, prev, pager, next, jumper"
        background
      />
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api.js'

const props = defineProps({
  adminT: { type: Function, required: true },
  field: { type: Function, required: true },
  formatNumber: { type: Function, required: true },
  formatDateTime: { type: Function, required: true },
  title: { type: String, default: '积分记录' },
  eyebrow: { type: String, default: '数据中心' }
})

const items = ref([])
const pager = reactive({ page: 1, pageSize: 10 })
const pagedItems = computed(() => {
  const start = (pager.page - 1) * pager.pageSize
  return items.value.slice(start, start + pager.pageSize)
})
const statsData = ref(null)
const loading = ref(false)
const filters = reactive({ userId: '', bizType: '', dateRange: null })

function bizTypeLabel(type) {
  const map = {
    CHECKIN: '签到', AD_REWARD: '广告奖励', PURCHASE: '购买',
    UNLOCK: '解锁消费', SHOP_EXCHANGE: '商城兑换', ADMIN_GRANT: '管理赠送',
    SHARE_REWARD: '分享奖励', WATCH_REWARD: '观看奖励', COMMENT_REWARD: '评论奖励',
    REFUND: '退款', EXPIRE: '过期'
  }
  return map[type] || type || '-'
}

async function load() {
  loading.value = true
  try {
    const params = { limit: 200 }
    if (filters.userId) params.userId = filters.userId
    if (filters.bizType) params.bizType = filters.bizType
    if (filters.dateRange && filters.dateRange.length === 2) {
      params.startDate = filters.dateRange[0]
      params.endDate = filters.dateRange[1]
    }
    items.value = await api.adminPointRecords(params)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const params = {}
    if (filters.dateRange && filters.dateRange.length === 2) {
      params.startDate = filters.dateRange[0]
      params.endDate = filters.dateRange[1]
    }
    statsData.value = await api.adminPointRecordStats(params)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<style scoped>
.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
.metric-card {
  background: var(--bg-card);
  border: 1px solid var(--border-gold);
  border-radius: 10px;
  padding: 16px;
  text-align: center;
  box-shadow: 0 14px 34px rgba(0, 0, 0, 0.3);
}
.metric-card.accent {
  background: linear-gradient(135deg, rgba(200, 109, 38, 0.32), rgba(30, 28, 25, 0.95));
}
.metric-card span {
  font-size: 28px;
  font-weight: 850;
  color: var(--text-main);
}
.metric-card label {
  display: block;
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-muted);
}
</style>