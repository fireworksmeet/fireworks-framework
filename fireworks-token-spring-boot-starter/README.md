# fireworks-token-spring-boot-starter

基于 Nimbus JOSE + JWT（HS256）的 Token 生成 / 解析 / 验证 Starter，提供普通安全随机 Token 与 JWT 两种模式。

## 功能特性

- **JWT 签发 / 解析 / 验证**：基于 `nimbus-jose-jwt`，HMAC-SHA256 签名，支持过期时间、签发者、受众、用户 ID、扩展字段。
- **普通安全随机 Token**：`generateSecureToken()` 基于 `SecureRandom` 生成 URL-safe 的随机 Token（默认 32 字节）。
- **Bearer 支持**：提供从请求头提取 Token、去除 / 拼接 `Bearer ` 前缀等静态工具方法。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.yzm.fireworks</groupId>
    <artifactId>fireworks-token-spring-boot-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

### 2. 配置 JWT

```yaml
fireworks:
  token:
    jwt:
      secret: your-secret-key-at-least-32-characters-long # 必填，长度不低于 32 位
      iss: your-issuer        # 签发者，可选
      aud: your-audience      # 受众，可选
      user-id-key: userId     # 存储 uid 的 key，可选
      ext-key: ext            # 扩展字段 key，可选
```

> `secret` 为必填项，长度必须 ≥ 32 位，否则启动时校验失败。

### 3. 注入 `TokenUtil`

```java
@Resource
private TokenUtil tokenUtil;
```

### 4. 生成 JWT

```java
JWTClaim claim = JWTClaim.builder()
        .userId(10086L)                       // 用户 ID
        .exp(Date.from(Instant.now().plus(2, ChronoUnit.HOURS))) // 过期时间
        .ext(Map.of("role", "admin"))         // 扩展字段（可选）
        .build();

String jwt = tokenUtil.generateJwtToken(claim);
```

### 5. 解析并验证 JWT

```java
JWTClaim claim = tokenUtil.parseJwtToken(jwt); // 签名校验失败会抛出 TokenException
Long userId = claim.getUserId();
```

### 6. 从请求中提取 Token

```java
// 从 Authorization 头提取（自动去除 "Bearer " 前缀），不存在返回 null
String token = TokenUtil.extractToken(request);

// 不存在则抛出 TokenException
String token = TokenUtil.extractTokenOrThrow(request);

// 手动去除 / 拼接 Bearer 前缀
String raw = TokenUtil.removePrefixBearer("Bearer abc");
String full = TokenUtil.appendPrefixBearer("abc");
```

### 7. 生成安全随机 Token

```java
String token = TokenUtil.generateSecureToken();        // 32 字节
String token = TokenUtil.generateSecureToken(64);      // 自定义长度（如 refresh_token 64 字节）
```

## 核心 API

| 类型 | 方法 / 常量 | 说明 |
| --- | --- | --- |
| `TokenUtil` | `generateJwtToken(JWTClaim)` | 签发 JWT |
| `TokenUtil` | `parseJwtToken(String)` | 解析并验证 JWT（失败抛 `TokenException`） |
| `TokenUtil` | `extractToken(HttpServletRequest)` | 从请求头提取 Token（无则 null） |
| `TokenUtil` | `extractTokenOrThrow(HttpServletRequest)` | 提取 Token，无则抛异常 |
| `TokenUtil` | `generateSecureToken()` | 生成 URL-safe 随机 Token |
| `TokenUtil` | `TOKEN_PREFIX` / `removePrefixBearer` / `appendPrefixBearer` | Bearer 前缀处理 |
| `JWTClaim` | 字段 `userId` / `sub` / `exp` / `iat` / `ext` | JWT 载荷对象 |

## 注意事项

- 项目依赖 `fireworks-common-spring-boot-starter`（`JsonUtil`、`Base64Util`、`StrUtil`）。
- JWT 使用对称密钥（HS256）签名，`secret` 需妥善保管，不得泄露。
- 若需实现登录认证拦截，可配合 `fireworks-web-spring-boot-starter` 或自定义 Filter 使用。
