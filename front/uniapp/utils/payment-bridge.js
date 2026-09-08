/**
 * 原生支付桥接层 —— Apple IAP / Google Play Billing / Apple Pay / Google Pay
 *
 * 设计目标:
 *   1. 统一 API: store.vue 只调这里
 *   2. 原生插件未就绪时优雅降级 (throw clear message)
 *   3. 条件编译隔离平台差异
 *
 * 后端端点 (已就绪):
 *   POST /api/user/orders/verify  { store: 'APPLE'|'GOOGLE', transactionId?, purchaseData?, purchaseSignature?, purchaseToken?, orderNo? }
 *
 * 依赖原生插件名: "PaymentBridge" (见 nativeplugins/payment-bridge/)
 *   Android: io.duanju.paymentbridge.PaymentBridgeModule
 *   iOS: PaymentBridge (UMeng 风格)
 */

import request from './request.js'

const PLUGIN_NAME = 'PaymentBridge'
let _plugin = null

function getPlugin() {
  if (_plugin) return _plugin
  // #ifdef APP-PLUS
  try {
    // #ifdef ANDROID
    _plugin = uni.requireNativePlugin(PLUGIN_NAME)
    // #endif
    // #ifdef IOS
    _plugin = uni.requireNativePlugin(PLUGIN_NAME)
    // #endif
  } catch (e) {
    console.warn('[PaymentBridge] native plugin not available:', e?.message)
  }
  // #endif
  return _plugin
}

/** 简单 Promise 包装 + 超时 */
function callNative(method, args, timeoutMs = 60000) {
  return new Promise((resolve, reject) => {
    const plugin = getPlugin()
    if (!plugin || typeof plugin[method] !== 'function') {
      reject(new Error(`原生支付插件未就绪 (${PLUGIN_NAME}.${method})。请重新打包 APP 或检查原生插件。`))
      return
    }
    let done = false
    const timer = setTimeout(() => {
      if (!done) {
        done = true
        reject(new Error('原生支付超时'))
      }
    }, timeoutMs)
    const callback = (result) => {
      if (done) return
      done = true
      clearTimeout(timer)
      if (result && result.code === 0) resolve(result)
      else reject(new Error(result?.message || '支付失败'))
    }
    try {
      plugin[method](args, callback)
    } catch (e) {
      clearTimeout(timer)
      reject(e)
    }
  })
}

/**
 * Apple IAP (StoreKit 2) —— 买积分包 / VIP 订阅
 * @param {string} sku App Store 配置的 product id / subscription id
 * @returns Promise<{ transactionId, originalTransactionId, receipt }>
 */
export function purchaseAppleIAP(sku) {
  // #ifdef APP-PLUS
  // #ifdef IOS
  return callNative('purchaseAppleIAP', { sku })
  // #endif
  // #ifdef ANDROID
  return Promise.reject(new Error('Apple IAP 仅支持 iOS 设备'))
  // #endif
  // #endif
  // #ifndef APP-PLUS
  return Promise.reject(new Error('Apple IAP 仅支持 APP 端'))
  // #endif
}

/**
 * Google Play Billing Library —— 买积分包 / VIP 订阅
 * @param {string} sku Google Play Console 配置的 inapp product id / subscription id
 * @returns Promise<{ purchaseData, purchaseSignature, purchaseToken, orderId }>
 */
export function purchaseGooglePlay(sku) {
  // #ifdef APP-PLUS
  // #ifdef ANDROID
  return callNative('purchaseGooglePlay', { sku })
  // #endif
  // #ifdef IOS
  return Promise.reject(new Error('Google Play Billing 仅支持 Android 设备'))
  // #endif
  // #endif
  // #ifndef APP-PLUS
  return Promise.reject(new Error('Google Play 仅支持 APP 端'))
  // #endif
}

/**
 * Apple Pay Wallet —— 刷 Apple Pay 银行卡支付 (需后端 Stripe Apple Pay Domain)
 * @param {number} amountCents 金额(分)
 * @param {string} currency 货币代码 (USD/EUR/JPY)
 * @param {string} countryCode 国家代码 (US/GB/JP)
 * @returns Promise<{ token }> Stripe PKToken
 */
export function purchaseApplePay(amountCents, currency, countryCode) {
  // #ifdef APP-PLUS
  // #ifdef IOS
  return callNative('purchaseApplePay', { amountCents, currency, countryCode })
  // #endif
  // #ifdef ANDROID
  return Promise.reject(new Error('Apple Pay 仅支持 iOS 设备'))
  // #endif
  // #endif
  // #ifndef APP-PLUS
  return Promise.reject(new Error('Apple Pay 仅支持 APP 端'))
  // #endif
}

/**
 * Google Pay Wallet —— 刷 Google Pay 银行卡支付 (需后端 Stripe)
 * @param {number} amountCents 金额(分)
 * @param {string} currency 货币代码
 * @param {string} countryCode 国家代码
 * @returns Promise<{ token }> Stripe PKToken
 */
export function purchaseGooglePay(amountCents, currency, countryCode) {
  // #ifdef APP-PLUS
  // #ifdef ANDROID
  return callNative('purchaseGooglePay', { amountCents, currency, countryCode })
  // #endif
  // #ifdef IOS
  return Promise.reject(new Error('Google Pay 仅支持 Android 设备'))
  // #endif
  // #endif
  // #ifndef APP-PLUS
  return Promise.reject(new Error('Google Pay 仅支持 APP 端'))
  // #endif
}

/**
 * 统一入口: 根据 payChannel 调对应原生 API
 * @param {string} payChannel 'APPLE_IAP' | 'GOOGLE_PLAY' | 'APPLE_PAY' | 'GOOGLE_PAY'
 * @param {object} params  { sku?, amountCents?, currency?, countryCode? }
 */
export async function purchase(payChannel, params) {
  switch (payChannel) {
    case 'APPLE_IAP':
      return purchaseAppleIAP(params.sku)
    case 'GOOGLE_PLAY':
      return purchaseGooglePlay(params.sku)
    case 'APPLE_PAY':
      return purchaseApplePay(params.amountCents, params.currency, params.countryCode)
    case 'GOOGLE_PAY':
      return purchaseGooglePay(params.amountCents, params.currency, params.countryCode)
    default:
      throw new Error(`未知支付通道: ${payChannel}`)
  }
}

/**
 * Apple Pay / Google Pay (Wallet) 拿到 Stripe PKToken 后, 需要创建 Stripe PaymentIntent confirm API.
 * 但后端目前只有 Stripe Checkout (web跳转), 没有 PaymentIntent confirm endpoint.
 * 这个 wrapper 先把 token 返回给调用方, 后续后端加 PaymentIntent confirm 后再集成.
 *
 * 同时 Apple IAP / Google Play Billing 原生支付完成后, 需要调后端 /api/user/orders/verify 验真.
 * 这个函数封装了完整的 "原生支付 → 后端验真/发放" 流程.
 */
export async function nativePayAndVerify({ payChannel, productSku, amountCents, currency, countryCode, orderNo }) {
  // 1. 调原生插件 —— 返回 { code: 0, data: {...}, message }
  const nativeResp = await purchase(payChannel, {
    sku: productSku,
    amountCents,
    currency,
    countryCode
  })
  const nativeData = nativeResp?.data || nativeResp
  if (nativeResp?.code === 0 || !nativeResp?.code) {
    // ok
  } else {
    throw new Error(nativeResp?.message || '原生支付失败')
  }

  // 2. 分渠道处理
  switch (payChannel) {
    case 'APPLE_IAP': {
      if (!nativeData.transactionId) throw new Error('原生支付未返回 transactionId')
      return request({
        url: '/user/orders/verify',
        method: 'POST',
        data: { store: 'APPLE', transactionId: nativeData.transactionId, orderNo }
      })
    }
    case 'GOOGLE_PLAY': {
      if (!nativeData.purchaseData) throw new Error('原生支付未返回 purchaseData')
      return request({
        url: '/user/orders/verify',
        method: 'POST',
        data: {
          store: 'GOOGLE',
          purchaseData: nativeData.purchaseData,
          purchaseSignature: nativeData.purchaseSignature,
          purchaseToken: nativeData.purchaseToken,
          orderNo
        }
      })
    }
    case 'APPLE_PAY':
    case 'GOOGLE_PAY': {
      console.warn('[PaymentBridge] Wallet 支付后端端点未就绪, PKToken:', nativeData.token)
      return { token: nativeData.token, walletFallback: true }
    }
    default:
      throw new Error(`未知支付通道: ${payChannel}`)
  }
}
