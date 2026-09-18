/**
 * 跨端视频源统一解析
 * 后端 API 返回字段可能是 snake_case (hls_url, video_url) 或 camelCase (hlsUrl, videoUrl)
 *
 * 策略：
 * - 有 hlsUrl → 用 m3u8（HLS），后端 playback_type="hls"
 * - 没有 hlsUrl → 用 mp4，后端 playback_type="direct"
 *
 * 解析顺序：playback_url → hlsUrl/hls_url → signedUrl/signed_url → videoUrl/video_url
 */

export function resolvePlayUrl(raw) {
  if (!raw) return { url: '', type: 'direct' }

  const pb = raw.playback_url || raw.playbackUrl
  if (pb) {
    const isHls = pb.includes('.m3u8')
    return { url: pb, type: isHls ? 'hls' : 'direct' }
  }

  // 回退：优先 HLS
  const hls = raw.hlsUrl || raw.hls_url
  if (hls) return { url: hls, type: 'hls' }

  const signed = raw.signedUrl || raw.signed_url
  if (signed) return { url: signed, type: 'direct' }

  const video = raw.videoUrl || raw.video_url
  if (video) return { url: video, type: 'direct' }

  return { url: '', type: 'direct' }
}

export function isHlsUrl(url) {
  return typeof url === 'string' && url.includes('.m3u8')
}

export async function resolveEpisodeSource(episodeId, fallbackUrl, api) {
  if (episodeId && api && api.playUrl) {
    try {
      const data = await api.playUrl(episodeId)
      const { url } = resolvePlayUrl(data)
      if (url) return url
    } catch (_) {}
  }
  return fallbackUrl
}
