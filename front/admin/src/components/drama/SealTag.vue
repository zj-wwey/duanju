<template>
  <span class="seal-tag" :class="{ active, muted }">
    <slot />
  </span>
</template>

<script setup>
defineProps({
  active: {
    type: Boolean,
    default: false
  },
  muted: {
    type: Boolean,
    default: false
  }
})
</script>
