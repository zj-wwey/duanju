<template>
  <div class="video-shell">
    <video
      v-if="src"
      :key="episodeId"
      controls
      autoplay
      :poster="poster"
      :src="src"
      @timeupdate="$emit('progress', $event)"
      @ended="$emit('ended')"
    />
    <div v-else class="locked-frame">
      <h2>{{ title }}</h2>
      <p>{{ pointsText }}</p>
      <button type="button" @click="$emit('unlock')">{{ unlockText }}</button>
      <button v-if="wholeUnlockText" class="secondary-unlock" type="button" @click="$emit('unlockWhole')">{{ wholeUnlockText }}</button>
    </div>
  </div>
</template>

<script setup>
defineProps({
  episodeId: {
    type: [Number, String],
    default: null
  },
  src: {
    type: String,
    default: ''
  },
  poster: {
    type: String,
    default: ''
  },
  title: {
    type: String,
    default: ''
  },
  pointsText: {
    type: String,
    default: ''
  },
  unlockText: {
    type: String,
    default: ''
  },
  wholeUnlockText: {
    type: String,
    default: ''
  }
})

defineEmits(['progress', 'ended', 'unlock', 'unlockWhole'])
</script>
