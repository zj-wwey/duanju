<template>
  <section class="stream-page detail-page">
    <button class="back-link back-button" type="button" @click="router.back()">← {{ t('common.back') }}</button>
    <template v-if="store.activeDrama.value">
      <div class="detail-hero">
        <img class="detail-cover" :src="store.coverOf(store.activeDrama.value)" :alt="store.activeDrama.value.title" />
        <div class="detail-copy">
          <p class="stream-kicker">{{ t('detail.kicker') }}</p>
          <h1>{{ store.activeDrama.value.title }}</h1>
          <div class="tag-row">
            <SealTag active>{{ store.activeDrama.value.contentType === 'ai' ? t('category.aiDrama') : t('category.realDrama') }}</SealTag>
            <SealTag>{{ episodeBadge(store.activeDrama.value) }}</SealTag>
            <SealTag>{{ t('common.freePreview') }}</SealTag>
          </div>
          <p>{{ store.activeDrama.value.description || t('detail.noSynopsis') }}</p>
          <GoldButton play @click="playFirst">{{ t('detail.play') }}</GoldButton>
          <GoldButton variant="seal" @click="favorite">
            {{ store.activeDrama.value.favorite ? t('detail.favorited') : t('detail.favorite') }}
          </GoldButton>
          <GoldButton v-if="wholeUnlockText" variant="seal" @click="unlockWhole">
            {{ wholeUnlockText }}
          </GoldButton>
        </div>
      </div>

      <section class="detail-block eastern-panel">
        <h2>{{ t('detail.synopsis') }}</h2>
        <p>{{ store.activeDrama.value.description || t('detail.noSynopsis') }}</p>
      </section>

      <section class="detail-block">
        <div class="section-title">
          <h2>{{ t('detail.episodes') }}</h2>
          <span>{{ t('detail.rule') }}</span>
        </div>
        <EpisodeList :episodes="store.episodes.value" :group-size="30" @select="playEpisode" />
      </section>

      <section class="detail-block">
        <div class="section-title">
          <h2>{{ t('detail.recommend') }}</h2>
        </div>
        <div class="poster-grid recommend-grid">
          <DramaCard v-for="item in recommendations" :key="item.id" :drama="item" @select="openDetail" />
        </div>
      </section>
    </template>
    <div v-else class="stream-empty">{{ t('common.loading') }}</div>
  </section>
</template>

<script setup>
import { computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useStreamI18n } from '../locales/streamI18n.js'
import { ElMessage } from 'element-plus'
import DramaCard from '../components/drama/DramaCard.vue'
import EpisodeList from '../components/drama/EpisodeList.vue'
import GoldButton from '../components/drama/GoldButton.vue'
import SealTag from '../components/drama/SealTag.vue'
import { useDramaStore } from '../user/store.js'

const props = defineProps({ id: String })
const router = useRouter()
const { t, locale } = useStreamI18n()
const store = useDramaStore()
const recommendations = computed(() => store.dramas.value.filter(item => String(item.id) !== String(props.id)).slice(0, 6))
const wholeUnlockText = computed(() => {
  const points = Number(store.activeDrama.value?.wholePricePoints ?? store.activeDrama.value?.whole_price_points ?? 0)
  const hasLockedEpisode = store.episodes.value.some(item => !item.unlocked)
  return points > 0 && hasLockedEpisode ? t('player.unlockWhole', { points }) : ''
})

onMounted(async () => {
  await loadPage()
})

watch(() => props.id, loadPage)
watch(locale, loadPage)

async function loadPage() {
  try {
    await Promise.all([store.loadDrama(props.id), store.loadDramas()])
  } catch (err) {
    ElMessage.error(err.message)
  }
}

function playFirst() {
  const first = store.episodes.value[0]
  playEpisode(first)
}

function playEpisode(episode) {
  if (!episode || !store.activeDrama.value) return
  router.push({ name: 'player', params: { dramaId: store.activeDrama.value.id, episodeId: episode.id } })
}

function openDetail(drama) {
  router.push({ name: 'detail', params: { id: drama.id } })
}

function episodeBadge(drama) {
  const total = drama?.totalEpisodes || drama?.total_episodes
  return total ? t('common.episodeTotal', { count: total }) : t('common.serializing')
}

async function favorite() {
  try {
    const favoriteState = await store.toggleFavorite(store.activeDrama.value)
    ElMessage.success(favoriteState ? t('detail.favoriteAdded') : t('detail.favoriteRemoved'))
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function unlockWhole() {
  try {
    await store.unlockDrama(store.activeDrama.value)
    ElMessage.success(t('player.unlockSuccess'))
  } catch (err) {
    ElMessage.error(err.message)
  }
}
</script>
