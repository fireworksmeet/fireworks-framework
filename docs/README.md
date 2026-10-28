# fireworks-framework 文档

框架层规范的**唯一权威出处**。业务仓库不再重复编写这里的任何主题，只保留本仓库特有的规范，并链接到本文档。

## 一、与其他文档的分工

| 文档 | 回答的问题 | 权威性 |
| --- | --- | --- |
| `xxx-starter/README.md` | 这个组件**怎么用**（依赖、配置项、示例） | 与代码同源，**永远最新** |
| `docs/capabilities.md` | 框架**提供什么、不提供什么** | 索引，可能滞后，以源码为准 |
| `docs/conventions/*` | 写这类代码**必须遵守什么** | 唯一出处 |
| `docs/decisions/*` | 这些约定**为什么这么定** | 历史证据，不随现状更新 |
| 业务仓库的 `AGENTS.md` | AI 代理在本仓库**怎么工作** | 业务仓库自己维护 |

【强制】同一个主题只允许有一个出处。其他文档需要提及时**只放一句引用**，不复述内容。

## 二、文档地图

| 文档 | 主题 |
| --- | --- |
| [`capabilities.md`](capabilities.md) | 框架能力索引：有哪些 starter、提供什么、**不提供什么** |
| [`conventions/java.md`](conventions/java.md) | Java 语言与代码风格（状态机、枚举、Lombok、Optional、集合与 Stream、常量、注释、格式） |
| [`conventions/spring-boot.md`](conventions/spring-boot.md) | 设计原则、分层与依赖方向、命名、事务、并发与一致性、运行时（DI / 配置 / 生命周期 / AOP / 异步 / 定时 / 缓存 / 外部调用）、安全与性能 |
| [`conventions/api.md`](conventions/api.md) | HTTP 契约、请求响应形态、错误码、规模上限、分页契约、序列化契约、接口版本演进 |
| [`conventions/database.md`](conventions/database.md) | 设计流程、建表、索引与约束、SQL、写路径、迁移与变更安全（PostgreSQL / MySQL 方言差异见其「方言差异总表」） |
| [`conventions/mybatis.md`](conventions/mybatis.md) | MyBatis / MyBatis-Plus 的 SQL 书写、Mapper 注册与 MyBatis-Plus 特性约定 |
| [`conventions/idempotency.md`](conventions/idempotency.md) | 幂等（幂等键、幂等表、执行模型、批量与清理） |
| [`conventions/reliability.md`](conventions/reliability.md) | 事务边界、最终一致性、可靠消息（Outbox）、补偿与对账 |
| [`conventions/observability.md`](conventions/observability.md) | 日志级别与内容、降噪、指标、链路、健康检查 |
| [`conventions/testing.md`](conventions/testing.md) | 测试分层、必须自动化的行为、命名 |
| [`decisions/`](decisions/README.md) | 架构决策记录（ADR） |

> 【说明】上表即当前**全部**规范文档；新增主题时在本表登记，并遵守"一主题一处"（§一）。

## 三、命名约定

### 3.1 文件名

- `conventions/` 下**只放主题名词**：全小写、`kebab-case`、**不带** `-convention` / `-style` / `-design` 等后缀——目录已经表达了文档性质，后缀是冗余。
- 用**名词**而非形容词：`idempotency`（不是 `idempotent`）、`observability`。
- **一文件一主题**；主题过大时**拆文件**，不要在一个文件里堆互不相关的章节。
- 反例（评审直接判不合格）：

| 反例 | 问题 |
| --- | --- |
| `SpringBoot.md` | 驼峰、大小写不规范 |
| `java-style.md` | `-style` 后缀冗余 |
| `infrastructure.md` | "基础设施"词义过宽，看名字判断不出该放什么内容 |
| `framework.md` | 放在框架仓库里零信息量（这里什么不是 framework） |

### 3.2 文档结构

- 一个文件**只有一个一级标题**（文档标题）；章节用 `## 一、…`，小节用 `### 1.1 …`。
- 条款行首统一使用四档标记：**【强制】（违反即代码问题）、【推荐】（默认遵守）、【禁止】（原则上不得使用）、【说明】（背景解释）**。

### 3.3 ADR

- 路径：`docs/decisions/NNNN-kebab-english-title.md`，编号 4 位、从 `0001` 连续递增。
- **文件名用英文 kebab、文档内标题用中文**，避免出现 `001-枚举字段存储SMALLINT.md` 这类中英混排。
- 保留 `状态` / `日期` 字段（属于 ADR 模板，不受下方"不写版本"约束）。

## 四、写作约定

- 只描述**应当如何**，不描述"如何从现状改过来"——迁移过程属于 PR / 提交说明，不属于规范。
- **不写版本号、不写变更记录**：内容调整直接改正文。
- **只写判据与反例**，不复述推导过程；"为什么"归 ADR。
- **不写协作约定**："AI 代理怎么工作"归各仓库的 `AGENTS.md`，不混入规范正文。
- **不绑定技术选型**：框架不规定业务用哪个 ORM / 哪个数据库。文档里出现具体技术时，必须写清它属于**框架内置支持**还是**业务自选**。
