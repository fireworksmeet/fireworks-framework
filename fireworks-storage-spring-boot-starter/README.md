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
      imgproxy:                   # 可选，配置后 getFileUrl(bucket, objectName, ImageOptions) 才能在 MinIO 场景下生效
        endpoint: http://imgproxy:8080
        key:      # 十六进制签名密钥，需与 imgproxy 服务端 IMGPROXY_KEY 一致；留空则使用 insecure 模式（仅建议本地开发）
        salt:     # 十六进制签名盐值，需与 imgproxy 服务端 IMGPROXY_SALT 一致

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
  - `getFileUrl(bucket, objectName)`：原图/原文件的固定访问地址。
  - `getFileUrl(bucket, objectName, ImageOptions options)`：薄封装的图片处理地址生成（按需实时处理，不会在存储端落盘生成额外文件）。
    `options` 为 `null` 时等价于 `getFileUrl(bucket, objectName)`，返回原图地址；不为 `null` 时按 `ImageOptions` 中的
    `width`、`height`、`format` 字段生成处理后的访问地址，三个字段可任意组合（只缩放、只转格式、或两者同时）：
    - 只指定 `width` 或只指定 `height`：等比缩放，缺失的一边按原图宽高比自动计算，不裁剪。
    - 同时指定 `width` 和 `height`：等比缩放后裁剪填充到目标宽高（旧版 `getThumbnailUrl` 的行为）。
    - 指定 `format`：转换为目标格式，可与缩放任意组合，也可单独使用（原图尺寸不变，仅转格式）。
    - Aliyun OSS：基于内置图片处理服务，拼接 `x-oss-process=image/...` 参数。
    - MinIO：自身没有图片处理能力，需要额外部署 [imgproxy](https://imgproxy.net) 并配置
      `fireworks.storage.minio.imgproxy`，该方法会生成 imgproxy 可识别的签名 URL，源图地址沿用 `getFileUrl` 的拼接规则。
      未配置 `imgproxy` 时传入非空 `options` 调用会抛出异常，请确认已部署并正确配置。

- `DirectUploadService`：
  - `getUploadCredential`：预签名 PUT 直传凭证，前端直接 PUT 整个文件。
  - `getUploadCredentialByPostPolicy`：表单直传(PostPolicy)凭证，前端以 `multipart/form-data` POST 上传。
    - Aliyun OSS：返回的 `formData.key` 已包含 `${filename}` 占位符，OSS 会自动替换为真实文件名；
      配置了 `aliyun.callback-url` 时会自动携带 `callback` 表单字段，OSS 上传成功后会回调该地址。
    - MinIO：S3 协议不支持 `${filename}` 占位符，返回的 `formData.key` 仅为目录前缀，
      前端提交表单前需要在该前缀基础上拼接真实文件名（满足 `startsWith` 策略条件即可）。

## 注意事项

- `minio.auto-create-bucket=true` 时，上传前会自动创建不存在的 Bucket；该选项只存在于 MinIO 配置节点下，
  因为 Aliyun OSS 权限模型不支持普通 RAM 用户自动创建 Bucket，所以不在 Aliyun 配置下提供对应字段，
  避免出现"配了但不生效"的配置项。
- Aliyun File 上传产生的 `<file>.ucp` checkpoint 文件与源文件同目录，上传完全成功后 SDK 会自动清理；
  上传失败/中断会保留该文件，用同样的本地文件路径重新调用 `uploadFile` 即可从断点处继续，不会重新上传已完成的分片。
- 两个 Provider 实现统一抛出 `StorageException`，业务代码无需感知底层 SDK 的异常类型。