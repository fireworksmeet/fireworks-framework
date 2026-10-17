# Java 语言与代码风格

> 定位：Java 语言层面的写法与风格——状态机与业务状态、枚举、Lombok、Optional、集合与 Stream、常量、注释、格式与静态检查。
> 边界：分层、依赖方向、命名、事务、异常见 `spring-boot.md`；API 契约与序列化见 `api.md`；数据库列与枚举取值规则见 `database.md`；数据访问与对象转换见 `persistence.md`。
>
> 规范级别：【强制】违反即视为代码问题；【推荐】默认遵守；【禁止】原则上不得使用；【说明】背景解释。

---

# 一、状态机与业务状态

- 【强制】多状态字段统一使用数据库规范定义的 `status SMALLINT`（见 `database.md` §3.10）。
- 【强制】Java 中用**枚举**表达状态，不允许在业务代码中散落 `status == 1`、`status == 2` 魔法数字。
- 【强制】状态迁移必须明确**合法路径**——只允许从哪些状态迁移到哪些状态，必须可读、可测试。
- 【禁止】直接通过 `setStatus(newStatus)` 绕过状态机规则：

```java
order.transitionTo(OrderStatus.PAID);   // 推荐
order.setStatus(2);                     // 禁止
```

- 【强制】识别"伪二值"：具有流程 / 生命周期语义的字段即使当下只有两个值，也使用 `status` + 枚举而非布尔（判据见 `database.md` §3.9.1）。
- 【推荐】状态迁移方法放在**领域对象上**，而不是散落在 Service 中"读 → 判断 → 写"——后者在多处调用时无法保证规则一致。

---

# 二、Java 枚举

- 【强制】业务枚举实现框架的取值契约 `CodeEnum<V>`（`fireworks-api`）：`getCode()` 表达业务编码，`getLabel()` 表达展示文案。
- 【推荐】需要暴露为前端字典项（下拉框、筛选条件）的枚举，额外标注 `@ExposedEnum`——**暴露是横切关注点，用注解表达**，不通过"实现了某个接口"作出隐式承诺（内部流转状态不应出现在前端字典里）。
- 【禁止】枚举实现任何 **ORM 专有接口**（如 MyBatis-Plus 的 `IEnum`）——框架不绑定 ORM，业务枚举同样不应绑定。
- 【强制】必须显式声明业务编码字段 `code`；**禁止**依赖 `ordinal()`。
- 【强制】必须提供展示文案（实现 `getLabel()`），字段名统一 `label`，供接口文档与前端字典项使用。
- 【说明】`@JsonValue` **同时作用于序列化与反序列化**（Jackson 2.6+）：标注后 Jackson 会自动按 `code` 匹配枚举，**不需要**再为每个枚举写 `@JsonCreator`。注意标注 `@JsonValue` 后反序列化**只接受 `code`**，传枚举名称（如 `"NORMAL"`）会失败。
- 【推荐】仅当需要"未知值兜底为默认枚举"或"抛业务异常而非 400"时，才额外添加 `@JsonCreator(mode = DELEGATING)` + `EnumUtil.match(...)`，把未知值的行为显式化。
- 【说明】未自定义时，非法枚举值抛 `InvalidFormatException`，由全局异常处理器统一转为 `400` + `REQUEST_PARAMS_ERROR`。
- 【强制】取值规则（起始值、预留间隔、禁止改语义 / 复用）见 `database.md` §3.10，本文不重复。
- 【说明】对外序列化方向（返回 `code` 而非名称 / `ordinal`）见 `api.md` §7.4。
- 【说明】`@ExposedEnum` 的扫描默认以启动类所在包为范围（需 `fireworks.exposed-enum.enabled=true` 开启），跨包时用 `scan-packages` 覆盖。框架只做发现与注册，不提供字典的增删改、缓存与国际化能力。

```java
@Getter
@AllArgsConstructor
@Schema(description = "用户状态 (10:禁用, 20:正常, 30:锁定, 40:注销)")
public enum UserStatus implements CodeEnum<Integer> {

    DISABLED(10, "禁用"),
    NORMAL(20, "正常"),
    LOCKED(30, "锁定"),
    CANCELLED(40, "注销"),
    ;

    @JsonValue
    private final Integer code;
    private final String label;
}
```

- 【强制】接口文档（`@Schema`）中的值域描述必须与实际 `code` **一一对应**，禁止文档与代码不一致。

---

# 三、Lombok

- 【推荐】可以使用 Lombok 减少样板代码。
- 【强制】DTO（Request / Response）按语义选择 `@Getter` / `@Setter` 或 `record`，而不是无脑 `@Data`。
- 【禁止】Entity / DO 无脑使用 `@Data`——`equals` / `hashCode` / `toString` 会带来隐藏问题（集合去重、日志打印泄露字段、懒加载触发查询）。
- 【推荐】Service 使用 `@RequiredArgsConstructor`（与构造器注入一致，见 `spring-boot.md`）。
- 【禁止】在有明确**不可变语义**的对象上使用 `@Setter`。

---

# 四、Optional

- 【推荐】`Optional` 用于表达"可能不存在"的**返回值**。
- 【禁止】Entity / DO 字段大量使用 `Optional`。
- 【禁止】Controller 的 Request 字段使用 `Optional`。
- 【禁止】把 `Optional` 当成所有 `null` 判断的替代品。
- 【禁止】`optional.get()` 前不判断存在性。

---

# 五、集合与 Stream

- 【推荐】简单遍历使用普通 `for`，不要为了 Stream 而 Stream。
- 【禁止】在 Stream 中执行数据库写操作、远程调用等**副作用**操作。
- 【禁止】复杂多层 Stream 导致代码难以调试。
- 【推荐】大批量数据处理前先确认数据规模，**禁止**一次性将百万级数据加载到内存。

## 5.1 Stream 收集到 Map 的空值与冲突

【说明】底层实现（`HashMap.merge` / `requireNonNull`）不接受 `null`，以下是高频线上坑：

- 【强制】`Collectors.toMap` 必须显式提供 mergeFunction，否则遇到重复 key 直接抛 `IllegalStateException`。
- 【强制】`Collectors.toMap` 的 valueMapper 不得返回 `null`（底层 `HashMap.merge` 对 null value 抛 NPE）：

```java
// 源字段可能为 null 时：先过滤，或映射时兜底
map = list.stream()
        .filter(e -> e.getCode() != null)
        .collect(Collectors.toMap(Entity::getCode, e -> Objects.requireNonNullElse(e.getName(), ""),
                                  (oldV, newV) -> newV));
```

- 【强制】`Collectors.groupingBy` 的**分组键不得为 null**（classifier 结果被 `requireNonNull` 校验）：分组键可能为 null 时先过滤，或映射为显式兜底键（如 `"UNKNOWN"`）。
- 【推荐】key 冲突语义必须显式表达（保留旧值 / 新值 / 合并），例如 `(oldV, newV) -> newV`，**禁止**依赖默认行为。

---

# 六、魔法数字与常量

- 【强制】业务代码禁止出现没有语义的魔法数字。
- 【强制】状态、类型、来源、渠道等业务枚举必须有统一枚举。
- 【强制】跨类共享的业务常量放在明确的常量类 / 枚举中。
- 【禁止】创建一个包含几百个互不相关字段的 `Constants` 巨型类。
- 【推荐】按业务域拆分常量类。

```java
if (user.getStatus() == 1) {}                            // 禁止
if (UserStatus.NORMAL.equals(user.getStatus())) {}       // 正确
```

---

# 七、Utils / Helper

- 【禁止】把无法归类的代码都塞进 `Utils`。
- 【推荐】`Utils` 必须是无状态、通用、稳定的纯工具。
- 【禁止】`Utils` 中直接注入 Service、Repository、`RedisTemplate` 等业务 / 基础设施 Bean。
- 【推荐】有业务语义的方法放入 Service，而不是 `Utils`。

---

# 八、代码注释

- 【强制】注释解释"**为什么**"，不要简单重复"是什么"。
- 【禁止】给显而易见的代码添加无意义注释。
- 【强制】复杂业务规则必须有注释或设计文档。
- 【推荐】架构决策写入 ADR（`../decisions/`），而不是全部塞进代码注释。

---

# 九、代码格式与静态检查

【推荐】统一使用工具链：Google Java Format / IDE Code Style、Checkstyle、Spotless、PMD、SonarLint。

- 【强制】代码格式化由**工具统一执行**，不通过人工争论空格与换行。
- 【强制】CI 必须阻止编译错误、格式错误与关键静态检查问题。
- 【推荐】SQL 中的 `updated_at` 维护使用静态检查兜底（见 `database.md` §8.3.3）。
