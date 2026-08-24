<template>
  <section class="admin-page-section">
    <div class="toolbar">
      <div>
        <p class="eyebrow">{{ eyebrow }}</p>
        <h2>{{ title }}</h2>
      </div>
      <div class="toolbar-actions">
        <el-input v-model="filters.keyword" :placeholder="adminT('keyword')" clearable @keyup.enter="load" @clear="load" />
        <el-select v-model="filters.status" clearable :placeholder="adminT('statusAll')" @change="load" @clear="load">
          <el-option label="SILVER" value="SILVER" />
          <el-option label="GOLD" value="GOLD" />
          <el-option label="DIAMOND" value="DIAMOND" />
        </el-select>
        <el-button @click="load">{{ adminT('search') }}</el-button>
        <el-button type="primary" @click="showGrantDialog()">{{ adminT('grantPoints') }}</el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="metric-grid" v-if="stats">
      <div class="metric-card">
        <span>{{ formatNumber(field(stats, 'totalMembers')) }}</span>
        <label>会员总数</label>
      </div>
      <div class="metric-card">
        <span>{{ formatNumber(field(stats, 'silverCount', 'silver_count')) }}</span>
        <label>SILVER</label>
      </div>
      <div class="metric-card">
        <span>{{ formatNumber(field(stats, 'goldCount', 'gold_count')) }}</span>
        <label>GOLD</label>
      </div>
      <div class="metric-card">
        <span>{{ formatNumber(field(stats, 'diamondCount', 'diamond_count')) }}</span>
        <label>DIAMOND</label>
      </div>
    </div>

    <el-table :data="pagedItems" border v-loading="loading">
      <el-table-column prop="userId" :label="adminT('userId')" width="100" />
      <el-table-column :label="adminT('username')" min-width="120">
        <template #default="{ row }">{{ field(row, 'username') || '-' }}</template>
      </el-table-column>
      <el-table-column :label="'会员等级'" width="110">
        <template #default="{ row }">
          <el-tag :type="levelTagType(field(row, 'membershipLevel', 'membership_level'))">
            {{ field(row, 'membershipLevel', 'membership_level') || 'NONE' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="adminT('points')" width="100">
        <template #default="{ row }">{{ formatNumber(field(row, 'points')) }}</template>
      </el-table-column>
      <el-table-column :label="adminT('vipStatus')" width="110">
        <template #default="{ row }">
          <el-tag :type="field(row, 'vipExpireAt', 'vip_expire_at') ? 'success' : 'info'">
            {{ field(row, 'vipExpireAt', 'vip_expire_at') ? 'VIP' : '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="'VIP到期'" width="160">
        <template #default="{ row }">{{ formatDateTime(field(row, 'vipExpireAt', 'vip_expire_at')) || '-' }}</template>
      </el-table-column>
      <el-table-column :label="'成长值'" width="100">
        <template #default="{ row }">{{ formatNumber(field(row, 'growthValue', 'growth_value')) }}</template>
      </el-table-column>
      <el-table-column :label="'累计消费'" width="120">
        <template #default="{ row }">{{ formatMoney(field(row, 'totalSpent', 'total_spent'), field(row, 'currency') || 'USD') }}</template>
      </el-table-column>
      <el-table-column :label="adminT('actions')" width="280">
        <template #default="{ row }">
          <el-button size="small" @click="showDetail(row)">{{ adminT('detail') }}</el-button>
          <el-button size="small" type="warning" @click="showAdjustDialog(row)">调整等级</el-button>
          <el-button size="small" type="primary" @click="showGrantDialog(row)">{{ adminT('grantPoints') }}</el-button>
        </template>
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

    <!-- 会员详情对话框 -->
    <el-dialog v-model="detailVisible" :title="'会员详情'" width="500px">
      <div v-if="detail" class="detail-info">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="用户ID">{{ detail.userId }}</el-descriptions-item>
          <el-descriptions-item label="用户名">{{ detail.username || '-' }}</el-descriptions-item>
          <el-descriptions-item label="会员等级">
            <el-tag :type="levelTagType(detail.membershipLevel || detail.membership_level)">{{ detail.membershipLevel || detail.membership_level || 'NONE' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="积分余额">{{ formatNumber(detail.points) }}</el-descriptions-item>
          <el-descriptions-item label="成长值">{{ formatNumber(detail.growthValue || detail.growth_value) }}</el-descriptions-item>
          <el-descriptions-item label="累计消费">{{ formatMoney(detail.totalSpent || detail.total_spent, detail.currency || 'USD') }}</el-descriptions-item>
          <el-descriptions-item label="VIP状态">{{ detail.vipExpireAt || detail.vip_expire_at ? 'VIP (到期: ' + formatDateTime(detail.vipExpireAt || detail.vip_expire_at) + ')' : '非VIP' }}</el-descriptions-item>
          <el-descriptions-item label="注册时间">{{ formatDateTime(detail.createdAt || detail.created_at) }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>

    <!-- 调整等级对话框 -->
    <el-dialog v-model="adjustVisible" :title="'调整会员等级'" width="400px">
      <el-form :model="adjustForm" label-width="100px">
        <el-form-item label="用户">
          <span>{{ adjustForm.username || adjustForm.userId }}</span>
        </el-form-item>
        <el-form-item label="新等级" required>
          <el-select v-model="adjustForm.level">
            <el-option label="SILVER" value="SILVER" />
            <el-option label="GOLD" value="GOLD" />
            <el-option label="DIAMOND" value="DIAMOND" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">{{ adminT('cancel') }}</el-button>
        <el-button type="primary" @click="submitAdjust" :loading="adjustLoading">{{ adminT('save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 赠送积分对话框 -->
    <el-dialog v-model="grantVisible" :title="'发放积分'" width="400px">
      <el-form :model="grantForm" label-width="100px">
        <el-form-item label="用户ID" v-if="!grantForm.userId">
          <el-input-number v-model="grantForm.userIdInput" :min="1" />
        </el-form-item>
        <el-form-item label="用户" v-else>
          <span>{{ grantForm.username || grantForm.userId }}</span>
        </el-form-item>
        <el-form-item label="积分数" required>
          <el-input-number v-model="grantForm.points" :min="1" :max="999999" />
        </el-form-item>
        <el-form-item label="过期天数">
          <el-input-number v-model="grantForm.expireDays" :min="1" :max="365" />
        </el-form-item>
        <el-form-item label="活动ID">
          <el-input v-model="grantForm.activityId" placeholder="ADMIN_GRANT" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="grantVisible = false">{{ adminT('cancel') }}</el-button>
        <el-button type="primary" @click="submitGrant" :loading="grantLoading">{{ adminT('save') }}</el-button>
      </template>
    </el-dialog>
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
  formatMoney: { type: Function, required: true },
  formatDateTime: { type: Function, required: true },
  title: { type: String, default: '会员管理' },
  eyebrow: { type: String, default: '用户操作' }
})

const items = ref([])
const pager = reactive({ page: 1, pageSize: 10 })
const pagedItems = computed(() => {
  const start = (pager.page - 1) * pager.pageSize
  return items.value.slice(start, start + pager.pageSize)
})
const stats = ref(null)
const loading = ref(false)
const filters = reactive({ keyword: '', status: '' })

// 详情
const detailVisible = ref(false)
const detail = ref(null)

// 调整等级
const adjustVisible = ref(false)
const adjustLoading = ref(false)
const adjustForm = reactive({ userId: null, username: '', level: 'SILVER' })

// 赠送积分
const grantVisible = ref(false)
const grantLoading = ref(false)
const grantForm = reactive({ userId: null, userIdInput: null, username: '', points: 100, expireDays: 90, activityId: 'ADMIN_GRANT' })

function levelTagType(level) {
  const map = { SILVER: '', GOLD: 'warning', DIAMOND: 'danger' }
  return map[level] || 'info'
}

async function load() {
  loading.value = true
  try {
    const params = { limit: 200 }
    if (filters.keyword) params.keyword = filters.keyword
    if (filters.status) params.status = filters.status
    items.value = await api.adminMembershipList(params)
    stats.value = await api.adminMembershipStats()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function showDetail(row) {
  try {
    detail.value = await api.adminMembershipDetail(row.userId)
    detailVisible.value = true
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function showAdjustDialog(row) {
  adjustForm.userId = row.userId
  adjustForm.username = props.field(row, 'username') || ''
  adjustForm.level = props.field(row, 'membershipLevel', 'membership_level') || 'SILVER'
  adjustVisible.value = true
}

async function submitAdjust() {
  adjustLoading.value = true
  try {
    await api.adminMembershipAdjust(adjustForm.userId, { level: adjustForm.level })
    ElMessage.success('调整成功')
    adjustVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    adjustLoading.value = false
  }
}

function showGrantDialog(row) {
  if (row) {
    grantForm.userId = row.userId
    grantForm.username = props.field(row, 'username') || ''
    grantForm.userIdInput = null
  } else {
    grantForm.userId = null
    grantForm.username = ''
    grantForm.userIdInput = null
  }
  grantForm.points = 100
  grantForm.expireDays = 90
  grantForm.activityId = 'ADMIN_GRANT'
  grantVisible.value = true
}

async function submitGrant() {
  const userId = grantForm.userId || grantForm.userIdInput
  if (!userId) {
    ElMessage.error('请输入用户ID')
    return
  }
  grantLoading.value = true
  try {
    await api.adminGrantPoints({
      userId,
      points: grantForm.points,
      expireDays: grantForm.expireDays,
      activityId: grantForm.activityId
    })
    ElMessage.success('积分发放成功')
    grantVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    grantLoading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
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
.detail-info {
  padding: 8px 0;
}
</style>