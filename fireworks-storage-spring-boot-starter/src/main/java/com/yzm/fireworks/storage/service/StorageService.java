package com.yzm.fireworks.storage.service;

import com.yzm.fireworks.storage.model.dto.StorageFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 核心文件存储服务接口（专注于常规文件的 CRUD 与访问 URL 获取）
 */
public interface StorageService {

    // ─── 上传 (Upload) ─────────────

    /**
     * 使用配置的默认 Bucket 上传本地文件
     */
    StorageFile upload(String objectKey, File file, String contentType) throws IOException;

    /*
     * 上传本地文件（最推荐：自动获取 File Length，支持零拷贝/高效分段）
     */
    StorageFile upload(String bucket, String objectKey, File file, String contentType) throws IOException;

    /**
     * 兼容未显式提供 contentLength 的 InputStream 场景（传 -1 代表未知长度）
     */
    StorageFile upload(String bucket, String objectKey, InputStream inputStream, String contentType);

    /**
     * 上传已知大小的输入流（推荐：显式指定 length，避免 SDK 内存/磁盘缓存）
     */
    StorageFile upload(String bucket, String objectKey, InputStream inputStream, long contentLength, String contentType);

    /**
     * 上传字节数组（仅建议用于内存小文件，如 < 10MB 的图片、凭证等）
     * 场景：内存小文件（二维码、图片裁剪、JSON 文本）
     */
    StorageFile upload(String bucket, String objectKey, byte[] bytes, String contentType);

    // ─── 下载与读取 (Download & Read) ─────────────

    /**
     * 函数式读取文件流（自动管理 Stream 的生命周期与关闭，防止连接泄漏）
     */
    void readStream(String bucket, String objectKey, Consumer<InputStream> streamConsumer);

    /**
     * 获取文件字节内容（适合小文件）
     */
    byte[] downloadBytes(String bucket, String objectKey);

    /**
     * 获取文件元数据信息
     */
    Optional<StorageFile> getMetadata(String bucket, String objectKey);

    /**
     * 检查文件是否存在
     */
    boolean exists(String bucket, String objectKey);

    // ─── 删除 (Delete) ─────────────

    /**
     * 删除单个文件
     */
    void deleteFile(String bucket, String objectKey);

    /**
     * 批量删除文件（单次 RPC 批量处理）
     */
    void deleteFiles(String bucket, List<String> objectKeys);

    // ─── URL 生成与访问 API ───────────────────────────────

    /**
     * 获取公网/CDN 静态访问 URL（适用于公共读 Bucket 或配置了 CDN 域名的场景）
     *
     * @return 永久有效的公网直链 (e.g. "https://cdn.example.com/docs/spec.pdf")
     */
    String getPublicUrl(String bucket, String objectKey);

    /**
     * 获取云厂商原厂临时签名 URL（适用于私有 Bucket 临时直连下载）
     *
     * @param duration 签名有效时长
     * @return 带云厂商签名的 URL (e.g. "https://my-bucket.oss-cn-hangzhou.aliyuncs.com/spec.pdf?OSSAccessKeyId=...")
     */
    String getPresignedUrl(String bucket, String objectKey, Duration duration);

    /**
     * 获取应用网关代理/防盗链下载 URL（适用于 Local 本地存储，或需要隐藏云存储真实域名的场景）
     *
     * @param duration 令牌/签名有效时长
     * @return 指向后端网关服务的 URL (e.g. "https://api.example.com/api/v1/storage/download?bucket=xx&key=xx&token=yy")
     */
    String getGatewayUrl(String bucket, String objectKey, Duration duration);
}