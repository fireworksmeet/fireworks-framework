# 0002. 审计字段三字段，时间列表达「绝对时刻」

- **状态**：已接受
- **日期**：2026-08-17

## 背景

时间列的约定参照阿里巴巴《Java 开发手册》的"必备三字段"（`id` / `create_time` / `update_time`，`DATETIME`），
但落地时长期踩两个坑：

1. 照搬"必备三字段"时顺手加上 `created_by` / `updated_by`：C 端业务表没有"后台操作人"概念，多出两个永远为空的列。
2. 列类型选了**不带时区语义**的 `DATETIME` / `TIMESTAMP`，跨时区部署与多端展示时同一行读出两个时间。
   其中 MySQL 的 `TIMESTAMP` 还多两个问题：**上限 2038-01-19**；读写按**会话时区**换算，同一时刻在不同连接的会话时区下会写成不同值。

## 决策

### 1. 审计字段：必备三个，操作人按需

【强制】每张业务表必备 `id`、`created_at`、`updated_at`。

【推荐】确有审计需求（B 端管理表、资金 / 权限变更）时才追加 `created_by` / `updated_by`；C 端业务表不加。

### 2. 绝对时刻列：PG 用 TIMESTAMPTZ，MySQL 用 DATETIME(6)（UTC）

| 数据库 | 类型 | 说明 |
| --- | --- | --- |
| PostgreSQL | `TIMESTAMPTZ` | 存绝对时刻，按会话时区展示 |
| MySQL | `DATETIME(6)` | **以 UTC 写入**；无 2038 上限，微秒精度与 PG 对齐 |

【禁止】MySQL 下用 `TIMESTAMP` 承载业务时间：2038 上限与会话时区隐式换算，两个问题都不受代码控制。

【强制】MySQL 连接必须固定会话时区为 UTC（`connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`），
使 `CURRENT_TIMESTAMP`、应用写入、应用读取三者的基准一致；否则 `DEFAULT CURRENT_TIMESTAMP` 会写入会话本地时间。

### 3. 命名：按时间语义选后缀

【强制】后缀由**语义与列类型**决定，不按语法（过去分词）硬套：

| 语义 | 列类型 | 后缀 | 示例 |
| --- | --- | --- | --- |
| 某一瞬间 | 绝对时刻列 | `_at` | `created_at`、`updated_at`、`paid_at`、`published_at`、`deleted_at`、`next_retry_at` |
| 区间端点 | 绝对时刻列 | `_from` / `_to` | `valid_from`、`valid_to`、`active_from` |
| 业务日期 | `DATE` | `_date`（惯用词可省略） | `settlement_date`、`holiday_date`、`birthday` |
| 一天中的钟点 | `TIME` | `_time` | `business_open_time`、`reminder_time` |
| 时长 | 数值 / `INTERVAL` | `_seconds`、`_days`、`_duration` | `timeout_seconds`、`retention_days` |

【禁止】用 `_time` 表示"某一瞬间"（`create_time`）：它与钟点列、时长列无法区分，读者无法只凭名字判断类型。

【禁止】同一语义两套写法：`expires_at` 与 `expired_at` 只能保留一个，全库统一。

### 4. Java 侧统一 Instant

【强制】绝对时刻列在 Java 侧映射为 `Instant`。

【禁止】映射为 `LocalDateTime`（丢失时区语义，序列化后前端无法判断基准）。

## 理由

1. **操作人是 B 端概念**：C 端表没有"后台操作人"，强制两列只会得到永远为 `NULL` 的字段。
2. **MySQL 的 TIMESTAMP 不可控**：范围止于 2038（合同到期、长期有效期直接溢出），且写入值与会话时区绑定——连接池里连接来源不同，同一时刻会落成不同值。
3. **DATETIME(6) 的代价可以覆盖**：它本身不带时区语义，用"连接强制 UTC + 应用只写 UTC"补上；精度 µs 与 `TIMESTAMPTZ` 一致，不丢精度。
4. **后缀即类型信号**：`_at` / `_from` / `_to` / `_date` / `_time` / `_seconds` 各自绑定一种语义与列类型，
   看到名字就能判断是瞬间、区间、日期、钟点还是时长；`create_time` 这类写法做不到这一点。
   语法上的"过去分词"只是巧合，`valid_from`、`business_open_time`、`timeout_seconds` 都不符合它。

## 后果

- **正面**：字段精简；不存在 2038 溢出；跨时区读取只有一个基准（UTC），展示层按需转换。
- **注意**：与阿里手册的差异是**有意的**——用 `created_at` 而非 `create_time`，操作人字段按需而非必备。
- **注意**：MySQL 的 UTC 约定是**部署要求**（连接参数），不是可选项；漏配不会报错，只会静默写入本地时间。
- **注意**：手写 SQL 中的 `CURRENT_TIMESTAMP` 依赖同一份会话时区配置，见 `database.md` §8.3。

## 相关

- `conventions/database.md` — §3.5 审计字段、§3.6 时间字段、§8.3 写路径
- ADR 0003 — 逻辑删除时间列 `deleted_at`
- ADR 0001 — 同属"通用数据库约定的方言适配"
