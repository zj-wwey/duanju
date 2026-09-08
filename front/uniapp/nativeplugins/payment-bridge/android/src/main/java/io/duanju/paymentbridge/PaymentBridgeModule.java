package io.duanju.paymentbridge;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import io.dcloud.feature.uniapp.annotation.UniJSMethod;
import io.dcloud.feature.uniapp.bridge.UniJSCallback;
import io.dcloud.feature.uniapp.common.UniModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Android 原生支付模块 (uni-app WXModule)
 *
 * 依赖 (在宿主 app/build.gradle 中添加):
 *   implementation "com.android.billingclient:billing:6.1.0"
 *   implementation "com.stripe:stripe-android:20.52.1"
 *   implementation "com.stripe:stripe-googlepay:20.52.1"
 *
 * AndroidManifest 需要 (宿主 AndroidManifest.xml):
 *   <uses-permission android:name="com.android.vending.BILLING"/>
 *
 * 暴露给 JS 的 4 个方法:
 *   purchaseAppleIAP    → iOS only (返回错误)
 *   purchaseGooglePlay  → Google Play Billing (inapp / subscription)
 *   purchaseApplePay    → iOS only
 *   purchaseGooglePay   → Stripe + Google Pay
 */
public class PaymentBridgeModule extends UniModule {

    private static final String TAG = "PaymentBridge";

    // -------- BillingClient (Google Play) --------
    private static BillingClient billingClient;
    private static boolean billingReady = false;
    private static final List<Runnable> pendingBilling = new CopyOnWriteArrayList<>();
    private static UniJSCallback activePlayCallback;
    private static ProductDetails activeProductDetails;

    // -------- Stripe Google Pay --------
    private com.stripe.android.googlepaylauncher.GooglePayLauncher stripeGooglePayLauncher;
    private UniJSCallback activeStripePayCallback;

    // ====================================================================
    // JS 入口方法
    // ====================================================================

    /** purchaseAppleIAP — Android 直接返回错误 */
    @UniJSMethod(uiThread = false)
    public void purchaseAppleIAP(@Nullable Map<String, Object> args, @Nullable UniJSCallback callback) {
        if (callback != null) callback.invokeAndKeepAlive(error("Apple IAP 仅支持 iOS 设备"));
    }

    /**
     * purchaseGooglePlay — Google Play Billing
     * args: { "sku": "com.duanju.points100" }
     */
    @UniJSMethod(uiThread = false)
    public void purchaseGooglePlay(@Nullable Map<String, Object> args, @Nullable UniJSCallback callback) {
        if (callback == null) return;
        final String sku = args == null ? null : String.valueOf(args.get("sku"));
        if (sku == null || sku.isEmpty()) {
            callback.invokeAndKeepAlive(error("sku 不能为空"));
            return;
        }
        this.activePlayCallback = callback;

        ensureBillingReady(new Runnable() {
            @Override
            public void run() {
                queryAndLaunch(sku);
            }
        });
    }

    /** purchaseApplePay — Android 直接返回错误 */
    @UniJSMethod(uiThread = false)
    public void purchaseApplePay(@Nullable Map<String, Object> args, @Nullable UniJSCallback callback) {
        if (callback != null) callback.invokeAndKeepAlive(error("Apple Pay 仅支持 iOS 设备"));
    }

    /**
     * purchaseGooglePay — Stripe + Google Pay Wallet
     * args: { "amountCents": 999, "currency": "USD", "countryCode": "US" }
     */
    @UniJSMethod(uiThread = false)
    public void purchaseGooglePay(@Nullable Map<String, Object> args, @Nullable UniJSCallback callback) {
        if (callback == null) return;
        final long amountCents = args == null ? 0L : toLong(args.get("amountCents"));
        final String currency  = args == null ? "USD" : String.valueOf(args.getOrDefault("currency", "USD"));
        final String countryCode = args == null ? "US" : String.valueOf(args.getOrDefault("countryCode", "US"));

        this.activeStripePayCallback = callback;

        try {
            Activity act = (Activity) mUniSDKInstance.getContext();
            if (act == null) {
                callback.invokeAndKeepAlive(error("无法获取 Activity 上下文"));
                return;
            }

            // 检查 Google Pay 是否可用
            com.stripe.android.googlepaylauncher.GooglePayAvailabilityChecker checker =
                    new com.stripe.android.googlepaylauncher.DefaultGooglePayAvailabilityChecker(act);
            if (!checker.isAvailable()) {
                callback.invokeAndKeepAlive(error("该设备不支持 Google Pay"));
                return;
            }

            // Google Pay Wallet 需要 Stripe PaymentIntent clientSecret — 当前后端没这个端点
            callback.invokeAndKeepAlive(error(
                    "Google Pay Wallet 后端端点未就绪. " +
                    "请后端实现 POST /api/user/orders/stripe-payment-intent 接口返回 client_secret."));

        } catch (Exception e) {
            Log.e(TAG, "purchaseGooglePay error", e);
            callback.invokeAndKeepAlive(error("Google Pay 初始化失败: " + e.getMessage()));
        }
    }

    // ====================================================================
    // Google Play Billing 内部逻辑
    // ====================================================================

    private synchronized void ensureBillingReady(Runnable onReady) {
        if (billingReady && billingClient != null) {
            onReady.run();
            return;
        }
        Activity act = (Activity) mUniSDKInstance.getContext();
        if (act == null) {
            onReady.run(); // caller 会报 NPE, 但不应该发生
            return;
        }

        pendingBilling.add(onReady);

        if (billingClient != null) return; // 已经在初始化中

        PurchasesUpdatedListener purchasesUpdatedListener = new PurchasesUpdatedListener() {
            @Override
            public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
                handlePurchasesUpdated(billingResult, purchases);
            }
        };

        billingClient = BillingClient.newBuilder(act.getApplicationContext())
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases()
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    billingReady = true;
                    Log.i(TAG, "BillingClient connected, pending=" + pendingBilling.size());
                    List<Runnable> tasks = new ArrayList<>(pendingBilling);
                    pendingBilling.clear();
                    for (Runnable r : tasks) r.run();
                } else {
                    Log.e(TAG, "BillingClient failed: " + billingResult.getDebugMessage());
                    billingReady = false;
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                Log.w(TAG, "BillingClient disconnected");
                billingReady = false;
            }
        });
    }

    private void queryAndLaunch(final String sku) {
        // 先尝试 INAPP, 失败再尝试 SUBS (也可以并行)
        queryProductDetails(BillingClient.ProductType.INAPP, sku, new ProductQueryCallback() {
            @Override
            public void onSuccess(ProductDetails details) {
                launchBilling(details);
            }
            @Override
            public void onFailed(String msg) {
                queryProductDetails(BillingClient.ProductType.SUBS, sku, new ProductQueryCallback() {
                    @Override
                    public void onSuccess(ProductDetails details) {
                        launchBilling(details);
                    }
                    @Override
                    public void onFailed(String msg2) {
                        if (activePlayCallback != null) {
                            activePlayCallback.invokeAndKeepAlive(error("商品未找到: " + sku + " (" + msg + ", " + msg2 + ")"));
                            activePlayCallback = null;
                        }
                    }
                });
            }
        });
    }

    private void queryProductDetails(String type, String sku, final ProductQueryCallback cb) {
        List<QueryProductDetailsParams.Product> productList = new ArrayList<>();
        productList.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(sku)
                .setProductType(type)
                .build());

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build();

        billingClient.queryProductDetailsAsync(params, new ProductDetailsResponseListener() {
            @Override
            public void onProductDetailsResponse(@NonNull BillingResult billingResult,
                                                 @NonNull List<ProductDetails> productDetailsList) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && !productDetailsList.isEmpty()) {
                    cb.onSuccess(productDetailsList.get(0));
                } else {
                    cb.onFailed(billingResult.getDebugMessage());
                }
            }
        });
    }

    private void launchBilling(ProductDetails details) {
        this.activeProductDetails = details;
        Activity act = (Activity) mUniSDKInstance.getContext();
        if (act == null) {
            if (activePlayCallback != null) activePlayCallback.invokeAndKeepAlive(error("Activity 丢失"));
            return;
        }

        BillingFlowParams.ProductDetailsParams.Builder pdb = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details);

        // 订阅需要 offerToken
        if (details.getProductType().equals(BillingClient.ProductType.SUBS)) {
            List<ProductDetails.SubscriptionOfferDetails> offers = details.getSubscriptionOfferDetails();
            if (offers != null && !offers.isEmpty()) {
                pdb.setOfferToken(offers.get(0).getOfferToken());
            }
        }

        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(java.util.Collections.singletonList(pdb.build()))
                .build();

        BillingResult result = billingClient.launchBillingFlow(act, flowParams);
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            if (activePlayCallback != null) {
                activePlayCallback.invokeAndKeepAlive(error("拉起支付失败: " + result.getDebugMessage()));
                activePlayCallback = null;
            }
        }
    }

    private void handlePurchasesUpdated(BillingResult result, List<Purchase> purchases) {
        int code = result.getResponseCode();
        if (code == BillingClient.BillingResponseCode.USER_CANCELED) {
            if (activePlayCallback != null) {
                activePlayCallback.invokeAndKeepAlive(error("用户取消"));
                activePlayCallback = null;
            }
            return;
        }
        if (code != BillingClient.BillingResponseCode.OK || purchases == null || purchases.isEmpty()) {
            if (activePlayCallback != null) {
                activePlayCallback.invokeAndKeepAlive(error("支付失败: " + result.getDebugMessage()));
                activePlayCallback = null;
            }
            return;
        }

        for (Purchase p : purchases) {
            if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                Map<String, Object> data = new HashMap<>();
                data.put("purchaseData", p.getOriginalJson());
                data.put("purchaseSignature", p.getSignature());
                data.put("purchaseToken", p.getPurchaseToken());
                data.put("orderId", p.getOrderId());
                data.put("purchaseTime", p.getPurchaseTime());
                data.put("productId", p.getProducts().isEmpty() ? "" : p.getProducts().get(0));

                // 关键区别:
                //   INAPP (积分包等消耗品) → consumeAsync, 这样用户可重复购买
                //   SUBS (订阅)           → acknowledgePurchase
                if (activeProductDetails != null
                        && BillingClient.ProductType.INAPP.equals(activeProductDetails.getProductType())) {
                    consumePurchase(p.getPurchaseToken());
                } else {
                    acknowledgePurchase(p.getPurchaseToken());
                }

                if (activePlayCallback != null) {
                    activePlayCallback.invokeAndKeepAlive(success(data));
                    activePlayCallback = null;
                }
                return;
            }
        }
    }

    private void consumePurchase(String purchaseToken) {
        try {
            ConsumeParams params = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchaseToken).build();
            billingClient.consumeAsync(params, new ConsumeResponseListener() {
                @Override
                public void onConsumeResponse(@NonNull BillingResult billingResult, @NonNull String outToken) {
                    Log.i(TAG, "consume: code=" + billingResult.getResponseCode() + " token=" + outToken);
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "consume failed", e);
        }
    }

    private void acknowledgePurchase(String purchaseToken) {
        try {
            AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchaseToken).build();
            billingClient.acknowledgePurchase(params, new AcknowledgePurchaseResponseListener() {
                @Override
                public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult) {
                    Log.i(TAG, "ack: " + billingResult.getResponseCode());
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "ack failed", e);
        }
    }

    // ====================================================================
    // Helpers
    // ====================================================================

    private Map<String, Object> error(String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", -1);
        m.put("message", msg);
        return m;
    }

    private Map<String, Object> success(Object data) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", 0);
        m.put("data", data == null ? new HashMap<>() : data);
        return m;
    }

    private static long toLong(Object o) {
        if (o == null) return 0L;
        try { return ((Number) o).longValue(); } catch (Exception e) {
            try { return Long.parseLong(String.valueOf(o)); } catch (Exception e2) { return 0L; }
        }
    }

    private interface ProductQueryCallback {
        void onSuccess(ProductDetails details);
        void onFailed(String msg);
    }
}
