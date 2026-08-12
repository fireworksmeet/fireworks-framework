# fireworks-storage-spring-boot-starter

统一对象存储 Starter，支持 Aliyun OSS 和 MinIO 两种 Provider，业务代码通过 `StorageService` / `DirectUploadService`
两个接口操作存储，无需关心底层具体厂商实现。

## 快速开始

```yaml
fireworks:
  storage:
    enabled: true
    provider: minio   # 可选: minio / aliyun
    part-size: 5242880              # 5MB，分片大小，直接传给 OSS/MinIO SDK 自身的分片能力，不是本框架自己实现的分片
    direct-upload-max-size: 1073741824  # 表单直传(PostPolicy)允许的最大文件大小，1GB
    public-endpoint:                # 可选，CDN/自定义域名

    minio:
      endpoint: localhost:9000
      access-key: xxxx
      secret-key: xxxx
      secure: false
      auto-create-bucket: false   # MinIO 专属：上传时 Bucket 不存在则自动创建

    aliyun:
      endpoint: oss-cn-shanghai.aliyuncs.com
      access-key: xxxx
      secret-key: xxxx
      callback-url:    # 可选，配置后表单直传成功会回调该地址通知文件信息
      multipart-concurrency: 4   # Aliyun 专属：断点续传上传的分片并发线程数（MinIO SDK 不支持该配置）
```

> **⚠️ 配置项变更提示**：`multipart-size` 已拆分为顶层的 `part-size`（分片大小，OSS/MinIO 通用）
> 和 `aliyun.multipart-concurrency`（并发数，仅 Aliyun 专属，不在顶层）；`multipart-threshold` 已彻底移除——
> 本框架不再自己实现任何分片逻辑，分片与否完全交给 OSS/MinIO SDK 自身判断。

## 核心接口

```java
@Resource
private StorageService storageService;

@Resource
private DirectUploadService directUploadService;
```

- `StorageService`：服务端上传/删除/获取访问地址。
  - 上传策略完全交给各 Provider SDK 自身实现，本框架不再手写任何分片/并发逻辑：
    - **File 上传**：Aliyun 使用 `ossClient.uploadFile()`（断点续传上传），SDK 自行判断是否分片、自行并发，
      并通过本地 `<file>.ucp` checkpoint 文件支持断点续传——上传中断后重新调用同一文件可自动跳过已完成的分片；
      MinIO 使用 `uploadObject()`，SDK 内部自动处理大文件分片，但不支持断点续传。
    - **Stream(InputStream) 上传**：Aliyun 使用最基础的简单上传（`putObject`），**单次硬上限 5GB**
      （OSS SDK 没有对 InputStream 的自动分片能力，断点续传依赖可重复读取的本地文件，流式数据不具备这个条件）；
      超过 5GB 的流请先落盘为本地文件再改用 File 上传。MinIO 的流式上传内部仍会自动分片，无此限制。
  - `getFileUrl(bucket, objectKey)`：文件的固定访问地址（原图/原文件）。本框架**只输出原图地址**，
    不提供任何图片缩放/格式转换的生成方法。图片处理（缩略图等）遵循"后端只存原图、前端/网关按需处理"的
    云原生解耦原则：前端拿到原图地址后，如需缩略图，直接在 URL 上拼接处理参数由网关/云存储实时处理
    （如阿里云 OSS 的 `?x-oss-process=image/resize,...`、MinIO 前端的 imgproxy），后端业务无需感知。
    相关图片处理 API（`ImageOptions`、`ImageFormat`、imgproxy 签名构建）已从本模块移除。

- `DirectUploadService`：
  - `getUploadCredential`：预签名 PUT 直传凭证，前端直接 PUT 整个文件。
  - `getUploadCredentialByPostPolicy`：表单直传(PostPolicy)凭证，前端以 `multipart/form-data` POST 上传。
    - `objectKey` 为后端生成的**完整对象路径（含文件名）**，会被精确写入 `formData.key`，
      云厂商（OSS/S3）收到请求后直接以该值作为存储路径并忽略前端文件原名。
      因此 `objectKey` 与 `displayUrl` 在签发时即可 100% 确定并精确返回，前端无需做任何文件名替换。
    - 后端生成 `objectKey` 时可使用 UUID / SnowFlake ID / MD5 等唯一标识作为文件名，
      彻底规避特殊字符、中文乱码、超长文件名导致的存储异常，以及不同用户上传同名文件造成的相互覆盖。
    - Aliyun OSS：配置了 `aliyun.callback-url` 时会自动携带 `callback` 表单字段，OSS 上传成功后会回调该地址。
    - MinIO：`formData.key` 同样写死为完整 `objectKey`，PostPolicy 的 `startsWith` 条件使用其所在目录前缀。
  - 两种凭证都会返回 `objectKey` 与 `displayUrl` 两个字段：
    - `objectKey`：文件在云存储中的唯一相对路径/标识，是业务落库的核心字段。
    - `displayUrl`：前端上传成功后的完整回显 URL（带 CDN 域名）。
    - 预签名 PUT 与表单直传(PostPolicy)均返回精确值，无需前端替换占位符。

  ```java
  // 示例：后端生成唯一 objectKey，表单直传
  String objectKey = "temp/avatar/202608/" + UUID.randomUUID() + ".jpg";
  UploadCredential credential = directUploadService.getUploadCredentialByPostPolicy(bucket, objectKey, duration);
  // credential.getFormData().get("key")       == "temp/avatar/202608/xxxxx.jpg"
  // credential.getObjectKey()                == "temp/avatar/202608/xxxxx.jpg"
  // credential.getDisplayUrl()                == "https://cdn.com/temp/avatar/202608/xxxxx.jpg"
  ```

## ObjectKey 生成工具（`ObjectKeyUtil`）

业务层在上传/直传时通常需要按规则生成 objectKey，框架提供 `com.yzm.fireworks.storage.model.util.ObjectKeyUtil`
对外工具类，覆盖三种最常见模式，统一处理目录归一化、后缀安全过滤与唯一标识生成：

```java
// 1. 按业务模块 + 日期分区（最常用）
//    chat/files/2026/08/11/a1b2c3d4e5f6.pdf
ObjectKeyUtil.buildDateKey("chat/files", "a.pdf");

// 2. 按用户/实体 ID 隔离（头像、个人文档）
//    avatar/users/10086/c8f9d0a1b2c3.jpg
ObjectKeyUtil.buildEntityKey("avatar/users", 10086L, "a.jpg");

// 3. 按 Hash 散列目录（超大规模海量小文件，防单目录过多）
//    docs/receipts/a1/b2/a1b2c3d4e5f67890.pdf
ObjectKeyUtil.buildHashKey("docs/receipts", "a.pdf");

// 辅助：目录 + 文件名拼接、安全后缀提取
ObjectKeyUtil.buildObjectKey("avatar/", "a.jpg");  // avatar/a.jpg
ObjectKeyUtil.getFileExtension("a.JPG");           // jpg（转小写、安全过滤）
```

## 声明式 URL 输出（`@StorageUrl`）

实体字段只需保存 objectKey，序列化输出时可通过 `@StorageUrl` 注解自动转换为可访问 URL，无需手动拼 URL：

```java
public class UserVO {
    private String avatarPath;        // 数据库存 objectKey，如 "avatar/users/10086/xxx.jpg"

    @StorageUrl(source = "avatarPath", type = UrlType.PRESIGNED, durationSeconds = 7200)
    private String avatarUrl;         // 序列化时自动填充为预签名 URL
}
```

**注解位于 common 模块**（`com.yzm.fireworks.common.annotation.StorageUrl`、`com.yzm.fireworks.common.enums.UrlType`），
业务分层中的 api 层 DTO 字段可直接使用，**无需依赖 storage 模块**。

注解参数：

| 参数 | 说明 | 默认值 |
| --- | --- | --- |
| `source` | 关联的 ObjectKey 源字段名；为空表示当前字段本身即 objectKey | 空 |
| `bucket` | 目标桶，为空用配置的默认桶 | 空 |
| `type` | URL 类型：`PUBLIC` / `PRESIGNED` / `GATEWAY` | `PUBLIC` |
| `durationSeconds` | 签名有效时长（秒） | `7200` |
| `delimiter` | 多图分隔符（逗号分隔的多个 objectKey） | `,` |

> **实现机制**：`@StorageUrl` 只是声明在 common 的标记注解，真正的 URL 解析逻辑 `StorageUrlJsonSerializer`
> 留在 storage 模块。引入 storage 后，`StorageAutoConfiguration` 自动注册 `StorageUrlModule`
> （基于 `BeanSerializerModifier`）把标注 `@StorageUrl` 的字段动态绑定到该序列化器，支持集合、数组、
> 分隔符字符串与单个 Key 的多态解析。**若应用未引入 storage，注解字段将原样输出**，天然向后兼容。

## 孤儿文件处理

**问题场景**：前端获取直传凭证后完成了上传，但后续没有提交业务表单，文件就会成为"孤儿文件"，
日积月累导致存储空间被无用文件占用。

**解决思路**：框架提供「待确认注册表 + 清理器」。业务层在发放凭证后登记一条"待确认"记录，
业务处理成功（表单落库）后调用 `confirm` 确认；超过配置时长仍未确认的记录，由清理器扫描并删除对应对象。

**注册表实现**：默认基于 **Redis ZSet**（键 `fireworks:storage:orphan:pending`，member 为 `bucket|objectKey`，
score 为截止时间戳）。利用 ZSet 按 score 有序 + Redis 单线程原子性，在高并发与多实例部署下
登记/确认/过期扫描均安全一致。

**定时调度**：框架**不内置任何定时任务**，只提供 `OrphanFileCleaner#cleanExpired()` 清理方法，
由业务侧通过 XXL-Job、PowerJob 或 Spring `@Scheduled` 等调度器周期触发，多实例由调度平台保证单实例执行。

### 接入（签发时自动登记 + 确认）

默认配置 `auto-mark-pending=true` 时，`DirectUploadService` 在**签发凭证的同时自动登记**待确认记录，
业务方无需手动调用 `pending`，从根本上避免漏写导致孤儿文件保护失效。业务方只需在业务处理成功后确认一次：

```java
@Resource
private DirectUploadService directUploadService;   // 签发凭证（内部已自动登记待确认记录）
@Resource
private OrphanFileGuard orphanFileGuard;           // 业务确认

// 1. 签发凭证（内部已自动登记待确认记录，TTL 用 default-ttl，默认 1 天）
UploadCredential credential = directUploadService.getUploadCredential(bucket, objectKey, Duration.ofHours(1));
// 表单直传：directUploadService.getUploadCredentialByPostPolicy(bucket, objectKey, duration)

// 2. 业务处理成功（如表单提交落库）后确认文件已被正常使用
orphanFileGuard.confirm(bucket, credential.getObjectKey());
```

若需要更精确的待确认 TTL（覆盖凭证有效期 + 业务处理耗时），或关闭自动登记，可自行调用：

```java
orphanFileGuard.pending(bucket, objectKey, Duration.ofHours(2)); // 手动登记（仅 auto-mark-pending=false 时需要）
```

> `objectKey` 在签发凭证时即由后端精确生成，业务侧直接用 `credential.getObjectKey()` 确认即可，无需额外处理文件名。
> 自动登记仅在「孤儿清理能力启用（已引入 Redis）且 `auto-mark-pending=true`」时生效；
> 未引入 Redis 时，签发凭证仅完成上传，不进行自动登记（文件存储功能不受影响）。

### 声明式自动确认（推荐）

显式调用 `orphanFileGuard.confirm(...)` 仍易被业务漏写。为此提供 **`@AutoConfirmFile`** 声明式注解：
标注在 Service 保存方法上，方法执行成功后自动从**方法参数**解析文件路径并**批量确认**，事务回滚时不会误确认。

实现采用与 OptLog / 分布式锁一致的 **`MetadataSource + Advisor`** 三件套模式（基于
`AbstractAnnotationMetadataSource` + `MetadataSourcePointcut`）：注解元数据解析结果缓存、支持泛型桥接方法，
首次命中后后续 0 反射。

```java
// 最常见：单个文件路径在入参 DTO 上（前端直传后把对象名传给后端保存）
@AutoConfirmFile(objectKey = "#userForm.avatarPath")
public User save(UserForm userForm) {
    // ... 业务保存，落库 avatarPath
    return user;
}

// 批量：一组对象名（List<String>）作为参数
@AutoConfirmFile(objectKey = "#args[0]")
public void savePics(List<String> picUrls) { ... }

// 显式指定桶名（SpEL 或回退到配置 default-bucket）
@AutoConfirmFile(bucket = "#userForm.bucket", objectKey = "#userForm.avatarPath")
public void save(UserForm form) { ... }
```

**`objectKey` 必填（SpEL 驱动）**：通过 SpEL 从方法参数精确定位文件路径（参数名如 `#userForm.avatarPath`、
位置访问如 `#args[0]`），避免盲目扫描造成误确认。**SpEL 变量**：方法参数名、`#args`（参数数组）。

**结果规约**：SpEL 解析结果需为 `String` 或 `String` 的 `Collection`/数组，框架会展开为待确认记录并批量确认；
其他类型会忽略并告警（不反射猜测）。

**桶名兜底**：`String` 路径不携带桶名，依次使用注解 `bucket` SpEL、配置
`fireworks.storage.orphan-cleanup.default-bucket`；仍无法确定则跳过该条并告警（需保证与登记时的桶一致才能匹配）。

**事务语义**：默认在事务提交（`afterCommit`）后确认；方法不在事务中则执行成功后立即确认。
依赖 `spring-boot-starter-aop` 与 `spring-tx`（storage 以 `optional` 引入），实际使用 `@AutoConfirmFile` 时建议在业务工程显式引入这两个依赖。

### 定时清理（业务侧触发）

```java
@Resource
private OrphanFileCleaner orphanFileCleaner;

// 例：Spring @Scheduled 每 1 小时清理一次
@Scheduled(fixedDelayString = "PT1H")
public void cleanOrphanFiles() {
    int deleted = orphanFileCleaner.cleanExpired();
}
```

### 配置

```yaml
fireworks:
  storage:
    orphan-cleanup:
      enabled: true                  # 是否启用清理能力，默认 true
      auto-mark-pending: true        # 签发凭证时是否自动登记待确认记录，默认 true
      redis-key: fireworks:storage:orphan:pending   # Redis ZSet 键名，多环境可隔离
      default-bucket: user-file      # @AutoConfirmFile 解析到对象无桶名时的兜底桶，默认空
      default-ttl: 1d                # 待确认记录默认有效时长，默认 1 天
      batch-size: 100                # 单次扫描最多清理的记录数，默认 100
      buckets:                       # 允许删除存储文件的桶白名单，留空表示不限制；
                                     # 白名单外的桶即使登记过期也只移除 Redis 登记、不删文件（防误删、防积压）
        - user-file
```

### 依赖说明

孤儿清理的 Redis 实现依赖 `fireworks-redis-spring-boot-starter`（以 `optional` 引入）。
引入 Redis 后自动注册 `RedisOrphanRegistry`（基于 Redis ZSet 的默认实现）；不引入 Redis 时不注册，孤儿清理功能整体不生效。
如需替换注册表实现，实现 `OrphanRegistry` 接口并声明为 `@Bean` 即可自动取代默认实现。

## 注意事项

- `minio.auto-create-bucket=true` 时，上传前会自动创建不存在的 Bucket；该选项只存在于 MinIO 配置节点下，
  因为 Aliyun OSS 权限模型不支持普通 RAM 用户自动创建 Bucket，所以不在 Aliyun 配置下提供对应字段，
  避免出现"配了但不生效"的配置项。
- Aliyun File 上传产生的 `<file>.ucp` checkpoint 文件与源文件同目录，上传完全成功后 SDK 会自动清理；
  上传失败/中断会保留该文件，用同样的本地文件路径重新调用 `uploadFile` 即可从断点处继续，不会重新上传已完成的分片。
- 两个 Provider 实现统一抛出 `StorageException`，业务代码无需感知底层 SDK 的异常类型。