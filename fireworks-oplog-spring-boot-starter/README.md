# fireworks-oplog-spring-boot-starter

业务操作日志组件，基于注解 + SpEL 表达式记录操作日志。

基于 [mzt-biz-log](https://github.com/mouzt/mzt-biz-log)（Apache-2.0）维护，与本框架的求值内核、日志模型保持统一：

- 占位符使用 Spring 原生 `#{表达式}`，由 `TemplateParserContext` 切分与求值，语法与
  `@Value`、`@ConditionalOnExpression` 一致
- 函数调用走 SpEL 原生 `@beanName.method(...)`，不提供自建函数注册表
- 保留变量：`#_result` / `#_exception`；方法参数用 `#参数名` / `#a0` / `#p0`
- 条件表达式按布尔语义求值
- 求值层保留原始类型（`render` 返回 `Object`）：单表达式返回对象本身、混排模板返回字符串，
  因此 `operator` 可以直接返回 `Operator`
- 指定 `operator` 表达式时直接采用其结果，不再查询 `IOperatorGetService`
- SpEL 求值内核位于 common 的 `com.yzm.fireworks.common.expression`，与分布式锁、文件确认等模块共用

## 快速开始

引入依赖即生效，**不需要任何 `@EnableXxx` 注解**，全部配置走 `fireworks.oplog.*`：

```yaml
fireworks:
  oplog:
    tenant: fireworks     # 租户/应用标识，写入每条日志
```

## 使用示例

```java
@LogRecord(
    type = "USER",
    bizNo = "#{#user.id}",
    success = "修改用户：#{#user.name}",
    condition = "#{#_result != null}"
)
public boolean update(User user) {
    // 需要方法执行前的旧值时，自行查库并放入上下文，
    // 上下文在方法执行前采集，因此此处拿到的必然是旧值
    LogRecordContext.putVariable("old", userMapper.selectById(user.getId()));
    return userMapper.updateById(user) > 0;
}
```

## 模板语法

模板中的动态部分使用 `#{表达式}` 占位，表达式为原生 SpEL，可与静态文案自由混排：

```java
// 混排静态文案与动态值
@LogRecord(type = "USER", bizNo = "#{#id}", success = "用户 #{#name} 在 #{#ip} 修改了订单")
// 表达式结果为 null 时渲染为空串，不会出现字面量 "null"
@LogRecord(type = "USER", bizNo = "#{#id}", success = "备注：#{#remark}")
// 方法调用直接使用 SpEL 的 @beanName.method(...) 语法
@LogRecord(type = "USER", bizNo = "#{#id}", success = "变更：#{@diffService.compare(#old, #new)}")
// 静态方法调用，可直接从工具类 / 上下文取当前登录人，无需手动放变量
@LogRecord(type = "USER", bizNo = "#{#id}", success = "操作人：#{T(com.xxx.UserContext).getUserName()}")
```

> 切分与求值由 Spring 官方的 `TemplateParserContext` 完成，语法与 `@Value`、
> `@ConditionalOnExpression` 完全一致，无需额外学习成本。
> 相比手写正则，官方实现能正确识别字符串字面量中的 `}`（如 `#{'a}b'}`），
> 也支持 SpEL 的 inline map 语法（`#{{'a':1}}`）。

可用变量：

| 变量 | 说明 |
| --- | --- |
| `#参数名` | 被注解方法的入参 |
| `#a0` / `#p0` | 按位置访问方法入参（参数名发现失效时的兜底） |
| `#_result` | 方法返回值 |
| `#_exception` | 方法抛出的异常对象，可取 `#_exception.message` |
| `#自定义名` | 业务通过 `LogRecordContext.putVariable` 放入的变量 |
| `#全局名` | 业务通过 `LogRecordContext.putGlobalVariable` 放入的变量 |
| `@beanName` | 容器中的 Bean，由 `BeanFactoryResolver` 解析 |

> 框架保留变量统一使用 `_` 前缀（`#_result` / `#_exception`），
> 以降低与业务参数名撞名的概率。

### 关于 `${}`

模板中**只解析 `#{}`，不解析 `${}`**。`${xxx}` 会被当作普通静态文本原样输出：

```java
// 输出：版本 ${app.version} 更新
@LogRecord(type = "APP", bizNo = "#{#id}", success = "版本 ${app.version} 更新")
```

这与 `@Value` 不同——`@Value` 能同时支持 `${}` 和 `#{}`，是因为它串联了
`PropertySourcesPlaceholderConfigurer`（处理 `${}`）与 `TemplateParserContext`
（处理 `#{}`）两级处理器；日志模板只使用后者，因此没有配置项引用能力。

若确实需要引用配置值，可在 SpEL 中通过内置的 `environment` Bean 获取：

```java
@LogRecord(type = "APP", bizNo = "#{#id}",
           success = "版本 #{@environment.getProperty('app.version')} 更新")
```

### 操作人

操作人通过 `operator` 属性指定，表达式**必须求值为 `Operator` 对象**：

```java
// 静态方法取当前登录人，无需实现 IOperatorGetService
@LogRecord(
    type = "USER",
    bizNo = "#{#id}",
    success = "修改用户：#{#name}",
    operator = "#{T(com.xxx.UserContext).getUser()}"
)

// 通过 Bean 取
@LogRecord(type = "USER", bizNo = "#{#id}", success = "...", operator = "#{@userContext.getUser()}")
```

- **指定了 `operator`**：直接采用表达式结果，**不再调用 `IOperatorGetService`**
- **未指定 `operator`**：回退到 `IOperatorGetService.getUser()` 获取当前登录人
- 表达式的求值结果不是 `Operator` 时会抛出异常，避免写入错误的操作人

条件类表达式（`condition`、`successCondition`）按布尔语义求值：

```java
// 返回值为 null 时不记录
@LogRecord(type = "USER", bizNo = "#{#id}", success = "删除用户", condition = "#{#_result != null}")
// 以业务返回值判定成功，而非是否抛异常
@LogRecord(type = "ORDER", bizNo = "#{#orderNo}", success = "下单成功", fail = "下单失败",
           successCondition = "#{#_result.success}")
```

## 扩展点

| 接口 | 用途 |
| --- | --- |
| `ILogRecordService` | 日志保存（对接内部 MQ / ES / 数据库），**必须实现** |
| `IOperatorGetService` | 操作人获取（对接内部鉴权体系）。在 `@LogRecord` 中指定了 `operator` 表达式时可不必实现 |

## 配置项

```yaml
fireworks:
  oplog:
    enabled: true                  # 模块开关
    tenant: ""                     # 租户/应用标识，写入每条日志
    order: 2147483647              # advisor 优先级（Ordered.LOWEST_PRECEDENCE）
    join-transaction: false        # 日志与业务是否同一事务
```

> 全部配置都在 `fireworks.oplog.*`，不需要 `@EnableXxx` 注解：注解形式的属性无法被
> `spring-configuration-metadata` 发现，也就没有 IDE 提示与校验。
