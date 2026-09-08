#import "PaymentBridge.h"
#import <StoreKit/StoreKit.h>
#import <PassKit/PassKit.h>

// Stripe 相关 —— 如果宿主没集成 Stripe, 这些 import 会报错.
// 把这两行注释掉也能编译, Apple Pay 方法会返回友好提示.
#if __has_include(<Stripe/Stripe.h>)
    #import <Stripe/Stripe.h>
    #define HAS_STRIPE 1
#else
    #define HAS_STRIPE 0
#endif

@implementation PaymentBridge {
    UniModuleKeepAliveCallback _activeIAPCallback;
    UniModuleKeepAliveCallback _activeApplePayCallback;
    NSString *_activeSku;
}

// ====================================================================
// DCUniModule 基础配置
// ====================================================================

UNI_EXPORT_METHOD(@selector(purchaseAppleIAP:callback:))
UNI_EXPORT_METHOD(@selector(purchaseGooglePlay:callback:))
UNI_EXPORT_METHOD(@selector(purchaseApplePay:callback:))
UNI_EXPORT_METHOD(@selector(purchaseGooglePay:callback:))

- (void)moduleDidLoad {
    [super moduleDidLoad];
    // 注册 StoreKit 交易观察者 (uni-app 原生插件标准生命周期)
    [[SKPaymentQueue defaultQueue] addTransactionObserver:self];
    NSLog(@"[PaymentBridge] moduleDidLoad — StoreKit observer registered");
}

- (void)dealloc {
    [[SKPaymentQueue defaultQueue] removeTransactionObserver:self];
}

// ====================================================================
// purchaseAppleIAP — StoreKit 1 应用内购买
// ====================================================================

- (void)purchaseAppleIAP:(NSDictionary *)args callback:(UniModuleKeepAliveCallback)callback {
    NSString *sku = args[@"sku"];
    if (!sku.length) {
        callback([self errorResult:@"sku 不能为空"]);
        return;
    }
    if (![SKPaymentQueue canMakePayments]) {
        callback([self errorResult:@"该设备不支持 In-App Purchase, 请在设置中启用"]);
        return;
    }

    _activeIAPCallback = callback;
    _activeSku = sku;

    // 1. 先同步处理队列中 pending 交易 (防止上次中断)
    [self finishPendingTransactions];

    // 2. 查询 App Store 产品信息
    NSSet *productIDs = [NSSet setWithObject:sku];
    SKProductsRequest *request = [[SKProductsRequest alloc] initWithProductIdentifiers:productIDs];
    request.delegate = self;
    [request start];
}

#pragma mark - SKProductsRequestDelegate

- (void)productsRequest:(SKProductsRequest *)request didReceiveResponse:(SKProductsResponse *)response {
    if (!_activeIAPCallback) return;

    SKProduct *product = nil;
    for (SKProduct *p in response.products) {
        if ([p.productIdentifier isEqualToString:_activeSku]) { product = p; break; }
    }
    if (!product) {
        NSString *invalid = [response.invalidProductIdentifiers componentsJoinedByString:@","];
        _activeIAPCallback([self errorResult:[NSString stringWithFormat:@"App Store 未找到商品: %@ (无效: %@)", _activeSku, invalid]]);
        _activeIAPCallback = nil;
        return;
    }

    SKPayment *payment = [SKPayment paymentWithProduct:product];
    [[SKPaymentQueue defaultQueue] addPayment:payment];
}

- (void)request:(SKRequest *)request didFailWithError:(NSError *)error {
    if (_activeIAPCallback) {
        _activeIAPCallback([self errorResult:[NSString stringWithFormat:@"查询商品失败: %@", error.localizedDescription]]);
        _activeIAPCallback = nil;
    }
}

#pragma mark - SKPaymentTransactionObserver

- (void)paymentQueue:(SKPaymentQueue *)queue updatedTransactions:(NSArray<SKPaymentTransaction *> *)transactions {
    for (SKPaymentTransaction *tx in transactions) {
        switch (tx.transactionState) {
            case SKPaymentTransactionStatePurchasing:
                // 等待用户操作
                break;
            case SKPaymentTransactionStatePurchased:
                [self handlePurchased:tx queue:queue];
                break;
            case SKPaymentTransactionStateRestored:
                [self finishTransaction:tx queue:queue];
                break;
            case SKPaymentTransactionStateFailed:
                [self handleFailed:tx queue:queue];
                break;
            case SKPaymentTransactionStateDeferred:
                // 等待家长批准
                break;
        }
    }
}

- (void)handlePurchased:(SKPaymentTransaction *)tx queue:(SKPaymentQueue *)queue {
    if (_activeIAPCallback && [tx.payment.productIdentifier isEqualToString:_activeSku]) {
        NSMutableDictionary *data = [NSMutableDictionary dictionary];
        data[@"transactionId"] = tx.transactionIdentifier ?: @"";
        data[@"originalTransactionId"] = tx.originalTransaction?.transactionIdentifier ?: @"";
        data[@"productId"] = tx.payment.productIdentifier;
        data[@"transactionDate"] = @((long)(tx.transactionDate.timeIntervalSince1970 * 1000));

        // 可选: 附带 receipt (App Store Server API 验真也可以用 transactionId 单独查)
        NSURL *receiptURL = [[NSBundle mainBundle] appStoreReceiptURL];
        NSData *receiptData = [NSData dataWithContentsOfURL:receiptURL];
        data[@"receipt"] = receiptData ? [receiptData base64EncodedStringWithOptions:0] : @"";

        _activeIAPCallback([self okResult:data]);
        _activeIAPCallback = nil;
        _activeSku = nil;
    }
    [queue finishTransaction:tx];
}

- (void)handleFailed:(SKPaymentTransaction *)tx queue:(SKPaymentQueue *)queue {
    NSError *err = tx.error;
    NSString *msg = @"支付失败";
    if (err.domain == SKErrorDomain) {
        switch (err.code) {
            case SKErrorPaymentCancelled: msg = @"用户取消"; break;
            case SKErrorPaymentNotAllowed: msg = @"该设备不允许支付"; break;
            case SKErrorPaymentInvalid:    msg = @"支付无效"; break;
            case SKErrorStoreProductNotAvailable: msg = @"商品不可用"; break;
            default: msg = err.localizedDescription ?: @"支付失败";
        }
    } else {
        msg = err.localizedDescription ?: @"支付失败";
    }
    if (_activeIAPCallback && [tx.payment.productIdentifier isEqualToString:_activeSku]) {
        _activeIAPCallback([self errorResult:msg]);
        _activeIAPCallback = nil;
        _activeSku = nil;
    }
    [queue finishTransaction:tx];
}

- (void)finishTransaction:(SKPaymentTransaction *)tx queue:(SKPaymentQueue *)queue {
    [queue finishTransaction:tx];
}

- (void)finishPendingTransactions {
    for (SKPaymentTransaction *tx in [SKPaymentQueue defaultQueue].transactions) {
        if (tx.transactionState == SKPaymentTransactionStatePurchased ||
            tx.transactionState == SKPaymentTransactionStateRestored) {
            [[SKPaymentQueue defaultQueue] finishTransaction:tx];
        }
    }
}

// ====================================================================
// purchaseApplePay — Stripe Apple Pay Wallet
// ====================================================================

- (void)purchaseApplePay:(NSDictionary *)args callback:(UniModuleKeepAliveCallback)callback {
    NSNumber *amountCents = args[@"amountCents"] ?: @0;
    NSString *currency    = args[@"currency"] ?: @"USD";
    NSString *countryCode = args[@"countryCode"] ?: @"US";

    if (![PKPaymentAuthorizationController canMakePayments]) {
        callback([self errorResult:@"该设备不支持 Apple Pay, 请在钱包中添加卡片"]);
        return;
    }

#if HAS_STRIPE
    // TODO: 需要 Stripe publishableKey + Apple Pay merchant id
    //
    // 完整流程:
    //   1. [STPPaymentConfiguration sharedConfiguration].applePayMerchantIdentifier = @"merchant.com.duanju"
    //   2. 构造 STPApplePayPaymentMethodParams: amount, currency, country
    //   3. [STPApplePayContext presentApplePayWithParams:...]
    //   4. didCreatePaymentMethod → PKToken → 回传给 JS
    //
    // 但当前后端没有 Stripe PaymentIntent confirm 端点.
    // 建议后端先加 POST /api/user/orders/stripe-payment-intent (创建 PI → 返回 client_secret),
    // 再补全这段代码.
    callback([self errorResult:@"Apple Pay Wallet 后端端点未就绪. 请后端先实现 Stripe PaymentIntent 接口."]);
#else
    callback([self errorResult:@"Stripe SDK 未集成. 请在 Podfile 中添加 pod 'Stripe' 和 pod 'StripeApplePay'"]);
#endif
}

// ====================================================================
// purchaseGooglePlay / purchaseGooglePay — Android only
// ====================================================================

- (void)purchaseGooglePlay:(NSDictionary *)args callback:(UniModuleKeepAliveCallback)callback {
    callback([self errorResult:@"Google Play Billing 仅支持 Android 设备"]);
}

- (void)purchaseGooglePay:(NSDictionary *)args callback:(UniModuleKeepAliveCallback)callback {
    callback([self errorResult:@"Google Pay Wallet 仅支持 Android 设备"]);
}

// ====================================================================
// Helpers
// ====================================================================

- (NSDictionary *)okResult:(NSDictionary *)data {
    return @{ @"code": @0, @"data": data ?: @{} };
}

- (NSDictionary *)errorResult:(NSString *)msg {
    return @{ @"code": @(-1), @"message": msg ?: @"unknown error" };
}

@end
