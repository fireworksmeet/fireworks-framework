# fireworks-dependencies

**依赖清单（BOM）模块**，统一声明整个框架所有第三方依赖的版本，作为根聚合工程的子模块与全局版本管理中心。

## 作用

- **统一版本管理**：集中定义 Spring Boot、Spring Cloud、Spring Cloud Alibaba、MyBatis-Plus、EasyExcel、RocketMQ、XXL-JOB、Redisson、MinIO、阿里云 OSS、Nimbus JOSE JWT、MapStruct、Lombok 等全部依赖版本。
- **BOM 依赖管理**：通过 `<dependencyManagement>` 管理依赖版本，供所有 starter 模块在声明依赖时**省略版本号**。
- **基础环境**：作为 `fireworks-spring-boot-parent` 与各 starter 模块的父 POM（间接）来源。

## 使用方式

该模块为 `pom` 打包的纯版本管理工程，**不包含任何可执行代码**。

各 starter 模块通过继承 `fireworks-spring-boot-parent`（其父为当前 BOM）即可使用这里锁定的依赖版本。

```xml
<!-- 业务工程中按需引入 BOM，即可使用统一版本，无需再写 version -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.yzm.fireworks</groupId>
            <artifactId>fireworks-dependencies</artifactId>
            <version>${revision}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## 关键版本

| 依赖 | 版本 |
| --- | --- |
| Spring Boot | 3.5.15 |
| Spring Cloud | 2025.0.2 |
| Spring Cloud Alibaba | 2025.0.0.0 |
| MyBatis-Plus | 3.5.15 |
| EasyExcel | 4.0.3 |
| RocketMQ Starter | 2.3.5 |
| XXL-JOB | 3.3.1 |
| Nimbus JOSE JWT | 9.31 |
| Lombok | 1.18.46 |
| MapStruct | 1.6.3 |

> 完整版本清单以 `pom.xml` 中 `<properties>` 为准。
