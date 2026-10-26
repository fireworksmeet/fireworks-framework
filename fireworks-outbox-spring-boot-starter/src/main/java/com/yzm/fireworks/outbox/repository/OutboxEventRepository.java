package com.yzm.fireworks.outbox.repository;

import com.yzm.fireworks.outbox.domain.OutboxEvent;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Outbox 事件持久化契约。
 * <p>
 * 默认实现基于 Spring JDBC（{@link JdbcOutboxEventRepository}），框架不依赖任何 ORM。
 * 业务若需替换持久化方式，声明同类型 Bean 覆盖即可。
 *
 * @author JYuan
 */
public interface OutboxEventRepository {

    /**
     * 写入事件（须在业务本地事务内执行）。
     */
    void insert(OutboxEvent event);

    /**
     * 领取待投递事件并置为 PROCESSING。
     * <p>实现须保证多实例并发领取不重叠（默认实现用 {@code FOR UPDATE SKIP LOCKED}）。
     *
     * @param workerId     领取者标识
     * @param limit        单次领取上限
     * @param includeTypes 只领取这些类型；{@code null} 或空表示不限
     * @param excludeTypes 排除这些类型；{@code null} 或空表示不排除（默认道用它避开已分道的类型）
     * @return 已领取的事件（状态、claimedBy、claimedAt 已回填）
     */
    List<OutboxEvent> claim(String workerId, int limit,
                            Collection<String> includeTypes, Collection<String> excludeTypes);

    /**
     * 标记投递成功。
     *
     * @return {@code true} 表示成功写入；{@code false} 表示已失去持有权（不应覆盖他人结果）
     */
    boolean markSucceeded(Long id, String workerId);

    /**
     * 标记待重试。
     *
     * @return {@code true} 表示成功写入
     */
    boolean markRetry(Long id, String workerId, Instant nextRetryAt, String error);

    /**
     * 标记死亡。
     *
     * @return {@code true} 表示成功写入
     */
    boolean markDead(Long id, String workerId, String error);

    /**
     * 恢复超时未完成的 PROCESSING 事件。
     * <p>恢复计入一次重试（{@code retry_count + 1}），达到 {@code max_retry_count} 的直接置 DEAD，
     * 避免进程反复崩溃导致同一条事件无限重投。
     *
     * @param before 领取时间早于该时刻的视为超时
     * @return 重置的行数
     */
    int recoverStale(Instant before);
}
