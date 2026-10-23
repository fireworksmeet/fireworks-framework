# fireworks-mybatis-plus-spring-boot-starter

**框架与 MyBatis-Plus 的粘合层（可选）**。

> 它**不是**官方 `mybatis-plus-spring-boot3-starter` 的再包装——官方已经做得足够好。
> 它只补官方不做、而每个项目都要做的事，并把「只服务 MyBatis-Plus 用户」的便利集中到这里，
> 从而让 `fireworks-api` / `fireworks-webmvc` 保持与 ORM 无关。

## 它做了什么

| 能力 | 说明 |
| --- | --- |
| **分页拦截器自动装配** | 官方 starter **不会**自动注册 `MybatisPlusInterceptor`（最常见的踩坑点）。本模块注册它并设置单页上限；方言默认由 MyBatis-Plus 按连接 URL 识别，可用 `pagination.db-type` 覆盖；同时带上自 MP 3.5.9 起被拆出的 `mybatis-plus-jsqlparser`（少了它分页**静默失效**） |
| **审计字段自动填充** | 注册 `AuditMetaObjectHandler`，让 `createdAt` / `updatedAt`（以及可选的 `createdBy` / `updatedBy`）免手写 |
| **分页适配** | `PageAdapters.offset(IPage)` → Offset 响应；`cursorPage(size)` + `PageAdapters.cursor(IPage, cursorFn)` → Cursor 响应 |
| **排序组件** | `AbstractSortRegistry` / `SortBuilder`：把「业务排序枚举」映射为 `OrderItem`，可直接应用到 `IPage` / `Wrapper`，也可导出 `ORDER BY` SQL 片段 |
| **实体基类** | `BaseEntity`（主键 + 时间）、`BaseOperatorEntity`（+ 审计人）；**流水/日志表不要继承**，见 §实体基类 |
| **扩展点** | `MybatisPlusCustomizer`：在框架装配的分页之上**追加**其它插件，不会覆盖分页 |
| **当前登录人接入** | 业务声明一个 `CurrentUserProvider` Bean 即可；框架不绑定任何鉴权体系，也不会自动注册 |
| **SQL 日志（可选）** | 接入 `p6spy-spring-boot-starter`（optional 依赖）：业务声明依赖即启用，不改 JDBC URL、不换驱动；开关用 `decorator.datasource.p6spy.enable-logging` |

## 它**不**做什么（很重要）

- ❌ **不内置乐观锁、防全表更新/删除**等插件——用 `MybatisPlusCustomizer` 按需追加（见下）。
  这两类插件要么与显式状态 CAS 重复（乐观锁本质上就是「version 列上的 CAS」，且要求先查后改、
  对手写 SQL 不生效），要么有误伤成本（防全表更新只拦「没有 where」的语句，且每条 DML 都要过 SQL 解析）。
- ❌ **不提供发号器**：框架自带 `fireworks-id`，自行声明 `IdentifierGenerator` Bean 即可，MP 会自动接管。
  但要注意：`BaseEntity` / `BaseOperatorEntity` 上写死了 `@TableId(type = IdType.AUTO)`（数据库自增），
  **继承基类的实体不会走发号器**；需要 CosId 发号时，不要继承基类，或在自己的实体上重新声明 `id` 字段。
- ❌ **不提供 `@MapperScan` 封装**：各服务扫描包不同，自行在启动类标注即可。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.yzm.fireworks</groupId>
    <artifactId>fireworks-mybatis-plus-spring-boot-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

引入后**不要**再单独引 `mybatis-plus-spring-boot3-starter`（本模块已带）。

### 2. 配置（均有默认值）

```yaml
fireworks:
  mybatis-plus:
    pagination:
      max-limit: 500        # 单页最大条数；<=0 或留空表示不限制
      db-type: postgresql   # 可选：仅在自动识别失效时配置，详见 §数据库支持
```

### 3. 用分页

**Offset 模式**（需要总数）：

```java
IPage<User> page = userMapper.selectPage(new Page<>(current, size), wrapper);

return Result.ok(PageAdapters.offset(page, UserVO::from));
```

**Cursor 模式**（瀑布流、导出、深分页；不做 count）：

```java
// 1. cursorPage(size) 内部查 size + 1 条并关闭 count
IPage<User> page = userMapper.selectPage(PageAdapters.cursorPage(size),
        Wrappers.<User>lambdaQuery()
                .lt(User::getCreatedAt, lastCreatedAt)      // 由上一页 nextCursor 解析而来
                .orderByDesc(User::getCreatedAt).orderByDesc(User::getId));

// 2. 转成游标响应：页大小从 page 反推，hasMore / nextCursor 自动得出
return Result.ok(PageAdapters.cursor(page, UserVO::from,
        user -> CursorUtil.build(user.getCreatedAt(), user.getId())));
```

两个必须遵守的前提：

1. **排序必须稳定且唯一**：游标是按「上一页最后一条」续读的，排序里不追加 `id` 会导致相同时间戳的记录漏读或重读；
2. **必须用 `PageAdapters.cursorPage(size)` 构造分页参数**：它负责多查一条并关闭 count，`cursor(...)` 再从 `page.getSize() - 1` 还原请求的页大小。自己 `new Page<>(1, size, false)` 会让 `hasMore` 判断失真。

### 4. 用实体基类

```java
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user")
public class User extends BaseOperatorEntity {
    private String username;
}
```

### 5. 用排序组件

前端只传排序枚举，排哪些列、什么方向由服务端注册——**列名不来自请求参数**，因此不存在排序字段注入。

```java
public enum SortType {
    LATEST, HOT
}

@Component
public class DefaultSortRegistry extends AbstractSortRegistry<SortType> {

    public DefaultSortRegistry() {
        super(SortType.class);
    }

    @Override
    protected void register() {
        // 方法引用：编译期可校验，自动转下划线列名（create_time）
        register(SortType.LATEST, SortBuilder.builder()
                .desc(User::getCreateTime)
                .desc(User::getId)
                .build());
        // 字符串列名：手写 SQL 列 / 聚合列
        register(SortType.HOT, SortBuilder.builder()
                .desc("view_count")
                .build());
    }
}
```

应用：

```java
// 分页
IPage<User> page = new Page<>(current, size);
sortRegistry.apply(SortType.LATEST, page);

// 条件查询（QueryWrapper / LambdaQueryWrapper 均可）
sortRegistry.apply(SortType.LATEST, wrapper);

// 手写 SQL（游标分页、联表查询等）
String orderBy = sortRegistry.getOrderBySql(SortType.LATEST);   // "create_time DESC, id DESC"
```

> `apply(...)` 是**追加**语义：不清除 page / wrapper 上已有的排序项。同一查询只调用一次即可；
> 需要替换时先 `page.orders().clear()`。wrapper 没有"只清空 orderBy"的 API，所以一律追加。

## 审计字段填充规则

只有**声明了 `@TableField(fill = ...)`** 的字段会被填充（两个基类均已声明）。

| 字段 | 插入 | 更新 | 说明 |
| --- | --- | --- | --- |
| `createdAt` / `updatedAt` | ✅ | ✅（`updatedAt`） | **总是填充**，无需登录上下文 |
| `createdBy` / `updatedBy` | ✅ | ✅（`updatedBy`） | **仅当能解析到当前登录人时填充** |

> **为什么审计人是「能解析才填」而不是写 `null` 占位**：定时任务、MQ 消费等场景没有登录上下文，
> 强行写占位值会让审计信息**静默出错**。此时字段保持 `null`，需要审计请在业务代码里显式赋值。

接入登录人：**业务声明一个 Bean 即可**，框架不绑定任何鉴权体系，也不提供默认实现。

```java
// 用了 fireworks-security：
@Bean
public CurrentUserProvider currentUserProvider() {
    return SecurityUserContext::getUserId;
}

// 用自己的鉴权体系：
@Bean
public CurrentUserProvider currentUserProvider() {
    return () -> MyAuthContext.getUserId();
}
```

不声明时审计人字段保持 `null`（不是写占位值），详见上面的「为什么审计人是能解析才填」。

## 实体基类：什么时候**不该**继承

`BaseEntity` / `BaseOperatorEntity` 面向**会变的业务表**（主键 + 创建时间 + 更新时间，可能带操作人）。它们不是“所有表都必须继承”的模板：

| 表类型 | 建议 |
| --- | --- |
| 普通业务表 | 继承 `BaseEntity` 或 `BaseOperatorEntity` |
| 流水 / 日志 / 事件表（只插入、不更新） | **不要继承**，自己声明 `id` + `createdAt` |
| 分区表、带业务时间与来源系统的表 | 自己声明字段；基类覆盖不了这些差异 |

原因：`BaseEntity` 的 `updatedAt` 在追加型表里永远等于 `createdAt`——这一列既误导读者（暗示可更新），又白占空间。

不继承**不影响**自动填充：`AuditMetaObjectHandler` 只认字段上的 `@TableField(fill = ...)`，与是否继承基类无关。所以流水表这样写就可以：

```java
@TableName("login_log")
public class LoginLog {

    @TableId
    private Long id;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;
}
```

> 为什么不提供 `BaseImmutableEntity`：基类会演变成「有无更新时间 × 有无操作人」的组合爆炸，而这类表数量少、差异大；让业务自己声明两三个字段，比多一层继承更清楚。

## 审计字段为什么只有 id，不存姓名

`createdBy` / `updatedBy` 存的是**用户 id**，不冗余姓名。姓名不是稳定标识：用户会改名、离职、注销，存快照会让同一列里新旧名字混杂，想统一更正就得刷历史数据；而且姓名属于个人信息，存得越多，脱敏与合规的处理面越大。

需要「按人名展示或筛选」时，按查询性质选方案：

| 场景 | 做法 |
| --- | --- |
| 列表展示创建人姓名 | 不冗余：拿本页的 `created_by` 去用户服务**批量**换姓名（或读本地缓存）；N+1 用批量解决，不用冗余解决 |
| 按姓名筛选，低频 / 后台运维 | 两步查询：用户服务按名字搜出 id 集合，再 `IN` 回查；限制返回规模，避免模糊匹配出上万条 |
| 按姓名筛选，高频 / 核心列表 | 建读模型（宽表或 ES）用事件同步用户信息；或冗余 `created_by_name` 列并接受不一致 |
| 订单 / 合同 / 审批等凭证 | 存**快照**（姓名、地址、商品名），且不做回填修正——历史就是历史 |

跨服务时最需要警惕的是「模糊查询 + 跨服务 + 分页」三者同时出现：按名字搜出 N 个 id 再 `IN` 回查，分页与排序都会失真。这类需求要么做成读模型，要么限定为导出 / 后台功能，不要放进主列表。

姓名是否冗余是**表的建模决策**，不同表结论不同，因此框架不把它放进基类：需要快照的业务自己加列、自己填充。

## 追加其它插件（乐观锁 / 防全表更新 / 多租户 …）

```java
@Bean
public MybatisPlusCustomizer optimisticLockerCustomizer() {
    return interceptor -> interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
}
```

用扩展点而**不是**自己声明 `MybatisPlusInterceptor` Bean 的好处：不会覆盖框架装配的分页拦截器。
（若确实需要完全接管，声明自己的 `MybatisPlusInterceptor` Bean 即可，框架自动让位——但那时要自己保证分页已配置。）

## 用 CosId 发号（可选，无需框架支持）

MP 会自动接管容器里的 `IdentifierGenerator`：

```java
@Bean
public IdentifierGenerator cosIdIdentifierGenerator() {
    return entity -> IdUtil.getShareId();   // 依赖 fireworks-id
}
```

## SQL 日志（p6spy，可选依赖）

框架**不自己包装数据源**——代理 `DataSource` 的坑很多（连接池具体类型被替换、`unwrap` 失效、初始化顺序、与动态数据源的兼容等），直接接入社区维护的 starter。

本模块 pom 里该依赖是 `optional`：不引入就没有任何代理与运行时开销。需要时在业务工程声明：

```xml
<dependency>
    <groupId>com.github.gavlyukovskiy</groupId>
    <artifactId>p6spy-spring-boot-starter</artifactId>
</dependency>
```

**不用改 JDBC URL，也不用换驱动**，starter 会自动装饰数据源。引入后默认就打印 SQL，关闭：

```yaml
decorator:
  datasource:
    p6spy:
      enable-logging: false     # 关闭 p6spy 日志
      # logging: slf4j          # SYSOUT / SLF4J / FILE / CUSTOM
      # multiline: false        # 单行还是多行
      # exclude-categories: ... # 过滤 resultset 等类别
```

`decorator.datasource.enabled: false` 可以关掉该 starter 的全部装饰器（不止 p6spy）。

两个必须知道的代价：

1. **参数是明文**：p6spy 打印完整参数值（手机号、token 都可能出现）。生产要么关闭，要么在采集侧脱敏。
2. **有性能开销**：代理了每个 `Connection` / `PreparedStatement`，压测能看到额外耗时，建议只在开发/测试开启。

> 只用 MyBatis、且只想要「SQL + 参数」不想引入代理时，也可以开 MyBatis 自己的日志：`mybatis-plus.configuration.log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl` 并调 `logging.level.<mapper 包>=debug`（没有耗时统计，也不覆盖 JdbcTemplate）。

## 数据库支持

**默认不配置方言**：MyBatis-Plus 在查询时按当前连接的 URL 识别（`PaginationInnerInterceptor#findIDialect`），支持哪些库完全由 MP 决定。

| 场景 | 做法 |
| --- | --- |
| 单库（推荐） | 什么都不配，交给 MP 自动识别 |
| 多数据源 / 读写分离 | **保持为空**：每个连接各自识别才正确，全局 `db-type` 会把所有连接强行按一种方言生成 SQL |
| 自动识别失效（如手工用 `jdbc:p6spy:` 前缀、分库分表中间件改写 URL） | 配 `db-type` 显式覆盖，或自行声明 `MybatisPlusInterceptor` |

```yaml
fireworks:
  mybatis-plus:
    pagination:
      db-type: postgresql   # 仅在自动识别失效时配置
```

取值范围是 MyBatis-Plus `DbType` 的 db 名称（大小写不敏感），全部 50 项已写入配置元数据，IDE 里输入 `db-type:` 会有下拉提示与说明；配了无法识别的值会**启动期失败**：

```
[MyBatis-Plus] 不支持的分页方言：fireworks.mybatis-plus.pagination.db-type=postgre；
取值见 com.baomidou.mybatisplus.annotation.DbType（如 postgresql / mysql / oracle / sqlserver / dm / kingbase），留空则自动识别
```

需要完全接管时，声明自己的拦截器即可（框架装配带 `@ConditionalOnMissingBean`，会让位；记得自己设置 `max-limit`）：

```java
@Bean
public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
    return interceptor;
}
```

## 核心类速览

| 类 | 说明 |
| --- | --- |
| `FireworksMybatisPlusAutoConfiguration` | 分页拦截器 + 审计填充 |
| `MybatisPlusProperties` | `fireworks.mybatis-plus.*` 配置 |
| `MybatisPlusCustomizer` | 追加自定义 inner interceptor 的扩展点 |
| `AuditMetaObjectHandler` | 审计字段填充实现 |
| `CurrentUserProvider` | 当前登录人来源扩展点（业务声明 Bean，框架不提供实现） |
| `PageAdapters` | `IPage` → `PageResponse` 适配（offset / cursor 两种模式） |
| `UniqueKeyUtil` | 唯一键冲突 → 业务异常（`throwOnConflict`） |
| `AbstractSortRegistry` / `SortBuilder` | 排序枚举 → `OrderItem` / `ORDER BY` |
| `BaseEntity` / `BaseOperatorEntity` | 实体基类 |

## 注意事项

- 本模块**可选**：不用 MyBatis-Plus 的服务不要引入，框架其余部分与 ORM 无关。
- 业务自行声明 `MetaObjectHandler` 后，框架实现**不再注册**（`@ConditionalOnMissingBean`），审计填充规则随之全部由业务负责。
- 分页 `maxLimit` 建议保留上限，避免深分页 / 超大页拖垮数据库。
- **不要关闭 `searchCount`**：MP 在 `searchCount=false` 时 `total` 恒为 0，客户端会误判为「无数据」。Offset 模式的下一页由 `total` 推导，需要省掉 `COUNT` 时请改用 Cursor 模式。
