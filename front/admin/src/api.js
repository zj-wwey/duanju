import axios from 'axios'
import { i18n } from './i18n/index.js'
import { STORAGE_KEY } from './i18n/loader.js'
import { resolveErrorMessage } from '../../shared/i18n/errorCodes.js'

function getCurrentLocale() {
  try {
    return localStorage.getItem(STORAGE_KEY) || i18n.global.locale.value || 'zh-CN'
  } catch (e) {
    return 'zh-CN'
  }
}

function t(key) {
  try {
    const msg = i18n.global.t(key)
    return msg !== key ? msg : key
  } catch (e) {
    return key
  }
}

function defaultBaseURL() {
  if (typeof window === 'undefined') {
    return 'http://127.0.0.1:8080/api'
  }
  // 如果通过 Vite dev server 访问(非 8080 端口),使用 /api 代理
  // Vite 配置了 proxy: /api -> http://127.0.0.1:8080
  return '/api'
}

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || defaultBaseURL(),
  timeout: 15000
})

let refreshingToken = null

http.interceptors.request.use(config => {
  const token = sessionStorage.getItem('adminToken') || sessionStorage.getItem('userToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  config.headers['X-Locale'] = getCurrentLocale()
  return config
})

http.interceptors.response.use(
  response => {
    // 对经代理的上传,若 Cloudflare WAF/代理拦截返回了 HTML(<!DOCTYPE html>/<html ...),直接报清晰中文
    const isProxiedUpload = !!(response.config?.__proxiedUpload)
    if (isProxiedUpload && typeof response.data === 'string' && /^\s*(<\?xml|<!doctype\s+html|<html\b)/i.test(response.data)) {
      console.error('[API] Proxied upload response was HTML (Cloudflare WAF / challenge / proxy intercept). url=', response.config?.url, 'status=', response.status)
      return Promise.reject(new Error('经 dash.marastel.com 的文件上传被 Cloudflare 代理/WAF 拦截（收到 HTML 挑战页，非 JSON 响应）。请改用 VPS 中转批量上传、R2/OSS/COS 对象存储预签名直传，或压缩视频至更小后重试。'))
    }
    const body = response.data
    if (body.code === 0) {
      return body.data
    }
    const backendMsg = body.message
    const localizedMsg = resolveErrorMessage(body.errorCode, t)
    const errorMsg = backendMsg && backendMsg !== ''
      ? backendMsg
      : (localizedMsg || t('common.requestFailed'))
    console.error('[API Error]', {
      url: response.config?.url,
      method: response.config?.method,
      params: response.config?.params,
      data: response.config?.data,
      errorCode: body.errorCode,
      backendMessage: body.message,
      localizedMessage: localizedMsg,
      stack: body.details || body.stackTrace
    })
    return Promise.reject(new Error(errorMsg))
  },
  async error => {
    if (error.code === 'ECONNABORTED') {
      return Promise.reject(new Error(t('common.requestFailed')))
    }
    const original = error.config || {}
    if (error.response?.status === 401 && !original._retry && !String(original.url || '').includes('/auth/refresh')) {
      const nextToken = await refreshAccessToken()
      if (nextToken) {
        original._retry = true
        original.headers = { ...(original.headers || {}), Authorization: `Bearer ${nextToken}` }
        return http(original)
      }
      clearExpiredAuth()
    }
    if (!error.response) {
      console.error('[API No Response]', {
        url: error.config?.url,
        method: error.config?.method,
        error: error.message
      })
      return Promise.reject(new Error(t('error.serverBusy')))
    }
    const body = error.response.data
    const backendMsg = body?.message || body?.error
    const localizedMsg = resolveErrorMessage(body?.errorCode, t)
    const message = backendMsg && backendMsg !== ''
      ? backendMsg
      : (localizedMsg || (typeof body === 'string' ? body : ''))
    const errorContext = {
      url: error.config?.url,
      method: error.config?.method,
      status: error.response.status,
      params: error.config?.params,
      errorCode: body?.errorCode,
      backendMessage: backendMsg
    }
    console.error('[API Error]', errorContext)
    if (error.response.status === 404) {
      return Promise.reject(new Error(message || t('error.resourceNotFound')))
    }
    if (error.response.status === 403) {
      return Promise.reject(new Error(message || t('error.forbidden')))
    }
    if (error.response.status === 401) {
      return Promise.reject(new Error(message || t('error.unauthorized')))
    }
    if (error.response.status >= 500) {
      return Promise.reject(new Error(message || t('error.serverBusy')))
    }
    return Promise.reject(new Error(message || t('common.requestFailed')))
  }
)

async function refreshAccessToken() {
  if (refreshingToken) return refreshingToken

  const adminRefreshToken = sessionStorage.getItem('adminRefreshToken')
  const userRefreshToken = sessionStorage.getItem('userRefreshToken')

  if (!adminRefreshToken && !userRefreshToken) {
    return null
  }

  refreshingToken = (async () => {
    if (adminRefreshToken) {
      try {
        const data = await http.post('/auth/refresh', { refreshToken: adminRefreshToken })
        sessionStorage.setItem('adminToken', data.token)
        sessionStorage.setItem('adminRefreshToken', data.refreshToken)
        return data.token
      } catch (e) {
        sessionStorage.removeItem('adminToken')
        sessionStorage.removeItem('adminRefreshToken')
      }
    }
    if (userRefreshToken) {
      try {
        const data = await http.post('/auth/refresh', { refreshToken: userRefreshToken })
        sessionStorage.setItem('userToken', data.token)
        sessionStorage.setItem('userRefreshToken', data.refreshToken)
        return data.token
      } catch (e) {
        sessionStorage.removeItem('userToken')
        sessionStorage.removeItem('userRefreshToken')
      }
    }
    return null
  })()
    .finally(() => {
      refreshingToken = null
    })

  return refreshingToken
}

function clearExpiredAuth() {
  for (const key of ['adminToken', 'adminRefreshToken', 'userToken', 'userRefreshToken', 'user']) {
    sessionStorage.removeItem(key)
  }
  window.dispatchEvent(new CustomEvent('duanju:auth-expired'))
}

export const api = {
  login(data) {
    return http.post('/auth/login', data)
  },
  adminLogin(data) {
    return http.post('/admin/auth/login', data)
  },
  register(data) {
    return http.post('/auth/register', data)
  },
  captcha(params = {}) {
    return http.get('/auth/captcha', { params })
  },
  publicStats() {
    return http.get('/auth/public-stats')
  },
  adminMe() {
    return http.get('/admin/auth/me')
  },
  publicCategoryFilters() {
    return http.get('/category-filters')
  },
  publicDramas(params = {}) {
    return http.get('/dramas', { params })
  },
  publicDrama(id) {
    return http.get(`/dramas/${id}`)
  },
  userMe() {
    return http.get('/user/me')
  },
  userUpdateProfile(data) {
    return http.put('/user/profile', data)
  },
  userChangePassword(data) {
    return http.put('/user/password', data)
  },
  userSettings() {
    return http.get('/user/settings')
  },
  userUpdateSettings(data) {
    return http.put('/user/settings', data)
  },
  userPoints() {
    return http.get('/user/points')
  },
  userCheckin() {
    return http.post('/user/checkin')
  },
  userToggleFavorite(dramaId) {
    return http.post(`/user/favorites/${dramaId}/toggle`)
  },
  userFavorites() {
    return http.get('/user/favorites')
  },
  userHistory() {
    return http.get('/user/history')
  },
  userSaveHistory(data) {
    return http.post('/user/history', data)
  },
  userDeleteHistory(dramaId) {
    return http.delete(`/user/history/${dramaId}`)
  },
  userClearHistory() {
    return http.delete('/user/history')
  },
  userUnlock(episodeId) {
    return http.post('/user/unlock', { episodeId })
  },
  userUnlockDrama(dramaId) {
    return http.post('/user/unlock-drama', { dramaId })
  },
  userOrders(params) {
    return http.get('/user/orders', { params })
  },
  userCreateOrder(data) {
    return http.post('/user/orders', data)
  },
  userStripeCheckout(orderNo) {
    return http.post('/user/orders/stripe-checkout', { orderNo })
  },
  userPayPalCheckout(orderNo) {
    return http.post('/user/orders/paypal-checkout', { orderNo })
  },
  userPayPalCapture(orderNo, paypalOrderId) {
    return http.post('/user/orders/paypal-capture', { orderNo, paypalOrderId })
  },
  userVerifyOrder(data) {
    return http.post('/user/orders/verify', data)
  },
  pointProducts() {
    return http.get('/point-products')
  },
  adminCategoryFilters() {
    return http.get('/admin/category-filters')
  },
  saveCategoryFilter(row) {
    return row.id ? http.put(`/admin/category-filters/${row.id}`, row) : http.post('/admin/category-filters', row)
  },
  deleteCategoryFilter(id) {
    return http.delete(`/admin/category-filters/${id}`)
  },
  dramas(params = {}) {
    return http.get('/admin/dramas', { params })
  },
  saveDrama(row) {
    return row.id ? http.put(`/admin/dramas/${row.id}`, row) : http.post('/admin/dramas', row)
  },
  deleteDrama(id) {
    return http.delete(`/admin/dramas/${id}`)
  },
  episodes(dramaId, params = {}) {
    return http.get(`/admin/dramas/${dramaId}/episodes`, { params })
  },
  applyFreePreview(dramaId, data) {
    return http.post(`/admin/dramas/${dramaId}/episodes/free-preview`, data)
  },
  saveEpisode(row) {
    return row.id ? http.put(`/admin/episodes/${row.id}`, row) : http.post('/admin/episodes', row)
  },
  deleteEpisode(id) {
    return http.delete(`/admin/episodes/${id}`)
  },
  batchCreateEpisodes(data) {
    return http.post('/admin/episodes/batch', data)
  },
  transcodeStatus(episodeId) {
    return http.get(`/admin/episodes/${episodeId}/transcode-status`)
  },
  retranscode(episodeId) {
    return http.post(`/admin/episodes/${episodeId}/retranscode`)
  },
  uploadStorage(file, type, onProgress) {
    const form = new FormData()
    form.append('file', file)
    form.append('type', type)
    return http.post('/admin/storage/upload', form, {
      onUploadProgress: onProgress,
      timeout: 600000,
      // 标记为「代理中转上传」:响应拦截器若收到 Cloudflare WAF 返回的 HTML(挑战页/拦截页),
      // 会给出清晰中文错误,不再兜底显示“服务繁忙”。
      __proxiedUpload: true
    })
  },
  // 签发 R2 预签名 PUT URL,允许浏览器直传,绕过 Cloudflare 代理 100MB / WAF 限制。
  // 默认视频签名有效期 30 分钟(传 500MB 视频需要较长时间,不设成 15s),后端会再卡上限 6 小时。
  presignStorage(fileName, contentType, type) {
    return http.post('/admin/storage/presign', null, {
      params: { fileName, contentType, type },
      timeout: 20000
    })
  },
  // 直传到 R2 预签名 URL (不经本服务/橙云代理,无 100MB 限制;单 PUT 最大 5GB,R2 S3 兼容
  // 端点对一次性 body 限制宽松,500MB 视频国内可稳定上传)。
  putToPresignedUrl(presignedUrl, file, headers, onProgress, signal) {
    return new Promise((resolve, reject) => {
      // 尽量把 R2/S3 XML 错误体翻译成中文可读的一行,避免只丢给用户「PUT failed: 403」这种无用信息。
      const parseR2Error = (status, rawText) => {
        if (!rawText) return null
        const text = String(rawText)
        // R2/S3 标准错误格式 <Error><Code>XXX</Code><Message>...</Message></Error>
        const matchCode = text.match(/<Code>([^<]+)<\/Code>/i)
        const matchMsg = text.match(/<Message>([^<]+)<\/Message>/i)
        const code = (matchCode && matchCode[1]) || ''
        const msg = (matchMsg && matchMsg[1]) || ''
        if (!code && !msg) return null
        const zhMap = {
          AccessDenied: 'R2 拒绝访问(403)。常见原因:预签名 URL 的 Content-Type/请求头与 PUT 时不一致;或桶 bucket 策略/Token 有效期过期。',
          SignatureDoesNotMatch: 'R2 签名不匹配。检查:AWS 访问密钥是否正确(32 字符);endpoint 是否包含 https 前缀;PUT 时 Content-Type 是否与 presign 时一致。',
          ExpiredToken: 'R2 预签名 URL 已过期,重新点本地上传触发新的预签名。',
          NoSuchKey: 'R2 对象不存在(通常是 GET/PUT 路径写错,检查桶绑定域名与 object key 是否对应)。',
          BadRequest: 'R2 收到格式错误的请求(Bad Request)。常见原因:presign 请求参数里 fileName 含非法字符/中文未编码。'
        }
        const zh = zhMap[code] || ''
        const combined = [
          `R2 错误码:${code || '(未知)'}`,
          msg ? `详情:${msg}` : null,
          zh ? `中文说明:${zh}` : null,
          `HTTP 状态:${status}`
        ].filter(Boolean).join(';')
        return combined.length > 0 ? combined : null
      }

      const xhr = new XMLHttpRequest()
      xhr.open('PUT', presignedUrl, true)
      if (signal) {
        if (signal.aborted) { reject(new Error('UPLOAD_CANCELLED')); return }
        signal.addEventListener('abort', () => {
          xhr.abort()
          reject(new Error('UPLOAD_CANCELLED'))
        })
      }
      // L3-fix (A): 显式设置 responseType=blob,绝不以默认文本方式读取跨源媒体 binary 的响应体。
      // Chromium 128+ ORB (Origin Restriction Boundary) 会拦截跨源媒体(MP4/JPEG/PNG)的文本 body,
      // 即使真实 HTTP 2xx,也会把 xhr.responseText/xhr.response 吃掉抛 net:ERR_BLOCKED_BY_ORB。
      // responseType=blob 可绕过,且我们本来就不需要读 PUT 的响应体(只看 status + loaded 字节数)。
      xhr.responseType = 'blob'
      let totalSent = 0
      if (headers) {
        Object.entries(headers).forEach(([k, v]) => {
          if (k && v) xhr.setRequestHeader(k, v)
        })
      }
      if (onProgress) {
        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable) {
            totalSent = e.loaded
            onProgress(e)
          }
        }
      }
      // L3-fix (B): 成功判定只看 2xx 状态码 + 已发字节数,不读 responseText(读 media body 会触发 ORB)。
      xhr.onload = () => {
        const loaded = xhr.upload && xhr.upload.loaded ? xhr.upload.loaded : totalSent
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve({ status: xhr.status, loaded })
        } else {
          // 非 2xx 才尝试读错误响应文本(blob -> text),S3/R2 4xx/5xx 错误一般 <512B,不怕 ORB
          const errBlob = (xhr.response instanceof Blob) ? xhr.response : null
          const parseErrAsText = errBlob ? errBlob.text().catch(() => '') : Promise.resolve('')
          parseErrAsText.then((errText) => {
            const r2Msg = parseR2Error(xhr.status, errText || '')
            const plain = (errText || '').toString().slice(0, 300)
            const detail = r2Msg
              ? r2Msg
              : (plain ? `响应片段:${plain}` : `HTTP ${xhr.status} ${xhr.statusText || ''}`)
            const err = new Error(`上传到 R2 失败(PUT ${xhr.status})。${detail}${r2Msg ? '' : '。请打开 Network 查看对应 PUT 请求的响应体,或检查 R2 桶 CORS/自定义域名是否为橙云 Active。'}`)
            err.response = { status: xhr.status, statusText: xhr.statusText, data: errText }
            reject(err)
          }).catch(reject)
        }
      }
      // L3-fix (C): onerror 加 ORB 兜底: 字节全发出去 + status=0 且 loaded==file.size => 真实是 R2 2xx,Chrome 把 body 吞了报 ORB,按成功处理。
      xhr.onerror = () => {
        const loaded = xhr.upload && xhr.upload.loaded ? xhr.upload.loaded : totalSent
        if (file && typeof file.size === 'number' && loaded === file.size && file.size > 0) {
          // 典型 ORB:所有字节都发到 R2,R2 返回 200,但 Chrome 在 V8 层把 Response Body 吃掉抛 ORB 为 onerror。
          // 这种情况按成功处理(R2 桶内对象已真实存在且字节数一致,不影响后续落库播放)。
          // 给 onProgress 一个 100% 的假事件,确保前端进度条不卡 99%。
          if (onProgress) onProgress({ lengthComputable: true, loaded: file.size, total: file.size })
          resolve({ status: 299, loaded, orbBypassed: true })
          return
        }
        // 真正网络错误(如 CORS 未放行 / DNS / WAF 拦截 0 字节 / 完全断网)
        const hint = '常见原因:R2 bucket 的 CORS 策略未放行 https://dash.marastel.com 的 PUT/GET,请在 Cloudflare R2→桶→Settings→CORS Policy 添加 AllowedOrigins=[https://dash.marastel.com],AllowedMethods=[GET,PUT]。'
        const err = new Error(`上传到 R2 失败:网络错误(Network Error)。已发送 ${loaded} 字节/共 ${(file && file.size) ? file.size : '未知'} 字节。${hint}`)
        err.response = null
        reject(err)
      }
      xhr.ontimeout = () => {
        const err = new Error('上传到 R2 失败:请求超时(1 小时上限)。请检查上行网络,或改用后台「VPS中转上传」Tab。')
        err.code = 'ECONNABORTED'
        reject(err)
      }
      xhr.timeout = 3600000 // 1 小时上限(单 PUT 传 500MB 国内上行慢时够用)
      xhr.send(file)
    })
  },
  uploadStream(file, name, dramaId, onProgress) {
    const form = new FormData()
    form.append('file', file)
    if (name) form.append('name', name)
    if (dramaId) form.append('dramaId', dramaId)
    return http.post('/admin/video/upload', form, {
      onUploadProgress: onProgress,
      timeout: 600000,
      // 标记为「代理中转上传」,响应拦截器发现是 HTML(WAF 挑战页/代理拦截)时给出清晰中文错误
      __proxiedUpload: true
    })
  },
  // 创建 Cloudflare Stream 上传资源,返回 uid + uploadURL (浏览器直传用)
  initStreamUpload(name, dramaId) {
    return http.post('/admin/video/init-upload', null, {
      params: { name, dramaId },
      timeout: 30000
    })
  },
  // 直传视频到 Cloudflare Stream uploadURL (不经本服务/橙云代理,无 100MB 限制)
  // Cloudflare Stream 要求 POST 而非 PUT (旧版 PUT 会报 CORS 错误)
  postToStreamUploadUrl(uploadUrl, file, onProgress) {
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      xhr.open('POST', uploadUrl, true)
      if (onProgress) {
        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable) onProgress(e)
        }
      }
      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve({ status: xhr.status })
        } else {
          const err = new Error(`POST Stream upload URL failed: ${xhr.status}`)
          err.response = { status: xhr.status, statusText: xhr.statusText }
          reject(err)
        }
      }
      xhr.onerror = () => {
        const err = new Error('Network Error')
        err.response = null
        reject(err)
      }
      xhr.ontimeout = () => {
        const err = new Error('Request timeout')
        err.code = 'ECONNABORTED'
        reject(err)
      }
      xhr.timeout = 1800000 // 30 分钟上限
      xhr.send(file)
    })
  },
  scanBatchUpload(dramaId) {
    return http.get('/admin/batch-upload/scan', { params: { dramaId } })
  },
  startBatchUpload(dramaId, startEpisodeNo = 1) {
    return http.post('/admin/batch-upload/start', null, { params: { dramaId, startEpisodeNo } })
  },
  batchUploadStatus(dramaId) {
    return http.get('/admin/batch-upload/status', { params: { dramaId } })
  },
  adminDashboard(params = {}) {
    return http.get('/admin/dashboard', { params })
  },
  analyticsOverview() {
    return http.get('/admin/analytics/overview')
  },
  analyticsDramas(params) {
    return http.get('/admin/analytics/dramas', { params })
  },
  analyticsEpisodes(params) {
    return http.get('/admin/analytics/episodes', { params })
  },
  analyticsTrend(params) {
    return http.get('/admin/analytics/trend', { params })
  },
  analyticsViewers(params) {
    return http.get('/admin/analytics/viewers', { params })
  },
  analyticsEpisodeReach(params) {
    return http.get('/admin/analytics/episode-reach', { params })
  },
  users(params) {
    return http.get('/admin/users', { params })
  },
  createUser(data) {
    return http.post('/admin/users', data)
  },
  updateUser(id, data) {
    return http.put(`/admin/users/${id}`, data)
  },
  deleteUser(id) {
    return http.delete(`/admin/users/${id}`)
  },
  updateUserStatus(id, data) {
    return http.put(`/admin/users/${id}/status`, data)
  },
  adjustUserPoints(id, data) {
    return http.post(`/admin/users/${id}/points`, data)
  },
  orders(params) {
    return http.get('/admin/orders', { params })
  },
  markOrderPaid(orderNo) {
    return http.post(`/admin/orders/${orderNo}/pay`)
  },
  refundOrder(orderNo) {
    return http.post(`/admin/orders/${orderNo}/refund`)
  },
  updateOrderStatus(orderNo, data) {
    return http.put(`/admin/orders/${orderNo}/status`, data)
  },
  admins() {
    return http.get('/admin/system/admins')
  },
  roles() {
    return http.get('/admin/system/roles')
  },
  saveRole(row) {
    return row.id ? http.put(`/admin/system/roles/${row.id}`, row) : http.post('/admin/system/roles', row)
  },
  deleteRole(id) {
    return http.delete(`/admin/system/roles/${id}`)
  },
  assignAdminRoles(id, roleIds) {
    return http.put(`/admin/system/admins/${id}/roles`, { roleIds })
  },
  createAdmin(data) {
    return http.post('/admin/system/admins', data)
  },
  updateAdmin(id, data) {
    return http.put(`/admin/system/admins/${id}`, data)
  },
  deleteAdmin(id) {
    return http.delete(`/admin/system/admins/${id}`)
  },
  operationLogs(params) {
    return http.get('/admin/system/operation-logs', { params })
  },

  // --- Announcement APIs ---
  announcements() {
    return http.get('/announcements')
  },
  announcementDetail(id) {
    return http.get(`/announcements/${id}`)
  },
  announcementUnreadCount() {
    return http.get('/announcements/unread-count')
  },
  markAnnouncementRead(id) {
    return http.post(`/announcements/${id}/read`)
  },
  adminAnnouncements(params = {}) {
    return http.get('/admin/announcements', { params })
  },
  createAnnouncement(data) {
    return http.post('/admin/announcements', data)
  },
  updateAnnouncement(id, data) {
    return http.put(`/admin/announcements/${id}`, data)
  },
  deleteAnnouncement(id) {
    return http.delete(`/admin/announcements/${id}`)
  },
  publishAnnouncement(id) {
    return http.post(`/admin/announcements/${id}/publish`)
  },
  toggleAnnouncementTop(id) {
    return http.put(`/admin/announcements/${id}/top`)
  },

  // --- Package/Product APIs ---
  adminPointProducts(params = {}) {
    return http.get('/admin/point-products', { params })
  },
  createPointProduct(data) {
    return http.post('/admin/point-products', data)
  },
  updatePointProduct(id, data) {
    return http.put(`/admin/point-products/${id}`, data)
  },
  deletePointProduct(id) {
    return http.delete(`/admin/point-products/${id}`)
  },
  updatePointProductStatus(id, status) {
    return http.put(`/admin/point-products/${id}/status`, { status })
  },

  // --- VIP APIs ---
  userVipStatus() {
    return http.get('/user/vip/status')
  },
  userVipRecords(params = {}) {
    return http.get('/user/vip/records', { params })
  },

  // --- Membership APIs ---
  /** 当前会员等级、权益、进度条 */
  membershipStatus() {
    return http.get('/membership/status')
  },
  /** 各等级权益对比表 */
  membershipBenefits() {
    return http.get('/membership/benefits')
  },
  /** 积分兑换会员天数 */
  membershipExchange(body) {
    return http.post('/membership/exchange', body)
  },
  /** 会员等级变更历史 */
  membershipHistory(params = {}) {
    return http.get('/membership/history', { params })
  },

  // --- Shop APIs ---
  /** 积分商城商品列表 */
  shopItems() {
    return http.get('/shop/items')
  },
  /** 兑换商品 */
  shopExchange(id) {
    return http.post(`/shop/exchange/${id}`)
  },
  /** 兑换记录 */
  shopRecords() {
    return http.get('/shop/records')
  },

  // --- Auto-Renewal APIs ---
  /** 取消自动续费 */
  autoRenewalCancel() {
    return http.post('/user/auto-renewal/cancel')
  },
  /** 自动续费状态 */
  autoRenewalStatus() {
    return http.get('/user/auto-renewal/status')
  },

  // --- Feedback APIs ---
  submitFeedback(data) {
    return http.post('/feedback', data)
  },
  myFeedback(params = {}) {
    return http.get('/feedback', { params })
  },
  adminFeedback(params = {}) {
    return http.get('/admin/feedback', { params })
  },
  replyFeedback(id, data) {
    return http.post(`/admin/feedback/${id}/reply`, data)
  },
  updateFeedbackStatus(id, status) {
    return http.put(`/admin/feedback/${id}/status`, { status })
  },
  deleteFeedbackAdmin(id) {
    return http.delete(`/admin/feedback/${id}`)
  },

  // --- Currency Rate APIs ---
  getCurrencyRates() {
    return http.get('/admin/currency-rates/list')
  },
  createCurrencyRate(data) {
    return http.post('/admin/currency-rates', data)
  },
  updateCurrencyRate(id, data) {
    return http.put(`/admin/currency-rates/${id}`, data)
  },
  deleteCurrencyRate(id) {
    return http.delete(`/admin/currency-rates/${id}`)
  },
  refreshCurrencyRateCache() {
    return http.post('/admin/currency-rates/refresh')
  },

  // --- Admin Profile APIs ---
  getAdminProfile() {
    return http.get('/admin/profile')
  },
  updateAdminProfile(data) {
    return http.put('/admin/profile', data)
  },
  changeAdminPassword(data) {
    return http.put('/admin/profile/password', data)
  },
  // 管理员头像上传 (multipart)
  uploadAdminAvatar(file, onProgress) {
    const form = new FormData()
    form.append('file', file)
    return http.post('/admin/profile/avatar', form, {
      onUploadProgress: onProgress,
      timeout: 120000
    })
  },
  // C 端用户头像上传 (multipart,管理员代用户上传场景)
  uploadUserAvatar(file, onProgress) {
    const form = new FormData()
    form.append('file', file)
    return http.post('/user/avatar', form, {
      onUploadProgress: onProgress,
      timeout: 120000
    })
  },

  // --- Admin Membership APIs ---
  /** 会员用户列表 */
  adminMembershipList(params = {}) {
    return http.get('/admin/membership/list', { params })
  },
  /** 单用户会员详情 */
  adminMembershipDetail(userId) {
    return http.get(`/admin/membership/${userId}`)
  },
  /** 活动赠送积分 */
  adminGrantPoints(body) {
    return http.post('/admin/membership/points/grant', body)
  },
  /** 手动调整会员等级 */
  adminMembershipAdjust(userId, data) {
    return http.post(`/admin/membership/${userId}/adjust`, data)
  },
  /** 会员统计 */
  adminMembershipStats() {
    return http.get('/admin/membership/stats')
  },

  // --- Admin Shop APIs ---
  /** 积分商城商品列表 */
  adminShopItems(params = {}) {
    return http.get('/admin/shop/items', { params })
  },
  /** 创建积分商城商品 */
  adminShopCreateItem(data) {
    return http.post('/admin/shop/items', data)
  },
  /** 更新积分商城商品 */
  adminShopUpdateItem(id, data) {
    return http.put(`/admin/shop/items/${id}`, data)
  },
  /** 删除积分商城商品 */
  adminShopDeleteItem(id) {
    return http.delete(`/admin/shop/items/${id}`)
  },
  /** 上下架积分商城商品 */
  adminShopUpdateItemStatus(id, status) {
    return http.put(`/admin/shop/items/${id}/status`, { status })
  },
  /** 积分商城兑换记录 */
  adminShopOrders(params = {}) {
    return http.get('/admin/shop/orders', { params })
  },
  /** 更新发货状态 */
  adminShopUpdateDelivery(id, data) {
    return http.put(`/admin/shop/orders/${id}/delivery`, data)
  },

  // --- Admin Point Record APIs ---
  /** 积分记录列表 */
  adminPointRecords(params = {}) {
    return http.get('/admin/point-records', { params })
  },
  /** 积分统计 */
  adminPointRecordStats(params = {}) {
    return http.get('/admin/point-records/stats', { params })
  },

  // --- Admin Auto-Renewal APIs ---
  /** 自动续费订阅列表 */
  adminAutoRenewalList(params = {}) {
    return http.get('/admin/auto-renewal/list', { params })
  },
  /** 管理员取消用户自动续费 */
  adminAutoRenewalCancel(userId) {
    return http.post(`/admin/auto-renewal/${userId}/cancel`)
  },

  // --- Admin Comment Moderation APIs ---
  /** 评论列表（可按 status / dramaId 过滤） */
  adminComments(params = {}) {
    return http.get('/admin/comments', { params })
  },
  /** 管理员强制删除单条评论（status → -2） */
  adminDeleteComment(id) {
    return http.delete(`/admin/comments/${id}`)
  },
  /** 管理员恢复被删评论（status → 1） */
  adminRestoreComment(id) {
    return http.post(`/admin/comments/${id}/restore`)
  },
  /** 批量删除评论 */
  adminBatchDeleteComments(ids) {
    return http.post('/admin/comments/batch-delete', { ids })
  }
}
