<template>
  <header class="stream-nav app-nav">
    <button class="stream-logo" type="button" @click="$emit('home')">
      <svg viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
        <defs>
          <linearGradient id="starGradNav" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#D4AF68"/>
            <stop offset="100%" stop-color="#C86D26"/>
          </linearGradient>
        </defs>
        <rect width="64" height="64" rx="14" fill="#1E1C19" stroke="url(#starGradNav)" stroke-width="2"/>
        <path d="M32 14 L35.5 26 L48 26 L38 33 L42 45 L32 37 L22 45 L26 33 L16 26 L28.5 26 Z" fill="url(#starGradNav)"/>
      </svg>
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
