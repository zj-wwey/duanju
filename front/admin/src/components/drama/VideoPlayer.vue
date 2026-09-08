<template>
  <div class="video-shell">
    <video
      ref="videoRef"
      v-show="src"
      controls
      autoplay
      :poster="poster"
      @timeupdate="$emit('progress', $event)"
      @ended="$emit('ended')"
    />
    <div v-show="!src" class="locked-frame">
      <h2>{{ title }}</h2>
      <p>{{ pointsText }}</p>
      <button type="button" @click="$emit('unlock')">{{ unlockText }}</button>
      <button v-if="wholeUnlockText" class="secondary-unlock" type="button" @click="$emit('unlockWhole')">{{ wholeUnlockText }}</button>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import Hls from 'hls.js'

const props = defineProps({
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

const videoRef = ref(null)
let hlsInstance = null

function destroyHls() {
  if (hlsInstance) {
    hlsInstance.destroy()
    hlsInstance = null
  }
}

function isHlsUrl(url) {
  if (!url) return false
  // 标准 HLS m3u8 播放列表
  if (url.toLowerCase().includes('.m3u8')) return true
  // Cloudflare Stream HLS (路径含 /manifest，实际返回 m3u8)
  if (/\/manifest(\?|$)/i.test(url)) return true
  // 常见 HLS 路径特征
  if (/\/hls\//i.test(url)) return true
  return false
}

function attachVideo(url) {
  console.log('[VideoPlayer] attachVideo called with url:', url)
  destroyHls()
  const video = videoRef.value
  console.log('[VideoPlayer] videoRef.value:', video)
  if (!video || !url) return

  const useHls = isHlsUrl(url)
  console.log('[VideoPlayer] useHls:', useHls, 'url:', url)
  if (!useHls) {
    console.log('[VideoPlayer] not HLS, using direct video.src')
    video.src = url
    return
  }

  if (video.canPlayType('application/vnd.apple.mpegurl')) {
    console.log('[VideoPlayer] using native HLS playback')
    video.src = url
  } else if (Hls.isSupported()) {
    console.log('[VideoPlayer] using hls.js')
    hlsInstance = new Hls()
    hlsInstance.loadSource(url)
    hlsInstance.attachMedia(video)
    hlsInstance.on(Hls.Events.ERROR, (event, data) => {
      console.warn('[VideoPlayer] hls.js error:', data?.type, data?.details)
      if (data?.fatal) {
        console.error('[VideoPlayer] hls.js fatal error, playback may fail')
      }
    })
  } else {
    console.log('[VideoPlayer] HLS not supported by browser, falling back to direct src')
    video.src = url
  }
}

watch(() => props.src, (url) => {
  if (url) attachVideo(url)
})

onMounted(() => {
  if (props.src) attachVideo(props.src)
})

onBeforeUnmount(() => {
  destroyHls()
})
</script>
