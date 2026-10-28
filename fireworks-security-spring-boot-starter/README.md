# fireworks-security-spring-boot-starter

**认证装配与授权约定**：业务只回答「这个请求是谁」，框架负责把角色/权限装配进 Spring Security，
并统一输出 401 / 403 / 500。

> 认证方式由业务决定（网关透传、JWT、Session……）。框架**不提供 `mode` 开关**，也不替你选机制。

## 定位

```text
业务：实现 SecurityUserResolver —— 从请求解析出 SecurityUser（含最终的角色与权限）
框架：角色加 role-prefix、权限原样 → GrantedAuthority
      → 写入 SecurityContext → 请求结束清理 → 失败时统一响应
```

框架不二次加载授权：`SecurityUser` 里的 `roles` / `permissions` 就是最终结果。

## 功能特性

- **默认安全链**：stateless、关闭 `formLogin` / `httpBasic`、`anyRequest().authenticated()`
- **链让位**：容器中已有 `SecurityFilterChain` 时框架不装配（与 Boot 默认链同规则）
- **归一化**：角色与权限去首尾空白、丢弃空值、去重；角色加前缀、权限原样
- **统一响应**：401 / 403 / 500 都是 `Result` JSON
- **预检放行**：CORS preflight 不调用 resolver
- **方法安全**：`@PreAuthorize` / `@PostAuthorize` 开箱可用

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.yzm.fireworks</groupId>
    <artifactId>fireworks-security-spring-boot-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

### 2. 实现 `SecurityUserResolver`

框架只认这一个 SPI：

```java
@Component
public class GatewayUserResolver implements SecurityUserResolver {

    @Override
    public SecurityUser resolve(HttpServletRequest request) {
        String header = request.getHeader("X-User-Context");
        if (!StringUtils.hasText(header)) {
            return null;                       // 无凭证 → 401
        }
        return JsonUtil.deserialize(Base64Util.decodeUrl(header), SecurityUser.class);
    }
}
```

`SecurityUser` 是 record，直接反序列化即可：

```json
{"userId":1,"username":"alice","roles":["ADMIN"],"permissions":["order:export"]}
```

### 3. 用 `@PreAuthorize` 授权

```java
@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/orders/export")
public Result<Void> export() { ... }

@PreAuthorize("hasAuthority('order:export') and hasRole('FINANCE')")
@GetMapping("/orders/{id}")
public Result<OrderVO> detail() { ... }
```

## 认证语义

| 情况 | 结果 | 说明 |
| --- | --- | --- |
| resolver 返回 `null` | **401** | 无凭证，常态；由授权层输出 |
| resolver 返回非 `null` | **已认证** | 字段组成由业务决定；未给 authority 时授权层按无权限处理 |
| resolver 抛异常 | **500** | 认证未完成，fail-closed，不继续过滤链 |
| CORS 预检 | 放行 | 不调用 resolver；授权层仍按 `permitAll(OPTIONS)` 判断 |

**resolver 契约**

- 无凭证 / 凭证不可用 → **返回 `null`**，不要抛异常；
- 解析过程出错（依赖故障、实现 bug）→ 抛异常，框架记 ERROR 并返回 500；
- 返回 `null` 表示未认证，返回非 `null` 即视为已认证；字段组成由业务决定，框架不校验；
- 返回的 `SecurityUser` 必须一次性给出最终角色与权限（框架不会二次加载）；
- 不要在实现里操作 `SecurityContextHolder`，也不要自行输出响应。

## 授权：角色与权限的约定

| 维度 | authority 形式 | 匹配方式 |
| --- | --- | --- |
| 角色 | `role-prefix` + 角色码（默认 `ROLE_ADMIN`） | `hasRole('ADMIN')` |
| 权限 | 权限码原样（`order:export`） | `hasAuthority('order:export')` |

`role-prefix` 默认 `ROLE_`；如果角色码本身已带前缀，把它设为空串。

## 读取当前用户

```java
SecurityUser user = SecurityUserContext.getUser();        // 未登录返回 null
SecurityUser user = SecurityUserContext.requireUser();    // 未登录抛 UNAUTHORIZED
Long userId = SecurityUserContext.getUserId();
```

| 方法 | 未登录时 |
| --- | --- |
| `getUser()` / `getUserId()` / `getUsername()` | `null` |
| `requireUser()` / `requireUserId()` | 抛 `BizException(UNAUTHORIZED)` |
| `getRoles()` / `getPermissions()` / `getAttributes()` | 空集合 |
| `getAttribute(key[, type])` | `null` |
| `hasRole(role)` | `false` |
| `isAuthenticated()` | `false` |

> `hasRole("ADMIN")` 比较的是**原始角色码**（无前缀）；Spring 的 `hasRole('ADMIN')` 比较的是
> `ROLE_ADMIN`。两者同名不同义，别写成 `hasRole("ROLE_ADMIN")`。

## 自定义 `SecurityFilterChain`

业务声明自己的链后，框架链（含过滤器）**整体让位**：

```java
@Bean
@Order(1)
SecurityFilterChain customChain(HttpSecurity http, SecurityUserResolver resolver,
                                SecurityProperties properties, SecurityErrorWriter errorWriter) throws Exception {
    http.securityMatcher("/api/**")
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(new SecurityUserAuthenticationFilter(resolver, properties.getRolePrefix(), errorWriter),
                UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS).permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated());
    return http.build();
}
```

三点提醒：

1. **`permit-all-paths` 与 `cors.*` 不再生效**，需要在业务链里自己配；
2. 过滤器不是 Spring Bean（避免被 Servlet 容器重复注册），必须手动 `addFilterBefore`；
3. 只覆盖部分路径时，**未命中的请求不会经过任何安全过滤器**——请确保业务链覆盖全部路径。

## 配置

```yaml
fireworks:
  security:
    enabled: true                     # 关闭后：链、过滤器、错误处理、方法安全全部不装配
    role-prefix: "ROLE_"              # 角色码本身带前缀时设为空串
    permit-all-paths:                 # 仅框架默认链生效
      - /actuator/**
    cors-enabled: true                # 仅框架默认链生效；未配 allowed-origin-patterns 时不启用 CORS
    cors:
      allowed-origin-patterns: [ "https://admin.example.com" ]
      allowed-methods: [ "*" ]
      allowed-headers: [ "*" ]
      exposed-headers: [ ]
      allow-credentials: false        # 与 "*" 来源同时开启会告警
      max-age: 3600
```

> **CORS 默认关闭**：`allowed-origin-patterns` 为空时不启用跨域，也不会向容器注册
> `CorsConfigurationSource` Bean；配置后只在框架默认链内生效。业务自定义链时请自行配置。

## 常见问题

**引了依赖、所有请求都是 401？**
没有提供 `SecurityUserResolver`。启动日志会打印
`未检测到 SecurityUserResolver，认证不会生效`。

**声明自己的链后，`permit-all-paths` 没用了？**
框架链已让位，相关配置自然失效，需要在业务链里重写。

**token 过期返回的是 401 还是 500？**
取决于 resolver：凭证问题请返回 `null`（→ 401）；抛异常会被当作服务端故障（→ 500）。

**`enabled: false` 会不会影响 `@PreAuthorize`？**
会——方法安全与链在同一个自动配置类上，关闭后 `@PreAuthorize` 不再生效。
只想接管链时，保留 `enabled: true` 并声明自己的 `SecurityFilterChain` 即可（框架链自动让位）。

**角色码大小写？**
框架不转换大小写。Spring 的 `hasRole('ADMIN')` 要求大写 `ROLE_ADMIN`，请在 resolver 里统一。

**为什么需要 Spring MVC？**
`permit-all-paths` 使用 `MvcRequestMatcher`，要求容器中存在 `mvcHandlerMappingIntrospector`；
本模块已依赖 `spring-boot-starter-web`，排除 `WebMvcAutoConfiguration` 的服务会启动失败。

## 核心类

| 类 | 职责 |
| --- | --- |
| `SecurityUserResolver` | 唯一 SPI：请求 → `SecurityUser`（含最终角色与权限） |
| `SecurityUserAuthenticationFilter` | 调用 resolver、装配 authorities、写入 / 清理上下文、异常时 500 |
| `SecurityUser` | 不可变身份载体（record），构造时归一化角色与权限 |
| `Authorities` | 角色 / 权限两个维度，转成 `GrantedAuthority`（角色加前缀） |
| `SecurityUserContext` | 只读门面，业务不直接操作 `SecurityContextHolder` |
| `SecurityErrorWriter` | 统一写出 401 / 403 / 500 的 `Result` |
| `SecurityAutoConfiguration` | 默认链、CORS、错误处理器、方法安全 |
| `SecurityProperties` | `role-prefix`、免认证路径、CORS |
