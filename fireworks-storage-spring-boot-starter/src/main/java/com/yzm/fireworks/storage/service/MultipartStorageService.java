package com.yzm.fireworks.storage.service;

import com.yzm.fireworks.storage.model.dto.StorageFile;

import java.io.InputStream;
import java.util.List;

/**
 * 大文件分片上传与断点续传服务接口
 * <p>
 * <b>应用场景：</b><br>
 * 本接口专门面向<b>前端/客户端大文件切片上传、秒传以及断点续传</b>场景。
 * 区别于后端服务器内部上传（由 SDK 自动切片），本接口将分片会话控制权（uploadId / partNumber / ETag）
 * 暴露出来，配合前端 HTML5 File API 切片后直接并行传输，<b>零消耗后端网关带宽与内存</b>。
 * <p>
 * <b>前端典型断点续传交互流程：</b><br>
 * <ol>
 *   <li><b>初始化会话：</b>前端请求后端调用 {@link #initiateMultipartUpload} 拿到全局唯一的 {@code uploadId}；</li>
 *   <li><b>并行切片上传：</b>前端将本地 File 切为 5MB~20MB 的 Blob 块，并发调用 {@link #uploadPart}（支持中途断网后仅补传未成功的 Part）；</li>
 *   <li><b>触发合并：</b>所有 Part 传完后，前端将 Part 序号与 ETag 列表汇总，请求后端调用 {@link #completeMultipartUpload} 组合成完整文件；</li>
 *   <li><b>异常取消：</b>传输中途取消或超时抛错，调用 {@link #abortMultipartUpload} 物理清除远端已传的中间碎片文件。</li>
 * </ol>
 */
public interface MultipartStorageService {

    /**
     * 步骤 1/4：初始化分片上传会话
     * <p>
     * 向云存储（S3/OSS/COS/MinIO）注册一次大文件上传事务。云存储在服务端开启临时缓存区并返回唯一的上传凭证 ID。
     *
     * @param bucket      目标存储桶名称（为空时使用默认配置桶）
     * @param objectKey   目标文件对象完整路径（如 "video/2026/08/course_1080p.mp4"）
     * @param contentType 文件 Content-Type（如 "video/mp4"）
     * @return uploadId   云存储服务端返回的全局唯一分片上传会话 ID（后续上传分片、合并、取消均需携带此 ID）
     */
    String initiateMultipartUpload(String bucket, String objectKey, String contentType);

    /**
     * 步骤 2/4：上传单个分片 (Part)
     * <p>
     * 支持多线程并发调用。网络波动导致某分片失败时，仅需重新传入该分片（即<b>断点续传</b>的核心粒度）。
     *
     * @param bucket      目标存储桶名称
     * @param objectKey   目标文件对象完整路径
     * @param uploadId    初始化会话时获取的 {@code uploadId}
     * @param partNumber  分片编号（从 1 开始递增，如 1, 2, 3...，决定远端合并时的顺序）
     * @param inputStream 当前分片的数据流（建议单分片大小不小于 5MB，云厂商通常有最小 Part 限制）
     * @param partSize    当前分片的数据字节长度 (Bytes)
     * @return partEtag   当前分片上传成功后云存储返回的散列校验码（通常为 MD5/SHA256 哈希，合并时必须按序传回）
     */
    String uploadPart(String bucket, String objectKey, String uploadId, int partNumber, InputStream inputStream, long partSize);

    /**
     * 步骤 3/4：完成并合并所有分片
     * <p>
     * 当前端确认所有 Part 全部上传完毕后调用。云存储收到请求后会在服务端校验各分片的 ETag 散列，
     * 并按分片序号无缝拼接到一起，生成最终可访问的物理文件。
     *
     * @param bucket    目标存储桶名称
     * @param objectKey 目标文件对象完整路径
     * @param uploadId  初始化会话时获取的 {@code uploadId}
     * @param partEtags 所有分片上传成功后返回的 ETag 列表（<b>必须严格按照 partNumber 从小到大的顺序排列</b>）
     * @return StorageFile 合并成功后的完整文件元数据对象（包含最终文件大小、访问 URL、ETag 等）
     */
    StorageFile completeMultipartUpload(String bucket, String objectKey, String uploadId, List<String> partEtags);

    /**
     * 步骤 4/4（可选/异常）：取消/终止分片上传会话
     * <p>
     * 用于用户主动取消上传、传输超时失败或清理废弃会话。调用后云存储会<b>物理清理</b>该 {@code uploadId}
     * 对应在远端缓存的所有中间碎片文件，避免产生高额存储费用和碎片垃圾。
     *
     * @param bucket    目标存储桶名称
     * @param objectKey 目标文件对象完整路径
     * @param uploadId  初始化会话时获取的 {@code uploadId}
     */
    void abortMultipartUpload(String bucket, String objectKey, String uploadId);
}