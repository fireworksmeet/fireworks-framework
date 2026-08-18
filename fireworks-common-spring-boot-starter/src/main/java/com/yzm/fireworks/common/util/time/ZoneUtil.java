package com.yzm.fireworks.common.util.time;

import lombok.experimental.UtilityClass;
import org.springframework.util.Assert;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * 时区工具类。
 *
 * <p>
 * 负责 Instant、LocalDateTime、LocalDate、LocalTime
 * 与 ZoneId 之间的转换。
 *
 * <p>
 * 核心原则：
 *
 * <pre>
 * Instant + ZoneId
 *       ↓
 * ZonedDateTime
 *       ↓
 * LocalDate / LocalTime / LocalDateTime
 * </pre>
 *
 * @author JYuan
 */
@UtilityClass
public class ZoneUtil {

    /**
     * UTC。
     */
    public static final ZoneId UTC = ZoneOffset.UTC;

    /**
     * 中国时区。
     */
    public static final ZoneId ASIA_SHANGHAI =
            ZoneId.of("Asia/Shanghai");

    /**
     * 日本时区。
     */
    public static final ZoneId ASIA_TOKYO =
            ZoneId.of("Asia/Tokyo");

    /**
     * 美国纽约。
     */
    public static final ZoneId AMERICA_NEW_YORK =
            ZoneId.of("America/New_York");

    /**
     * 美国洛杉矶。
     */
    public static final ZoneId AMERICA_LOS_ANGELES =
            ZoneId.of("America/Los_Angeles");

    /**
     * 英国。
     */
    public static final ZoneId EUROPE_LONDON =
            ZoneId.of("Europe/London");


    // ============================================================
    // 1. ZoneId 创建
    // ============================================================

    /**
     * 根据字符串创建 ZoneId。
     *
     * <p>
     * 示例：
     *
     * <pre>
     * Asia/Shanghai
     * Asia/Tokyo
     * America/New_York
     * Europe/London
     * </pre>
     */
    public static ZoneId of(String zoneId) {

        if (zoneId == null || zoneId.isBlank()) {
            return null;
        }

        return ZoneId.of(zoneId);
    }

    /**
     * 获取系统默认时区。
     *
     * <p>
     * 不建议用于用户业务。
     * 仅用于兼容老系统。
     */
    public static ZoneId systemDefault() {
        return ZoneId.systemDefault();
    }

    /**
     * 获取所有可用时区。
     */
    public static Set<String> availableZoneIds() {
        return ZoneId.getAvailableZoneIds();
    }


    // ============================================================
    // 2. Instant -> ZonedDateTime
    // ============================================================

    public static ZonedDateTime toZonedDateTime(
            Instant instant,
            ZoneId zone) {

        return convert(instant, zone);
    }


    // ============================================================
    // 3. ZonedDateTime -> Instant
    // ============================================================

    public static Instant toInstant(
            ZonedDateTime dateTime) {

        return dateTime == null
                ? null
                : dateTime.toInstant();
    }


    // ============================================================
    // 4. Instant -> LocalDateTime
    // ============================================================

    /**
     * 获取一个时间点在指定时区下的本地时间。
     */
    public static LocalDateTime toLocalDateTime(
            Instant instant,
            ZoneId zone) {

        if (instant == null) {
            return null;
        }

        requireZone(zone);

        return LocalDateTime.ofInstant(
                instant,
                zone
        );
    }


    // ============================================================
    // 5. Instant -> LocalDate
    // ============================================================

    /**
     * 获取一个时间点在指定时区下对应的日期。
     */
    public static LocalDate toLocalDate(
            Instant instant,
            ZoneId zone) {

        if (instant == null) {
            return null;
        }

        requireZone(zone);

        return instant
                .atZone(zone)
                .toLocalDate();
    }


    // ============================================================
    // 6. Instant -> LocalTime
    // ============================================================

    /**
     * 获取一个时间点在指定时区下对应的本地时间。
     */
    public static LocalTime toLocalTime(
            Instant instant,
            ZoneId zone) {

        if (instant == null) {
            return null;
        }

        requireZone(zone);

        return instant
                .atZone(zone)
                .toLocalTime();
    }


    // ============================================================
    // 7. LocalDateTime -> Instant
    // ============================================================

    /**
     * 将一个没有时区的 LocalDateTime
     * 按指定时区解释为一个绝对时间点。
     *
     * <p>
     * 例如：
     *
     * <pre>
     * 2026-08-18 09:00
     *
     * Asia/Shanghai
     *       ↓
     * 2026-08-18T01:00:00Z
     * </pre>
     */
    public static Instant toInstant(
            LocalDateTime dateTime,
            ZoneId zone) {

        if (dateTime == null) {
            return null;
        }

        requireZone(zone);

        return dateTime
                .atZone(zone)
                .toInstant();
    }


    // ============================================================
    // 8. LocalDate + LocalTime -> Instant
    // ============================================================

    /**
     * LocalDate + LocalTime + ZoneId -> Instant。
     */
    public static Instant toInstant(
            LocalDate date,
            LocalTime time,
            ZoneId zone) {

        if (date == null || time == null) {
            return null;
        }

        requireZone(zone);

        return ZonedDateTime
                .of(date, time, zone)
                .toInstant();
    }


    // ============================================================
    // 9. LocalDate -> ZonedDateTime
    // ============================================================

    /**
     * 获取某个日期在指定时区的开始时间。
     */
    public static ZonedDateTime atStartOfDay(
            LocalDate date,
            ZoneId zone) {

        if (date == null) {
            return null;
        }

        requireZone(zone);

        return date.atStartOfDay(zone);
    }

    /**
     * 获取某个日期的指定 LocalTime 对应的 ZonedDateTime。
     */
    public static ZonedDateTime at(
            LocalDate date,
            LocalTime time,
            ZoneId zone) {

        if (date == null || time == null) {
            return null;
        }

        requireZone(zone);

        return ZonedDateTime.of(
                date,
                time,
                zone
        );
    }


    // ============================================================
    // 10. 时区转换
    // ============================================================

    /**
     * 将一个 ZonedDateTime 转换到另外一个时区。
     *
     * <p>
     * 时间点不变，只改变观察时区。
     *
     * <pre>
     * 2026-08-18 09:00 Asia/Shanghai
     *
     * =>
     *
     * 2026-08-18 10:00 Asia/Tokyo
     * </pre>
     */
    public static ZonedDateTime convert(
            ZonedDateTime dateTime,
            ZoneId targetZone) {

        if (dateTime == null) {
            return null;
        }

        requireZone(targetZone);

        return dateTime.withZoneSameInstant(
                targetZone
        );
    }

    /**
     * Instant 在源时区显示，再转换到目标时区。
     */
    public static ZonedDateTime convert(
            Instant instant,
            ZoneId targetZone) {

        if (instant == null) {
            return null;
        }

        requireZone(targetZone);

        return instant.atZone(targetZone);
    }


    // ============================================================
    // 11. 时区之间的时间差
    // ============================================================

    /**
     * 获取指定时区在某个时间点的 UTC Offset。
     */
    public static ZoneOffset offsetAt(
            Instant instant,
            ZoneId zone) {

        if (instant == null) {
            return null;
        }

        requireZone(zone);

        return zone
                .getRules()
                .getOffset(instant);
    }

    /**
     * 获取当前时区 Offset。
     */
    public static ZoneOffset currentOffset(
            ZoneId zone) {

        requireZone(zone);

        return zone
                .getRules()
                .getOffset(Instant.now());
    }


    // ============================================================
    // 12. 时区合法性
    // ============================================================

    public static boolean isValid(String zoneId) {

        if (zoneId == null || zoneId.isBlank()) {
            return false;
        }

        return ZoneId
                .getAvailableZoneIds()
                .contains(zoneId);
    }

    private static void requireZone(ZoneId zone) {
        Assert.notNull(zone,"zone must not be null");
    }
}