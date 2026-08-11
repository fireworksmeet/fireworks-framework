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

    void deleteFile(String bucket, String objectKey);

    void deleteFile(String bucket, List<String> objectKeys);

    /**
     * 获取对象的固定访问地址，不带任何签名/鉴权信息，也没有过期时间。
     * <p>
     * 本质上只是字符串拼接：{@code publicEndpoint（或 endpoint）+ "/" + bucket + "/" + objectKey}，
     * 不会校验该对象是否真的存在，也不会校验 Bucket 的读权限策略。
     * <p>
     * 只适用于 Bucket（或该路径前缀）已配置为公共读，或者该地址是由 CDN/反向代理对接到对象存储的场景；
     * 如果 Bucket 是私有的，访问该地址会被拒绝（403）。需要长期分享一个固定不变的链接（如头像、公开素材）时使用这个方法。
     *
     * @param bucket     桶名
     * @param objectKey 对象完整路径
     * @return 不带签名、永久有效（但可能因为私有权限而无法直接访问）的地址
     */
    String getFileUrl(String bucket, String objectKey);

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
     * @param objectKey 对象完整路径
     * @param duration   该临时地址的有效期，超过后链接失效
     * @return 带签名、到期后失效的临时访问地址
     */
    String getPresignedUrl(String bucket, String objectKey, Duration duration);
}