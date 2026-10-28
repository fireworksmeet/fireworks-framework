# 框架能力索引

> 定位：框架**提供什么、不提供什么**的总索引，供业务仓库与 AI 代理"动手前先确认是否已有能力"。
> 边界：各 starter 的**用法、配置项、示例**以模块目录下的 `README.md` 为准（与代码同源）；编码规范见 `conventions/`。

---

# 一、三条不变量

框架在以下三点上不妥协，业务侧不应试图在框架层之外绕开：

1. **不绑定 ORM**：框架自身持久化只用 Spring JDBC，不依赖任何 ORM。业务用 MyBatis / MyBatis-Plus / JPA / jOOQ / 手写 SQL 均不受约束。
2. **不绑定数据库**：框架不引驱动、不规定数据库。内置方言支持 **PostgreSQL 12+ / MySQL 8.0.1+**，其余数据库按 §五 的扩展点接入。
3. **不提供分布式事务引擎**：不提供 Saga / TCC / 2PC。跨服务一致性统一走 **Outbox + 幂等 + 补偿**（见 `conventions/reliability.md`）。

---

# 二、模块清单

两个基础设施模块 + 17 个 starter：

| 模块 | 定位 | 关键能力 |
| --- | --- | --- |
| `fireworks-dependencies` | BOM | 统一第三方版本；业务以 `<scope>import</scope>` 引入 |
| `fireworks-spring-boot-parent` | 父 POM | Lombok、编译插件、注解处理器统一约定 |
| [`fireworks-common`](../fireworks-common-spring-boot-starter/README.md) | 公共基础 | `JsonUtil`、`SpringContextHolder`、加密（SM2/SM4/AES/RSA）、脱敏、Jackson 全局配置、日志关联段占位 |
| [`fireworks-api`](../fireworks-api-spring-boot-starter/README.md) | API 契约 | `Result`、分页（`PageRequest`/`PageResponse`/`PageWithFooterResponse`/`CursorUtil`）、`BizException`/`BizAssert`、枚举契约（`CodeEnum`/`@ExposedEnum`/`EnumUtil`） |
| [`fireworks-webmvc`](../fireworks-webmvc-spring-boot-starter/README.md) | WebMVC 增强 | 全局异常处理、统一错误响应（`/error` 兜底）、访问日志（LogBook）、IP 定位、客户端解析；默认引入 `fireworks-tracing` |
| [`fireworks-tracing`](../fireworks-tracing-spring-boot-starter/README.md) | 链路追踪 | Micrometer Tracing + OTel 桥接；Servlet 入站 / 客户端出站 / 跨线程自动埋点；注入日志关联段（`traceId` / `spanId`）；注册容器中唯一的上下文传播 `TaskDecorator`；不设采样率、不含 exporter |
| [`fireworks-oplog`](../fireworks-oplog-spring-boot-starter/README.md) | 操作日志 | `@LogRecord`（SpEL 模板，`success` / `type` / `bizNo` 必填）；操作人走 `IOperatorGetService`，持久化走 `ILogRecordService` ——**只生产不存储**，不提供查询契约 |
| [`fireworks-redis`](../fireworks-redis-spring-boot-starter/README.md) | Redis | 多数据源、`@DistributedLock`、`LockService`、`JsonRedisTemplate`、`RedisUtil` |
| [`fireworks-storage`](../fireworks-storage-spring-boot-starter/README.md) | 对象存储 | 统一 S3 协议接入（MinIO/OSS/AWS S3/COS/OBS）、上传下载、URL 生成、直传凭证、`@StorageUrl` |
| [`fireworks-export`](../fireworks-export-spring-boot-starter/README.md) | 导出 | EasyExcel 流式导出、临时文件 + `ExportFileStore` SPI 落点（不绑定存储实现）、`ExcelDataProvider` 流式批处理、合并单元格 |
| [`fireworks-message`](../fireworks-message-spring-boot-starter/README.md) | 消息推送 | WebSocket(Nchan)、短信（阿里/腾讯，SDK optional）、邮件；可插拔限流（`RateLimiter`，引入 `fireworks-redis` 时提供 GCRA 实现）。**不做**异步线程池、去重、幂等、发送记录落库（需要可靠投递时配 `fireworks-outbox`） |
| [`fireworks-id`](../fireworks-id-spring-boot-starter/README.md) | 分布式 ID | CosId 号段/雪花双模式（互斥）、`IdUtil` |
| [`fireworks-token`](../fireworks-token-spring-boot-starter/README.md) | Token | JWT 签发与校验（仅 `HS256` / `RS256` / `ES256`，多密钥 `kid` 轮换、签发唯一 / 验签全量）、失败分类、不透明令牌原语；**零 Web 依赖**，不解析 Bearer 头 |
| [`fireworks-security`](../fireworks-security-spring-boot-starter/README.md) | 安全认证 | 默认安全链（无认证机制，fail-closed）、`SecurityUserResolver` SPI、角色/权限 → `GrantedAuthority`、401/403 统一响应、`SecurityUserContext` |
| [`fireworks-rest-client`](../fireworks-rest-client-spring-boot-starter/README.md) | 同步 HTTP 客户端 | RestClient 连接池、超时、负载均衡、接口代理 |
| [`fireworks-webflux-client`](../fireworks-webflux-client-spring-boot-starter/README.md) | 响应式 HTTP 客户端 | WebClient 连接池、超时、负载均衡、接口代理 |
| [`fireworks-xxljob`](../fireworks-xxljob-spring-boot-starter/README.md) | 定时任务 | XXL-JOB 执行器自动配置、地址注册（IP 由 xxl-job 自行探测，可用 `executor.ip` 覆盖） |
| [`fireworks-outbox`](../fireworks-outbox-spring-boot-starter/README.md) | 可靠消息 | 事务性发件箱：同事务写入、`SKIP LOCKED` 并发领取、重试、超时恢复、DEAD、`event_id` 幂等、链路追踪 |
| [`fireworks-mybatis-plus`](../fireworks-mybatis-plus-spring-boot-starter/README.md) | MyBatis-Plus 粘合层（**可选**） | 分页拦截器自动装配（方言可显式覆盖）、审计字段填充、`PageAdapters.offset`、排序组件（枚举 → `OrderItem` / `ORDER BY`）、实体基类、`UniqueKeyUtil`、拦截器扩展点、可选接入 p6spy |

---

# 三、关键契约速查

| 主题 | 唯一出处 | 必须知道的一点 |
| --- | --- | --- |
| 统一响应 | `api` → `Result` | 成功用 `Result.ok(data)`；失败由全局异常处理统一转换，**不得手工拼装错误 `Result`** |
| 分页响应 | `api` → `PageResponse` | Offset 用 `PageResponse.ofOffset(records, current, size, total[, mapper])`，游标用 `ofCursor(...)`；空结果用 `emptyOffset` / `emptyCursor`；**框架不管取数** |
| 分页（MyBatis-Plus） | `mybatis-plus` → `PageAdapters` | Offset 用 `PageAdapters.offset(IPage[, mapper])`；Cursor 不经 `IPage`，多查一条后直接 `PageResponse.ofCursor(rows, size, cursorFn)`；该模块可选，不用 MP 的业务不引入 |
| 实体基类 | `mybatis-plus` → `BaseEntity` / `BaseOperatorEntity` | 仅对使用 MyBatis-Plus 且引入本模块的工程可用；**不在 `webmvc` 模块** |
| 序列化契约 | `common` → `CommonAutoConfiguration` | `null` 不输出；时间用 ISO 且不设全局时区；`BigInteger`/`BigDecimal` 字符串输出；`Long` 保持数字。**业务仓库禁止自定义全局 Jackson** |
| 脱敏 | `common` → `@Sensitive` / `SensitiveUtil` | `@Sensitive` 只对 `String` 字段生效、序列化时自动脱敏；算法在 `SensitiveUtil`，非序列化场景可直接调用 |
| 分布式 ID | `id` → `IdUtil` | 业务编号用 `getId(domain)`；技术 ID（traceId/消息 ID/幂等键）用 `getShareId*()`；一律 fail-fast，不返回哨兵值 |
| 分页方言 | MyBatis-Plus 自动识别 + `pagination.db-type` | 默认按连接 URL 识别（多数据源各自生效）；仅在 URL 被代理包装等场景显式覆盖，多数据源请保持为空 |
| 可靠消息 | `outbox` → `OutboxService` / `OutboxHandler` | `append` 必须与业务写操作处于**同一本地事务**；语义是 **At-Least-Once**，消费端必须幂等 |
| 操作日志 | `oplog` → `@LogRecord` | 框架只做拦截与渲染，落库由业务实现 `ILogRecordService` |
| 当前登录人 | `security` → `SecurityUserContext` | 只读门面；写入与清理由认证过滤器负责，业务不得直接操作 `SecurityContextHolder` |
| 错误码与 HTTP 状态 | `common` → `ExceptionStatus`；`webmvc` → 全局异常处理器 | 错误码只声明 `code` / `message`。**业务异常统一返回 HTTP `200`**，由 `code` 分流；4xx / 5xx 只留给协议 / 容器 / 系统层异常，业务失败率须按 `code` 统计 |
| 服务间错误传播 | `rest-client` / `webflux-client` | 框架不预设：负载均衡 Builder 保持标准行为（非 2xx 抛异常）。需要按 `Result.code` 判定的业务自行注册 `RestClientCustomizer` / `WebClientCustomizer` 关闭状态码异常（`api.md` §4.3） |
| 链路 traceId | `tracing`（`webmvc` 默认引入） | 引入即自动产生 traceId 并写入日志关联段——`micrometer-tracing-bridge-otel` + Actuator，**不含 exporter、不覆盖采样率**。`common` 只预留关联段占位；改用 Agent 等其他实现时须覆盖 `logging.pattern.correlation`，否则关联段恒为空且**不会报错** |

---

# 四、框架不提供什么

明确不做，避免业务侧误找或重复建设：

| 不提供 | 原因 / 替代 |
| --- | --- |
| Saga / TCC / 2PC | 补偿逻辑的业务语义无法复用；跨服务一致性走 Outbox + 幂等 + 补偿 |
| ORM 抽象 | 框架不规定业务用哪个 ORM；只为 MyBatis-Plus 提供**可选**粘合层 |
| 分页取数实现 | 取数属 ORM 插件职责；框架只提供响应契约，以及 MP 的适配 |
| 数据库驱动 | 业务自选数据库与驱动；框架只提供方言相关能力 |
| 追踪上报与后端 | 框架随 `webmvc` 提供追踪实现但**不含 exporter**：用什么 collector、是否上报、采样多少，全由业务决定 |
| 业务领域模型 / 表结构 / 错误码段位 | 属各业务仓库；框架只提供跨业务通用的工程规范 |
| 业务仓库的 `AGENTS.md` | AI 协作约定由各仓库自行维护 |

---

# 五、支持矩阵与扩展点

| 能力 | 内置支持 | 扩展方式 |
| --- | --- | --- |
| Outbox SQL | PostgreSQL 12+ / MySQL 8.0.1+ 共用一套（列类型统一，领取依赖 `SKIP LOCKED`） | 其他数据库需替换 `JdbcOutboxEventRepository`（只依赖 `OutboxEventRepository` 契约） |
| MyBatis-Plus 分页 `DbType` | MP 按连接 URL 自动识别 | 识别失败时配 `fireworks.mybatis-plus.pagination.db-type`，或自行声明 `MybatisPlusInterceptor`（框架装配会让位） |
| 其他数据库 | 不保证 | 按各组件扩展点接入，或不为该数据库启用对应能力 |
