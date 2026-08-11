package com.yzm.fireworks.storage.core.orphan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一条"待确认"文件记录，用 {@code bucket + objectKey} 唯一标识。
 * <p>
 * 用于标记一个已经通过直传凭证可能已上传到云存储、但业务侧尚未最终确认（例如前端上传完成后未提交业务表单）
 * 的对象。是否已过期由底层注册表（如 Redis ZSet 的 score）判定，本实体仅作为待确认文件标识的载体，
 * 供确认、删除等操作使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingFile {

    /**
     * 桶名。
     */
    private String bucket;

    /**
     * 对象完整路径。
     */
    private String objectKey;
}
