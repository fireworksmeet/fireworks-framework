package com.yzm.fireworks.storage.api;

import java.time.Duration;

public interface DirectUploadService {

    /**
     * 获取预签名 PUT 直传凭证：前端使用该 URL 直接发起 HTTP PUT 上传整个文件。
     * <p>
     * 若孤儿文件清理能力已启用（已引入 Redis）且配置 {@code fireworks.storage.orphan-cleanup.auto-mark-pending=true}（默认），
     * 签发成功后会自动登记待确认记录，业务方无需手动登记，业务处理完成后仅需调用
     * {@code OrphanFileGuard#confirm} 确认即可。
     */
    UploadCredential getUploadCredential(String bucket, String objectName, Duration duration);

    /**
     * 获取表单直传（PostPolicy）凭证：前端使用返回的 url + formData 以 multipart/form-data 形式 POST 上传文件。
     * <p>
     * {@code objectName} 为后端生成的完整对象路径（含文件名），会被精确写入 formData 的 {@code key} 字段，
     * 云厂商（OSS/S3）会直接以该值作为存储路径并忽略前端文件原名。因此在签发时即可 100% 确定
     * {@link UploadCredential#getObjectName()} 与 {@link UploadCredential#getDisplayUrl()} 并精确返回，
     * 前端无需再做任何文件名替换。
     * <p>
     * 自动登记待确认记录的行为与 {@link #getUploadCredential} 一致，受
     * {@code fireworks.storage.orphan-cleanup.auto-mark-pending} 配置控制。
     * <p>
     * 默认不支持，由具体 Provider 实现按需覆盖；不支持时抛出 {@link UnsupportedOperationException}。
     *
     * @param bucket    桶名
     * @param objectName 后端生成的完整对象路径（含文件名），如 {@code temp/avatar/202608/a1b2c3d4e5.jpg}
     * @param duration  凭证有效期
     */
    default UploadCredential getUploadCredentialByPostPolicy(String bucket, String objectName, Duration duration) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " 不支持表单直传(PostPolicy)凭证");
    }
}
