<template>
  <div class="episode-list">
    <div v-if="showRanges && ranges.length > 1" class="episode-ranges">
      <button
        v-for="(range, index) in ranges"
        :key="range.label"
        :class="{ active: activeRange === index }"
        type="button"
        @click="activeRange = index"
      >
        {{ range.label }}
      </button>
    </div>

    <div class="episode-grid" :class="{ compact }">
      <button
        v-for="item in visibleEpisodes"
        :key="item.id"
        :class="{ active: isActive(item), locked: !item.unlocked }"
        type="button"
        @click="$emit('select', item)"
      >
        <span>{{ item.episodeNo }}</span>
        <small>{{ episodeState(item) }}</small>
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useStreamI18n } from '../../locales/streamI18n.js'
import { formatDuration as formatDurationUtil } from '../../utils/helpers.js'

const props = defineProps({
  episodes: {
    type: Array,
    default: () => []
  },
  activeEpisodeId: {
    type: [Number, String],
    default: null
  },
  groupSize: {
    type: Number,
    default: 20
  },
  compact: {
    type: Boolean,
    default: false
  },
  showRanges: {
    type: Boolean,
    default: true
  }
})

defineEmits(['select'])

const { t } = useStreamI18n()
const activeRange = ref(0)

const ranges = computed(() => {
  const groups = []
  for (let start = 0; start < props.episodes.length; start += props.groupSize) {
    const end = Math.min(start + props.groupSize, props.episodes.length)
    groups.push({ label: `${start + 1}-${end}`, items: props.episodes.slice(start, end) })
  }
  return groups
})

const visibleEpisodes = computed(() => ranges.value[activeRange.value]?.items || props.episodes.slice(0, props.groupSize))

watch(() => props.activeEpisodeId, id => {
  const index = props.episodes.findIndex(item => String(item.id) === String(id))
  if (index >= 0) {
    activeRange.value = Math.floor(index / props.groupSize)
  }
}, { immediate: true })

function isActive(item) {
  return String(item.id) === String(props.activeEpisodeId)
}

function episodeState(item) {
  const parts = []
  if (item.unlocked) {
    parts.push(item.free ? t('common.free') : t('common.unlocked'))
  } else {
    parts.push(`${Number(item.pricePoints) || 5}${t('common.points')}`)
  }
  const durText = item.durationText || formatDuration(item.durationSeconds || item.duration_seconds)
  if (durText) parts.push(durText)
  return parts.join(' · ')
}

function formatDuration(seconds) {
  return formatDurationUtil(seconds, 'zh-CN', { returnEmpty: true })
}
</script>
