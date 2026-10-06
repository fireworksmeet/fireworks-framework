# fireworks-storage-spring-boot-starter

基于 **AWS S3 SDK v2** 的统一对象存储 Starter。**一套代码、零抽象切换**，兼容所有 S3 协议存储：

- MinIO / SeaweedFS / RustFS / Ceph RGW（自建 / 私有化）
- AWS S3（公有云）
- 阿里云 OSS（**S3 兼容模式**）
- 腾讯云 COS / 华为云 OBS（S3 兼容）

换厂商只需修改 `endpoint`（部分公有云还需调整 `path-style-access` / `region`），**无需更换 SDK、无需重写代码**。

> **设计原则**：单一具体类（`S3StorageService`），不做接口抽象、不做模板方法模式。
> Starter 只做“裸的上传/下载/删除/凭证签发”，**不涉及任何业务状态管理**——孤儿文件、生命周期、声明式 URL 转换等下放到使用方处理。

## 能力边界（明确不做的三件事）

| 不做的事 | 原因 | 替代方案 |
|---|---|---|
| **分片上传（Multipart Upload）** | 需要状态机 + 分片重试策略，属于上层编排职责 | 单个对象受 S3 单次 PUT 的 **5GB 上限**约束；更大文件请自行实现分片 |
| **PostPolicy 表单直传** | AWS SDK Java v2 未提供 `presignPostRequest`，V4 签名需 100+ 行手工实现 | 使用方按需自行实现 |
| **网关防盗链签名** | 签名规则由网关（nginx/OpenCDN/云厂商 CDN）决定，框架无法预知 | 使用方在网关配置与业务代码中成对实现 |

## 快速开始

```yaml
fireworks:
  storage:
    enabled: true
    endpoint: localhost:9000                   # MinIO 示例，换 SeaweedFS/RustFS 只改这里
    region: us-east-1
    access-key: xxxx
    secret-key: xxxx
    secure: false                              # endpoint 未带协议头时生效
    path-style-access: true                    # 见下方「URL 风格」说明
    auto-create-bucket: false                  # Bucket 不存在时自动创建（仅自建服务支持）
    default-bucket: my-bucket                  # 调用时 bucket 传 null 则使用此值
    public-endpoint: https://cdn.example.com   # 可选：CDN / 自定义域名
    enable-bucket-in-url: true                 # 仅在配置 public-endpoint 时生效
```

**多厂商配置对照**：

```yaml
# MinIO / 自建（未配置泛域名 DNS *.your-domain）
endpoint: http://minio.example.com:9000
path-style-access: true

# 阿里云 OSS（S3 兼容）
endpoint: https://s3.oss-cn-shanghai.aliyuncs.com    # 注意 s3. 前缀，原生 oss-<region>.aliyuncs.com 访问 S3 协议可能被拒
region: cn-shanghai                                  # 与 Bucket 地域一致，OSS 不支持跨地域访问
path-style-access: false                             # OSS 要求 Virtual-Hosted Style

# AWS S3
endpoint: https://s3.us-east-1.amazonaws.com
region: us-east-1
path-style-access: false
```

> 框架已在 S3Client 上固定 `chunkedEncodingEnabled(false)`：阿里云 OSS 等实现不支持 `aws-chunked`
> 传输编码，开启会上传失败。本模块所有上传路径都提供确定的 `Content-Length`（未知长度会先缓冲或落盘），
> 因此关闭该编码无功能损失，**无需使用方配置**。

## 核心接口

```java
@Resource
private S3StorageService storageService;
```

### `S3StorageService` — 一个类覆盖所有存储能力

| 方法分类 | 方法 | 说明 |
|---|---|---|
| **上传** | `putObject(...)`（5 个重载） | File / byte[] / InputStream / 显式长度 InputStream；自动 bucket 兜底、objectKey 规范化、Bucket 自动创建 |
| **读取** | `getObject(bucket, key, consumer)` | 函数式读取，自动关闭底层流，防止连接泄漏 |
| | `getObjectAsBytes(bucket, key)` | 取回字节内容（适合小文件） |
| | `headObject(bucket, key)` | 返回 `Optional<StorageFile>`；对象不存在时返回 `Optional.empty()` |
| | `doesObjectExist(bucket, key)` | 判断对象是否存在 |
| **删除** | `deleteObject(bucket, key)` | 删除单个对象 |
| | `deleteObjects(bucket, keys)` | 批量删除 |
| **URL** | `getUrl(bucket, key)` | 公开访问 URL，优先使用 `public-endpoint`（CDN） |
| | `generatePresignedUrl(bucket, key, duration)` | 临时签名 GET URL（私有 Bucket 临时下载） |
| **直传凭证** | `generatePresignedPutUrl(bucket, key, contentType, duration)` | 签发 PUT 预签名直传凭证 |
| | `generatePresignedPutUrl(key, contentType, duration)` | 同上，使用默认 Bucket |

方法命名对齐 AWS S3 SDK v2 与阿里云 OSS SDK，业务方可直接参照官方文档使用。

### 客户端直传

签发时 `bucket` 与 `objectKey` 已 100% 确定，前端无需做任何替换：

```java
// 1. 后端生成唯一 objectKey
String objectKey = ObjectKeyUtil.buildDateKey("avatar/users", "cat.jpg");

// 2. 签发凭证
DirectUploadCredential credential =
        storageService.generatePresignedPutUrl("user-file", objectKey, "image/jpeg", Duration.ofMinutes(15));
```

```java
credential.getUploadUrl();    // 带签名的完整 URL，前端直接请求
credential.getHttpMethod();   // HttpMethod.PUT（枚举，JSON 序列化为 "PUT"）
credential.getHeaders();      // 必须原样携带，增删改任一都会 403 SignatureDoesNotMatch
credential.getExpiration();   // 绝对过期时间（Unix 秒，10 位）
```

## 工具类

### `ObjectKeyUtil` — objectKey 生成与规范化

```java
// 1. 按日期分区（最常用）
//    chat/files/2026/10/01/a1b2c3d4e5f67890.pdf
ObjectKeyUtil.buildDateKey("chat/files", "a.pdf");

// 2. 按实体 ID 隔离（头像、个人文档）
//    avatar/users/10086/c8f9d0a1b2c3.jpg
ObjectKeyUtil.buildEntityKey("avatar/users", 10086L, "a.jpg");

// 3. 按哈希散列目录（海量小文件，防单目录文件过多）
//    docs/receipts/a1/b2/a1b2c3d4e5f67890.pdf
ObjectKeyUtil.buildHashKey("docs/receipts", "a.pdf");

// 辅助方法
ObjectKeyUtil.buildObjectKey("avatar/", "a.jpg");    // 目录 + 文件名拼接
ObjectKeyUtil.getFileExtension("a.JPG");             // jpg（转小写 + 安全过滤）
ObjectKeyUtil.normalizeObjectKey("/avatar//a.jpg");  // avatar/a.jpg
```

设计要点：

- **key 主体一律用 UUID，原始文件名只贡献后缀**——从根上规避中文乱码、特殊字符、超长文件名、同名覆盖
- **失败即抛 `IllegalArgumentException`**，不返回空串等哨兵值，避免非法 key 静默写入
- 校验 S3 协议上限（objectKey ≤ 1024 字节）
- 注意：S3 是扁平命名空间，`/` 与 `..` 无目录语义，本类**不构成路径穿越防护**；但严禁把 objectKey 直接当本地文件路径使用

### `ContentTypeUtil` — 内容类型识别

**数据源为 Tika 的 `tika-mimetypes.xml`**（Freedesktop MIME-info）。与 Spring 内置表
（`mime.types`，实测 788 行）的关键差异（均为实测）：

| 扩展名 | Spring 内置表 | Tika |
|---|---|---|
| `.yaml` / `.yml` | ❌ octet-stream | ✅ `text/x-yaml` |
| `.md` | ❌ octet-stream | ✅ `text/x-web-markdown` |
| `.toml` | ❌ | ❌（Tika 也未收录） |
| `.png` / `.json` / `.csv` | ✅ | ✅ |

> ⚠️ Tika 返回的是**社区惯用名，不等于 RFC 注册名**：`.yaml` → `text/x-yaml`（RFC 9512 注册名是
> `application/yaml`）、`.md` → `text/x-web-markdown`（注册名 `text/markdown`）。要求标准名时请显式声明。

```java
// 只有一个动词：由参数区分“手里有什么”
ContentTypeUtil.getContentType("config.yaml");                 // "text/x-yaml"（查表，不读内容）
ContentTypeUtil.getContentType("cat.png?v=1");                 // "image/png"（自动剥离 query）
ContentTypeUtil.getContentType("a.toml");                      // "application/octet-stream"（Tika 也未收录）
ContentTypeUtil.getContentType(new File("/tmp/upload.bin"));   // "image/png"（文件名是 .bin 也认得出）
ContentTypeUtil.getContentType("photo.heic", inputStream);     // 内容 + 文件名一起判断
ContentTypeUtil.getContentType(inputStream);                   // 只有内容
```

| 重载 | 依据 | 读内容 |
|---|---|---|
| `getContentType(String)` | 文件名后缀查表 | ❌ |
| `getContentType(File)` | 字节签名 + 文件名细化 | ✅（推荐） |
| `getContentType(String, InputStream)` | 字节签名 + 文件名细化 | ✅（仅头部，最多 64KB） |
| `getContentType(InputStream)` | 仅字节签名 | ✅（仅头部，最多 64KB） |

返回类型统一为 `String`，识别不出即 `application/octet-stream`——需要区分“未识别”时与该常量比较即可。
嗅探类重载**不关闭**调用方的流，并在流支持 mark/reset 时检测后复位。

> **能力边界（务必知晓）**：内容嗅探只对**二进制格式**有效（图片 / PDF / 压缩包 / Office / 音视频）。
> YAML、JSON、CSV、Markdown 这类纯文本格式**没有魔数**，任何检测库都只能靠文件名
> ——Tika 对它们同样依赖 `*.yaml` 之类的 glob 匹配。因此**后端自产文件必须显式声明类型**
> （生成 YAML 就传 `application/yaml`），不要指望检测。
>
> **预签名直传的额外约束**：Content-Type 参与 SigV4 签名计算，签名时用什么类型、客户端就必须传什么类型，
> 否则 403 `SignatureDoesNotMatch`——这类场景不要让客户端另行嗅探。

## 配置项清单（`fireworks.storage.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | 是否装配存储模块 |
| `endpoint` | — | **必填**，S3 兼容 Endpoint |
| `region` | `us-east-1` | 阿里云 OSS 建议填地域 ID（如 `cn-shanghai`） |
| `access-key` / `secret-key` | — | **必填** |
| `secure` | `false` | endpoint 未带协议头时生效 |
| `path-style-access` | `true` | 见下方「URL 风格」 |
| `default-bucket` | — | 调用时 bucket 传 null 使用此值 |
| `auto-create-bucket` | `false` | Bucket 不存在时自动创建 |
| `public-endpoint` | — | CDN / 自定义域名，用于 `getUrl` |
| `enable-bucket-in-url` | `true` | `public-endpoint` 场景是否拼 Bucket 路径段 |

## URL 风格（Path-Style vs Virtual-Hosted）——最容易踩的坑

| 取值 | URL 形态 |
|---|---|
| `true` | `https://endpoint/bucket/key` |
| `false` | `https://bucket.endpoint/key` |

**必须与服务端要求一致，配错直接 403**：

- 阿里云 OSS 要求 Virtual-Hosted（配成 Path-Style 会报 `Please use virtual hosted style to access`）
- AWS S3 / 腾讯云 COS / 华为云 OBS 同样以 Virtual-Hosted 为默认
- MinIO / Ceph 等自建服务若未配置泛域名 DNS，需使用 Path-Style
- Virtual-Hosted 下 Bucket 名必须符合 DNS 规范（**不能含下划线 `_`**）

## 不在 Starter 中体现的职责

| 能力 | 推荐实现位置 |
|---|---|
| 孤儿文件清理 | 使用方业务侧 + 对象存储生命周期规则 |
| 业务状态管理（待审核 / 已通过 / 已拒绝） | 使用方业务表（与文件生命周期解耦） |
| 声明式 URL 自动转换 | 使用方自行处理（如 entity 存 objectKey，展示时调 `getUrl`） |
| 图片缩略图 / 格式转换 | CDN / 网关侧按需拼接处理参数 |
| 阿里云 OSS callback 通知 | 该能力是 OSS 私有扩展（S3 协议无此特性），需要时由使用方引入 `aliyun-sdk-oss` |
| 网关防盗链签名 | 使用方（网关配置与签名代码是同一份私有契约，必须成对演进） |

## 模块结构

```
fireworks-storage-spring-boot-starter/
├── pom.xml
└── src/main/java/com/yzm/fireworks/storage/
    ├── StorageAutoConfiguration.java      # 装配 S3Client + S3Presigner + S3StorageService
    ├── StorageProperties.java             # 统一 S3 协议配置
    ├── StorageFile.java                   # 文件元数据 DTO
    ├── DirectUploadCredential.java        # 直传凭证 DTO（内含 HttpMethod 枚举）
    ├── ObjectKeyUtil.java                 # objectKey 生成与规范化
    ├── ContentTypeUtil.java               # 内容类型识别（Tika 表 + magic 嗅探）
    ├── FileNameSupport.java               # 包内共享：文件名归一化（非对外 API）
    ├── exception/
    │   └── StorageException.java          # 统一异常包装
    └── service/
        └── S3StorageService.java          # 唯一服务（上传/下载/删除/URL/直传凭证）
```

**9 个 Java 文件，零接口抽象、零模板方法模式、零冗余配置节点**。

## 依赖说明

- `software.amazon.awssdk:s3` + `software.amazon.awssdk:url-connection-client`
- `fireworks-common-spring-boot-starter`（仅用于 `StringPool` 常量）
- `spring-boot-starter-validation`
- `org.apache.tika:tika-core`（765KB）：提供内容类型映射表与 magic 嗅探。
  **只需 `tika-core`，不含 `tika-parsers-*`**（官方文档：仅 Core 时 `DefaultDetector` 使用 Mime Magic 与 Resource Name 检测）
- 不引入 `aliyun-sdk-oss` / `minio`——避免“多 SDK 多实现”的混乱

## 注意事项

1. **Mock 测试**：`S3StorageService` 是 `final class`，需要 `mockito-inline` 依赖（或 Mockito 5+，默认支持 final 类）。
2. **`StorageFile` 是视图对象**：`url` 是派生字段（由 `public-endpoint` / `enable-bucket-in-url` 决定），**不要持久化**；`size` 为 `null` 表示未知；`lastModified` / `userMetadata` 仅 `headObject` 填充。
3. **直传凭证的单位**：`expiration` 是 **Unix 秒**（10 位）；与前端约定毫秒时请在 API 层显式换算。
4. **时钟偏差**：预签名 URL 的最终校验由存储服务端完成，客户端做的是“预刷新”判断，服务端校验才是裁定者。
5. **断点续传 / 分片上传**：未实现，见「能力边界」。
