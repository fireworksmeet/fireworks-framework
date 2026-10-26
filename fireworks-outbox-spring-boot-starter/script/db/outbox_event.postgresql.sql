-- ----------------------------
-- Outbox 事件表（PostgreSQL 12+）
-- 设计约束：
--   1. 业务数据与 outbox_event 必须处于【同一个本地事务】，以此保证「业务成功 ⇔ 消息最终会被投递」；
--   2. 投递语义是 At-Least-Once，不是 Exactly-Once，消费端必须幂等（用 event_id 做唯一键）；
--   3. status 使用 SMALLINT 编码：10=PENDING 20=PROCESSING 30=SUCCEEDED 40=DEAD；
--   4. event_id 用 varchar(32)（32 位紧凑 UUID）、payload 用 text：与 MySQL 版列类型一致，
--      这样 Java 侧一套 SQL 就能同时写入两种库（不需要 ::uuid / ::jsonb 转型）；
--      时间列用 timestamptz（存绝对时刻）；payload 不需要 JSON 运算符，故不用 jsonb。
--   5. 列注释与 MySQL 版的 COMMENT 一一对应，修改时请两边同步。
-- ----------------------------
CREATE TABLE IF NOT EXISTS outbox_event
(
    id              BIGSERIAL       NOT NULL PRIMARY KEY,
    event_id        VARCHAR(32)     NOT NULL,
    event_type      VARCHAR(128)    NOT NULL,
    entity_type     VARCHAR(64)     NULL,
    entity_id       VARCHAR(128)    NULL,
    payload         TEXT            NOT NULL,
    status          SMALLINT        NOT NULL DEFAULT 10,
    retry_count     INT             NOT NULL DEFAULT 0,
    max_retry_count INT             NOT NULL DEFAULT 20,
    next_retry_at   TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_at      TIMESTAMPTZ     NULL,
    claimed_by      VARCHAR(128)    NULL,
    published_at    TIMESTAMPTZ     NULL,
    last_error      TEXT            NULL,
    trace_id        VARCHAR(64)     NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_outbox_event_event_id UNIQUE (event_id)
    );

COMMENT ON TABLE outbox_event IS '事务性发件箱事件表';
COMMENT ON COLUMN outbox_event.id IS '主键';
COMMENT ON COLUMN outbox_event.event_id IS '事件实例ID（消息实例唯一标识，32 位紧凑 UUID）';
COMMENT ON COLUMN outbox_event.event_type IS '事件类型，由 OutboxHandler#eventType 消费';
COMMENT ON COLUMN outbox_event.entity_type IS '业务实体类型，如 ORDER / USER；用于按实体排查';
COMMENT ON COLUMN outbox_event.entity_id IS '业务实体ID，如订单号；与 entity_type 一起定位消息来源';
COMMENT ON COLUMN outbox_event.payload IS '消息载荷（只放最小上下文，禁止写入密码 / Token / 密钥）';
COMMENT ON COLUMN outbox_event.status IS '状态：10=PENDING 20=PROCESSING 30=SUCCEEDED 40=DEAD';
COMMENT ON COLUMN outbox_event.retry_count IS '已重试次数';
COMMENT ON COLUMN outbox_event.max_retry_count IS '最大重试次数，超过置为 DEAD';
COMMENT ON COLUMN outbox_event.next_retry_at IS '下次可投递时间（指数退避 + jitter）';
COMMENT ON COLUMN outbox_event.claimed_at IS '领取时间：Worker 把该行领走并置为 PROCESSING 的时刻；超时恢复据此判断';
COMMENT ON COLUMN outbox_event.claimed_by IS '领取者标识（Worker ID）；状态回写时用作持有权校验';
COMMENT ON COLUMN outbox_event.published_at IS '投递成功时间（终态）';
COMMENT ON COLUMN outbox_event.last_error IS '最近一次失败原因';
COMMENT ON COLUMN outbox_event.trace_id IS '写入时的链路追踪 ID，投递时恢复 MDC 以串起异步链路';
COMMENT ON COLUMN outbox_event.created_at IS '创建时间';
COMMENT ON COLUMN outbox_event.updated_at IS '更新时间';

-- 待投递扫描：部分索引，只覆盖 PENDING
CREATE INDEX IF NOT EXISTS idx_outbox_event_pending
    ON outbox_event (next_retry_at, id) WHERE status = 10;

-- PROCESSING 超时恢复：部分索引，只覆盖未完成的行
CREATE INDEX IF NOT EXISTS idx_outbox_event_processing
    ON outbox_event (claimed_at) WHERE status = 20;

-- 按业务实体排查（"这单产生了哪些消息"）
CREATE INDEX IF NOT EXISTS idx_outbox_event_entity
    ON outbox_event (entity_type, entity_id);
