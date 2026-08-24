<template>
  <section class="admin-page-section">
    <div class="toolbar">
      <div>
        <p class="eyebrow">{{ eyebrow }}</p>
        <h2>{{ title }}</h2>
      </div>
      <div class="toolbar-actions">
        <el-select v-model="filters.status" clearable :placeholder="adminT('statusAll')" @change="load" @clear="load">
          <el-option label="活跃" value="ACTIVE" />
          <el-option label="已取消" value="CANCELLED" />
          <el-option label="已过期" value="EXPIRED" />
        </el-select>
        <el-button @click="load">{{ adminT('search') }}</el-button>
      </div>
    </div>

    <el-table :data="pagedItems" border v-loading="loading">
      <el-table-column prop="id" :label="adminT('id')" width="80" />
      <el-table-column :label="adminT('userId')" width="100">
        <template #default="{ row }">{{ field(row, 'userId', 'user_id') }}</template>
      </el-table-column>
      <el-table-column :label="'套餐ID'" width="100">
        <template #default="{ row }">{{ field(row, 'productId', 'product_id') || '-' }}</template>
      </el-table-column>
      <el-table-column :label="'支付渠道'" width="110">
        <template #default="{ row }">{{ field(row, 'payChannel', 'pay_channel') || '-' }}</template>
      </el-table-column>
      <el-table-column :label="'状态'" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTagType(field(row, 'status'))">
            {{ statusLabel(field(row, 'status')) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="'失败次数'" width="90">
        <template #default="{ row }">{{ field(row, 'failCount', 'fail_count') || 0 }}/3</template>
      </el-table-column>
      <el-table-column :label="'下次扣款时间'" width="170">
        <template #default="{ row }">{{ formatDateTime(field(row, 'nextChargeAt', 'next_charge_at')) || '-' }}</template>
      </el-table-column>
      <el-table-column :label="'创建时间'" width="170">
        <template #default="{ row }">{{ formatDateTime(field(row, 'createdAt', 'created_at')) }}</template>
      </el-table-column>
      <el-table-column :label="'更新时间'" width="170">
        <template #default="{ row }">{{ formatDateTime(field(row, 'updatedAt', 'updated_at')) }}</template>
      </el-table-column>
      <el-table-column :label="'操作'" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="field(row, 'status') === 'ACTIVE'"
            type="danger"
            size="small"
            @click="cancelRenewal(row)"
          >
            取消续费
          </el-button>
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
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api.js'

const props = defineProps({
  adminT: { type: Function, required: true },
  field: { type: Function, required: true },
  formatDateTime: { type: Function, required: true },
  title: { type: String, default: '自动续费管理' },
  eyebrow: { type: String, default: '交易中心' }
})

const items = ref([])
const pager = reactive({ page: 1, pageSize: 10 })
const pagedItems = computed(() => {
  const start = (pager.page - 1) * pager.pageSize
  return items.value.slice(start, start + pager.pageSize)
})
const loading = ref(false)
const filters = reactive({ status: '' })

function statusTagType(status) {
  const map = { ACTIVE: 'success', CANCELLED: 'warning', EXPIRED: 'info' }
  return map[status] || 'info'
}

function statusLabel(status) {
  const map = { ACTIVE: '活跃', CANCELLED: '已取消', EXPIRED: '已过期' }
  return map[status] || status || '-'
}

async function load() {
  loading.value = true
  try {
    const params = { limit: 200 }
    if (filters.status) params.status = filters.status
    items.value = await api.adminAutoRenewalList(params)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function cancelRenewal(row) {
  const userId = props.field(row, 'userId', 'user_id')
  if (!userId) return
  try {
    await ElMessageBox.confirm('确认取消该用户的自动续费？此操作不可撤销。', '取消自动续费', { type: 'warning' })
    await api.adminAutoRenewalCancel(userId)
    ElMessage.success('已取消自动续费')
    load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || e)
  }
}

onMounted(load)
</script>