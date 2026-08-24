<template>
  <div class="admin-profile-page">
    <div class="profile-layout">
      <!-- 顶部用户卡片 -->
      <div class="profile-user-card">
        <div class="avatar-frame">
          <img v-if="profile.avatar" :src="profile.avatar" alt="头像" />
          <span v-else>{{ (profile.nickname || profile.username || 'A')[0] }}</span>
        </div>
        <div class="user-info">
          <h1>{{ profile.nickname || profile.username || t('admin.role') }}</h1>
          <p class="user-id">ID: {{ profile.id || '-' }}</p>
          <div class="role-badge">{{ t('admin.role') }}</div>
        </div>
      </div>

      <!-- 主内容区 -->
      <div class="profile-main">
        <!-- 个人信息 -->
        <div v-if="activePanel === 'profile'" class="profile-panel">
          <h2 class="panel-title">{{ t('admin.profileInfo') }}</h2>
          
          <div class="form-row">
            <div class="avatar-editor">
              <div class="avatar-preview">
                <img v-if="avatarPreview" :src="avatarPreview" alt="头像预览" />
                <span v-else>{{ (profile.nickname || profile.username || 'A')[0] }}</span>
              </div>
              <input ref="avatarInputRef" type="file" accept="image/*" hidden @change="selectAvatar" />
              <button class="btn-ghost" @click="avatarInputRef?.click()">{{ t('admin.uploadAvatar') }}</button>
            </div>

            <div class="form-fields">
              <div class="form-item">
                <label>{{ t('admin.nickname') }}</label>
                <input v-model="profileForm.nickname" type="text" maxlength="24" :placeholder="t('admin.enterNickname')" />
              </div>
              <div class="form-item">
                <label>{{ t('admin.username') }}</label>
                <input :value="profile.username || '-'" type="text" disabled />
              </div>
              <div class="form-item">
                <label>{{ t('admin.adminId') }}</label>
                <input :value="profile.id || '-'" type="text" disabled />
              </div>
              <div class="form-item">
                <label>{{ t('admin.joinTime') }}</label>
                <input :value="formatDate(profile.createdAt)" type="text" disabled />
              </div>
            </div>
          </div>

          <div class="form-actions">
            <button class="btn-primary" :disabled="saving" @click="saveProfile">
              {{ saving ? t('admin.saving') : t('admin.saveChanges') }}
            </button>
          </div>
        </div>

        <!-- 修改密码 -->
        <div v-else-if="activePanel === 'password'" class="profile-panel">
          <h2 class="panel-title">{{ t('admin.changePassword') }}</h2>
          <div class="form-fields center">
            <div class="form-item">
              <label><em>*</em>{{ t('admin.oldPassword') }}</label>
              <input v-model="passwordForm.oldPassword" type="password" :placeholder="t('admin.enterOldPassword')" />
            </div>
            <div class="form-item">
              <label><em>*</em>{{ t('admin.newPassword') }}</label>
              <input v-model="passwordForm.newPassword" type="password" :placeholder="t('admin.enterNewPassword')" />
            </div>
            <div class="form-item">
              <label><em>*</em>{{ t('admin.confirmPassword') }}</label>
              <input v-model="passwordForm.confirmPassword" type="password" :placeholder="t('admin.enterNewPasswordAgain')" />
            </div>
          </div>

          <div class="form-actions">
            <button class="btn-primary" :disabled="changingPassword" @click="changePassword">
              {{ changingPassword ? t('admin.changing') : t('admin.confirmChange') }}
            </button>
            <button class="btn-ghost" @click="resetPassword">{{ t('admin.reset') }}</button>
          </div>
        </div>
      </div>

      <!-- 底部菜单 -->
      <nav class="profile-menu">
        <button
          :class="{ active: activePanel === 'profile' }"
          type="button"
          @click="activePanel = 'profile'"
        >
          <span>01</span>
          {{ t('admin.menuProfile') }}
        </button>
        <button
          :class="{ active: activePanel === 'password' }"
          type="button"
          @click="activePanel = 'password'"
        >
          <span>02</span>
          {{ t('admin.menuPassword') }}
        </button>
      </nav>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api.js'
import { formatDate as formatDateUtil, validatePassword as validatePasswordUtil } from '../utils/helpers.js'
import { useStreamI18n } from '../locales/streamI18n.js'

const { t, locale } = useStreamI18n()

const profile = ref({})
const saving = ref(false)
const changingPassword = ref(false)
const avatarInputRef = ref(null)
const activePanel = ref('profile')
const avatarPreview = ref('')

const profileForm = reactive({
  nickname: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

onMounted(() => {
  loadProfile()
})

async function loadProfile() {
  try {
    const res = await api.getAdminProfile()
    profile.value = res || {}
    profileForm.nickname = res?.nickname || ''
    avatarPreview.value = res?.avatar || ''
  } catch (e) {
    console.error('Load profile failed:', e)
  }
}

async function saveProfile() {
  if (!profileForm.nickname) {
    ElMessage.warning(t('admin.enterNickname'))
    return
  }
  saving.value = true
  try {
    await api.updateAdminProfile({
      nickname: profileForm.nickname,
      avatar: avatarPreview.value
    })
    ElMessage.success(t('admin.saveSuccess'))
    loadProfile()
  } catch (e) {
    ElMessage.error(e.message || t('admin.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function changePassword() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    ElMessage.warning(t('common.fillAllFields'))
    return
  }
  if (!validatePasswordUtil(passwordForm.newPassword)) {
    ElMessage.warning(t('admin.passwordRule'))
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning(t('admin.passwordMismatch'))
    return
  }

  changingPassword.value = true
  try {
    await api.changeAdminPassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
      confirmPassword: passwordForm.confirmPassword
    })
    ElMessage.success(t('admin.passwordChanged'))
    resetPassword()
  } catch (e) {
    ElMessage.error(e.message || t('admin.passwordChangeFailed'))
  } finally {
    changingPassword.value = false
  }
}

function resetPassword() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
}

function selectAvatar(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning(t('admin.selectImage'))
    return
  }

  const reader = new FileReader()
  reader.onload = () => {
    avatarPreview.value = reader.result
  }
  reader.readAsDataURL(file)
}

function formatDate(dateStr) {
  return formatDateUtil(dateStr, locale.value)
}
</script>

<style scoped>
.admin-profile-page {
  padding: 0;
}

.profile-layout {
  display: flex;
  flex-direction: column;
  gap: 0;
}

/* 用户卡片 */
.profile-user-card {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 32px 40px;
  background: linear-gradient(180deg, rgba(212, 175, 104, 0.12), rgba(212, 175, 104, 0.04));
  border: 1px solid rgba(212, 175, 104, 0.2);
  border-radius: 16px;
  margin-bottom: 0;
}

.avatar-frame {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
  background: linear-gradient(135deg, #d4af68, #d4a84b);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 2px solid rgba(212, 175, 104, 0.4);
}

.avatar-frame img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-frame span {
  font-size: 32px;
  font-weight: 700;
  color: #171513;
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.user-info h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #fff;
}

.user-id {
  margin: 0;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.5);
}

.role-badge {
  display: inline-block;
  width: fit-content;
  padding: 4px 14px;
  margin-top: 4px;
  background: rgba(212, 175, 104, 0.2);
  border: 1px solid rgba(212, 175, 104, 0.4);
  border-radius: 16px;
  color: #d4af68;
  font-size: 12px;
  font-weight: 500;
}

/* 主内容区 */
.profile-main {
  padding: 32px 40px;
  background: rgba(255, 255, 255, 0.02);
  border-left: 1px solid rgba(212, 175, 104, 0.15);
  border-right: 1px solid rgba(212, 175, 104, 0.15);
}

.profile-panel {
  max-width: 700px;
}

.panel-title {
  margin: 0 0 28px;
  font-size: 20px;
  font-weight: 600;
  color: #fff;
  padding-bottom: 16px;
  border-bottom: 1px solid rgba(212, 175, 104, 0.15);
}

/* 表单行布局 */
.form-row {
  display: flex;
  gap: 40px;
  align-items: flex-start;
}

.avatar-editor {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.avatar-preview {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  overflow: hidden;
  background: linear-gradient(135deg, #d4af68, #d4a84b);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid rgba(212, 175, 104, 0.3);
}

.avatar-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-preview span {
  font-size: 36px;
  font-weight: 700;
  color: #171513;
}

/* 表单字段 */
.form-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px 32px;
  flex: 1;
}

.form-fields.center {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 450px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-item label {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);
}

.form-item label em {
  color: #d4af68;
  font-style: normal;
  margin-right: 2px;
}

.form-item input {
  width: 100%;
  padding: 10px 14px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(212, 175, 104, 0.15);
  border-radius: 8px;
  color: #fff;
  font-size: 14px;
  transition: all 0.2s ease;
  box-sizing: border-box;
}

.form-item input::placeholder {
  color: rgba(255, 255, 255, 0.25);
}

.form-item input:hover:not(:disabled) {
  border-color: rgba(212, 175, 104, 0.4);
}

.form-item input:focus {
  outline: none;
  border-color: rgba(212, 175, 104, 0.6);
  background: rgba(255, 255, 255, 0.06);
}

.form-item input:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 操作按钮 */
.form-actions {
  display: flex;
  gap: 12px;
  margin-top: 32px;
}

.btn-primary {
  padding: 10px 28px;
  background: linear-gradient(135deg, #d4af68, #d4a84b);
  border: none;
  border-radius: 8px;
  color: #171513;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(212, 175, 104, 0.25);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-ghost {
  padding: 10px 24px;
  background: transparent;
  border: 1px solid rgba(212, 175, 104, 0.3);
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.7);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-ghost:hover {
  background: rgba(212, 175, 104, 0.1);
  color: #fff;
}

/* 底部菜单 */
.profile-menu {
  display: flex;
  gap: 16px;
  padding: 20px 40px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(212, 175, 104, 0.15);
  border-top: none;
  border-radius: 0 0 16px 16px;
}

.profile-menu button {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 20px;
  background: transparent;
  border: 1px solid rgba(212, 175, 104, 0.15);
  border-radius: 10px;
  color: rgba(255, 255, 255, 0.6);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.25s ease;
}

.profile-menu button span {
  font-size: 12px;
  color: rgba(212, 175, 104, 0.6);
  font-weight: 500;
}

.profile-menu button:hover {
  background: rgba(212, 175, 104, 0.08);
  color: #fff;
}

.profile-menu button.active {
  background: rgba(212, 175, 104, 0.15);
  border-color: rgba(212, 175, 104, 0.4);
  color: #d4af68;
}

.profile-menu button.active span {
  color: #d4af68;
}

@media (max-width: 768px) {
  .profile-user-card {
    padding: 24px;
    gap: 16px;
  }

  .profile-main {
    padding: 24px;
  }

  .form-row {
    flex-direction: column;
    gap: 24px;
  }

  .form-fields {
    grid-template-columns: 1fr;
  }
}
</style>
