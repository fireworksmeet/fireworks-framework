package com.yzm.fireworks.common.util.time;

import lombok.experimental.UtilityClass;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * 日期与本地时间工具类。
 *
 * <p>
 * 本工具类只处理：
 *
 * <ul>
 *     <li>LocalDate</li>
 *     <li>LocalTime</li>
 *     <li>LocalDateTime</li>
 * </ul>
 *
 * <p>
 * 注意：
 * LocalDateTime 不表示一个绝对时间点。
 * 如果需要和 timestamptz / Instant 互转，请使用 InstantUtil + ZoneId。
 *
 * @author JYuan
 */
@UtilityClass
public class DateUtil {

    public static final String DEFAULT_DATE_FORMAT =
            "yyyy-MM-dd";

    public static final String DEFAULT_TIME_FORMAT =
            "HH:mm:ss";

    public static final String DEFAULT_DATE_TIME_FORMAT =
            "yyyy-MM-dd HH:mm:ss";

    public static final String DEFAULT_DATE_TIME_MILLI_FORMAT =
            "yyyy-MM-dd HH:mm:ss.SSS";

    public static final DateTimeFormatter FORMATTER_DATE =
            DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT);

    public static final DateTimeFormatter FORMATTER_TIME =
            DateTimeFormatter.ofPattern(DEFAULT_TIME_FORMAT);

    public static final DateTimeFormatter FORMATTER_DATE_TIME =
            DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT);

    public static final DateTimeFormatter FORMATTER_DATE_TIME_MILLI =
            DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_MILLI_FORMAT);


    // ============================================================
    // 1. 当前日期时间
    // ============================================================

    public static LocalDate today() {
        return LocalDate.now();
    }

    public static LocalTime nowTime() {
        return LocalTime.now();
    }

    public static LocalDateTime nowDateTime() {
        return LocalDateTime.now();
    }


    // ============================================================
    // 2. String -> Date
    // ============================================================

    public static LocalDate parseDate(String text) {

        if (text == null || text.isBlank()) {
            return null;
        }

        return LocalDate.parse(
                text,
                FORMATTER_DATE
        );
    }

    public static LocalTime parseTime(String text) {

        if (text == null || text.isBlank()) {
            return null;
        }

        return LocalTime.parse(
                text,
                FORMATTER_TIME
        );
    }

    public static LocalDateTime parseDateTime(String text) {

        if (text == null || text.isBlank()) {
            return null;
        }

        return LocalDateTime.parse(
                text,
                FORMATTER_DATE_TIME
        );
    }


    // ============================================================
    // 3. Date -> String
    // ============================================================

    public static String formatDate(LocalDate date) {

        return date == null
                ? null
                : date.format(FORMATTER_DATE);
    }

    public static String formatTime(LocalTime time) {

        return time == null
                ? null
                : time.format(FORMATTER_TIME);
    }

    public static String formatDateTime(
            LocalDateTime dateTime) {

        return dateTime == null
                ? null
                : dateTime.format(FORMATTER_DATE_TIME);
    }

    public static String formatDateTimeMillis(
            LocalDateTime dateTime) {

        return dateTime == null
                ? null
                : dateTime.format(
                        FORMATTER_DATE_TIME_MILLI
                );
    }


    // ============================================================
    // 4. 日期开始/结束
    // ============================================================

    /**
     * 日期开始：
     *
     * 00:00:00
     */
    public static LocalDateTime dayStart(
            LocalDate date) {

        return date == null
                ? null
                : date.atStartOfDay();
    }

    /**
     * 日期结束。
     *
     * <p>
     * 主要用于展示。
     *
     * <p>
     * 数据库查询不要优先使用这个方法，
     * 推荐使用：
     *
     * <pre>
     * [dayStart, nextDayStart)
     * </pre>
     */
    public static LocalDateTime dayEnd(
            LocalDate date) {

        return date == null
                ? null
                : LocalDateTime.of(
                        date,
                        LocalTime.MAX
                );
    }

    /**
     * 下一天开始。
     */
    public static LocalDateTime nextDayStart(
            LocalDate date) {

        return date == null
                ? null
                : date.plusDays(1)
                .atStartOfDay();
    }


    // ============================================================
    // 5. 周
    // ============================================================

    /**
     * 获取本周周一。
     */
    public static LocalDate weekStart(
            LocalDate date) {

        if (date == null) {
            return null;
        }

        return date.with(
                TemporalAdjusters.previousOrSame(
                        DayOfWeek.MONDAY
                )
        );
    }

    /**
     * 获取本周周日。
     */
    public static LocalDate weekEnd(
            LocalDate date) {

        if (date == null) {
            return null;
        }

        return date.with(
                TemporalAdjusters.nextOrSame(
                        DayOfWeek.SUNDAY
                )
        );
    }

    /**
     * 获取下周周一。
     */
    public static LocalDate nextWeekStart(
            LocalDate date) {

        LocalDate start = weekStart(date);

        return start == null
                ? null
                : start.plusWeeks(1);
    }

    /**
     * 本周开始时间。
     */
    public static LocalDateTime weekDateTimeStart(
            LocalDate date) {

        LocalDate start = weekStart(date);

        return dayStart(start);
    }

    /**
     * 本周结束时间。
     */
    public static LocalDateTime weekDateTimeEnd(
            LocalDate date) {

        LocalDate end = weekEnd(date);

        return dayEnd(end);
    }


    // ============================================================
    // 6. 月
    // ============================================================

    /**
     * 获取本月第一天。
     */
    public static LocalDate monthStart(
            LocalDate date) {

        if (date == null) {
            return null;
        }

        return date.with(
                TemporalAdjusters.firstDayOfMonth()
        );
    }

    /**
     * 获取本月最后一天。
     */
    public static LocalDate monthEnd(
            LocalDate date) {

        if (date == null) {
            return null;
        }

        return date.with(
                TemporalAdjusters.lastDayOfMonth()
        );
    }

    /**
     * 获取下个月第一天。
     */
    public static LocalDate nextMonthStart(
            LocalDate date) {

        LocalDate start = monthStart(date);

        return start == null
                ? null
                : start.plusMonths(1);
    }

    public static LocalDateTime monthDateTimeStart(
            LocalDate date) {

        return dayStart(monthStart(date));
    }

    public static LocalDateTime monthDateTimeEnd(
            LocalDate date) {

        return dayEnd(monthEnd(date));
    }


    // ============================================================
    // 7. 年
    // ============================================================

    /**
     * 获取本年第一天。
     */
    public static LocalDate yearStart(
            LocalDate date) {

        if (date == null) {
            return null;
        }

        return date.with(
                TemporalAdjusters.firstDayOfYear()
        );
    }

    /**
     * 获取本年最后一天。
     */
    public static LocalDate yearEnd(
            LocalDate date) {

        if (date == null) {
            return null;
        }

        return date.with(
                TemporalAdjusters.lastDayOfYear()
        );
    }

    /**
     * 获取下一年第一天。
     */
    public static LocalDate nextYearStart(
            LocalDate date) {

        LocalDate start = yearStart(date);

        return start == null
                ? null
                : start.plusYears(1);
    }

    public static LocalDateTime yearDateTimeStart(
            LocalDate date) {

        return dayStart(yearStart(date));
    }

    public static LocalDateTime yearDateTimeEnd(
            LocalDate date) {

        return dayEnd(yearEnd(date));
    }


    // ============================================================
    // 8. 月份天数
    // ============================================================

    public static int lengthOfMonth(
            LocalDate date) {

        return date == null
                ? 0
                : date.lengthOfMonth();
    }

    public static int lengthOfYear(
            LocalDate date) {

        return date == null
                ? 0
                : date.lengthOfYear();
    }

    public static int lengthOfMonth(
            int year,
            int month) {

        return YearMonth
                .of(year, month)
                .lengthOfMonth();
    }


    // ============================================================
    // 9. 日期加减
    // ============================================================

    public static LocalDate plusDays(
            LocalDate date,
            long days) {

        return date == null
                ? null
                : date.plusDays(days);
    }

    public static LocalDate plusWeeks(
            LocalDate date,
            long weeks) {

        return date == null
                ? null
                : date.plusWeeks(weeks);
    }

    public static LocalDate plusMonths(
            LocalDate date,
            long months) {

        return date == null
                ? null
                : date.plusMonths(months);
    }

    public static LocalDate plusYears(
            LocalDate date,
            long years) {

        return date == null
                ? null
                : date.plusYears(years);
    }


    // ============================================================
    // 10. LocalDateTime 加减
    // ============================================================

    public static LocalDateTime plusDays(
            LocalDateTime dateTime,
            long days) {

        return dateTime == null
                ? null
                : dateTime.plusDays(days);
    }

    public static LocalDateTime plusHours(
            LocalDateTime dateTime,
            long hours) {

        return dateTime == null
                ? null
                : dateTime.plusHours(hours);
    }

    public static LocalDateTime plusMinutes(
            LocalDateTime dateTime,
            long minutes) {

        return dateTime == null
                ? null
                : dateTime.plusMinutes(minutes);
    }

    public static LocalDateTime plusSeconds(
            LocalDateTime dateTime,
            long seconds) {

        return dateTime == null
                ? null
                : dateTime.plusSeconds(seconds);
    }


    // ============================================================
    // 11. 日期比较
    // ============================================================

    public static boolean isBefore(
            LocalDate first,
            LocalDate second) {

        return first != null
                && second != null
                && first.isBefore(second);
    }

    public static boolean isAfter(
            LocalDate first,
            LocalDate second) {

        return first != null
                && second != null
                && first.isAfter(second);
    }

    public static boolean isEqual(
            LocalDate first,
            LocalDate second) {

        return first != null
                && second != null
                && first.isEqual(second);
    }


    // ============================================================
    // 12. 日期差
    // ============================================================

    public static long daysBetween(
            LocalDate start,
            LocalDate end) {

        if (start == null || end == null) {
            return 0;
        }

        return ChronoUnit.DAYS.between(
                start,
                end
        );
    }

    public static long monthsBetween(
            LocalDate start,
            LocalDate end) {

        if (start == null || end == null) {
            return 0;
        }

        return ChronoUnit.MONTHS.between(
                start,
                end
        );
    }

    public static long yearsBetween(
            LocalDate start,
            LocalDate end) {

        if (start == null || end == null) {
            return 0;
        }

        return ChronoUnit.YEARS.between(
                start,
                end
        );
    }


    // ============================================================
    // 13. LocalTime
    // ============================================================

    public static LocalTime startOfDay() {
        return LocalTime.MIN;
    }

    public static LocalTime endOfDay() {
        return LocalTime.MAX;
    }

    public static LocalTime plusHours(
            LocalTime time,
            long hours) {

        return time == null
                ? null
                : time.plusHours(hours);
    }

    public static LocalTime plusMinutes(
            LocalTime time,
            long minutes) {

        return time == null
                ? null
                : time.plusMinutes(minutes);
    }

    public static LocalTime plusSeconds(
            LocalTime time,
            long seconds) {

        return time == null
                ? null
                : time.plusSeconds(seconds);
    }
}