package com.yzm.fireworks.mybatisplus.support;

import com.yzm.fireworks.api.exception.BizException;
import com.yzm.fireworks.common.enums.ExceptionStatus;
import lombok.experimental.UtilityClass;
import org.springframework.dao.DuplicateKeyException;

import java.util.function.Supplier;

/**
 * 唯一键冲突 → 业务异常。
 *
 * <pre>{@code
 * // 冲突 → 抛业务码（返回 409，提示"手机号已注册"）
 * UniqueKeyUtil.throwOnConflict(() -> userMapper.insert(user), USER_MOBILE_EXISTS);
 * }</pre>
 *
 * <p><b>注意</b>：{@link DuplicateKeyException} 不区分是哪个唯一约束冲突。表上存在多个唯一键时，
 * 请确认"任何一种冲突都等价于同一个业务语义"，否则应改用捕获具体约束名的判断方式。
 *
 * <p><b>为什么不提供"忽略冲突"的方法</b>：捕获该异常后继续执行，事务语义随数据库而异——
 * PostgreSQL 里语句失败会让<b>整个事务</b>进入 aborted 状态，后续语句一律报错；
 * MySQL / InnoDB 只回滚这一条语句。同一个 helper 在两种库上行为不同，排查成本极高。
 * 需要"重复执行也安全"时，用数据库原生能力，并以影响行数判断：
 *
 * <pre>
 * INSERT ... ON CONFLICT DO NOTHING   -- PostgreSQL
 * INSERT IGNORE ...                   -- MySQL
 * </pre>
 *
 * 这类语句本身不抛异常，也不会弄脏事务；返回 0 行即表示已存在。
 *
 * @author JYuan
 */
@UtilityClass
public class UniqueKeyUtil {

    /**
     * 执行写操作；唯一键冲突时抛出带指定业务码的 {@link BizException}。
     *
     * @param action 写操作（通常是一次 insert）
     * @param status 冲突时返回给前端的业务状态
     * @return 写操作的结果
     */
    public static <T> T throwOnConflict(Supplier<T> action, ExceptionStatus status) {
        try {
            return action.get();
        } catch (DuplicateKeyException e) {
            // 保留原始异常：否则日志里只剩业务码，看不出是哪个唯一键冲突
            throw new BizException(status, e);
        }
    }
}
