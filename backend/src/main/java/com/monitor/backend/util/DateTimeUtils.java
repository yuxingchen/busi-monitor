package com.monitor.backend.util;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 日期时间工具类
 * 统一使用 Asia/Shanghai 时区
 */
public final class DateTimeUtils {

    /**
     * 上海时区
     */
    public static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    private DateTimeUtils() {
        // 工具类禁止实例化
    }

    /**
     * 获取当前时间（上海时区）
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE_SHANGHAI);
    }

    /**
     * 获取指定分钟后的时间
     */
    public static LocalDateTime nowPlusMinutes(long minutes) {
        return now().plusMinutes(minutes);
    }

    /**
     * 获取指定小时后的时间
     */
    public static LocalDateTime nowPlusHours(long hours) {
        return now().plusHours(hours);
    }

    /**
     * 获取指定天后的时间
     */
    public static LocalDateTime nowPlusDays(long days) {
        return now().plusDays(days);
    }
}
