<template>
  <section class="stream-page home-page">
    <section class="home-discovery" :aria-label="t('home.discoverTitle')">
      <div class="home-discovery__title">
        <p class="stream-kicker">{{ t('home.discoverKicker') }}</p>
        <h2>{{ t('home.discoverTitle') }}</h2>
      </div>
      <div class="home-discovery__modes">
        <button
          v-for="mode in homeModes"
          :key="mode.key"
          :class="{ active: activeHomeMode === mode.key }"
          type="button"
          @click="selectHomeMode(mode.key)"
        >
          <strong>{{ t(mode.label) }}</strong>
          <span>{{ t(mode.desc) }}</span>
        </button>
      </div>
    </section>

    <!-- 快捷功能入口 -->
    <section class="home-quick-entries">
      <button class="quick-entry-card vip-card" @click="router.push({ name: 'vip' })">
        <div class="quick-entry-icon">👑</div>
        <div class="quick-entry-info">
          <div class="quick-entry-title">{{ t('home.vipEntry') }}</div>
          <div class="quick-entry-desc">{{ t('home.vipEntryDesc') }}</div>
        </div>
        <div class="quick-entry-arrow">→</div>
      </button>
      <button class="quick-entry-card recharge-card" @click="router.push({ name: 'recharge' })">
        <div class="quick-entry-icon">💳</div>
        <div class="quick-entry-info">
          <div class="quick-entry-title">{{ t('home.rechargeEntry') }}</div>
          <div class="quick-entry-desc">{{ t('home.rechargeEntryDesc') }}</div>
        </div>
        <div class="quick-entry-arrow">→</div>
      </button>
      <button class="quick-entry-card points-card" @click="router.push({ name: 'recharge' })">
        <div class="quick-entry-icon">💰</div>
        <div class="quick-entry-info">
          <div class="quick-entry-title">{{ t('home.pointsBalance') }}</div>
          <div class="quick-entry-desc">{{ t('home.pointsBalanceDesc', { points: formatNumber(store.points.value) }) }}</div>
        </div>
      </button>
    </section>

    <section v-if="heroDrama" class="home-carousel" @mouseenter="pauseCarousel" @mouseleave="startCarousel">
      <div class="home-carousel__backdrop" :style="{ backgroundImage: `url(${store.coverOf(heroDrama)})` }"></div>
      <button v-if="carouselDramas.length > 1" class="home-carousel__nav home-carousel__nav--prev" type="button" :aria-label="t('home.previous')" @click="prevHero">
        &lsaquo;
      </button>
      <button v-if="carouselDramas.length > 1" class="home-carousel__nav home-carousel__nav--next" type="button" :aria-label="t('home.next')" @click="nextHero">
        &rsaquo;
      </button>

      <div class="home-carousel__poster">
        <img :src="store.coverOf(heroDrama)" :alt="heroDrama.title" />
      </div>
      <div class="home-carousel__content">
        <p class="stream-kicker">{{ t('home.selected') }}</p>
        <h1>{{ heroDrama.title }}</h1>
        <div class="tag-row">
          <SealTag active>{{ heroDrama.contentType === 'ai' ? t('category.aiDrama') : t('category.realDrama') }}</SealTag>
          <SealTag>{{ episodeBadge(heroDrama) }}</SealTag>
          <SealTag>{{ t('common.freePreview') }}</SealTag>
        </div>
        <p class="feature-copy">{{ heroDrama.description || t('home.heroFallback') }}</p>
        <GoldButton play @click="openDetail(heroDrama)">{{ t('home.watchNow') }}</GoldButton>
      </div>

      <div v-if="carouselDramas.length > 1" class="home-carousel__rail">
        <button
          v-for="(item, index) in carouselDramas"
          :key="item.id"
          :class="{ active: index === activeHeroIndex }"
          type="button"
          :aria-label="item.title"
          @click="selectHero(index)"
        >
          <img :src="store.coverOf(item)" :alt="item.title" />
        </button>
      </div>
    </section>

    <div class="page-toolbar">
      <div>
        <p class="stream-kicker">{{ t('home.library') }}</p>
        <h2>{{ t('home.trending') }}</h2>
      </div>
      <GoldButton variant="ghost" compact @click="router.push({ name: 'category' })">{{ t('home.allCategories') }}</GoldButton>
    </div>

    <div v-if="store.loading.value" class="skeleton-grid">
      <div v-for="item in 12" :key="item" class="skeleton-card"></div>
    </div>
    <div v-else-if="displayDramas.length" class="poster-grid">
      <DramaCard v-for="item in displayDramas" :key="item.id" :drama="item" @select="openDetail" />
    </div>
    <div v-else class="stream-empty">{{ t('common.noDramas') }}</div>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useStreamI18n } from '../locales/streamI18n.js'
import { ElMessage } from 'element-plus'
import DramaCard from '../components/drama/DramaCard.vue'
import GoldButton from '../components/drama/GoldButton.vue'
import SealTag from '../components/drama/SealTag.vue'
import { useDramaStore } from '../user/store.js'
import { formatNumber as formatNumberUtil } from '../utils/helpers.js'

const router = useRouter()
const { t, locale } = useStreamI18n()
const store = useDramaStore()
const activeHomeMode = ref('hot')
const activeHeroIndex = ref(0)
let carouselTimer = null

function formatNumber(value) {
  return formatNumberUtil(value, locale.value)
}

const homeModes = [
  { key: 'hot', label: 'home.modes.hot', desc: 'home.modeDesc.hot' },
  { key: 'latest', label: 'home.modes.latest', desc: 'home.modeDesc.latest' },
  { key: 'free', label: 'home.modes.free', desc: 'home.modeDesc.free' },
  { key: 'long', label: 'home.modes.long', desc: 'home.modeDesc.long' }
]

const displayDramas = computed(() => {
  const list = [...store.dramas.value]
  if (activeHomeMode.value === 'latest') {
    return list.sort((a, b) => Number(b.id) - Number(a.id))
  }
  if (activeHomeMode.value === 'free') {
    return list.filter(item => Number(item.freeEpisodeCount ?? item.free_episode_count ?? 0) > 0)
  }
  if (activeHomeMode.value === 'long') {
    return list.filter(item => Number(item.totalEpisodes || item.total_episodes || 0) >= 50)
  }
  return list.sort((a, b) => Number(b.totalEpisodes || b.total_episodes || 0) - Number(a.totalEpisodes || a.total_episodes || 0))
})

const carouselDramas = computed(() => displayDramas.value.slice(0, 5))
const heroDrama = computed(() => carouselDramas.value[activeHeroIndex.value] || carouselDramas.value[0] || null)

onMounted(async () => {
  try {
    await store.loadDramas()
    startCarousel()
  } catch (err) {
    ElMessage.error(err.message)
  }
})

onBeforeUnmount(() => pauseCarousel())

watch(locale, () => {
  store.loadDramas().catch(err => ElMessage.error(err.message))
})

watch(carouselDramas, list => {
  if (activeHeroIndex.value >= list.length) {
    activeHeroIndex.value = 0
  }
  startCarousel()
})

function selectHomeMode(mode) {
  activeHomeMode.value = mode
  activeHeroIndex.value = 0
  startCarousel()
}

function openDetail(drama) {
  router.push({ name: 'detail', params: { id: drama.id } })
}

function episodeBadge(drama) {
  const total = drama?.totalEpisodes || drama?.total_episodes
  return total ? t('common.episodeTotal', { count: total }) : t('common.serializing')
}

function selectHero(index) {
  activeHeroIndex.value = index
  startCarousel()
}

function prevHero() {
  const total = carouselDramas.value.length
  if (total <= 1) return
  activeHeroIndex.value = (activeHeroIndex.value - 1 + total) % total
  startCarousel()
}

function nextHero() {
  const total = carouselDramas.value.length
  if (total <= 1) return
  activeHeroIndex.value = (activeHeroIndex.value + 1) % total
  startCarousel()
}

function startCarousel() {
  pauseCarousel()
  if (carouselDramas.value.length <= 1) return
  carouselTimer = window.setInterval(() => {
    nextHero()
  }, 5200)
}

function pauseCarousel() {
  if (!carouselTimer) return
  window.clearInterval(carouselTimer)
  carouselTimer = null
}
</script>
