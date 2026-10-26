package com.yzm.fireworks.outbox.api;

/**
 * Outbox 写入入口。
 *
 * <p><b>必须在业务本地事务内调用</b>：业务数据写入 + outbox 事件写入处于同一个数据库事务，
 * 提交则两者都在，回滚则两者都不在。这是 Outbox 唯一的、也是全部的正确性来源——
 * 不要在这里做任何远程调用。
 *
 * <pre>{@code
 * @Transactional
 * public void bindFile(Long userId, String fileKey) {
 *     userRepository.updateAvatar(userId, fileKey);
 *     outboxService.append(OutboxAppendRequest.builder()
 *             .eventType("FILE_REFERENCE_ADD")
 *             .entityType("USER").entityId(userId.toString())
 *             .payload(Map.of("userId", userId, "fileKey", fileKey))
 *             .build());
 * }
 * }</pre>
 *
 * @author JYuan
 */
public interface OutboxService {

    /**
     * 追加一条不带实体信息的事件。
     *
     * @param eventType 事件类型
     * @param payload   消息载荷（对象或 JSON 字符串）
     * @return 事件实例 ID（{@code eventId}）
     */
    String append(String eventType, Object payload);

    /**
     * 追加一条带实体信息的事件（便于按实体排查）。
     *
     * @param eventType  事件类型
     * @param entityType 业务实体类型（如 ORDER / USER）
     * @param entityId   业务实体 ID
     * @param payload    消息载荷
     * @return 事件实例 ID
     */
    String append(String eventType, String entityType, String entityId, Object payload);

    /**
     * 追加一条事件（完整参数）。
     *
     * @param request 追加请求
     * @return 事件实例 ID
     */
    String append(OutboxAppendRequest request);
}
