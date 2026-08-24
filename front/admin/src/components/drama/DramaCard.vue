<template>
  <article class="drama-cover-card" @click="$emit('select', drama)">
    <div class="drama-cover-media">
      <img :src="cover" :alt="drama.title" />
      <span class="drama-episode-badge">{{ episodeBadge }}</span>
      <SealTag v-if="isFreePreview" class="drama-free-badge" active>{{ t('common.freePreview') }}</SealTag>
    </div>
    <h3>{{ drama.title }}</h3>
    <div class="mini-tags">
      <SealTag v-for="tag in visibleTags" :key="tag" muted>{{ tag }}</SealTag>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import { useStreamI18n } from '../../locales/streamI18n.js'
import SealTag from './SealTag.vue'
import { useDramaStore } from '../../user/store.js'

defineEmits(['select'])

const props = defineProps({
  drama: {
    type: Object,
    required: true
  }
})

const { t } = useStreamI18n()
const store = useDramaStore()
const cover = computed(() => store.coverOf(props.drama))
const episodeBadge = computed(() => {
  const total = props.drama.totalEpisodes || props.drama.total_episodes
  return total ? t('common.episodeTotal', { count: total }) : t('common.serializing')
})
const isFreePreview = computed(() => Number(props.drama.freeEpisodeCount ?? props.drama.free_episode_count ?? 0) > 0)
const contentTypeLabel = computed(() => props.drama.contentType === 'ai' || props.drama.content_type === 'ai'
  ? t('category.aiDrama')
  : t('category.realDrama'))
const visibleTags = computed(() => [
  contentTypeLabel.value,
  isFreePreview.value ? t('common.freePreview') : t('common.premiumEpisode')
].filter(Boolean))
</script>
