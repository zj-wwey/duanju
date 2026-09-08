import request from './request.js'

export const api = {
  register(data) {
    return request({ url: '/auth/register', method: 'POST', data })
  },
  login(data) {
    return request({ url: '/auth/login', method: 'POST', data })
  },
  captcha(scene = 'LOGIN') {
    return request({ url: '/auth/captcha?scene=' + scene })
  },
  refresh(refreshToken) {
    return request({ url: '/auth/refresh', method: 'POST', data: { refreshToken } })
  },
  me() {
    return request({ url: '/user/me' })
  },
  categoryFilters() {
    return request({ url: '/category-filters' })
  },
  categories() {
    return this.categoryFilters().then(groups => {
      const contentTypes = (groups || []).find(group => group.key === 'contentType')
      return (contentTypes?.options || []).map(option => ({
        id: option.key,
        name: option.label
      }))
    })
  },
  dramas(params) {
    if (typeof params === 'string') {
      return request({ url: params ? '/dramas?contentType=' + encodeURIComponent(params) : '/dramas' })
    }
    const query = Object.entries(params || {})
      .filter(([, value]) => value !== undefined && value !== null && value !== '')
      .map(([key, value]) => encodeURIComponent(key) + '=' + encodeURIComponent(value))
      .join('&')
    return request({ url: query ? '/dramas?' + query : '/dramas' })
  },
  drama(id) {
    return request({ url: '/dramas/' + id })
  },
  feed(params) {
    const query = Object.entries(params || {})
      .filter(([, value]) => value !== undefined && value !== null && value !== '')
      .map(([key, value]) => encodeURIComponent(key) + '=' + encodeURIComponent(value))
      .join('&')
    return request({ url: '/dramas/feed' + (query ? '?' + query : '') })
  },
  playUrl(episodeId) {
    return request({ url: '/user/video/play/' + episodeId })
  },
  toggleFavorite(dramaId) {
    return request({ url: '/user/favorites/' + dramaId + '/toggle', method: 'POST' })
  },
  favorites() {
    return request({ url: '/user/favorites' })
  },
  toggleLike(dramaId) {
    return request({ url: '/user/likes/' + dramaId + '/toggle', method: 'POST' })
  },
  dramaComments(dramaId, page = 1, pageSize = 20) {
    return request({ url: '/dramas/' + dramaId + '/comments?page=' + page + '&pageSize=' + pageSize })
  },
  dramaCommentReplies(rootId, page = 1, pageSize = 20) {
    return request({ url: '/dramas/comments/' + rootId + '/replies?page=' + page + '&pageSize=' + pageSize })
  },
  addComment(dramaId, content, episodeId) {
    return request({ url: '/user/comments', method: 'POST', data: { dramaId, episodeId, content } })
  },
  replyComment(dramaId, episodeId, parentId, replyToUserId, content) {
    return request({ url: '/user/comments/reply', method: 'POST',
      data: { dramaId, episodeId, parentId, replyToUserId, content } })
  },
  deleteComment(commentId) {
    return request({ url: '/user/comments/' + commentId, method: 'DELETE' })
  },
  // ---- 通知 ----
  notifications(page = 1, pageSize = 20) {
    return request({ url: '/user/notifications?page=' + page + '&pageSize=' + pageSize })
  },
  notificationUnreadCount() {
    return request({ url: '/user/notifications/unread-count' })
  },
  markAllNotificationsRead() {
    return request({ url: '/user/notifications/read-all', method: 'POST' })
  },
  markNotificationRead(id) {
    return request({ url: '/user/notifications/' + id + '/read', method: 'POST' })
  },
  saveHistory(data) {
    return request({ url: '/user/history', method: 'POST', data })
  },
  histories() {
    return request({ url: '/user/history' })
  },
  checkin() {
    return request({ url: '/user/checkin', method: 'POST' })
  },
  points() {
    return request({ url: '/user/points' })
  },
  pointProducts() {
    return request({ url: '/point-products' })
  },
  productsByLocale() {
    return request({ url: '/user/products-by-locale' })
  },
  createOrder(productId, payChannel) {
    const payload = typeof productId === 'object' ? productId : { productId, payChannel }
    if (!payload.payChannel) {
      return Promise.reject(new Error('请选择支付方式'))
    }
    return request({ url: '/user/orders', method: 'POST', data: payload })
  },
  stripeCheckout(orderNo) {
    return request({ url: '/user/orders/stripe-checkout', method: 'POST', data: { orderNo } })
  },
  paypalCheckout(orderNo) {
    return request({ url: '/user/orders/paypal-checkout', method: 'POST', data: { orderNo } })
  },
  paypalCapture(orderNo, paypalOrderId) {
    return request({ url: '/user/orders/paypal-capture', method: 'POST', data: { orderNo, paypalOrderId } })
  },
  verifyOrder(data) {
    return request({ url: '/user/orders/verify', method: 'POST', data })
  },
  unlock(episodeId) {
    return request({ url: '/user/unlock', method: 'POST', data: { episodeId } })
  },
  unlockDrama(dramaId) {
    return request({ url: '/user/unlock-drama', method: 'POST', data: { dramaId } })
  },
  unlockEpisodePreview(episodeId) {
    return request({ url: '/user/unlock/preview/episode/' + episodeId })
  },
  unlockDramaPreview(dramaId) {
    return request({ url: '/user/unlock/preview/drama/' + dramaId })
  },
  getProfile() {
    return request({ url: '/user/me' })
  },
  updateProfile(data) {
    return request({ url: '/user/profile', method: 'PUT', data })
  },
  updatePassword(data) {
    return request({ url: '/user/password', method: 'PUT', data })
  },
  getSettings() {
    return request({ url: '/user/settings' })
  },
  updateSettings(data) {
    return request({ url: '/user/settings', method: 'PUT', data })
  },
  deleteHistory(dramaId) {
    return request({ url: '/user/history/' + dramaId, method: 'DELETE' })
  },
  clearHistory() {
    return request({ url: '/user/history', method: 'DELETE' })
  },
  orders() {
    return request({ url: '/user/orders' })
  },
  adReward(adSlot = 'default', traceId) {
    return request({ url: '/user/ad-reward', method: 'POST', data: { adSlot, traceId: traceId || String(Date.now()) } })
  },
  adRewards() {
    return request({ url: '/user/ad-rewards' })
  },
  rewardEpisode(episodeId) {
    return request({ url: '/user/reward/episode/' + episodeId, method: 'POST' })
  },
  rewardShare(dramaId) {
    return request({ url: '/user/reward/share/' + dramaId, method: 'POST' })
  },
  rewardDuration(minutes) {
    return request({ url: '/user/reward/duration', method: 'POST', data: { minutes } })
  },

  announcements() {
    return request({ url: '/announcements' })
  },
  announcementRead(id) {
    return request({ url: '/announcements/' + id + '/read', method: 'POST' })
  },
  announcementUnreadCount() {
    return request({ url: '/announcements/unread-count' })
  },

  vipStatus() {
    return request({ url: '/user/vip/status' })
  },
  vipRecords() {
    return request({ url: '/user/vip/records' })
  },

  submitFeedback(data) {
    return request({ url: '/feedback', method: 'POST', data })
  },
  myFeedback() {
    return request({ url: '/feedback' })
  },
  feedbackDetail(id) {
    return request({ url: '/feedback/' + id })
  },
  updateFeedback(id, data) {
    return request({ url: '/feedback/' + id, method: 'PUT', data })
  },
  deleteFeedback(id) {
    return request({ url: '/feedback/' + id, method: 'DELETE' })
  },

  // 会员体系
  membershipStatus() {
    return request({ url: '/membership/status' })
  },
  membershipBenefits() {
    return request({ url: '/membership/benefits' })
  },
  membershipExchange(data) {
    return request({ url: '/membership/exchange', method: 'POST', data })
  },
  membershipHistory() {
    return request({ url: '/membership/history' })
  },

  // 积分商城
  shopItems() {
    return request({ url: '/shop/items' })
  },
  shopExchange(id) {
    return request({ url: '/shop/exchange/' + id, method: 'POST' })
  },
  shopRecords() {
    return request({ url: '/shop/records' })
  },

  // 自动续费
  autoRenewalStatus() {
    return request({ url: '/user/auto-renewal/status' })
  },
  autoRenewalSubscribe(data) {
    return request({ url: '/user/auto-renewal/subscribe', method: 'POST', data })
  },
  autoRenewalCancel() {
    return request({ url: '/user/auto-renewal/cancel', method: 'POST' })
  }
}

export default api
