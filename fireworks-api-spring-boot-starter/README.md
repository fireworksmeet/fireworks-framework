# fireworks-api-spring-boot-starter

API 层公共能力 Starter，提供**统一响应**、**分页响应**、**业务异常**、**业务断言**、**枚举工具**与**操作日志注解**，以及可选的 Hibernate Validator 与选项枚举扫描。

## 功能特性

- **统一响应 `Result<T>`**：code / data / message / error / system 五段结构，提供丰富的静态工厂与链式处理、错误透传、数据转换方法。
- **分页响应 `PageResponse<T>`**：同时支持传统 **Offset 模式**（兼容 MyBatis-Plus `IPage`）与 **Cursor 游标模式**（瀑布流 / 海量数据）。
- **带 footer 合计的分页 `PageWithFooterResponse<T>`**：分页结果附带合计行。
- **业务异常 `BizException` / 断言 `BizAssert`**：不打印堆栈提升性能，断言快速失败。
- **操作日志注解 `@OptLog`**：声明式标注业务操作，配合 `fireworks-web` 模块的 `@EnableOptLog` 拦截。
- **枚举工具 `EnumUtil` / 游标工具 `CursorUtil`**：枚举值匹配、游标编解码。
- **选项枚举扫描**：扫描实现 `IOptionEnum` 的枚举供前端下拉选项使用。
- **Hibernate Validator**：默认注册 fail-fast 校验器。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.yzm.fireworks</groupId>
    <artifactId>fireworks-api-spring-boot-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

### 2. 统一响应 `Result`

```java
// 成功
return Result.ok(data);

// 失败（携带业务状态码）
return Result.error(CommonExceptionStatus.PARAM_NOT_VALID, "参数不合法");

// 链式处理
Result<UserVO> result = userService.getById(id)
        .onSuccess(u -> log.info("用户: {}", u.getName()))
        .onFailure(r -> log.warn("失败: {}", r.getMessage()));
```

内部服务调用结果处理：

```java
// 失败时抛 BizException（错误快速传播）
UserDTO user = userService.getById(id).getDataOrThrow();

// 失败时降级
UserDTO user = userService.getById(id).getDataOrElse(() -> defaultUser);

// 将失败结果原样透传为其他类型的 Result
if (orderResult.fail()) {
    return orderResult.propagate();
}
```

### 3. 分页响应 `PageResponse`

**Offset 模式（MyBatis-Plus）**：

```java
IPage<User> page = new Page<>(current, size);
userMapper.selectPage(page, queryWrapper);

return Result.ok(PageResponse.create(page, UserVO::from));
```

**Cursor 游标模式（瀑布流）**：

```java
// 建议 SQL 按 limit size + 1 查询
List<Article> list = articleMapper.queryByCursor(cursor, size + 1);

PageResponse<ArticleVO> page = PageResponse.ofCursor(
        list, size,
        ArticleVO::from,          // PO -> VO 转换
        last -> CursorUtil.encode(last.getId())  // 从最后一条提取 nextCursor
);
```

### 4. 业务异常与断言

```java
// 抛异常
throw new BizException(CommonExceptionStatus.SERVER_ERROR);

// 断言（快速失败）
BizAssert.notNull(user, CommonExceptionStatus.USER_NOT_FOUND);
BizAssert.isTrue(amount > 0, CommonExceptionStatus.PARAM_NOT_VALID);
```

### 5. 操作日志注解 `@OptLog`

```java
@OptLog(module = "用户管理", type = OptLogType.UPDATE, description = "更新用户")
public void updateUser(UserDTO user) { ... }
```

> 需配合 `fireworks-web-spring-boot-starter` 的 `@EnableOptLog` 才能被拦截处理。

### 6. 选项枚举扫描

实现 `IOptionEnum` 的枚举，并开启扫描：

```yaml
fireworks:
  option-enum:
    enabled: true
    scan-packages:
      - com.yourcompany.**.enums
```

### 7. 校验器

默认注册 fail-fast 的 Hibernate Validator（`fireworks.api.validator.enabled=false` 可关闭）：

```yaml
fireworks:
  api:
    validator:
      enabled: true
```

## 核心类速览

| 类 | 说明 |
| --- | --- |
| `Result<T>` | 统一响应结果 |
| `PageRequest` | 分页请求参数 |
| `PageResponse<T>` | 分页响应（Offset + Cursor） |
| `PageWithFooterResponse<T>` | 带合计行的分页响应 |
| `BizException` | 业务异常 |
| `BizAssert` | 业务断言 |
| `@OptLog` / `OptLogType` | 操作日志注解 / 操作类型 |
| `EnumUtil` | 枚举值匹配工具 |
| `CursorUtil` | 游标编解码工具 |
| `IOptionEnum` | 选项枚举接口 |

## 注意事项

- 依赖 `fireworks-common-spring-boot-starter`（`Result` 中的状态码、`SpringContextHolder` 等）。
- `BizException.fillInStackTrace()` 被重写为空实现以提升性能，请勿依赖其堆栈信息定位问题。
