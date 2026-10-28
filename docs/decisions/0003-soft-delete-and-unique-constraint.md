# 0003. 逻辑删除用 `deleted_at`，MySQL 以 `deleted_flag` 满足唯一约束

- **状态**：已接受
- **日期**：2026-08-17

## 背景

逻辑删除字段的选型有多个方案：阿里规范主张 `is_deleted`（`UNSIGNED TINYINT`，1/0）；早期实现中还有用 `deleted`（`BIGINT`，0/时间戳）的。

更棘手的是**软删除与唯一约束的冲突**：业务表常有唯一键需求（手机号、账号等），但软删除后旧记录仍占用唯一索引，导致同名记录无法重新插入。PostgreSQL 可用部分索引解决，MySQL 没有这个能力。

## 决策

### 1. `deleted_at` 是唯一的语义列：`NULL` = 未删除

【强制】逻辑删除字段为 `deleted_at`（可空绝对时刻列）：

- `NULL` = 未删除；
- 非空 = 删除时刻。

【强制】所有查询、实体判断、恢复判定统一使用 `deleted_at IS NULL`。

【禁止】用哨兵值（如 `1970-01-01`）表达"未删除"：那会让每条 SQL 都携带一个魔法常量，
写错一个字符就静默查空，且 `IS NULL` 的可读性更好。

【禁止】使用 `is_deleted`、`deleted` 等等价替代（MySQL 唯一约束所需的 `deleted_flag` 属配套列，见下）。

### 2. 唯一约束：PG 用部分索引，MySQL 用 `deleted_flag`

**PostgreSQL** — 部分唯一索引，只约束未删除行：

```sql
CREATE UNIQUE INDEX uk_xxx_biz_key ON xxx (biz_key) WHERE deleted_at IS NULL;
```

**MySQL** — 无部分索引，引入仅服务索引的判别列：

```sql
-- 未删除恒为 0；删除时置为该行 id（保证已删除行之间也互不冲突）
deleted_flag BIGINT NOT NULL DEFAULT 0,
UNIQUE KEY uk_xxx_biz_key (biz_key, deleted_flag)
```

- 【强制】`deleted_flag` **只服务唯一约束**，不参与任何业务查询——一旦用它写条件，SQL 就与方言绑定（PG 没有这一列）。
- 【说明】MySQL 的组合唯一索引之所以成立，是因为 `deleted_flag` 非空且未删除行恒为 `0`：`(biz_key, 0)` 只允许一条。
- 【说明】判别列取 `id` 而不是时间戳：`id` 唯一且不可重复，时间戳在同一微秒会撞。
- 【说明】反例：`UNIQUE(biz_key, deleted_at)` 在 MySQL 下失效——唯一索引把 `NULL` 视为互不相等，未删除行之间不受约束。

### 3. 删除与恢复

【强制】删除同时维护两列（MySQL），并带未删除条件；**影响行数即结果，不需要先查后删**：

```sql
-- PostgreSQL
UPDATE app_user SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
WHERE id = ? AND deleted_at IS NULL;

-- MySQL：多维护 deleted_flag
UPDATE app_user SET deleted_at = CURRENT_TIMESTAMP,
                    deleted_flag = id,
                    updated_at = CURRENT_TIMESTAMP
WHERE id = ? AND deleted_at IS NULL;
```

【强制】恢复时 MySQL 必须把 `deleted_flag` 置回 `0`，否则该行仍占着唯一键：

```sql
UPDATE app_user SET deleted_at = NULL, deleted_flag = 0, updated_at = CURRENT_TIMESTAMP
WHERE id = ? AND deleted_at IS NOT NULL;
```

【禁止】依赖 ORM 的自动逻辑删除配置：其 0/1 语义与 `NULL` / 时间戳语义不符，且隐式注入的条件在评审中不可见。

## 理由

1. **查询只有一个写法**：`IS NULL` 在两种方言下都成立，业务 SQL 与方言解耦。哨兵方案会让每条 SQL 都带魔法常量，是更大的长期成本。
2. **MySQL 必须有不参与 `NULL` 语义的判别列**：唯一索引把 `NULL` 视为互不相等，未删除行无法被约束。
3. **判别列用 `id`**：唯一、不可重复；时间戳判别在同一微秒会撞唯一键。
4. **代价可控**：多一列只出现在 MySQL，且维护动作可以写进删除模板；`deleted_flag` 不进入业务语义。

## 后果

- **正面**：查询、实体、恢复判断在两种方言下完全一致；PG 不增加列；MySQL 唯一约束正确。
- **注意**：MySQL 删除时**必须**同时置 `deleted_flag = id`，漏掉时旧值仍占着唯一键（典型现象：用户删了，手机号还是注册不了）。恢复时置回 `0`。
- **注意**：`deleted_flag` 一旦被用于业务查询，SQL 就失去跨方言可移植性。
- **注意**：更新语句的影响行数同时反映"是否真的删除成功"，调用方据此判断幂等结果。
- **注意**：与阿里规范（`is_deleted` TINYINT）不同——用时间戳而非布尔，理由见上。

## 相关

- `conventions/database.md` — §3.13 逻辑删除、§十 软删除专项、§十五 建表模板
- `conventions/mybatis.md` — 禁止依赖 ORM 自动逻辑删除
- ADR 0002 — 时间列类型与命名
