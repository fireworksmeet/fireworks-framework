# fireworks-xxljob-spring-boot-starter

基于 **XXL-JOB** 的分布式定时任务 Starter，封装 `XxlJobSpringExecutor` 的自动配置，开箱即用。

## 功能特性

- **自动配置执行器**：引入依赖并开启开关后，自动装配 `XxlJobSpringExecutor`，无需手动初始化。
- **IP 自动探测**：未显式配置执行器 IP 时，自动获取本机非回环地址。
- **地址注册**：支持通过 `appName` 自动注册到调度中心，或通过 `address` 指定固定地址。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.yzm.fireworks</groupId>
    <artifactId>fireworks-xxljob-spring-boot-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

### 2. 配置

```yaml
fireworks:
  xxljob:
    enabled: true                     # 必须为 true 才会启用执行器
    access-token: your-access-token   # 调度中心通信令牌（选填）
    admin:
      addresses: http://127.0.0.1:8080/xxl-job-admin   # 调度中心地址
    executor:
      app-name: xxl-job-executor-sample   # 执行器 AppName，为空则不自动注册
      address: ''                         # 固定执行器地址（选填，为空用 appName 自动注册）
      ip: ''                              # 执行器 IP，为空自动获取
      port: 9999                          # 执行器端口，<=0 则自动获取
      log-path: /data/applogs/xxl-job/jobhandler   # 日志路径，为空用默认
      log-retention-days: 30              # 日志保存天数，>3 才生效
```

### 3. 编写任务

任务类上标注 `@Component`，处理器方法标注 `@XxlJob("任务名称")`：

```java
@Component
public class SampleXxlJob {

    @XxlJob("demoJobHandler")
    public void demoJobHandler() {
        System.out.println("XXL-JOB hello world.");
    }
}
```

> `@XxlJob` 注解来自 `com.xxl.job.core.handler.annotation.XxlJob`。

### 4. 在调度中心配置任务

在 XXL-JOB 调度中心（Admin）中新增执行器与任务，任务 `JobHandler` 填入上面的 `"demoJobHandler"`。

## 配置项汇总

| 前缀 | 字段 | 说明 |
| --- | --- | --- |
| `fireworks.xxljob.enabled` | 总开关 | 未开启时整个自动配置不生效 |
| `fireworks.xxljob.access-token` | 通信令牌 | 与调度中心一致 |
| `fireworks.xxljob.admin.addresses` | 调度中心地址 | 必填 |
| `fireworks.xxljob.executor.app-name` | 执行器名称 | 为空则关闭自动注册 |
| `fireworks.xxljob.executor.ip` | 执行器 IP | 为空自动获取 |
| `fireworks.xxljob.executor.port` | 执行器端口 | `<=0` 自动获取 |
| `fireworks.xxljob.executor.log-path` | 日志路径 | 为空用默认 |
| `fireworks.xxljob.executor.log-retention-days` | 日志保留天数 | `>3` 生效 |

> 依赖的 XXL-JOB 具体版本以 `fireworks-dependencies` 为准。
