# fireworks-framework

基于 **Spring Boot 3.5** + **Spring Cloud 2025** 的生产级微服务基础设施框架（Starter 集合）。
`groupId: com.yzm.fireworks`，目标版本 `1.0.0-SNAPSHOT`，基于 **Java 21**。

以一组开箱即用的 `*-spring-boot-starter` 模块，为业务工程提供统一响应、Web 增强、Redis、消息、存储、导出、分布式事务、Token 认证等能力，屏蔽第三方中间件的接入与配置细节。

## 架构总览

```
fireworks-framework (聚合根)
├── fireworks-dependencies              # BOM 依赖清单（统一版本管理）
├── fireworks-spring-boot-parent        # 公共父 POM（Lombok / 编译插件 / 注解处理器）
└── 13 个 starter 模块
```

```
fireworks-common-spring-boot-starter       ─┐ 基础能力（工具/加密/脱敏/Jackson/MDC）
fireworks-api-spring-boot-starter          ─┤ API 层（Result/分页/BizException/操作日志注解）
┌──────────────────────────────────────────┤
│  Web 能力                    Redis 能力   │
│  fireworks-web               fireworks-redis
│  全局异常/日志/IP/客户端      多数据源/分布式锁
├──────────────────────────────────────────┤
│  Storage 存储  │  Export 导出  │  Msg 消息 │
│  OSS/MinIO     │  EasyExcel    │  短信/邮件/WS
├──────────────────────────────────────────┤
│  Saga 分布式事务 │  Id 分布式ID │  Token JWT │
│  XxlJob 定时任务  │  Rest/WebFlux Client
└──────────────────────────────────────────┘
```

## 模块清单

| 模块 | 定位 | 核心能力 |
| --- | --- | --- |
| `fireworks-dependencies` | BOM 依赖清单 | 统一 Spring Cloud、MyBatis-Plus、EasyExcel 等版本 |
| `fireworks-spring-boot-parent` | 公共父 POM | Lombok、编译插件、注解处理器统一约定 |
| `fireworks-common` | 公共基础 | `JsonUtil`/`SpringContextHolder`/加密(SM2/SM4/AES/RSA)/脱敏/Jackson 全局配置/MDC |
| `fireworks-api` | API 层 | `Result`/分页(Offset+Cursor)/`BizException`/`BizAssert`/`@OptLog` |
| `fireworks-web` | Web 增强 | 全局异常/Tomcat 兜底/系统日志/操作日志/IP 定位/客户端解析/排序组件 |
| `fireworks-redis` | Redis | 多数据源/`@DistributedLock`/`LockService`/JSON 序列化/JFR |
| `fireworks-storage` | 对象存储 | MinIO/OSS/直传凭证/孤儿文件治理/`@StorageUrl` |
| `fireworks-export` | 导出 | EasyExcel 流式导出/对象存储落库/Cursor 批处理/合并单元格 |
| `fireworks-msg` | 消息推送 | WebSocket/短信(阿里/腾讯)/邮件/去重/限流/记录 |
| `fireworks-saga` | 分布式事务 | Saga 编排/补偿/CAS 状态机/定时恢复 |
| `fireworks-id` | ID 生成 | 腾讯 Leaf 号段/`IdUtil`/`IdType` |
| `fireworks-token` | Token | JWT 签发解析/安全随机 Token/Bearer 支持 |
| `fireworks-rest-client` | HTTP 客户端 | RestClient 连接池/超时/负载均衡/接口代理 |
| `fireworks-webflux-client` | 响应式客户端 | WebClient 连接池/超时/负载均衡/接口代理 |
| `fireworks-xxljob` | 定时任务 | XXL-JOB 执行器自动配置/IP 探测/地址注册 |

## 技术栈

| 类别 | 选型 |
| --- | --- |
| 语言 / 框架 | Java 21、Spring Boot 3.5.15 |
| 微服务 | Spring Cloud 2025.0.2、Spring Cloud Alibaba 2025.0.0.0 |
| ORM | MyBatis-Plus 3.5.15 |
| 缓存 / 锁 | Redis（Spring Data Redis + Redisson） |
| 消息 | RocketMQ Starter 2.3.5 |
| 存储 | MinIO、阿里云 OSS |
| 导出 | EasyExcel 4.0.3 |
| 定时任务 | XXL-JOB 3.3.1 |
| 认证 | Nimbus JOSE JWT 9.31 |
| 分布式事务 | 自研 Saga 模式 |
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
        <artifactId>fireworks-web-spring-boot-starter</artifactId>
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

各模块使用方式、配置项与示例见对应模块目录下的 `README.md`：

| 模块 | 文档 |
| --- | --- |
| fireworks-common | [common/README.md](fireworks-common-spring-boot-starter/README.md) |
| fireworks-api | [api/README.md](fireworks-api-spring-boot-starter/README.md) |
| fireworks-web | [web/README.md](fireworks-web-spring-boot-starter/README.md) |
| fireworks-redis | [redis/README.md](fireworks-redis-spring-boot-starter/README.md) |
| fireworks-storage | [storage/README.md](fireworks-storage-spring-boot-starter/README.md) |
| fireworks-export | [export/README.md](fireworks-export-spring-boot-starter/README.md) |
| fireworks-msg | [msg/README.md](fireworks-msg-spring-boot-starter/README.md) |
| fireworks-saga | [saga/README.md](fireworks-saga-spring-boot-starter/README.md) |
| fireworks-id | [id/README.md](fireworks-id-spring-boot-starter/README.md) |
| fireworks-token | [token/README.md](fireworks-token-spring-boot-starter/README.md) |
| fireworks-rest-client | [rest-client/README.md](fireworks-rest-client-spring-boot-starter/README.md) |
| fireworks-webflux-client | [webflux-client/README.md](fireworks-webflux-client-spring-boot-starter/README.md) |
| fireworks-xxljob | [xxljob/README.md](fireworks-xxljob-spring-boot-starter/README.md) |

## 工程结构

- **聚合根** `pom.xml`：声明全部子模块，`<modules>` 顺序即构建顺序。
- **版本管理**：所有第三方版本集中在 `fireworks-dependencies` 的 `<properties>`。
- **模块依赖**：`common` 为最底层，被绝大多数模块依赖；`api`、`redis`、`id` 等作为其他模块的基础依赖。

## License

```
MIT License
Copyright (c) 2026 yzm
```
