package com.yzm.fireworks.storage.api;

import java.io.File;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;

public interface StorageService {

    StorageFile uploadFile(String bucket, String dir, String file);

    StorageFile uploadFile(String bucket, String dir, File file);

    StorageFile uploadFile(String bucket, String dir, String fileName, InputStream inputStream, long contentLength);

    StorageFile uploadFile(String bucket, String dir, String fileName, InputStream inputStream, long contentLength, String contentType);

    void deleteFile(String bucket, String objectName);

    void deleteFile(String bucket, List<String> objectNames);

    /**
     * 获取对象的固定访问地址，不带任何签名/鉴权信息，也没有过期时间。
     * <p>
     * 本质上只是字符串拼接：{@code publicEndpoint（或 endpoint）+ "/" + bucket + "/" + objectName}，
     * 不会校验该对象是否真的存在，也不会校验 Bucket 的读权限策略。
     * <p>
     * 只适用于 Bucket（或该路径前缀）已配置为公共读，或者该地址是由 CDN/反向代理对接到对象存储的场景；
     * 如果 Bucket 是私有的，访问该地址会被拒绝（403）。需要长期分享一个固定不变的链接（如头像、公开素材）时使用这个方法。
     *
     * @param bucket     桶名
     * @param objectName 对象完整路径
     * @return 不带签名、永久有效（但可能因为私有权限而无法直接访问）的地址
     */
    String getFileUrl(String bucket, String objectName);

    /**
     * 获取对象的访问地址，并可附带图片处理选项（缩放/裁剪/格式转换）。
     * <p>
     * 这是 {@link #getFileUrl(String, String)} 的增强版本，二者共享同一套地址拼接规则（是否带签名、
     * 是否过期完全取决于具体 Provider 的 {@code getFileUrl} 实现），唯一区别在于是否附加图片处理参数：
     * <ul>
     *     <li>{@code options} 为 {@code null}：等价于直接调用 {@link #getFileUrl(String, String)}，
     *     返回原图地址，不做任何处理。</li>
     *     <li>{@code options} 不为 {@code null}：在原图地址基础上按 {@link ImageOptions} 中指定的
     *     宽高/格式附加图片处理参数，由对应 Provider 在访问时按需实时生成处理后的图片，不会在存储端
     *     落盘生成额外文件。{@code width}、{@code height}、{@code format} 三个字段可以任意组合使用，
     *     具体语义见 {@link ImageOptions} 的类注释。</li>
     * </ul>
     * 不同 Provider 的具体实现方式：
     * <ul>
     *     <li>Aliyun OSS：基于内置的"图片处理"服务，在 {@link #getFileUrl} 的基础上拼接
     *     {@code x-oss-process} 参数，OSS 会在访问时按需实时生成处理后的图片。</li>
     *     <li>MinIO：自身不具备图片处理能力，需要在 MinIO 前面搭配 imgproxy（见
     *     {@code fireworks.storage.minio.imgproxy} 配置），返回符合 imgproxy 协议的签名 URL，
     *     由 imgproxy 拉取源图并实时生成处理后的图片。未配置 imgproxy 时，传入非空 {@code options}
     *     会抛出异常。</li>
     * </ul>
     * 默认实现仅处理 {@code options} 为 {@code null} 的情况（回退到 {@link #getFileUrl(String, String)}）；
     * {@code options} 不为 {@code null} 时默认抛出 {@link UnsupportedOperationException}，由具体 Provider
     * 实现按需覆盖。
     *
     * @param bucket     桶名
     * @param objectName 对象完整路径
     * @param options    图片处理选项，{@code null} 表示不做任何处理，直接返回原图地址
     */
    default String getFileUrl(String bucket, String objectName, ImageOptions options) {
        if (options == null) {
            return getFileUrl(bucket, objectName);
        }
        throw new UnsupportedOperationException(getClass().getSimpleName() + " 不支持基于 ImageOptions 生成图片处理地址");
    }

    /**
     * 获取带签名、限时有效的临时访问地址（GET 预签名 URL）。
     * <p>
     * 与 {@link #getFileUrl} 的本质区别：这里会用 AccessKey/SecretKey 对请求做签名，URL 中会带上
     * 签名串和过期时间，{@code duration} 到期后该 URL 自动失效，无法再访问。
     * <p>
     * 适用于 Bucket 是私有的、但需要临时授权给前端/第三方下载某个对象的场景（如用户私有文件的下载链接），
     * 不要求 Bucket 配置公共读权限。
     *
     * @param bucket     桶名
     * @param objectName 对象完整路径
     * @param duration   该临时地址的有效期，超过后链接失效
     * @return 带签名、到期后失效的临时访问地址
     */
    String getPresignedUrl(String bucket, String objectName, Duration duration);
}