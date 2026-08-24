package com.duanju.mapper.entity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duanju.entity.UserOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserOrderMapper extends BaseMapper<UserOrder> {

    @Select("""
            select o.id, o.order_no, o.user_id, u.phone, u.nickname, o.product_id, p.name product_name,
                   o.order_type, o.pay_channel, o.points, o.amount_cents, o.currency, o.status,
                   o.paid_at, o.created_at, o.updated_at,
                   o.store_transaction_id, o.store_product_id, o.refund_reason,
                   o.stripe_payment_intent_id, o.paypal_order_id, o.paypal_payment_id,
                   o.google_purchase_token
            from user_order o
            left join app_user u on u.id = o.user_id
            left join point_product p on p.id = o.product_id
            where o.user_id = #{userId}
            order by o.id desc
            limit #{limit}
            """)
    List<Map<String, Object>> userOrders(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("""
            select o.id, o.order_no, o.user_id, u.phone, u.nickname, o.product_id, p.name product_name,
                   o.order_type, o.pay_channel, o.points, o.amount_cents, o.currency, o.status,
                   o.paid_at, o.created_at, o.updated_at,
                   o.store_transaction_id, o.store_product_id, o.refund_reason,
                   o.stripe_payment_intent_id, o.paypal_order_id, o.paypal_payment_id,
                   o.google_purchase_token
            from user_order o
            left join app_user u on u.id = o.user_id
            left join point_product p on p.id = o.product_id
            where o.order_no = #{orderNo}
            """)
    Map<String, Object> orderByNo(@Param("orderNo") String orderNo);

    @Select("""
            select o.id, o.order_no, o.user_id, u.phone, u.nickname, o.product_id, p.name product_name,
                   o.order_type, o.pay_channel, o.points, o.amount_cents, o.currency, o.status,
                   o.paid_at, o.created_at, o.updated_at,
                   o.store_transaction_id, o.store_product_id, o.refund_reason,
                   o.stripe_payment_intent_id, o.paypal_order_id, o.paypal_payment_id,
                   o.google_purchase_token
            from user_order o
            left join app_user u on u.id = o.user_id
            left join point_product p on p.id = o.product_id
            where (#{status} is null or #{status} = '' or o.status = #{status})
              and (#{keyword} is null or #{keyword} = ''
                or o.order_no like concat('%', #{keyword}, '%')
                or u.phone like concat('%', #{keyword}, '%')
                or u.nickname like concat('%', #{keyword}, '%'))
            order by o.id desc
            limit #{limit}
            """)
    List<Map<String, Object>> adminOrders(@Param("keyword") String keyword, @Param("status") String status,
                                          @Param("limit") int limit);
}
