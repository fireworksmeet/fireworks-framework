# fireworks-framework

基于 **Spring Boot 3.5** + **Spring Cloud 2025** 的生产级微服务基础设施框架（Starter 集合）。
`groupId: com.yzm.fireworks`，目标版本 `1.0.0-SNAPSHOT`，基于 **Java 21**。

以一组开箱即用的 `*-spring-boot-starter` 模块，为业务工程提供统一响应、Web 增强、Redis、消息推送、可靠消息（Outbox）、存储、导出、Token 认证等能力，屏蔽第三方中间件的接入与配置细节。

框架**不绑定 ORM 与数据库**：内部持久化只用 Spring JDBC，内置数据库方言支持 **PostgreSQL 12+ / MySQL 8**。
业务侧的 ORM（MyBatis / MyBatis-Plus / JPA / jOOQ / 手写 SQL）与数据库选择均不受框架约束；
习惯 MyBatis-Plus 的服务，可按需引入可选的 `fireworks-mybatis-plus` 粘合层（分页拦截器自动装配 + 审计字段填充）。

## 架构总览

```
fireworks-framework (聚合根)
├── fireworks-dependencies              # BOM 依赖清单（统一版本管理）
├── fireworks-spring-boot-parent        # 公共父 POM（Lombok / 编译插件 / 注解处理器）
└── 17 个 starter 模块
```

```
fireworks-common-spring-boot-starter       ─┐ 基础能力（工具/加密/脱敏/Jackson/MDC）
fireworks-api-spring-boot-starter          ─┤ API 层（Result/分页/BizException）
┌──────────────────────────────────────────┤
│  Web 能力                    Redis 能力   │
│  fireworks-webmvc            fireworks-redis
│  全局异常/日志/IP/客户端      多数据源/分布式锁
├──────────────────────────────────────────┤
│  Storage 存储  │  Export 导出  │  Msg 消息 │
│  OSS/MinIO     │  EasyExcel    │  短信/邮件/WS
├──────────────────────────────────────────┤
│  Id 分布式ID  │  Token JWT   │  XxlJob 定时任务
│  Rest/WebFlux Client
└──────────────────────────────────────────┘
```

## 模块清单

| 模块 | 定位 | 核心能力 |
| --- | --- | --- |
| `fireworks-dependencies` | BOM 依赖清单 | 统一 Spring Cloud、EasyExcel、RocketMQ 等版本 |
| `fireworks-spring-boot-parent` | 公共父 POM | Lombok、编译插件、注解处理器统一约定 |
| `fireworks-common` | 公共基础 | `JsonUtil`/`SpringContextHolder`/加密(SM2/SM4/AES/RSA)/脱敏/Jackson 全局配置/MDC |
| `fireworks-api` | API 层 | `Result`/分页(Offset+Cursor)/`BizException`/`BizAssert`/枚举选项 |
| `fireworks-webmvc` | WebMVC 增强 | 全局异常/统一错误响应(`/error` 兜底)/访问日志(LogBook)/IP 定位/客户端解析（默认引入 tracing） |
| `fireworks-tracing` | 链路追踪 | Micrometer Tracing + OTel 桥接；traceId 全链路串联并写入日志关联段；不含 exporter |
| `fireworks-oplog` | 操作日志 | `@LogRecord` 注解 + SpEL 记录操作日志 |
| `fireworks-redis` | Redis | 多数据源/`@DistributedLock`/`LockService`/JSON 序列化/JFR |
| `fireworks-storage` | 对象存储 | 统一 S3 协议接入(MinIO/OSS/AWS S3/COS/OBS)/上传下载/URL 生成/直传凭证 |
| `fireworks-export` | 导出 | EasyExcel 流式导出/对象存储落库/Cursor 批处理/合并单元格 |
| `fireworks-msg` | 消息推送 | WebSocket/短信(阿里/腾讯)/邮件/去重/限流/记录 |
| `fireworks-id` | ID 生成 | CosId 号段/雪花双模式（互斥）/`IdUtil` |
| `fireworks-token` | Token | JWT 签发解析/安全随机 Token/Bearer 支持 |
| `fireworks-security` | 安全认证 | Spring Security 集成/Header Token 认证/`@RequirePermission` 权限校验 |
| `fireworks-rest-client` | HTTP 客户端 | RestClient 连接池/超时/负载均衡/接口代理 |
| `fireworks-webflux-client` | 响应式客户端 | WebClient 连接池/超时/负载均衡/接口代理 |
| `fireworks-xxljob` | 定时任务 | XXL-JOB 执行器自动配置/IP 探测/地址注册 |
| `fireworks-outbox` | 可靠消息 | 事务性发件箱/SKIP LOCKED 并发领取/重试/超时恢复/DEAD/幂等键 |
| `fireworks-mybatis-plus` | MyBatis-Plus 粘合层（可选） | 分页拦截器自动装配/审计字段填充/`PageAdapters`/排序组件/`BaseEntity`/插件扩展点 |

## 技术栈

| 类别 | 选型 |
| --- | --- |
| 语言 / 框架 | Java 21、Spring Boot 3.5.15 |
| 微服务 | Spring Cloud 2025.0.2、Spring Cloud Alibaba 2025.0.0.0 |
| 数据库 | 不绑定驱动；内置方言支持 PostgreSQL 12+ / MySQL 8（见各模块 `script/db/`） |
| ORM | 不绑定（框架内部仅用 Spring JDBC；业务自选 MyBatis / JPA / jOOQ） |
| 链路追踪 | Micrometer Tracing + OTel 桥接（`fireworks-tracing`，由 `webmvc` 默认引入，不含 exporter） |
| 缓存 / 锁 | Redis（Spring Data Redis + Redisson） |
| 消息 | RocketMQ Starter 2.3.5 |
| 存储 | AWS S3 SDK v2（S3 协议，兼容 MinIO / OSS / COS / OBS） |
| 导出 | EasyExcel 4.0.3 |
| 定时任务 | XXL-JOB 3.3.1 |
| 认证 | Nimbus JOSE JWT 9.31 |
| 辅助 | Lombok、MapStruct |

## 快速开始

在业务工程中按需引入对应 Starter：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.yzm.fireworks</groupId>
            <artifactId>fireworks-dependencies</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.yzm.fireworks</groupId>
        <artifactId>fireworks-webmvc-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>com.yzm.fireworks</groupId>
        <artifactId>fireworks-redis-spring-boot-starter</artifactId>
    </dependency>
    <!-- 按需添加其他模块 -->
</dependencies>
```

各模块均基于 Spring Boot 自动配置（`META-INF/spring/*.AutoConfiguration.imports`）自动生效，无需手动装配。

## 文档

框架层规范（能力索引、编码规范、架构决策）见 [`docs/`](docs/README.md)——**这是跨业务通用规范的唯一权威出处**。
各模块使用方式、配置项与示例见对应模块目录下的 `README.md`：

| 模块 | 文档 |
| --- | --- |
| fireworks-common | [common/README.md](fireworks-common-spring-boot-starter/README.md) |
| fireworks-api | [api/README.md](fireworks-api-spring-boot-starter/README.md) |
| fireworks-webmvc | [webmvc/README.md](fireworks-webmvc-spring-boot-starter/README.md) |
| fireworks-redis | [redis/README.md](fireworks-redis-spring-boot-starter/README.md) |
| fireworks-storage | [storage/README.md](fireworks-storage-spring-boot-starter/README.md) |
| fireworks-export | [export/README.md](fireworks-export-spring-boot-starter/README.md) |
| fireworks-msg | [msg/README.md](fireworks-msg-spring-boot-starter/README.md) |
| fireworks-id | [id/README.md](fireworks-id-spring-boot-starter/README.md) |
| fireworks-token | [token/README.md](fireworks-token-spring-boot-starter/README.md) |
| fireworks-rest-client | [rest-client/README.md](fireworks-rest-client-spring-boot-starter/README.md) |
| fireworks-webflux-client | [webflux-client/README.md](fireworks-webflux-client-spring-boot-starter/README.md) |
| fireworks-xxljob | [xxljob/README.md](fireworks-xxljob-spring-boot-starter/README.md) |
| fireworks-oplog | [oplog/README.md](fireworks-oplog-spring-boot-starter/README.md) |
| fireworks-outbox | [outbox/README.md](fireworks-outbox-spring-boot-starter/README.md) |
| fireworks-mybatis-plus | [mybatis-plus/README.md](fireworks-mybatis-plus-spring-boot-starter/README.md) |
| fireworks-tracing | [tracing/README.md](fireworks-tracing-spring-boot-starter/README.md) |

## 工程结构

- **聚合根** `pom.xml`：声明全部子模块，`<modules>` 顺序即构建顺序。
- **版本管理**：所有第三方版本集中在 `fireworks-dependencies` 的 `<properties>`。
- **模块依赖**：`common` 为最底层，被绝大多数模块依赖；`api`、`redis`、`id` 等作为其他模块的基础依赖。
- **组件表脚本**：各 starter 自带的建表脚本放在**该模块根目录**的 `script/db/` 下（不进 jar，部署时从源码仓库取用）。
  含框架自有表的模块（outbox / msg / id）按方言分文件命名，如 `xxx.postgresql.sql` / `xxx.mysql.sql`，按业务实际数据库取用。

## License

```
MIT License
Copyright (c) 2026 yzm
```
