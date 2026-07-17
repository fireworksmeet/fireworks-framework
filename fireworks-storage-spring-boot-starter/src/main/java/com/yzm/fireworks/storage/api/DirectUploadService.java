package com.yzm.fireworks.storage.api;

import java.time.Duration;

public interface DirectUploadService {

    /**
     * 获取预签名 PUT 直传凭证：前端使用该 URL 直接发起 HTTP PUT 上传整个文件。
     */
    UploadCredential getUploadCredential(String bucket, String objectName, Duration duration);

    /**
     * 获取表单直传（PostPolicy）凭证：前端使用返回的 url + formData 以 multipart/form-data 形式 POST 上传文件，
     * 文件名由前端通过 formData 中的 key 模板（包含 ${filename}）决定。
     * 默认不支持，由具体 Provider 实现按需覆盖；不支持时抛出 {@link UnsupportedOperationException}。
     *
     * @param bucket   桶名
     * @param dir      上传目标目录前缀，可为空表示根目录
     * @param duration 凭证有效期
     */
    default UploadCredential getUploadCredentialByPostPolicy(String bucket, String dir, Duration duration) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " 不支持表单直传(PostPolicy)凭证");
    }
}
