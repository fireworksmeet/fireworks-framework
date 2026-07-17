-- ----------------------------
-- 消息记录表
-- ----------------------------
CREATE TABLE IF NOT EXISTS message_record
(
    id                  BIGSERIAL                           NOT NULL PRIMARY KEY,
    message_id          VARCHAR(64)                         NOT NULL,
    message_type        VARCHAR(32)                         NOT NULL,
    priority            VARCHAR(32)                         NULL,
    platform            VARCHAR(32)                         NOT NULL,
    user_id             text                                NULL,
    groupId             varchar(64)                         NULL,
    title               varchar(128)                        NULL,
    content             text                                NULL,
    template_id         varchar(64)                         NULL,
    status              varchar(16)                         NOT NULL,
    error_message       text                                NULL,
    external_message_id varchar(64)                         NULL,
    send_time           TIMESTAMP                           NULL,
    retry_count         int       DEFAULT 0                 NULL,
    max_retries         int       DEFAULT 3                 NULL,
    next_retry_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

COMMENT ON TABLE message_record IS '消息记录表';
COMMENT ON COLUMN message_record.id IS '主键';
COMMENT ON COLUMN message_record.message_id IS '消息ID';
COMMENT ON COLUMN message_record.message_type IS '消息类型';
COMMENT ON COLUMN message_record.priority IS '消息优先级';
COMMENT ON COLUMN message_record.platform IS '平台标识';
COMMENT ON COLUMN message_record.user_id IS '接收用户ID';
COMMENT ON COLUMN message_record.groupId IS '群组ID';
COMMENT ON COLUMN message_record.title IS '消息标题';
COMMENT ON COLUMN message_record.content IS '消息内容';
COMMENT ON COLUMN message_record.template_id IS '模板ID';
COMMENT ON COLUMN message_record.status IS '发送状态';
COMMENT ON COLUMN message_record.error_message IS '错误信息';
COMMENT ON COLUMN message_record.external_message_id IS '外部消息ID';
COMMENT ON COLUMN message_record.send_time IS '发送时间';
COMMENT ON COLUMN message_record.retry_count IS '重试次数';
COMMENT ON COLUMN message_record.max_retries IS '最大重试次数';
COMMENT ON COLUMN message_record.next_retry_time IS '下次重试时间';
COMMENT ON COLUMN message_record.created_at IS '创建时间';
COMMENT ON COLUMN message_record.updated_at IS '更新时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_message_messageid ON message_record (message_id);
CREATE INDEX IF NOT EXISTS idx_message_status_retrycount ON message_record (status, retry_count);
CREATE INDEX IF NOT EXISTS idx_message_next_retry_time ON message_record (next_retry_time) WHERE status IN ('PENDING', 'RETRYING');
CREATE INDEX IF NOT EXISTS idx_message_user_id ON message_record (user_id);
CREATE INDEX IF NOT EXISTS idx_message_created_at_id ON message_record (created_at DESC,id DESC);