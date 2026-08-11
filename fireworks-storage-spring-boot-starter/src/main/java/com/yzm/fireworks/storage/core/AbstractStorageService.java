package com.yzm.fireworks.storage.core;

import com.yzm.fireworks.storage.api.ObjectKeyUtil;
import com.yzm.fireworks.storage.api.StorageFile;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static com.yzm.fireworks.common.constants.StringPool.EMPTY;
import static com.yzm.fireworks.common.constants.StringPool.SLASH;

public abstract class AbstractStorageService {

    /**
     * 目录归一化：补上末尾斜杠（如 {@code avatar} → {@code avatar/}）。
     * 该语义与 {@link ObjectKeyUtil#cleanPath}（去除头尾斜杠）相反，用于需要目录前缀匹配（如 PostPolicy）
     * 的内部场景，故保留为内部方法，不对外暴露。
     */
    protected String normalizeDir(String dir) {
        if (!StringUtils.hasText(dir)) {
            return EMPTY;
        }
        if (dir.startsWith(SLASH)) {
            dir = dir.substring(1);
        }
        if (!dir.endsWith(SLASH)) {
            dir = dir + SLASH;
        }
        return dir;
    }

    /**
     * 从完整 objectKey 中提取目录前缀（去掉最后一段文件名，保留末尾斜杠），
     * 用于表单直传(PostPolicy)等需要按目录前缀做匹配策略的场景。
     * 例如 {@code temp/avatar/a1b2c3.jpg} 返回 {@code temp/avatar/}；无目录时返回空串。
     */
    protected String extractDirPrefix(String objectKey) {
        int lastSlash = objectKey.lastIndexOf(SLASH);
        if (lastSlash < 0) {
            return EMPTY;
        }
        return objectKey.substring(0, lastSlash + 1);
    }

    protected String encodeFileName(String fileName) {
        return URLEncoder.encode(fileName, StandardCharsets.UTF_8);
    }

    protected StorageFile buildStorageFile(String bucketName, String objectKey, String fileName, String url,
                                           Long size, String contentType, String etag) {
        return StorageFile.builder()
                .bucketName(bucketName)
                .objectKey(objectKey)
                .fileName(fileName)
                .url(url)
                .size(size)
                .contentType(contentType)
                .etag(etag)
                .build();
    }
}
