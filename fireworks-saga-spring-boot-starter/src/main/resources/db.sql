CREATE TABLE IF NOT EXISTS saga_log
(
    id              bigserial,
    saga_id         varchar(32)                         NOT NULL,
    step_name       varchar(32)                         NOT NULL,
    status          varchar(32)                         NOT NULL,
    retry_count     int       DEFAULT 0                 NULL,
    max_retries     int       DEFAULT 0                 NULL,
    next_retry_time timestamp DEFAULT CURRENT_TIMESTAMP NULL,
    error_msg       text                                NULL,
    param           text                                NULL,
    created_at      timestamp DEFAULT CURRENT_TIMESTAMP NULL,
    updated_at      timestamp DEFAULT CURRENT_TIMESTAMP NULL,
    CONSTRAINT pk_saga_log PRIMARY KEY (id)
    );


COMMENT ON TABLE saga_log IS 'Saga事务日志表';
COMMENT ON COLUMN saga_log.id IS '主键';
COMMENT ON COLUMN saga_log.saga_id IS 'Saga全局事务ID';
COMMENT ON COLUMN saga_log.step_name IS 'SagaStep的名称';
COMMENT ON COLUMN saga_log.status IS 'SagaStatus的状态值';
COMMENT ON COLUMN saga_log.retry_count IS '重试次数';
COMMENT ON COLUMN saga_log.max_retries IS '最大重试次数';
COMMENT ON COLUMN saga_log.next_retry_time IS '下次重试时间';
COMMENT ON COLUMN saga_log.error_msg IS '错误信息';
COMMENT ON COLUMN saga_log.param IS '参数信息';
COMMENT ON COLUMN saga_log.created_at IS '创建时间';
COMMENT ON COLUMN saga_log.updated_at IS '更新时间';


CREATE UNIQUE INDEX uk_sagaid_stepname ON saga_log (saga_id, step_name);
-- 部分索引：专为定时重试任务优化（假设需要重试的状态为 'FAILED' 或 'RETRYING'）
-- 这样成功/归档的数据不会占用这个索引空间，扫表极快
CREATE INDEX idx_saga_log_retry_active ON saga_log (next_retry_time)
    WHERE status IN ('FAILED', 'RETRYING') AND retry_count < max_retries;