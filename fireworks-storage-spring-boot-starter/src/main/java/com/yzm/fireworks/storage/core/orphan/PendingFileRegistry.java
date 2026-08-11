package com.yzm.fireworks.storage.core.orphan;

import java.time.Duration;
import java.util.List;

/**
 * 待确认文件注册表（SPI）。
 * <p>
 * 用于登记"已发放直传凭证 / 可能已上传"但尚未被业务确认的对象，配合 {@link OrphanFileCleaner} 清理孤儿文件：
 * <ul>
 *     <li>业务层在发放直传凭证、或前端完成上传后调用 {@link #markPending} 登记一条待确认记录；</li>
 *     <li>业务处理成功后调用 {@link #confirm} 确认该文件已被正常使用，清理器将不再处理它；</li>
 *     <li>已过期（超过待确认有效时长）且仍未确认的记录，由清理器通过 {@link #listExpired} 分批取出并删除对应对象。</li>
 * </ul>
 * 基础框架默认提供基于 Redis ZSet 的实现 {@link RedisPendingFileRegistry}，天然支持高并发与多实例部署。
 * 如需替换（如换用独立 Redis 键、其他存储），实现本接口并声明为 @Bean 即可自动取代默认实现。
 */
public interface PendingFileRegistry {

    /**
     * 登记一条待确认记录，生效时长为 {@code ttl}。
     *
     * @param bucket     桶名
     * @param objectKey 对象完整路径
     * @param ttl        期望确认的时长，超过该时长未确认则视为孤儿；为 null 时使用清理器默认 TTL
     */
    void markPending(String bucket, String objectKey, Duration ttl);

    /**
     * 确认某条记录对应的文件已被业务正常使用，不再视为孤儿。
     * 记录不存在时静默忽略，不抛异常。
     *
     * @param bucket     桶名
     * @param objectKey 对象完整路径
     */
    void confirm(String bucket, String objectKey);

    /**
     * 批量确认同一桶下的多个文件。
     *
     * @param bucket      桶名
     * @param objectKeys 对象完整路径列表
     */
    void confirm(String bucket, List<String> objectKeys);

    /**
     * 批量确认包含不同桶的多条待确认文件记录。
     *
     * @param pendingFiles 待确认文件列表
     */
    void confirmFiles(List<PendingFile> pendingFiles);


    /**
     * 列出当前已过期、尚未确认的记录（至多 {@code limit} 条），供清理器扫描。
     * <p>
     * 批大小由调用方传入，在注册表层（底层存储）控制一次取出的数量，
     * 避免一次性取出全部过期记录造成内存与网络压力；剩余记录由后续扫描批次继续处理。
     *
     * @param limit 本次最多取出的记录数，需大于 0
     * @return 已过期且未确认的记录列表，可能为空
     */
    List<PendingFile> listExpired(int limit);

    /**
     * 移除一条记录。文件已被确认或已被清理后调用。
     *
     * @param bucket     桶名
     * @param objectKey 对象完整路径
     */
    void remove(String bucket, String objectKey);

    /**
     * 批量移除多条记录。文件已被确认或已被清理后调用。
     *
     * @param bucket      桶名
     * @param objectKeys 对象完整路径列表
     */
    void removeAll(String bucket, List<String> objectKeys);
}

