-- ----------------------------
-- Outbox 事件表（MySQL 8.0.1+，领取依赖 SKIP LOCKED）
-- 与 PostgreSQL 版的差异：
--   1. 时间列用 DATETIME(6) 而不是 TIMESTAMP：MySQL 的 TIMESTAMP 上限是 2038-01-19，
--      与本表的投递时间、创建时间冲突；DATETIME 无此限制。精度取 6 位（微秒），
--      与 PostgreSQL 的 timestamptz 对齐，也符合 database.md 方言表 #2 的约定。
--   2. MySQL 无「部分索引」，改为普通复合索引（把 status 放在最左列，效果等价）；
--   3. status 编码、唯一约束语义与 PostgreSQL 版保持一致（MySQL 唯一索引允许多个 NULL）。
-- 列类型与 PostgreSQL 版保持一致（event_id VARCHAR(32)、payload TEXT），Java 侧共用一套 SQL。
-- 列注释与 PostgreSQL 版的 COMMENT ON 一一对应，修改时请两边同步。
--
-- 【部署要求】DATETIME 不带时区，存的是写入时刻在会话时区下的字面量。
-- 必须把所有实例的连接时区固定为 UTC，否则不同时区写入的同一时刻会得到不同字面量，比较结果出错：
--   jdbc:mysql://host/db?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
-- PostgreSQL 的 timestamptz 自带时区语义，无此要求。
-- ----------------------------
CREATE TABLE IF NOT EXISTS outbox_event
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id        VARCHAR(32)  NOT NULL COMMENT '事件实例ID（消息实例唯一标识，32 位紧凑 UUID）',
    event_type      VARCHAR(128) NOT NULL COMMENT '事件类型，由 OutboxHandler#eventType 消费',
    entity_type     VARCHAR(64)  NULL COMMENT '业务实体类型，如 ORDER / USER；用于按实体排查',
    entity_id       VARCHAR(128) NULL COMMENT '业务实体ID，如订单号；与 entity_type 一起定位消息来源',
    payload         TEXT         NOT NULL COMMENT '消息载荷（只放最小上下文，禁止写入密码 / Token / 密钥）',
    status          SMALLINT     NOT NULL DEFAULT 10 COMMENT '状态：10=PENDING 20=PROCESSING 30=SUCCEEDED 40=DEAD',
    retry_count     INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    max_retry_count INT          NOT NULL DEFAULT 20 COMMENT '最大重试次数，超过置为 DEAD',
    next_retry_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '下次可投递时间（指数退避 + jitter）',
    claimed_at      DATETIME(6)  NULL COMMENT '领取时间：Worker 把该行领走并置为 PROCESSING 的时刻；超时恢复据此判断',
    claimed_by      VARCHAR(128) NULL COMMENT '领取者标识（Worker ID）；状态回写时用作持有权校验',
    published_at    DATETIME(6)  NULL COMMENT '投递成功时间（终态）',
    last_error      TEXT         NULL COMMENT '最近一次失败原因',
    trace_id        VARCHAR(64)  NULL COMMENT '写入时的链路追踪 ID，投递时恢复 MDC 以串起异步链路',
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event_event_id (event_id)
    ) ENGINE = InnoDB
      DEFAULT CHARSET = utf8mb4
      COMMENT = '事务性发件箱事件表';

-- 待投递扫描：status 放在最左列，等价于 PG 的部分索引
CREATE INDEX idx_outbox_event_pending ON outbox_event (status, next_retry_at, id);

-- PROCESSING 超时恢复
CREATE INDEX idx_outbox_event_processing ON outbox_event (status, claimed_at);

-- 按业务实体排查（"这单产生了哪些消息"）
CREATE INDEX idx_outbox_event_entity ON outbox_event (entity_type, entity_id);
