# 0001. 枚举字段存储使用 SMALLINT，状态与布尔字段命名规则

- **状态**：已接受
- **日期**：2026-08-17

## 背景

枚举字段的存储方式长期不统一：既有用 `VARCHAR` 存枚举名（如 `'ACTIVE'`），也有用数字存 `code`，导致查询、索引、
与 Java 枚举对齐时混乱。状态类字段命名同样不统一（`enabled` / `valid` / `state` / `status` 混用）。

这三条同属表结构基础规范，一起定以避免彼此冲突。**具体条款（类型、命名、列注释、`CodeEnum` / `IEnum` 映射、
Checklist）以 `conventions/database.md` §3.9 / §3.10 为准，本文只记录"为什么这么定"。**

## 决策

1. 枚举字段用 `SMALLINT` 存 `code`：**不存枚举名**，也不用数据库原生 `ENUM` 类型。
2. `code` 不从 `0` 起，相邻值之间预留间隔（起始值与间隔见 `database.md` §3.10）。
3. 状态与布尔按**业务语义**分流：属性语义用 `is_xxx` 布尔列；流程 / 生命周期语义用 `status SMALLINT`
   （存在多个状态维度时用 `xxx_status`，命名细则见 `database.md` §3.10）。

## 理由

1. **存储与索引成本**：`SMALLINT` 2 字节，远小于 `VARCHAR(32)`；索引更小，查询与写入更快。
2. **与枚举命名解耦**：存数字时，Java 枚举常量重命名（`NORMAL` → `ACTIVE`）**无需改动数据库数据**；
   存枚举名则每次重命名都要 `UPDATE` 全部历史数据，成本高且易漏。
3. **避免 0 值歧义**：`0` 与"未初始化 / 默认值"语义易混淆（Java `int` 默认 0、JS 中 0 为 falsy）。
4. **预留间隔是为演进准备的**：新增值可插入预留位，已有 `code` 不变——这正是"新增枚举值是兼容变更"的前提（见 ADR 0005）。
5. **被否决的方案**：
   - `VARCHAR` 存枚举名：重命名要全表改数据，字符串比较与索引也更贵（见理由 2）；
   - PostgreSQL 原生 `ENUM`：增删值要 `ALTER TYPE` 重建类型，MySQL 无等价能力，而框架同时支持两种方言；
   - `TINYINT`：没有额外收益（`SMALLINT` 已是 2 字节），且 MySQL 的 `TINYINT(1)` 会被当作布尔别名，产生语义歧义。
6. **布尔列保留 `is_`、Java 属性去掉前缀**：数据库侧保留可读性；Java 侧若带 `is` 前缀（字段 `isEnabled`），
   getter 会变成 `getIsEnabled()`，序列化字段名与 SQL 列名两头都不一致。

## 后果

- **正面**：类型统一后，查询、索引、与 Java 枚举对齐都不再有方言与习惯差异。
- **注意**：裸 SQL 可读性下降，因此**列注释是强制要求**，不是可选项。
- **注意**：Java 枚举需承载业务编码——框架契约为 `fireworks-api` 的 `CodeEnum`；
  使用 MyBatis-Plus 的工程用 `IEnum` 或 `@EnumValue` 完成持久化映射。

## 相关

- `conventions/database.md` — §3.9 布尔字段、§3.10 状态字段与枚举（条款唯一出处）
- `conventions/java.md` — Java 枚举（`code` 与 `label`、`@JsonValue`）
- ADR 0005 — 枚举演进依赖本文的"预留间隔"
