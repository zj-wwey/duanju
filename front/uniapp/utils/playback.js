function pickPlayUrl(data, fallback) {
  if (!data) return fallback
  return data.hlsUrl || data.hls_url || data.signedUrl || data.signed_url || data.videoUrl || data.video_url || fallback
}

export async function resolveEpisodeSource(episodeId, fallbackUrl, api) {
  let source = fallbackUrl
  if (episodeId && api && api.playUrl) {
    try {
      const data = await api.playUrl(episodeId)
      source = pickPlayUrl(data, source)
    } catch (_) {}
  }
  return source
}
