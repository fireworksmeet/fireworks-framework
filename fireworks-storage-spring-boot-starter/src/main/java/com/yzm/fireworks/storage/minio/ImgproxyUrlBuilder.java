package com.yzm.fireworks.storage.minio;

import com.yzm.fireworks.storage.api.ImageOptions;
import com.yzm.fireworks.common.util.StrUtil;
import com.yzm.fireworks.storage.core.exception.StorageException;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static com.yzm.fireworks.common.constants.StringPool.COLON;
import static com.yzm.fireworks.common.constants.StringPool.SLASH;

/**
 * imgproxy（https://imgproxy.net）URL 构建工具，使用其 "Advanced" 处理参数格式：
 * <pre>{@code http(s)://{imgproxy_host}/{signature}/{processing_options}/plain/{source_url}}</pre>
 * <p>
 * 签名算法：{@code base64url( HMAC-SHA256(key, salt || path) )}，其中 key、salt 均为十六进制配置项解码后的原始字节，
 * path 为签名段之后的完整路径（含开头的 "/"）。该算法与 imgproxy 官方文档及各语言客户端实现保持一致。
 * <p>
 * 未配置 key/salt 时回退到 imgproxy 的 "insecure" 模式（要求 imgproxy 服务端开启
 * {@code IMGPROXY_ALLOW_INSECURE=true}），仅建议本地开发环境使用。
 */
final class ImgproxyUrlBuilder {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final HexFormat HEX = HexFormat.of();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();


    private ImgproxyUrlBuilder() {
    }

    /**
     * 构建按 {@link ImageOptions} 指定选项进行处理（缩放/裁剪/格式转换）的图片访问 URL。
     *
     * @param endpoint  imgproxy 服务地址，如 http://imgproxy:8080
     * @param prefix    imgproxy 的访问前缀。，可为空
     * @param keyHex    签名密钥（十六进制），可为空（启用 insecure 模式）
     * @param saltHex   签名盐值（十六进制），可为空（启用 insecure 模式）
     * @param sourceUrl 源图片地址，imgproxy 会主动发起 HTTP 请求拉取该地址
     * @param options   图片处理选项，不为空（由调用方保证），resize/format 可任意组合
     */
    static String buildImageUrl(String endpoint, String prefix, String keyHex, String saltHex,
                                 String sourceUrl, ImageOptions options) {
        String processingOptions = buildProcessingOptions(options);
        String path = SLASH + processingOptions + SLASH + encodeSourceUrl(sourceUrl);

        String signatureSegment = (StringUtils.hasText(keyHex) && StringUtils.hasText(saltHex))
                ? sign(keyHex, saltHex, path)
                : "insecure";

        String base = StrUtil.removeSuffix(endpoint, SLASH);
        if (StringUtils.hasText(prefix)) {
            if (!prefix.startsWith(SLASH)) {
                base += SLASH;
            }
            base += prefix;
        }
        return base + SLASH + signatureSegment + path;
    }

    /**
     * 按 {@link ImageOptions} 中携带的字段拼接 imgproxy 处理参数，resize/format 各自独立、可任意组合：
     * <ul>
     *     <li>宽高都指定：{@code rs:fill:W:H:0} 等比缩放后裁剪填充到目标宽高
     *     （与旧版 {@code getThumbnailUrl} 行为一致）。</li>
     *     <li>只指定宽或高其中一边：{@code rs:fit:W:0:0} / {@code rs:fit:0:H:0}，缺失的一边传 0，
     *     由 imgproxy 按原图宽高比自动计算，不会裁剪图片内容。</li>
     *     <li>指定了 format：追加 {@code f:<ext>}，可与上面任意 resize 结果组合，也可单独使用
     *     （只转格式不改尺寸）。</li>
     * </ul>
     */
    private static String buildProcessingOptions(ImageOptions options) {
        List<String> segments = new ArrayList<>();
        if (options.hasResize()) {
            segments.add(buildResizeOption(options));
        }
        if (options.hasFormat()) {
            segments.add("f:" + options.getFormat().getExtension());
        }
        return String.join(SLASH, segments);
    }

    private static String buildResizeOption(ImageOptions options) {
        Integer width = options.getWidth();
        Integer height = options.getHeight();
        int w = width != null ? width : 0;
        int h = height != null ? height : 0;
        // 宽高都指定时用 fill：等比缩放后裁剪，效果与 OSS 的 m_fill 对齐；
        // 只指定一边时用 fit：等比缩放、不裁剪，缺失的一边传 0 由 imgproxy 自动按比例计算。
        String resizeType = (width != null && height != null) ? "fill" : "fit";
        return "rs:" + resizeType + COLON + w + COLON + h + ":0";
    }

    private static String sign(String keyHex, String saltHex, String path) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(HEX.parseHex(keyHex), HMAC_SHA256));
            mac.update(HEX.parseHex(saltHex));
            return URL_ENCODER.encodeToString(mac.doFinal(path.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new StorageException("计算 imgproxy 签名失败，请检查 fireworks.storage.minio.imgproxy.key/salt 是否为合法的十六进制字符串", e);
        }
    }

    /**
     * 对源 URL 按字节做百分号编码：保留 ASCII 字母数字及 URI 中常见的非保留/保留字符，其余（含所有非 ASCII 多字节字符）
     * 一律转义为 %XX。在字节层面编码可以正确处理 UTF-8 多字节字符，无需额外区分字符编码。
     */
    private static String encodeSourceUrl(String sourceUrl) {
        return URL_ENCODER.encodeToString(sourceUrl.getBytes(StandardCharsets.UTF_8));
    }
}