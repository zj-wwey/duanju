package com.duanju.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付 Webhook 事件日志服务。
 *
 * <p>核心职责:</p>
 * <ul>
 *   <li>事件去重:通过 (channel, event_id) 唯一索引,确保同一事件不会被重复处理</li>
 *   <li>审计追踪:记录每个事件的类型、关联订单、处理结果</li>
 * </ul>
 *
 * <p>使用 REQUIRES_NEW 传播级别,确保事件日志在独立事务中提交,
 * 即使业务处理失败回滚,事件日志仍然保留,避免重试时丢失去重记录。</p>
 */
@Service
public class PaymentEventLogService {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventLogService.class);

    private final JdbcTemplate jdbcTemplate;

    public PaymentEventLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 尝试记录事件,用于去重判断。
     *
     * <p>利用 uk_channel_event 唯一索引:插入成功表示首次收到该事件,
     * 插入失败(唯一索引冲突)表示事件已被处理过,直接返回 false。</p>
     *
     * @param channel   支付渠道 (STRIPE/PAYPAL/APPLE_IAP/GOOGLE_PLAY)
     * @param eventId   渠道侧事件 ID
     * @param eventType 事件类型
     * @return true=首次收到可以处理,false=已处理过应跳过
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryLogEvent(String channel, String eventId, String eventType) {
        if (channel == null || eventId == null || eventId.isBlank()) {
            // 没有事件 ID 时无法去重,放行让业务层处理
            return true;
        }
        try {
            jdbcTemplate.update(
                    "insert into payment_event_log(channel, event_id, event_type, status) values(?, ?, ?, 'PROCESSING')",
                    channel, eventId, eventType);
            return true;
        } catch (Exception ex) {
            log.info("PaymentEventLog: event already processed, channel={}, eventId={}, type={}",
                    channel, eventId, eventType);
            return false;
        }
    }

    /**
     * 更新事件处理结果。
     *
     * @param channel 支付渠道
     * @param eventId 渠道侧事件 ID
     * @param status  处理结果 (SUCCESS/FAILED)
     * @param orderNo 关联订单号 (可为空)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateResult(String channel, String eventId, String status, String orderNo) {
        if (channel == null || eventId == null || eventId.isBlank()) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "update payment_event_log set status=?, order_no=? where channel=? and event_id=?",
                    status, orderNo, channel, eventId);
        } catch (Exception ex) {
            log.warn("PaymentEventLog: failed to update result, channel={}, eventId={}", channel, eventId, ex);
        }
    }
}
