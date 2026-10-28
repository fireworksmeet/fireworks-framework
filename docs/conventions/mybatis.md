# MyBatis / MyBatis-Plus 使用约定

> 定位：选用 MyBatis / MyBatis-Plus 时的 SQL 书写、Mapper 注册与 MyBatis-Plus 特性使用约定。
> 适用性：**仅在选用 MyBatis / MyBatis-Plus 时适用**。框架不绑定 ORM，通用规则见 `database.md`（表与 SQL 设计）、`api.md` §六（分页契约）、`spring-boot.md`（分层、事务与对象转换）。
>
> 规范级别：【强制】违反即视为代码问题；【推荐】默认遵守；【禁止】原则上不得使用；【说明】背景解释。

---

# 一、SQL 编写

- 【强制】复杂 SQL 必须写清楚业务语义，写在 XML 中。
- 【强制】自定义 SQL 统一写在 XML，**禁止**在 Mapper 接口上使用 `@Select` / `@Insert` / `@Update` / `@Delete` 注解。
- 【禁止】使用 `${}` 接收用户输入（SQL 注入）。
- 【强制】用户输入必须使用 `#{}` 参数绑定。
- 【说明】`${}` 仅允许用于**经过白名单校验、无法参数化**的结构性内容（如排序列），且必须严格限制候选值：

```xml
<!-- 禁止：ORDER BY ${sortField} -->
<!-- 正确：sortField 只允许 id / created_at / updated_at，再映射到固定 SQL 片段 -->
```

- 【禁止】为"快速开发"大量依赖条件构造器（Wrapper）拼接超复杂 SQL。
- 【推荐】简单条件可用条件构造器；复杂查询使用 XML SQL。

> SQL 书写风格（关键字大写、各子句换行、显式 `INNER JOIN`、字段下划线）见 `database.md` §6.7。

---

# 二、Mapper 注册

- 【强制】Mapper 接口**不标注** `@Mapper` 注解，由 `@MapperScan` **在一处集中注册**。
  【说明】集中注册才能保证扫描范围可审计；分散标注会让"哪些接口会被注册"难以判断。
- 【说明】框架自身的 starter **不注册 Mapper**（框架自用表走 Spring JDBC）；Mapper 扫描由业务工程自己声明。
- 【强制】数据库列与 Java 属性必须保持明确映射（列名变化同步修改映射），不依赖不稳定的自动推导。

---

# 三、MyBatis-Plus 约定

- 【强制】**禁止**使用 `@TableLogic` 自动逻辑删除：其 0/1 语义与 `deleted_at`（`NULL` / 删除时刻）不兼容，且隐式注入的条件在评审中不可见（见 `../decisions/0003-soft-delete-and-unique-constraint.md`）。
- 【说明】`updateById(entity)` 的字段策略为 `NOT_NULL`：只更新实体中**非 null 字段**，并非全量更新。代价是更新列由"字段是否为 null"隐式决定，且**无法把字段置为 null**。
- 【推荐】更新列需要显式控制（指定更新列、置 null、复杂条件）时，使用条件构造器（`LambdaUpdateWrapper` / `update(entity, wrapper)`）。
- 【说明】引入 `fireworks-mybatis-plus-spring-boot-starter` 后，**分页拦截器与审计字段自动填充已由框架装配**，默认不要再声明 `MybatisPlusInterceptor` 或 `MetaObjectHandler`；要追加插件走 `MybatisPlusCustomizer`。
- 【说明】声明自己的 `MybatisPlusInterceptor` 时框架会**让位**（`@ConditionalOnMissingBean`），不是"覆盖"——这是自动识别方言失效（如手工 `jdbc:p6spy:` 前缀）时的逃生口；此时必须自己装配 `PaginationInnerInterceptor` 并设置 `max-limit`。
- 【说明】`MetaObjectHandler` 同理：自行声明后框架实现不再注册，审计字段的填充规则全部由你负责。

---

# 四、分页结果的转换

- 【强制】不要把 ORM 的分页对象直接作为接口响应返回。
- 【推荐】引入 `fireworks-mybatis-plus-spring-boot-starter` 时，用 `PageAdapters.offset(IPage[, mapper])` 转成框架统一分页响应；游标分页**不经过 `IPage`**：多查一条（`size + 1`，关闭 count）后直接 `PageResponse.ofCursor(rows, size, nextCursorFunction)`。
- 【说明】使用其他 ORM 时直接调用 `PageResponse.ofOffset(records, current, size, total[, mapper])` 或 `PageResponse.ofCursor(...)`（契约见 `api.md` §6.2）。
