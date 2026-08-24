<template>
  <div ref="root" class="language-select">
    <button
      class="language-trigger"
      type="button"
      :aria-expanded="open"
      @pointerdown.stop
      @click.stop="open = !open"
    >
      <span>{{ currentOption.native }}</span>
      <small>{{ currentOption.code }}</small>
    </button>

    <Transition name="language-menu">
      <div v-if="open" class="language-menu">
        <button
          v-for="item in languageOptions"
          :key="item.code"
          :class="{ active: item.code === locale }"
          type="button"
          @pointerdown.stop.prevent="selectLocale(item.code)"
          @click.stop
        >
          <span>{{ item.native }}</span>
          <small>{{ item.code }}</small>
          <b v-if="item.code === locale" aria-hidden="true">&#10003;</b>
        </button>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from '../../i18n/index.js'
import { useStreamI18n } from '../../locales/streamI18n.js'
import { languageOptions } from '../../../../shared/i18n/locale-options.js'

const { locale, setLocale } = useI18n()
const { setLocale: setStreamLocale } = useStreamI18n()
const open = ref(false)
const root = ref(null)
const currentOption = computed(() => languageOptions.find(item => item.code === locale.value) || languageOptions[0])

async function selectLocale(code) {
  await setLocale(code)
  await setStreamLocale(code)
  open.value = false
}

function closeOnOutside(event) {
  if (root.value && !root.value.contains(event.target)) {
    open.value = false
  }
}

onMounted(() => document.addEventListener('pointerdown', closeOnOutside))
onBeforeUnmount(() => document.removeEventListener('pointerdown', closeOnOutside))
</script>
