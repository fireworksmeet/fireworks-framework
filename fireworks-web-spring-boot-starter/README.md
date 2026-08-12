# fireworks-web-spring-boot-starter

Web 层增强 Starter，提供全局异常处理、Tomcat 错误兜底、系统日志、操作日志、IP 定位、客户端信息解析与通用排序组件。

## 功能特性

- **全局异常处理**：`GlobalExceptionHandler` 统一捕获参数校验、业务异常、系统异常等，返回统一的 `Result` 结构。
- **Tomcat 错误兜底**：`BaseController` 兜底处理 Tomcat 转发的异常，绕过消息转换器避免二次异常链。
- **系统日志**：通过 `@EnableSystemLog` 开启，记录每个请求的参数、耗时、返回值，支持慢请求告警与敏感 Header 过滤。
- **操作日志**：通过 `@EnableOptLog` 开启，拦截标记了 `@OptLog` 的方法，交由 `OptLogService` 处理用户行为。
- **IP 定位**：`IpLocationService` 根据 IP 查询地理位置（需开启 `fireworks.web.ip-location.enabled=true`）。
- **客户端信息**：`ClientInfoUtil` 解析 UA 与 IP，获取设备、浏览器、系统等信息（需开启 `fireworks.web.client-info.enabled=true`）。
- **通用排序组件**：`SortBuilder` / `AbstractSortRegistry` 将枚举排序类型映射为排序字段，前端可控排序。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.yzm.fireworks</groupId>
    <artifactId>fireworks-web-spring-boot-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

### 2. 全局异常处理（默认生效）

引入依赖后，`WebAutoConfiguration` 会自动注册 `GlobalExceptionHandler`，无需额外配置。业务代码抛出 `BizException` 即返回统一的错误结构：

```java
throw new BizException(CommonExceptionStatus.REQUEST_PARAMS_ERROR, "参数不合法");
```

支持的异常类型：参数校验（`BindException` / `ConstraintViolationException` / `MethodArgumentNotValidException`）、
缺少参数、类型不匹配、非法参数/状态、请求方法不支持、请求体格式错误、`BizException` 以及兜底 `Exception`。

### 3. 系统日志

在启动类或配置类上添加 `@EnableSystemLog`：

```java
@EnableSystemLog
@SpringBootApplication
public class DemoApplication { ... }
```

开启后，每个请求的参数、耗时、返回值都会写入日志。可通过配置调整行为：

```yaml
fireworks:
  web:
    system-log:
      print-params: true          # 是否打印请求参数，默认 true
      print-result: true          # 是否打印返回值，默认 true
      slow-threshold-ms: 2000     # 慢请求阈值，超过则 WARN 告警，0 表示关闭
      max-length: 2048            # 日志内容最大长度，-1 表示不限制
      include-headers:            # 仅打印白名单中的 Header，避免敏感信息泄露
        - Authorization
        - X-User-Id
      exclude-paths:              # 排除的路径（Ant 风格），不记录日志
        - /actuator/**
```

### 4. 操作日志

在启动类或配置类上添加 `@EnableOptLog`：

```java
@EnableOptLog
@SpringBootApplication
public class DemoApplication { ... }
```

然后为需要记录的方法添加 `@OptLog` 注解（注解位于 `com.yzm.fireworks.api.annotation.OptLog`）：

```java
@OptLog(module = "用户", type = OptLogType.CREATE, content = "新增用户: #{user.name}")
public void addUser(User user) { ... }
```

拦截器会调用 `OptLogService.processLog(...)` 处理日志，业务方需实现 `OptLogService` 接口自定义落库逻辑；
操作人信息可通过实现 `OptLogOperatorProvider` 提供。

> 注意：操作日志的开关注解是 **`@EnableOptLog`**，与系统日志的 `@EnableSystemLog` 相互独立。

### 5. IP 定位

开启 `fireworks.web.ip-location.enabled=true` 后注入 `IpLocationService`：

```yaml
fireworks:
  web:
    ip-location:
      enabled: true
```

```java
@Resource
private IpLocationService ipLocationService;

IpLocation location = ipLocationService.locate(request.getRemoteAddr());
```

### 6. 客户端信息解析

开启 `fireworks.web.client-info.enabled=true` 后使用 `ClientInfoUtil` 解析 UA：

```java
ClientInfo info = ClientInfoUtil.getClientInfo(request);
// info.getBrowser() / info.getOs() / info.getDevice() ...
```

### 7. 通用排序组件

1. 定义一个排序枚举（实现 `IEnum<Integer>`），用于与前端通信。
2. 实现 `AbstractSortRegistry<T>` 注册排序字段映射：

```java
@Component
public class DefaultSortRegistry extends AbstractSortRegistry<SortType> {

    public DefaultSortRegistry() {
        super(SortType.class);
    }

    @Override
    protected void register() {
        register(SortType.COMMON_LATEST, new SortBuilder()
                .desc(BaseEntity::getCreatedAt)
                .desc(BaseEntity::getId)
                .build());
    }
}
```

3. 在查询处调用 `registry.apply(sortType, page)` 将排序字段塞入分页对象。

### 8. 字典表 SQL

`resources/db/dict_table.sql` 中提供了常用的字典表建表脚本，按需执行即可。

## 配置项汇总

| 前缀 | 说明 |
| --- | --- |
| `fireworks.web.ip-location` | IP 定位开关 |
| `fireworks.web.client-info` | 客户端信息解析开关 |
| `fireworks.web.system-log` | 系统日志相关配置 |
