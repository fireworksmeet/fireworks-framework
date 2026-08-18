package com.yzm.fireworks.xxljob;
 
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author JYuan
 */
public class CronUtil {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("ss mm HH dd MM ? yyyy");
 
    public enum TimeCycle {
        /**
         * 时间枚举
         */
        YEAR, MONTH, WEEK, DAY, HOUR, MINUTE, SECOND
    }
 
    /**
     * 将LocalDateTime转换为cron表达式的字符串。
     *
     * @param dateTime 要转换的LocalDateTime
     * @return cron表达式
     */
    public static String toCronExpression(LocalDateTime dateTime) {
        return dateTime.format(FORMAT);
    }
 
    /**
     * 将指定的 LocalDateTime 对象转换为 指定周期的 cron 表达式字符串
     * @param dateTime LocalDateTime 对象
     * @param timeCycle 时间周期枚举值
     * @return cron 表达式字符串
     */
    public static String toCronExpression(LocalDateTime dateTime, TimeCycle timeCycle) {
        return switch (timeCycle) {
            case YEAR -> String.format("%d %d %d %d %d ? *", dateTime.getSecond(),
                    dateTime.getMinute(), dateTime.getHour(), dateTime.getDayOfMonth(),
                    dateTime.getMonthValue());
            case MONTH -> String.format("%d %d %d %d * ? *", dateTime.getSecond(),
                    dateTime.getMinute(), dateTime.getHour(), dateTime.getDayOfMonth());
            case WEEK -> String.format("%d %d %d ? * %d *", dateTime.getSecond(),
                    dateTime.getMinute(), dateTime.getHour(), dateTime.getDayOfWeek().getValue() % 7);
            case DAY -> String.format("%d %d %d * * ? *", dateTime.getSecond(),
                    dateTime.getMinute(), dateTime.getHour());
            case HOUR -> String.format("%d %d * * * ? *", dateTime.getSecond(),
                    dateTime.getMinute());
            case MINUTE -> String.format("%d * * * * ? *", dateTime.getSecond());
            case SECOND -> "0/1 * * * * ? *";
        };
    }
}