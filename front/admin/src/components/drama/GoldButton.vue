<template>
  <button class="gold-button" :class="[`gold-button--${variant}`, { compact }]" :type="type">
    <span v-if="play" class="gold-button__play"></span>
    <slot />
  </button>
</template>

<script setup>
defineProps({
  variant: {
    type: String,
    default: 'primary'
  },
  type: {
    type: String,
    default: 'button'
  },
  compact: {
    type: Boolean,
    default: false
  },
  play: {
    type: Boolean,
    default: false
  }
})
</script>
