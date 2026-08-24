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
  uploadStorage(file, type) {
    const form = new FormData()
    form.append('file', file)
    form.append('type', type)
    return http.post('/admin/storage/upload', form)
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
  }
}
