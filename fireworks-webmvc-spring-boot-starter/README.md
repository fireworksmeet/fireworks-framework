# fireworks-webmvc-spring-boot-starter

Servlet（WebMVC）层增强 Starter，提供全局异常处理、统一错误响应（`/error` 兜底）、访问日志、链路追踪、IP 定位与客户端信息解析。

## 功能特性

- **全局异常处理**：`GlobalExceptionHandler` 统一捕获参数校验、业务异常、系统异常等，返回统一的 `Result` 结构。
- **统一错误响应**：`ResultErrorAttributes` 替换 Spring Boot 默认的 `ErrorAttributes`，保留官方 `/error` 链路（`BasicErrorController`），把未进入 Controller 的容器 / 协议层错误也装配成 `Result` 结构，HTTP 状态保持容器记录的真实值。
- **OpenAPI 文档**：携带 `springdoc-openapi-starter-webmvc-ui`，引入即具备 OpenAPI 描述与 Swagger UI，无额外配置。
- **访问日志**：基于 LogBook，记录每次 HTTP 请求与响应的元数据、耗时与（按策略的）body，支持路径排除与敏感字段脱敏。**引入依赖即生效，无开关注解**。
- **链路追踪**：**默认引入 `fireworks-tracing`**，每个请求自动产生 traceId / spanId 并写入日志关联段；不含 exporter，上报后端由业务自选（详见 [tracing/README.md](../fireworks-tracing-spring-boot-starter/README.md)）。
- **IP 定位**：`IpLocationService` 根据 IP 查询地理位置（需开启 `fireworks.webmvc.ip-location.enabled=true`）。
- **客户端信息**：`ClientInfoUtil` 解析 UA 与 IP，获取设备、浏览器、系统等信息（需开启 `fireworks.webmvc.client-info.enabled=true`）。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.yzm.fireworks</groupId>
    <artifactId>fireworks-webmvc-spring-boot-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

### 2. 全局异常处理（默认生效）

引入依赖后，`WebMvcAutoConfiguration` 会自动注册 `GlobalExceptionHandler`，无需额外配置。业务代码抛出 `BizException` 即返回统一的错误结构：

```java
throw new BizException(CommonExceptionStatus.REQUEST_PARAMS_ERROR, "参数不合法");
```

支持的异常类型：参数校验（`BindException` / `ConstraintViolationException` / `MethodArgumentNotValidException`）、
缺少参数、类型不匹配、非法参数/状态、请求方法不支持、请求体格式错误、`BizException` 以及兜底 `Exception`。

未进入 Controller 的错误（过滤器 / 异步链路中未被捕获的异常、404、405、容器层 5xx）由 `/error` 链路兜底：
`WebMvcAutoConfiguration` 以 `@ConditionalOnMissingBean(ErrorAttributes.class)` 注册 `ResultErrorAttributes`，
响应体同样是 `Result` 结构，HTTP 状态沿用容器记录的真实状态码。业务异常则统一返回 HTTP `200`，由 `code` 分流。

### 3. 访问日志（默认生效）

由 [LogBook](https://github.com/zalando/logbook) 提供。**框架已注入所需默认值，引入依赖即可工作**，每个请求输出请求与响应两条 JSON 记录：

```json
{"origin":"remote","type":"request","correlation":"cc4319a91cc36761","protocol":"HTTP/1.1","remote":"127.0.0.1","method":"GET","uri":"http://localhost:8080/echo","path":"/echo","headers":{...}}
{"origin":"local","type":"response","correlation":"cc4319a91cc36761","duration":12,"protocol":"HTTP/1.1","status":200,"headers":{...},"body":"{\"ok\":true}"}
```

框架注入的默认值（均为最低优先级，业务可覆盖）：

| 配置 | 框架默认值 | 说明 |
| --- | --- | --- |
| `logging.level.org.zalando.logbook` | `TRACE` | LogBook 的写入器判断 `isTraceEnabled()`，不打开则**一条日志都没有且不报错** |
| `logbook.write.max-body-size` | `8192` | 单条 body 上限（字节）；库默认 `-1`（不限），大 body 会撑爆日志体积 |

**body 由策略决定**。库默认 `default`（全量记录 body），生产环境推荐「黑匣子」——正常请求只留元数据，出错时才带上完整报文，把 body 的存储与 IO 开销压到错误率量级：

```yaml
logbook:
  strategy: body-only-if-status-at-least   # default | status-at-least | body-only-if-status-at-least | without-body
  minimum-status: 400                     # 仅状态码 >= 该值时才记录 body
```

其余常用配置：

```yaml
logbook:
  predicate:                              # 只处理哪些请求；path 为 Glob（Ant 风格）
    exclude:                              # 命中即不记录
      - path: /actuator/**                # 以 / 开头匹配路径（不含 query string），否则匹配整个 URI
      - path: /health
      - path: /internal/**
        methods: [POST, PUT]              # 同一条内 path 与 methods 为「与」关系
  obfuscate:                              # 脱敏；Authorization 已由库默认处理
    parameters:
      - access_token
    json-body-fields:
      - $.password
      - $.idCard
```

> **`logbook.exclude` / `logbook.include` 已废弃，不要再写**。3.x 的配置元数据里这两个键的 `deprecation.level`
> 是 `warning`，官方 `replacement` 就是 `logbook.predicate.exclude` / `logbook.predicate.include`。
> 旧键目前仍然生效（新旧两处会被合并，且 exclude 优先于 include），但它只接受裸路径，无法表达 `methods`
> 之类的条件；新配置一律走 `predicate`。

关闭访问日志：

```yaml
logbook:
  filter:
    enabled: false
```

#### 必须与业务日志分流

访问日志输出到独立 logger `org.zalando.logbook.Logbook`，**不要与业务日志混排**（见 `docs/conventions/observability.md`）。在 `logback-spring.xml` 中导向独立 appender：

```xml
<appender name="ACCESS_LOG" class="ch.qos.logback.core.rolling.RollingFileAppender">
  <!-- rolling policy 等略 -->
</appender>

<appender name="ASYNC_ACCESS" class="ch.qos.logback.classic.AsyncAppender">
  <queueSize>8192</queueSize>
  <appender-ref ref="ACCESS_LOG"/>
</appender>

<logger name="org.zalando.logbook.Logbook" level="TRACE" additivity="false">
  <appender-ref ref="ASYNC_ACCESS"/>
</logger>
```

> 访问日志走 `org.zalando.logbook.Logbook` 这个 logger。若它的 appender pattern 里带 `%X{traceId:-}`（框架默认的 console / file 格式就带），访问日志即可与业务日志按 traceId 对齐；上面这种自建 appender 需要自己把关联段写进 `<pattern>`。

### 4. IP 定位

开启 `fireworks.webmvc.ip-location.enabled=true` 后注入 `IpLocationService`：

```yaml
fireworks:
  webmvc:
    ip-location:
      enabled: true
      v4-xdb-path: classpath:ip2region/ip2region_v4.xdb   # 支持 classpath: / file: 前缀
      v6-xdb-path: classpath:ip2region/ip2region_v6.xdb
```

> **地址库需自备**：ip2region 的 xdb 文件（约 10MB，且需随官方更新）不在本 Starter 内，
> 请放到 classpath（如 `src/main/resources/ip2region/`）或文件系统后配置路径。
> 路径无 `classpath:` / `file:` 前缀时：以 `/` 开头或含 `:` 视为文件系统路径，其余视为 classpath 相对路径。
>
> **开启即强依赖**：地址库缺失或损坏时**启动直接失败**（`IllegalStateException` 并指明配置项），
> 不会退化成「每次请求都查不到、只刷 WARN」的静默空转。

```java
@Resource
private IpLocationService ipLocationService;

LocationInfo location = ipLocationService.getLocation(ClientInfoUtil.getClientIp(request));
// location.getCountry() / getProvince() / getCity() / getIsp() / getCountryCode()
```

> 私网 / 环回地址直接返回「本地」，不会查库。

### 5. 客户端信息解析

开启 `fireworks.webmvc.client-info.enabled=true` 后使用 `ClientInfoUtil` 解析 UA：

```java
ClientInfo info = ClientInfoUtil.getClientInfo(request);
// info.getBrowser() / info.getOs() / info.getDevice() ...
```

> **IP 采信规则**：只有直连对端是内网 / 环回地址时才读取 `X-Real-IP`、`X-Forwarded-For` 等代理头；
> 公网直连一律使用 `remoteAddr`，避免客户端伪造。地址链从右往左跳过内网地址，取第一个公网地址。
>
> **`ClientInfo.requestUrl` 不含 query string**：该值常用于操作日志 / 审计落库，query 中的
> `token`、`password` 等参数不应入库；确有需要请自行读取 `getQueryString()` 并脱敏。

## 配置项汇总

| 前缀 | 说明 |
| --- | --- |
| `fireworks.webmvc.ip-location` | IP 定位。`enabled` 为开关；`v4-xdb-path` / `v6-xdb-path` 为 ip2region 库路径（默认 `ip2region/ip2region_v4.xdb`、`ip2region/ip2region_v6.xdb`）；`searcher-num` 为搜索器数量（默认 `15`） |
| `fireworks.webmvc.client-info` | 客户端信息解析。`enabled` 为开关，开启后激活 UA 解析并注册预热处理器 |
| `logbook` | 访问日志。原生 LogBook 配置，框架仅注入日志级别与 body 上限，其余键以 [官方文档](https://github.com/zalando/logbook) 为准 |
