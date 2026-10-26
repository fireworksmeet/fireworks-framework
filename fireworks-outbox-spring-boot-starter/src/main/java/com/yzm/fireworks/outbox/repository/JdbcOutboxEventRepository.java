package com.yzm.fireworks.outbox.repository;

import com.yzm.fireworks.outbox.domain.OutboxEvent;
import com.yzm.fireworks.outbox.domain.OutboxEventStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * 基于 Spring JDBC 的 Outbox 持久化实现。
 *
 * <p><b>与 ORM 无关</b>，也<b>不按数据库分方言</b>：所有语句都是 PG / MySQL 都能执行的写法。
 * 前提是表结构按脚本里的统一类型建（{@code event_id VARCHAR(32)}、{@code payload TEXT}），
 * 这样 INSERT 不需要 {@code ::uuid} / {@code ::jsonb} 之类的转型。
 *
 * <p><b>领取为何需要事务</b>：领取是「SELECT ... FOR UPDATE SKIP LOCKED → UPDATE 置 PROCESSING」
 * 两步，必须在同一短事务内完成，行锁才能持有到置位结束。事务里<b>不做任何远程调用</b>。
 *
 * <p><b>为什么不用 RETURNING</b>：{@code INSERT/UPDATE ... RETURNING} 是 PostgreSQL 专有语法，
 * 用了就无法与 MySQL 共用一套 SQL。
 *
 * <p><b>时间列</b>：统一用 {@link Timestamp} 绑定 / {@link ResultSet#getTimestamp} 读取，
 * JDBC 标准类型，PG 与 MySQL 驱动都原生支持，避免驱动对 {@code java.time} 的支持差异。
 *
 * @author JYuan
 */
public class JdbcOutboxEventRepository implements OutboxEventRepository {

    /**
     * 写入事件。列类型统一为字符串，PG / MySQL 都能直接绑定。
     */
    private static final String INSERT_SQL = """
            INSERT INTO outbox_event
                (event_id, event_type, entity_type, entity_id, payload,
                 status, retry_count, max_retry_count, next_retry_at, trace_id, created_at, updated_at)
            VALUES
                (:eventId, :eventType, :entityType, :entityId, :payload,
                 10, 0, :maxRetryCount, :nextRetryAt, :traceId, :createdAt, :updatedAt)
            """;

    /**
     * 领取候选行：{@code FOR UPDATE SKIP LOCKED} 保证多实例并发领取不重叠（PG 9.5+ / MySQL 8.0.1+）。
     * <p>必须在事务中执行，锁持有至随后的 {@link #CLAIM_UPDATE_SQL} 完成。
     */
    private static final String CLAIM_SELECT_HEAD = """
            SELECT id, event_id, event_type, entity_type, entity_id,
                   payload, status, retry_count, max_retry_count, next_retry_at, claimed_at,
                   claimed_by, last_error, trace_id, created_at, updated_at, published_at
            FROM outbox_event
            WHERE status = 10
              AND next_retry_at <= :now
            """;

    private static final String CLAIM_SELECT_TAIL = """
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """;

    /**
     * 将领取到的候选行置为 PROCESSING。
     */
    private static final String CLAIM_UPDATE_SQL = """
            UPDATE outbox_event
            SET status = 20,
                claimed_at = :claimedAt,
                claimed_by = :workerId,
                updated_at = :claimedAt
            WHERE id IN (:ids)
              AND status = 10
            """;

    private static final String MARK_SUCCEEDED_SQL = """
            UPDATE outbox_event
            SET status = 30,
                published_at = :now,
                updated_at = :now,
                last_error = NULL,
                claimed_at = NULL,
                claimed_by = NULL
            WHERE id = :id
              AND status = 20
              AND claimed_by = :workerId
            """;

    private static final String MARK_RETRY_SQL = """
            UPDATE outbox_event
            SET status = 10,
                retry_count = retry_count + 1,
                next_retry_at = :nextRetryAt,
                claimed_at = NULL,
                claimed_by = NULL,
                last_error = :error,
                updated_at = :now
            WHERE id = :id
              AND status = 20
              AND claimed_by = :workerId
            """;

    private static final String MARK_DEAD_SQL = """
            UPDATE outbox_event
            SET status = 40,
                retry_count = retry_count + 1,
                claimed_at = NULL,
                claimed_by = NULL,
                last_error = :error,
                updated_at = :now
            WHERE id = :id
              AND status = 20
              AND claimed_by = :workerId
            """;

    /**
     * 恢复超时的 PROCESSING 事件。
     * <p><b>恢复也计入一次重试</b>：否则进程反复崩溃（OOM、重启）时，同一行会被无限恢复、无限重投，
     * {@code max_retry_count} 形同虚设；达到上限的直接置 DEAD，让问题暴露出来。
     */
    private static final String RECOVER_STALE_SQL = """
            UPDATE outbox_event
            SET status = CASE WHEN retry_count + 1 >= max_retry_count THEN 40 ELSE 10 END,
                retry_count = retry_count + 1,
                claimed_at = NULL,
                claimed_by = NULL,
                next_retry_at = :now,
                updated_at = :now
            WHERE status = 20
              AND claimed_at < :before
            """;

    private static final RowMapper<OutboxEvent> ROW_MAPPER = (rs, rowNum) -> OutboxEvent.builder()
            .id(rs.getLong("id"))
            .eventId(rs.getString("event_id"))
            .eventType(rs.getString("event_type"))
            .entityType(rs.getString("entity_type"))
            .entityId(rs.getString("entity_id"))
            .payload(rs.getString("payload"))
            .status(rs.getInt("status"))
            .retryCount(rs.getInt("retry_count"))
            .maxRetryCount(rs.getInt("max_retry_count"))
            .nextRetryAt(toInstant(rs.getTimestamp("next_retry_at")))
            .claimedAt(toInstant(rs.getTimestamp("claimed_at")))
            .claimedBy(rs.getString("claimed_by"))
            .publishedAt(toInstant(rs.getTimestamp("published_at")))
            .lastError(rs.getString("last_error"))
            .traceId(rs.getString("trace_id"))
            .createdAt(toInstant(rs.getTimestamp("created_at")))
            .updatedAt(toInstant(rs.getTimestamp("updated_at")))
            .build();

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public JdbcOutboxEventRepository(NamedParameterJdbcTemplate jdbcTemplate,
                                     PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void insert(OutboxEvent event) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventId", event.getEventId())
                .addValue("eventType", event.getEventType())
                .addValue("entityType", event.getEntityType())
                .addValue("entityId", event.getEntityId())
                .addValue("payload", event.getPayload())
                .addValue("maxRetryCount", event.getMaxRetryCount())
                .addValue("nextRetryAt", Timestamp.from(event.getNextRetryAt()))
                .addValue("traceId", event.getTraceId())
                .addValue("createdAt", Timestamp.from(event.getCreatedAt()))
                .addValue("updatedAt", Timestamp.from(event.getUpdatedAt()));
        jdbcTemplate.update(INSERT_SQL, params);
    }

    @Override
    public List<OutboxEvent> claim(String workerId, int limit,
                                   Collection<String> includeTypes, Collection<String> excludeTypes) {
        Timestamp now = Timestamp.from(Instant.now());
        StringBuilder sql = new StringBuilder(CLAIM_SELECT_HEAD);
        MapSqlParameterSource params = new MapSqlParameterSource("now", now).addValue("limit", limit);
        if (includeTypes != null && !includeTypes.isEmpty()) {
            sql.append("  AND event_type IN (:includeTypes)\n");
            params.addValue("includeTypes", includeTypes);
        }
        if (excludeTypes != null && !excludeTypes.isEmpty()) {
            sql.append("  AND event_type NOT IN (:excludeTypes)\n");
            params.addValue("excludeTypes", excludeTypes);
        }
        sql.append(CLAIM_SELECT_TAIL);

        List<OutboxEvent> claimed = transactionTemplate.execute(status -> {
            List<OutboxEvent> candidates = jdbcTemplate.query(sql.toString(), params, ROW_MAPPER);
            if (candidates.isEmpty()) {
                return candidates;
            }
            List<Long> ids = candidates.stream().map(OutboxEvent::getId).toList();
            jdbcTemplate.update(CLAIM_UPDATE_SQL, new MapSqlParameterSource()
                    .addValue("ids", ids)
                    .addValue("workerId", workerId)
                    .addValue("claimedAt", now));
            for (OutboxEvent event : candidates) {
                event.setStatus(OutboxEventStatus.PROCESSING.getCode());
                event.setClaimedBy(workerId);
                event.setClaimedAt(now.toInstant());
            }
            return candidates;
        });
        return claimed == null ? List.of() : claimed;
    }

    @Override
    public boolean markSucceeded(Long id, String workerId) {
        return update(MARK_SUCCEEDED_SQL, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("workerId", workerId)
                .addValue("now", Timestamp.from(Instant.now())));
    }

    @Override
    public boolean markRetry(Long id, String workerId, Instant nextRetryAt, String error) {
        return update(MARK_RETRY_SQL, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("workerId", workerId)
                .addValue("nextRetryAt", Timestamp.from(nextRetryAt))
                .addValue("error", error)
                .addValue("now", Timestamp.from(Instant.now())));
    }

    @Override
    public boolean markDead(Long id, String workerId, String error) {
        return update(MARK_DEAD_SQL, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("workerId", workerId)
                .addValue("error", error)
                .addValue("now", Timestamp.from(Instant.now())));
    }

    @Override
    public int recoverStale(Instant before) {
        Timestamp now = Timestamp.from(Instant.now());
        return jdbcTemplate.update(RECOVER_STALE_SQL, new MapSqlParameterSource()
                .addValue("before", Timestamp.from(before))
                .addValue("now", now));
    }

    private boolean update(String sql, MapSqlParameterSource params) {
        return jdbcTemplate.update(sql, params) > 0;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
