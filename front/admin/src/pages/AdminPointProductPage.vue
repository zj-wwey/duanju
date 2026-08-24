<template>
  <section class="admin-page-section">
    <div class="toolbar">
      <div>
        <p class="eyebrow">{{ eyebrow }}</p>
        <h2>{{ title }}</h2>
      </div>
      <div class="toolbar-actions">
        <el-input v-model="filters.keyword" :placeholder="adminT('keyword')" clearable @keyup.enter="load" @clear="load" />
        <el-select v-model="filters.type" clearable :placeholder="'套餐类型'" @change="load" @clear="load">
          <el-option label="VIP日卡" value="DAILY" />
          <el-option label="VIP周卡" value="WEEKLY" />
          <el-option label="VIP月卡" value="MONTHLY" />
          <el-option label="积分充值" value="RECHARGE" />
        </el-select>
        <el-select v-model="filters.status" clearable :placeholder="adminT('statusAll')" @change="load" @clear="load">
          <el-option :label="adminT('online')" :value="1" />
          <el-option :label="adminT('offline')" :value="0" />
        </el-select>
        <el-button type="primary" @click="editItem(newDraft())">{{ adminT('pointProductCreate') }}</el-button>
      </div>
    </div>
    <el-table :data="pagedItems" border>
      <el-table-column prop="id" :label="adminT('id')" width="80" />
      <el-table-column :label="adminT('name')" min-width="150">
        <template #default="{ row }">{{ field(row, 'name') || '-' }}</template>
      </el-table-column>
      <el-table-column :label="adminT('packageType')" width="100">
        <template #default="{ row }">{{ packageTypeLabel(field(row, 'packageType', 'package_type')) }}</template>
      </el-table-column>
      <el-table-column :label="'会员等级'" width="100">
        <template #default="{ row }">{{ field(row, 'membershipLevel', 'membership_level') || '-' }}</template>
      </el-table-column>
      <el-table-column :label="adminT('points')" width="100">
        <template #default="{ row }">{{ formatNumber(field(row, 'points')) }}</template>
      </el-table-column>
      <el-table-column :label="'赠送积分'" width="100">
        <template #default="{ row }">{{ formatNumber(field(row, 'bonusPoints', 'bonus_points')) }}</template>
      </el-table-column>
      <el-table-column :label="adminT('amount')" width="110">
        <template #default="{ row }">{{ formatMoney(field(row, 'priceCents', 'price_cents'), field(row, 'currency') || 'USD') }}</template>
      </el-table-column>
      <el-table-column :label="adminT('durationDays')" width="100">
        <template #default="{ row }">{{ field(row, 'durationDays', 'duration_days') || '-' }}</template>
      </el-table-column>
      <el-table-column :label="'每日限额'" width="90">
        <template #default="{ row }">{{ field(row, 'dailyLimit', 'daily_limit') ?? '-' }}</template>
      </el-table-column>
      <el-table-column :label="'每月限额'" width="90">
        <template #default="{ row }">{{ field(row, 'monthlyLimit', 'monthly_limit') ?? '-' }}</template>
      </el-table-column>
      <el-table-column :label="adminT('tagText')" width="100">
        <template #default="{ row }">{{ field(row, 'tagText', 'tag_text') || '-' }}</template>
      </el-table-column>
      <el-table-column :label="'首充赠送'" width="110">
        <template #default="{ row }">{{ formatNumber(field(row, 'firstPurchaseBonus', 'first_purchase_bonus')) }}</template>
      </el-table-column>
      <el-table-column :label="adminT('sort')" width="70">
        <template #default="{ row }">{{ field(row, 'sortOrder', 'sort_order') }}</template>
      </el-table-column>
      <el-table-column :label="adminT('status')" width="100">
        <template #default="{ row }">
          <el-tag :type="Number(field(row, 'status')) === 1 ? 'success' : 'info'">
            {{ Number(field(row, 'status')) === 1 ? adminT('online') : adminT('offline') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="adminT('actions')" width="300">
        <template #default="{ row }">
          <el-button size="small" @click="editItem(row)">{{ adminT('edit') }}</el-button>
          <el-button size="small" :type="Number(field(row, 'status')) === 1 ? 'warning' : 'success'" @click="toggleStatus(row)">
            {{ Number(field(row, 'status')) === 1 ? adminT('takeOffline') : adminT('putOnline') }}
          </el-button>
          <el-button size="small" type="danger" @click="confirmDelete(row)">{{ adminT('delete') }}</el-button>
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

    <!-- 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? adminT('edit') : adminT('pointProductCreate')" width="600px">
      <el-form :model="form" label-width="120px">
        <el-form-item :label="adminT('name')" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="adminT('packageType')" required>
          <el-select v-model="form.packageType">
            <el-option label="积分充值" value="RECHARGE" />
            <el-option label="VIP" value="VIP" />
            <el-option label="VIP日卡" value="VIP_DAILY" />
            <el-option label="VIP周卡" value="VIP_WEEKLY" />
            <el-option label="VIP月卡" value="VIP_MONTHLY" />
            <el-option label="VIP季卡" value="VIP_QUARTERLY" />
            <el-option label="VIP年卡" value="VIP_YEARLY" />
          </el-select>
        </el-form-item>
        <el-form-item label="会员等级">
          <el-select v-model="form.membershipLevel" clearable placeholder="无">
            <el-option label="无" value="" />
            <el-option label="白银" value="SILVER" />
            <el-option label="黄金" value="GOLD" />
            <el-option label="钻石" value="DIAMOND" />
          </el-select>
        </el-form-item>
        <el-form-item :label="adminT('points')" required>
          <el-input-number v-model="form.points" :min="0" />
        </el-form-item>
        <el-form-item :label="'赠送积分'">
          <el-input-number v-model="form.bonusPoints" :min="0" />
        </el-form-item>
        <el-form-item :label="'金额(分)'" required>
          <el-input-number v-model="form.priceCents" :min="0" :step="100" />
        </el-form-item>
        <el-form-item :label="'原价(分)'">
          <el-input-number v-model="form.originalPriceCents" :min="0" :step="100" />
        </el-form-item>
        <el-form-item :label="'货币'">
          <el-input v-model="form.currency" placeholder="USD" />
        </el-form-item>
        <el-form-item :label="adminT('durationDays')">
          <el-input-number v-model="form.durationDays" :min="0" />
        </el-form-item>
        <el-form-item :label="'每日限额'">
          <el-input-number v-model="form.dailyLimit" :min="0" />
        </el-form-item>
        <el-form-item :label="'每月限额'">
          <el-input-number v-model="form.monthlyLimit" :min="0" />
        </el-form-item>
        <el-form-item :label="'首充赠送'">
          <el-input-number v-model="form.firstPurchaseBonus" :min="0" />
        </el-form-item>
        <el-form-item :label="'商店产品ID'">
          <el-input v-model="form.storeProductId" />
        </el-form-item>
        <el-form-item :label="adminT('tagText')">
          <el-input v-model="form.tagText" />
        </el-form-item>
        <el-form-item :label="adminT('sort')">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="产品分类">
          <el-select v-model="form.productCategory">
            <el-option label="积分充值" value="RECHARGE" />
            <el-option label="VIP" value="VIP" />
            <el-option label="积分兑换" value="EXCHANGE" />
          </el-select>
        </el-form-item>
        <el-form-item label="封面图片">
          <div class="upload-row">
            <el-input v-model="form.coverUrl" />
            <el-upload :show-file-list="false" accept="image/*"
              :http-request="options => uploadLocal(options, form, 'coverUrl', 'image')">
              <el-button>上传</el-button>
            </el-upload>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ adminT('cancel') }}</el-button>
        <el-button type="primary" @click="submit">{{ adminT('save') }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api.js'

const props = defineProps({
  adminT: { type: Function, required: true },
  field: { type: Function, required: true },
  formatNumber: { type: Function, required: true },
  formatMoney: { type: Function, required: true },
  title: { type: String, default: '积分商品管理' },
  eyebrow: { type: String, default: '交易中心' }
})

const items = ref([])
const pager = reactive({ page: 1, pageSize: 10 })
const pagedItems = computed(() => {
  const start = (pager.page - 1) * pager.pageSize
  return items.value.slice(start, start + pager.pageSize)
})
const dialogVisible = ref(false)
const editingId = ref(null)
const filters = reactive({ keyword: '', type: '', status: null })
const form = reactive({
  name: '', packageType: 'RECHARGE', membershipLevel: '', points: 0, bonusPoints: 0,
  priceCents: 0, originalPriceCents: null, currency: 'USD', durationDays: null,
  dailyLimit: null, monthlyLimit: null, firstPurchaseBonus: 0, storeProductId: '',
  tagText: '', sortOrder: 0, productCategory: 'RECHARGE', coverUrl: ''
})

function newDraft() {
  return { name: '', packageType: 'RECHARGE', membershipLevel: '', points: 0, bonusPoints: 0,
    priceCents: 0, originalPriceCents: null, currency: 'USD', durationDays: null,
    dailyLimit: null, monthlyLimit: null, firstPurchaseBonus: 0, storeProductId: '',
    tagText: '', sortOrder: 0, productCategory: 'RECHARGE', coverUrl: '' }
}

function packageTypeLabel(type) {
  const map = { DAILY: 'VIP日卡', WEEKLY: 'VIP周卡', MONTHLY: 'VIP月卡', QUARTERLY: 'VIP季卡', YEARLY: 'VIP年卡', RECHARGE: '积分充值', VIP: 'VIP', VIP_DAILY: 'VIP日卡', VIP_WEEKLY: 'VIP周卡', VIP_MONTHLY: 'VIP月卡', VIP_QUARTERLY: 'VIP季卡', VIP_YEARLY: 'VIP年卡' }
  return map[type] || type || '-'
}

async function load() {
  try {
    const params = {}
    if (filters.type) params.packageType = filters.type
    if (filters.status !== null && filters.status !== '') params.status = filters.status
    if (filters.keyword) params.keyword = filters.keyword
    items.value = await api.adminPointProducts(params)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function editItem(row) {
  editingId.value = row.id || null
  Object.assign(form, {
    name: props.field(row, 'name') || '',
    packageType: props.field(row, 'packageType', 'package_type') || 'RECHARGE',
    membershipLevel: props.field(row, 'membershipLevel', 'membership_level') || '',
    points: props.field(row, 'points') || 0,
    bonusPoints: props.field(row, 'bonusPoints', 'bonus_points') || 0,
    priceCents: props.field(row, 'priceCents', 'price_cents') || 0,
    originalPriceCents: props.field(row, 'originalPriceCents', 'original_price_cents') ?? null,
    currency: props.field(row, 'currency') || 'USD',
    durationDays: props.field(row, 'durationDays', 'duration_days') ?? null,
    dailyLimit: props.field(row, 'dailyLimit', 'daily_limit') ?? null,
    monthlyLimit: props.field(row, 'monthlyLimit', 'monthly_limit') ?? null,
    firstPurchaseBonus: props.field(row, 'firstPurchaseBonus', 'first_purchase_bonus') || 0,
    storeProductId: props.field(row, 'storeProductId', 'store_product_id') || '',
    tagText: props.field(row, 'tagText', 'tag_text') || '',
    sortOrder: props.field(row, 'sortOrder', 'sort_order') || 0,
    productCategory: props.field(row, 'productCategory', 'product_category') || 'RECHARGE',
    coverUrl: props.field(row, 'coverUrl', 'cover_url') || ''
  })
  dialogVisible.value = true
}

async function submit() {
  try {
    if (editingId.value) {
      await api.updatePointProduct(editingId.value, { ...form })
      ElMessage.success(props.adminT('updateSuccess'))
    } else {
      await api.createPointProduct({ ...form })
      ElMessage.success(props.adminT('createSuccess'))
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function toggleStatus(row) {
  try {
    const newStatus = Number(props.field(row, 'status')) === 1 ? 0 : 1
    await api.updatePointProductStatus(row.id, newStatus)
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function confirmDelete(row) {
  try {
    await ElMessageBox.confirm('确认删除该积分商品？', props.adminT('delete'), { type: 'warning' })
    await api.deletePointProduct(row.id)
    ElMessage.success(props.adminT('deleteSuccess'))
    await load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || e)
  }
}

async function uploadLocal(options, target, fieldName, type) {
  try {
    const data = await api.uploadStorage(options.file, type)
    target[fieldName] = data.url
    options.onSuccess?.(data)
    ElMessage.success('上传成功')
  } catch (err) {
    options.onError?.(err)
    ElMessage.error(err.message || String(err))
  }
}

onMounted(load)
</script>