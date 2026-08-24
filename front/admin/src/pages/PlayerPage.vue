<template>
  <section class="player-page routed-player">
    <section class="cinema">
      <button class="player-back" type="button" @click="router.back()">← {{ t('common.back') }}</button>
      <h1>{{ t('player.episode', { num: activeEpisode?.episodeNo || 1 }) }}</h1>
      <VideoPlayer
        :episode-id="activeEpisode?.id"
        :src="activeEpisode?.videoUrl || ''"
        :poster="activeEpisode?.coverUrl || store.activeDrama.value?.coverUrl || ''"
        :title="lockedText"
        :points-text="t('nav.points', { points: store.points.value })"
        :unlock-text="unlockText"
        :whole-unlock-text="wholeUnlockText"
        @unlock="unlock"
        @unlock-whole="unlockWhole"
        @ended="playNext"
        @progress="saveProgress"
      />
    </section>

    <aside class="player-side">
      <div v-if="store.activeDrama.value" class="side-title">
        <img :src="store.coverOf(store.activeDrama.value)" :alt="store.activeDrama.value.title" />
        <div>
          <h2>{{ store.activeDrama.value.title }}</h2>
          <div class="mini-tags">
            <span>{{ store.activeDrama.value.contentType === 'ai' ? t('category.aiDrama') : t('category.realDrama') }}</span>
            <span>{{ episodeBadge(store.activeDrama.value) }}</span>
          </div>
        </div>
      </div>
      <h3>{{ t('player.intro') }}</h3>
      <p>{{ store.activeDrama.value?.description || t('player.noIntro') }}</p>
      <h3>{{ t('player.episodes') }}</h3>
      <label class="auto-next">
        <input v-model="autoNext" type="checkbox" @change="persistAutoNext" />
        <span>{{ t('player.autoNext') }}</span>
      </label>
      <EpisodeList
        :episodes="store.episodes.value"
        :active-episode-id="activeEpisode?.id"
        :group-size="10"
        compact
        @select="openEpisode"
      />

      <h3>{{ t('player.recommend') }}</h3>
      <div class="side-recommend">
        <img
          v-for="item in recommendations"
          :key="item.id"
          :src="store.coverOf(item)"
          :alt="item.title"
          @click="openDrama(item)"
        />
      </div>
    </aside>
  </section>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useStreamI18n } from '../locales/streamI18n.js'
import { ElMessage } from 'element-plus'
import EpisodeList from '../components/drama/EpisodeList.vue'
import VideoPlayer from '../components/drama/VideoPlayer.vue'
import { api } from '../api.js'
import { useDramaStore } from '../user/store.js'

const props = defineProps({ dramaId: String, episodeId: String })
const router = useRouter()
const { t } = useStreamI18n()
const store = useDramaStore()
const autoNext = ref(true)
const lastProgressSecond = ref(-1)
const activeEpisode = computed(() => store.episodes.value.find(item => String(item.id) === String(props.episodeId)) || store.episodes.value[0])
const lockedText = computed(() => t('player.locked', { num: activeEpisode.value?.episodeNo || 1 }))
const unlockText = computed(() => t('player.unlock', { points: Number(activeEpisode.value?.pricePoints) || 5 }))
const wholeUnlockText = computed(() => {
  const points = Number(store.activeDrama.value?.wholePricePoints ?? store.activeDrama.value?.whole_price_points ?? 0)
  return points > 0 ? t('player.unlockWhole', { points }) : ''
})
const recommendations = computed(() => store.dramas.value.filter(item => String(item.id) !== String(props.dramaId)).slice(0, 6))

onMounted(async () => {
  await loadPage()
})

watch(() => props.dramaId, loadPage)

async function loadPage() {
  try {
    const [, , settings] = await Promise.all([
      store.loadDrama(props.dramaId),
      store.loadDramas(),
      api.userSettings().catch(() => null)
    ])
    if (settings) {
      autoNext.value = settings.autoNext !== false
    }
    lastProgressSecond.value = -1
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function persistAutoNext() {
  try {
    const current = await api.userSettings().catch(() => ({ notice: true }))
    await api.userUpdateSettings({
      notice: current.notice !== false,
      autoNext: Boolean(autoNext.value)
    })
  } catch (err) {
    ElMessage.error(err.message)
  }
}

function openEpisode(episode) {
  router.push({ name: 'player', params: { dramaId: props.dramaId, episodeId: episode.id } })
}

function openDrama(drama) {
  router.push({ name: 'detail', params: { id: drama.id } })
}

function episodeBadge(drama) {
  const total = drama?.totalEpisodes || drama?.total_episodes
  return total ? t('common.episodeTotal', { count: total }) : t('common.serializing')
}

function playNext() {
  if (!autoNext.value || !activeEpisode.value) return
  const index = store.episodes.value.findIndex(item => String(item.id) === String(activeEpisode.value.id))
  const next = store.episodes.value[index + 1]
  if (next) openEpisode(next)
}

function saveProgress(event) {
  if (!store.activeDrama.value || !activeEpisode.value) return
  const currentSecond = Math.floor(event.target.currentTime || 0)
  if (currentSecond <= 0 || currentSecond === lastProgressSecond.value || currentSecond % 10 !== 0) return
  lastProgressSecond.value = currentSecond
  store.saveProgress({
    dramaId: store.activeDrama.value.id,
    episodeId: activeEpisode.value.id,
    progressSeconds: currentSecond
  }).catch(() => {})
}

async function unlock() {
  try {
    await store.unlockEpisode(activeEpisode.value)
    ElMessage.success(t('player.unlockSuccess'))
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
