package com.yzm.fireworks.storage.aliyun;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.internal.Mimetypes;
import com.aliyun.oss.model.*;
import com.yzm.fireworks.storage.api.ImageOptions;
import com.yzm.fireworks.storage.api.StorageFile;
import com.yzm.fireworks.storage.api.StorageService;
import com.yzm.fireworks.storage.core.AbstractStorageService;
import com.yzm.fireworks.storage.core.StorageProperties;
import com.yzm.fireworks.storage.core.StorageUrlUtils;
import com.yzm.fireworks.storage.core.exception.StorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.yzm.fireworks.common.constants.StringPool.*;

/**
 * 阿里云 OSS 存储实现。
 * <p>
 * 分片上传完全交给 OSS SDK 自身的能力
 * <ul>
 *     <li>File 上传：使用 {@code ossClient.uploadFile()}（断点续传上传），由 SDK 自行判断是否需要分片、
 *     自行管理并发线程，并提供基于本地 checkpoint 文件的断点续传能力。</li>
 *     <li>Stream 上传：使用最基础的 {@code putObject}（简单上传），OSS SDK 没有提供针对任意
 *     {@link InputStream} 的自动分片能力（断点续传依赖可重复读取的本地文件，流式数据天然不具备这个条件），
 *     因此流式上传被限制在 OSS 简单上传的单次大小上限（5GB）以内，超出该大小请先落盘后改用 File 上传。</li>
 * </ul>
 */
@Slf4j
public class AliyunStorageService extends AbstractStorageService implements StorageService, DisposableBean {

    private static final long ZERO_BYTE = 0;

    /**
     * OSS 简单上传（PutObject）单次最大支持 5GB，超过此大小必须使用分片上传。
     * 由于流式上传没有走分片，这里作为硬性上限做前置校验，避免把一个含糊的远端错误丢给调用方。
     */
    private static final long SIMPLE_PUT_MAX_SIZE = 5L * 1024 * 1024 * 1024;

    private final OSS client;
    private final StorageProperties properties;
    private final StorageProperties.Aliyun aliyunProperties;

    public AliyunStorageService(StorageProperties properties, OSS client) {
        Assert.notNull(properties, "StorageProperties 不能为空");
        Assert.notNull(client, "OSS client 不能为空");
        this.properties = properties;
        this.aliyunProperties = properties.getAliyun();
        this.client = client;
    }

    @Override
    public void destroy() {
        if (client != null) {
            client.shutdown();
        }
    }

    @Override
    public StorageFile uploadFile(String bucket, String dir, String file) {
        Assert.hasText(file, "文件路径不能为空");
        return uploadFile(bucket, dir, new File(file));
    }

    @Override
    public StorageFile uploadFile(String bucket, String dir, File file) {
        Assert.hasText(bucket, "bucket 不能为空");
        Assert.notNull(file, "file 不能为空");
        Assert.isTrue(file.isFile(), () -> "文件不存在或不是合法文件: " + file.getAbsolutePath());

        String objectName = buildObjectName(dir, file.getName());
        return uploadFileWithResumableUpload(bucket, objectName, file);
    }

    @Override
    public StorageFile uploadFile(String bucket, String dir, String fileName, InputStream inputStream, long contentLength) {
        return uploadFile(bucket, dir, fileName, inputStream, contentLength, null);
    }

    @Override
    public StorageFile uploadFile(String bucket, String dir, String fileName, InputStream inputStream, long contentLength, String contentType) {
        Assert.hasText(bucket, "bucket 不能为空");
        Assert.hasText(fileName, "fileName 不能为空");
        Assert.notNull(inputStream, "inputStream 不能为空");
        Assert.isTrue(contentLength > ZERO_BYTE, "contentLength 必须大于0");
        Assert.isTrue(contentLength <= SIMPLE_PUT_MAX_SIZE, () ->
                "流式上传的 contentLength(" + contentLength + ") 超过 OSS 简单上传单次大小上限(5GB)。"
                        + "OSS SDK 没有提供针对 InputStream 的自动分片能力，超过该大小请先落盘为本地文件后改用 uploadFile(File) 方法"
                        + "（支持断点续传分片上传）");

        String objectName = buildObjectName(dir, fileName);
        try (InputStream in = inputStream) {
            return simpleUploadFromStream(bucket, objectName, fileName, in, contentLength, contentType);
        } catch (StorageException e) {
            throw e;
        } catch (IOException e) {
            throw new StorageException("关闭文件输入流失败, object=" + objectName, e);
        } catch (Exception e) {
            throw new StorageException("文件流式上传失败: bucket=" + bucket + ", object=" + objectName, e);
        }
    }

    @Override
    public void deleteFile(String bucket, String objectName) {
        Assert.hasText(bucket, "bucket 不能为空");
        Assert.hasText(objectName, "objectName 不能为空");
        try {
            client.deleteObject(bucket, objectName);
            log.info("文件删除成功, bucket={}, object={}", bucket, objectName);
        } catch (Exception e) {
            throw new StorageException("删除文件失败, bucket=" + bucket + ", object=" + objectName, e);
        }
    }

    @Override
    public void deleteFile(String bucket, List<String> objectNames) {
        Assert.hasText(bucket, "bucket 不能为空");
        if (CollectionUtils.isEmpty(objectNames)) {
            log.warn("deleteFile 批量删除时 objectNames 为空，跳过操作, bucket={}", bucket);
            return;
        }
        try {
            client.deleteObjects(new DeleteObjectsRequest(bucket)
                    .withKeys(objectNames).withEncodingType("url"));
            log.info("文件批量删除成功, bucket={}, count={}", bucket, objectNames.size());
        } catch (Exception e) {
            throw new StorageException("批量删除文件失败, bucket=" + bucket, e);
        }
    }

    @Override
    public String getFileUrl(String bucket, String objectName) {
        String publicEndpoint = properties.getPublicEndpoint();
        if (StringUtils.hasText(publicEndpoint)) {
            return publicEndpoint + SLASH + bucket + SLASH + objectName;
        }
        return StorageUrlUtils.buildBucketUrl(bucket, aliyunProperties.getEndpoint(), true) + SLASH + objectName;
    }

    @Override
    public String getPresignedUrl(String bucket, String objectName, Duration duration) {
        Date expiration = new Date(System.currentTimeMillis() + duration.toMillis());
        // 显式指定 HttpMethod.GET（即便这是 SDK 3 参数版 generatePresignedUrl 的默认行为），
        // 与 AliyunDirectUploadService#getUploadCredential 中显式指定 PUT 的写法保持一致，
        // 避免一边显式写方法、一边依赖隐式默认值，看代码时容易误以为两者是同一种签名。
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, objectName, HttpMethod.GET);
        request.setExpiration(expiration);
        String urlStr = client.generatePresignedUrl(request).toString();
        // OSS 使用 V1 签名，Host 不在签名范围内（CanonicalizedResource 只含路径），
        // 因此可以在签名完成后安全地将内网 host 替换为公网地址，签名依然有效。
        String publicEndpoint = properties.getPublicEndpoint();
        if (StringUtils.hasText(publicEndpoint)) {
            urlStr = StorageUrlUtils.replaceHost(urlStr, StorageUrlUtils.buildBucketUrl(bucket, publicEndpoint, true));
        }
        return urlStr;
    }

    @Override
    public String getFileUrl(String bucket, String objectName, ImageOptions options) {
        if (options == null) {
            return getFileUrl(bucket, objectName);
        }
        options.validate();
        String baseUrl = getFileUrl(bucket, objectName);
        String separator = baseUrl.contains(QUESTION_MARK) ? AMPERSAND : QUESTION_MARK;
        return baseUrl + separator + "x-oss-process=image/" + buildImageProcess(options);
    }

    /**
     * 按 {@link ImageOptions} 中携带的字段拼接 OSS 图片处理参数，resize/format 各自独立、可任意组合：
     * <ul>
     *     <li>宽高都指定：{@code resize,m_fill,w_W,h_H} 等比缩放后裁剪填充到目标宽高
     *     （与旧版 {@code getThumbnailUrl} 行为一致）。</li>
     *     <li>只指定宽或高其中一边：{@code resize,w_W} / {@code resize,h_H}，OSS 默认按 lfit 模式
     *     等比缩放，不会裁剪图片内容。</li>
     *     <li>指定了 format：追加 {@code format,<ext>}，可与上面任意 resize 结果组合，也可单独使用
     *     （只转格式不改尺寸）。</li>
     * </ul>
     */
    private String buildImageProcess(ImageOptions options) {
        List<String> segments = new ArrayList<>();
        if (options.hasResize()) {
            segments.add(buildResizeSegment(options));
        }
        if (options.hasFormat()) {
            segments.add("format," + options.getFormat().getExtension());
        }
        return String.join(SLASH, segments);
    }

    private String buildResizeSegment(ImageOptions options) {
        Integer width = options.getWidth();
        Integer height = options.getHeight();
        if (width != null && height != null) {
            return "resize,m_fill,w_" + width + ",h_" + height;
        }
        if (width != null) {
            return "resize,w_" + width;
        }
        return "resize,h_" + height;
    }

    private ObjectMetadata buildContentDispositionMetadata(String fileName) {
        ObjectMetadata metadata = new ObjectMetadata();
        String encodedFileName = encodeFileName(fileName);
        metadata.setContentDisposition(
                String.format("attachment;filename=%s;filename*=UTF-8''%s", encodedFileName, encodedFileName));
        return metadata;
    }

    /**
     * 使用 OSS SDK 自带的断点续传上传能力：内部自行判断是否需要分片、自行管理并发线程，
     * 并通过本地 checkpoint 文件（{@code <file>.ucp}）记录已完成的分片，上传中断后下次调用可自动跳过已传分片。
     * 上传完全成功后，SDK 会自动清理 checkpoint 文件；失败则保留，供下次重试时续传。
     */
    private StorageFile uploadFileWithResumableUpload(String bucket, String objectName, File file) {
        ObjectMetadata metadata = buildContentDispositionMetadata(file.getName());
        metadata.setContentType(Mimetypes.getInstance().getMimetype(file, objectName));

        UploadFileRequest uploadFileRequest = new UploadFileRequest(bucket, objectName);
        uploadFileRequest.setUploadFile(file.getAbsolutePath());
        uploadFileRequest.setObjectMetadata(metadata);
        uploadFileRequest.setPartSize(properties.getPartSize());
        uploadFileRequest.setTaskNum(Math.max(1, aliyunProperties.getMultipartConcurrency()));
        uploadFileRequest.setEnableCheckpoint(true);
        uploadFileRequest.setCheckpointFile(file.getAbsolutePath() + ".ucp");

        try {
            UploadFileResult result = client.uploadFile(uploadFileRequest);
            String etag = extractETag(result);
            log.info("文件上传成功(断点续传), bucket={}, object={}, size={}, etag={}",
                    bucket, objectName, file.length(), etag);
            return buildStorageFile(bucket, objectName, file.getName(), getFileUrl(bucket, objectName),
                    file.length(), metadata.getContentType(), etag);
        } catch (Throwable t) {
            // OSS SDK 的 uploadFile 方法声明 throws Throwable（内部多线程分片上传的异常会原样透出），
            // 这里只包装真正的异常，JVM 级别的 Error（如 OutOfMemoryError）原样向上抛出，不做包装。
            if (t instanceof Error error) {
                throw error;
            }
            throw new StorageException("断点续传上传失败: bucket=" + bucket + ", object=" + objectName
                    + "，checkpoint文件: " + uploadFileRequest.getCheckpointFile() + "，可重新调用以从断点处继续上传", t);
        }
    }

    private String extractETag(UploadFileResult result) {
        // uploadFile() 内部始终走分片上传协议完成上传（即便文件只有一个分片，也是以
        // CompleteMultipartUpload 收尾），不存在"走了简单 PutObject 分支"的情况，
        // 因此 UploadFileResult 只有 getMultipartUploadResult() 这一个结果来源。
        if (result != null && result.getMultipartUploadResult() != null) {
            return result.getMultipartUploadResult().getETag();
        }
        return null;
    }

    private StorageFile simpleUploadFromStream(String bucket, String objectName, String fileName,
                                                InputStream inputStream, long contentLength, String contentType) {
        ObjectMetadata metadata = buildContentDispositionMetadata(fileName);
        metadata.setContentLength(contentLength);
        if (StringUtils.hasText(contentType)) {
            metadata.setContentType(contentType);
        }
        try {
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, objectName, inputStream, metadata);
            PutObjectResult result = client.putObject(putObjectRequest);
            log.info("文件流式上传成功, bucket={}, object={}, size={}, etag={}",
                    bucket, objectName, contentLength, result.getETag());
            return buildStorageFile(bucket, objectName, fileName, getFileUrl(bucket, objectName),
                    contentLength, metadata.getContentType(), result.getETag());
        } catch (Exception e) {
            throw new StorageException("文件流式上传失败: bucket=" + bucket + ", object=" + objectName, e);
        }
    }
}