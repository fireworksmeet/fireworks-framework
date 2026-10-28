# 0006. Token 验签边界：只做自签自验，外部 IdP 由业务接入

- **状态**：已接受
- **日期**：2026-10-21

## 背景

`fireworks-token` 的验签设计容易陷入摇摆：既想支持多算法、多 issuer、JWKS，又想收敛到单一算法。表面上是"配置项多不多"的争论，实质是**没有先定一件事：这个模块要不要校验别人签发的 token**。

两个模型的信任来源不同：

| | 闭合域 | 开放域 |
| --- | --- | --- |
| token 由谁签发 | 本框架 | 任意 IdP |
| 密钥从哪来 | 本地配置 | 远端 JWKS（随时轮换） |
| 能否强约束 | 能：`iss` 精确匹配、`kid` 必须命中、算法与密钥绑定 | 不能：必须接受对方声明的算法与轮换节奏 |
| 依赖 | 无网络 | HTTP 客户端 + 密钥缓存 |

模型不定，每个配置项都能被论证成"可能有用"（`allowed-algorithms`、`required-claims`、`typ` 校验、refresh 维度）——这不是细节问题，而是**边界没划**。

## 决策

1. 【强制】**token 模块只做闭合域**：只接受本框架签发、`iss` 等于配置值、`kid` 命中本地配置密钥的 token。
2. 【禁止】token 模块实现 JWKS 拉取、多 issuer 路由、密钥发现与缓存、远端元数据驱动的算法白名单；不引入 HTTP 客户端。
3. 【强制】**外部 IdP 不由框架内任何模块实现**：Keycloak / Auth0 / OIDC / 非本框架签发者，由业务自行引入 `spring-boot-starter-oauth2-resource-server`（`JwtDecoder` + `issuer-uri` / `jwk-set-uri`）。`fireworks-security` 只提供 `SecurityUserResolver` 等 SPI 承接"认证结果 → 当前用户"的映射，**不内置 `JwtDecoder`、不提供 `mode` 开关**；两套验签入口**不复用代码**。
4. 【强制】校验侧配置只保留四项：`fireworks.token.issuer` 与 `fireworks.token.verification.audience` / `clock-skew` / `max-token-length`。`exp` / `iat` / `sub` 硬编码为必需声明（`nbf` 可选）；不提供 `max-age`（签发侧 `max-ttl` 已限制）。
5. 【强制】header 固定写 `typ: at+jwt`（RFC 9068），但**不据此做任何校验**：本模块不签发 refresh token，用途区分由 claim 表达。

## 理由

1. **一个模块不能同时服务两种信任模型**。本地密钥可以做"属性强绑定"（算法挂在密钥上、iss 精确匹配），远端 JWKS 做不到——对方随时换算法、换 kid。把两者塞进一个 `JwtVerifier`，结果是两边的约束都变松。
2. **Spring Security 的 JWKS 实现成熟**，`NimbusJwtDecoder` + 缓存 + issuer 校验已经覆盖开放域，业务直接用它即可；在框架内重写只会得到功能更少、还要维护两套的版本。
3. **闭合域才能真正砍配置**：算法由密钥决定，不需要独立的算法白名单；`required-claims` 由签发侧保证；refresh 不在本模块职责内。这些配置项只在"兼容外部 token"的假设下才成立，边界一定，它们就没有存在理由。

## 后果

- **正面**：token 模块零网络依赖、失败可预测（全部在启动期暴露配置错误）；校验侧配置面稳定在四项。
- **注意**：本框架**不提供**外部 IdP 的接入实现——`fireworks-security` 明确"认证方式由业务决定，框架不提供 `mode` 开关"，只负责装配、授权转换与错误响应；业务需自行引入 `spring-boot-starter-oauth2-resource-server`，并通过 `SecurityUserResolver` 把认证结果映射为当前用户。
- **注意**：如果将来真的需要"本模块也验外部 token"，必须**新开 ADR 推翻本条**，而不是新增一个配置项悄悄扩展边界。
- **注意**：`iss` 必须按环境区分（`https://auth.test` vs `https://auth`），否则测试/生产共用密钥时该检查失效——这是配置纪律，框架无法强制。

## 相关

- `fireworks-token-spring-boot-starter/README.md` §适用范围
- `fireworks-security-spring-boot-starter/README.md` — 定位（认证方式由业务决定、不提供 `mode`）
- `fireworks-security-spring-boot-starter` 的 `SecurityUserResolver` — 认证结果到当前用户的映射点
