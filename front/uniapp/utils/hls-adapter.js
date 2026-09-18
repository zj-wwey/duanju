/**
 * H5 端 HLS 播放器适配层
 *
 * Safari/iOS 原生支持 m3u8 → 直接用 <video>.src
 * Chrome/Edge/Firefox 不支持 → 用 hls.js（需通过 CDN script 标签引入 window.Hls）
 *
 * 核心：attachMedia 前必须确保 video 元素是干净的（没有被原生尝试加载 m3u8 导致 MediaSource 状态异常）
 */

function hasHlsJs() {
  // #ifdef H5
  return typeof window !== 'undefined' && window.Hls && typeof window.Hls.isSupported === 'function'
  // #endif
  return false
}

/**
 * 懒加载自托管的 hls.min.js（兜底：index.html 已同步引入，正常不会走到这里）
 * 全局只加载一次，多个播放实例共享同一个 Promise
 */
let hlsScriptPromise = null
function loadHlsScript() {
  // #ifdef H5
  if (hasHlsJs()) return Promise.resolve(window.Hls)
  if (hlsScriptPromise) return hlsScriptPromise
  hlsScriptPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = '/static/hls.min.js'
    script.async = true
    script.onload = () => {
      if (hasHlsJs()) resolve(window.Hls)
      else reject(new Error('hls.js loaded but window.Hls invalid'))
    }
    script.onerror = () => reject(new Error('hls.js script load failed'))
    document.head.appendChild(script)
  })
  return hlsScriptPromise
  // #endif
  return Promise.reject(new Error('hls.js only available on H5'))
}

/**
 * 应用启动时预加载 hls.js（H5 专用）
 * uni-app 发行 H5 不会保留 index.html 中手写的 <script>，因此用代码注入；
 * 在 main.js 启动阶段调用，与页面 JS 并行下载，用户进入视频前基本就绪。
 */
export function preloadHls() {
  // #ifdef H5
  try {
    loadHlsScript()
  } catch (_) {}
  // #endif
}

function nativeHlsSupported() {
  try {
    // #ifdef H5
    const v = document.createElement('video')
    return v.canPlayType('application/vnd.apple.mpegurl') !== ''
    // #endif
  } catch (_) {}
  return false
}

export function isHlsUrl(url) {
  return typeof url === 'string' && url.includes('.m3u8')
}

/**
 * 清理 video 元素上可能残留的 hls 实例
 */
export function cleanupVideo(videoEl) {
  if (!videoEl) return
  if (videoEl.__hls) {
    try { videoEl.__hls.destroy() } catch (_) {}
    videoEl.__hls = null
  }
  try {
    videoEl.removeAttribute('src')
    videoEl.load()
  } catch (_) {}
}

/**
 * 在 H5 端设置 video 源
 * - m3u8 + Safari 原生 → 直接 src
 * - m3u8 + Chrome/Edge → hls.js 接管
 * - mp4 → 直接 src
 *
 * @param {HTMLVideoElement} videoEl 原生 video DOM 元素
 * @param {string} url 播放地址
 * @param {boolean} [autoplay=true] 是否自动播放（H5 需要 muted 才能 autoplay）
 * @param {Function} [onManifestParsed] manifest 解析成功回调
 * @param {Function} [onError] 错误回调
 * @returns {{ destroy: () => void, hls: any }}
 */
export function setupVideo(videoEl, url, autoplay = true, onManifestParsed, onError) {
  if (!videoEl || !url) return { destroy: () => {}, hls: null }

  // 清理旧实例
  cleanupVideo(videoEl)

  // 非 m3u8 → 直接 src（mp4 或其他）
  if (!isHlsUrl(url)) {
    videoEl.src = url
    if (autoplay) {
      videoEl.muted = true
      try { videoEl.play().catch(() => {}) } catch (_) {}
    }
    return { destroy: () => {}, hls: null }
  }

  // m3u8
  if (nativeHlsSupported()) {
    // Safari/iOS 原生支持
    videoEl.src = url
    if (autoplay) {
      videoEl.muted = true
      try { videoEl.play().catch(() => {}) } catch (_) {}
    }
    return { destroy: () => {}, hls: null }
  }

  // Chrome/Edge/Firefox → hls.js（自托管脚本，index.html 已同步引入；未就绪时懒加载兜底）
  const handle = {
    hls: null,
    destroyed: false,
    destroy() {
      this.destroyed = true
      if (videoEl.__hls) {
        try { videoEl.__hls.destroy() } catch (_) {}
        videoEl.__hls = null
      }
    }
  }

  const startHls = (Hls) => {
    if (handle.destroyed || !videoEl) return

    const hls = new Hls({
      enableWorker: false,        // worker=true 时 MediaSource 内部 race condition 导致 internalException
      // 起播优化：解析清单阶段就预取首个分片，减少"黑屏等首片"时间
      startFragPrefetch: true,
      // 弱网卡顿优化：拉大前向缓冲，让分片提前下载而不是边播边赶
      maxBufferLength: 30,
      maxMaxBufferLength: 120,
      backBufferLength: 30,
      lowLatencyMode: false,
      // R2 回源 TTFB 较高，超时放宽，重试加量
      manifestLoadingTimeOut: 15000,
      manifestLoadingMaxRetry: 6,
      levelLoadingTimeOut: 15000,
      levelLoadingMaxRetry: 6,
      fragLoadingTimeOut: 30000,
      fragLoadingMaxRetry: 8
    })

    hls.attachMedia(videoEl)
    hls.loadSource(url)

    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      if (autoplay) {
        videoEl.muted = true
        try { videoEl.play().catch(() => {}) } catch (_) {}
      }
      onManifestParsed && onManifestParsed(hls, videoEl)
    })

    hls.on(Hls.Events.ERROR, (_evt, data) => {
      if (data?.fatal) {
        console.warn('[hls-adapter] fatal error:', data.type, data.details)
        onError && onError(data)
        // 尝试恢复
        switch (data.type) {
          case Hls.ErrorTypes.NETWORK_ERROR:
            hls.startLoad()
            break
          case Hls.ErrorTypes.MEDIA_ERROR:
            hls.recoverMediaError()
            break
          default:
            try { hls.destroy() } catch (_) {}
            break
        }
      } else {
        console.warn('[hls-adapter] non-fatal:', data?.type, data?.details)
      }
    })

    videoEl.__hls = hls
    handle.hls = hls
  }

  if (hasHlsJs()) {
    startHls(window.Hls)
  } else {
    // index.html 脚本异常未就绪时的兜底路径
    loadHlsScript()
      .then(startHls)
      .catch((err) => {
        console.warn('[hls-adapter] hls.js unavailable, fallback to native src (will likely fail on Chrome):', err)
        if (!handle.destroyed) {
          videoEl.src = url
        }
      })
  }

  return handle
}
