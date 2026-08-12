package com.yzm.fireworks.storage.service;

import com.yzm.fireworks.storage.model.dto.DirectUploadCredential;

import java.time.Duration;

/**
 * 客户端直传凭证服务接口
 */
public interface DirectUploadService {

    /**
     * 签发直传凭证（使用默认桶）
     *
     * @param objectKey    目标对象路径（支持业务自定义或自动生成）
     * @param contentType 上传文件的 MIME 类型
     * @param duration    凭证有效时长
     */
    DirectUploadCredential issueCredential(String objectKey, String contentType, Duration duration);

    /**
     * 签发直传凭证（指定桶名）
     */
    DirectUploadCredential issueCredential(String bucket, String objectKey, String contentType, Duration duration);
}