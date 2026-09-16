# fireworks-oplog-spring-boot-starter

业务操作日志组件，基于注解 + SpEL 表达式记录操作日志。

源码移植自 [mzt-biz-log](https://github.com/mouzt/mzt-biz-log)（Apache-2.0），
原项目长期停更，故拷贝源码至本项目自行维护，做了如下定制：

- 移除 `java-object-diff`（2015 年后停更）依赖，去掉 `diff` 能力
- 移除 `log4j` 工具类依赖，改用 JDK / Spring 工具类
- 兼容 Jakarta EE（Spring Boot 3）
- 包路径调整为 `com.yzm.fireworks.oplog`
- `LogRecord.createdAt` 改用 `Instant`，`id` 改用 `Long`
- 占位符改为 Spring 原生 `#{表达式}`，由 `TemplateParserContext` 切分与求值，
  与 `@Value`、`@ConditionalOnExpression` 语法一致；同时移除 `IFunctionService`
  等自建函数注册表，函数调用回归 SpEL 原生 `@beanName.method(...)`
- 框架保留变量改名为 `#_result` / `#_exception`，与 common 的
  `SpelEvaluator` 统一；移除 `#args`，方法参数统一用 `#参数名` / `#a0` / `#p0`
- 条件类表达式改为按布尔语义求值，不再依赖字符串后缀匹配 `true` / `false`
- 求值层保留原始类型（`render` 返回 `Object`），单表达式返回对象本身、
  混排模板返回字符串，使 `operator` 可直接返回 `Operator` 对象
- 操作人表达式指定后不再调用 `IOperatorGetService`，避免重复查询
- SpEL 求值能力下沉至 common 的 `com.yzm.fireworks.common.expression`，
  与分布式锁、文件确认等模块共用同一套带缓存的求值内核

## 快速开始

```java
@EnableLogRecord(tenant = "fireworks")
@SpringBootApplication
public class Application { }
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
    record:
      # 是否跳过"未真正渲染"的日志
      # false（默认）：模板含 #{} 占位符、但渲染结果与模板原文完全相同时，跳过记录
      # true：不做此判断，一律记录
      skip-unrendered-log: false
```

## 开关注解属性

```java
@EnableLogRecord(
    tenant = "fireworks",       // 租户/应用标识（必填）
    proxyTargetClass = false,   // 是否强制 CGLIB 代理
    mode = AdviceMode.PROXY,    // 代理方式
    joinTransaction = false,    // 日志与业务是否同一事务
    order = Ordered.LOWEST_PRECEDENCE  // 切面优先级
)
```
