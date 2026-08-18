package com.yzm.fireworks.common.util.time;

import lombok.experimental.UtilityClass;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Instant 时间工具类。
 *
 * <p>
 * Instant 表示时间轴上的一个绝对时间点，不携带业务时区。
 *
 * <p>
 * 推荐用途：
 * <ul>
 *     <li>PostgreSQL timestamptz</li>
 *     <li>创建时间</li>
 *     <li>修改时间</li>
 *     <li>删除时间</li>
 *     <li>发布时间</li>
 *     <li>过期时间</li>
 *     <li>消息发送时间</li>
 *     <li>登录时间</li>
 *     <li>时间戳</li>
 * </ul>
 *
 * @author JYuan
 */
@UtilityClass
public class InstantUtil {

    /**
     * 默认时区仅用于“展示/兼容”场景。
     *
     * <p>
     * Instant 本身没有时区。
     * 业务代码如果涉及用户当地日期，应显式传入 ZoneId。
     */
    public static final ZoneId UTC = ZoneOffset.UTC;

    // ============================================================
    // 1. 当前时间
    // ============================================================

    /**
     * 获取当前时间。
     */
    public static Instant now() {
        return Instant.now();
    }

    /**
     * 获取当前毫秒时间戳。
     */
    public static long nowEpochMilli() {
        return System.currentTimeMillis();
    }

    /**
     * 获取当前秒时间戳。
     */
    public static long nowEpochSecond() {
        return Instant.now().getEpochSecond();
    }


    // ============================================================
    // 2. Instant <-> EpochMilli
    // ============================================================

    /**
     * Instant 转毫秒时间戳。
     */
    public static Long toEpochMilli(Instant instant) {
        return instant == null
                ? null
                : instant.toEpochMilli();
    }

    /**
     * 毫秒时间戳转 Instant。
     */
    public static Instant ofEpochMilli(Long epochMilli) {
        return epochMilli == null
                ? null
                : Instant.ofEpochMilli(epochMilli);
    }


    // ============================================================
    // 3. Instant <-> EpochSecond
    // ============================================================

    /**
     * Instant 转秒时间戳。
     */
    public static Long toEpochSecond(Instant instant) {
        return instant == null
                ? null
                : instant.getEpochSecond();
    }

    /**
     * 秒时间戳转 Instant。
     */
    public static Instant ofEpochSecond(Long epochSecond) {
        return epochSecond == null
                ? null
                : Instant.ofEpochSecond(epochSecond);
    }


    // ============================================================
    // 4. Instant <-> LocalDateTime
    // ============================================================

    /**
     * Instant 转指定时区的 LocalDateTime。
     *
     * <p>
     * 注意：
     * LocalDateTime 不包含时区，所以必须明确指定 ZoneId。
     */
    public static LocalDateTime toLocalDateTime(
            Instant instant,
            ZoneId zone) {

        if (instant == null) {
            return null;
        }

        Assert.notNull(zone, "zone must not be null");

        return LocalDateTime.ofInstant(instant, zone);
    }

    /**
     * Instant 转 UTC LocalDateTime。
     *
     * <p>
     * 主要用于兼容场景。
     */
    public static LocalDateTime toUtcLocalDateTime(Instant instant) {
        return toLocalDateTime(instant, UTC);
    }

    /**
     * 指定时区的 LocalDateTime 转 Instant。
     */
    public static Instant fromLocalDateTime(
            LocalDateTime dateTime,
            ZoneId zone) {

        if (dateTime == null) {
            return null;
        }

        Assert.notNull(zone, "zone must not be null");

        return dateTime
                .atZone(zone)
                .toInstant();
    }


    // ============================================================
    // 5. Instant <-> ZonedDateTime
    // ============================================================

    /**
     * Instant 转 ZonedDateTime。
     */
    public static ZonedDateTime toZonedDateTime(
            Instant instant,
            ZoneId zone) {

        if (instant == null) {
            return null;
        }

        Assert.notNull(zone, "zone must not be null");

        return instant.atZone(zone);
    }

    /**
     * ZonedDateTime 转 Instant。
     */
    public static Instant fromZonedDateTime(
            ZonedDateTime dateTime) {

        return dateTime == null
                ? null
                : dateTime.toInstant();
    }


    // ============================================================
    // 6. Instant -> LocalDate
    // ============================================================

    /**
     * 获取 Instant 在指定时区下对应的日期。
     *
     * <p>
     * 例如：
     *
     * <pre>
     * Instant: 2026-08-17T23:00:00Z
     *
     * Asia/Shanghai -> 2026-08-18
     * America/New_York -> 2026-08-17
     * </pre>
     */
    public static LocalDate toLocalDate(
            Instant instant,
            ZoneId zone) {

        if (instant == null) {
            return null;
        }

        Assert.notNull(zone, "zone must not be null");

        return instant
                .atZone(zone)
                .toLocalDate();
    }


    // ============================================================
    // 7. Instant <-> Date
    // ============================================================

    /**
     * Instant 转 java.util.Date。
     */
    public static Date toDate(Instant instant) {
        return instant == null
                ? null
                : Date.from(instant);
    }

    /**
     * java.util.Date 转 Instant。
     */
    public static Instant fromDate(Date date) {
        return date == null
                ? null
                : date.toInstant();
    }


    // ============================================================
    // 8. Instant 格式化
    // ============================================================

    /**
     * 按 ISO-8601 格式输出。
     *
     * <p>
     * 示例：
     *
     * <pre>
     * 2026-08-18T01:30:00Z
     * </pre>
     */
    public static String toIsoString(Instant instant) {
        return instant == null
                ? null
                : instant.toString();
    }

    /**
     * 按指定时区格式化。
     *
     * <p>
     * 示例：
     *
     * <pre>
     * Instant
     *     ↓
     * Asia/Shanghai
     *     ↓
     * 2026-08-18 09:30:00
     * </pre>
     */
    public static String format(
            Instant instant,
            ZoneId zone,
            java.time.format.DateTimeFormatter formatter) {

        if (instant == null) {
            return null;
        }

        Assert.notNull(zone, "zone must not be null");

        Assert.notNull(formatter, "formatter must not be null");

        return formatter
                .withZone(zone)
                .format(instant);
    }


    // ============================================================
    // 9. 日期边界
    // ============================================================

    /**
     * 获取 Instant 在指定时区对应日期的开始时间。
     *
     * <p>
     * 返回值仍然是 Instant。
     *
     * <p>
     * 适合直接用于：
     *
     * <pre>
     * WHERE created_at >= start
     * </pre>
     */
    public static Instant dayStart(
            Instant instant,
            ZoneId zone) {

        LocalDate date = toLocalDate(instant, zone);

        return dayStart(date, zone);
    }

    /**
     * 获取 Instant 在指定时区对应日期的下一个日期开始时间。
     *
     * <p>
     * 推荐数据库查询使用：
     *
     * <pre>
     * created_at >= start
     * AND created_at < nextStart
     * </pre>
     */
    public static Instant nextDayStart(
            Instant instant,
            ZoneId zone) {

        LocalDate date = toLocalDate(instant, zone);

        if (date == null) {
            return null;
        }

        return date
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant();
    }

    /**
     * LocalDate + ZoneId -> 当天开始。
     */
    public static Instant dayStart(
            LocalDate date,
            ZoneId zone) {

        if (date == null) {
            return null;
        }

        Assert.notNull(zone, "zone must not be null");

        return date
                .atStartOfDay(zone)
                .toInstant();
    }

    /**
     * LocalDate + ZoneId -> 下一天开始。
     */
    public static Instant nextDayStart(
            LocalDate date,
            ZoneId zone) {

        if (date == null) {
            return null;
        }

        Assert.notNull(zone, "zone must not be null");

        return date
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant();
    }


    // ============================================================
    // 10. 周边界
    // ============================================================

    /**
     * 获取当前 Instant 所在周的周一 00:00。
     */
    public static Instant weekStart(
            Instant instant,
            ZoneId zone) {

        LocalDate date = toLocalDate(instant, zone);

        if (date == null) {
            return null;
        }

        return DateUtil.weekStart(date)
                .atStartOfDay(zone)
                .toInstant();
    }

    /**
     * 获取当前 Instant 所在周的下周一 00:00。
     *
     * <p>
     * 推荐数据库范围查询使用：
     *
     * <pre>
     * [weekStart, nextWeekStart)
     * </pre>
     */
    public static Instant nextWeekStart(
            Instant instant,
            ZoneId zone) {

        LocalDate date = toLocalDate(instant, zone);

        if (date == null) {
            return null;
        }

        return DateUtil
                .weekStart(date)
                .plusWeeks(1)
                .atStartOfDay(zone)
                .toInstant();
    }


    // ============================================================
    // 11. 月边界
    // ============================================================

    /**
     * 当前 Instant 所在月份开始。
     */
    public static Instant monthStart(
            Instant instant,
            ZoneId zone) {

        LocalDate date = toLocalDate(instant, zone);

        if (date == null) {
            return null;
        }

        return DateUtil
                .monthStart(date)
                .atStartOfDay(zone)
                .toInstant();
    }

    /**
     * 当前 Instant 所在月份下个月开始。
     */
    public static Instant nextMonthStart(
            Instant instant,
            ZoneId zone) {

        LocalDate date = toLocalDate(instant, zone);

        if (date == null) {
            return null;
        }

        return DateUtil
                .monthStart(date)
                .plusMonths(1)
                .atStartOfDay(zone)
                .toInstant();
    }


    // ============================================================
    // 12. 年边界
    // ============================================================

    /**
     * 当前 Instant 所在年份开始。
     */
    public static Instant yearStart(
            Instant instant,
            ZoneId zone) {

        LocalDate date = toLocalDate(instant, zone);

        if (date == null) {
            return null;
        }

        return DateUtil
                .yearStart(date)
                .atStartOfDay(zone)
                .toInstant();
    }

    /**
     * 当前 Instant 所在年份下一年开始。
     */
    public static Instant nextYearStart(
            Instant instant,
            ZoneId zone) {

        LocalDate date = toLocalDate(instant, zone);

        if (date == null) {
            return null;
        }

        return DateUtil
                .yearStart(date)
                .plusYears(1)
                .atStartOfDay(zone)
                .toInstant();
    }


    // ============================================================
    // 13. 时间加减
    // ============================================================

    public static Instant plusMillis(
            Instant instant,
            long millis) {

        return instant == null
                ? null
                : instant.plusMillis(millis);
    }

    public static Instant plusSeconds(
            Instant instant,
            long seconds) {

        return instant == null
                ? null
                : instant.plusSeconds(seconds);
    }

    public static Instant plusMinutes(
            Instant instant,
            long minutes) {

        return instant == null
                ? null
                : instant.plus(minutes, ChronoUnit.MINUTES);
    }

    public static Instant plusHours(
            Instant instant,
            long hours) {

        return instant == null
                ? null
                : instant.plus(hours, ChronoUnit.HOURS);
    }

    public static Instant plusDays(
            Instant instant,
            long days) {

        return instant == null
                ? null
                : instant.plus(days, ChronoUnit.DAYS);
    }

    public static Instant plusWeeks(
            Instant instant,
            long weeks) {

        return instant == null
                ? null
                : instant.plus(weeks, ChronoUnit.WEEKS);
    }


    // ============================================================
    // 14. 比较
    // ============================================================

    public static boolean isBefore(
            Instant first,
            Instant second) {

        return first != null
                && second != null
                && first.isBefore(second);
    }

    public static boolean isAfter(
            Instant first,
            Instant second) {

        return first != null
                && second != null
                && first.isAfter(second);
    }

    public static boolean isEqual(
            Instant first,
            Instant second) {

        return first != null
                && first.equals(second);
    }

    /**
     * 判断 target 是否处于 [start, end)。
     */
    public static boolean isBetween(
            Instant target,
            Instant start,
            Instant end) {

        if (target == null || start == null || end == null) {
            return false;
        }

        return !target.isBefore(start)
                && target.isBefore(end);
    }


    // ============================================================
    // 15. Duration
    // ============================================================

    public static Duration between(
            Instant start,
            Instant end) {

        if (start == null || end == null) {
            return null;
        }

        return Duration.between(start, end);
    }

    public static long betweenMillis(
            Instant start,
            Instant end) {

        if (start == null || end == null) {
            return 0;
        }

        return Duration
                .between(start, end)
                .toMillis();
    }

    public static long betweenSeconds(
            Instant start,
            Instant end) {

        if (start == null || end == null) {
            return 0;
        }

        return Duration
                .between(start, end)
                .getSeconds();
    }


    // ============================================================
    // 16. 重试退避
    // ============================================================

    /**
     * 计算下一次重试时间。
     *
     * @return epoch milli，超过最大重试次数返回 0
     */
    public static long calculateNextRetryEpochMilli(
            int attemptCount,
            int maxRetries,
            long initialDelayMs,
            long maxDelayMs,
            double jitterFactor) {

        if (attemptCount > maxRetries) {
            return 0;
        }

        long delay = getTotalDelayMs(
                attemptCount,
                initialDelayMs,
                maxDelayMs,
                jitterFactor
        );

        return System.currentTimeMillis() + delay;
    }

    /**
     * 使用 Instant 计算下一次重试时间。
     */
    public static Instant calculateNextRetryTime(
            int attemptCount,
            int maxRetries,
            long initialDelayMs,
            long maxDelayMs,
            double jitterFactor,
            Instant now) {

        if (now == null) {
            now = Instant.now();
        }

        if (attemptCount > maxRetries) {
            return now;
        }

        long delay = getTotalDelayMs(
                attemptCount,
                initialDelayMs,
                maxDelayMs,
                jitterFactor
        );

        return now.plusMillis(delay);
    }

    private static long getTotalDelayMs(
            int attemptCount,
            long initialDelayMs,
            long maxDelayMs,
            double jitterFactor) {

        Assert.isTrue(attemptCount > 0, "attemptCount must be positive");

        Assert.isTrue(initialDelayMs >= 0, "initialDelayMs must be >= 0");

        Assert.isTrue(maxDelayMs >= 0, "maxDelayMs must be >= 0");

        Assert.isTrue(jitterFactor >= 0, "jitterFactor must be >= 0");

        long baseDelayMs;

        try {
            long multiplier =
                    (long) Math.pow(2, attemptCount - 1);

            baseDelayMs =
                    Math.multiplyExact(
                            initialDelayMs,
                            multiplier
                    );

            baseDelayMs =
                    Math.min(baseDelayMs, maxDelayMs);

        } catch (ArithmeticException e) {
            baseDelayMs = maxDelayMs;
        }

        double jitterRange =
                jitterFactor * baseDelayMs;

        double random =
                ThreadLocalRandom
                        .current()
                        .nextDouble();

        double jitter =
                random * 2 * jitterRange - jitterRange;

        return Math.max(
                0,
                (long) (baseDelayMs + jitter)
        );
    }
}