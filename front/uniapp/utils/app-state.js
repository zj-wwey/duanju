export const APP_DATA_EVENTS = {
  user: 'user',
  points: 'points',
  favorite: 'favorite',
  history: 'history',
  order: 'order',
  membership: 'membership',
  shop: 'shop'
}

export function notifyDataChanged(type, payload = {}) {
  try {
    uni.$emit('app:data-changed', { type, payload, at: Date.now() })
  } catch (_) {}
}

export function onDataChanged(vm, handler) {
  const listener = event => handler && handler(event || {})
  uni.$on('app:data-changed', listener)
  const original = vm.$options.beforeDestroy
  vm.$options.beforeDestroy = Array.isArray(original) ? original : (original ? [original] : [])
  vm.$options.beforeDestroy.push(function cleanupAppDataChanged() {
    uni.$off('app:data-changed', listener)
  })
}

export function hasToken() {
  return !!uni.getStorageSync('token')
}

export function requireLogin() {
  if (hasToken()) return true
  uni.navigateTo({ url: '/pages/login/login' })
  return false
}
