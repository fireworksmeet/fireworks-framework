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
        String cron;
        switch (timeCycle) {
            case YEAR:
                cron = String.format("%d %d %d %d %d ? *", dateTime.getSecond(),
                        dateTime.getMinute(), dateTime.getHour(), dateTime.getDayOfMonth(),
                        dateTime.getMonthValue());
                break;
            case MONTH:
                cron = String.format("%d %d %d %d * ? *", dateTime.getSecond(),
                        dateTime.getMinute(), dateTime.getHour(), dateTime.getDayOfMonth());
                break;
            case WEEK:
                cron = String.format("%d %d %d ? * %d *", dateTime.getSecond(),
                        dateTime.getMinute(), dateTime.getHour(), dateTime.getDayOfWeek().getValue() % 7);
                break;
            case DAY:
                cron = String.format("%d %d %d * * ? *", dateTime.getSecond(),
                        dateTime.getMinute(), dateTime.getHour());
                break;
            case HOUR:
                cron = String.format("%d %d * * * ? *", dateTime.getSecond(),
                        dateTime.getMinute());
                break;
            case MINUTE:
                cron = String.format("%d * * * * ? *", dateTime.getSecond());
 
            break;
            case SECOND:
                cron = "0/1 * * * * ? *";
                break;
            default:
                throw new IllegalArgumentException("Unknown time cycle: " + timeCycle);
        }
        return cron;
    }
}