<template>
  <section class="admin-page-section">
    <div class="toolbar">
      <div>
        <p class="eyebrow">{{ eyebrow }}</p>
        <h2>{{ title }}</h2>
      </div>
      <div class="toolbar-actions">
        <el-input
          v-model="filters.dramaId"
          :placeholder="adminT('dramaIdPlaceholder')"
          clearable
          @keyup.enter="load"
          @clear="load"
        />
        <el-select
          v-model="filters.status"
          clearable
          :placeholder="adminT('statusAll')"
          @change="load"
          @clear="load"
        >
          <el-option :label="adminT('commentStatusActive')" :value="1" />
          <el-option :label="adminT('commentStatusUserDeleted')" :value="-1" />
          <el-option :label="adminT('commentStatusAdminDeleted')" :value="-2" />
        </el-select>
        <el-button @click="load">{{ adminT('refresh') }}</el-button>
        <el-button
          type="danger"
          :disabled="!selectedIds.length"
          @click="confirmBatchDelete"
        >
          {{ adminT('batchDelete') }}<span v-if="selectedIds.length"> ({{ selectedIds.length }})</span>
        </el-button>
      </div>
    </div>

      <el-table
      :data="records"
      border
      v-loading="loading"
      style="width: 100%"
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="42" />
      <el-table-column prop="id" :label="adminT('id')" width="80" />
      <el-table-column :label="adminT('commentUser')" width="180">
        <template #default="{ row }">
          <div class="user-cell">
            <el-avatar v-if="row.avatar_url" :src="row.avatar_url" :size="28" />
            <el-avatar v-else :size="28">{{ (row.nickname || '匿名')[0] }}</el-avatar>
            <span class="user-nick">{{ row.nickname || '匿名' }}</span>
            <span class="user-id">#{{ row.user_id }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column :label="adminT('commentDrama')" width="220">
        <template #default="{ row }">
          <span class="drama-title">{{ row.drama_title || '未知剧集' }}</span>
          <span v-if="row.episode_no" class="episode-tag">第{{ row.episode_no }}集</span>
        </template>
      </el-table-column>
      <el-table-column :label="adminT('commentParent')" width="120">
        <template #default="{ row }">
          <span v-if="row.parent_id" class="parent-tag">回复 #{{ row.parent_id }}</span>
          <span v-else class="root-tag">{{ adminT('commentRoot') }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="adminT('commentContent')" min-width="280">
        <template #default="{ row }">
          <div class="comment-content">{{ row.content }}</div>
        </template>
      </el-table-column>
      <el-table-column :label="adminT('commentReplyCount')" width="100">
        <template #default="{ row }">
          {{ row.reply_count ?? 0 }}
        </template>
      </el-table-column>
      <el-table-column :label="adminT('createdAt')" width="160">
        <template #default="{ row }">
          {{ formatDateTime(row.created_at) }}
        </template>
      </el-table-column>
      <el-table-column :label="adminT('status')" width="120">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="adminT('actions')" width="220">
        <template #default="{ row }">
          <div class="action-btns">
            <el-button
              v-if="Number(row.status) === 1"
              size="small"
              type="danger"
              @click="confirmDelete(row)"
            >{{ adminT('delete') }}</el-button>
            <template v-else>
              <el-button
                size="small"
                type="success"
                @click="confirmRestore(row)"
              >{{ adminT('restore') }}</el-button>
              <el-button
                size="small"
                type="danger"
                @click="confirmDelete(row)"
              >{{ adminT('delete') }}</el-button>
            </template>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="pager.page"
        v-model:page-size="pager.pageSize"
        :page-sizes="[20, 50, 100, 200]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="load"
        @size-change="onSizeChange"
      />
    </div>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api.js'

const props = defineProps({
  adminT: { type: Function, required: true },
  field: { type: Function, required: true },
  formatDateTime: { type: Function, required: true },
  title: { type: String, default: '评论控评' },
  eyebrow: { type: String, default: '内容管理' }
})

const records = ref([])
const total = ref(0)
const loading = ref(false)
const selectedIds = ref([])
const filters = reactive({ dramaId: '', status: null })
const pager = reactive({ page: 1, pageSize: 20 })

async function load() {
  loading.value = true
  try {
    const params = {
      page: pager.page,
      pageSize: pager.pageSize
    }
    if (filters.dramaId) params.dramaId = filters.dramaId
    if (filters.status !== null && filters.status !== '') params.status = filters.status
    const data = await api.adminComments(params)
    records.value = data.records || []
    total.value = Number(data.total || 0)
  } catch (e) {
    ElMessage.error(e.message || String(e))
  } finally {
    loading.value = false
  }
}

function onSizeChange() {
  pager.page = 1
  load()
}

function onSelectionChange(rows) {
  selectedIds.value = rows.map(r => r.id)
}

function statusTagType(status) {
  if (Number(status) === 1) return 'success'
  if (Number(status) === -1) return 'info'
  if (Number(status) === -2) return 'danger'
  return 'info'
}

function statusLabel(status) {
  if (Number(status) === 1) return props.adminT('commentStatusActive')
  if (Number(status) === -1) return props.adminT('commentStatusUserDeleted')
  if (Number(status) === -2) return props.adminT('commentStatusAdminDeleted')
  return '-'
}

async function confirmDelete(row) {
  try {
    await ElMessageBox.confirm(
      props.adminT('commentDeleteConfirm'),
      props.adminT('delete'),
      { type: 'warning', confirmButtonText: props.adminT('delete'), cancelButtonText: props.adminT('cancel') }
    )
    await api.adminDeleteComment(row.id)
    ElMessage.success(props.adminT('deleteSuccess'))
    await load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || String(e))
  }
}

async function confirmRestore(row) {
  try {
    await ElMessageBox.confirm(
      props.adminT('commentRestoreConfirm'),
      props.adminT('restore'),
      { type: 'info', confirmButtonText: props.adminT('restore'), cancelButtonText: props.adminT('cancel') }
    )
    await api.adminRestoreComment(row.id)
    ElMessage.success(props.adminT('restoreSuccess'))
    await load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || String(e))
  }
}

async function confirmBatchDelete() {
  if (!selectedIds.value.length) return
  try {
    await ElMessageBox.confirm(
      props.adminT('commentBatchDeleteConfirm', [selectedIds.value.length]),
      props.adminT('batchDelete'),
      { type: 'warning', confirmButtonText: props.adminT('delete'), cancelButtonText: props.adminT('cancel') }
    )
    const res = await api.adminBatchDeleteComments(selectedIds.value)
    ElMessage.success(props.adminT('batchDeleteSuccess', [res.deleted || 0]))
    selectedIds.value = []
    await load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || String(e))
  }
}

onMounted(load)
</script>

<style scoped>
.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.user-nick {
  font-weight: 600;
  color: #303133;
}
.user-id {
  color: #909399;
  font-size: 12px;
}
.drama-title {
  color: #303133;
  font-size: 13px;
  font-weight: 600;
  display: block;
  margin-bottom: 4px;
}
.episode-tag {
  color: #409eff;
  font-size: 12px;
  background: #ecf5ff;
  padding: 2px 8px;
  border-radius: 4px;
}
.parent-tag {
  color: #e6a23c;
  font-size: 12px;
}
.root-tag {
  color: #67c23a;
  font-size: 12px;
}
.comment-content {
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.5;
  color: #303133;
}
.pagination-wrap {
  margin-top: 16px;
  text-align: right;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
  gap: 12px;
  flex-wrap: wrap;
}
.toolbar-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.eyebrow {
  margin: 0;
  color: #909399;
  font-size: 12px;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}
.toolbar h2 {
  margin: 4px 0 0;
  font-size: 20px;
  font-weight: 700;
  color: #303133;
}
.action-btns {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: nowrap;
}
</style>
