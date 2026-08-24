<template>
  <section class="admin-page-section">
    <div class="toolbar">
      <div>
        <p class="eyebrow">{{ eyebrow }}</p>
        <h2>{{ title }}</h2>
      </div>
      <div class="toolbar-actions">
        <el-select v-model="filters.status" clearable :placeholder="adminT('statusAll')" @change="loadItems" @clear="loadItems">
          <el-option :label="adminT('online')" :value="1" />
          <el-option :label="adminT('offline')" :value="0" />
        </el-select>
        <el-button type="primary" @click="editItem(newDraft())">{{ '新建商品' }}</el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="商品管理" name="items">
        <el-table :data="pagedItems" border v-loading="itemsLoading">
          <el-table-column prop="id" :label="adminT('id')" width="80" />
          <el-table-column :label="'商品图片'" width="90">
            <template #default="{ row }">
              <img v-if="field(row, 'imageUrl', 'image_url')" :src="field(row, 'imageUrl', 'image_url')" class="item-thumb" />
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column :label="adminT('name')" min-width="150">
            <template #default="{ row }">{{ field(row, 'name') || '-' }}</template>
          </el-table-column>
          <el-table-column :label="'商品类型'" width="100">
            <template #default="{ row }">{{ field(row, 'itemType', 'item_type') || '-' }}</template>
          </el-table-column>
          <el-table-column :label="adminT('points')" width="100">
            <template #default="{ row }">{{ formatNumber(field(row, 'pointsCost', 'points_cost')) }}</template>
          </el-table-column>
          <el-table-column :label="'VIP折扣价'" width="110">
            <template #default="{ row }">{{ formatNumber(field(row, 'vipPointsCost', 'vip_points_cost')) || '-' }}</template>
          </el-table-column>
          <el-table-column :label="'库存'" width="80">
            <template #default="{ row }">
              {{ field(row, 'stock') == null || field(row, 'stock') < 0 ? '无限' : field(row, 'stock') }}
            </template>
          </el-table-column>
          <el-table-column :label="adminT('sort')" width="60">
            <template #default="{ row }">{{ field(row, 'sortOrder', 'sort_order') }}</template>
          </el-table-column>
          <el-table-column :label="adminT('status')" width="100">
            <template #default="{ row }">
              <el-tag :type="Number(field(row, 'status')) === 1 ? 'success' : 'info'">
                {{ Number(field(row, 'status')) === 1 ? adminT('online') : adminT('offline') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="adminT('actions')" width="280">
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
            v-model:current-page="itemPager.page"
            v-model:page-size="itemPager.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="items.length"
            layout="total, sizes, prev, pager, next, jumper"
            background
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="兑换记录" name="orders">
        <div class="toolbar" style="margin-top:0">
          <el-input v-model="orderFilters.userId" :placeholder="adminT('userId')" clearable @keyup.enter="loadOrders" @clear="loadOrders" />
          <el-select v-model="orderFilters.deliveryStatus" clearable :placeholder="'发货状态'" @change="loadOrders" @clear="loadOrders">
            <el-option label="待发货" value="PENDING" />
            <el-option label="已发货" value="SHIPPED" />
            <el-option label="已完成" value="COMPLETED" />
          </el-select>
          <el-button @click="loadOrders">{{ adminT('search') }}</el-button>
        </div>
        <el-table :data="pagedOrders" border v-loading="ordersLoading">
          <el-table-column prop="id" :label="adminT('id')" width="80" />
          <el-table-column :label="adminT('userId')" width="100">
            <template #default="{ row }">{{ field(row, 'userId', 'user_id') }}</template>
          </el-table-column>
          <el-table-column :label="'商品名称'" min-width="150">
            <template #default="{ row }">{{ field(row, 'itemName', 'item_name') || '-' }}</template>
          </el-table-column>
          <el-table-column :label="adminT('points')" width="100">
            <template #default="{ row }">{{ formatNumber(field(row, 'pointsCost', 'points_cost')) }}</template>
          </el-table-column>
          <el-table-column :label="'发货状态'" width="110">
            <template #default="{ row }">
              <el-tag :type="deliveryTagType(field(row, 'deliveryStatus', 'delivery_status'))">
                {{ deliveryLabel(field(row, 'deliveryStatus', 'delivery_status')) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="'兑换时间'" width="160">
            <template #default="{ row }">{{ formatDateTime(field(row, 'createdAt', 'created_at')) }}</template>
          </el-table-column>
          <el-table-column :label="adminT('actions')" width="150">
            <template #default="{ row }">
              <el-button size="small" v-if="field(row, 'deliveryStatus', 'delivery_status') === 'PENDING'" @click="updateDelivery(row, 'SHIPPED')">标记发货</el-button>
              <el-button size="small" v-if="field(row, 'deliveryStatus', 'delivery_status') === 'SHIPPED'" @click="updateDelivery(row, 'COMPLETED')">标记完成</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="orderPager.page"
            v-model:page-size="orderPager.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="orders.length"
            layout="total, sizes, prev, pager, next, jumper"
            background
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 编辑商品对话框 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? adminT('edit') : '新建商品'" width="540px">
      <el-form :model="form" label-width="110px">
        <el-form-item :label="adminT('name')" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="商品类型" required>
          <el-select v-model="form.itemType">
            <el-option label="虚拟商品" value="VIRTUAL" />
            <el-option label="实物商品" value="PHYSICAL" />
          </el-select>
        </el-form-item>
        <el-form-item :label="adminT('points')" required>
          <el-input-number v-model="form.pointsCost" :min="0" />
        </el-form-item>
        <el-form-item label="VIP折扣价">
          <el-input-number v-model="form.vipPointsCost" :min="0" />
        </el-form-item>
        <el-form-item label="商品图片">
          <div class="upload-row">
            <el-input v-model="form.imageUrl" placeholder="图片URL" />
            <el-upload :show-file-list="false" accept="image/*"
              :http-request="options => uploadLocal(options, form, 'imageUrl', 'image')">
              <el-button>上传</el-button>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item label="库存 (-1=无限)">
          <el-input-number v-model="form.stock" :min="-1" />
        </el-form-item>
        <el-form-item :label="adminT('sort')">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ adminT('cancel') }}</el-button>
        <el-button type="primary" @click="submit" :loading="submitLoading">{{ adminT('save') }}</el-button>
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
  formatDateTime: { type: Function, required: true },
  title: { type: String, default: '积分商城' },
  eyebrow: { type: String, default: '交易中心' }
})

const activeTab = ref('items')
const items = ref([])
const itemPager = reactive({ page: 1, pageSize: 10 })
const pagedItems = computed(() => {
  const start = (itemPager.page - 1) * itemPager.pageSize
  return items.value.slice(start, start + itemPager.pageSize)
})
const itemsLoading = ref(false)
const orders = ref([])
const orderPager = reactive({ page: 1, pageSize: 10 })
const pagedOrders = computed(() => {
  const start = (orderPager.page - 1) * orderPager.pageSize
  return orders.value.slice(start, start + orderPager.pageSize)
})
const ordersLoading = ref(false)
const filters = reactive({ status: null })
const orderFilters = reactive({ userId: '', deliveryStatus: '' })

const dialogVisible = ref(false)
const editingId = ref(null)
const submitLoading = ref(false)
const form = reactive({
  name: '', description: '', itemType: 'VIRTUAL', pointsCost: 0,
  vipPointsCost: null, imageUrl: '', stock: -1, sortOrder: 0
})

function newDraft() {
  return { name: '', description: '', itemType: 'VIRTUAL', pointsCost: 0,
    vipPointsCost: null, imageUrl: '', stock: -1, sortOrder: 0 }
}

function deliveryTagType(status) {
  const map = { PENDING: 'warning', SHIPPED: 'primary', COMPLETED: 'success' }
  return map[status] || 'info'
}

function deliveryLabel(status) {
  const map = { PENDING: '待发货', SHIPPED: '已发货', COMPLETED: '已完成' }
  return map[status] || status || '-'
}

async function loadItems() {
  itemsLoading.value = true
  try {
    const params = {}
    if (filters.status !== null && filters.status !== '') params.status = filters.status
    items.value = await api.adminShopItems(params)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    itemsLoading.value = false
  }
}

async function loadOrders() {
  ordersLoading.value = true
  try {
    const params = { limit: 100 }
    if (orderFilters.userId) params.userId = orderFilters.userId
    if (orderFilters.deliveryStatus) params.deliveryStatus = orderFilters.deliveryStatus
    orders.value = await api.adminShopOrders(params)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    ordersLoading.value = false
  }
}

function onTabChange(tab) {
  if (tab === 'items') loadItems()
  else if (tab === 'orders') loadOrders()
}

function editItem(row) {
  editingId.value = row.id || null
  Object.assign(form, {
    name: props.field(row, 'name') || '',
    description: props.field(row, 'description') || '',
    itemType: props.field(row, 'itemType', 'item_type') || 'VIRTUAL',
    pointsCost: props.field(row, 'pointsCost', 'points_cost') || 0,
    vipPointsCost: props.field(row, 'vipPointsCost', 'vip_points_cost') ?? null,
    imageUrl: props.field(row, 'imageUrl', 'image_url') || '',
    stock: props.field(row, 'stock') ?? -1,
    sortOrder: props.field(row, 'sortOrder', 'sort_order') || 0
  })
  dialogVisible.value = true
}

async function submit() {
  submitLoading.value = true
  try {
    const data = { ...form }
    if (editingId.value) {
      await api.adminShopUpdateItem(editingId.value, data)
      ElMessage.success(props.adminT('updateSuccess'))
    } else {
      await api.adminShopCreateItem(data)
      ElMessage.success(props.adminT('createSuccess'))
    }
    dialogVisible.value = false
    await loadItems()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    submitLoading.value = false
  }
}

async function toggleStatus(row) {
  try {
    const newStatus = Number(props.field(row, 'status')) === 1 ? 0 : 1
    await api.adminShopUpdateItemStatus(row.id, newStatus)
    await loadItems()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function confirmDelete(row) {
  try {
    await ElMessageBox.confirm('确认删除该商品？', props.adminT('delete'), { type: 'warning' })
    await api.adminShopDeleteItem(row.id)
    ElMessage.success(props.adminT('deleteSuccess'))
    await loadItems()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || e)
  }
}

async function updateDelivery(row, status) {
  try {
    await api.adminShopUpdateDelivery(row.id, { deliveryStatus: status })
    ElMessage.success('更新成功')
    await loadOrders()
  } catch (e) {
    ElMessage.error(e.message)
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

onMounted(loadItems)
</script>

<style scoped>
.item-thumb {
  width: 60px;
  height: 60px;
  object-fit: cover;
  border-radius: 4px;
}

:deep(.el-tab-pane) .pagination-wrap {
  margin-bottom: -16px;
}
</style>