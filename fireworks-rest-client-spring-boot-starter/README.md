# Fireworks RestClient Spring Boot Starter

生产级 RestClient 配置，提供连接池管理、超时控制、负载均衡等功能。

## 功能特性

- 连接池管理（PoolingHttpClientConnectionManager）
- 三级超时精细控制（connectTimeout / socketTimeout / connectionRequestTimeout）
- 后台线程自动回收过期和空闲连接，防止僵尸连接
- 支持 Spring Cloud LoadBalancer 负载均衡

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.yzm.fireworks</groupId>
    <artifactId>fireworks-rest-client-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置参数

```yaml
fireworks:
  rest-client:
    connect-timeout: 3000        # TCP 握手超时（ms）
    socket-timeout: 10000        # 读取数据超时（ms）
    connection-request-timeout: 800  # 从连接池获取连接超时（ms）
    max-conn-total: 500          # 连接池总容量
    max-conn-per-route: 200      # 单个目标主机最大连接数
    keep-alive: 30000            # 连接空闲保活时长（ms）
    evict-idle-connections: 60000  # 回收空闲连接间隔（ms）
```

### 3. 使用示例

#### 普通 HTTP 调用

```java
@Service
public class UserService {

    private final RestClient restClient;

    public UserService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl("https://api.example.com")
                .build();
    }

    public UserDTO getUser(Long id) {
        return restClient.get()
                .uri("/users/{id}", id)
                .retrieve()
                .body(UserDTO.class);
    }
}
```

#### 负载均衡调用（Spring Cloud）

```java
@Service
public class UserService {

    private final RestClient lbRestClient;

    public UserService(@LoadBalanced RestClient.Builder lbRestClientBuilder) {
        this.lbRestClient = lbRestClientBuilder.build();
    }

    public UserDTO getUser(Long id) {
        return lbRestClient.get()
                .uri("http://user-service/api/users/{id}", id)
                .retrieve()
                .body(UserDTO.class);
    }
}
```

## 配置说明

### 超时配置

- `connect-timeout`: TCP 三次握手超时，推荐内网 1000~2000ms，外网 3000~5000ms
- `socket-timeout`: 等待服务端响应超时，根据下游接口 P99 响应时间的 2~3 倍设置
- `connection-request-timeout`: 从连接池获取连接超时，建议设短（500~1000ms），快速失败

### 连接池配置

- `max-conn-total`: 连接池总容量，推荐 200~500
- `max-conn-per-route`: 单个目标主机最大连接数，推荐 100~200
  - 推导公式：maxConnPerRoute ≥ 峰值QPS × 平均响应时间(秒) × 缓冲系数(1.5)
  - 示例：峰值 500QPS，平均响应 200ms → 500 × 0.2 × 1.5 = 150，取 200

### 连接保活

- `keep-alive`: 连接空闲保活时长，必须小于服务端 keepAlive 设置
- `evict-idle-connections`: 后台线程回收空闲连接间隔

## 注意事项

1. 慢接口（报表/文件下载）建议单独创建 RestClient 实例并使用更大的超时值
2. 若频繁出现 ConnectionRequestTimeoutException，说明连接池容量不足，应调大 maxConnPerRoute
3. keepAlive 必须小于目标服务端的 keepAlive 超时设置，否则会出现"僵尸连接"
