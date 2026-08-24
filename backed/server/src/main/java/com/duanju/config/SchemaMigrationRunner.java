package com.duanju.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SchemaMigrationRunner implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public SchemaMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrateCategorySystem();
        ensureDramaColumns();
        ensureEpisodeColumns();
        ensureCategoryFilters();
        ensureOrderColumns();
        ensureAdRewardColumns();
        ensurePointProductColumns();
        ensureAnnouncementTables();
        ensureVipTables();
        ensureFeedbackTables();
        ensureMembershipTables();
        ensurePointExpireTable();
        ensurePointProductMembershipColumns();
        ensurePointShopTables();
        ensureAutoRenewalTable();
        ensureInviteTable();
        ensureCheckinColumns();
        ensureContentTranslationTable();
        ensurePaymentEventLogTable();
        seedDefaultProducts();
    }

    private void migrateCategorySystem() {
        if (!tableExists("drama")) {
            return;
        }
        if (foreignKeyExists("drama", "fk_drama_category")) {
            jdbcTemplate.execute("alter table drama drop foreign key fk_drama_category");
        }
        if (indexExists("drama", "idx_drama_category")) {
            jdbcTemplate.execute("alter table drama drop index idx_drama_category");
        }
        if (columnExists("drama", "category_id")) {
            jdbcTemplate.execute("alter table drama drop column category_id");
        }
        if (tableExists("drama_category")) {
            jdbcTemplate.execute("drop table drama_category");
        }
    }

    private void ensureDramaColumns() {
        if (!tableExists("drama")) {
            return;
        }
        addColumnIfMissing("drama", "horizontal_cover_url", "varchar(1000) comment '横版封面图片URL'");
        addColumnIfMissing("drama", "vertical_cover_url", "varchar(1000) comment '竖版封面图片URL'");
        addColumnIfMissing("drama", "tags", "varchar(255) comment '短剧标签'");
        addColumnIfMissing("drama", "episode_price_points", "int not null default 10 comment '默认单集解锁积分'");
        addColumnIfMissing("drama", "whole_price_points", "int not null default 0 comment '整剧购买积分'");
        addColumnIfMissing("drama", "content_type", "varchar(16) not null default 'real' comment '内容类型'");
        addColumnIfMissing("drama", "background", "varchar(32) not null default 'modern' comment '背景'");
        addColumnIfMissing("drama", "theme", "varchar(32) not null default 'romance' comment '题材'");
        addColumnIfMissing("drama", "setting_key", "varchar(32) not null default 'ordinary' comment '设定'");
        addColumnIfMissing("drama", "audience", "varchar(16) not null default 'female' comment '受众'");
        addColumnIfMissing("drama", "publish_date", "date comment '上新日期'");
        addColumnIfMissing("drama", "online_time", "datetime comment '上线时间'");
        addColumnIfMissing("drama", "hot_score", "int not null default 0 comment '热度值'");
        addColumnIfMissing("drama", "recommended", "tinyint not null default 0 comment '是否推荐'");
        addColumnIfMissing("drama", "sort_order", "int not null default 0 comment '排序值'");
    }

    private void ensureEpisodeColumns() {
        if (!tableExists("drama_episode")) {
            return;
        }
        addColumnIfMissing("drama_episode", "access_type", "varchar(16) not null default 'POINTS' comment '观看权限'");
        addColumnIfMissing("drama_episode", "storage_provider", "varchar(16) not null default 'oss' comment '存储来源'");
        addColumnIfMissing("drama_episode", "cloudflare_uid", "varchar(128) comment 'Cloudflare Stream 视频 UID'");
        addColumnIfMissing("drama_episode", "hls_url", "varchar(1000) comment 'HLS 播放清单 URL (m3u8)'");
        addColumnIfMissing("drama_episode", "video_duration", "int comment '视频时长(秒),由 Cloudflare 异步回写'");
        if (!indexExists("drama_episode", "uk_episode_cloudflare_uid")) {
            jdbcTemplate.execute("create unique index uk_episode_cloudflare_uid on drama_episode(cloudflare_uid)");
        }
        jdbcTemplate.update("""
                update drama_episode
                set access_type = 'POINTS', is_free = 0,
                    price_points = case when price_points <= 0 then 10 else price_points end
                where access_type = 'VIP'
                """);
    }

    private void ensureOrderColumns() {
        if (!tableExists("user_order")) {
            return;
        }
        addColumnIfMissing("user_order", "store_transaction_id", "varchar(128) comment '应用商店交易ID (Apple originalTransactionId / Google purchaseToken)'");
        addColumnIfMissing("user_order", "store_original_transaction_id", "varchar(128) comment 'Apple 订阅原始交易ID,订阅生命周期内不变'");
        addColumnIfMissing("user_order", "store_product_id", "varchar(128) comment '应用商店侧商品ID (Apple productId / Google sku)'");
        addColumnIfMissing("user_order", "store_bundle_id", "varchar(128) comment 'Apple bundleId / Google packageName'");
        addColumnIfMissing("user_order", "store_environment", "varchar(16) comment 'SANDBOX/PRODUCTION'");
        addColumnIfMissing("user_order", "store_receipt_hash", "varchar(128) comment '已验证的 receipt/transaction payload 的 SHA-256 摘要'");
        addColumnIfMissing("user_order", "refund_reason", "varchar(64) comment '退款原因 (Apple refundReason enum)'");
        addColumnIfMissing("user_order", "tax_amount_cents", "int not null default 0 comment '税额,单位分'");
        // Stripe Web 支付列
        addColumnIfMissing("user_order", "stripe_session_id", "varchar(128) comment 'Stripe Checkout Session ID (cs_test_xxx),创建 Session 时写入'");
        addColumnIfMissing("user_order", "stripe_payment_intent_id", "varchar(128) comment 'Stripe PaymentIntent ID (pi_xxx),支付成功 webhook 写入,幂等'");
        // Google Play Billing 列
        addColumnIfMissing("user_order", "google_purchase_token", "varchar(512) comment 'Google Play Billing purchaseToken (verifyPurchase/RTDN/退款查询)'");
        // PayPal Web 支付列
        addColumnIfMissing("user_order", "paypal_order_id", "varchar(128) comment 'PayPal Order ID (创建订单时写入)'");
        addColumnIfMissing("user_order", "paypal_payment_id", "varchar(128) comment 'PayPal Capture ID / Payment ID (捕获支付时写入,幂等)'");
        // 应用商店交易ID 唯一索引 (用于 webhook 幂等去重)
        if (!indexExists("user_order", "uk_order_store_tx")) {
            jdbcTemplate.execute("create unique index uk_order_store_tx on user_order(store_transaction_id)");
        }
        // Stripe PaymentIntent ID 唯一索引 (每个 pi_ 只能落一次订单)
        if (!indexExists("user_order", "uk_order_stripe_pi")) {
            jdbcTemplate.execute("create unique index uk_order_stripe_pi on user_order(stripe_payment_intent_id)");
        }
        // Stripe Checkout Session ID 索引 (按 cs_ 反查订单,webhook checkout.session.completed 场景)
        if (!indexExists("user_order", "idx_order_stripe_session")) {
            jdbcTemplate.execute("create index idx_order_stripe_session on user_order(stripe_session_id)");
        }
        // Google Play purchaseToken 唯一索引 (RTDN / 退款 / 重复 verifyPurchase 幂等)
        if (!indexExists("user_order", "uk_order_google_purchase_token")) {
            jdbcTemplate.execute("create unique index uk_order_google_purchase_token on user_order(google_purchase_token)");
        }
        // PayPal Payment ID 唯一索引 (每个 capture_id 只能落一次订单,webhook 幂等去重)
        if (!indexExists("user_order", "uk_order_paypal_payment_id")) {
            jdbcTemplate.execute("create unique index uk_order_paypal_payment_id on user_order(paypal_payment_id)");
        }
    }

    private void ensureAdRewardColumns() {
        if (!tableExists("ad_reward_record")) {
            return;
        }
        // 旧索引迁移:从 trace_id 全局唯一改为 (user_id, trace_id) 用户级唯一
        // 全局唯一会导致不同用户使用相同 traceId 时互相阻断 (DoS)
        if (indexExists("ad_reward_record", "uk_ad_trace")) {
            jdbcTemplate.execute("alter table ad_reward_record drop index uk_ad_trace");
        }
        if (indexExists("ad_reward_record", "uk_ad_reward_trace")) {
            jdbcTemplate.execute("alter table ad_reward_record drop index uk_ad_reward_trace");
        }
        // (user_id, trace_id) 复合唯一索引:同一用户同一 traceId 只能发放一次积分
        if (!indexExists("ad_reward_record", "uk_ad_user_trace")) {
            jdbcTemplate.execute("create unique index uk_ad_user_trace on ad_reward_record(user_id, trace_id)");
        }
    }

    private void ensurePointProductColumns() {
        if (!tableExists("point_product")) {
            return;
        }
        addColumnIfMissing("point_product", "store_product_id",
                "varchar(128) comment '应用商店商品ID (Apple productId / Google sku)，用于 IAP/webhook 反查内部商品'");
        // 唯一索引:防止同一商店商品ID被绑定到多个内部商品,避免 webhook 反查歧义
        if (!indexExists("point_product", "uk_point_product_store_id")) {
            jdbcTemplate.execute("create unique index uk_point_product_store_id on point_product(store_product_id)");
        }
        // 回填历史数据:对 store_product_id 为空的行,按 id 生成约定值 com.duanju.points{id}
        // 让旧版部署升级后 webhook 仍能反查到商品,管理员后续可手动改为 App Store Connect 中的实际 productId
        jdbcTemplate.update("""
                update point_product
                set store_product_id = concat('com.duanju.points', id)
                where store_product_id is null
                """);
    }

    private void ensureCategoryFilters() {
        jdbcTemplate.execute("""
                create table if not exists drama_category_filter (
                  id bigint primary key auto_increment comment '分类筛选项ID',
                  group_key varchar(32) not null comment '筛选组标识',
                  group_label_key varchar(64) not null comment '筛选组展示文案',
                  group_sort_order int not null default 0 comment '筛选组排序',
                  option_key varchar(32) not null comment '筛选项标识',
                  option_label_key varchar(64) not null comment '筛选项展示文案',
                  option_sort_order int not null default 0 comment '筛选项排序',
                  status tinyint not null default 1 comment '状态：1启用，0禁用，-1删除',
                  created_at datetime not null default current_timestamp comment '创建时间',
                  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
                  unique key uk_category_filter_option (group_key, option_key),
                  key idx_category_filter_sort (status, group_sort_order, option_sort_order)
                ) engine=InnoDB default charset=utf8mb4 comment='系统分类筛选表'
                """);

        List<CategorySeed> seeds = categorySeeds();
        for (CategorySeed seed : seeds) {
            jdbcTemplate.update("""
                    insert into drama_category_filter(group_key, group_label_key, group_sort_order,
                      option_key, option_label_key, option_sort_order, status)
                    values(?, ?, ?, ?, ?, ?, 1)
                    on duplicate key update id = id
                    """, seed.groupKey(), seed.groupLabel(), seed.groupSort(), seed.optionKey(),
                    seed.optionLabel(), seed.optionSort());
        }
    }

    private List<CategorySeed> categorySeeds() {
        return List.of(
                seed("contentType", "category.contentType", 0, "ai", "category.aiDrama", 1),
                seed("contentType", "category.contentType", 0, "real", "category.realDrama", 2),
                seed("background", "category.background", 1, "all", "category.options.all", 0),
                seed("background", "category.background", 1, "modern", "category.options.modern", 1),
                seed("background", "category.background", 1, "urban", "category.options.urban", 2),
                seed("background", "category.background", 1, "ancient", "category.options.ancient", 3),
                seed("background", "category.background", 1, "rural", "category.options.rural", 4),
                seed("background", "category.background", 1, "period", "category.options.period", 5),
                seed("background", "category.background", 1, "fantasySpace", "category.options.fantasySpace", 6),
                seed("background", "category.background", 1, "workplace", "category.options.workplace", 7),
                seed("background", "category.background", 1, "republican", "category.options.republican", 8),
                seed("background", "category.background", 1, "campus", "category.options.campus", 9),
                seed("background", "category.background", 1, "palace", "category.options.palace", 10),
                seed("background", "category.background", 1, "island", "category.options.island", 11),
                seed("theme", "category.theme", 2, "all", "category.options.all", 0),
                seed("theme", "category.theme", 2, "romance", "category.options.romance", 1),
                seed("theme", "category.theme", 2, "femaleGrowth", "category.options.femaleGrowth", 2),
                seed("theme", "category.theme", 2, "brainHole", "category.options.brainHole", 3),
                seed("theme", "category.theme", 2, "fantasy", "category.options.fantasy", 4),
                seed("theme", "category.theme", 2, "xuanhuan", "category.options.xuanhuan", 5),
                seed("theme", "category.theme", 2, "ancientRomance", "category.options.ancientRomance", 6),
                seed("theme", "category.theme", 2, "warGod", "category.options.warGod", 7),
                seed("theme", "category.theme", 2, "palaceFight", "category.options.palaceFight", 8),
                seed("theme", "category.theme", 2, "xianxia", "category.options.xianxia", 9),
                seed("theme", "category.theme", 2, "power", "category.options.power", 10),
                seed("theme", "category.theme", 2, "farming", "category.options.farming", 11),
                seed("theme", "category.theme", 2, "ageLove", "category.options.ageLove", 12),
                seed("theme", "category.theme", 2, "suspense", "category.options.suspense", 13),
                seed("theme", "category.theme", 2, "comedy", "category.options.comedy", 14),
                seed("theme", "category.theme", 2, "youth", "category.options.youth", 15),
                seed("theme", "category.theme", 2, "republicanLove", "category.options.republicanLove", 16),
                seed("setting", "category.setting", 3, "all", "category.options.all", 0),
                seed("setting", "category.setting", 3, "revenge", "category.options.revenge", 1),
                seed("setting", "category.setting", 3, "maleLead", "category.options.maleLead", 2),
                seed("setting", "category.setting", 3, "femaleLead", "category.options.femaleLead", 3),
                seed("setting", "category.setting", 3, "hiddenIdentity", "category.options.hiddenIdentity", 4),
                seed("setting", "category.setting", 3, "rebirth", "category.options.rebirth", 5),
                seed("setting", "category.setting", 3, "timeTravel", "category.options.timeTravel", 6),
                seed("setting", "category.setting", 3, "system", "category.options.system", 7),
                seed("setting", "category.setting", 3, "marriageFirst", "category.options.marriageFirst", 8),
                seed("setting", "category.setting", 3, "family", "category.options.family", 9),
                seed("setting", "category.setting", 3, "ordinary", "category.options.ordinary", 10),
                seed("setting", "category.setting", 3, "reunion", "category.options.reunion", 11),
                seed("setting", "category.setting", 3, "tycoon", "category.options.tycoon", 12),
                seed("setting", "category.setting", 3, "wealthy", "category.options.wealthy", 13),
                seed("setting", "category.setting", 3, "comeback", "category.options.comeback", 14),
                seed("audience", "category.audience", 4, "all", "category.options.all", 0),
                seed("audience", "category.audience", 4, "male", "category.options.male", 1),
                seed("audience", "category.audience", 4, "female", "category.options.female", 2),
                seed("time", "category.time", 5, "all", "category.options.all", 0),
                seed("time", "category.time", 5, "d7", "category.options.d7", 1),
                seed("time", "category.time", 5, "d14", "category.options.d14", 2),
                seed("time", "category.time", 5, "d30", "category.options.d30", 3),
                seed("time", "category.time", 5, "d90", "category.options.d90", 4),
                seed("sort", "category.sort", 6, "all", "category.options.all", 0),
                seed("sort", "category.sort", 6, "newest", "category.options.newest", 1),
                seed("sort", "category.sort", 6, "hottest", "category.options.hottest", 2)
        );
    }

    private CategorySeed seed(String groupKey, String groupLabel, int groupSort, String optionKey,
                              String optionLabel, int optionSort) {
        return new CategorySeed(groupKey, groupLabel, groupSort, optionKey, optionLabel, optionSort);
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        if (!columnExists(table, column)) {
            jdbcTemplate.execute("alter table " + table + " add column " + column + " " + definition);
        }
    }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = database() and table_name = ?
                """, Integer.class, table);
        return count != null && count > 0;
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = database() and table_name = ? and column_name = ?
                """, Integer.class, table, column);
        return count != null && count > 0;
    }

    private boolean indexExists(String table, String index) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.statistics
                where table_schema = database() and table_name = ? and index_name = ?
                """, Integer.class, table, index);
        return count != null && count > 0;
    }

    private boolean foreignKeyExists(String table, String constraint) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.table_constraints
                where constraint_schema = database() and table_name = ? and constraint_name = ?
                """, Integer.class, table, constraint);
        return count != null && count > 0;
    }

    private record CategorySeed(String groupKey, String groupLabel, int groupSort, String optionKey,
                                String optionLabel, int optionSort) {
    }

    private void ensureAnnouncementTables() {
        jdbcTemplate.execute("""
                create table if not exists system_announcement (
                  id bigint primary key auto_increment comment 'ID',
                  title varchar(200) not null comment '公告标题',
                  content text not null comment '公告内容',
                  type varchar(16) not null default 'SYSTEM' comment '类型',
                  is_top tinyint not null default 0 comment '是否置顶',
                  status tinyint not null default 0 comment '状态：0草稿，1已发布，-1已删除',
                  publisher_id bigint comment '发布人ID',
                  publisher_name varchar(64) comment '发布人名称',
                  published_at datetime comment '发布时间',
                  start_at datetime comment '生效开始时间',
                  end_at datetime comment '生效结束时间',
                  view_count int not null default 0 comment '查看次数',
                  created_at datetime not null default current_timestamp comment '创建时间',
                  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
                  key idx_announcement_status (status, is_top, published_at),
                  key idx_announcement_time (start_at, end_at)
                ) engine=InnoDB default charset=utf8mb4 comment='系统公告表'
                """);

        jdbcTemplate.execute("""
                create table if not exists announcement_read_record (
                  id bigint primary key auto_increment comment 'ID',
                  user_id bigint not null comment '用户ID',
                  announcement_id bigint not null comment '公告ID',
                  read_at datetime not null default current_timestamp comment '阅读时间',
                  unique key uk_user_announcement (user_id, announcement_id),
                  key idx_read_record_user (user_id, read_at)
                ) engine=InnoDB default charset=utf8mb4 comment='公告阅读记录表'
                """);
    }

    private void ensureVipTables() {
        addColumnIfMissing("point_product", "package_type",
                "varchar(16) comment '套餐类型：RECHARGE/VIP_DAILY/VIP_WEEKLY/VIP_MONTHLY'");
        addColumnIfMissing("point_product", "duration_days",
                "int comment 'VIP套餐有效天数'");
        addColumnIfMissing("point_product", "original_price_cents",
                "int comment '原价（分）'");
        addColumnIfMissing("point_product", "tag_text",
                "varchar(64) comment '标签文案'");
        addColumnIfMissing("point_product", "cover_url",
                "varchar(1000) comment '套餐封面图URL'");

        jdbcTemplate.execute("""
                create table if not exists user_vip_record (
                  id bigint primary key auto_increment comment 'ID',
                  user_id bigint not null comment '用户ID',
                  product_id bigint not null comment '购买的套餐ID',
                  order_no varchar(64) not null comment '订单号',
                  vip_type varchar(16) not null comment 'VIP类型',
                  start_at datetime not null comment '生效时间',
                  expire_at datetime not null comment '过期时间',
                  status tinyint not null default 1 comment '状态：1生效中，0已过期',
                  created_at datetime not null default current_timestamp comment '创建时间',
                  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
                  key idx_user_vip_user (user_id, status),
                  key idx_user_vip_expire (expire_at, status)
                ) engine=InnoDB default charset=utf8mb4 comment='用户VIP会员记录表'
                """);
    }

    private void ensureFeedbackTables() {
        jdbcTemplate.execute("""
                create table if not exists user_feedback (
                  id bigint primary key auto_increment comment 'ID',
                  user_id bigint not null comment '用户ID',
                  user_nickname varchar(64) comment '用户昵称',
                  user_phone varchar(32) comment '用户手机号',
                  type varchar(32) not null default 'BUG' comment '反馈类型',
                  title varchar(200) not null comment '反馈标题',
                  content text not null comment '反馈内容',
                  screenshots varchar(2000) comment '截图URL列表',
                  contact varchar(128) comment '联系方式',
                  device_info varchar(256) comment '设备信息',
                  app_version varchar(64) comment 'APP版本号',
                  status varchar(16) not null default 'PENDING' comment '状态',
                  handler_id bigint comment '处理人ID',
                  handler_name varchar(64) comment '处理人名称',
                  reply text comment '管理员回复',
                  replied_at datetime comment '回复时间',
                  created_at datetime not null default current_timestamp comment '创建时间',
                  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
                  key idx_feedback_user (user_id, created_at),
                  key idx_feedback_status (status, type, created_at),
                  key idx_feedback_type (type, created_at)
                ) engine=InnoDB default charset=utf8mb4 comment='用户反馈表'
                """);
    }

    private void ensureMembershipTables() {
        addColumnIfMissing("app_user", "total_spent_cents",
                "int not null default 0 comment '累计消费金额(分)'");

        jdbcTemplate.execute("""
                create table if not exists user_membership (
                  id bigint primary key auto_increment comment 'ID',
                  user_id bigint not null comment '用户ID',
                  level varchar(20) not null default 'NONE' comment '当前等级: NONE/SILVER/GOLD/DIAMOND',
                  level_source varchar(20) not null default 'NONE' comment '等级来源: NONE/SPEND/PURCHASE',
                  spent_upgrade_at datetime comment '消费升级达标时间',
                  purchase_level varchar(20) comment '购买等级(降级回退依据)',
                  purchase_expire_at datetime comment '购买等级过期时间',
                  daily_free_used int not null default 0 comment '今日已用免费集数',
                  daily_free_reset_at date comment '免费集数重置日期',
                  status tinyint not null default 1 comment '1=有效 0=已降级',
                  created_at datetime not null default current_timestamp comment '创建时间',
                  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
                  unique key uk_membership_user (user_id),
                  key idx_membership_level (level, status)
                ) engine=InnoDB default charset=utf8mb4 comment='用户会员等级表'
                """);

        // 为现有用户初始化 user_membership 记录
        jdbcTemplate.update("""
                insert ignore into user_membership(user_id, level, level_source, daily_free_used, status)
                select id, 'NONE', 'NONE', 0, 1 from app_user
                """);
    }

    private void ensurePointExpireTable() {
        jdbcTemplate.execute("""
                create table if not exists point_expire (
                  id bigint primary key auto_increment comment 'ID',
                  user_id bigint not null comment '用户ID',
                  point_type varchar(20) not null comment 'EARNED(赚取)/PURCHASED(购买)/PROMOTION(活动)',
                  total_points int not null comment '该批次积分总数',
                  remaining int not null comment '剩余可用积分',
                  expire_at date not null comment '过期日期(PURCHASED类型设为9999-12-31)',
                  created_at datetime not null default current_timestamp comment '创建时间',
                  key idx_expire_user (user_id, expire_at),
                  key idx_expire_cleanup (expire_at, point_type)
                ) engine=InnoDB default charset=utf8mb4 comment='积分有效期批次表'
                """);

        // 将现有积分余额迁移为 PURCHASED 批次（永久有效，避免被过期清理）
        if (tableExists("app_user") && columnExists("app_user", "points")) {
            Integer batchCount = jdbcTemplate.queryForObject(
                    "select count(*) from point_expire", Integer.class);
            if (batchCount != null && batchCount == 0) {
                jdbcTemplate.update("""
                        insert into point_expire(user_id, point_type, total_points, remaining, expire_at)
                        select id, 'PURCHASED', points, points, '9999-12-31' from app_user where points > 0
                        """);
            }
        }
    }

    private void ensurePointProductMembershipColumns() {
        if (!tableExists("point_product")) {
            return;
        }
        addColumnIfMissing("point_product", "product_category",
                "varchar(20) not null default 'RECHARGE' comment '商品分类: RECHARGE(积分充值)/VIP(会员套餐)'");
        addColumnIfMissing("point_product", "membership_level",
                "varchar(20) comment '关联会员等级: SILVER/GOLD/DIAMOND(仅VIP类商品)'");
        addColumnIfMissing("point_product", "daily_limit",
                "int comment '每日限购次数'");
        addColumnIfMissing("point_product", "monthly_limit",
                "int comment '每月限购次数'");
        addColumnIfMissing("point_product", "first_purchase_bonus",
                "int not null default 0 comment '首充额外赠送积分'");

        // 回填 product_category
        jdbcTemplate.update("""
                update point_product set product_category = 'VIP' where package_type is not null and package_type like 'VIP%'
                """);
        jdbcTemplate.update("""
                update point_product set product_category = 'RECHARGE' where package_type = 'RECHARGE' or package_type is null
                """);
    }

    private void ensurePointShopTables() {
        jdbcTemplate.execute("""
                create table if not exists point_shop_item (
                  id bigint primary key auto_increment comment 'ID',
                  name varchar(128) not null comment '商品名称',
                  description varchar(500) comment '商品描述',
                  item_type varchar(32) not null comment 'PHYSICAL(实物)/VIRTUAL(虚拟)/AVATAR_FRAME(头像框)/DANMU_EFFECT(弹幕特效)',
                  points_cost int not null comment '基础积分价格',
                  vip_points_cost int comment '钻石会员优惠价(null=无优惠)',
                  image_url varchar(1000) comment '商品图片URL',
                  stock int not null default -1 comment '库存(-1=无限)',
                  status tinyint not null default 1 comment '1上架 0下架',
                  sort_order int not null default 0 comment '排序值',
                  created_at datetime not null default current_timestamp comment '创建时间',
                  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间'
                ) engine=InnoDB default charset=utf8mb4 comment='积分商城商品表'
                """);

        jdbcTemplate.execute("""
                create table if not exists point_shop_order (
                  id bigint primary key auto_increment comment 'ID',
                  user_id bigint not null comment '用户ID',
                  item_id bigint not null comment '商品ID',
                  item_name varchar(128) not null comment '商品名称(快照)',
                  points_cost int not null comment '消耗积分',
                  delivery_status varchar(20) not null default 'PENDING' comment 'PENDING/DELIVERED/SHIPPED/FAILED',
                  created_at datetime not null default current_timestamp comment '创建时间',
                  key idx_shop_order_user (user_id, created_at)
                ) engine=InnoDB default charset=utf8mb4 comment='积分商城兑换记录表'
                """);
    }

    private void ensureAutoRenewalTable() {
        jdbcTemplate.execute("""
                create table if not exists auto_renewal_subscription (
                  id bigint primary key auto_increment comment 'ID',
                  user_id bigint not null comment '用户ID',
                  product_id bigint not null comment '关联套餐ID',
                  pay_channel varchar(32) not null comment '支付渠道: STRIPE/PAYPAL',
                  pay_method_token varchar(512) not null comment '支付方式token',
                  next_charge_at datetime not null comment '下次扣款时间',
                  status varchar(20) not null default 'ACTIVE' comment 'ACTIVE/CANCELLED/EXPIRED/FAILED',
                  last_charge_order_no varchar(64) comment '最近一次扣款订单号',
                  fail_count int not null default 0 comment '连续失败次数',
                  created_at datetime not null default current_timestamp comment '创建时间',
                  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
                  unique key uk_auto_renewal_user (user_id, status),
                  key idx_auto_renewal_next (next_charge_at, status)
                ) engine=InnoDB default charset=utf8mb4 comment='自动续费订阅表'
                """);
    }

    private void ensureInviteTable() {
        jdbcTemplate.execute("""
                create table if not exists user_invite (
                  id bigint primary key auto_increment comment 'ID',
                  inviter_id bigint not null comment '邀请人ID',
                  invitee_id bigint not null comment '被邀请人ID',
                  status varchar(20) not null default 'PENDING' comment 'PENDING(待完成)/REWARDED(已奖励)',
                  rewarded_at datetime comment '发放奖励时间',
                  created_at datetime not null default current_timestamp comment '创建时间',
                  unique key uk_invite_invitee (invitee_id),
                  key idx_invite_inviter (inviter_id, status)
                ) engine=InnoDB default charset=utf8mb4 comment='用户邀请关系表'
                """);
    }

    private void ensureCheckinColumns() {
        if (!tableExists("user_checkin")) {
            return;
        }
        addColumnIfMissing("user_checkin", "consecutive_days",
                "int not null default 1 comment '连续签到天数'");
    }

    private void ensureContentTranslationTable() {
        jdbcTemplate.execute("""
                create table if not exists content_translation (
                  id bigint primary key auto_increment comment 'ID',
                  entity_type varchar(50) not null comment '实体类型: drama, category_option',
                  entity_id varchar(100) not null comment '实体ID或key',
                  field_name varchar(50) not null comment '字段名: title, synopsis, label',
                  locale varchar(10) not null comment '语言代码: zh-CN, en-US, ja-JP',
                  content text not null comment '翻译内容',
                  created_at datetime not null default current_timestamp comment '创建时间',
                  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
                  unique key uk_entity_field_locale (entity_type, entity_id, field_name, locale),
                  key idx_entity_type_id (entity_type, entity_id)
                ) engine=InnoDB default charset=utf8mb4 comment='内容翻译表'
                """);

        seedCategoryTranslations();
    }

    private void seedCategoryTranslations() {
        if (!tableExists("content_translation")) {
            return;
        }
        List<TranslationSeed> seeds = categoryTranslationSeeds();
        for (TranslationSeed seed : seeds) {
            jdbcTemplate.update("""
                    insert into content_translation(entity_type, entity_id, field_name, locale, content)
                    values(?, ?, ?, ?, ?)
                    on duplicate key update content = values(content)
                    """, seed.entityType(), seed.entityId(), seed.fieldName(), seed.locale(), seed.content());
        }
    }

    private List<TranslationSeed> categoryTranslationSeeds() {
        return List.of(
                // background (背景)
                t("category_option", "modern", "label", "zh-CN", "现代"),
                t("category_option", "modern", "label", "en-US", "Modern"),
                t("category_option", "modern", "label", "ja-JP", "現代"),
                t("category_option", "modern", "label", "ko-KR", "현대"),
                t("category_option", "modern", "label", "es-ES", "Moderno"),
                t("category_option", "modern", "label", "fr-FR", "Moderne"),
                t("category_option", "modern", "label", "de-DE", "Modern"),
                t("category_option", "modern", "label", "pt-BR", "Moderno"),
                t("category_option", "modern", "label", "ru-RU", "Современный"),
                t("category_option", "modern", "label", "it-IT", "Moderno"),
                t("category_option", "modern", "label", "ar-SA", "حديث"),
                t("category_option", "modern", "label", "tr-TR", "Modern"),
                t("category_option", "modern", "label", "th-TH", "สมัยใหม่"),
                t("category_option", "modern", "label", "vi-VN", "Hiện đại"),
                t("category_option", "modern", "label", "id-ID", "Modern"),
                t("category_option", "modern", "label", "ms-MY", "Moden"),
                t("category_option", "modern", "label", "hi-IN", "आधुनिक"),

                t("category_option", "urban", "label", "zh-CN", "都市"),
                t("category_option", "urban", "label", "en-US", "Urban"),
                t("category_option", "urban", "label", "ja-JP", "都市"),
                t("category_option", "urban", "label", "ko-KR", "도시"),
                t("category_option", "urban", "label", "es-ES", "Urbano"),
                t("category_option", "urban", "label", "fr-FR", "Urbain"),
                t("category_option", "urban", "label", "de-DE", "Stadt"),
                t("category_option", "urban", "label", "pt-BR", "Urbano"),
                t("category_option", "urban", "label", "ru-RU", "Городской"),
                t("category_option", "urban", "label", "it-IT", "Urbano"),
                t("category_option", "urban", "label", "ar-SA", "حضري"),
                t("category_option", "urban", "label", "tr-TR", "Şehir"),
                t("category_option", "urban", "label", "th-TH", "เมือง"),
                t("category_option", "urban", "label", "vi-VN", "Đô thị"),
                t("category_option", "urban", "label", "id-ID", "Perkotaan"),
                t("category_option", "urban", "label", "ms-MY", "Bandar"),
                t("category_option", "urban", "label", "hi-IN", "शहरी"),

                t("category_option", "ancient", "label", "zh-CN", "古装"),
                t("category_option", "ancient", "label", "en-US", "Ancient"),
                t("category_option", "ancient", "label", "ja-JP", "時代劇"),
                t("category_option", "ancient", "label", "ko-KR", "고대"),
                t("category_option", "ancient", "label", "es-ES", "Antiguo"),
                t("category_option", "ancient", "label", "fr-FR", "Ancien"),
                t("category_option", "ancient", "label", "de-DE", "Antike"),
                t("category_option", "ancient", "label", "pt-BR", "Antigo"),
                t("category_option", "ancient", "label", "ru-RU", "Древний"),
                t("category_option", "ancient", "label", "it-IT", "Antico"),
                t("category_option", "ancient", "label", "ar-SA", "قديم"),
                t("category_option", "ancient", "label", "tr-TR", "Antik"),
                t("category_option", "ancient", "label", "th-TH", "โบราณ"),
                t("category_option", "ancient", "label", "vi-VN", "Cổ đại"),
                t("category_option", "ancient", "label", "id-ID", "Kuno"),
                t("category_option", "ancient", "label", "ms-MY", "Kuno"),
                t("category_option", "ancient", "label", "hi-IN", "प्राचीन"),

                // theme (主题)
                t("category_option", "romance", "label", "zh-CN", "现言"),
                t("category_option", "romance", "label", "en-US", "Romance"),
                t("category_option", "romance", "label", "ja-JP", "恋愛"),
                t("category_option", "romance", "label", "ko-KR", "로맨스"),
                t("category_option", "romance", "label", "es-ES", "Romance"),
                t("category_option", "romance", "label", "fr-FR", "Romance"),
                t("category_option", "romance", "label", "de-DE", "Romantik"),
                t("category_option", "romance", "label", "pt-BR", "Romance"),
                t("category_option", "romance", "label", "ru-RU", "Романтика"),
                t("category_option", "romance", "label", "it-IT", "Romantico"),
                t("category_option", "romance", "label", "ar-SA", "رومانسي"),
                t("category_option", "romance", "label", "tr-TR", "Romantik"),
                t("category_option", "romance", "label", "th-TH", "โรแมนติก"),
                t("category_option", "romance", "label", "vi-VN", "Tình cảm"),
                t("category_option", "romance", "label", "id-ID", "Romansa"),
                t("category_option", "romance", "label", "ms-MY", "Romantik"),
                t("category_option", "romance", "label", "hi-IN", "रोमांस"),

                t("category_option", "fantasy", "label", "zh-CN", "奇幻"),
                t("category_option", "fantasy", "label", "en-US", "Fantasy"),
                t("category_option", "fantasy", "label", "ja-JP", "ファンタジー"),
                t("category_option", "fantasy", "label", "ko-KR", "판타지"),
                t("category_option", "fantasy", "label", "es-ES", "Fantasía"),
                t("category_option", "fantasy", "label", "fr-FR", "Fantaisie"),
                t("category_option", "fantasy", "label", "de-DE", "Fantasy"),
                t("category_option", "fantasy", "label", "pt-BR", "Fantasia"),
                t("category_option", "fantasy", "label", "ru-RU", "Фэнтези"),
                t("category_option", "fantasy", "label", "it-IT", "Fantasy"),
                t("category_option", "fantasy", "label", "ar-SA", "خيال"),
                t("category_option", "fantasy", "label", "tr-TR", "Fantastik"),
                t("category_option", "fantasy", "label", "th-TH", "แฟนตาซี"),
                t("category_option", "fantasy", "label", "vi-VN", "Kỳ ảo"),
                t("category_option", "fantasy", "label", "id-ID", "Fantasi"),
                t("category_option", "fantasy", "label", "ms-MY", "Fantasi"),
                t("category_option", "fantasy", "label", "hi-IN", "फंतासी"),

                t("category_option", "suspense", "label", "zh-CN", "悬疑"),
                t("category_option", "suspense", "label", "en-US", "Suspense"),
                t("category_option", "suspense", "label", "ja-JP", "サスペンス"),
                t("category_option", "suspense", "label", "ko-KR", "스릴러"),
                t("category_option", "suspense", "label", "es-ES", "Suspenso"),
                t("category_option", "suspense", "label", "fr-FR", "Suspense"),
                t("category_option", "suspense", "label", "de-DE", "Thriller"),
                t("category_option", "suspense", "label", "pt-BR", "Suspense"),
                t("category_option", "suspense", "label", "ru-RU", "Триллер"),
                t("category_option", "suspense", "label", "it-IT", "Suspense"),
                t("category_option", "suspense", "label", "ar-SA", "إثارة"),
                t("category_option", "suspense", "label", "tr-TR", "Gerilim"),
                t("category_option", "suspense", "label", "th-TH", "ลึกลับ"),
                t("category_option", "suspense", "label", "vi-VN", "Hình sự"),
                t("category_option", "suspense", "label", "id-ID", "Suspense"),
                t("category_option", "suspense", "label", "ms-MY", "Suspens"),
                t("category_option", "suspense", "label", "hi-IN", "रोमांचक"),

                // setting (设定)
                t("category_option", "revenge", "label", "zh-CN", "复仇"),
                t("category_option", "revenge", "label", "en-US", "Revenge"),
                t("category_option", "revenge", "label", "ja-JP", "復讐"),
                t("category_option", "revenge", "label", "ko-KR", "복수"),
                t("category_option", "revenge", "label", "es-ES", "Venganza"),
                t("category_option", "revenge", "label", "fr-FR", "Vengeance"),
                t("category_option", "revenge", "label", "de-DE", "Rache"),
                t("category_option", "revenge", "label", "pt-BR", "Vingança"),
                t("category_option", "revenge", "label", "ru-RU", "Месть"),
                t("category_option", "revenge", "label", "it-IT", "Vendetta"),
                t("category_option", "revenge", "label", "ar-SA", "انتقام"),
                t("category_option", "revenge", "label", "tr-TR", "İntikam"),
                t("category_option", "revenge", "label", "th-TH", "แก้แค้น"),
                t("category_option", "revenge", "label", "vi-VN", "Báo thù"),
                t("category_option", "revenge", "label", "id-ID", "Balas Dendam"),
                t("category_option", "revenge", "label", "ms-MY", "Dendam"),
                t("category_option", "revenge", "label", "hi-IN", "प्रतिशोध"),

                t("category_option", "rebirth", "label", "zh-CN", "重生"),
                t("category_option", "rebirth", "label", "en-US", "Rebirth"),
                t("category_option", "rebirth", "label", "ja-JP", "転生"),
                t("category_option", "rebirth", "label", "ko-KR", "환생"),
                t("category_option", "rebirth", "label", "es-ES", "Renacimiento"),
                t("category_option", "rebirth", "label", "fr-FR", "Renaissance"),
                t("category_option", "rebirth", "label", "de-DE", "Wiedergeburt"),
                t("category_option", "rebirth", "label", "pt-BR", "Renascimento"),
                t("category_option", "rebirth", "label", "ru-RU", "Перерождение"),
                t("category_option", "rebirth", "label", "it-IT", "Rinascita"),
                t("category_option", "rebirth", "label", "ar-SA", "بعث"),
                t("category_option", "rebirth", "label", "tr-TR", "Yeniden Doğuş"),
                t("category_option", "rebirth", "label", "th-TH", "เกิดใหม่"),
                t("category_option", "rebirth", "label", "vi-VN", "Trùng sinh"),
                t("category_option", "rebirth", "label", "id-ID", "Kelahiran Kembali"),
                t("category_option", "rebirth", "label", "ms-MY", "Kelahiran Semula"),
                t("category_option", "rebirth", "label", "hi-IN", "पुनर्जन्म"),

                // audience (受众)
                t("category_option", "female", "label", "zh-CN", "女频"),
                t("category_option", "female", "label", "en-US", "Female"),
                t("category_option", "female", "label", "ja-JP", "女性向け"),
                t("category_option", "female", "label", "ko-KR", "여성"),
                t("category_option", "female", "label", "es-ES", "Femenino"),
                t("category_option", "female", "label", "fr-FR", "Féminin"),
                t("category_option", "female", "label", "de-DE", "Weiblich"),
                t("category_option", "female", "label", "pt-BR", "Feminino"),
                t("category_option", "female", "label", "ru-RU", "Женский"),
                t("category_option", "female", "label", "it-IT", "Femminile"),
                t("category_option", "female", "label", "ar-SA", "إناث"),
                t("category_option", "female", "label", "tr-TR", "Kadın"),
                t("category_option", "female", "label", "th-TH", "หญิง"),
                t("category_option", "female", "label", "vi-VN", "Nữ"),
                t("category_option", "female", "label", "id-ID", "Perempuan"),
                t("category_option", "female", "label", "ms-MY", "Perempuan"),
                t("category_option", "female", "label", "hi-IN", "महिला"),

                t("category_option", "male", "label", "zh-CN", "男频"),
                t("category_option", "male", "label", "en-US", "Male"),
                t("category_option", "male", "label", "ja-JP", "男性向け"),
                t("category_option", "male", "label", "ko-KR", "남성"),
                t("category_option", "male", "label", "es-ES", "Masculino"),
                t("category_option", "male", "label", "fr-FR", "Masculin"),
                t("category_option", "male", "label", "de-DE", "Männlich"),
                t("category_option", "male", "label", "pt-BR", "Masculino"),
                t("category_option", "male", "label", "ru-RU", "Мужской"),
                t("category_option", "male", "label", "it-IT", "Maschile"),
                t("category_option", "male", "label", "ar-SA", "ذكور"),
                t("category_option", "male", "label", "tr-TR", "Erkek"),
                t("category_option", "male", "label", "th-TH", "ชาย"),
                t("category_option", "male", "label", "vi-VN", "Nam"),
                t("category_option", "male", "label", "id-ID", "Laki-laki"),
                t("category_option", "male", "label", "ms-MY", "Lelaki"),
                t("category_option", "male", "label", "hi-IN", "पुरुष"),

                // contentType (内容类型)
                t("category_option", "ai", "label", "zh-CN", "AI剧"),
                t("category_option", "ai", "label", "en-US", "AI Drama"),
                t("category_option", "ai", "label", "ja-JP", "AIドラマ"),
                t("category_option", "ai", "label", "ko-KR", "AI 드라마"),
                t("category_option", "ai", "label", "es-ES", "Drama IA"),
                t("category_option", "ai", "label", "fr-FR", "Drame IA"),
                t("category_option", "ai", "label", "de-DE", "KI-Drama"),
                t("category_option", "ai", "label", "pt-BR", "Drama IA"),
                t("category_option", "ai", "label", "ru-RU", "ИИ-драма"),
                t("category_option", "ai", "label", "it-IT", "Dramma IA"),
                t("category_option", "ai", "label", "ar-SA", "دراما ذكية"),
                t("category_option", "ai", "label", "tr-TR", "AI Drama"),
                t("category_option", "ai", "label", "th-TH", "ละคร AI"),
                t("category_option", "ai", "label", "vi-VN", "Phim AI"),
                t("category_option", "ai", "label", "id-ID", "Drama AI"),
                t("category_option", "ai", "label", "ms-MY", "Drama AI"),
                t("category_option", "ai", "label", "hi-IN", "AI नाटक"),

                t("category_option", "real", "label", "zh-CN", "真人剧"),
                t("category_option", "real", "label", "en-US", "Live Drama"),
                t("category_option", "real", "label", "ja-JP", "実写ドラマ"),
                t("category_option", "real", "label", "ko-KR", "실사 드라마"),
                t("category_option", "real", "label", "es-ES", "Drama en Vivo"),
                t("category_option", "real", "label", "fr-FR", "Drame en Direct"),
                t("category_option", "real", "label", "de-DE", "Live-Drama"),
                t("category_option", "real", "label", "pt-BR", "Drama ao Vivo"),
                t("category_option", "real", "label", "ru-RU", "Живая драма"),
                t("category_option", "real", "label", "it-IT", "Dramma Live"),
                t("category_option", "real", "label", "ar-SA", "دراما حية"),
                t("category_option", "real", "label", "tr-TR", "Canlı Drama"),
                t("category_option", "real", "label", "th-TH", "ละครสด"),
                t("category_option", "real", "label", "vi-VN", "Phim thực"),
                t("category_option", "real", "label", "id-ID", "Drama Langsung"),
                t("category_option", "real", "label", "ms-MY", "Drama Langsung"),
                t("category_option", "real", "label", "hi-IN", "लाइव ड्रामा")
        );
    }

    private TranslationSeed t(String entityType, String entityId, String fieldName, String locale, String content) {
        return new TranslationSeed(entityType, entityId, fieldName, locale, content);
    }

    private record TranslationSeed(String entityType, String entityId, String fieldName,
                                   String locale, String content) {
    }

    private void ensurePaymentEventLogTable() {
        jdbcTemplate.execute("""
                create table if not exists payment_event_log (
                  id bigint primary key auto_increment comment 'ID',
                  channel varchar(32) not null comment '支付渠道: STRIPE/PAYPAL/APPLE_IAP/GOOGLE_PLAY',
                  event_id varchar(128) not null comment '渠道侧事件ID (Stripe Event.id / PayPal transmission_id / Apple notification UUID / Google eventId)',
                  event_type varchar(64) comment '事件类型 (checkout.session.completed / PAYMENT.CAPTURE.COMPLETED 等)',
                  order_no varchar(64) comment '关联订单号',
                  status varchar(16) not null default 'PROCESSING' comment 'PROCESSING/SUCCESS/FAILED',
                  created_at datetime not null default current_timestamp comment '创建时间',
                  unique key uk_channel_event (channel, event_id),
                  key idx_event_log_status (status, created_at)
                ) engine=InnoDB default charset=utf8mb4 comment='支付事件日志表 (Webhook去重审计)'
                """);
    }

    private void seedDefaultProducts() {
        if (!tableExists("point_product")) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from point_product", Integer.class);
        if (count != null && count > 0) {
            return;
        }

        // 积分充值包
        String[][] rechargePacks = {
                {"迷你包", "100", "0", "99", "com.duanju.points.mini", "3", "30", "20"},
                {"基础包", "500", "50", "499", "com.duanju.points.basic", "3", "30", "100"},
                {"进阶包", "1200", "180", "999", "com.duanju.points.advanced", "3", "15", "240"},
                {"豪华包", "3000", "600", "2499", "com.duanju.points.luxury", "2", "10", "600"},
                {"至尊包", "6000", "1500", "4999", "com.duanju.points.ultimate", "1", "5", "1200"},
        };
        for (int i = 0; i < rechargePacks.length; i++) {
            String[] p = rechargePacks[i];
            jdbcTemplate.update("""
                    insert into point_product(name, points, bonus_points, price_cents, currency, store_product_id,
                      product_category, daily_limit, monthly_limit, first_purchase_bonus, sort_order, status)
                    values(?, ?, ?, ?, 'USD', ?, 'RECHARGE', ?, ?, ?, ?, 1)
                    """, p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                    p[4], Integer.parseInt(p[5]), Integer.parseInt(p[6]), Integer.parseInt(p[7]), i + 1);
        }

        // VIP套餐
        String[][] vipPacks = {
                {"银牌月卡", "SILVER", "30", "299", "499", "3", "12"},
                {"银牌季卡", "SILVER", "90", "799", "1197", "3", "4"},
                {"银牌年卡", "SILVER", "365", "2680", "3588", "1", "2"},
                {"金牌月卡", "GOLD", "30", "499", "699", "3", "12"},
                {"金牌季卡", "GOLD", "90", "1299", "1797", "3", "4"},
                {"金牌年卡", "GOLD", "365", "4280", "5988", "1", "2"},
                {"钻石月卡", "DIAMOND", "30", "799", "999", "3", "12"},
                {"钻石季卡", "DIAMOND", "90", "1999", "2697", "3", "4"},
                {"钻石年卡", "DIAMOND", "365", "6680", "9588", "1", "2"},
        };
        int sortOrder = rechargePacks.length + 1;
        for (String[] p : vipPacks) {
            jdbcTemplate.update("""
                    insert into point_product(name, points, bonus_points, price_cents, currency, store_product_id,
                      product_category, membership_level, package_type, duration_days,
                      original_price_cents, daily_limit, monthly_limit, sort_order, status)
                    values(?, 0, 0, ?, 'USD', concat('com.duanju.vip.', lower(?)), 'VIP', ?, 'VIP_', ?, ?, ?, ?, ?, 1)
                    """, p[0], Integer.parseInt(p[2]), p[1], p[1],
                    Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]),
                    Integer.parseInt(p[6]), sortOrder++);
        }
    }
}
