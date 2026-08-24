<template>
  <header class="stream-nav app-nav">
    <button class="stream-logo" type="button" @click="$emit('home')">
      <span></span>
      <strong>{{ brand }}</strong>
    </button>

    <nav class="nav-tabs" :aria-label="t('nav.aria')">
      <button class="nav-link" :class="{ active: activeName === 'home' }" type="button" @click="$emit('home')">
        {{ t('nav.home') }}
      </button>
      <button class="nav-link" :class="{ active: activeName === 'category' }" type="button" @click="$emit('category')">
        {{ t('nav.category') }}
      </button>
    </nav>

    <form class="nav-search" @submit.prevent="$emit('search', keyword)">
      <span></span>
      <input v-model.trim="keyword" type="search" :placeholder="t('nav.search')" />
    </form>

    <div class="nav-spacer"></div>

    <div class="nav-quick-entries">
      <button
        class="nav-entry-btn"
        :class="{ active: activeName === 'vip' }"
        type="button"
        title="VIP会员"
        @click="$emit('vip')"
      >
        <span class="nav-entry-icon">👑</span>
        <span class="nav-entry-label">{{ t('nav.vip') }}</span>
      </button>
      <button
        class="nav-entry-btn"
        type="button"
        title="系统公告"
        @click="$emit('announcements')"
      >
        <span class="nav-entry-icon">📢</span>
        <span class="nav-entry-label">{{ t('nav.announcements') }}</span>
        <span v-if="unreadCount > 0" class="nav-entry-badge">{{ unreadCount > 9 ? '9+' : unreadCount }}</span>
      </button>
      <button
        class="nav-entry-btn"
        type="button"
        title="用户反馈"
        @click="$emit('feedback')"
      >
        <span class="nav-entry-icon">💬</span>
        <span class="nav-entry-label">{{ t('nav.feedback') }}</span>
      </button>
    </div>

    <button class="checkin-link" type="button" :disabled="checkingIn" @click="$emit('checkin')">
      {{ t('nav.checkin') }}
    </button>
    <div ref="profileRoot" class="nav-profile">
      <button
        class="nav-profile__trigger"
        :class="{ active: activeName === 'profile' || profileOpen }"
        type="button"
        :aria-expanded="profileOpen"
        @pointerdown.stop
        @click.stop="profileOpen = !profileOpen"
      >
        <span class="nav-avatar">
          <img v-if="avatarUrl" :src="avatarUrl" :alt="userLabel" />
          <span v-else>{{ avatarInitial }}</span>
        </span>
        <span class="nav-profile__name">{{ userLabel }}</span>
        <strong>{{ pointsText }}</strong>
      </button>

      <Transition name="profile-menu">
        <div v-if="profileOpen" class="profile-menu-popover" @pointerdown.stop>
          <button class="profile-menu-card" type="button" @click="goProfile">
            <span class="nav-avatar nav-avatar--large">
              <img v-if="avatarUrl" :src="avatarUrl" :alt="userLabel" />
              <span v-else>{{ avatarInitial }}</span>
            </span>
            <span>
              <b>{{ t('nav.profile') }}</b>
              <small>{{ userLabel }} · {{ pointsText }}</small>
            </span>
          </button>
          <button class="profile-menu-action" type="button" @click="$emit('recharge')">
            💳 {{ t('nav.recharge') }}
          </button>
          <button class="profile-menu-action" type="button" @click="$emit('feedback')">
            💬 {{ t('nav.feedback') }}
          </button>
          <button class="profile-menu-action" type="button" @click="logout">
            {{ t('nav.logout') }}
          </button>
        </div>
      </Transition>
    </div>

    <LanguageSelect />
  </header>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useStreamI18n } from '../../locales/streamI18n.js'
import LanguageSelect from './LanguageSelect.vue'

const props = defineProps({
  brand: {
    type: String,
    default: 'Xingmu'
  },
  activeName: {
    type: String,
    default: 'home'
  },
  userLabel: {
    type: String,
    default: 'Guest'
  },
  avatarUrl: {
    type: String,
    default: ''
  },
  pointsText: {
    type: String,
    default: '0'
  },
  checkingIn: {
    type: Boolean,
    default: false
  },
  unreadCount: {
    type: Number,
    default: 0
  }
})

const emit = defineEmits(['home', 'category', 'profile', 'search', 'checkin', 'logout', 'vip', 'announcements', 'feedback', 'recharge'])

const keyword = ref('')
const profileOpen = ref(false)
const profileRoot = ref(null)
const { t } = useStreamI18n()
const avatarInitial = computed(() => (props.userLabel || t('nav.profile')).trim().slice(0, 1).toUpperCase())

function goProfile() {
  profileOpen.value = false
  emit('profile')
}

function logout() {
  profileOpen.value = false
  emit('logout')
}

function closeOnOutside(event) {
  if (profileRoot.value && !profileRoot.value.contains(event.target)) {
    profileOpen.value = false
  }
}

onMounted(() => document.addEventListener('pointerdown', closeOnOutside))
onBeforeUnmount(() => document.removeEventListener('pointerdown', closeOnOutside))
</script>
