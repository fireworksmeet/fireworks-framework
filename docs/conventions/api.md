# API 契约

> 定位：HTTP 接口契约——方法与路径、请求与响应形态、参数校验、异常与错误码、规模上限、分页契约、序列化契约、接口文档、版本演进。
> 边界：分层与事务见 `spring-boot.md`；幂等见 `idempotency.md`；版本演进"为什么这么定"见 `../decisions/0005-api-versioning-strategy.md`；框架提供的 `Result` / `PageResponse` 见 `../capabilities.md`。
>
> 规范级别：【强制】违反即视为代码问题；【推荐】默认遵守；【禁止】原则上不得使用；【说明】背景解释。

---

# 一、方法与路径

## 1.1 方法：统一 POST

- 【强制】所有接口统一使用 `@PostMapping`，**禁止** GET / PUT / DELETE / PATCH。
- 【说明】**保留统一 POST 的唯一有效理由是"统一入口"**：网关的签名校验（`X-Timestamp` + `X-Nonce` + `X-Sign`）、访问日志、限流、幂等因此只有一条路径。历史上"老旧浏览器不支持 PATCH""企业代理只放行 GET/POST""跨域预检不友好"等理由**今天均已不成立**，不得再作为依据。
- 【说明】这是**有意偏离公开规范**的一项：Google AIP（标准方法 + 自定义方法）、Zalando Guidelines（#141 `keep URLs verb-free`）、微信支付 APIv3（标准 HTTP 方法）都使用标准 HTTP 方法。本项目以"统一入口"的收益换取这项偏离。

## 1.2 路径构成

【强制】映射路径 = `{模块路径}`（+ 可选后缀，见 §1.4）；`{模块路径}` 可含一层语义前缀（如 `/orders/items`）。路径中的资源名段使用**复数**（§1.3）。

【强制】API 路径集中定义为常量，**禁止在 Controller 中硬编码**。

【强制】除内部接口统一使用 `/internal` 前缀外，**不额外添加前缀**。

【说明】`/internal` 是**有功能的例外**，不是违规：它用于 Swagger 内外接口分组与网关白名单，使内部接口对外不可达。

```java
public interface ExampleApi {
    String REQUEST_MAPPING = EXAMPLE_PATH;   // /examples
}
```

### 版本段：默认不出现，需要时置于首段

【强制】**首版路径不带版本段**——`v1` 不承载任何区分信息（按 §10.1，大多数变更属兼容演进，升版本是最后手段），与 §10.5"不预建模板"同理。

【强制】确需表达版本时（出现无法兼容的破坏性变更），**在路径首段引入**：`/v2/{模块路径}/...`。这样网关用**一条** `/v2/**` 规则即可完成版本分流与灰度——这正是版本段真正的价值。

【说明】四种做法都有大厂实例，不存在唯一标准：

| 做法 | 代表 |
| --- | --- |
| **无版本段** | 微信公众平台 `/cgi-bin/token`、微信支付 v2 `/pay/unifiedorder` |
| 版本在首段 | 微信支付 v3 `/v3/pay/transactions/jsapi`、高德 `/v3/geocode/geo`、Google `/v1/...`、Stripe |
| 模块在前、版本在后 | 百度地图 `/place/v2/search`、腾讯地图 `/ws/geocoder/v1/` |
| 版本在查询参数 | Microsoft / Azure `?api-version=`；腾讯云、阿里云 `Version=` |

【说明】不采用"`{模块}/v1`"的原因：它把版本当作**模块**的属性，而版本是 **API** 的属性；且首版就带版本段，承载的是"暂不存在的信息"。代价是升版本时各模块**同批**迁移，不能逐模块独立升版本。

## 1.3 路径段命名

- 【强制】路径段使用**小写 + 连字符（kebab-case）**：`/shipment-orders`、`/user-profiles`。**禁止**小驼峰与下划线。
  【说明】对齐 Zalando #129（`{MUST} use kebab-case for path segments`，路径段须匹配 `^[a-z][a-z\-0-9]*$`），也是 Microsoft / GitHub / Stripe 的通行做法。
- 【强制】**资源名段使用复数**：`/users`、`/orders`。**禁止**单数资源名（`/user`）。对齐 Zalando #134（`{MUST} pluralize resource names`）。
  【说明】**复数只约束 URL 路径**，不牵动表名与 Java 类名：路径是**资源集合**的对外命名，表 / 类是**单条记录**的内部命名，两者不要求一致。例如 `/users/detail` ↔ `UserController.detail()` ↔ `app_user` 表（表名规则见 `database.md` §三，类名规则见 `spring-boot.md` §四）。
  【说明】Zalando #134 约束的是**资源名**，不是"所有路径段"：**命名空间 / 分组段**（`/internal`、`/system`）与**功能端点段**（`/captcha`、`/upload`、`/callback`）不承载实体集合语义，保持原形，**禁止**机械复数化为 `/internal`→(不变)、`/system`→`/systems`、`/captcha`→`/captchas`。
- 【强制】禁止尾斜杠与重复斜杠（`/users/`、`/users//orders` 均不合规）。
  【说明】对齐 Zalando #136（`use normalized paths without empty path segments and trailing slashes`）。

## 1.4 动作后缀

【强制】后缀只能取自下表，置于**模块路径之后**：`{模块路径} + {后缀}`。

| 操作 | 后缀 | 操作 | 后缀 |
| --- | --- | --- | --- |
| 分页查询 | `/page` | 删除 | `/delete` |
| 列表查询 | `/list` | 树形结构 | `/tree` |
| 详情查询 | `/detail` | 启用 / 禁用 | `/enable` `/disable` |
| 新增 | `/add` | 审核 | `/audit` |
| 更新 | `/update` | 导出 / 导入 | `/export` `/import` |
| 清空 | `/clear` | 分配关系 | `/assign-{目标}` |
| 当前调用者自身 | `/self` | 子资源集合 | `/{子资源}` |
| 第三方回调 | `/callback-{事件}` | — | — |

- 【强制】**禁止新增同义后缀**：`/get` → `/detail`、`/query` → `/page` 或 `/list`、`/edit` → `/update`、`/info` → `/self`。
- 【说明】本表是**推荐词汇，不是穷举**：业务需要新后缀时，按"动作 + 目标"的形态命名并登记入本表，**禁止各写各的同义词**。
- 【说明】动词后缀是"统一 POST"的连带产物（放弃 HTTP 动词后，动作只能落到路径或参数）。支付宝开放平台同样放弃 HTTP 动词，却把动作放在 `method` 参数里——**"动作放哪"本身没有公开规范来源**。本项目选择放路径以保持自解释。

## 1.5 查询参数命名

【强制】URL 查询字符串中的参数名与 **Java 参数名一致，使用 `camelCase`**：`?pageSize=20&withExtraInfo=false`。**禁止** `snake_case` 与 kebab-case。

【说明】**不采用** Zalando #130（`{MUST} use snake_case (never camelCase) for query parameters`）。理由：本项目是 **Java 单一栈**，`camelCase` 使"URL 参数名 = Java 参数名"，**零额外映射**；改用 `snake_case` 则每个多词参数都要写 `@RequestParam("page_size")`，且拼装 URL 时还要再记一套风格。国内主流开放 API 同样与自家 SDK 命名对齐而非用 `snake_case`（阿里云 `PascalCase`，如 `PageSize`；部分产品为 `camelCase`）。

【说明】本节**只约束 URL 查询字符串**；**JSON 请求体字段**的命名见 `spring-boot.md` §四。

---

# 二、请求与响应形态

- 【强制】入参使用专用 Request DTO，**禁止**用数据库对象接收请求。
- 【强制】出参使用 Response DTO，**禁止**直接返回数据库对象或领域实体。
- 【禁止】用 `Map`、`HttpServletRequest` 或数据库对象**承载业务入参**——三者都无字段契约，会同时废掉 Bean Validation 与接口契约（§八）。业务字段按下方「参数形态」由专用 DTO 或 `@RequestParam` 显式承载。
- 【说明】`HttpServletRequest` 作为**辅助参数**（取 IP / UA / Header 等请求元信息）在边界层**是允许的**，且比 `RequestContextHolder` 更显式；框架的 `ClientInfoUtil.getClientInfo(request)` 即按此设计。但**不要逐个调用收 request 的重载**（每次都会重复解析 UA）。
- 【强制】`HttpServletRequest` 等 Web 容器类型**禁止进入 Service / 数据访问 / 领域层**（见 `spring-boot.md` §3.3），也**禁止**传入异步任务——请求结束后容器会回收请求对象，访问将抛 `IllegalStateException`（比 `RequestContextHolder` 取不到更危险）。
- 【强制】需要往下传的信息必须在**同步阶段抽成普通对象**再传（`ClientInfo` 就是这种产物）；链路追踪上下文（traceId）由链路追踪组件负责跨异步传递（见 `observability.md` §二），业务代码不得自行搬运 MDC。
- 【强制】参数形态按复杂度选择：
  - **多字段请求**（分页筛选、新增、更新、批量）：`@RequestBody` + 专用 DTO；
  - **单参数 / 少量简单参数**（按 id 查详情、删除、启用禁用）：`@RequestParam`，**不要**为单个参数新建 DTO。
- 【强制】统一响应体由框架提供，成功用 `Result.ok(data)`；**禁止**手工拼装错误响应（失败由全局异常处理统一转换）。

## 2.1 `Result` 字段语义

【强制】响应结构由框架统一，业务不得自行加壳或改字段名：

| 字段 | 出现时机 | 语义 |
| --- | --- | --- |
| `code` | 恒有 | 业务码；**`0` = 成功**（见 §4.2） |
| `data` | 成功且有数据 | 结果集；`null` 时因全局 `NON_NULL` 而不输出（见 §七） |
| `message` | 失败时 | 面向用户的提示文案 |
| `system` | 失败时 | **出错的系统**（`spring.application.name`），跨服务透传时标识错误来源 |

【禁止】直接暴露内部异常对象、数据库对象、代理对象。

## 2.2 服务间调用的结果处理

【强制】**禁止**忽略 `code` 直接取 `data`——服务间调用的失败必须以异常或显式分支继续传播，否则上游会把"失败"当成"空数据"。

| 方法 | 用途 |
| --- | --- |
| `getDataOrThrow()` | 失败即抛 `BizException`（携带完整错误信息），由上游全局异常处理器转回响应 |
| `getDataOrElse(Supplier)` | 失败时降级，返回兜底值 |
| `propagate()` | 把失败原样传播为另一泛型的 `Result`（`code` / `message` / `system` 保持不变；仅可对失败结果调用） |
| `onSuccess` / `onFailure` / `convert` | 链式处理与数据转换 |

【说明】`BizException(Result)` 会保留原始 `code` / `message`，因此跨服务的业务错误可以**穿透多层调用**后仍以原码返回给最终调用方。

---

# 三、参数校验

- 【强制】边界输入优先由 Bean Validation 完成。
- 【强制】业务规则校验由 Service 完成（用业务断言，见 §4.1）。
- 【强制】分页 `size` 与批量 `List` 入参必须声明规模上限（见 §五）。
- 【禁止】把校验写成 Controller 中的大量 `if`。
- 【禁止】只依赖前端校验。
- 【推荐】复杂、可复用的校验规则封装为自定义 Validator。

绑定方式与对应校验手段：

- `@RequestParam`：必填性由 `required` 保证（缺失直接返回 400，不进入方法体），**不要**再加 `@NotNull`；数值范围、长度、格式等业务约束才用 `@Min` / `@Max` / `@Size` / `@Pattern`（接口类标注 `@Validated` 启用方法级校验）。
- `@RequestBody`：使用 `@Valid` + DTO 字段上的 Bean Validation 注解。
- 【说明】`@NotNull` 加在 `@RequestBody` 参数上**没有校验效果**（body 缺失时由消息转换器抛异常，参数不会以 `null` 进入校验）。

---

# 四、异常与错误码

## 4.1 异常体系

```text
RuntimeException
└── BizException（业务异常，携带 ExceptionStatus）
```

- 【强制】可预期的业务失败抛业务异常，**禁止**用返回 `null` 表达业务失败。
- 【强制】业务断言使用框架提供的 `BizAssert`（如 `BizAssert.notNull(user, USER_NOT_FOUND)`），**禁止**到处 `throw new BizException`。
- 【强制】业务异常必须携带错误码枚举（实现 `ExceptionStatus`），**禁止** `new Exception("xxx")` 到处手工创建。
- 【说明】组件归属：`ExceptionStatus` / `CommonExceptionStatus` 在 `fireworks-common`；`BizException` / `BizAssert` / `Result` 在 `fireworks-api`。

## 4.2 错误码编码

- 【强制】业务码与 HTTP 状态码是**两套体系，不共享数值空间**。HTTP 状态由异常类型决定，业务码只表达业务语义；**禁止**把 `401` / `403` / `404` / `405` 等 HTTP 状态码当业务码使用。
- 【强制】成功码固定为 **`0`**；错误码用**位数**区分层级：
  - **4 位**——通用错误码（框架 `CommonExceptionStatus`），按语义分段：`1xxx` 认证授权 / `2xxx` 系统级 / `3xxx` 请求参数 / `4xxx` 幂等与重复提交；
  - **7 位**——业务服务错误码，**前 2 位为服务前缀**（每个服务固定一段，跨服务唯一性由前缀规划保证）。
- 【强制】错误码**只声明 `code` 与 `message`**，`ExceptionStatus` 刻意不提供 HTTP 状态码方法。HTTP 状态由**异常类型**决定，业务侧不得为某个错误码指定 HTTP 状态。

```java
public enum OrderExceptionStatus implements ExceptionStatus {
    STOCK_NOT_ENOUGH(2000001, "库存不足"),
    DEPENDENCY_UNAVAILABLE(2000002, "依赖服务不可用"),
    ;
}
```

【说明】这样分层的理由：业务码表达**精确原因**（客户端据此做业务分支），HTTP 状态码表达**这次调用是否被正确处理完成**（监控 / 网关 / APM 据此判定错误率）。两者分属不同层，各自演进——错误码不必为迁就 HTTP 语义做取舍，也不会出现"同一错误码在不同接口需要不同 HTTP 语义却无法表达"的困境。

【说明】失败的归因用**异常类型**表达，改造错误码更准确：依赖不可用应抛系统异常（走 5xx），而不是给业务错误码声明 503。同一个 `code` 今天因业务规则拒绝（200）、明天因依赖故障（503），是两种不同事实，不应压在同一个声明里。
- 【强制】错误码在**同一服务内唯一**，由**业务在启动时**调用框架的 `ExceptionStatus.checkCodeUnique(...)` 校验（框架不代为扫描，避免 classpath 扫描）：

```java
@PostConstruct
void checkErrorCodes() {
    ExceptionStatus.checkCodeUnique(OrderExceptionStatus.class);
}
```

- 【强制】`message` 面向用户、全中文、句式统一；禁止中英混用，禁止直接使用 HTTP 状态描述（如 `Bad Request`）。

## 4.3 全局异常处理

- 【强制】HTTP 层异常转换由框架的全局异常处理器（`fireworks-webmvc`）统一完成，业务**禁止重复实现**。
- 【禁止】Controller 到处 `try/catch` 业务异常。
- 【强制】日志记录异常堆栈，但**响应不得暴露内部堆栈**。
- 【禁止】向前端返回 SQL、数据库异常、类名、堆栈等内部实现信息。

异常与 HTTP 状态的映射（**业务异常返回 HTTP 200**，由 `code` 分流；协议 / 容器层异常才用 4xx / 5xx）：

| 异常 | HTTP | 业务码 |
| --- | --- | --- |
| `BizException` | **200** | `ExceptionStatus.getCode()` |
| 参数校验失败、缺失参数、参数类型不匹配、请求体无法解析 | 400 | `3001` |
| `IllegalArgumentException` / `IllegalStateException` / 客户端断连 / 未捕获异常 | 500 | `2001` |
| 请求方法不支持 | 405 | `3002` |
| 路由 / 资源不存在 | 404 | `1003` |
| `Content-Type` 不支持 | 415 | `3003` |
| `Accept` 协商失败 | 406 | `3004` |
| 异步请求超时 | 503 | `2002` |

【说明】容器层错误（未进入 Controller 的 401 / 403 / 404 / 405 / 429 等）由框架的 `ErrorAttributes` 装配成同样的 `Result` 结构，**容器记录的真实 HTTP 状态码原样返回**——它们本就是协议层事实，不属于业务失败。

【说明】**业务失败率不能依赖 HTTP 状态码统计**：业务失败统一返回 200，APM / 网关 / Service Mesh 的 error rate 只反映系统故障。业务失败率必须由业务码统计（日志聚合或指标埋点，按 `code` / `system` 打标签）。这实际把"系统故障率"与"业务失败率"拆成了两个互不干扰的指标，比混在一起更易定位问题。

【强制】**业务失败一律返回 200，禁止把业务规则拒绝映射成 4xx**：4xx 在本框架中只表示"请求本身不合法"（参数、方法、媒体类型、路由），一旦业务错误也占用 4xx，监控将无法区分"用户操作失败"与"调用方请求有误"。

【强制】服务间调用**不得把非 2xx 当作传输层异常抛出**：HTTP 客户端必须拦截非 2xx 并把响应体按 `Result` 反序列化返回。业务失败是 `200` + `code`，本可正常解析；但依赖侧的系统故障（4xx / 5xx）若被客户端库抛成传输层异常，`code` / `system` 就会丢失，调用方只能兜成笼统的 500，`getDataOrThrow()` / `propagate()` 的透传语义随之失效。

【强制】因此**内部服务调用必须使用带负载均衡的 Builder**（`loadBalancedRestClientBuilder` / `loadBalancedWebClientBuilder`）——框架已在这两个 Builder 上关闭"非 2xx 抛异常"；第三方接口使用普通 Builder，保持"非 2xx 抛异常"的标准行为。

---

# 五、入参规模约束

【强制】所有接口必须声明入参规模上限，**禁止**无边界接收客户端传来的 `size` 或 `List`。

【强制】上限值必须在**一处集中定义**（业务侧常量类），**禁止**在 DTO / Service 中硬编码数字。

【说明】具体数值属**业务决策**，框架不写死——不同接口的分页诉求不同（C 端列表、管理后台、报表导出），上限值由业务按接口定档。框架只要求三件事：**必须声明上限**、**必须集中定义**、**超限时的行为必须明确**。

## 5.1 批量入参的落地

- 【强制】批量类、筛选类 `List` 字段必须声明 `@Size`，**超限直接拒绝**（400）。
- 【禁止】静默丢弃部分入参（如"只取前 N 条"却不告知调用方）。

## 5.2 与其他约束的区别

【强制】**接口入参上限**与**后端批处理批次**是两类不同约束，不得混用：

| 类型 | 性质 | 行为 |
| --- | --- | --- |
| 接口入参上限 | **契约约束** | 超限**拒绝**（400） |
| 后端批处理批次 | **性能约束** | **不拒绝**，由实现内部分批 |

【禁止】把"只有 N 条"当作"可以放进一个大事务"的理由。

【说明】分页的 `size` / `current` 同样属于**契约约束**：超限即拒绝，规则见 §6.1。

---

# 六、分页契约

【说明】框架提供分页的**请求与响应契约**，但**不参与取数**——取数属 ORM / SQL 职责（Keyset 分页与索引写法见 `database.md`）。

## 6.1 请求：`PageRequest`

| 字段 | 说明 |
| --- | --- |
| `current` | 当前页，从 `1` 开始（Offset 模式使用） |
| `size` | 每页条数 |
| `cursor` | 上一页返回的游标（Cursor 模式使用） |

- 【强制】`PageRequest` **只提供字段与默认值，不承载任何校验**（上限、下限都不校验）——遵循"框架提供机制、业务决定规则"。
- 【强制】上限值在业务侧**一处集中定义**（见 §五），由分页参数的转换方法统一兜底。
- 【强制】Controller 接收 `PageRequest` 后**必须转换为业务分页查询对象再传入 Service**；**禁止**绕过该转换方法直接构造分页对象，否则会跳过约束。
- 【强制】**下限同样必须兜底**（`size ≥ 1`、`current ≥ 1`）：`size` 为 0 或负数会导致取数异常或退化为全表扫描。
- 【强制】**超限直接拒绝，禁止静默截断**：`size` 上限是公开契约的一部分，截断会让调用方误以为拿到了请求的量。拒绝必须走**参数校验链路**（HTTP 400），不得抛业务异常——业务异常返回 HTTP 200，属于"业务规则拒绝"，两者语义不同。
- 【说明】请求侧**不提供"是否统计总数"的开关**：是否执行 `COUNT` 是取数层的实现决策，不是协议契约。通用字段无法保证被任何 ORM 消费（业务忘了翻译就等于没设置），承诺了却做不到的开关比没有更糟。**Offset 模式必须给出总数**；不想为大表付出 `COUNT` 代价时，改用 Cursor 模式。

## 6.2 响应：`PageResponse`

| 字段 | Offset 模式 | Cursor 模式 |
| --- | --- | --- |
| `records` | 当前页数据 | 当前页数据 |
| `current` / `size` | 有值 | `current` 为 `null` |
| `total` | 有值（必填） | `null` |
| `hasMore` | `null`（由调用方用 `total` 推导） | 有值 |
| `nextCursor` | `null` | 有值（无下一页时为 `null`） |

- 【说明】Offset 模式**不返回 `hasMore`**：下一页由调用方用 `current × size < total` 推导。再返回一个布尔值既是冗余，也可能与 `total` 给出矛盾结论（`COUNT` 条件与列表查询条件不一致时，两者会指向相反结果）。
- 【说明】Cursor 模式没有 `total`，`hasMore` 是它**唯一的**下一页信号，由 `limit size + 1` 保证可靠——这也是它不该出现在 Offset 模式的原因：那里没有这个额外一行数据可以依赖，“本页是否装满”只能是猜测。

```java
// Offset 模式
PageResponse.ofOffset(records, current, size, total);              // 不转换类型
PageResponse.ofOffset(records, current, size, total, mapper);      // PO -> VO
PageResponse.emptyOffset(current, size);                           // 空结果

// Cursor 模式
PageResponse.ofCursor(records, size, nextCursorFunction);
PageResponse.ofCursor(records, size, mapper, nextCursorFunction);
PageResponse.emptyCursor(size);                                    // 空结果
```

- 【禁止】把 ORM 的分页对象（如 `IPage`）直接作为接口响应返回。
- 【说明】`ofOffset(...)` / `ofCursor(...)` 的 `mapper` 返回 `null` 的记录会被**静默丢弃**——不要在 mapper 中用"返回 null"表达业务过滤，过滤应在取数层完成。代价是当页条数可能少于 `size`（不影响 `total` 与 `hasMore` 的取值），客户端若按"本页条数"反推分页会得到错误结论。

## 6.3 游标模式的实现契约

- 【强制】游标模式下取数 SQL 必须 `LIMIT size + 1`：框架以"返回条数 > size"判断 `hasMore` 并截断多余一条。**若不 `+1`，`hasMore` 会恒为 `false`**，调用方永远拿不到下一页。
- 【强制】`nextCursorFunction` 必须从**截断后**的最后一条记录提取游标，且游标字段与 `ORDER BY` 完全一致（索引与游标配合见 `database.md` §6.5.1）。
- 【推荐】游标用 `CursorUtil.build(...)` / `parse(...)` 编码：多个字段按排序顺序传入，内部用 `_` 连接后做 URL-safe Base64；`parse` 对非法游标返回空数组而非抛异常。

## 6.4 带页脚的分页

【推荐】"明细 + 汇总"分离的场景（报表、对账）使用 `PageWithFooterResponse<T, F>`：`F` 为独立的汇总 VO，**不污染**明细 VO 的字段。

```java
PageWithFooterResponse.ofOffset(records, current, size, total, footer);           // 不转换类型
PageWithFooterResponse.ofOffset(records, current, size, total, mapper, footer);   // PO -> VO
PageWithFooterResponse.ofCursor(records, size, nextCursorFunction, footer);
PageWithFooterResponse.ofCursor(records, size, mapper, nextCursorFunction, footer);
```

---

# 七、序列化契约

## 7.1 唯一配置出处

【强制】全局 JSON 配置的**唯一出处**是框架（`fireworks-common-spring-boot-starter` 的 `CommonAutoConfiguration`）。

【禁止】业务仓库自定义全局 Jackson 配置（`ObjectMapper` Bean、`Jackson2ObjectMapperBuilderCustomizer`、`@JsonComponent` 等），避免多份配置互相覆盖。

## 7.2 全局契约

【强制】以下行为由框架统一提供，业务代码**不得逐 DTO 重复声明**：

| 项 | 行为 |
| --- | --- |
| `null` 字段 | **不输出**（`NON_NULL`）——DTO 上再写 `@JsonInclude(NON_NULL)` 属冗余 |
| 时间类型 | ISO 格式（如 `2026-08-17T08:00:00Z`），禁用时间戳输出 |
| **时区** | **不设置全局 TimeZone**——有意为之，避免给无时区类型附加时区语义 |
| `BigDecimal` / `BigInteger` | **字符串输出**（防科学计数法、防前端精度丢失） |
| `Long` | **数字输出**（不做全局转字符串，见 §7.3） |
| 未知字段 | 忽略（兼容调用方多传字段） |
| 宽松解析 | 允许单引号、无引号字段名、未转义控制字符、反斜杠转义 |
| 脱敏 | `@Sensitive` 自动生效（仅 `String` 字段） |

【说明】"不设置全局 TimeZone"（序列化行为）与"连接固定会话时区"（JDBC 行为）不冲突。

【说明】框架自身的 `Result` / `PageResponse` **不再重复声明** `@JsonInclude`——与业务 DTO 同样依赖上述全局配置，保持唯一出处。

## 7.3 自增 ID 的对外暴露

【强制】对外标识的选型规则：

| 场景 | 是否可对外返回自增 ID | 理由 |
| --- | --- | --- |
| C 端业务实体（用户、订单、交易等） | ❌ 使用业务编号（`xxx_no`） | 防枚举（连续 ID 泄露业务量）+ 规避前端大数精度问题 |
| B 端管理接口 | ✅ 可返回 `id` | 使用者为内部人员，数据量可控 |
| 字典 / 配置类小表 | ✅ 可返回 `id` | 量级极小 |
| 树形结构父子 ID | ⚠️ B 端可用；C 端需转业务编号 | 同上 |

【说明】该策略同时消除两个问题：**防枚举**与**前端精度**（超过 `2^53-1` 的数值会精度丢失——不返回大 ID 即从源头规避）。

【说明】**不引入"`Long` 全局转字符串"的兜底机制**：全局转换会影响 `total` / `size` / `code` 等小数值字段并改变 API 契约，收益不足。若确需对外返回大 ID，单独评估并走 ADR。

> 数据库视角的标识选型（有序编号 vs 不可枚举标识、路径鉴权强度判据）见 `database.md` §3.4.2。

## 7.4 其他约束

- 【强制】禁止单个 DTO 私自覆盖全局时间格式，除非有明确兼容需求并在评审中说明。
- 【强制】禁止在 DTO / Entity 上重复声明全局性序列化注解。
- 【推荐】枚举对外返回稳定的业务编码 `code`（`@JsonValue`），**禁止**返回 `ordinal`。
- 【禁止】直接暴露内部异常对象、数据库对象、代理对象。
- 【禁止】核心业务查询返回不透明的 `Map<String, Object>`；优先定义明确的 DTO / Projection。

---

# 八、接口文档

- 【推荐】使用 OpenAPI / Swagger 维护接口契约。
- 【强制】接口字段必须有明确说明。
- 【强制】枚举必须在接口文档中列出**完整值域**（每个 `code` 的含义），且与枚举定义一致（见 `java.md` §二）。
- 【推荐】错误码必须有明确文档。

---

# 九、幂等（红线）

> 完整规范见 `idempotency.md`；补偿与补偿竞态见 `reliability.md` 与 `../decisions/0004-compensation-idempotency-and-race.md`。

【强制】以下**类型**的写操作必须评估幂等（具体业务场景清单由各业务仓库列明）：

| 类型 | 风险 |
| --- | --- |
| **创建类** | 同一意图重复提交产生多条业务数据 |
| **支付 / 资金类** | 重复回调或重复请求导致重复扣款、重复记账 |
| **回调类** | 第三方回调、上传确认等入口天生可能重放 |
| **消息消费** | MQ / 事件消费的重复投递 |
| **定时任务** | 多实例并发、补偿重跑产生重复副作用 |

- 【禁止】在业务服务重复实现防重放（校验 nonce、重算签名）——入口层已负责；但**入口层防重放不能替代业务幂等**（连点 / 重试会产生不同报文）。
- 【强制】网络重试不能导致重复业务结果；落地顺序为**业务唯一键 → 幂等表 → 数据库唯一约束兜底**。
- 【禁止】"先查后插"而无唯一约束。
- 【禁止】把"服务端签发幂等 Token"包装成安全机制。
- 【禁止】同步同事务场景下，在幂等占位后 `catch` 业务异常并正常返回。

---

# 十、版本演进

> 决策与权衡见 `../decisions/0005-api-versioning-strategy.md`。

## 10.1 基本原则

- 【强制】优先**原地兼容演进**；仅在**无法兼容**时才升版本。
- 【说明】引入版本段（§1.2）**不等于"每次改动都升版本"**——升版本意味着同一份业务逻辑维护两套，是最后手段。

## 10.2 兼容性判定

【强制】按"**调用方不改代码能否继续工作**"判定：

| 兼容（不升版本） | 破坏（需过渡或升版本） |
| --- | --- |
| 新增可选请求字段 | 重命名字段、删除字段 |
| 新增响应字段 | 修改字段类型、修改字段语义 |
| 新增接口 | 收紧校验（新增必填、长度变严） |
| 放宽校验 | 删除接口、修改路径 |
| 新增枚举值（用预留间隔位） | 删除枚举值 |
| 修改注释 / 文案 | 修改响应结构层级 |

## 10.3 优先原地演进

【推荐】破坏性变更优先用**兼容方式**实现：

| 需求 | 兼容做法 |
| --- | --- |
| 删除响应字段 | 先标记废弃，观察期后删除 |
| 重命名字段 | 新增新字段 + 保留旧字段（双写过渡） |
| 修改字段类型 | 新增新字段（如 `id` → `idStr`），旧字段保留过渡 |
| 收窄枚举语义 | 新增枚举值，或新增字段表达新语义 |

## 10.4 废弃流程与观察期

【强制】三段式：① 标记废弃（注明替代方案与计划移除时机）→ ② 观察期确认无调用方 → ③ 删除。

【禁止】跳过 ①② 直接删除已发布的字段或接口。

【强制】观察期以**绝对时间**衡量，**不得以"发布版本数"计**——存在无法强制升级的客户端（如移动端应用商店不允许强制升级）。

【强制】观察期结束需**同时满足**：达到时间下限 **且** 确认无调用方（依赖日志 / 监控能力，见 `observability.md`）。

【强制】时间下限**由各业务按客户端升级能力显式定档**并写入业务仓库规范；框架只要求"以绝对时间计"与"需确认无调用方"两项。

【说明】该规则**自接口首次对外发布起生效**；未上线阶段的调整不受约束。

## 10.5 何时升版本

- 【强制】需**同时满足**：确实破坏性、无法用兼容方式实现、已评估调用方改造量。
- 【强制】新旧版本**并存**：新版本承载新逻辑，旧版本冻结（只修缺陷）并进入废弃期。
- 【禁止】修改已发布接口的路径，或原地改写版本段（把 `/v1` 直接改成 `/v2` 而不保留旧路径）。
- 【说明】版本号常量只为当前需要预留，不预建更多模板。

## 10.6 内部接口：协同改造

- 【强制】内部接口**不通过 URL 版本号演进**，采用**协同改造**：修改共享契约模块时**必须同步修改所有调用方**；变更前**全仓库搜索调用方**；同一批次发布，**禁止只改一半**。
- 【强制】灰度 / 滚动发布期间确认兼容性；不确定时走过渡：`新增接口 → 保留旧接口 → 调用方切换 → 删除旧接口`。
- 【说明】内部接口调用方**可控**，故不需长期并存多版本；但"可控"不等于"可以单方面破坏"，因此需要协同。

## 10.7 枚举与响应结构的演进

- 【推荐】新增枚举项使用**预留间隔位**，保证已有 `code` 不变（见 `../decisions/0001-enum-stored-as-smallint.md`）。
- 【强制】调用方遇到**未知枚举值必须降级展示**，禁止崩溃。
- 【禁止】删除或复用已发布的枚举 `code`。
- 【强制】新增响应字段必须**可选**，不得导致旧调用方解析失败（框架已忽略未知字段，这是兼容的基础）。

---

# 十一、Checklist

- [ ] 是否统一 POST？路径是否走路径常量？
- [ ] 路径是否为 kebab-case + **复数**资源名（未用单数、驼峰、下划线）？无尾斜杠与重复斜杠？
- [ ] 入参 / 出参是否为专用 DTO（未用数据库对象、未用 `Map`、未用 `HttpServletRequest` 承载业务字段）？
- [ ] `HttpServletRequest` 是否只出现在边界层（未进入 Service / 数据访问层，未传入异步任务）？
- [ ] 服务间调用是否处理了 `Result.code`（未直接取 `data`）？
- [ ] 校验是否分层（Bean Validation 管边界、Service 管业务）？
- [ ] 错误码是否符合位数分段与唯一性要求？是否在启动时校验了唯一性？`message` 是否面向用户？
- [ ] 是否依赖框架全局异常处理（未重复实现）？
- [ ] 分页 / 批量是否声明了规模上限？上限值是否集中定义？分页下限是否兜底？
- [ ] 游标分页是否 `LIMIT size + 1`？游标字段是否与 `ORDER BY` 一致？
- [ ] 是否复用了框架的序列化契约（未重复声明）？
- [ ] C 端是否暴露了自增 ID？
- [ ] 是否需要幂等？是否遵循 `idempotency.md`？
- [ ] 本次改动是兼容还是破坏性变更？破坏性变更是否优先采用兼容做法？
- [ ] 删除字段 / 接口是否走了三段式？观察期是否以绝对时间计并确认无调用方？
- [ ] 修改内部契约时是否排查并同步了**所有**调用方？
- [ ] 是否删除了已发布的枚举 `code`？
