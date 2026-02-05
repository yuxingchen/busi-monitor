package com.monitor.backend.enums;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * SQL 时间占位符枚举
 * 用于统一管理时间相关占位符的解析逻辑
 */
public enum TimePlaceholder {
    
    NOW("now", (now, today) -> now),
    TODAY("today", (now, today) -> today.atStartOfDay()),
    YESTERDAY("yesterday", (now, today) -> today.minusDays(1).atStartOfDay()),
    TODAY_START("todayStart", (now, today) -> today.atStartOfDay()),
    TODAY_END("todayEnd", (now, today) -> today.atTime(23, 59, 59)),
    YESTERDAY_START("yesterdayStart", (now, today) -> today.minusDays(1).atStartOfDay()),
    YESTERDAY_END("yesterdayEnd", (now, today) -> today.minusDays(1).atTime(23, 59, 59));
    
    public static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private final String placeholder;
    private final BiFunction<LocalDateTime, LocalDate, LocalDateTime> calculator;
    
    TimePlaceholder(String placeholder, BiFunction<LocalDateTime, LocalDate, LocalDateTime> calculator) {
        this.placeholder = placeholder;
        this.calculator = calculator;
    }
    
    public String getPlaceholder() {
        return placeholder;
    }
    
    /**
     * 计算占位符对应的时间值
     * @param referenceTime 参考时间点
     * @return 格式化后的时间字符串
     */
    public String resolve(LocalDateTime referenceTime) {
        LocalDate referenceDate = referenceTime.toLocalDate();
        LocalDateTime resultTime = calculator.apply(referenceTime, referenceDate);
        
        // TODAY 和 YESTERDAY 只返回日期部分
        if (this == TODAY || this == YESTERDAY) {
            return resultTime.toLocalDate().format(DATE_FORMATTER);
        }
        return resultTime.format(DATETIME_FORMATTER);
    }
    
    /**
     * 构建所有时间占位符的值映射
     * @param referenceTime 参考时间点（当前时间或历史时间）
     * @return 占位符名称到值的映射
     */
    public static Map<String, String> buildValues(LocalDateTime referenceTime) {
        Map<String, String> values = new HashMap<>();
        for (TimePlaceholder tp : values()) {
            values.put(tp.placeholder, tp.resolve(referenceTime));
        }
        return values;
    }
}
