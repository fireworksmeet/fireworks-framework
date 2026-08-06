package com.yzm.fireworks.storage.core;

import com.yzm.fireworks.storage.api.StorageFile;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static com.yzm.fireworks.common.constants.StringPool.EMPTY;
import static com.yzm.fireworks.common.constants.StringPool.SLASH;

public abstract class AbstractStorageService {

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

    protected String buildObjectName(String dir, String fileName) {
        return normalizeDir(dir) + fileName;
    }

    /**
     * 从完整 objectName 中提取目录前缀（去掉最后一段文件名，保留末尾斜杠），
     * 用于表单直传(PostPolicy)等需要按目录前缀做匹配策略的场景。
     * 例如 {@code temp/avatar/a1b2c3.jpg} 返回 {@code temp/avatar/}；无目录时返回空串。
     */
    protected String extractDirPrefix(String objectName) {
        int lastSlash = objectName.lastIndexOf(SLASH);
        if (lastSlash < 0) {
            return EMPTY;
        }
        return objectName.substring(0, lastSlash + 1);
    }

    protected String encodeFileName(String fileName) {
        return URLEncoder.encode(fileName, StandardCharsets.UTF_8);
    }

    protected StorageFile buildStorageFile(String bucketName, String objectName, String fileName, String url,
                                           Long size, String contentType, String etag) {
        return StorageFile.builder()
                .bucketName(bucketName)
                .objectName(objectName)
                .fileName(fileName)
                .url(url)
                .size(size)
                .contentType(contentType)
                .etag(etag)
                .build();
    }
}
