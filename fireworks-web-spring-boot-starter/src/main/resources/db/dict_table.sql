-- ----------------------------
-- 字典类型表
-- ----------------------------
CREATE TABLE IF NOT EXISTS dict_type
(
    id          BIGSERIAL   NOT NULL PRIMARY KEY,
    name        VARCHAR(64) NOT NULL,
    code        VARCHAR(64) NOT NULL,
    status      BOOLEAN     NOT NULL DEFAULT TRUE,
    description VARCHAR(255),
    created_at  TIMESTAMP            DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP            DEFAULT CURRENT_TIMESTAMP,
    created_by  BIGINT,
    updated_by  BIGINT
    );

COMMENT ON TABLE dict_type IS '字典类型表';
COMMENT ON COLUMN dict_type.id IS '主键';
COMMENT ON COLUMN dict_type.name IS '字典名称';
COMMENT ON COLUMN dict_type.code IS '字典类型编码';
COMMENT ON COLUMN dict_type.status IS '状态';
COMMENT ON COLUMN dict_type.description IS '备注';
COMMENT ON COLUMN dict_type.created_at IS '创建时间';
COMMENT ON COLUMN dict_type.updated_at IS '更新时间';
COMMENT ON COLUMN dict_type.created_by IS '创建人';
COMMENT ON COLUMN dict_type.updated_by IS '更新人';

CREATE UNIQUE INDEX IF NOT EXISTS uk_dict_type_code ON dict_type (code);
CREATE INDEX IF NOT EXISTS idx_dict_type_created_id ON dict_type (created_at DESC, id DESC);

-- ----------------------------
-- 字典数据项表
-- ----------------------------
CREATE TABLE IF NOT EXISTS dict_item
(
    id          BIGSERIAL    NOT NULL PRIMARY KEY,
    type_id     BIGINT       NOT NULL,
    type_code   VARCHAR(64)  NOT NULL,
    label       VARCHAR(64)  NOT NULL,
    value       VARCHAR(128) NOT NULL,
    sort        INT          NOT NULL DEFAULT 0,
    status      BOOLEAN      NOT NULL DEFAULT TRUE,
    description VARCHAR(255),
    created_at  TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    created_by  BIGINT,
    updated_by  BIGINT
    );

COMMENT ON TABLE dict_item IS '字典数据项表';
COMMENT ON COLUMN dict_item.id IS '主键';
COMMENT ON COLUMN dict_item.type_id IS '对应的字典类型id';
COMMENT ON COLUMN dict_item.type_code IS '对应的字典类型编码';
COMMENT ON COLUMN dict_item.label IS '字典的key(显示标签主项)';
COMMENT ON COLUMN dict_item.value IS '字典的value(实际值)';
COMMENT ON COLUMN dict_item.sort IS '字典排序';
COMMENT ON COLUMN dict_item.status IS '状态';
COMMENT ON COLUMN dict_item.description IS '备注';
COMMENT ON COLUMN dict_item.created_at IS '创建时间';
COMMENT ON COLUMN dict_item.updated_at IS '更新时间';
COMMENT ON COLUMN dict_item.created_by IS '创建人';
COMMENT ON COLUMN dict_item.updated_by IS '更新人';

CREATE INDEX IF NOT EXISTS idx_dict_item_type_code ON dict_item (type_code);
CREATE INDEX IF NOT EXISTS idx_dict_item_type_code_value ON dict_item (type_code, value) WHERE status = TRUE;
CREATE INDEX IF NOT EXISTS idx_dict_item_type_id_created ON dict_item (type_id, created_at DESC, id DESC);