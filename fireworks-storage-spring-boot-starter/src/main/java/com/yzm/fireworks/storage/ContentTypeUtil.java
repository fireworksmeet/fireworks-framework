package com.yzm.fireworks.storage;

import lombok.experimental.UtilityClass;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;

import java.util.Optional;

/**
 * 文件 ContentType (MIME-Type) 识别工具类。
 * <p>
 * 识别依据为<b>文件名后缀</b>，数据源是 Spring 内置的 {@code mime.types} 映射表
 * （1000+ 后缀，覆盖度优于 JDK 的 {@code URLConnection.guessContentTypeFromName}）。
 * <p>
 * <b>能力边界</b>：仅按后缀推断，<b>不嗅探文件内容</b>。无后缀或后缀被伪造时无法准确识别；
 * 需要基于内容的识别（magic number）请引入 Apache Tika 等专用库。
 * <p>
 * 相比直接调用 Spring {@link MediaTypeFactory} 的差异：
 * <ul>
 *   <li>入参由 {@link FileNameSupport} 统一归一化：容忍 URL 形态（自动剥离 {@code ?query} / {@code #fragment}）、
 *       裁剪空白、后缀大小写不敏感（{@code "cat.PNG"} 同样识别为 {@code image/png}）</li>
 *   <li>返回 {@link Optional}，消除“返回 null 还是空串”的歧义</li>
 *   <li>提供永不返回空值的 {@link #getContentTypeOrDefault(String)}，适配“上传必须带 Content-Type”的存储场景</li>
 * </ul>
 */
@UtilityClass
public class ContentTypeUtil {

    /**
     * 兜底 MIME 类型：无法识别后缀时的通用二进制类型（RFC 2046）。
     */
    public static final String DEFAULT_CONTENT_TYPE = MediaType.APPLICATION_OCTET_STREAM_VALUE;

    /**
     * 按文件名后缀推断 MIME 类型（规范方法）。
     *
     * @param filename 文件名或 URL，如 {@code "cat.png"}、{@code "cat.png?v=1"}、{@code "cat.PNG"}
     * @return 识别成功时返回 MIME 字符串（如 {@code "image/png"}）；
     * 入参为空、无后缀或后缀无法识别时返回 {@link Optional#empty()}
     */
    public static Optional<String> getContentType(String filename) {
        return Optional.ofNullable(FileNameSupport.normalize(filename))
                .flatMap(MediaTypeFactory::getMediaType)
                .map(MediaType::toString);
    }

    /**
     * 按文件名后缀推断 MIME 类型，无法识别时回退到 {@link #DEFAULT_CONTENT_TYPE}。
     * <p>
     * 适合上传场景：S3/OSS 要求 Content-Type 必须存在，不允许为 null。
     *
     * @param filename 文件名或 URL
     * @return 识别出的 MIME 字符串；无法识别时返回 {@code application/octet-stream}
     */
    public static String getContentTypeOrDefault(String filename) {
        return getContentType(filename).orElse(DEFAULT_CONTENT_TYPE);
    }
}
