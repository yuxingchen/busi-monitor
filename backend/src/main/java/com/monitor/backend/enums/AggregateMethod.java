package com.monitor.backend.enums;

/**
 * 聚合计算方法枚举
 */
public enum AggregateMethod {
    SUM("SUM", "求和"),
    COUNT("COUNT", "计数"),
    AVG("AVG", "平均值"),
    MAX("MAX", "最大值"),
    MIN("MIN", "最小值");

    private final String code;
    private final String description;

    AggregateMethod(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据code获取枚举值（忽略大小写）
     */
    public static AggregateMethod fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AggregateMethod method : values()) {
            if (method.code.equalsIgnoreCase(code)) {
                return method;
            }
        }
        return null;
    }
}
