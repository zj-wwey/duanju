package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.service.AppleIapService;
import com.duanju.service.CloudflareStreamService;
import com.duanju.service.GooglePlayIapService;
import com.duanju.service.PayPalPaymentService;
import com.duanju.service.StripePaymentService;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 第三方平台 Webhook 接收端点。
 *
 * <p>所有端点位于 /api/webhooks/** 下,不经过 AuthInterceptor 鉴权
 * (路径不以 /api/user/ 或 /api/admin/ 开头),改用各平台约定的签名或自定义 token 校验。</p>
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {
    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final AppleIapService appleIapService;
    private final StripePaymentService stripePaymentService;
    private final GooglePlayIapService googlePlayIapService;
    private final CloudflareStreamService cloudflareStreamService;
    private final PayPalPaymentService payPalPaymentService;

    @Value("${duanju.apple-iap.webhook-auth-token:}")
    private String webhookAuthToken;

    @Value("${duanju.cloudflare.webhook-auth-token:}")
    private String cloudflareWebhookToken;

    public WebhookController(AppleIapService appleIapService,
                             StripePaymentService stripePaymentService,
                             GooglePlayIapService googlePlayIapService,
                             CloudflareStreamService cloudflareStreamService,
                             PayPalPaymentService payPalPaymentService) {
        this.appleIapService = appleIapService;
        this.stripePaymentService = stripePaymentService;
        this.googlePlayIapService = googlePlayIapService;
        this.cloudflareStreamService = cloudflareStreamService;
        this.payPalPaymentService = payPalPaymentService;
    }

    /**
     * Apple App Store Server Notification V2 接收端点。
     *
     * <p>配置方法:登录 App Store Connect → 用户和访问 → 密钥 → App Store Server API →
     * 设置 "生产服务器 URL" 和 "沙盒服务器 URL" 为 https://yourdomain.com/api/webhooks/apple/notify</p>
     *
     * <p>请求体格式:{"signedPayload":"<JWS>"}
     * Apple 期望收到 HTTP 2xx 表示成功;非 2xx 会触发重试 (最多 10 次,指数退避)。</p>
     *
     * <p>鉴权:可选,通过 X-Duanju-Webhook-Token 自定义 header 校验
     * (在 App Store Connect 无法配置该 header,需在 CDN/反向代理层注入,或依赖 JWS 签名验证)。</p>
     */
    @PostMapping("/apple/notify")
    public R<Void> appleNotify(@RequestBody Map<String, String> body,
                               @RequestHeader(value = "X-Duanju-Webhook-Token", required = false) String token) {
        // 1. 必填:自定义 token 校验 (由反向代理层注入,App Store Connect 本身不支持自定义 header)
        if (webhookAuthToken == null || webhookAuthToken.isBlank()) {
            log.error("Apple webhook token not configured, ignoring notification (returning 200 to stop Apple retries)");
            return R.ok();
        }
        if (!webhookAuthToken.equals(token)) {
            throw new IllegalArgumentException("invalid webhook token");
        }
        // 2. 校验 Apple IAP 是否启用
        if (!appleIapService.isEnabled()) {
            log.error("Apple IAP not enabled, ignoring notification (returning 200 to stop Apple retries)");
            return R.ok();
        }
        // 3. 取出 signedPayload
        String signedPayload = body == null ? null : body.get("signedPayload");
        if (signedPayload == null || signedPayload.isBlank()) {
            throw new IllegalArgumentException("missing signedPayload");
        }
        // 4. 验签 + 解码 + 业务处理 (内部已捕获业务异常,确保返回 200 不触发 Apple 重试;
        //    仅验签失败抛 IllegalArgumentException,Apple 重试也无意义)
        appleIapService.handleNotification(signedPayload);
        return R.ok();
    }

    /**
     * Stripe Webhook 接收端点。
     *
     * <p>配置方法:登录 Stripe Dashboard → Developers → Webhooks → Add endpoint →
     * Endpoint URL = https://yourdomain.com/api/webhooks/stripe/notify →
     * 监听事件: checkout.session.completed、payment_intent.succeeded、charge.refunded →
     * 拿到 Signing secret (whsec_xxx),配置到 STRIPE_WEBHOOK_SECRET 环境变量。</p>
     *
     * <p>关键实现注意:
     * <ul>
     *   <li>必须用 HttpServletRequest.getInputStream() 读取 RAW payload,
     *       不能用 @RequestBody Map/String,因为 Spring 的 MappingJackson2HttpMessageConverter
     *       会重新序列化 JSON 导致 key 顺序/空格变化,与 Stripe-Signature 计算所用的原文不一致,
     *       从而导致 Webhook.SignatureVerification 永远失败。</li>
     *   <li>必须读取 Stripe-Signature header (小写亦可,Servlet 自动 canonicalize),
     *       传给 Webhook.constructEvent 做签名校验,防止第三方伪造 webhook event。</li>
     *   <li>任何业务异常 (重复发积分、商品不存在等) 内部 catch 吞掉,返回 200,
     *       避免 Stripe 无意义重试 (幂等唯一索引保证不会重复发积分)。</li>
     * </ul>
     *
     * <p>Stripe 期望 2xx 表示成功接收;非 2xx 会触发指数退避重试 (最多 3 天,~20 次后放弃)。</p>
     */
    @PostMapping("/stripe/notify")
    public R<Void> stripeNotify(HttpServletRequest request,
                                @RequestHeader("Stripe-Signature") String stripeSignatureHeader) {
        if (!stripePaymentService.isEnabled()) {
            // 如果 Stripe 未启用仍收到 webhook:直接 200,不抛异常 (运维在 Dashboard 配错时不产生告警风暴)
            return R.ok();
        }
        // 1. 读取 RAW payload (保留原文字节,不经过 JSON 解析重新序列化)
        String payload;
        try (ServletInputStream is = request.getInputStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                baos.write(buf, 0, n);
            }
            payload = baos.toString(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            // 读取请求体失败时,返回 4xx (Stripe 会重试,下次可能网络正常)
            throw new IllegalArgumentException("cannot read stripe request payload: " + ex.getMessage());
        }
        // 2. 签名校验 + 分发 (内部:constructEvent → switch eventType → markPaidByStore / 退款)
        //    业务异常已在 handleWebhook 里 catch 并落日志,这里永远返回 200 (除非签名非法直接抛 IAE)
        stripePaymentService.handleWebhook(payload, stripeSignatureHeader);
        return R.ok();
    }

    /**
     * Google Play RTDN (Real-time Developer Notifications) Pub/Sub 接收端点。
     *
     * <p>配置方法:登录 Google Play Console → Monetize → Real-time developer notifications →
     * 将 Webhook URL 设置为 https://yourdomain.com/api/webhooks/google/notify,
     * 选择 "Send notifications for: One-time products"。</p>
     *
     * <p>请求体格式 (Pub/Sub push):</p>
     * <pre>
     * {
     *   "message": {
     *     "attributes": { "packageName": "...", "eventId": "...", "eventType": "ONE_TIME_PRODUCT_PURCHASED" },
     *     "data": "base64-encoded-PurchaseOrder-JSON",
     *     "messageId": "...",
     *     "publishTime": "..."
     *   },
     *   "subscription": "..."
     * }
     * </pre>
     *
     * <p>鉴权:可选,通过 X-Duanju-Webhook-Token header 校验,
     * 需在 Google Cloud Pub/Sub 订阅中自定义 header (或由反向代理层注入)。</p>
     *
     * <p>Google RTDN 期望 2xx 表示成功接收;非 2xx 会触发重试。</p>
     */
    @PostMapping("/google/notify")
    public R<Void> googleNotify(@RequestBody Map<String, Object> payload,
                                @RequestHeader(value = "X-Duanju-Webhook-Token", required = false) String token) {
        // 1. 必填:自定义 token 校验
        String googleToken = googlePlayIapService.getWebhookAuthToken();
        if (googleToken == null || googleToken.isBlank()) {
            log.error("Google webhook token not configured, ignoring notification (returning 200 to stop Google retries)");
            return R.ok();
        }
        if (!googleToken.equals(token)) {
            throw new IllegalArgumentException("invalid google webhook token");
        }
        // 2. 校验 Google Play IAP 是否启用
        if (!googlePlayIapService.isEnabled()) {
            log.error("Google Play IAP not enabled, ignoring notification (returning 200 to stop Google retries)");
            return R.ok();
        }
        // 3. 委托给 GooglePlayIapService 处理 RTDN 事件
        googlePlayIapService.handleRtdnEvent(payload);
        return R.ok();
    }

    /**
     * PayPal Webhook 接收端点。
     *
     * <p>配置方法:登录 PayPal Developer Dashboard → Apps → Create App →
     * 设置 Webhook URL = https://yourdomain.com/api/webhooks/paypal/notify →
     * 订阅事件: PAYMENT.CAPTURE.COMPLETED、PAYMENT.CAPTURE.REFUNDED、
     * PAYMENT.CAPTURE.DECLINED、PAYMENT.CAPTURE.DENIED、PAYMENT.ORDER.CANCELED →
     * 拿到 Webhook ID,配置到 PAYPAL_WEBHOOK_ID 环境变量。</p>
     *
     * <p>关键实现注意:
     * <ul>
     *   <li>必须用 HttpServletRequest.getInputStream() 读取 RAW payload,
     *       不能用 @RequestBody Map/String,因为 PayPal 的签名验证需要原始 JSON 字符串。</li>
     *   <li>必须读取 PayPal cert_url header (或 transmission_id + transmission_time 等),
     *       用于 PayPal 侧签名验证。</li>
     *   <li>任何业务异常 (重复发积分、商品不存在等) 内部 catch 并返回 200,
     *       避免 PayPal 无意义重试 (幂等唯一索引保证不会重复发积分)。</li>
     * </ul>
     *
     * <p>PayPal 期望 2xx 表示成功接收;非 2xx 会触发重试。</p>
     */
    @PostMapping("/paypal/notify")
    public R<Void> paypalNotify(HttpServletRequest request) {
        if (!payPalPaymentService.isEnabled()) {
            return R.ok();
        }
        // 1. 读取 RAW payload
        String payload;
        try (ServletInputStream is = request.getInputStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                baos.write(buf, 0, n);
            }
            payload = baos.toString(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalArgumentException("cannot read paypal request payload: " + ex.getMessage());
        }
        // 2. 读取 PayPal 传输头用于签名验证
        String transmissionId = request.getHeader("PAYPAL-TRANSMISSION-ID");
        String transmissionTime = request.getHeader("PAYPAL-TRANSMISSION-TIME");
        String certUrl = request.getHeader("PAYPAL-CERT-URL");
        String authAlgo = request.getHeader("PAYPAL-AUTH-ALGO");
        String transmissionSig = request.getHeader("PAYPAL-TRANSMISSION-SIG");
        if (transmissionId == null || transmissionTime == null || certUrl == null
                || authAlgo == null || transmissionSig == null) {
            log.error("PayPal webhook: missing required headers, returning 200 to stop retries");
            return R.ok();
        }
        // 3. 签名校验 + 分发
        payPalPaymentService.handleWebhook(payload, transmissionId, transmissionTime, certUrl, authAlgo, transmissionSig);
        return R.ok();
    }

    /**
     * Cloudflare Stream 视频完成回调 (video.completed)。
     *
     * <p>配置方法: Cloudflare Dashboard → Stream → Settings → Webhooks →
     * 添加 Webhook URL: https://yourdomain.com/api/webhooks/cloudflare/stream →
     * 选择事件: "Video completed"。</p>
     *
     * <p>当视频处理完成时,Cloudflare 会发送包含 HLS URL、duration、ready 状态等信息的回调,
     * 后端据此回写 drama_episode 表的 hls_url 和 video_duration 字段。</p>
     *
     * <p>Cloudflare webhook 使用 Bearer Token 鉴权 (与 API Token 相同),
     * 建议在 Cloudflare Webhook 设置中配置 Bearer Token,由反向代理层注入 Authorization header。</p>
     */
    @PostMapping("/cloudflare/stream")
    public R<Void> cloudflareStreamNotify(@RequestBody Map<String, Object> payload,
                                          @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!cloudflareStreamService.isEnabled()) {
            return R.ok();
        }
        // 鉴权:必须有配置的 token 且与请求 Bearer Token 匹配
        if (cloudflareWebhookToken == null || cloudflareWebhookToken.isBlank()) {
            log.error("Cloudflare webhook token not configured, ignoring notification (returning 200 to stop Cloudflare retries)");
            return R.ok();
        }
        String token = authHeader;
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (!cloudflareWebhookToken.equals(token)) {
            throw new IllegalArgumentException("invalid cloudflare webhook token");
        }
        cloudflareStreamService.handleStreamCompleted(payload);
        return R.ok();
    }
}
