# fireworks-token-spring-boot-starter

**凭证编解码层**：把业务声明变成签名的 JWT，把不可信的 JWT 变成经过完整校验的不可变声明集合；同时提供不透明令牌的生成与比对原语。

它不决定谁能访问什么，也不保存任何 token：

| 不提供 | 归属 |
| --- | --- |
| 登录、会话、刷新、撤销列表、踢人下线 | 认证中心 |
| `Authorization` 头 / Bearer 解析、401 / 403 响应 | `fireworks-security` |
| 权限模型与放行判定 | `fireworks-security` |
| JWKS 发布、KMS / Vault 集成 | IdP / 业务实现 `JwtKeyProvider` |

因此本模块**零 Web 依赖**：不引 `spring-web`、不引 `jakarta.servlet-api`，可在 MQ 消费者、定时任务、CLI 中直接使用。

## 能力

| 能力 | 说明 |
| --- | --- |
| JWT 签发 | `JwtIssuer`：声明 + TTL → 紧凑序列化字符串，自动补 `iat` / `exp` / `nbf` / `jti`，header 写 `kid` 与 `typ=at+jwt` |
| JWT 校验 | `JwtVerifier`：长度 → 结构 → 算法 → 密钥 → 签名 → 时间 → 签发方 → 受众 → 业务扩展，逐级短路 |
| 密钥管理 | `JwtKeyProvider`：多密钥、`kid` 路由、签发唯一 / 验签全量、配置化轮换 |
| 算法 | 只保留 `HS256`、`RS256`、`ES256`（见 §算法矩阵） |
| 不透明令牌 | `OpaqueToken`：URL-safe 随机值 + SHA-256 摘要 + 常量时间比对 |
| 失败原因 | `TokenErrorCode` 枚举：统一映射 401，`e.code()` 供日志与指标 |

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.yzm.fireworks</groupId>
    <artifactId>fireworks-token-spring-boot-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

### 2. 最小配置（单服务自签自验）

```yaml
fireworks:
  token:
    issuer: https://auth.example.com
    keys:
      - kid: local-hs256
        algorithm: HS256
        secret-value: ${JWT_HS256_SECRET}
        signing: true
```

> `secret-value` 原文必须不少于 32 字节（HS256 要求 ≥ 256 bit）。
> 对称密钥的验签方同时具备签发能力，只适用于单信任域；多服务请用非对称。

### 3. 签发与校验

```java
@Service
@RequiredArgsConstructor
public class LoginTokenService {

    private final JwtIssuer jwtIssuer;

    public String issueAccessToken(Long userId) {
        return jwtIssuer.issue(TokenSpec.builder()
                .subject(userId.toString())
                .ttl(Duration.ofMinutes(15))          // 省略则用 issuance.default-ttl
                .attribute("roles", List.of("ADMIN"))
                .build());
    }
}
```

```java
@Service
@RequiredArgsConstructor
public class TokenAuthService {

    private final JwtVerifier jwtVerifier;

    public String resolveUserId(String token) {
        try {
            TokenClaims claims = jwtVerifier.verify(token);
            return claims.subject();
        } catch (TokenValidationException e) {
            log.debug("token rejected: {}", e.code());      // 枚举用于日志与指标
            throw new AuthenticationException("invalid token");   // → 401
        }
    }
}
```

`TokenClaims` 字段：`subject` / `issuer` / `audience` / `issuedAt` / `expiresAt` / `notBefore` / `jwtId` / `attributes`。
`kid` 与 `typ` 属于 header：`kid` 在校验阶段用于选密钥，`typ` 固定写 `at+jwt` 供网关识别，都不进结果。

### 4. 认证中心签发、业务服务只验签

认证中心：

```yaml
fireworks:
  token:
    issuer: https://auth.example.com
    keys:
      - kid: auth-2026-10
        algorithm: ES256
        private-key-value: |
          -----BEGIN PRIVATE KEY-----
          ...
          -----END PRIVATE KEY-----
        public-key-value: |
          -----BEGIN PUBLIC KEY-----
          ...
          -----END PUBLIC KEY-----
        signing: true
```

业务服务（只需要公钥，拿不到签发能力）。公钥既可以写值，也可以指向挂载的文件：

```yaml
fireworks:
  token:
    issuer: https://auth.example.com       # 必须与签发方一致，否则 ISSUER_MISMATCH
    keys:
      - kid: auth-2026-10
        algorithm: ES256
        public-key-file: file:/etc/fireworks/jwt-public.pem   # 也可以写 public-key-value: |
```

## 密钥

### 字段

每种材料两个字段，**二选一**；同时配置即启动失败。

| 字段 | 必填性 | 说明 |
| --- | --- | --- |
| `kid` | 必填 | 唯一；写入 header，用于轮换与排障 |
| `algorithm` | 必填 | 该把密钥使用的算法；不同密钥可用不同算法 |
| `private-key-value` | 签发密钥必填 | PKCS#8 PEM 原文 |
| `private-key-file` | 同上 | 私钥文件，内容必须是 PEM |
| `public-key-value` | 验签必需 | X.509 公钥或证书 PEM 原文 |
| `public-key-file` | 同上 | 公钥/证书文件，内容必须是 PEM |
| `secret-value` | 对称算法必填 | 字符串原文 |
| `secret-file` | 同上 | 密钥文件，按原始字节读取并去掉首尾空白 |
| `signing` | 否 | 是否为当前签发密钥；全局最多一把 |

`*file` 的类型是 Spring `Resource`，所以 `file:`、裸绝对路径、`classpath:` 由 Spring 的转换规则处理——框架不解析路径。
**`classpath:` 只允许 `public-key-file`**：私钥打进 jar 会随制品进入镜像仓库，且轮换必须重新发版；`private-key-file` / `secret-file` 用 classpath 直接启动失败。

### 密钥格式：只收 PEM

解析交给 BouncyCastle 的 `PEMParser`，覆盖用户实际会拿到的几种 PEM：

| 格式 | 处理 |
| --- | --- |
| PKCS#8 私钥（`BEGIN PRIVATE KEY`） | 支持（openssl 3 / `keytool` 默认输出） |
| PKCS#1 私钥（`BEGIN RSA PRIVATE KEY`） | 支持（openssl 1.x 的 `genrsa` 默认输出） |
| X.509 公钥（`BEGIN PUBLIC KEY`） | 支持 |
| 证书（`BEGIN CERTIFICATE`） | 支持，直接取其中的公钥 |
| `BEGIN EC PRIVATE KEY`（SEC1） | 拒绝：曲线参数不在 PKCS#8 的算法标识里，转换 `openssl pkcs8 -topk8 -nocrypt` |
| `BEGIN OPENSSH PRIVATE KEY` | 拒绝：RSA/ECDSA 用 `ssh-keygen -p -m PEM -f id_rsa` 导出；**`id_ed25519` 不支持**（EdDSA 不在算法矩阵内） |
| `BEGIN ENCRYPTED PRIVATE KEY` | 拒绝：先解密，框架不接收口令 |
| 裸 DER / Base64 DER / JWK | 拒绝：`openssl pkey -inform DER` 转换 |

【说明】带多个 PEM 块的文件（如证书链）直接失败，避免"到底用了哪张证书"的歧义。
【说明】除了 `PEMParser` 的识别，`KeyParser` 仍保留"把对象类型翻译成可执行建议"的逻辑——库的异常是 `PEMException`，不会告诉你该跑哪条命令。

### 值 与 文件：用字段名区分

```yaml
# 直接写内容
private-key-value: |
  -----BEGIN PRIVATE KEY-----
  ...
  -----END PRIVATE KEY-----

# 或者指向文件
private-key-file: file:/run/secrets/jwt-private.pem
```

不用 `file:` / `base64:` 之类的**字符串前缀**，因为"来源"是一个配置维度，放在字段名里比藏在字符串里清楚：读配置的人一眼能看出这是值还是路径，也不会出现"密钥本身以 `file:` 开头"的歧义。

`*file` 是 Spring `Resource`，路径语义交给 Spring：

| 写法 | 解析 |
| --- | --- |
| `file:/run/secrets/jwt.pem` 或裸绝对路径 | 文件系统 |
| `classpath:jwt-public.pem` | 仅公钥；私钥/对称密钥用即启动失败 |
| `http(s):...` | 能读，但不建议（启动依赖外部服务） |

不需要 `base64:`：环境变量里放 PEM 本身就是多行字符串（K8s 从 Secret 注入 env 时会保留换行），`private-key-value: ${JWT_PRIVATE_KEY}` 直接可用；真要单行传输，用 `openssl base64` 手动转一次即可，不需要框架在校验链路上多做一层解码。

需要从 KMS / Vault 运行期取密钥时，实现 `JwtKeyProvider`（可配合缓存与轮换）。

**KMS 的边界要说清**：`JwtKeyProvider` 返回的是 `SigningKey`，而它持有 JDK `PrivateKey`。因此它只覆盖两种 KMS 用法：

| KMS 用法 | 是否支持 | 说明 |
| --- | --- | --- |
| 从 KMS 读公钥验签 | 支持 | `GetPublicKey` → PEM → `public-key-value` |
| 用 KMS 做信封加密，解出私钥后由本模块签名 | 支持 | 私钥会进入应用内存，仍可用 `JwtKeyProvider` |
| **私钥不出 KMS，由 KMS 的 Sign API 直接签** | **不支持** | 本模块需要一个可调用的 `PrivateKey`；这种模式要把签名动作委托出去，需要额外的签名扩展点，当前版本未提供 |

如果你们的合规要求是"私钥不出硬件"，本模块目前不能直接满足，需要扩展签名接口——这一点不藏着。

### 信任边界

| 来源 | 谁可能读到 | 建议 |
| --- | --- | --- |
| 配置中心（Nacos / Apollo） | 该命名空间所有读权限者 | 公钥、联调密钥；**私钥不建议** |
| 环境变量 | 同容器进程 | 私钥可接受；`private-key-value: ${JWT_PRIVATE_KEY}`，值里保留换行 |
| 文件挂载 | 同上，可配只读 + `0400` | **私钥首选**；`private-key-file: file:/run/secrets/jwt.pem` |
| KMS / HSM | 密钥不出硬件 | 读公钥 / 信封加密解出私钥：实现 `JwtKeyProvider`；"仅 KMS 可签"需签名扩展点，当前不支持（见上） |

私钥进配置中心的代价不是"框架不允许"，而是**签发能力的分发范围变成了配置的读权限范围**。

### 轮换

```text
1. 新增密钥 kid=B（signing=false），公钥先下发到所有验签方
2. 等待一个 access token 生命周期
3. 把 B 设为 signing=true，A 改为 signing=false
4. 等待旧 token 全部过期后移除 A（refresh 凭证的轮换由认证中心负责，不在本模块）
```

轮换期间新旧 `kid` 必须同时可验签。缺少 `kid` 且配置了多把验签密钥时拒绝（`UNKNOWN_KEY`），框架不做"逐个试"。

## 配置项

| 属性 | 默认 | 说明 |
| --- | --- | --- |
| `fireworks.token.enabled` | `true` | 总开关 |
| `fireworks.token.issuer` | — | 签发与校验的 `iss`，必填 |
| `fireworks.token.keys[]` | `[]` | 密钥列表 |
| `fireworks.token.issuance.default-ttl` | `15m` | `TokenSpec.ttl` 缺省值 |
| `fireworks.token.issuance.max-ttl` | `24h` | 超过即拒绝 |
| `fireworks.token.issuance.audience` | `[]` | 默认受众 |
| `fireworks.token.issuance.include-jti` | `true` | 是否自动生成 `jti` |
| `fireworks.token.verification.clock-skew` | `30s` | 时间容忍度 |
| `fireworks.token.verification.audience` | `[]` | 非空时要求交集非空 |
| `fireworks.token.verification.max-token-length` | `8KB` | 超过直接拒绝 |

**开关只有一个：`enabled`**。

| 状态 | 行为 |
| --- | --- |
| `enabled=false` | 整体不装配，`JwtIssuer` / `JwtVerifier` 都不存在 |
| `enabled=true`（默认）且 `keys` 为空 | **启动失败** |
| `enabled=true` 且 `keys` 非空 | 按 `signing` / 公钥装配，并做启动期校验与自检 |

报错内容直接给出两条出路：

```
'fireworks.token.keys' must not be empty;
add at least one key, or disable the module with 'fireworks.token.enabled=false'
```

不做“`keys` 为空就静默跳过”的判断：那样“是否启用”会取决于配置里恰好有没有 `keys`，排障时看不出是漏配还是故意关闭，也会让注入 `JwtIssuer` 的地方变成难以定位的 `NoSuchBeanDefinitionException`。因此引入但暂不使用的工程，显式写 `fireworks.token.enabled=false`。

## 不透明令牌

```java
OpaqueToken token = OpaqueToken.generate();            // 32 字节
OpaqueToken prefixed = OpaqueToken.generate(64, "fw_");// 自定义长度与前缀

tokenRepository.save(userId, prefixed.digest());       // 只存摘要
boolean ok = OpaqueToken.matches(presented, storedDigest);
```

- 至少 16 字节（128 bit）；比对使用 `MessageDigest.isEqual`。
- `value` 只在签发时出现一次，`toString` 不输出。
- 过期、撤销、绑定用户属于认证中心，不在本模块。

## 失败语义

```java
public enum TokenErrorCode {
    MALFORMED, TOKEN_TOO_LARGE, UNSUPPORTED_ALGORITHM, UNKNOWN_KEY,
    SIGNATURE_INVALID, MISSING_CLAIM, EXPIRED, NOT_YET_VALID,
    INVALID_ISSUED_AT, ISSUER_MISMATCH, AUDIENCE_MISMATCH;
}
```

所有错误码都表示"这个 token 不被接受"，上层统一映射 **401**；`e.code()` 用于日志、指标与排障，不做"401 还是 503"的分类——状态码映射属于 security 层。

## 适用范围（先看这一节）

**本模块只做"本框架签发、本框架校验"的闭环**：认证中心用 `JwtIssuer` 签发，业务服务用 `JwtVerifier` 校验同一套配置产出的 token。

| 需求 | 用什么 |
| --- | --- |
| 校验本框架签发的 token | 本模块 `JwtVerifier` |
| 对接 Keycloak / Auth0 / OIDC 等外部 IdP | `spring-boot-starter-oauth2-resource-server`（`issuer-uri` / `jwk-set-uri`），本模块不做 |
| 对外发布 JWKS 供他人验签 | 认证中心的职责；本模块不生成 JWKS |
| 校验非 JWT 的不透明令牌 | 认证中心 + 业务缓存，本模块只提供 `OpaqueToken` 原语 |

`fireworks.token.issuer` 是**单一值**，校验时要求 `iss` 与之严格相等，不等即 `ISSUER_MISMATCH`。因此**本模块天然只接受这一个 issuer 签发的 token**——这不是限制，而是边界声明。对端换了 issuer，请改用 `oauth2-resource-server` 的 JWKS 模式，而不是在本模块里堆 issuer 列表。

注意 `iss` 本身是明文、可伪造，它之所以能起到"只认自己"的作用，是因为必须**与密钥同时成立**：`iss` 对但签名不对 → 拒绝；签名对但 `iss` 不对 → 也拒绝。单一条件都不够。

"支持多种算法"的意思是**不同部署可以选不同算法**（Java、非 Java、网关的兼容性不同），不是让你在一个部署里配一堆。同一个部署里出现两种算法只有一个正当理由：**算法迁移窗口**（旧 token 未过期）。`kid` 已经把来源区分开，所以两种算法并存不会互相干扰。

## 设计要点

- **不提供 `decode()`**：解码不等于校验，"只解码不验签"是最常见的 JWT 事故来源。
- **算法由每把密钥声明**：`kid` 命中后仍校验 `header.alg == key.algorithm()`，header 不能反过来决定用什么算法。因此不需要额外的"允许算法"白名单——配置了哪些密钥，就允许哪些算法。
- **签发唯一、验签全量**：只有 `signing: true` 的那把用于签发；全部密钥用于验签，因此同一部署可同时接受不同算法。
- **启动自检**：配置了签发密钥且有对应公钥时，启动期签发一个 1 秒有效期的探针令牌并立即校验，提前暴露算法错配与公私钥不配对。
- **失败分类**：凭证坏了返回 401，依赖坏了返回 503，不混用。
- **密钥材料不进日志**：`SigningKey` / `VerificationKey` 的 `toString` 只输出 `kid` 与算法。

## 算法矩阵

只有三种，一种一个理由：

| 算法 | 签发需要 | 验签需要 | 用在什么场景 |
| --- | --- | --- | --- |
| `HS256` | `secret` | 同一个 `secret` | 单服务自签自验、联调；验签方同时具备签发能力 |
| `RS256` | RSA 私钥 | RSA 公钥 | 兼容性最好：任何网关、任何语言的 JWT 库都支持 |
| `ES256` | EC 私钥（P-256） | EC 公钥 | 推荐默认：密钥小、签名 64 字节（`RS256` 是 256 字节） |

**不提供** `RS384/512`、`PS*`、`ES384`、`EdDSA`：

- `384` / `512` 在 JWT 里没有实际收益（token 更大、验签更慢），只是多一倍配置项与测试矩阵；
- `PS*` 与 `RS256` 的差别是填充方案而非安全等级，除非上游明确要求，用 `RS256` 兼容性更好；
- `EdDSA` 在 Nimbus 10.x 需要额外的 Google Tink 依赖，为一个算法引入第三方实现不划算。

需要这几种时，openssl / 网关侧转换或改用外部 IdP，本模块不为此增加分支。

## 与其它模块

| 模块 | 关系 |
| --- | --- |
| `fireworks-security` | 消费方：由其把 `JwtVerifier` 适配为 Spring Security 的 `JwtDecoder`，并负责 Bearer 提取与 401 / 403 响应 |
| `fireworks-rest-client` | 服务间调用可用短 TTL 令牌，自行拼 `Bearer ` 前缀 |
| `fireworks-id` | 需要业务化 `jti` 时提供 `Supplier<String>` 覆盖 |

## 版本策略

- `nimbus-jose-jwt` 版本只在 `fireworks-dependencies` 声明，当前 **10.9.1**（修复 `CVE-2025-53864` 深嵌套 JSON DoS）。
- 各 starter 不写版本号，也不用 `dependencyManagement` 私自覆盖，避免同一 classpath 出现两套 JOSE 实现。
- 安全公告按"72 小时内评估、patch 级升级"处理。
