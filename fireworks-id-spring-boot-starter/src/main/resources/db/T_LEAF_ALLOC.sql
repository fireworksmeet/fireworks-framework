-- ----------------------------
-- T_LEAF_ALLOC (Leaf分布式ID分配表)
-- ----------------------------
CREATE TABLE IF NOT EXISTS T_LEAF_ALLOC
(
    biz_tag     varchar(128) NOT NULL DEFAULT '',
    max_id      bigint       NOT NULL DEFAULT 1,
    step        int          NOT NULL DEFAULT 5000,
    description varchar(256) NULL     DEFAULT NULL,
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (biz_tag)
    );

-- 使用号段模式时，需要指定key(对应biz_tag的值)，如果该key不在数据库中，则无法生成
-- 官方建议step为单机QPS的600倍
INSERT INTO T_LEAF_ALLOC(biz_tag, max_id, step, description)
VALUES ('ORDER', 131949450910, 5000, '用于订单的编号'),
       ('MEMBER', 908536682, 5000, '用于会员的编号'),
       ('FILE_SUFFIX', 13253190733140, 10000, '用于文件的后缀编号'),
       ('SERIAL_NUMBER', 1544986154200202, 10000, '用于流水号');