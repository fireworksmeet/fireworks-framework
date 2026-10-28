package com.yzm.fireworks.outbox.retry;

/**
 * 重试终局判定：是否应该置为 DEAD。
 *
 * <p>投递路径（Java）与恢复路径（SQL 的 {@code CASE WHEN}）都依赖这条规则，
 * 集中在这里是为了避免两处各写一份比较表达式而漂移。
 *
 * <p>语义：{@code currentRetryCount} 是<b>已重试次数</b>，本次失败后是第
 * {@code currentRetryCount + 1} 次尝试；达到 {@code maxRetryCount} 即不再重试。
 *
 * @author JYuan
 */
public final class RetryDecision {

    private RetryDecision() {
        throw new AssertionError("Cannot instantiate RetryDecision class");
    }

    /**
     * 本次失败后是否应置为 DEAD。
     *
     * @param currentRetryCount 已重试次数（不含本次）
     * @param maxRetryCount     最大尝试次数
     */
    public static boolean shouldDead(int currentRetryCount, int maxRetryCount) {
        return currentRetryCount + 1 >= maxRetryCount;
    }
}
