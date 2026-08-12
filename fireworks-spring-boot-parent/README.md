# fireworks-spring-boot-parent

**公共父 POM**，统一各 starter 模块的编译与构建约定，是所有 `*-spring-boot-starter` 模块的父工程。

## 作用

- **统一 Lombok**：以 `scope=provided` 引入 Lombok，仅编译期需要、运行时由环境提供。
- **统一配置元数据处理器**：以 `optional=true` 引入 `spring-boot-configuration-processor`，编译期为属性提示生成元数据，不打包进最终 Jar。
- **统一编译插件**：锁定 `maven-compiler-plugin`，强制 `source`/`target` 与 `${java.version}` 一致、`encoding=UTF-8`。
  - 开启 `<parameters>true</parameters>`，保留方法参数名，避免 Spring 反射/参数绑定丢失。
  - 配置注解处理器路径：Lombok、Spring Boot 配置处理器、MapStruct、Lombok-MapStruct 绑定。

## 使用方式

各 starter 模块声明父 POM 为当前模块即可获得统一构建约定：

```xml
<parent>
    <groupId>com.yzm.fireworks</groupId>
    <artifactId>fireworks-spring-boot-parent</artifactId>
    <version>${revision}</version>
</parent>
```

该模块为 `pom` 打包的纯工程管理模块，不包含任何可执行代码。
