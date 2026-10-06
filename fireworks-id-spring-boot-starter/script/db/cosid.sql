-- ------------------------------------------------------------
-- cosid（CosId 号段模式的分段分配表）
-- ------------------------------------------------------------
-- 背景与注意事项：
--
-- 1. 表名 cosid 沿用 CosId 上游默认值，**不要改名**：CosId 内置 SQL、后续版本与
--    监控工具都可能假定该表名，改名需要覆盖更多内置 SQL，收益为零。
--
-- 2. 本项目使用 PostgreSQL，而 CosId 内置的建表/初始化 SQL 是 MySQL 方言：
--      · create table ... bigint unsigned ... engine = InnoDB   （PG 不支持 unsigned / engine）
--      · insert ... value (?, ?, unix_timestamp())              （PG 无 unix_timestamp()）
--      · update cosid set last_max_id = last_max_id + ?, last_fetch_time = unix_timestamp()
--    因此：
--      · 建表由本脚本负责（应用侧关闭 enable-auto-init-cosid-table）
--      · 自增 SQL 必须覆盖为 PG 版本（见 README 的配置示例），否则取号会直接报错
--
-- 3. 号段行（name = {namespace}.{业务号段}）由 CosId 依据 cosid.segment.provider.<name>.offset
--    自动初始化（enable-auto-init-id-segment=true，其 SQL 同样覆盖为 PG 版本），
--    因此本脚本只建表、不插入号段数据。
--
-- 4. 迁移安全：offset 必须 **大于** 原 Leaf 表中的 max_id（推荐 max_id + 1），
--    否则新号可能与迁移前的历史号重叠。
-- ------------------------------------------------------------

create table if not exists cosid
(
    name            varchar(100) not null,
    last_max_id     bigint       not null default 0,
    last_fetch_time bigint       not null default 0,
    constraint cosid_pk primary key (name)
);

comment on table cosid is 'CosId 号段分配表（name = {namespace}.{业务号段}，last_max_id 为已分配到的最大 ID）';
