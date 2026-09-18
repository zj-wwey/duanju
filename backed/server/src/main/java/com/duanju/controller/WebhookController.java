package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.service.AppleIapService;
import com.duanju.service.CloudflareStreamService;
import com.duanju.service.GooglePlayIapService;
import com.duanju.service.OrderService;
import com.duanju.service.PayPalPaymentService;
import com.duanju.service.StripePaymentService;
import com.duanju.service.entity.UserOrderService;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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
    private final OrderService orderService;
    private final UserOrderService userOrderService;

    @Value("${duanju.apple-iap.webhook-auth-token:}")
    private String webhookAuthToken;

    @Value("${duanju.cloudflare.webhook-auth-token:}")
    private String cloudflareWebhookToken;

    public WebhookController(AppleIapService appleIapService,
                             StripePaymentService stripePaymentService,
                             GooglePlayIapService googlePlayIapService,
                             CloudflareStreamService cloudflareStreamService,
                             PayPalPaymentService payPalPaymentService,
                             OrderService orderService,
                             UserOrderService userOrderService) {
        this.appleIapService = appleIapService;
        this.stripePaymentService = stripePaymentService;
        this.googlePlayIapService = googlePlayIapService;
        this.cloudflareStreamService = cloudflareStreamService;
        this.payPalPaymentService = payPalPaymentService;
        this.orderService = orderService;
        this.userOrderService = userOrderService;
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
    /**
     * 公开订单状态查询 (按 orderNo,无需鉴权)。
     *
     * <p>给 Stripe/PayPal Checkout 跳转后的支付结果页 HTML 轮询用。
     * orderNo 是随机 UUID,第三方无法枚举,泄露风险极低。</p>
     *
     * @return { orderNo, status, payChannel, productName, points, amountCents, currency }
     */
    @GetMapping("/order-status/{orderNo}")
    public R<Map<String, Object>> orderStatus(@PathVariable String orderNo) {
        Map<String, Object> order = userOrderService.orderByNo(orderNo);
        if (order == null) {
            Map<String, Object> notFound = new HashMap<>();
            notFound.put("orderNo", orderNo);
            notFound.put("status", "NOT_FOUND");
            return R.ok(notFound);
        }
        Map<String, Object> slim = new HashMap<>();
        slim.put("orderNo", order.get("order_no"));
        slim.put("status", order.get("status"));
        slim.put("payChannel", order.get("pay_channel"));
        slim.put("productName", order.get("product_name"));
        slim.put("points", order.get("points"));
        slim.put("amountCents", order.get("amount_cents"));
        slim.put("currency", order.get("currency"));
        slim.put("paidAt", order.get("paid_at"));
        return R.ok(slim);
    }

    /**
     * Stripe Checkout Session success_url / cancel_url 跳转页 (浏览器 GET 请求,不是 Webhook)。
     *
     * <p>返回一个完整的支付结果 HTML 页面,包含:</p>
     * <ul>
     *   <li>成功/失败/取消状态 UI (图标、文案、订单号)</li>
     *   <li>JS 轮询 {@link #orderStatus} 接口确认 webhook 真实支付结果</li>
     *   <li>10 秒倒计时后自动关闭 (uni-app webview 内用 uni.navigateBack,浏览器跳转 admin 充值页)</li>
     * </ul>
     */
    @GetMapping("/stripe/return")
    public org.springframework.http.ResponseEntity<String> stripeReturn(
            @RequestParam(value = "orderNo", required = false) String orderNo,
            @RequestParam(value = "status", required = false) String status) {
        String title = "success".equalsIgnoreCase(status) ? "支付成功" :
                       "cancelled".equalsIgnoreCase(status) ? "支付已取消" : "支付状态未知";
        String initialStatus = "success".equalsIgnoreCase(status) ? "SUCCESS" :
                              "cancelled".equalsIgnoreCase(status) ? "CANCELLED" : "UNKNOWN";

        String html = buildPaymentResultHtml(orderNo, initialStatus, title);
        return org.springframework.http.ResponseEntity.ok(html);
    }

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
        // 入口日志:即使后续校验失败也能确认请求到达 (不打印 token,避免泄露)
        log.info("Cloudflare Stream webhook arrived: hasPayload={}, hasAuthHeader={}, serviceEnabled={}",
                payload != null, authHeader != null, cloudflareStreamService.isEnabled());

        if (!cloudflareStreamService.isEnabled()) {
            log.warn("Cloudflare Stream webhook: service not enabled, returning 200 to stop retries");
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
            log.warn("Cloudflare Stream webhook: token mismatch (got length={}, expected length={})",
                    token == null ? 0 : token.length(), cloudflareWebhookToken.length());
            throw new IllegalArgumentException("invalid cloudflare webhook token");
        }
        cloudflareStreamService.handleStreamCompleted(payload);
        return R.ok();
    }

    // --- Payment Result Page ---

    /**
     * 生成 Stripe Checkout 跳转后的支付结果 HTML 页面。
     *
     * <p>页面特性:</p>
     * <ul>
     *   <li>根据 orderNo 轮询 {@code /api/webhooks/order-status/{orderNo}} 确认 webhook 真实落单结果</li>
     *   <li>初始状态按 success_url / cancel_url 的 status 参数展示,轮询后更新为真实状态</li>
     *   <li>10 秒倒计时后自动关闭:uni-app webview 内用 uni.navigateBack(),浏览器跳 admin 充值页</li>
     *   <li>无法确认 uni-app 环境时,延迟 2 秒再自动跳转/关闭</li>
     * </ul>
     */
    private String buildPaymentResultHtml(String orderNo, String initialStatus, String fallbackTitle) {
        String orderJson = orderNo == null ? "null" : "\"" + orderNo.replace("\"", "\\\"") + "\"";
        String escapedInitial = initialStatus == null ? "UNKNOWN" : initialStatus.toUpperCase();
        String escapedFallbackTitle = fallbackTitle == null ? "支付结果" : fallbackTitle.replace("\"", "&quot;");
        String apiBase = "/api/webhooks";
        String webRedirect = "https://web.marastel.com/pages/recharge/recharge";

        return "<!doctype html>" +
        "<html lang='zh-CN'>" +
        "<head>" +
        "<meta charset='utf-8'>" +
        "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'>" +
        "<meta name='theme-color' content='#080a10'>" +
        "<title>支付结果</title>" +
        "<style>" +
          "*{margin:0;padding:0;box-sizing:border-box}" +
          "html,body{height:100%;background:#080a10;color:#fff;font-family:-apple-system,'PingFang SC','Segoe UI',sans-serif}" +
          ".wrap{min-height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:32px 24px}" +
          ".icon{width:96px;height:96px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:48px;margin-bottom:24px}" +
          ".icon.success{background:rgba(52,199,89,0.15);color:#34c759}" +
          ".icon.failure{background:rgba(255,59,48,0.15);color:#ff3b30}" +
          ".icon.cancel{background:rgba(142,142,147,0.2);color:#8e8e93}" +
          ".icon.loading{background:rgba(247,198,106,0.15);color:#f7c66a}" +
          ".icon.loading::after{content:'';width:32px;height:32px;border:3px solid #f7c66a;border-top-color:transparent;border-radius:50%;animation:spin 0.8s linear infinite}" +
          "@keyframes spin{to{transform:rotate(360deg)}}" +
          ".title{font-size:24px;font-weight:600;margin-bottom:8px;text-align:center}" +
          ".title.success{color:#34c759}" +
          ".title.failure{color:#ff3b30}" +
          ".title.cancel{color:#8e8e93}" +
          ".desc{font-size:14px;color:rgba(255,255,255,0.6);text-align:center;max-width:280px;line-height:1.5}" +
          ".order-no{margin-top:24px;font-size:12px;color:rgba(255,255,255,0.4);word-break:break-all;text-align:center}" +
          ".countdown{margin-top:16px;font-size:12px;color:rgba(255,255,255,0.5)}" +
          ".countdown b{color:#f7c66a;font-weight:600}" +
          ".btn-row{margin-top:32px;display:flex;gap:12px}" +
          ".btn{padding:10px 24px;border-radius:24px;font-size:14px;border:none;cursor:pointer}" +
          ".btn.primary{background:#f7c66a;color:#080a10}" +
          ".btn.ghost{background:transparent;color:rgba(255,255,255,0.7);border:1px solid rgba(255,255,255,0.2)}" +
        "</style>" +
        "</head>" +
        "<body>" +
        "<div class='wrap'>" +
          "<div class='icon loading' id='iconBox'></div>" +
          "<div class='title' id='titleBox'>确认支付结果中…</div>" +
          "<div class='desc' id='descBox'>正在与服务器核对订单状态,请稍候</div>" +
          "<div class='order-no' id='orderBox'></div>" +
          "<div class='countdown' id='countdownBox'></div>" +
          "<div class='btn-row'>" +
            "<button class='btn ghost' onclick='closeThis()' id='closeBtn' style='display:none'>关闭</button>" +
          "</div>" +
        "</div>" +
        "<script>" +
          "(function(){" +
            "var orderNo=" + orderJson + ";" +
            "var initial='" + escapedInitial + "';" +
            "var webRedirect='" + webRedirect + "';" +
            "var apiBase='" + apiBase + "';" +
            "var isUniApp=false;" +
            "var countdown=10;" +
            "var finalStatus=null;" +
            "var pollCount=0;" +
            "var maxPolls=15;" +
            "" +
            "function detectUniApp(){" +
              "try{" +
                "if(window.uni||window.UniAppJSBridge){isUniApp=true;return true}" +
              "}catch(e){}" +
              "return false;" +
            "}" +
            "" +
            "function applyStatus(status, orderData){" +
              "var iconBox=document.getElementById('iconBox');" +
              "var titleBox=document.getElementById('titleBox');" +
              "var descBox=document.getElementById('descBox');" +
              "var orderBox=document.getElementById('orderBox');" +
              "var closeBtn=document.getElementById('closeBtn');" +
              "var success=status==='PAID'||status==='SUCCEEDED'||status==='PLEASE_PAY'&&initial==='SUCCESS';" +
              "var cancelled=status==='CANCELLED'||status==='CLOSED';" +
              "iconBox.className='icon '+(success?'success':cancelled?'cancel':'failure');" +
              "iconBox.textContent=success?'✓':cancelled?'✕':'✕';" +
              "titleBox.className='title '+(success?'success':cancelled?'cancel':'failure');" +
              "titleBox.textContent=success?'支付成功':cancelled?'支付已取消':'支付失败';" +
              "descBox.textContent=success?'积分已到账,感谢您的充值。':cancelled?'您已取消此次支付,没有产生扣款。':'支付未完成,您可以稍后再试或联系客服。';" +
              "if(orderNo){orderBox.textContent='订单号: '+orderNo}" +
              "closeBtn.style.display='inline-block';" +
              "finalStatus=status;" +
            "}" +
            "" +
            "function pollOnce(){" +
              "if(!orderNo){return}" +
              "pollCount++;" +
              "fetch(apiBase+'/order-status/'+encodeURIComponent(orderNo),{credentials:'omit'})" +
                ".then(function(r){return r.json()})" +
                ".then(function(res){" +
                  "var status=res&&res.data&&res.data.status;" +
                  "if(status){applyStatus(status,res.data)}" +
                "})" +
                ".catch(function(){})" +
                ".then(function(){" +
                  "if(pollCount<maxPolls&&!finalStatus){setTimeout(pollOnce,800)}" +
                "});" +
            "}" +
            "" +
            "function tickCountdown(){" +
              "var box=document.getElementById('countdownBox');" +
              "if(countdown>0){" +
                "box.innerHTML='<b>'+countdown+'</b> 秒后自动关闭';" +
                "countdown--;" +
                "setTimeout(tickCountdown,1000);" +
              "}else{autoclose();}" +
            "}" +
            "" +
            "function autoclose(){" +
              "if(detectUniApp()&&window.uni&&typeof window.uni.navigateBack==='function'){" +
                "try{window.uni.navigateBack({delta:1})}catch(e){window.close()}" +
              "}else{" +
                "window.location.href=webRedirect;" +
              "}" +
            "}" +
            "" +
            "window.closeThis=function(){autoclose()};" +
            "" +
            "document.addEventListener('UniAppJSBridgeReady',function(){isUniApp=true});" +
            "" +
            "if(initial==='CANCELLED'){" +
              "applyStatus('CANCELLED',null);" +
            "}" +
            "setTimeout(function(){detectUniApp()},2000);" +
            "pollOnce();" +
            "tickCountdown();" +
          "})();" +
        "</script>" +
        "</body>" +
        "</html>";
    }
}
