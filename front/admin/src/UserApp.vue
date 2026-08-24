<template>
  <RouterView />
</template>

<script setup>
import { provide } from 'vue'
import { RouterView } from 'vue-router'

const emit = defineEmits(['logout'])

provide('logoutUser', () => emit('logout'))
</script>
