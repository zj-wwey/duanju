import { computed, ref } from 'vue'
import { api } from '../api.js'

const user = ref(JSON.parse(sessionStorage.getItem('user') || 'null'))
const points = ref(Number(user.value?.points) || 0)
const pointsExpireAt = ref(null)
const dramas = ref([])
const activeDrama = ref(null)
const episodes = ref([])
const loading = ref(false)
const profileLoading = ref(false)

const userLabel = computed(() => user.value?.nickname || user.value?.username || '游客')

function normalizeDrama(row = {}) {
  return {
    ...row,
    coverUrl: row.coverUrl ?? row.cover_url,
    freeEpisodeCount: row.freeEpisodeCount ?? row.free_episode_count ?? 2,
    totalEpisodes: row.totalEpisodes ?? row.total_episodes,
    episodePricePoints: row.episodePricePoints ?? row.episode_price_points,
    wholePricePoints: row.wholePricePoints ?? row.whole_price_points,
    contentType: row.contentType ?? row.content_type ?? 'real',
    setting: row.setting ?? row.setting_key,
    publishDate: row.publishDate ?? row.publish_date,
    hotScore: row.hotScore ?? row.hot_score ?? 0
  }
}

function normalizeEpisode(row = {}) {
  return {
    ...row,
    dramaId: row.dramaId ?? row.drama_id,
    episodeNo: row.episodeNo ?? row.episode_no,
    coverUrl: row.coverUrl ?? row.cover_url,
    videoUrl: row.videoUrl ?? row.video_url,
    hlsUrl: row.hlsUrl ?? row.hls_url,
    signedUrl: row.signedUrl ?? row.signed_url,
    pricePoints: row.pricePoints ?? row.price_points ?? 5,
    durationSeconds: row.durationSeconds ?? row.duration_seconds,
    durationText: row.durationText ?? row.duration_text
  }
}

async function loadProfile() {
  profileLoading.value = true
  try {
    const data = await api.userPoints()
    user.value = data.user
    points.value = Number(data.user?.points) || 0
    pointsExpireAt.value = data.pointsExpireAt || null
    sessionStorage.setItem('user', JSON.stringify(data.user))
    return data
  } finally {
    profileLoading.value = false
  }
}

async function checkin() {
  await api.userCheckin()
  await loadProfile()
}

async function loadDramas(params = {}) {
  loading.value = true
  try {
    dramas.value = (await api.publicDramas(params)).map(normalizeDrama)
    return dramas.value
  } finally {
    loading.value = false
  }
}

async function loadDrama(id) {
  loading.value = true
  try {
    const data = await api.publicDrama(id)
    activeDrama.value = normalizeDrama(data)
    episodes.value = (data.episodes || []).map(normalizeEpisode)
    return activeDrama.value
  } finally {
    loading.value = false
  }
}

async function toggleFavorite(drama) {
  const data = await api.userToggleFavorite(drama.id)
  const favorite = Boolean(data.favorite)
  dramas.value = dramas.value.map(item => item.id === drama.id ? { ...item, favorite } : item)
  if (activeDrama.value?.id === drama.id) {
    activeDrama.value = { ...activeDrama.value, favorite }
  }
  return favorite
}

async function unlockEpisode(episode) {
  await api.userUnlock(episode.id)
  await loadProfile()
  if (activeDrama.value?.id) {
    await loadDrama(activeDrama.value.id)
  }
}

async function unlockDrama(drama) {
  await api.userUnlockDrama(drama.id)
  await loadProfile()
  await loadDrama(drama.id)
}

function saveProgress(payload) {
  return api.userSaveHistory(payload)
}

function coverOf(row) {
  return row?.coverUrl || row?.cover_url || 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 225"%3E%3Crect fill="%23161616" width="400" height="225"/%3E%3Ctext x="50%25" y="50%25" fill="%23555" font-family="sans-serif" font-size="14" text-anchor="middle" dy=".3em"%3ENo Cover%3C/text%3E%3C/svg%3E'
}

function episodeBadge(row, t) {
  const total = row?.totalEpisodes || row?.total_episodes
  if (!t) return total ? `全${total}集` : '连载中'
  return total ? t('common.episodeTotal', { count: total }) : t('common.serializing')
}

export function useDramaStore() {
  return {
    user,
    userLabel,
    points,
    pointsExpireAt,
    dramas,
    activeDrama,
    episodes,
    loading,
    profileLoading,
    loadProfile,
    checkin,
    loadDramas,
    loadDrama,
    toggleFavorite,
    unlockEpisode,
    unlockDrama,
    saveProgress,
    coverOf,
    episodeBadge
  }
}
