#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
#import <UniversePlugin/UniversePlugin.h>

/**
 * iOS 原生支付插件 (uni-app DCUniModule)
 *
 * 依赖 (在宿主 Podfile 中添加):
 *   pod 'Stripe', '~> 23.12'
 *   pod 'StripeApplePay', '~> 23.12'
 *
 * Capabilities (Xcode → Signing & Capabilities):
 *   ✅ In-App Purchase
 *   ✅ Apple Pay
 *
 * 暴露给 JS 的 4 个方法:
 *   purchaseAppleIAP    → StoreKit 1 购买积分包/VIP
 *   purchaseGooglePlay   → Android only (返回错误)
 *   purchaseApplePay     → Stripe Apple Pay Wallet
 *   purchaseGooglePay    → Android only
 *
 * StoreKit 2 是 Swift-only, 这里用 StoreKit 1 SKPaymentQueue (ObjC 兼容)
 */
@interface PaymentBridge : DCUniModule <SKProductsRequestDelegate, SKPaymentTransactionObserver>

// JS 可调用方法 (需要 UNI_EXPORT_METHOD)
- (void)purchaseAppleIAP:(NSDictionary *)args callback:(UniModuleKeepAliveCallback)callback;
- (void)purchaseGooglePlay:(NSDictionary *)args callback:(UniModuleKeepAliveCallback)callback;
- (void)purchaseApplePay:(NSDictionary *)args callback:(UniModuleKeepAliveCallback)callback;
- (void)purchaseGooglePay:(NSDictionary *)args callback:(UniModuleKeepAliveCallback)callback;

@end
