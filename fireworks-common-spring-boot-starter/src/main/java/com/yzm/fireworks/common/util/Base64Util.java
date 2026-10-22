package com.yzm.fireworks.common.util;

import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Base64 编解码工具类
 *
 * <p>线程安全说明：
 * {@link Base64.Encoder} 与 {@link Base64.Decoder} 为不可变且线程安全对象，
 * 本类复用静态常量以降低 GC 开销。
 *
 * @author JYuan
 */
@UtilityClass
public class Base64Util {

    private static final Base64.Encoder BASIC_ENCODER = Base64.getEncoder();
    private static final Base64.Decoder BASIC_DECODER = Base64.getDecoder();
    private static final Base64.Decoder MIME_DECODER = Base64.getMimeDecoder();

    private static final Base64.Encoder URL_ENCODER_NO_PADDING = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    // ── Standard Base64 (常用在密钥、报文、文件传输) ─────────────────────────

    public static String encode(String src) {
        if (src == null) {
            return null;
        }
        return BASIC_ENCODER.encodeToString(src.getBytes(StandardCharsets.UTF_8));
    }

    public static String encode(byte[] src) {
        if (src == null) {
            return null;
        }
        return BASIC_ENCODER.encodeToString(src);
    }

    public static byte[] decodeToBytes(String src) {
        if (src == null) {
            return new byte[0];
        }
        return BASIC_DECODER.decode(src.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String src) {
        if (src == null) {
            return null;
        }
        return new String(decodeToBytes(src), StandardCharsets.UTF_8);
    }

    /**
     * MIME Base64 解码：忽略换行与其它非字母表字符，用于 PEM 正文、折行的 JSON 等。
     *
     * <p>与 {@link #decodeToBytes(String)} 的区别：后者是严格解码，遇到换行直接抛异常。
     * 这里非法输入仍然抛 {@link IllegalArgumentException}，不返回空数组——把"解不出来"伪装成
     * "空内容"会让上层报一个离现场很远的错。
     */
    public static byte[] decodeMime(String src) {
        if (src == null) {
            return new byte[0];
        }
        return MIME_DECODER.decode(src.getBytes(StandardCharsets.UTF_8));
    }

    // ── URL Safe Base64 (常用在 URL 参数、游标 Cursor、Cookie) ───────────────

    /**
     * URL 安全编码（自动去除尾部 '=' 填补符）
     */
    public static String encodeUrl(String src) {
        if (src == null) {
            return null;
        }
        return URL_ENCODER_NO_PADDING.encodeToString(src.getBytes(StandardCharsets.UTF_8));
    }

    public static String encodeUrl(byte[] src) {
        if (src == null) {
            return null;
        }
        return URL_ENCODER_NO_PADDING.encodeToString(src);
    }

    /**
     * URL 安全解码
     */
    public static String decodeUrl(String src) {
        if (src == null) {
            return null;
        }
        try {
            byte[] bytes = URL_DECODER.decode(src.getBytes(StandardCharsets.UTF_8));
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // 防御非法篡改的字符
            return null;
        }
    }
}