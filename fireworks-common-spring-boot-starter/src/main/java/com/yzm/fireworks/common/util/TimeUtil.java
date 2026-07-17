package com.yzm.fireworks.common.util;

import org.apache.commons.lang3.RandomUtils;
import org.springframework.util.ObjectUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * @author JYuan
 */
public class TimeUtil {

    public static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    public static final String DEFAULT_TIME_FORMAT = "HH:mm:ss";

    public static String toString(LocalDate date) {
        if (ObjectUtils.isEmpty(date)) {
            return null;
        }
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT);
        return date.format(dateTimeFormatter);
    }

    public static String toString(LocalDateTime date) {
        if (ObjectUtils.isEmpty(date)) {
            return null;
        }
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT);
        return date.format(dateTimeFormatter);
    }

    /**
     * 获取指定日期的00:00
     */
    public static LocalDateTime dateBegin(LocalDate date) {
        return date.atStartOfDay();
    }

    /**
     * 获取指定日期的23:59:59
     */
    public static LocalDateTime dateEnd(LocalDate date) {
        return LocalDateTime.of(date, LocalTime.MAX);
    }

    /**
     * 获取指定日期所在周的周一的00:00
     */
    public static LocalDateTime weekBegin(LocalDate date) {
        // 周一
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        // 本周开始时间
        return monday.atStartOfDay();
    }

    /**
     * 获取指定日期所在周的周日23:59:59
     */
    public static LocalDateTime weekEnd(LocalDate date) {
        // 周日
        LocalDate sunday = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        // 本周结束时间
        return LocalDateTime.of(sunday, LocalTime.MAX);
    }

    /**
     * 获取指定日期所在月的1号的00:00
     */
    public static LocalDateTime monthBegin(LocalDate date) {
        // 本月1号
        LocalDate firstDayOfMonth = date.with(TemporalAdjusters.firstDayOfMonth());
        return firstDayOfMonth.atStartOfDay();
    }

    /**
     * 获取指定日期所在月的最后一天的23:59:59
     */
    public static LocalDateTime monthEnd(LocalDate date) {
        // 本月最后一天
        LocalDate lastDayOfMonth = date.with(TemporalAdjusters.lastDayOfMonth());
        return LocalDateTime.of(lastDayOfMonth, LocalTime.MAX);
    }

    /**
     * 获取指定日期所在年的1号的00:00
     */
    public static LocalDateTime yearBegin(LocalDate date) {
        // 当年第一天
        LocalDate firstDayOfYear = date.with(TemporalAdjusters.firstDayOfYear());
        return firstDayOfYear.atStartOfDay();
    }

    /**
     * 获取指定日期所在年的最后一天的23:59:59
     */
    public static LocalDateTime yearEnd(LocalDate date) {
        // 当年最后一天
        LocalDate lastDayOfYear = date.with(TemporalAdjusters.lastDayOfYear());
        return LocalDateTime.of(lastDayOfYear, LocalTime.MAX);
    }

    /**
     * 计算下次重试时间
     *
     * @param attemptCount   当前重试次数
     * @param maxRetries     最大重试次数
     * @param initialDelayMs 初始延迟时间(毫秒)
     * @param maxDelayMs     最大延迟时间(毫秒)
     * @param jitterFactor   抖动因子
     * @return 下次重试时间的毫秒值
     */
    public static long calculateNextRetryTime(int attemptCount, int maxRetries, int initialDelayMs, int maxDelayMs, double jitterFactor) {
        if (attemptCount > maxRetries) {
            return 0;
        }
        long totalDelayMs = getTotalDelayMs(attemptCount, initialDelayMs, maxDelayMs, jitterFactor);
        // 计算绝对时间戳（当前时间 + 延迟）
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
        // 计算基础指数延迟 (毫秒)
        long totalDelayMs = getTotalDelayMs(attemptCount, initialDelayMs, maxDelayMs, jitterFactor);

        // 计算绝对时间戳（当前时间 + 延迟）
        return now.plus(totalDelayMs, ChronoUnit.MILLIS);
    }

    private static long getTotalDelayMs(int attemptCount, long initialDelayMs, long maxDelayMs, double jitterFactor) {
        // 边界检查
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must be positive");
        }

        long baseDelayMs;

        try {
            // 计算基础指数延迟 (毫秒)，可能存在溢出所以需要try catch
            baseDelayMs = Math.multiplyExact(initialDelayMs, (long) Math.pow(2, attemptCount - 1));
            // 应用最大延迟限制
            baseDelayMs = Math.min(baseDelayMs, maxDelayMs);
        } catch (ArithmeticException e) {
            baseDelayMs = maxDelayMs;
        }

        /*
         * 计算抖动范围 (-jitterFactor * baseDelayMs,-jitterFactor * baseDelayMs]
         * RandomUtils.secure().randomDouble(0, 1)表示生成一个[0,1)之间的随机数
         */
        double jitterRangeMs = jitterFactor * baseDelayMs;
        double jitterMs = RandomUtils.secure().randomDouble(0, 1) * 2 * jitterRangeMs - jitterRangeMs;

        // 计算总延迟（确保不为负）
        return Math.max(0, (long) (baseDelayMs + jitterMs));
    }
}
