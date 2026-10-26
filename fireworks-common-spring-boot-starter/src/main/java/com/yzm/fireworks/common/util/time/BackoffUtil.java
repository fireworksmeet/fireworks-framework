package com.yzm.fireworks.common.util.time;

import lombok.experimental.UtilityClass;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 退避时间计算（指数退避 + 抖动）。
 *
 * <p>所有方法都遵循同一条底线：{@code retryCount} 从 <b>0</b> 开始，
 * 指数部分为 {@code baseDelay × 2^retryCount}，并先按 {@code maxDelay} 截断；
 * 抖动只改变“落点”，<b>最终结果不会超过 {@code maxDelay}</b>。
 *
 * <p>抖动形态按场景选择：
 * <table border="1">
 *   <caption>抖动形态</caption>
 *   <tr><th>方法</th><th>公式</th><th>适用</th></tr>
 *   <tr><td>{@link #fullJitter}</td><td>{@code U(0, min(cap, base·2ⁿ))}</td>
 *       <td>AWS 推荐；进程内即时重试，尽快卸压</td></tr>
 *   <tr><td>{@link #percentageJitter}</td><td>{@code x·(1 ± factor)}，{@code x = min(cap, base·2ⁿ)}</td>
 *       <td>gRPC 默认风格；对既有延迟做小幅扰动</td></tr>
 *   <tr><td>{@link #additiveJitter}</td><td>{@code min(cap, x + U(0, jitter))}</td>
 *       <td>持久化调度（如 Outbox）；只往后推迟，不提前重试</td></tr>
 * </table>
 *
 * <p><b>不提供无抖动版本</b>：无抖动会在故障后制造同步重试的「惊群」，
 * 已不是推荐做法。确需固定间隔（如测试）时，把抖动参数设为 {@code 0} 即可。
 *
 * @author JYuan
 */
@UtilityClass
public class BackoffUtil {

    /**
     * 位移上限：防止 {@code 2^n} 在 retryCount 很大时溢出
     */
    private static final int MAX_SHIFT = 30;

    /**
     * Full jitter：在 {@code [0, min(cap, base·2ⁿ)]} 内均匀取值。
     * <p>AWS《Exponential Backoff And Jitter》推荐的形态，削峰效果最好。
     */
    public static Instant fullJitter(int retryCount, Duration baseDelay, Duration maxDelay, Instant now) {
        long ceiling = cappedExponentialMillis(retryCount, baseDelay, maxDelay);
        long delay = ceiling <= 0 ? 0 : ThreadLocalRandom.current().nextLong(ceiling + 1);
        return plusMillis(now, delay);
    }

    /**
     * 百分比抖动：{@code x·(1 ± jitterFactor)}，结果再夹到 {@code [0, min(cap, ...)]}。
     * <p>gRPC 的默认退避参数就是这个形态（jitter = 0.2）。
     *
     * @param jitterFactor 抖动比例，{@code 0.2} 表示 ±20%；设为 0 即固定间隔（仅测试等特殊场景）
     */
    public static Instant percentageJitter(int retryCount, Duration baseDelay, Duration maxDelay,
                                           double jitterFactor, Instant now) {
        long ceiling = cappedExponentialMillis(retryCount, baseDelay, maxDelay);
        long cap = Math.max(maxDelay.toMillis(), 0);

        double range = Math.max(jitterFactor, 0) * ceiling;
        double delta = (ThreadLocalRandom.current().nextDouble() * 2 - 1) * range;
        long delay = Math.round(ceiling + delta);

        return plusMillis(now, Math.max(0, Math.min(cap, delay)));
    }

    /**
     * 单向抖动：在指数延迟上叠加 {@code [0, jitter]}，再按 {@code maxDelay} 截断。
     * <p>只往后推迟、不提前重试，适合把重试时间落库、由调度器轮询的场景。
     *
     * @param jitter 抖动上限；设为 {@link Duration#ZERO} 即固定间隔（仅测试等特殊场景）
     */
    public static Instant additiveJitter(int retryCount, Duration baseDelay, Duration maxDelay,
                                         Duration jitter, Instant now) {
        long ceiling = cappedExponentialMillis(retryCount, baseDelay, maxDelay);
        long jitterMillis = Math.max(jitter.toMillis(), 0);
        long delay = ceiling + (jitterMillis <= 0 ? 0 : ThreadLocalRandom.current().nextLong(jitterMillis + 1));

        return plusMillis(now, Math.min(delay, Math.max(maxDelay.toMillis(), 0)));
    }

    /**
     * {@code min(cap, base·2^retryCount)}，并处理溢出与非法输入。
     */
    private static long cappedExponentialMillis(int retryCount, Duration baseDelay, Duration maxDelay) {
        Objects.requireNonNull(baseDelay, "baseDelay must not be null");
        Objects.requireNonNull(maxDelay, "maxDelay must not be null");
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative: " + retryCount);
        }

        long base = Math.max(baseDelay.toMillis(), 0);
        long cap = Math.max(maxDelay.toMillis(), 0);
        if (base == 0 || cap == 0) {
            return 0;
        }

        int shift = Math.min(retryCount, MAX_SHIFT);
        double exponential = base * Math.pow(2, shift);
        return exponential >= cap ? cap : (long) exponential;
    }

    private static Instant plusMillis(Instant now, long delayMillis) {
        Objects.requireNonNull(now, "now must not be null");
        return now.plusMillis(Math.max(delayMillis, 0));
    }
}
