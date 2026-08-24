import config from './config.js'
import { getLocale, t } from './i18n.js'
import { resolveErrorMessage } from '../../shared/i18n/errorCodes.js'

let refreshPromise = null

// 后端历史数据中的硬编码主机（127.0.0.1 / localhost / 0.0.0.0）
// 这些地址在真机/模拟器上无法访问，必须按当前请求的 API_BASE_URL 重写
const URL_FIELD_KEYS = /(url|cover(_url)?|image(_url)?|avatar|poster|video(_url)?|hls(_url)?|signed_url|signedUrl|icon|banner|thumbnail)$/i

/**
 * 补齐相对路径为完整 URL。
 * 如 /uploads/images/test.jpg -> http://10.0.2.2:8080/uploads/images/test.jpg
 */
function resolveRelativeUrl(value) {
  if (typeof value !== 'string' || !value) return value
  if (!value.startsWith('/')) return value
  const base = config.API_BASE_URL || ''
  const origin = base.indexOf('/api') > -1 ? base.slice(0, base.indexOf('/api')) : base.replace(/\/$/, '')
  return origin + value
}

function rewriteHost(value) {
  if (typeof value !== 'string' || !value) return value
  // 先补齐相对路径
  let resolved = resolveRelativeUrl(value)
  // 再重写硬编码主机
  const re = /^(https?:\/\/|\/\/)(127\.0\.0\.1|localhost|0\.0\.0\.0)(:\d+)?/i
  const match = re.exec(resolved)
  if (!match) return resolved
  const base = config.API_BASE_URL
  if (!base) return resolved
  try {
    const end = base.indexOf('/api')
    const origin = end > 0 ? base.slice(0, end) : base.replace(/\/$/, '')
    let proto = match[1].startsWith('http') ? match[1] : 'http:'
    let host = '127.0.0.1'
    let port = match[3] || ''
    try {
      const parsed = new URL(origin)
      proto = parsed.protocol
      host = parsed.hostname
      if (parsed.port) port = ':' + parsed.port
    } catch (_) {
      const m = /^(https?):\/\/([^/:]+)(:\d+)?/.exec(origin)
      if (m) {
        proto = m[1] + ':'  // 加上冒号: 'http' -> 'http:'
        host = m[2]
        if (m[3]) port = m[3]
      }
    }
    return proto + '//' + host + port + resolved.slice(match[0].length)
  } catch (_) {
    return resolved
  }
}

/**
 * 深度遍历响应数据，把 URL 字段中的硬编码主机替换为当前 API 主机。
 * 同时处理以 / 开头的相对路径，补齐为完整地址。
 */
function normalizeResponse(value) {
  if (value == null) return value
  if (Array.isArray(value)) return value.map(normalizeResponse)
  if (typeof value === 'object') {
    const out = {}
    for (const key of Object.keys(value)) {
      const child = value[key]
      if (typeof child === 'string' && child && URL_FIELD_KEYS.test(key)) {
        out[key] = rewriteHost(child)
      } else if (typeof child === 'string' && child && /^\/uploads?\//.test(child)) {
        out[key] = resolveRelativeUrl(child)
      } else {
        out[key] = normalizeResponse(child)
      }
    }
    return out
  }
  return value
}

function clientType() {
  let type = 'WEB'
  // #ifdef APP-PLUS
  type = 'APP'
  // #endif
  // #ifdef H5
  type = 'H5'
  // #endif
  return type
}

function clientVersion() {
  try {
    // #ifdef APP-PLUS
    return plus.runtime.version || 'APP'
    // #endif
  } catch (_) {}
  return 'UNIAPP'
}

function request(options) {
  const token = uni.getStorageSync('token')
  const locale = getLocale()
  const isGet = (options.method || 'GET').toUpperCase() === 'GET'
  return new Promise((resolve, reject) => {
    const headers = {
      'X-Locale': locale,
      'X-Client-Type': clientType(),
      'X-Client-Version': clientVersion(),
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
      ...(options.header || {})
    }
    if (!isGet) {
      headers['Content-Type'] = 'application/json'
    }

    const fullUrl = config.API_BASE_URL + options.url

    // 设置整体超时保护（比 uni.request 自身超时更安全）
    const safetyTimeout = setTimeout(() => {
      reject(new Error('网络请求超时，请检查网络连接'))
    }, 15000)

    uni.request({
      url: fullUrl,
      method: options.method || 'GET',
      data: isGet ? (options.data || '') : (options.data || {}),
      header: headers,
      timeout: 10000,
      success(res) {
        clearTimeout(safetyTimeout)

        if (res.statusCode === 401) {
          handle401(options, resolve, reject)
          return
        }
        if (res.statusCode === 403) {
          const body = res.data || {}
          reject(new Error(resolveErrorMessage(body.errorCode, t) || body.message || t('requestFailed')))
          return
        }
        if (res.statusCode >= 500) {
          reject(new Error(t('error.serverBusy') || '服务器繁忙，请稍后重试'))
          return
        }
        const body = res.data || {}
        if (body.code === 0) {
          resolve(normalizeResponse(body.data))
        } else {
          const localizedMsg = resolveErrorMessage(body.errorCode, t)
          reject(new Error(localizedMsg || body.message || t('requestFailed')))
        }
      },
      fail(err) {
        clearTimeout(safetyTimeout)
        reject(new Error(err.errMsg || '网络请求失败，请检查网络连接'))
      }
    })
  })
}

function handle401(options, resolve, reject) {
  // 如果已经在重试了，直接拒绝，避免无限循环
  if (options._retry) {
    uni.removeStorageSync('token')
    uni.removeStorageSync('refreshToken')
    reject(new Error(t('pleaseSignIn') || '请重新登录'))
    return
  }

  const refreshResult = ensureRefreshToken()
  // 添加超时保护，防止 token 刷新挂起
  const timeoutPromise = new Promise((_, rejectTimeout) => {
    setTimeout(() => rejectTimeout(new Error('token_refresh_timeout')), 5000)
  })

  Promise.race([refreshResult, timeoutPromise]).then(nextToken => {
    if (!nextToken) {
      uni.removeStorageSync('token')
      uni.removeStorageSync('refreshToken')
      reject(new Error(t('pleaseSignIn') || '请重新登录'))
      return
    }
    // 用新 token 重试请求
    request({ ...options, _retry: true }).then(resolve).catch(reject)
  }).catch(() => {
    uni.removeStorageSync('token')
    uni.removeStorageSync('refreshToken')
    reject(new Error(t('pleaseSignIn') || '登录已过期，请重新登录'))
  })
}

function ensureRefreshToken() {
  if (refreshPromise) return refreshPromise
  refreshPromise = refreshToken().finally(() => {
    refreshPromise = null
  })
  return refreshPromise
}

function refreshToken() {
  const refreshTokenValue = uni.getStorageSync('refreshToken')
  if (!refreshTokenValue) {
    return Promise.resolve(null)
  }
  return new Promise((resolve) => {
    const timeout = setTimeout(() => {
      resolve(null)
    }, 5000)
    uni.request({
      url: config.API_BASE_URL + '/auth/refresh',
      method: 'POST',
      data: { refreshToken: refreshTokenValue },
      header: {
        'Content-Type': 'application/json',
        'X-Locale': getLocale(),
        'X-Client-Type': clientType(),
        'X-Client-Version': clientVersion()
      },
      success(res) {
        clearTimeout(timeout)
        const body = res.data || {}
        if (res.statusCode === 200 && body.code === 0 && body.data?.token) {
          uni.setStorageSync('token', body.data.token)
          uni.setStorageSync('refreshToken', body.data.refreshToken)
          resolve(body.data.token)
        } else {
          resolve(null)
        }
      },
      fail() {
        clearTimeout(timeout)
        resolve(null)
      }
    })
  })
}

export default request
