package com.yzm.fireworks.storage.model.util;

import com.yzm.fireworks.common.constants.StringPool;
import lombok.experimental.UtilityClass;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 文件 ContentType (MIME-Type) 识别与兜底工具类。
 */
@UtilityClass
public class ContentTypeUtil {

    /**
     * 带有兜底参数的方法：推导失败时，纯粹返回调用方指定的 fallbackType
     *
     * @param filename     文件名 (如 "avatar.unknown")
     * @param fallbackType 调用方指定的兜底类型 (如 "image/jpeg")
     * @return 识别到的 MIME 类型，若识别失败则直接返回 fallbackType
     */
    public static String getContentType(String filename, String fallbackType) {
        String mimeType = getContentType(filename);
        return StringUtils.hasText(mimeType) ? mimeType : fallbackType;
    }

    /**
     * 根据文件名/后缀推断 MIME 类型 (基于 Spring 框架内置的 MimeTypes 映射表)。
     */
    public static String getContentType(String filename) {
        if (!StringUtils.hasText(filename)) {
            return StringPool.EMPTY;
        }
        Optional<MediaType> mediaType = MediaTypeFactory.getMediaType(filename);
        return mediaType.map(MediaType::toString).orElse(StringPool.EMPTY);
    }
}