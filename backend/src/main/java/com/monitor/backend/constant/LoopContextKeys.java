package com.monitor.backend.constant;

import java.util.List;

/**
 * 循环上下文变量键名常量
 */
public final class LoopContextKeys {
    private LoopContextKeys() {}
    
    // 上下文变量键
    public static final String INDEX = "loop.index";
    public static final String VALUE = "loop.value";
    public static final String TOTAL = "loop.total";
    
    // SQL占位符
    public static final String INDEX_PLACEHOLDER = "${loop.index}";
    public static final String VALUE_PLACEHOLDER = "${loop.value}";
    public static final String TOTAL_PLACEHOLDER = "${loop.total}";
    
    // 所有键列表
    public static final List<String> ALL_KEYS = List.of(INDEX, VALUE, TOTAL);
    
    /**
     * 替换SQL中的循环占位符
     */
    public static String replacePlaceholders(String sql, int index, Object value, int total) {
        return sql
            .replace(INDEX_PLACEHOLDER, String.valueOf(index))
            .replace(VALUE_PLACEHOLDER, String.valueOf(value))
            .replace(TOTAL_PLACEHOLDER, String.valueOf(total));
    }
}
