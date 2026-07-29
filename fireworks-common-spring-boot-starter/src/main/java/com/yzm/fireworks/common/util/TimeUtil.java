package com.yzm.fireworks.common.util;

import org.springframework.util.ObjectUtils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 时间与时间戳工具类
 *
 * @author JYuan
 */
public class TimeUtil {

    private TimeUtil() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    // ================= 1. 常量定义与预编译 Formatter (提高性能) =================
    public static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    public static final String DEFAULT_TIME_FORMAT = "HH:mm:ss";

    public static final DateTimeFormatter FORMATTER_DATE_TIME = DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT);
    public static final DateTimeFormatter FORMATTER_DATE = DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT);
    public static final DateTimeFormatter FORMATTER_TIME = DateTimeFormatter.ofPattern(DEFAULT_TIME_FORMAT);

    /**
     * 默认系统时区 (东八区可显式指定 ZoneOffset.ofHours(8))
     */
    public static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();

    // ================= 2. 格式化 (Time -> String) =================
    public static String toString(LocalDate date) {
        if (ObjectUtils.isEmpty(date)) {
            return null;
        }
        return date.format(FORMATTER_DATE);
    }

    public static String toString(LocalDateTime date) {
        if (ObjectUtils.isEmpty(date)) {
            return null;
        }
        return date.format(FORMATTER_DATE_TIME);
    }

    // ================= 3. 解析 (String -> Time) =================
    public static LocalDateTime parseDateTime(String text) {
        if (ObjectUtils.isEmpty(text)) {
            return null;
        }
        return LocalDateTime.parse(text, FORMATTER_DATE_TIME);
    }

    public static LocalDate parseDate(String text) {
        if (ObjectUtils.isEmpty(text)) {
            return null;
        }
        return LocalDate.parse(text, FORMATTER_DATE);
    }

    // ================= 4. 💡 重点新增：时间戳与 LocalDateTime 互转 =================

    /**
     * LocalDateTime 转 毫秒时间戳 (默认时区)
     */
    public static Long toEpochMilli(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(DEFAULT_ZONE).toInstant().toEpochMilli();
    }

    /**
     * 毫秒时间戳 转 LocalDateTime (默认时区)
     */
    public static LocalDateTime ofEpochMilli(Long epochMilli) {
        if (epochMilli == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), DEFAULT_ZONE);
    }

    /**
     * LocalDateTime 转 秒时间戳 (默认时区)
     */
    public static Long toEpochSecond(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(DEFAULT_ZONE).toInstant().getEpochSecond();
    }

    /**
     * 秒时间戳 转 LocalDateTime (默认时区)
     */
    public static LocalDateTime ofEpochSecond(Long epochSecond) {
        if (epochSecond == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), DEFAULT_ZONE);
    }

    // ================= 5. 老旧 java.util.Date 兼容转换 =================
    public static Date toDate(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return Date.from(time.atZone(DEFAULT_ZONE).toInstant());
    }

    public static LocalDateTime ofDate(Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), DEFAULT_ZONE);
    }

    // ================= 6. 日期边界获取 (00:00:00 / 23:59:59) =================

    public static LocalDateTime dateBegin(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    /**
     * 获取指定日期的23:59:59
     */
    public static LocalDateTime dateEnd(LocalDate date) {
        return date == null ? null : LocalDateTime.of(date, LocalTime.MAX);
    }

    /**
     * 获取指定日期所在周的周一的00:00
     */
    public static LocalDateTime weekBegin(LocalDate date) {
        if (date == null) {
            return null;
        }
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.atStartOfDay();
    }

    /**
     * 获取指定日期所在周的周日23:59:59
     */
    public static LocalDateTime weekEnd(LocalDate date) {
        if (date == null) {
            return null;
        }
        LocalDate sunday = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return LocalDateTime.of(sunday, LocalTime.MAX);
    }

    /**
     * 获取指定日期所在月的1号的00:00
     */
    public static LocalDateTime monthBegin(LocalDate date) {
        if (date == null) {
            return null;
        }
        LocalDate firstDayOfMonth = date.with(TemporalAdjusters.firstDayOfMonth());
        return firstDayOfMonth.atStartOfDay();
    }

    /**
     * 获取指定日期所在月的最后一天的23:59:59
     */
    public static LocalDateTime monthEnd(LocalDate date) {
        if (date == null) {
            return null;
        }
        LocalDate lastDayOfMonth = date.with(TemporalAdjusters.lastDayOfMonth());
        return LocalDateTime.of(lastDayOfMonth, LocalTime.MAX);
    }

    /**
     * 获取指定日期所在年的1号的00:00
     */
    public static LocalDateTime yearBegin(LocalDate date) {
        if (date == null) {
            return null;
        }
        LocalDate firstDayOfYear = date.with(TemporalAdjusters.firstDayOfYear());
        return firstDayOfYear.atStartOfDay();
    }

    /**
     * 获取指定日期所在年的最后一天的23:59:59
     */
    public static LocalDateTime yearEnd(LocalDate date) {
        if (date == null) {
            return null;
        }
        LocalDate lastDayOfYear = date.with(TemporalAdjusters.lastDayOfYear());
        return LocalDateTime.of(lastDayOfYear, LocalTime.MAX);
    }

    // ================= 7. 重试退避算法 (Exponential Backoff with Jitter) =================

    public static long calculateNextRetryTime(int attemptCount, int maxRetries, int initialDelayMs, int maxDelayMs, double jitterFactor) {
        if (attemptCount > maxRetries) {
            return 0;
        }
        long totalDelayMs = getTotalDelayMs(attemptCount, initialDelayMs, maxDelayMs, jitterFactor);
        return System.currentTimeMillis() + totalDelayMs;
    }

    /**
     * 计算下次重试时间
     *
     * @param attemptCount   当前重试次数
     * @param maxRetries     最大重试次数
     * @param initialDelayMs 初始延迟时间(毫秒)
     * @param maxDelayMs     最大延迟时间(毫秒)
     * @param jitterFactor   抖动因子
     * @param now            当前时间
     * @return 下次重试时间
     */
    public static LocalDateTime calculateNextRetryTime(int attemptCount, int maxRetries, long initialDelayMs, long maxDelayMs, double jitterFactor, LocalDateTime now) {
        if (attemptCount > maxRetries) {
            return now;
        }
        long totalDelayMs = getTotalDelayMs(attemptCount, initialDelayMs, maxDelayMs, jitterFactor);
        return now.plus(totalDelayMs, ChronoUnit.MILLIS);
    }

    private static long getTotalDelayMs(int attemptCount, long initialDelayMs, long maxDelayMs, double jitterFactor) {
        if (attemptCount <= 0) {
            throw new IllegalArgumentException("attemptCount must be positive");
        }

        long baseDelayMs;
        try {
            // 计算基础指数延迟 (毫秒)，可能存在溢出所以需要try catch
            baseDelayMs = Math.multiplyExact(initialDelayMs, (long) Math.pow(2, attemptCount - 1));
            baseDelayMs = Math.min(baseDelayMs, maxDelayMs);
        } catch (ArithmeticException e) {
            baseDelayMs = maxDelayMs;
        }

        /*
         * 使用高并发下性能更好的 ThreadLocalRandom 生成 [0, 1) 的随机数
         */
        double jitterRangeMs = jitterFactor * baseDelayMs;
        double randomDouble = ThreadLocalRandom.current().nextDouble(); // [0.0, 1.0)
        double jitterMs = randomDouble * 2 * jitterRangeMs - jitterRangeMs;

        return Math.max(0, (long) (baseDelayMs + jitterMs));
    }
}