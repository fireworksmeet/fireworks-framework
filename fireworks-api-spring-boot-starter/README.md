# fireworks-api-spring-boot-starter

API 层公共能力 Starter，提供**统一响应**、**分页响应**、**业务异常**、**业务断言**与**枚举契约/工具**，以及可选的 Hibernate Validator 与选项枚举扫描。

## 功能特性

- **统一响应 `Result<T>`**：code / data / message / system 四段结构，提供丰富的静态工厂与链式处理、错误透传、数据转换方法。
- **分页响应 `PageResponse<T>`**：同时支持传统 **Offset 模式**（接收记录列表 + 分页元信息，**与任何 ORM 无关**）与 **Cursor 游标模式**（瀑布流 / 海量数据）。
- **带 footer 合计的分页 `PageWithFooterResponse<T>`**：分页结果附带合计行。
- **业务异常 `BizException` / 断言 `BizAssert`**：不打印堆栈提升性能，断言快速失败。
- **枚举契约 `CodeEnum<V>`**：以 `getCode()` 统一「入库值 / 前端交互值 / JSON 序列化值」，`getLabel()` 提供展示文案。
- **枚举工具 `EnumUtil` / 游标工具 `CursorUtil`**：按 `CodeEnum#getCode()` 匹配枚举、游标编解码。
- **暴露枚举扫描**：扫描标注 `@ExposedEnum` 的枚举，供前端下拉字典使用。
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
return Result.error(CommonExceptionStatus.REQUEST_PARAMS_ERROR, "参数不合法");

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
if (orderResult.isFailure()) {
    return orderResult.propagate();
}
```

### 3. 分页响应 `PageResponse`

**Offset 模式**（与任何 ORM / 手写 SQL 无关，只接收记录列表与分页元信息）：

```java
List<User> records = userMapper.selectPage(queryWrapper);  // 任意持久化方案均可
long total = userMapper.countByQuery(queryWrapper);

return Result.ok(PageResponse.ofOffset(records, current, size, total, UserVO::from));
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
BizAssert.isTrue(amount > 0, CommonExceptionStatus.REQUEST_PARAMS_ERROR);
```

### 5. 选项枚举扫描

在需要暴露的枚举上标注 `@ExposedEnum`，并开启扫描。扫描范围默认取启动类所在包，跨包时用 `scan-packages` 覆盖：

```yaml
fireworks:
  exposed-enum:
    enabled: true
    # 可选：不配置则使用启动类所在包
    scan-packages:
      - com.yourcompany.enums
```

### 6. 校验器

默认注册 fail-fast 的 Hibernate Validator（`fireworks.api.validator.fail-fast=false` 可关闭）：

```yaml
fireworks:
  api:
    validator:
      fail-fast: true
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
| `EnumUtil` | 按 `CodeEnum#getCode()` 匹配枚举的工具 |
| `CursorUtil` | 游标编解码工具 |
| `CodeEnum<V>` | 带业务编码与展示文案的枚举契约 |
| `@ExposedEnum` | 标记枚举对外暴露（供字典扫描） |

## 注意事项

- 依赖 `fireworks-common-spring-boot-starter`（`Result` 中的状态码、`SpringContextHolder` 等）。
- `BizException.fillInStackTrace()` 被重写为空实现以提升性能，请勿依赖其堆栈信息定位问题。
